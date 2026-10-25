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
        assertEquals(5, ForgeRecipeDefaults.HINTS.stream().map(ForgeRecipeDefaults.Hint::title).distinct().count());
        for (var hint : ForgeRecipeDefaults.HINTS) {
            var book = JsonParser.parseString(ForgeRecipeDefaults.bookComponents(hint)).getAsJsonObject()
                    .getAsJsonObject("minecraft:written_book_content");
            assertEquals(hint.title(), book.get("title").getAsString());
            assertEquals(3, book.getAsJsonArray("pages").size());
            for (int i = 0; i < 3; i++) assertEquals(hint.pages().get(i),
                    book.getAsJsonArray("pages").get(i).getAsJsonObject().get("text").getAsString());
        }
    }

    @Test void notesAreOptionalNamedReferencesWithIndependentChances() {
        LootType defaults = ForgeRecipeDefaults.notesType();
        assertEquals(0, defaults.recommendedChests());
        assertEquals(0, defaults.refillMinutes());
        assertTrue(defaults.entries().isEmpty());
        assertEquals(5, defaults.books().size());
        assertTrue(defaults.books().stream().allMatch(entry -> entry.chancePercent() == 4));
        assertEquals(List.of("quiet_below:field_journal_i","quiet_below:field_journal_ii","quiet_below:field_journal_iii","quiet_below:field_journal_iv","quiet_below:field_journal_v"),defaults.books().stream().map(io.github.chronicle.core.books.BookChance::bookId).toList());
        // Rich ordinary written books remain supported, independently of named references.
        var legacy = new LootType(defaults.id(),defaults.label(),defaults.description(),0,0,
            ForgeRecipeDefaults.HINTS.stream().map(hint -> new LootEntry("minecraft:written_book",100,1,1,ForgeRecipeDefaults.bookComponents(hint))).toList());
        var rolled=LootRoller.roll(legacy,new Random(5));assertEquals(5,rolled.size());
        for(int i=0;i<5;i++)assertEquals(ForgeRecipeDefaults.bookComponents(ForgeRecipeDefaults.HINTS.get(i)),rolled.get(i).entry().componentsJson());
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
