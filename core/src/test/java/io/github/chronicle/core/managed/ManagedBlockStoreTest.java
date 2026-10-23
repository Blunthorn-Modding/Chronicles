package io.github.chronicle.core.managed;

import io.github.chronicle.core.authoring.BlockPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ManagedBlockStoreTest {
    @TempDir Path directory;

    @Test void roundTripsLedgerAndPolicyAndKeepsBackups() throws IOException {
        Path ledgerFile = directory.resolve("managed-blocks.json");
        ManagedBlockStore store = new ManagedBlockStore(ledgerFile);
        assertEquals(ManagedBlockLedger.unmigrated(), store.load()); assertTrue(store.wasAbsent());
        ManagedBlockLedger first = new ManagedBlockLedger(1, 1, List.of(instance(1, ManagedBlockInstance.Registration.RESERVED)));
        ManagedBlockLedger second = new ManagedBlockLedger(2, 1, List.of(instance(2, ManagedBlockInstance.Registration.CONFIRMED)));
        store.save(first); store.save(second);
        assertEquals(second, new ManagedBlockStore(ledgerFile).load());
        assertEquals(first, new ManagedBlockStore(ledgerFile.resolveSibling("managed-blocks.json.bak")).load());

        Path limitsFile = directory.resolve("managed-block-limits.json");
        ManagedBlockLimitStore limits = new ManagedBlockLimitStore(limitsFile);
        limits.save(new ManagedBlockLimits(1, Map.of("test:altar", 3, "missing:opaque", 7)));
        assertEquals(Map.of("test:altar", 3, "missing:opaque", 7), new ManagedBlockLimitStore(limitsFile).load().overrides());
    }

    @Test void rejectsDuplicatePositionsUnknownFieldsAndExternalReplacement() throws IOException {
        Path file = directory.resolve("managed-blocks.json");
        String duplicate = "{\"schemaVersion\":1,\"revision\":2,\"migrationVersion\":1,\"instances\":[" + encoded("00000000-0000-0000-0000-000000000001") + "," + encoded("00000000-0000-0000-0000-000000000002") + "]}";
        Files.writeString(file, duplicate);
        assertThrows(IOException.class, () -> new ManagedBlockStore(file).load());
        assertEquals(duplicate, Files.readString(file));

        Files.writeString(file, "{\"schemaVersion\":1,\"revision\":0,\"migrationVersion\":1,\"instances\":[],\"extra\":true}");
        assertThrows(IOException.class, () -> new ManagedBlockStore(file).load());

        ManagedBlockStore store = new ManagedBlockStore(directory.resolve("conflict.json"));
        store.load(); Files.writeString(directory.resolve("conflict.json"), "{\"schemaVersion\":1,\"revision\":0,\"migrationVersion\":1,\"instances\":[]}");
        assertThrows(IOException.class, () -> store.save(ManagedBlockLedger.empty()));
    }

    @Test void invalidFixedOverrideRemainsUntouchedForServiceToReject() throws IOException {
        Path file = directory.resolve("managed-block-limits.json");
        Files.writeString(file, "{\"schemaVersion\":1,\"revision\":1,\"overrides\":{\"test:heart\":2}}");
        ManagedBlockCatalog catalog = new ManagedBlockCatalog();
        catalog.register(new ManagedBlockDefinition("test:heart", "test:heart", "Heart", "Root", 1, ManagedBlockDefinition.LimitMode.FIXED));
        assertThrows(IllegalStateException.class, () -> new ManagedBlockService(catalog, new MemoryLedger(), new ManagedBlockLimitStore(file)));
        assertEquals("{\"schemaVersion\":1,\"revision\":1,\"overrides\":{\"test:heart\":2}}", Files.readString(file));
    }

    @Test void incompleteMigrationResumesButCompletedLedgerDoesNotReimportHistory() throws IOException {
        Path file = directory.resolve("managed-blocks.json"); ManagedBlockStore store = new ManagedBlockStore(file);
        ManagedBlockService service = new ManagedBlockService(catalog(), store, new MemoryLimits());
        assertTrue(service.migrationRequired());
        ManagedBlockInstance imported = service.adopt("test:altar", "minecraft:overworld", new BlockPosition(1, 2, 3), ManagedBlockInstance.Origin.LEGACY);
        assertTrue(new ManagedBlockService(catalog(), new ManagedBlockStore(file), new MemoryLimits()).migrationRequired());
        service.completeMigration(); service.release(imported.id());
        ManagedBlockService restarted = new ManagedBlockService(catalog(), new ManagedBlockStore(file), new MemoryLimits());
        assertFalse(restarted.migrationRequired());
        assertTrue(restarted.ledger().instances().isEmpty());
    }

    private static ManagedBlockCatalog catalog() {
        ManagedBlockCatalog catalog = new ManagedBlockCatalog();
        catalog.register(new ManagedBlockDefinition("test:altar", "test:altar", "Altar", "Station", 1, ManagedBlockDefinition.LimitMode.EDITABLE));
        return catalog;
    }

    private static ManagedBlockInstance instance(long revision, ManagedBlockInstance.Registration registration) {
        return new ManagedBlockInstance(UUID.fromString("00000000-0000-0000-0000-000000000001"), "test:altar", "test:altar", "minecraft:overworld",
                new BlockPosition(1, 2, 3), registration, ManagedBlockInstance.Origin.ITEM, revision);
    }
    private static String encoded(String id) {
        return "{\"id\":\"" + id + "\",\"type\":\"test:altar\",\"block\":\"test:altar\",\"dimension\":\"minecraft:overworld\",\"position\":{\"x\":1,\"y\":2,\"z\":3},\"registration\":\"CONFIRMED\",\"origin\":\"ITEM\",\"revision\":1}";
    }
    private static final class MemoryLedger implements ManagedBlockLedgerRepository {
        public ManagedBlockLedger load() { return ManagedBlockLedger.empty(); }
        public void save(ManagedBlockLedger ledger) { }
    }
    private static final class MemoryLimits implements ManagedBlockLimitRepository {
        ManagedBlockLimits state = ManagedBlockLimits.empty();
        public ManagedBlockLimits load() { return state; }
        public void save(ManagedBlockLimits limits) { state = limits; }
    }
}
