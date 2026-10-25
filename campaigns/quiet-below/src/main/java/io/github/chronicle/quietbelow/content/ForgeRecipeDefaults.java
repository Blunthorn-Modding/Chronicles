package io.github.chronicle.quietbelow.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.enchanting.IngredientEnchantmentRecipe;
import io.github.chronicle.core.loot.LootEntry;
import io.github.chronicle.core.loot.LootType;
import java.util.List;

/** Campaign recommendations shared by Forge registration and optional discovery loot. */
public final class ForgeRecipeDefaults {
    public static final String GROUP = "quiet_below:forge";
    public static final String NOTES_TYPE = "quiet_below:forge_notes";
    public static final String TITLE = "Forge notes";
    public record Hint(String ingredient, String enchantment, String page) {
        public IngredientEnchantmentRecipe recipe() {
            return new IngredientEnchantmentRecipe("quiet_below:" + ingredient, GROUP,
                    "minecraft:" + ingredient, "minecraft:" + enchantment, 1, 1, true);
        }
    }
    public static final List<Hint> HINTS = List.of(
            new Hint("echo_shard", "sharpness", "A sharper edge\n\nAn echo shard and a sword or axe.\n\nThe edge remembers."),
            new Hint("amethyst_shard", "unbreaking", "A lasting tool\n\nAn amethyst shard and equipment that wears with use.\n\nLet it endure."),
            new Hint("prismarine_shard", "protection", "A guarded body\n\nA prismarine shard and a piece of armour.\n\nSomething stands between."),
            new Hint("flint", "power", "A stronger bow\n\nFlint and a bow.\n\nLet the arrow strike harder."),
            new Hint("feather", "feather_falling", "A gentler fall\n\nA feather and a pair of boots.\n\nLet the ground wait."));
    private ForgeRecipeDefaults() { }

    public static List<String> pages() {
        var pages = new java.util.ArrayList<String>();
        pages.add("Forge notes\n\nWake the Forge.\nOffer two items,\none at a time.\n\nEither order works.\n\nSome offerings\nnever return.");
        for (Hint hint : HINTS) pages.add(hint.page());
        return List.copyOf(pages);
    }

    public static String bookComponents() {
        JsonObject book = new JsonObject();
        book.addProperty("title", TITLE);
        book.addProperty("author", "Unknown");
        book.addProperty("generation", 0);
        book.addProperty("resolved", true);
        JsonArray pages = new JsonArray();
        for (String page : pages()) {
            JsonObject text = new JsonObject();
            text.addProperty("text", page);
            pages.add(text);
        }
        book.add("pages", pages);
        JsonObject components = new JsonObject();
        components.add("minecraft:written_book_content", book);
        return components.toString();
    }

    public static LootType notesType() {
        return new LootType(NOTES_TYPE, TITLE, "Optional scraps of knowledge about the Ancient Forge.", 0, 0,
                List.of(new LootEntry("minecraft:written_book", 20, 1, 1, bookComponents())));
    }
}
