package io.github.chronicle.core.spawn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutomaticSpawnStoreTest {
    @TempDir Path directory;
    private static final UUID PLAYER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void restartsRoundTripIndependentPoliciesAndPreservePreviousBackup() throws IOException {
        Path file = directory.resolve("automatic-spawns.json");
        AutomaticSpawnStore store = new AutomaticSpawnStore(file);
        AutomaticSpawnState first = state(1, new SpawnBinding("arrival", "city", 2), null, null);
        AutomaticSpawnState second = state(2, new SpawnBinding("arrival", "city", 2), new SpawnBinding("respawn", "mine", 3),
                new AutomaticSpawnState.Arrival("arrival", AutomaticSpawnState.Status.MOVED, "moved"));
        store.save(first);
        store.save(second);
        assertEquals(second, new AutomaticSpawnStore(file).load());
        assertEquals(first, new AutomaticSpawnStore(file.resolveSibling("automatic-spawns.json.bak")).load());
    }

    @Test
    void rejectsStrictSchemaDuplicateMembersAndInvalidUuidWithoutReplacingEvidence() throws IOException {
        Path file = directory.resolve("automatic-spawns.json");
        AutomaticSpawnStore store = new AutomaticSpawnStore(file);
        String duplicate = "{\"schemaVersion\":1,\"revision\":0,\"policies\":[],\"arrivals\":[],\"revision\":1}";
        Files.writeString(file, duplicate);
        assertThrows(IOException.class, store::load);
        assertThrows(IOException.class, () -> store.save(AutomaticSpawnState.empty()));
        assertEquals(duplicate, Files.readString(file));

        String unknown = "{\"schemaVersion\":1,\"revision\":0,\"policies\":[],\"arrivals\":[],\"unknown\":true}";
        Files.writeString(file, unknown);
        assertThrows(IOException.class, store::load);
        String badUuid = "{\"schemaVersion\":1,\"revision\":0,\"policies\":[{\"player\":\"not-a-uuid\",\"arrival\":null,\"respawn\":{\"purpose\":\"respawn\",\"locationId\":\"mine\",\"locationRevision\":1}}],\"arrivals\":[]}";
        Files.writeString(file, badUuid);
        assertThrows(IOException.class, store::load);
        String duplicatePlayer = "{\"schemaVersion\":1,\"revision\":0,\"policies\":[{\"player\":\"22222222-2222-2222-2222-222222222222\",\"arrival\":{\"purpose\":\"arrival\",\"locationId\":\"city\",\"locationRevision\":1},\"respawn\":null},{\"player\":\"22222222-2222-2222-2222-222222222222\",\"arrival\":null,\"respawn\":{\"purpose\":\"respawn\",\"locationId\":\"mine\",\"locationRevision\":1}}],\"arrivals\":[]}";
        Files.writeString(file, duplicatePlayer);
        assertThrows(IOException.class, store::load);
        Files.writeString(file, "{\"schemaVersion\":2,\"revision\":0,\"policies\":[],\"arrivals\":[]}");
        assertThrows(IOException.class, store::load);
    }

    @Test
    void refusesExternalEditsAndPresenceChangesAfterObservation() throws IOException {
        Path file = directory.resolve("automatic-spawns.json");
        AutomaticSpawnStore store = new AutomaticSpawnStore(file);
        store.save(AutomaticSpawnState.empty());
        store.load();
        Files.writeString(file, "{\"schemaVersion\":1,\"revision\":1,\"policies\":[],\"arrivals\":[]}");
        assertThrows(IOException.class, () -> store.save(AutomaticSpawnState.empty()));

        Path absent = directory.resolve("created.json");
        AutomaticSpawnStore absentStore = new AutomaticSpawnStore(absent);
        absentStore.load();
        Files.writeString(absent, "{\"schemaVersion\":1,\"revision\":0,\"policies\":[],\"arrivals\":[]}");
        assertThrows(IOException.class, () -> absentStore.save(AutomaticSpawnState.empty()));
    }

    @Test
    void rejectsOversizedAndNestedJson() throws IOException {
        Path oversized = directory.resolve("oversized.json");
        Files.writeString(oversized, " ".repeat((int) AutomaticSpawnStore.MAX_FILE_BYTES + 1));
        assertThrows(IOException.class, () -> new AutomaticSpawnStore(oversized).load());

        Path nested = directory.resolve("nested.json");
        Files.writeString(nested, "[".repeat(18) + "0" + "]".repeat(18));
        assertThrows(IOException.class, () -> new AutomaticSpawnStore(nested).load());
    }

    private static AutomaticSpawnState state(long revision, SpawnBinding arrival, SpawnBinding respawn, AutomaticSpawnState.Arrival receipt) {
        Map<UUID, AutomaticSpawnState.Policy> policies = arrival == null && respawn == null ? Map.of() : Map.of(PLAYER, new AutomaticSpawnState.Policy(arrival, respawn));
        return new AutomaticSpawnState(revision, policies, receipt == null ? Map.of() : Map.of(PLAYER, receipt));
    }
}
