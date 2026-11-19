package io.github.chronicle.core.access;

import io.github.chronicle.core.access.RegionAccessStore.Binding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegionAccessStoreTest {
    private static final Binding PALACE = new Binding("palace", 4, "test:access", "palace_exit", 9);
    @TempDir Path world;

    @Test void portableBindingsSurviveRestartWithExactSiteRevisions() throws IOException {
        var store = new RegionAccessStore(world);
        assertEquals(Map.of(), store.load());
        assertFalse(Files.exists(world.resolve("region-access.json")));
        var pending = new Binding("garden", 11, "test:access", "", 0);
        var bindings = Map.of("palace", PALACE, "garden", pending);
        store.save(bindings);
        assertEquals(bindings, new RegionAccessStore(world).load());
        store.save(Map.of());
        assertEquals(Map.of(), new RegionAccessStore(world).load());
    }

    @Test void initializedMissingCatalogRefusesAndDoesNotRecreate() throws IOException {
        var store = new RegionAccessStore(world);
        store.load();
        store.save(Map.of("palace", PALACE));
        var file = world.resolve("region-access.json");
        Files.delete(file);
        assertThrows(IOException.class, () -> new RegionAccessStore(world).load());
        assertFalse(Files.exists(file));
    }

    @Test void invalidCatalogAndExternalReplacementArePreserved() throws IOException {
        var store = new RegionAccessStore(world);
        store.load();
        store.save(Map.of("palace", PALACE));
        var file = world.resolve("region-access.json");
        var invalid = "{\"schemaVersion\":1,\"bindings\":[],\"bindings\":[]}";
        Files.writeString(file, invalid);
        assertThrows(IOException.class, () -> new RegionAccessStore(world).load());
        assertThrows(IOException.class, () -> store.save(Map.of()));
        assertEquals(invalid, Files.readString(file));
    }

    @Test void initializedMarkerChangesCannotBeOverwrittenDuringAnEdit() throws IOException {
        var store = new RegionAccessStore(world);
        store.load();
        store.save(Map.of("palace", PALACE));
        var marker = world.resolve("region-access.initialized");
        Files.writeString(marker, "corrupt\n");
        assertThrows(IOException.class, () -> store.save(Map.of()));
        assertThrows(IOException.class, () -> new RegionAccessStore(world).load());
        assertEquals("corrupt\n", Files.readString(marker));
    }
}
