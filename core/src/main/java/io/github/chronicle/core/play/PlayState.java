package io.github.chronicle.core.play;

import io.github.chronicle.core.authoring.AuthoringSite;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable durable participation, active-time and observation-delivery state for one world. */
public record PlayState(long revision, long elapsedTicks, Set<UUID> participants,
                        Map<String, Binding> bindings, Map<String, Delivery> deliveries) {
    public static final int MAX_PARTICIPANTS = 512;
    public static final int MAX_BINDINGS = 128;
    public static final int MAX_DELIVERIES = 512;
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    public PlayState {
        if (revision < 0) throw new IllegalArgumentException("play revision must not be negative");
        if (elapsedTicks < 0) throw new IllegalArgumentException("elapsed play ticks must not be negative");
        participants = participants(participants);
        bindings = bindings(bindings, revision, elapsedTicks);
        deliveries = deliveries(deliveries, elapsedTicks);
    }

    public static PlayState empty() { return new PlayState(0, 0, Set.of(), Map.of(), Map.of()); }

    public enum Mode { MANUAL, ENTRY, TIMER, CONDITION }
    public enum Status { CLAIMED, DELIVERED }
    public enum DeliverySource { MANUAL, ENTRY, TIMER, CONDITION }

    /** A definition and region-revision-pinned optional trigger armed at shared active time. */
    public record Binding(String id, long revision, String definitionFingerprint, Mode mode,
                          String regionId, long regionRevision, long armedAtTicks,
                          long delayTicks, String requiredFlag) {
        public Binding {
            ObservationCatalog.requireIdentifier(id, "observation binding ID");
            if (revision < 1) throw new IllegalArgumentException("observation binding revision must be positive");
            if (definitionFingerprint == null || !FINGERPRINT.matcher(definitionFingerprint).matches()) {
                throw new IllegalArgumentException("observation definition fingerprint must be a lowercase SHA-256 digest");
            }
            if (mode == null) throw new IllegalArgumentException("observation binding mode is required");
            if (regionId == null) throw new IllegalArgumentException("observation region ID is required; use an empty string for no region");
            if (regionId.isEmpty()) {
                if (regionRevision != 0) throw new IllegalArgumentException("an unbound observation region must have revision zero");
            } else {
                if (!AuthoringSite.ID.matcher(regionId).matches()) throw new IllegalArgumentException("observation region ID is invalid");
                if (regionRevision < 1) throw new IllegalArgumentException("a bound observation region must have a positive revision");
            }
            if (mode == Mode.ENTRY && regionId.isEmpty()) throw new IllegalArgumentException("entry observation binding requires a region");
            if (armedAtTicks < 0) throw new IllegalArgumentException("observation armed time must not be negative");
            long maximumDelay = Math.multiplyExact(ObservationConfiguration.MAX_DELAY_SECONDS, 20L);
            if (mode == Mode.TIMER) {
                if (delayTicks < 1 || delayTicks > maximumDelay) throw new IllegalArgumentException("timer delay ticks are outside the supported range");
            } else if (delayTicks != 0) {
                throw new IllegalArgumentException("only timer observations may have delay ticks");
            }
            if (requiredFlag == null) throw new IllegalArgumentException("required flag is required; use an empty string for no prerequisite");
            if (!requiredFlag.isEmpty()) ObservationCatalog.requireIdentifier(requiredFlag, "required flag");
            if (mode == Mode.CONDITION && requiredFlag.isEmpty()) throw new IllegalArgumentException("condition observation binding requires a flag");
        }
    }

    /** A durable before-effect delivery claim. CLAIMED is deliberately uncertain after a crash. */
    public record Delivery(String id, Status status, DeliverySource source, UUID actor,
                           int session, long elapsedTicks, List<UUID> recipients) {
        public Delivery {
            ObservationCatalog.requireIdentifier(id, "observation delivery ID");
            if (status == null) throw new IllegalArgumentException("observation delivery status is required");
            if (source == null) throw new IllegalArgumentException("observation delivery source is required");
            if ((source == DeliverySource.MANUAL || source == DeliverySource.ENTRY) && actor == null) {
                throw new IllegalArgumentException(source.name().toLowerCase(java.util.Locale.ROOT) + " observation delivery requires an actor");
            }
            if (session < 1) throw new IllegalArgumentException("observation delivery session must be positive");
            if (elapsedTicks < 0) throw new IllegalArgumentException("observation delivery time must not be negative");
            if (recipients == null || recipients.isEmpty() || recipients.size() > MAX_PARTICIPANTS) {
                throw new IllegalArgumentException("observation delivery must contain 1-" + MAX_PARTICIPANTS + " recipients");
            }
            TreeSet<UUID> ordered = new TreeSet<>();
            for (UUID recipient : recipients) {
                if (recipient == null || !ordered.add(recipient)) throw new IllegalArgumentException("observation recipients must be distinct UUIDs");
            }
            recipients = List.copyOf(ordered);
        }
    }

    private static Set<UUID> participants(Set<UUID> source) {
        if (source == null || source.size() > MAX_PARTICIPANTS) throw new IllegalArgumentException("participants may contain at most " + MAX_PARTICIPANTS + " entries");
        TreeSet<UUID> ordered = new TreeSet<>();
        for (UUID player : source) {
            if (player == null) throw new IllegalArgumentException("participants must contain UUIDs");
            ordered.add(player);
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(ordered));
    }

    private static Map<String, Binding> bindings(Map<String, Binding> source, long revision, long elapsedTicks) {
        if (source == null || source.size() > MAX_BINDINGS) throw new IllegalArgumentException("observation bindings may contain at most " + MAX_BINDINGS + " entries");
        LinkedHashMap<String, Binding> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, Binding> entry : new TreeMap<>(source).entrySet()) {
            Binding binding = entry.getValue();
            if (binding == null || !entry.getKey().equals(binding.id())) throw new IllegalArgumentException("observation binding key must match its ID");
            if (binding.revision() > revision) throw new IllegalArgumentException("observation binding revision may not exceed play revision");
            if (binding.armedAtTicks() > elapsedTicks) throw new IllegalArgumentException("observation armed time may not exceed elapsed play time");
            ordered.put(entry.getKey(), binding);
        }
        return Collections.unmodifiableMap(ordered);
    }

    private static Map<String, Delivery> deliveries(Map<String, Delivery> source, long elapsedTicks) {
        if (source == null || source.size() > MAX_DELIVERIES) throw new IllegalArgumentException("observation deliveries may contain at most " + MAX_DELIVERIES + " entries");
        LinkedHashMap<String, Delivery> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, Delivery> entry : new TreeMap<>(source).entrySet()) {
            Delivery delivery = entry.getValue();
            if (delivery == null || !entry.getKey().equals(delivery.id())) throw new IllegalArgumentException("observation delivery key must match its ID");
            if (delivery.elapsedTicks() > elapsedTicks) throw new IllegalArgumentException("observation delivery time may not exceed elapsed play time");
            ordered.put(entry.getKey(), delivery);
        }
        return Collections.unmodifiableMap(ordered);
    }
}
