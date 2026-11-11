package io.github.chronicle.core.offering;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferingServiceTest {
    @TempDir Path directory;
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final OfferingDefinition HEART = new OfferingDefinition("quiet_below:extra_heart", new OfferingTerms(2, 2, 0), 3);

    @Test void purchasesReplayRestartAndSnapshotChangedTerms() throws Exception {
        Path file = directory.resolve("offerings.json");
        UUID firstTransaction = transaction(1);
        OfferingLedger saved;
        try (OfferingService service = new OfferingService(new OfferingStore(file))) {
            OfferingLedger first = service.purchase(0, firstTransaction, PLAYER, "quiet_below:network", HEART);
            assertEquals(first, service.purchase(0, firstTransaction, PLAYER, "quiet_below:network", HEART));
            OfferingDefinition changed = new OfferingDefinition(HEART.id(), new OfferingTerms(4, 1, 0), 3);
            saved = service.purchase(1, transaction(2), PLAYER, "quiet_below:network", changed);
            assertEquals(new OfferingTerms(6, 3, 0), saved.totals(PLAYER).get("quiet_below:network"));
        }
        try (OfferingService restarted = new OfferingService(new OfferingStore(file))) { assertEquals(saved, restarted.ledger()); }
    }

    @Test void closurePersistsTombstoneAndLeavesOtherSourceIntact() throws Exception {
        Path file = directory.resolve("closure.json");
        OfferingLedger closed;
        try (OfferingService service = new OfferingService(new OfferingStore(file))) {
            service.purchase(0, transaction(1), PLAYER, "quiet_below:network", HEART);
            service.purchase(1, transaction(2), PLAYER, "other:network", HEART);
            closed = service.closeSource("quiet_below:network");
            assertTrue(closed.closedSources().contains("quiet_below:network"));
            assertEquals(1, closed.receipts().size());
            assertEquals(closed, service.closeSource("quiet_below:network"));
            assertThrows(IllegalStateException.class, () -> service.purchase(closed.revision(), transaction(3), PLAYER, "quiet_below:network", HEART));
        }
        try (OfferingService restarted = new OfferingService(new OfferingStore(file))) { assertEquals(closed, restarted.ledger()); }
    }

    @Test void failedPublishAndExternalEditNeverPublishNewInMemoryState() throws Exception {
        Path failed = directory.resolve("failed.json");
        try (OfferingService service = new OfferingService(new OfferingStore(failed))) {
            Files.createDirectory(failed.resolveSibling("failed.json.bak"));
            assertThrows(IOException.class, () -> service.purchase(0, transaction(1), PLAYER, "quiet_below:network", HEART));
            assertEquals(OfferingLedger.EMPTY, service.ledger());
            assertEquals(OfferingLedger.EMPTY, OfferingStore.decode(Files.readString(failed)));
        }

        Path external = directory.resolve("external.json");
        try (OfferingService service = new OfferingService(new OfferingStore(external))) {
            Files.writeString(external, Files.readString(external) + "\n");
            assertThrows(IOException.class, () -> service.purchase(0, transaction(2), PLAYER, "quiet_below:network", HEART));
            assertEquals(OfferingLedger.EMPTY, service.ledger());
        }
    }

    @Test void externalInterleavingAtEitherPublishBoundaryPreservesExternalBytesAndServiceState() throws Exception {
        for (OfferingStore.SaveCheckpoint target : OfferingStore.SaveCheckpoint.values()) {
            Path file = directory.resolve("interleaving-" + target + ".json");
            OfferingStore.SaveObserver mutation = checkpoint -> {
                if (checkpoint == target) Files.writeString(file, Files.readString(file) + "\n");
            };
            try (OfferingService service = new OfferingService(new OfferingStore(file, mutation))) {
                String original = Files.readString(file);
                assertThrows(IOException.class, () -> service.purchase(0, transaction(10 + target.ordinal()), PLAYER,
                        "quiet_below:network", HEART));
                assertEquals(OfferingLedger.EMPTY, service.ledger());
                assertEquals(original + "\n", Files.readString(file));
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                if (target == OfferingStore.SaveCheckpoint.BEFORE_BACKUP_PUBLISH) {
                    assertTrue(Files.notExists(backup));
                } else {
                    assertEquals(original, Files.readString(backup));
                }
            }
        }
    }

    private static UUID transaction(int index) { return new UUID(5, index); }
}
