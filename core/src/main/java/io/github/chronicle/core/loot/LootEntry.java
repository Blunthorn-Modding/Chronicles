package io.github.chronicle.core.loot;

import java.util.regex.Pattern;

/** One independently rolled item entry in a loot type. Minecraft registry validation happens at binding time. */
public record LootEntry(String item, double chancePercent, int minCount, int maxCount, String componentsJson) {
    private static final Pattern NAMESPACED_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public LootEntry {
        if (item == null || !NAMESPACED_ID.matcher(item).matches()) throw new IllegalArgumentException("loot item must be a namespaced identifier");
        if (!Double.isFinite(chancePercent) || chancePercent < 0 || chancePercent > 100) throw new IllegalArgumentException("loot chancePercent must be finite and between 0 and 100");
        if (minCount < 1 || minCount > 64 || maxCount < 1 || maxCount > 64 || minCount > maxCount) throw new IllegalArgumentException("loot counts must be between 1 and 64 with minCount no greater than maxCount");
        if (componentsJson == null) componentsJson = "{}";
        LootJson.object(componentsJson, "loot components");
    }
}
