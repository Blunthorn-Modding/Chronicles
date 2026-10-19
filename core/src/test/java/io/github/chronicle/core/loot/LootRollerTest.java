package io.github.chronicle.core.loot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LootRollerTest {
    @Test void rollsBoundariesIndependentlyAndUsesInclusiveCounts() {
        LootEntry never = new LootEntry("minecraft:dirt", 0, 1, 1, "{}");
        LootEntry always = new LootEntry("minecraft:bread", 100, 2, 4, "{}");
        LootType type = new LootType("example:cache", "Cache", "A cache.", 0, 0, List.of(never, always));
        for (int seed = 0; seed < 100; seed++) {
            List<LootRoller.RolledEntry> result = LootRoller.roll(type, new Random(seed));
            assertEquals(1, result.size());
            assertSame(always, result.getFirst().entry());
            assertTrue(result.getFirst().count() >= 2 && result.getFirst().count() <= 4);
        }
    }

    @Test void producesReproducibleIndependentRolls() {
        LootType type = new LootType("example:cache", "Cache", "A cache.", 0, 0, List.of(
                new LootEntry("minecraft:stick", 60, 1, 3, "{}"),
                new LootEntry("minecraft:bread", 60, 1, 3, "{}"),
                new LootEntry("minecraft:apple", 60, 1, 3, "{}")));
        assertEquals(LootRoller.roll(type, new Random(781)), LootRoller.roll(type, new Random(781)));

        boolean sawOnlyOne = false;
        boolean sawMoreThanOne = false;
        for (int seed = 0; seed < 200; seed++) {
            int size = LootRoller.roll(type, new Random(seed)).size();
            sawOnlyOne |= size == 1;
            sawMoreThanOne |= size > 1;
        }
        assertTrue(sawOnlyOne);
        assertTrue(sawMoreThanOne);
    }
}
