package io.github.chronicle.core.loot;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Startup-only registry of campaign-supplied loot types. */
public final class LootCatalog {
    public static final int MAX_TYPES = 128;
    private final TreeMap<String, LootType> types = new TreeMap<>();
    private boolean frozen;

    public synchronized void register(LootType type) {
        if (frozen) throw new IllegalStateException("Loot catalog is frozen");
        if (type == null) throw new IllegalArgumentException("loot type is required");
        if (types.containsKey(type.id())) throw new IllegalArgumentException("duplicate loot type: " + type.id());
        if (types.size() >= MAX_TYPES) throw new IllegalArgumentException("loot catalog accepts at most " + MAX_TYPES + " types");
        types.put(type.id(), type);
    }

    public synchronized void freeze() { frozen = true; }

    public synchronized Map<String, LootType> types() {
        return Collections.unmodifiableMap(new TreeMap<>(types));
    }
}
