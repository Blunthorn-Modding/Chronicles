package io.github.chronicle.core.spawn;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Immutable bounded registry of reusable spawn bindings for one world. */
public record SpawnRegistry(long revision, Map<String, SpawnBinding> bindings) {
    public static final int MAX_BINDINGS = 256;
    public SpawnRegistry {
        if (revision < 0) throw new IllegalArgumentException("spawn registry revision must not be negative");
        if (bindings == null || bindings.size() > MAX_BINDINGS) throw new IllegalArgumentException("spawn registry may contain at most " + MAX_BINDINGS + " bindings");
        var ordered = new TreeMap<String, SpawnBinding>();
        bindings.forEach((key, binding) -> {
            if (binding == null || !key.equals(binding.purpose())) throw new IllegalArgumentException("binding key must match its purpose");
            ordered.put(key, binding);
        });
        bindings = Collections.unmodifiableMap(ordered);
    }
    public static SpawnRegistry empty() { return new SpawnRegistry(0, Map.of()); }
}
