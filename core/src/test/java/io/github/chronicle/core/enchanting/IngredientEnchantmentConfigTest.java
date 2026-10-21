package io.github.chronicle.core.enchanting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IngredientEnchantmentConfigTest {
    @TempDir Path directory;

    @Test void createsDefaultsButExistingFileIsACompleteOverrideAndRoundTrips() throws Exception {
        Path file = directory.resolve("config/ingredient-enchantments.json");
        List<IngredientEnchantmentRecipe> defaults = List.of(IngredientEnchantmentRecipeTest.recipe("chronicle:echo", "minecraft:echo_shard"), IngredientEnchantmentRecipeTest.recipe("chronicle:flint", "minecraft:flint"));
        IngredientEnchantmentConfig config = new IngredientEnchantmentConfig(file);
        assertEquals(defaults, config.loadOrCreate(defaults));
        assertEquals(defaults, IngredientEnchantmentConfig.decode(IngredientEnchantmentConfig.encode(defaults)));
        Files.writeString(file, "{\"schemaVersion\":1,\"recipes\":[]}");
        assertEquals(List.of(), config.loadOrCreate(defaults));
    }

    @Test void rejectsStrictCorruptionDuplicatesAndBoundsWithoutReplacingSource() throws Exception {
        Path file = directory.resolve("ingredient-enchantments.json");
        String[] invalid = {
                "{\"schemaVersion\":1,\"recipes\":[],\"unknown\":true}",
                "{\"schemaVersion\":1,\"recipes\":[],\"recipes\":[]}",
                "{\"schemaVersion\":1,\"recipes\":[{\"id\":\"chronicle:echo\",\"group\":\"chronicle:forge\",\"ingredient\":\"minecraft:echo_shard\",\"enchantment\":\"minecraft:sharpness\",\"level\":1.5,\"count\":1,\"enabled\":true}]}",
                "{\"schemaVersion\":1,\"recipes\":[{\"id\":\"chronicle:echo\",\"group\":\"chronicle:forge\",\"ingredient\":\"minecraft:echo_shard\",\"enchantment\":\"minecraft:sharpness\",\"level\":1,\"count\":1,\"enabled\":\"true\"}]}",
                "{\"schemaVersion\":1,\"recipes\":[{\"id\":\"chronicle:one\",\"group\":\"chronicle:forge\",\"ingredient\":\"minecraft:echo_shard\",\"enchantment\":\"minecraft:sharpness\",\"level\":1,\"count\":1,\"enabled\":true},{\"id\":\"chronicle:two\",\"group\":\"chronicle:forge\",\"ingredient\":\"minecraft:echo_shard\",\"enchantment\":\"minecraft:unbreaking\",\"level\":1,\"count\":1,\"enabled\":false}]}"
        };
        for (String source : invalid) {
            Files.writeString(file, source);
            assertThrows(IOException.class, () -> new IngredientEnchantmentConfig(file).loadOrCreate(List.of()));
            assertEquals(source, Files.readString(file));
        }
        List<IngredientEnchantmentRecipe> tooMany = new ArrayList<>();
        for (int index = 0; index <= IngredientEnchantmentCatalog.MAX_RECIPES; index++) tooMany.add(IngredientEnchantmentRecipeTest.recipe("chronicle:r" + index, "test:i" + index));
        assertThrows(IllegalArgumentException.class, () -> IngredientEnchantmentConfig.encode(tooMany));
        String oversized = " ".repeat((int) IngredientEnchantmentConfig.MAX_FILE_BYTES + 1);
        Files.writeString(file, oversized);
        assertThrows(IOException.class, () -> new IngredientEnchantmentConfig(file).loadOrCreate(List.of()));
        assertEquals(oversized.length(), Files.size(file));
    }

    @Test void savesWithObservedByteConcurrencyAndValidBackup() throws Exception {
        Path file = directory.resolve("ingredient-enchantments.json");
        IngredientEnchantmentConfig config = new IngredientEnchantmentConfig(file);
        List<IngredientEnchantmentRecipe> initial = config.loadOrCreate(List.of(IngredientEnchantmentRecipeTest.recipe("chronicle:echo", "minecraft:echo_shard")));
        String before = Files.readString(file);
        List<IngredientEnchantmentRecipe> replacement = List.of(IngredientEnchantmentRecipeTest.recipe("chronicle:flint", "minecraft:flint"));
        assertEquals(replacement, config.save(replacement, initial));
        assertEquals(before, Files.readString(file.resolveSibling("ingredient-enchantments.json.bak")));
        assertEquals(initial, new IngredientEnchantmentConfig(file.resolveSibling("ingredient-enchantments.json.bak")).loadOrCreate(List.of()));

        String saved = Files.readString(file);
        Files.writeString(file, saved + "\n");
        assertThrows(IOException.class, () -> config.save(initial, replacement));
        assertEquals(saved + "\n", Files.readString(file));
        assertThrows(IllegalStateException.class, () -> config.save(initial, initial));
    }
}
