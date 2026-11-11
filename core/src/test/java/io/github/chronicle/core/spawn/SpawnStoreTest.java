package io.github.chronicle.core.spawn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpawnStoreTest {
    @TempDir Path directory;
    @Test void restartAndBackupKeepStrictRevisionedBindings() throws IOException {
        Path file = directory.resolve("spawns.json"); SpawnStore store = new SpawnStore(file);
        SpawnRegistry first = new SpawnRegistry(1, Map.of("arrival", new SpawnBinding("arrival", "mine", 3)));
        SpawnRegistry second = new SpawnRegistry(2, Map.of("arrival", new SpawnBinding("arrival", "city", 5)));
        store.save(first); store.save(second);
        assertEquals(second, new SpawnStore(file).load());
        assertEquals(first, new SpawnStore(file.resolveSibling("spawns.json.bak")).load());
    }
    @Test void rejectsCorruptAndFutureDataWithoutOverwritingIt() throws IOException {
        Path file = directory.resolve("spawns.json"); SpawnStore store = new SpawnStore(file);
        String corrupt = "{\"schemaVersion\":2,\"revision\":0,\"bindings\":[]}"; Files.writeString(file, corrupt);
        assertThrows(IOException.class, store::load); assertThrows(IOException.class, () -> store.save(SpawnRegistry.empty())); assertEquals(corrupt, Files.readString(file));
        Files.writeString(file, "{\"schemaVersion\":1,\"revision\":0,\"bindings\":[],\"unknown\":true}"); assertThrows(IOException.class, store::load);
        Files.writeString(file, "{\"schemaVersion\":1,\"revision\":0,\"revision\":1,\"bindings\":[]}"); assertThrows(IOException.class, store::load);
    }
}
