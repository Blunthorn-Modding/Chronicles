package io.github.chronicle.core.offering.temporary;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Immutable terms accepted by one temporary offering. Campaign-specific prices stay bounded evidence. */
public record TemporaryOfferingTerms(long durationTicks, int health, int exhaustionPercent,
                                     Map<String, String> evidence) {
    public static final int MAX_EVIDENCE = 32;
    public TemporaryOfferingTerms {
        if (durationTicks < 0 || durationTicks > 20L * 60 * 60 * 24 * 30)
            throw new IllegalArgumentException("temporary offering duration is outside the supported range");
        if (health < -1024 || health > 1024 || exhaustionPercent < -100 || exhaustionPercent > 25_500)
            throw new IllegalArgumentException("temporary offering modifiers are outside the supported range");
        if (evidence == null || evidence.size() > MAX_EVIDENCE)
            throw new IllegalArgumentException("temporary offering evidence exceeds " + MAX_EVIDENCE + " entries");
        TreeMap<String, String> ordered = new TreeMap<>();
        for (var entry : evidence.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[a-z][a-z0-9_.-]{0,63}")
                    || entry.getValue() == null || entry.getValue().length() > 2048 || entry.getValue().indexOf('\0') >= 0)
                throw new IllegalArgumentException("invalid temporary offering evidence");
            ordered.put(entry.getKey(), entry.getValue());
        }
        evidence = Collections.unmodifiableMap(ordered);
    }
}
