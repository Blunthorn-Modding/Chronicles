package io.github.chronicle.core.entry;

import io.github.chronicle.core.content.InventorySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleEntryStoreTest {
    @TempDir Path temporary;

    @Test void exactOperationRoundTripsIncludingFloatingPointEvidence() throws IOException {
        Path file = temporary.resolve("role-first-entry.json"); RoleEntryStore store = new RoleEntryStore(file);
        RoleEntryOperation operation = operation(); RoleEntryState expected = new RoleEntryState(7, Map.of(operation.playerId(), operation));
        store.load(); store.save(expected);
        assertEquals(expected, new RoleEntryStore(file).load());
    }

    @Test void truncatedOrExternallyChangedEvidenceIsNeverOverwritten() throws IOException {
        Path file = temporary.resolve("role-first-entry.json"); RoleEntryStore store = new RoleEntryStore(file);
        store.load(); store.save(new RoleEntryState(1, Map.of(operation().playerId(), operation())));
        Files.writeString(file, "{\"schemaVersion\":1");
        assertThrows(IOException.class, () -> store.save(RoleEntryState.empty()));
        assertEquals("{\"schemaVersion\":1", Files.readString(file));
        assertThrows(IOException.class, () -> new RoleEntryStore(file).load());
    }

    @Test void duplicatePlayersAndUnknownFieldsFailClosed() {
        String encoded = RoleEntryStore.encode(new RoleEntryState(1, Map.of(operation().playerId(), operation())));
        assertThrows(RuntimeException.class, () -> RoleEntryStore.decode(encoded.replaceFirst("\\{", "{\"unexpected\":true,")));
        // Parser-level duplicate JSON members are rejected before schema conversion.
        assertThrows(RuntimeException.class, () -> RoleEntryStore.decode("{\"schemaVersion\":1,\"schemaVersion\":1,\"revision\":0,\"operations\":[]}"));
    }

    private static RoleEntryOperation operation() {
        ArrayList<String> before = new ArrayList<>(java.util.Collections.nCopies(36, "{}"));
        ArrayList<String> after = new ArrayList<>(before); after.set(35, "{\"id\":\"minecraft:bread\",\"count\":1}");
        String content = "{\"id\":\"quiet_below:scholar\"}";
        return new RoleEntryOperation(UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "quiet_below:scholar", fingerprint(content), content,
                new InventorySnapshot(36, before), new InventorySnapshot(36, after),
                new PlayerPosition("minecraft:overworld", Double.valueOf("0x1.0000000000001p0"), 64, -2, 12.5f, -4.5f),
                new PlayerPosition("minecraft:overworld", 8.5, 70, 2.5, 180, 0), "",
                List.of(), RoleEntryOperation.Stage.APPLYING, "claimed", "2026-10-26T12:00:00Z");
    }
    private static String fingerprint(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new AssertionError(exception); }
    }
}
