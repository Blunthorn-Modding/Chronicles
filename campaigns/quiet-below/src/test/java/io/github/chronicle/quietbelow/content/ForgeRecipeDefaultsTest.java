package io.github.chronicle.quietbelow.content;

import com.google.gson.JsonParser;
import io.github.chronicle.core.loot.LootConfig;
import io.github.chronicle.core.loot.LootEntry;
import io.github.chronicle.core.loot.LootRoller;
import io.github.chronicle.core.loot.LootType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ForgeRecipeDefaultsTest {
    @TempDir Path directory;

    @Test void preservesBundledRecipeContractAndReadableHints() {
        assertEquals(Map.of("echo_shard", "sharpness", "amethyst_shard", "unbreaking",
                "prismarine_shard", "protection", "flint", "power", "feather", "feather_falling"),
                ForgeRecipeDefaults.HINTS.stream().collect(java.util.stream.Collectors.toMap(
                        ForgeRecipeDefaults.Hint::ingredient, ForgeRecipeDefaults.Hint::enchantment)));
        for (var hint : ForgeRecipeDefaults.HINTS) {
            var recipe = hint.recipe();
            assertEquals("quiet_below:" + hint.ingredient(), recipe.id());
            assertEquals("quiet_below:forge", recipe.group());
            assertEquals("minecraft:" + hint.ingredient(), recipe.ingredient());
            assertEquals("minecraft:" + hint.enchantment(), recipe.enchantment());
            assertEquals(1, recipe.level());
            assertEquals(1, recipe.count());
            assertTrue(recipe.enabled());
        }
        var book = JsonParser.parseString(ForgeRecipeDefaults.bookComponents()).getAsJsonObject()
                .getAsJsonObject("minecraft:written_book_content");
        assertEquals(6, book.getAsJsonArray("pages").size());
        for (int i = 0; i < 6; i++) assertEquals(ForgeRecipeDefaults.pages().get(i),
                book.getAsJsonArray("pages").get(i).getAsJsonObject().get("text").getAsString());
    }

    @Test void notesAreOptionalAndIndependentWithSingleBookComponents() {
        LootType defaults = ForgeRecipeDefaults.notesType();
        assertEquals(0, defaults.recommendedChests());
        assertEquals(0, defaults.refillMinutes());
        assertEquals(20, defaults.entries().getFirst().chancePercent());
        for (double chance : new double[]{0, 100}) {
            LootType type = new LootType(defaults.id(), defaults.label(), defaults.description(), 0, 0,
                    List.of(new LootEntry("minecraft:written_book", chance, 1, 1, ForgeRecipeDefaults.bookComponents())));
            for (int seed = 0; seed < 100; seed++) {
                var result = LootRoller.roll(type, new Random(seed));
                assertEquals(chance == 0 ? 0 : 1, result.size());
                if (!result.isEmpty()) {
                    assertEquals(1, result.getFirst().count());
                    assertEquals(ForgeRecipeDefaults.bookComponents(), result.getFirst().entry().componentsJson());
                }
            }
        }
    }

    @Test void newDefaultDoesNotRewriteExistingConfigAndConfiguredNotesWin() throws Exception {
        Path path = directory.resolve("loot.json");
        String old = "{\"schemaVersion\":1,\"types\":[]}\n";
        Files.writeString(path, old);
        var defaults = ForgeRecipeDefaults.notesType();
        assertEquals(defaults, new LootConfig(path).loadOrCreate(Map.of(defaults.id(), defaults)).get(defaults.id()));
        assertEquals(old, Files.readString(path));
        LootType custom = new LootType(defaults.id(), "Organizer notes", "Custom", 0, 0,
                List.of(new LootEntry("minecraft:written_book", 100, 1, 1,
                        "{\"minecraft:written_book_content\":{\"title\":\"Custom notes\",\"author\":\"Organizer\",\"pages\":[\"Custom hint\"]}}")));
        Files.delete(path);
        new LootConfig(path).loadOrCreate(Map.of(custom.id(), custom));
        String configured = Files.readString(path);
        assertEquals(custom, new LootConfig(path).loadOrCreate(Map.of(defaults.id(), defaults)).get(defaults.id()));
        assertEquals(configured, Files.readString(path));
    }
}
