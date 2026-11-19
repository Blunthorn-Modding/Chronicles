package io.github.chronicle.core.voice;

import io.github.chronicle.core.authoring.AuthoringRegistry;
import io.github.chronicle.core.authoring.AuthoringSite;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable, validated geometry for the world's voice rooms. */
public final class VoiceRoomCatalog {
    private final Map<String, AuthoringSite.Region> regions;

    public VoiceRoomCatalog(Map<String, VoiceRoomStore.Binding> bindings, AuthoringRegistry registry) {
        if (bindings == null || registry == null || bindings.size() > 1024)
            throw new IllegalArgumentException("Invalid voice room definitions");
        Map<String, AuthoringSite.Region> resolved = new LinkedHashMap<>();
        for (var entry : new TreeMap<>(bindings).entrySet()) {
            var binding = entry.getValue();
            if (binding == null || !entry.getKey().equals(binding.regionId()))
                throw new IllegalArgumentException("Voice room key mismatch");
            var site = registry.sites().get(binding.regionId());
            if (!(site instanceof AuthoringSite.Region region) || region.revision() != binding.regionRevision())
                throw new IllegalArgumentException("Voice room Region is missing or has changed: " + binding.regionId());
            for (var other : resolved.values()) {
                if (overlaps(region, other)) throw new IllegalArgumentException("Voice room Regions overlap");
            }
            resolved.put(entry.getKey(), region);
        }
        regions = Collections.unmodifiableMap(resolved);
    }

    public Map<String, AuthoringSite.Region> regions() { return regions; }

    public Optional<AuthoringSite.Region> at(String dimension, double x, double y, double z) {
        if (dimension == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return Optional.empty();
        return regions.values().stream().filter(region -> region.dimension().equals(dimension)
                && x >= region.min().x() && x < (long) region.max().x() + 1
                && y >= region.min().y() && y < (long) region.max().y() + 1
                && z >= region.min().z() && z < (long) region.max().z() + 1).findFirst();
    }

    public static boolean overlaps(AuthoringSite.Region a, AuthoringSite.Region b) {
        return a.dimension().equals(b.dimension())
                && a.min().x() <= b.max().x() && b.min().x() <= a.max().x()
                && a.min().y() <= b.max().y() && b.min().y() <= a.max().y()
                && a.min().z() <= b.max().z() && b.min().z() <= a.max().z();
    }
}
