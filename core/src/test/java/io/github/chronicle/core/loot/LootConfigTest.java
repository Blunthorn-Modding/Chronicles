package io.github.chronicle.core.loot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LootConfigTest {
    @TempDir Path directory;

    private static LootType type(String id, String label) {
        return new LootType(id, label, "A useful cache.", 3, 30, List.of(new LootEntry("minecraft:bread", 75, 1, 2, "{\"minecraft:custom_name\":\"Rations\"}")));
    }

    @Test void createsPrettyForcedDefaultsAndLoadsTheirUnionWithOverridesAndCustomTypes() throws Exception {
        Path file = directory.resolve("config/loot.json");
        Map<String, LootType> defaults = Map.of("example:camp", type("example:camp", "Default"), "example:mine", type("example:mine", "Mine"));
        LootConfig config = new LootConfig(file);
        assertEquals(defaults, config.loadOrCreate(defaults));
        String initial = Files.readString(file);
        assertTrue(initial.contains("\n  \"types\": ["));
        assertTrue(initial.contains("\"components\": {"));

        Files.writeString(file, """
                {"schemaVersion":1,"types":[
                  {"id":"example:camp","label":"Override","description":"A replacement.","recommendedChests":1,"refillMinutes":0,"entries":[]},
                  {"id":"custom:store","label":"Custom","description":"A custom cache.","recommendedChests":4,"refillMinutes":2.5,"entries":[{"item":"minecraft:apple","chancePercent":100,"minCount":1,"maxCount":1}]}
                ]}
                """);
        Map<String, LootType> loaded = config.loadOrCreate(defaults);
        assertEquals(3, loaded.size());
        assertEquals("Override", loaded.get("example:camp").label());
        assertEquals("Mine", loaded.get("example:mine").label());
        assertEquals("{}", loaded.get("custom:store").entries().getFirst().componentsJson());
        assertThrows(UnsupportedOperationException.class, () -> loaded.clear());
    }

    @Test void rejectsStrictInvalidConfigWithoutOverwritingIt() throws Exception {
        Path file = directory.resolve("loot.json");
        String invalid = "{\"schemaVersion\":1,\"types\":[],\"types\":[]}";
        Files.writeString(file, invalid);
        IOException failure = assertThrows(IOException.class, () -> new LootConfig(file).loadOrCreate(Map.of("example:camp", type("example:camp", "Default"))));
        assertTrue(failure.getMessage().contains("duplicate"));
        assertEquals(invalid, Files.readString(file));

        String unknown = "{\"schemaVersion\":1,\"types\":[],\"unexpected\":true}";
        Files.writeString(file, unknown);
        assertThrows(IOException.class, () -> new LootConfig(file).loadOrCreate(Map.of()));
        assertEquals(unknown, Files.readString(file));
    }
    @Test void replacesOneWholeTypeAtomicallyWhileKeepingCustomTypesComponentsAndBackup() throws Exception {
        Path file = directory.resolve("loot.json");
        LootType camp = type("example:camp", "Camp");
        LootType mine = type("example:mine", "Mine");
        LootType custom = new LootType("custom:store", "Store", "Kept exactly as configured.", 4, 12.5,
                List.of(new LootEntry("minecraft:apple", 40, 2, 3, "{\"minecraft:custom_name\":\"Store apple\"}")));
        Map<String, LootType> defaults = Map.of(camp.id(), camp, mine.id(), mine);
        LootConfig config = new LootConfig(file);
        config.loadOrCreate(defaults);

        Files.writeString(file, """
                {"schemaVersion":1,"types":[
                  {"id":"example:camp","label":"Camp","description":"A useful cache.","recommendedChests":3,"refillMinutes":30,"entries":[{"item":"minecraft:bread","chancePercent":75,"minCount":1,"maxCount":2,"components":{"minecraft:custom_name":"Rations"}}]},
                  {"id":"example:mine","label":"Mine","description":"A useful cache.","recommendedChests":3,"refillMinutes":30,"entries":[{"item":"minecraft:bread","chancePercent":75,"minCount":1,"maxCount":2,"components":{"minecraft:custom_name":"Rations"}}]},
                  {"id":"custom:store","label":"Store","description":"Kept exactly as configured.","recommendedChests":4,"refillMinutes":12.5,"entries":[{"item":"minecraft:apple","chancePercent":40,"minCount":2,"maxCount":3,"components":{"minecraft:custom_name":"Store apple"}}]}
                ]}
                """);
        Map<String, LootType> initial = config.loadOrCreate(defaults);
        String before = Files.readString(file);
        LootType replacement = new LootType("example:camp", "Camp supplies", "Fresh supplies with two independent rolls.", 7, 2.5,
                List.of(new LootEntry("minecraft:bread", 100, 1, 4, "{\"minecraft:custom_name\":\"Rations\"}"),
                        new LootEntry("minecraft:torch", 35.5, 3, 8, "{\"minecraft:custom_data\":{\"chronicle\":\"camp\"}}")));

        Map<String, LootType> saved = config.replaceType(replacement, initial.get("example:camp"));
        assertEquals(replacement, saved.get("example:camp"));
        assertEquals(mine, saved.get("example:mine"));
        assertEquals(custom, saved.get("custom:store"));
        assertEquals(saved, new LootConfig(file).loadOrCreate(defaults));
        assertEquals(before, Files.readString(file.resolveSibling("loot.json.bak")));
        assertEquals(initial, new LootConfig(file.resolveSibling("loot.json.bak")).loadOrCreate(defaults));
    }

    @Test void refusesStaleExternalAndFailedReplacementWithoutPublishingNewState() throws Exception {
        Path file = directory.resolve("loot.json");
        LootType original = type("example:camp", "Camp");
        LootType replacement = type("example:camp", "Edited");
        LootConfig config = new LootConfig(file);
        config.loadOrCreate(Map.of(original.id(), original));
        assertThrows(IllegalStateException.class, () -> config.replaceType(replacement, type("example:camp", "Different editor")));

        String originalBytes = Files.readString(file);
        Files.writeString(file, originalBytes + "\n");
        IOException external = assertThrows(IOException.class, () -> config.replaceType(replacement, original));
        assertTrue(external.getMessage().contains("changed outside Chronicle"));
        assertEquals(originalBytes + "\n", Files.readString(file));

        Files.writeString(file, originalBytes);
        Path backup = file.resolveSibling("loot.json.bak");
        Files.createDirectory(backup);
        Files.writeString(backup.resolve("keep"), "do not replace this directory");
        assertThrows(IOException.class, () -> config.replaceType(replacement, original));
        assertEquals(originalBytes, Files.readString(file));
        // A failed backup move must leave the observed table untouched so a safe retry works.
        Files.delete(backup.resolve("keep"));
        Files.delete(backup);
        assertEquals(replacement, config.replaceType(replacement, original).get(replacement.id()));
    }
}
