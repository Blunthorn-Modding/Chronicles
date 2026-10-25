package io.github.chronicle.core.loot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LootEntryLabelTest {
    private LootEntry book(String content) {
        return new LootEntry("minecraft:written_book", 20, 1, 1,
                "{\"minecraft:written_book_content\":" + content + "}");
    }
    @Test void usesPlainOrRawVanillaTitleWithoutExposingPages() {
        assertEquals("Field journal I", LootEntryLabel.of(book("{\"title\":\"Field journal I\",\"pages\":[\"Secret\"]}")));
        assertEquals("Field journal II", LootEntryLabel.of(book("{\"title\":{\"raw\":\"Field journal II\",\"filtered\":\"Other\"}}")));
    }
    @Test void fallsBackForAbsentInvalidOrNonBookComponents() {
        for (String content : new String[]{"{}", "null", "[]", "{\"title\":{}}", "{\"title\":17}",
                "{\"title\":\"\"}", "{\"title\":\"a\\nb\"}", "{\"title\":\"" + "x".repeat(33) + "\"}"})
            assertEquals("minecraft:written_book", LootEntryLabel.of(book(content)));
        assertEquals("minecraft:written_book", LootEntryLabel.of(new LootEntry("minecraft:written_book", 20, 1, 1, "{}")));
        assertEquals("minecraft:bread", LootEntryLabel.of(new LootEntry("minecraft:bread", 20, 1, 1, "{}")));
    }
}
