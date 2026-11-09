package io.github.chronicle.core.server;

import io.github.chronicle.core.eventpool.*;
import io.github.chronicle.core.scenario.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioPortableSectionsTest {
    @TempDir Path directory;
    @Test void missingProviderRejectsProfileWithoutDiscardingUnknownData() throws Exception {
        var content = new ScenarioContent("custom:a",Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of("missing_test:section","{}"));
        assertEquals(content,ScenarioContentJson.decode(ScenarioContentJson.encode(content)));
        assertThrows(IOException.class,()->ScenarioContentRuntime.validateExtensions(content));
    }
    @Test void legacyMigrationPreservesCatalogAndNeverRestoresDeletedPools() throws Exception {
        var existing=new EventPoolDefinition("custom:existing","Existing",List.of());
        var imported=new EventPoolDefinition("custom:imported","Imported",List.of());
        var catalog=new EventPoolCatalog(new EventPoolCatalogStore(directory.resolve("event-pools.json")),List.of(existing));
        var content=new ScenarioContent("custom:a",Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(imported.id(),imported),Map.of());
        ScenarioAuthoringProfiles.initializePortableSections(directory,content);
        assertEquals(Map.of(existing.id(),existing),EventPoolCatalogStore.decode(Files.readAllBytes(directory.resolve("event-pools.json"))));
        catalog.delete(catalog.revision(),existing.id());
        ScenarioAuthoringProfiles.initializePortableSections(directory,content);
        assertTrue(EventPoolCatalogStore.decode(Files.readAllBytes(directory.resolve("event-pools.json"))).isEmpty());
        Files.delete(directory.resolve("event-pools.json"));
        assertThrows(IOException.class,()->ScenarioAuthoringProfiles.initializePortableSections(directory,content));
        assertFalse(Files.exists(directory.resolve("event-pools.json")));
    }
}
