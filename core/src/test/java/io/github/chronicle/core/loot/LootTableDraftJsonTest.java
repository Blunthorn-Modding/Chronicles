package io.github.chronicle.core.loot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LootTableDraftJsonTest {
    @Test void roundTripsAllEditableFieldsAndComponentObjectsWithoutDroppingEntries() {
        LootType original = new LootType("quiet_below:ancient_cache", "Ancient cache", "A full editor payload.", 12, 4.5,
                List.of(new LootEntry("minecraft:bread", 67.5, 1, 3, "{\"minecraft:custom_name\":\"Rations\"}"),
                        new LootEntry("minecraft:echo_shard", 10, 1, 2, "{\"minecraft:custom_data\":{\"chronicle\":\"quiet_below\"}}")));
        String encoded = LootTableDraftJson.encode(original);
        assertEquals(original, LootTableDraftJson.decode(encoded));
    }

    @Test void rejectsUnknownOrIncompleteFieldsRatherThanSilentlyStrippingThem() {
        assertThrows(IllegalArgumentException.class, () -> LootTableDraftJson.decode("""
                {"id":"example:cache","label":"Cache","description":"A cache.","recommendedChests":0,"refillMinutes":0,
                "entries":[{"item":"minecraft:bread","chancePercent":100,"minCount":1,"maxCount":1,"components":{},"extra":true}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> LootTableDraftJson.decode("""
                {"id":"example:cache","label":"Cache","description":"A cache.","recommendedChests":0,"refillMinutes":0,"entries":[]}
                trailing
                """));
    }
}
