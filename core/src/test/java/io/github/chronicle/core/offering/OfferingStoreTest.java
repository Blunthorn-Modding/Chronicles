package io.github.chronicle.core.offering;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferingStoreTest {
    @TempDir Path directory;
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRANSACTION = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final OfferingDefinition HEART = new OfferingDefinition("quiet_below:extra_heart", new OfferingTerms(2, 2, 0), 3);

    @Test void abandonedBackupPreventsSilentEmptyInitialization() throws Exception {
        Path file=directory.resolve("offerings.json");
        byte[] backup=OfferingStore.encode(OfferingLedger.EMPTY.purchase(0,TRANSACTION,PLAYER,"quiet_below:network",HEART)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(file.resolveSibling("offerings.json.bak"),backup);
        try(OfferingStore store=new OfferingStore(file)) { assertThrows(IOException.class,store::loadOrCreate); }
        assertFalse(Files.exists(file));
        assertArrayEquals(backup,Files.readAllBytes(file.resolveSibling("offerings.json.bak")));
    }

    @Test void createsMarkerRoundTripsAndRetainsExactPreviousBackup() throws Exception {
        Path file = directory.resolve("world/chronicle/offerings.json");
        OfferingLedger replacement;
        byte[] initial;
        try (OfferingStore store = new OfferingStore(file)) {
            OfferingLedger empty = store.loadOrCreate();
            initial = Files.readAllBytes(file);
            assertTrue(Files.isRegularFile(file.resolveSibling("offerings.json.initialized")));
            replacement = empty.purchase(0, TRANSACTION, PLAYER, "quiet_below:network", HEART);
            assertEquals(replacement, store.save(replacement, empty));
            store.verifyUnchanged();
        }
        assertArrayEquals(initial, Files.readAllBytes(file.resolveSibling("offerings.json.bak")));
        assertEquals(replacement, OfferingStore.decode(Files.readString(file)));
        try (OfferingStore restarted = new OfferingStore(file)) { assertEquals(replacement, restarted.loadOrCreate()); }
    }

    @Test void rejectsUnknownDuplicateWrongTypesAndNoncanonicalUuidWithoutReplacingEvidence() throws Exception {
        String valid = OfferingStore.encode(OfferingLedger.EMPTY);
        String[] invalid = {
                valid.replaceFirst("\\{", "{\"unknown\":true,"),
                valid.replaceFirst("\"revision\"\\s*:\\s*0", "\"revision\":0,\"revision\":1"),
                valid.replaceFirst("\"revision\"\\s*:\\s*0", "\"revision\":false"),
                receiptJson(TRANSACTION.toString().toUpperCase()),
                receiptJson("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa"),
                receiptJson("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa").replace("\"health\": 2", "\"health\": 2.0")
        };
        for (int index = 0; index < invalid.length; index++) {
            Path file = directory.resolve("invalid-" + index + ".json");
            initialize(file);
            Files.writeString(file, invalid[index]);
            try (OfferingStore store = new OfferingStore(file)) { assertThrows(IOException.class, store::loadOrCreate); }
            assertEquals(invalid[index], Files.readString(file));
            assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".bak")));
        }
    }

    @Test void initializedMissingLedgerAndLedgerWithoutMarkerBothHold() throws Exception {
        Path missing = directory.resolve("missing.json");
        initialize(missing);
        Files.delete(missing);
        try (OfferingStore store = new OfferingStore(missing)) { assertThrows(IOException.class, store::loadOrCreate); }
        assertFalse(Files.exists(missing));

        Path unmarked = directory.resolve("unmarked.json");
        Files.writeString(unmarked, OfferingStore.encode(OfferingLedger.EMPTY));
        try (OfferingStore store = new OfferingStore(unmarked)) { assertThrows(IOException.class, store::loadOrCreate); }
        assertEquals(OfferingLedger.EMPTY, OfferingStore.decode(Files.readString(unmarked)));
    }

    @Test void detectsExternalByteChangesAndMissingFiles() throws Exception {
        Path file = directory.resolve("external.json");
        try (OfferingStore store = new OfferingStore(file)) {
            store.loadOrCreate();
            String changed = Files.readString(file) + "\n";
            Files.writeString(file, changed);
            assertThrows(IOException.class, store::verifyUnchanged);
            assertEquals(changed, Files.readString(file));
        }
        Path deleted = directory.resolve("deleted.json");
        try (OfferingStore store = new OfferingStore(deleted)) {
            store.loadOrCreate();
            Files.delete(deleted);
            assertThrows(IOException.class, store::verifyUnchanged);
        }
    }

    @Test void lifetimeLockExcludesAnotherStoreAndReleasesOnClose() throws Exception {
        Path file = directory.resolve("locked.json");
        OfferingStore first = new OfferingStore(file);
        first.loadOrCreate();
        assertThrows(IOException.class, () -> new OfferingStore(file));
        first.close();
        try (OfferingStore reopened = new OfferingStore(file)) { assertEquals(OfferingLedger.EMPTY, reopened.loadOrCreate()); }
    }

    @Test void rejectsOversizedExistingFileAndInvalidMarker() throws Exception {
        Path oversized = directory.resolve("oversized.json");
        initialize(oversized);
        Files.write(oversized, new byte[(int) OfferingStore.MAX_FILE_BYTES + 1]);
        try (OfferingStore store = new OfferingStore(oversized)) { assertThrows(IOException.class, store::loadOrCreate); }

        Path marker = directory.resolve("marker.json");
        initialize(marker);
        Files.writeString(marker.resolveSibling("marker.json.initialized"), "tampered\n");
        try (OfferingStore store = new OfferingStore(marker)) { assertThrows(IOException.class, store::loadOrCreate); }
    }

    private static void initialize(Path file) throws Exception {
        try (OfferingStore store = new OfferingStore(file)) { store.loadOrCreate(); }
    }

    private static String receiptJson(String transaction) {
        OfferingReceipt receipt = new OfferingReceipt(TRANSACTION, PLAYER, "quiet_below:network", HEART.id(), HEART.terms());
        String valid = OfferingStore.encode(new OfferingLedger(1, List.of(receipt), Set.of()));
        return valid.replace(TRANSACTION.toString(), transaction);
    }
}
