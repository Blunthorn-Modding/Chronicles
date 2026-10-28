package io.github.chronicle.core.play;

import java.util.*;

/** Durable arming, discrete-trigger and countdown pins; the occupied play clock owns time. */
public record EventProgressState(long revision, Map<String, Entry> entries) {
    public EventProgressState {
        if (revision < 0 || entries == null || entries.size() > PlayState.MAX_EVENTS)
            throw new IllegalArgumentException("Invalid Event progression state");
        var copy = new TreeMap<String, Entry>();
        entries.forEach((id, value) -> {
            if (value == null || !id.equals(value.eventId())) throw new IllegalArgumentException("Event progression key mismatch");
            copy.put(id, value);
        });
        entries = Collections.unmodifiableMap(copy);
    }

    public static EventProgressState empty() { return new EventProgressState(0, Map.of()); }

    public record Entry(String eventId, String fingerprint, long startedAt, boolean triggered,
                        UUID actor, PlayState.DeliverySource source, String occurrence, boolean activeOrigin,
                        Set<String> seenFacts, Map<String, Long> offVersions, boolean skipped) {
        public static final int MAX_FACTS = 1024;
        public Entry {
            ObservationCatalog.requireIdentifier(eventId, "Event progression ID");
            if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}") || startedAt < -1 || source == null
                    || occurrence == null || occurrence.length() > 256 || occurrence.indexOf('\0') >= 0
                    || seenFacts == null || seenFacts.size() > MAX_FACTS || offVersions == null || offVersions.size() > 32)
                throw new IllegalArgumentException("Invalid Event activation");
            var facts = new TreeSet<String>();
            for (String id : seenFacts) {
                if (id == null || id.isBlank() || id.length() > 256 || id.indexOf('\0') >= 0)
                    throw new IllegalArgumentException("Invalid Event source receipt");
                facts.add(id);
            }
            seenFacts = Collections.unmodifiableSet(facts);
            var versions = new TreeMap<String, Long>();
            offVersions.forEach((id, version) -> {
                ObservationCatalog.requireIdentifier(id, "Event required flag");
                if (version == null || version < 0) throw new IllegalArgumentException("Invalid flag reset version");
                versions.put(id, version);
            });
            offVersions = Collections.unmodifiableMap(versions);
            if (!triggered && startedAt != -1) throw new IllegalArgumentException("An untriggered Event cannot have a countdown");
            if (activeOrigin && (source != PlayState.DeliverySource.SYSTEM || occurrence.isBlank()))
                throw new IllegalArgumentException("Trusted completion requires an originating system receipt");
        }
    }
}
