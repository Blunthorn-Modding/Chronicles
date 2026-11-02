package io.github.chronicle.core.scenario;

import io.github.chronicle.core.books.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioProfileContentStoreTest {
    @TempDir Path root;
    @Test void publicationIsCompleteAndMissingInitializedCatalogNeverRegenerates()throws Exception{
        var profile=ScenarioProfile.empty("custom:a","A",Set.of());var store=new ScenarioProfileContentStore(root.resolve("config"));
        Path directory=store.initialize(profile);assertTrue(Files.isRegularFile(directory.resolve("books.json")));assertTrue(Files.isRegularFile(directory.resolve("chest-loot.json")));
        Files.delete(directory.resolve("books.json"));assertThrows(java.io.IOException.class,()->store.initialize(profile));assertFalse(Files.exists(directory.resolve("books.json")));
    }
    @Test void existingServerCatalogsAndCampaignWorldArePreserved()throws Exception{
        Path config=root.resolve("config");Files.createDirectories(config);Files.writeString(config.resolve("books.json"),"original");
        var profile=ScenarioProfile.empty("custom:a","A",Set.of());assertThrows(java.io.IOException.class,()->new ScenarioProfileContentStore(config).initialize(profile));assertEquals("original",Files.readString(config.resolve("books.json")));
        Path world=root.resolve("world");Files.createDirectories(world.resolve("chronicle"));Files.writeString(world.resolve("chronicle/authoring.json"),"original");
        assertThrows(java.io.IOException.class,()->ScenarioProfileContentStore.bindWorld(world,profile));assertEquals("original",Files.readString(world.resolve("chronicle/authoring.json")));
    }
    @Test void matchingProfileRestartKeepsEditsAndRejectsMissingOrDifferentIdentity()throws Exception{
        var profile=ScenarioProfile.empty("custom:a","A",Set.of());var store=new ScenarioProfileContentStore(root.resolve("config"));Path config=store.initialize(profile);
        var book=new BookDefinition(BookDefinition.GENERATED_ID_ROOT+"profile_test","Edited","Author","",List.of("Page"),BookDefinition.Rule.ONCE);
        var books=new BookConfig(config.resolve("books.json"));books.loadExisting();books.replace(book,null);store.initialize(profile);
        assertEquals(book,new BookConfig(config.resolve("books.json")).loadExisting().get(book.id()));
        Path world=root.resolve("world");ScenarioProfileContentStore.bindWorld(world,profile);ScenarioProfileContentStore.bindWorld(world,profile);
        assertThrows(java.io.IOException.class,()->ScenarioProfileContentStore.bindWorld(world,null));
        assertThrows(java.io.IOException.class,()->ScenarioProfileContentStore.bindWorld(world,ScenarioProfile.empty("custom:a","A",Set.of())));
    }
}
