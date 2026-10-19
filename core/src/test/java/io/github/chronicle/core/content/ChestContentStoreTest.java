package io.github.chronicle.core.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestContentStoreTest {
    @TempDir Path directory;

    @Test
    void roundTripsPendingEvidenceAndKeepsPreviousValidBackup() throws IOException {
        Path file = directory.resolve("chest-content.json");
        ChestContentStore store = new ChestContentStore(file);
        ChestContentState first = state(false);
        ChestContentState second = state(true);
        store.save(first);
        store.save(second);
        assertEquals(second, new ChestContentStore(file).load());
        assertEquals(first, new ChestContentStore(file.resolveSibling("chest-content.json.bak")).load());
    }

    @Test
    void corruptionDuplicatesAndExternalReplacementFailClosed() throws IOException {
        Path corrupt = directory.resolve("corrupt.json");
        Files.writeString(corrupt, "{bad data}");
        ChestContentStore corruptStore = new ChestContentStore(corrupt);
        assertThrows(IOException.class, corruptStore::load);
        assertThrows(IOException.class, () -> corruptStore.save(ChestContentState.empty()));
        assertEquals("{bad data}", Files.readString(corrupt));

        Path duplicate = directory.resolve("duplicate.json");
        Files.writeString(duplicate, "{\"schemaVersion\":1,\"schemaVersion\":1,\"revision\":0,\"templates\":[],\"bindings\":[],\"receipts\":[],\"pending\":[]}");
        assertThrows(IOException.class, new ChestContentStore(duplicate)::load);

        Path replaced = directory.resolve("replaced.json");
        ChestContentStore store = new ChestContentStore(replaced);
        store.save(ChestContentState.empty());
        store.load();
        Files.writeString(replaced, "{\"schemaVersion\":1,\"revision\":1,\"templates\":[],\"bindings\":[],\"receipts\":[],\"pending\":[]}");
        assertThrows(IOException.class, () -> store.save(ChestContentState.empty()));
    }

    @Test
    void semanticReferencesFailClosedOnLoad() throws IOException {
        Path file = directory.resolve("semantic.json");
        String missingTemplate = "{\"schemaVersion\":1,\"revision\":2,\"templates\":[],\"bindings\":[{\"id\":\"cache\",\"revision\":2,\"templateId\":\"missing\",\"templateRevision\":1,\"anchorId\":\"quiet_below:forge\",\"anchorRevision\":8,\"policy\":\"MANUAL\"}],\"receipts\":[],\"pending\":[]}";
        Files.writeString(file, missingTemplate);
        assertThrows(IOException.class, new ChestContentStore(file)::load);

        String missingBindingPending = "{\"schemaVersion\":1,\"revision\":3,\"templates\":[{\"id\":\"kit\",\"revision\":1,\"contents\":{\"size\":1,\"stacks\":[\"{}\"]}}],\"bindings\":[],\"receipts\":[],\"pending\":[{\"operationId\":\"00000000-0000-0000-0000-000000000001\",\"actorUuid\":\"00000000-0000-0000-0000-000000000002\",\"actorName\":\"op\",\"bindingId\":\"cache\",\"bindingRevision\":2,\"sessionNumber\":1,\"target\":{\"anchorId\":\"quiet_below:forge\",\"anchorRevision\":8,\"dimension\":\"minecraft:overworld\",\"x\":0,\"y\":64,\"z\":0,\"expectedBlock\":\"minecraft:barrel\"},\"before\":{\"size\":1,\"stacks\":[\"{}\"]},\"after\":{\"size\":1,\"stacks\":[\"{\\\"id\\\":\\\"minecraft:diamond\\\",\\\"count\\\":1}\"]},\"createdAt\":\"2026-10-19T12:00:00Z\"}]}";
        Files.writeString(file, missingBindingPending);
        assertThrows(IOException.class, new ChestContentStore(file)::load);
    }

    @Test
    void schemaOneMigratesOnSaveAndPreservesTheOriginalAsAValidBackup() throws IOException {
        Path file = directory.resolve("legacy.json");
        String schemaOne = "{\"schemaVersion\":1,\"revision\":2,\"templates\":[{\"id\":\"kit\",\"revision\":1,\"contents\":{\"size\":1,\"stacks\":[\"{}\"]}}],\"bindings\":[{\"id\":\"cache\",\"revision\":2,\"templateId\":\"kit\",\"templateRevision\":1,\"anchorId\":\"quiet_below:forge\",\"anchorRevision\":8,\"policy\":\"MANUAL\"}],\"receipts\":[],\"pending\":[]}";
        Files.writeString(file, schemaOne);
        ChestContentStore store = new ChestContentStore(file);
        ChestContentState migrated = store.load();
        assertEquals(LootSchedule.empty(), migrated.schedule());
        store.save(migrated);

        String schemaTwo = Files.readString(file);
        assertTrue(schemaTwo.contains("\"schemaVersion\":2"));
        assertTrue(schemaTwo.contains("\"schedule\""));
        assertEquals(schemaOne, Files.readString(file.resolveSibling("legacy.json.bak")));
        assertEquals(migrated, new ChestContentStore(file).load());
        assertEquals(migrated, new ChestContentStore(file.resolveSibling("legacy.json.bak")).load());
    }

    @Test
    void schemaTwoRoundTripsTypedBindingCyclesAndSchedule() throws IOException {
        Path file = directory.resolve("typed.json");
        ChestContentStore store = new ChestContentStore(file);
        ChestTarget physical = new ChestTarget("temporary", 1, "minecraft:overworld", 7, 64, 9, "minecraft:barrel");
        ChestBinding binding = ChestBinding.typed("chest_00000000000000000000000000000000", 1, "quiet_below:mine", physical);
        InventorySnapshot snapshot = new InventorySnapshot(1, List.of("{}"));
        PendingChestOperation pending = new PendingChestOperation(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "operator", binding.id(), binding.revision(), 4, 2, binding.directTarget(), snapshot, snapshot,
                "2026-10-19T12:00:00Z");
        ChestContentState state = new ChestContentState(1, Map.of(), Map.of(binding.id(), binding), List.of(),
                Map.of(pending.operationId(), pending), new LootSchedule(4, 10,
                Map.of("quiet_below:mine", new LootCycle(2, 30, 20))));
        store.save(state);
        assertEquals(state, new ChestContentStore(file).load());
    }

    private static ChestContentState state(boolean withPending) {
        InventorySnapshot snapshot = new InventorySnapshot(2, List.of("{}", "{\"id\":\"minecraft:diamond\",\"count\":1}"));
        ChestTemplate template = new ChestTemplate("kit", 1, snapshot);
        ChestBinding binding = new ChestBinding("cache", 2, "kit", 1, "quiet_below:forge", 8, ChestPolicy.ONCE_SESSION);
        Map<String, PendingChestOperation> pending = Map.of();
        if (withPending) {
            PendingChestOperation operation = new PendingChestOperation(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "operator", "cache", 2, 3,
                    new ChestTarget("quiet_below:forge", 8, "minecraft:overworld", 1, 2, 3, "minecraft:barrel"), snapshot, snapshot, "2026-10-19T12:00:00Z");
            pending = Map.of(operation.operationId(), operation);
        }
        return new ChestContentState(withPending ? 3 : 2, Map.of("kit", template), Map.of("cache", binding), List.of(), pending);
    }
}
