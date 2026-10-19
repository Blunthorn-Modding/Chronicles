package io.github.chronicle.core.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Pure random selection; chest placement and registry-aware stack construction belong to the runtime layer. */
public final class LootRoller {
    private LootRoller() { }

    public record RolledEntry(LootEntry entry, int count) { }

    public static List<RolledEntry> roll(LootType type, RandomGenerator random) {
        if (type == null || random == null) throw new IllegalArgumentException("loot type and random generator are required");
        List<RolledEntry> result = new ArrayList<>();
        for (LootEntry entry : type.entries()) {
            if (random.nextDouble() * 100 < entry.chancePercent()) {
                int count = entry.minCount() + random.nextInt(entry.maxCount() - entry.minCount() + 1);
                result.add(new RolledEntry(entry, count));
            }
        }
        return List.copyOf(result);
    }
}
