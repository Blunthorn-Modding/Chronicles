package io.github.chronicle.core.managed;

import io.github.chronicle.core.authoring.AuthoringSite;
import java.util.Map;
import java.util.TreeMap;

public record ManagedBlockLimits(long revision, Map<String, Integer> overrides) {
    public ManagedBlockLimits {
        if (revision < 0) throw new IllegalArgumentException("managed limit revision cannot be negative");
        if (overrides == null || overrides.size() > ManagedBlockCatalog.MAX_TYPES)
            throw new IllegalArgumentException("too many managed limit overrides");
        TreeMap<String, Integer> copy = new TreeMap<>();
        for (var entry : overrides.entrySet()) {
            if (entry.getKey() == null || !AuthoringSite.NAMESPACED_ID.matcher(entry.getKey()).matches())
                throw new IllegalArgumentException("managed limit type must be namespaced");
            Integer value = entry.getValue();
            if (value == null || value < 0 || value > ManagedBlockDefinition.MAX_LIMIT)
                throw new IllegalArgumentException("managed limit must be 0-" + ManagedBlockDefinition.MAX_LIMIT);
            copy.put(entry.getKey(), value);
        }
        overrides = Map.copyOf(copy);
    }

    public static ManagedBlockLimits empty() { return new ManagedBlockLimits(0, Map.of()); }
}
