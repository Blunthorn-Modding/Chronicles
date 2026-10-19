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
}
