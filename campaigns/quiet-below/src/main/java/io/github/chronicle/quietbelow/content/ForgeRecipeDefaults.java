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
    public record Hint(String ingredient, String enchantment, String title, List<String> pages) {
        public Hint { pages = List.copyOf(pages); }
        public IngredientEnchantmentRecipe recipe() {
            return new IngredientEnchantmentRecipe("quiet_below:" + ingredient, GROUP,
                    "minecraft:" + ingredient, "minecraft:" + enchantment, 1, 1, true);
        }
    }
    public static final List<Hint> HINTS = List.of(
            new Hint("echo_shard", "sharpness", "Field journal I", List.of(
                    "I found the stair below a seam of dark stone. The buildings beyond it were old long before my arrival. I do not know who laid them out.",
                    "I have built a small forge beside the growth. Ordinary coals did nothing. It warmed only after the blue threads reached its base.",
                    "Today I lowered a chipped blade and one of the echoing fragments into the basin. I expected slag. The blade survived, and cut deeper than before.")),
            new Hint("amethyst_shard", "unbreaking", "Field journal II", List.of(
                    "I have been experimenting with the purple crystals. A sliver of amethyst went into the forge with the pick I have been wearing down in the lower galleries.",
                    "The pick looks no different. After days of cutting the same stone, its edge is holding better than the others. I have set aside another crystal to repeat the trial.",
                    "I have begun shaping a second stone station. The forge answers objects. I want to know whether a similar arrangement can answer a person.")),
            new Hint("prismarine_shard", "protection", "Field journal III", List.of(
                    "The sea-green stone I brought below has survived every heat test. This morning I put a shard into the basin with a spare piece of iron armour.",
                    "The plates have not thickened. I put them on for the next test. A blow that bruised my shoulder yesterday barely troubled me today. I checked the padding; it has not changed.",
                    "The second station is finished. I have taken to calling it an altar, although there is no one here to dedicate it to. I built it from my own trials, not a plan from the city.")),
            new Hint("flint", "power", "Field journal IV", List.of(
                    "Most of the gravel flakes vanished without a useful result. I nearly threw the last piece of flint away, then tried it with the bow I keep beside the stores.",
                    "The string draws as it did before. But arrows from this bow now bury themselves farther into the same target. I must mark the distance before the next trial.",
                    "The growth has reached the stores. Clearing it takes longer each morning. I need it near the forge, but I can no longer keep my work separate from it.")),
            new Hint("feather", "feather_falling", "Field journal V", List.of(
                    "I tried something lighter today: a fallen feather, with the boots I wear on the upper walkways. Both went into the basin. Only the boots came back.",
                    "I tested them from the lowest ledge first. The landing hurt less than it should. I am not willing to trust them from the broken bridge.",
                    "I am leaving at first light. I cannot keep the workshop clear and continue the trials. The forge and altar must stay. I will leave these volumes here rather than carry all of them.")));
    private ForgeRecipeDefaults() { }

    public static String bookComponents(Hint hint) {
        JsonObject book = new JsonObject();
        book.addProperty("title", hint.title());
        book.addProperty("author", "Unknown");
        book.addProperty("generation", 0);
        book.addProperty("resolved", true);
        JsonArray pages = new JsonArray();
        for (String page : hint.pages()) {
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
        return new LootType(NOTES_TYPE, "Research journals", "Abandoned experiments by a later explorer of the ancient city.", 0, 0,
                List.of(), java.util.stream.IntStream.range(0,HINTS.size()).mapToObj(i -> new io.github.chronicle.core.books.BookChance("quiet_below:field_journal_"+List.of("i","ii","iii","iv","v").get(i),4)).toList());
    }
}
