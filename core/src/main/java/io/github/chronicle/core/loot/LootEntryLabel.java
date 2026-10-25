package io.github.chronicle.core.loot;

/** Plain book titles distinguish component-bearing loot entries without exposing their pages. */
public final class LootEntryLabel {
    private LootEntryLabel() { }
    public static String of(LootEntry entry) {
        if (!entry.item().equals("minecraft:written_book")) return entry.item();
        try {
            var title = LootJson.object(entry.componentsJson(), "loot components")
                    .getAsJsonObject("minecraft:written_book_content").get("title");
            if (title.isJsonObject()) title = title.getAsJsonObject().get("raw");
            if (!title.isJsonPrimitive() || !title.getAsJsonPrimitive().isString()) return entry.item();
            String label = title.getAsString();
            if (!label.isBlank() && label.length() <= 32 && label.codePoints().noneMatch(Character::isISOControl)) return label;
        } catch (RuntimeException ignored) { /* Incomplete local draft; registry validation remains server-owned. */ }
        return entry.item();
    }
}
