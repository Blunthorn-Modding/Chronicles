package io.github.chronicle.core.enchanting;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Immutable server policy for reducing stored enchantment levels. */
public record EnchantmentCaps(boolean enabled, int defaultMaxLevel, Map<String, Integer> overrides) {
    public static final int MAX_OVERRIDES = 1024;
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 255;
    public static final EnchantmentCaps DEFAULT = new EnchantmentCaps(false, MAX_LEVEL, Map.of());
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public EnchantmentCaps {
        validLevel(defaultMaxLevel, "default maximum level");
        if (overrides == null) throw new IllegalArgumentException("enchantment cap overrides are required");
        if (overrides.size() > MAX_OVERRIDES) throw new IllegalArgumentException("enchantment cap overrides may contain at most " + MAX_OVERRIDES + " entries");
        TreeMap<String, Integer> copy = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : overrides.entrySet()) {
            String id = entry.getKey();
            Integer level = entry.getValue();
            validId(id);
            if (level == null) throw new IllegalArgumentException("enchantment cap for " + id + " is required");
            validLevel(level, "enchantment cap for " + id);
            copy.put(id, level);
        }
        overrides = Collections.unmodifiableMap(copy);
    }

    /** Applies the active maximum without ever raising a vanilla maximum. */
    public int cap(String id, int vanillaMax) {
        validId(id);
        return enabled ? Math.min(vanillaMax, overrides.getOrDefault(id, defaultMaxLevel)) : vanillaMax;
    }

    /** Applies a permanent stored-level reduction; disabled policies leave levels untouched. */
    public int storedLevel(String id, int currentLevel) {
        validId(id);
        return enabled ? Math.min(currentLevel, overrides.getOrDefault(id, defaultMaxLevel)) : currentLevel;
    }

    static void validId(String id) {
        if (id == null || id.length() > 256 || !ID.matcher(id).matches()) throw new IllegalArgumentException("enchantment id must be a bounded lowercase namespaced id: " + id);
    }

    static void validLevel(int level, String field) {
        if (level < MIN_LEVEL || level > MAX_LEVEL) throw new IllegalArgumentException(field + " must be between " + MIN_LEVEL + " and " + MAX_LEVEL);
    }
}
