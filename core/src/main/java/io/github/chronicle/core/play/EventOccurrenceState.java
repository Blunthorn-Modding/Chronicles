package io.github.chronicle.core.play;

import java.util.*;

/** Bounded live queue and stable per-actor consumption index for repeating Events. */
public record EventOccurrenceState(long revision, Map<UUID, Claim> claims,
                                   Map<String, ActorConsumption> actors) {
    public static final int MAX_LIVE_CLAIMS = 256;
    public static final int MAX_LIVE_ACTORS = 4_096;

    public EventOccurrenceState {
        if (revision < 0 || claims == null || claims.size() > MAX_LIVE_CLAIMS
                || actors == null || actors.size() > MAX_LIVE_ACTORS) {
            throw new IllegalArgumentException("Event occurrence evidence exceeds its live bounds");
        }
        LinkedHashMap<UUID, Claim> claimCopy = new LinkedHashMap<>();
        Set<String> occurrences = new HashSet<>();
        claims.forEach((operation, claim) -> {
            if (operation == null || claim == null || !operation.equals(claim.operationId())) {
                throw new IllegalArgumentException("Event occurrence claim identity mismatch");
            }
            if (!occurrences.add(sourceKey(claim.eventId(), claim.source().id()))) {
                throw new IllegalArgumentException("Event source occurrence is claimed more than once");
            }
            claimCopy.put(operation, claim);
        });
        claims = Collections.unmodifiableMap(claimCopy);
        TreeMap<String, ActorConsumption> actorCopy = new TreeMap<>();
        actors.forEach((key, actor) -> {
            if (key == null || actor == null || !key.equals(actor.key())) {
                throw new IllegalArgumentException("Event actor history identity mismatch");
            }
            actorCopy.put(key, actor);
        });
        actors = Collections.unmodifiableMap(actorCopy);
    }

    public static EventOccurrenceState empty() { return new EventOccurrenceState(0, Map.of(), Map.of()); }

    public static String actorKey(String eventId, UUID actor) {
        ObservationCatalog.requireIdentifier(eventId, "Event actor history ID");
        return eventId + "|" + Objects.requireNonNull(actor, "Event actor is required");
    }

    public static String sourceKey(String eventId, String sourceId) {
        ObservationCatalog.requireIdentifier(eventId, "Event occurrence ID");
        bounded(sourceId, 256, false, "Event source receipt ID");
        return eventId + "|" + sourceId;
    }

    public enum Stage { CLAIMED, EXECUTION, COMPLETE }

    /** Immutable source evidence copied before the live source ledger may be maintained. */
    public record SourceSnapshot(String id, String type, String target, String activity, UUID actor,
                                 boolean activeOrigin, Map<String, Boolean> flags, String authority) {
        public SourceSnapshot {
            bounded(id, 256, false, "source receipt ID");
            ObservationCatalog.requireIdentifier(type, "source receipt type");
            bounded(target, 160, true, "source receipt target");
            bounded(activity, 128, true, "source receipt activity");
            bounded(authority, 256, false, "source receipt authority");
            if (!activeOrigin) throw new IllegalArgumentException("repeating Event sources require an active origin");
            if (flags == null || flags.size() > 256) throw new IllegalArgumentException("source flag snapshot exceeds its bound");
            TreeMap<String, Boolean> copy = new TreeMap<>();
            flags.forEach((flagId, value) -> {
                ObservationCatalog.requireIdentifier(flagId, "source flag");
                copy.put(flagId, Objects.requireNonNull(value, "source flag value is required"));
            });
            flags = Collections.unmodifiableMap(copy);
        }
    }

    /** One claim saved before an EventExecution is published. */
    public record Claim(String eventId, UUID operationId, String configurationFingerprint,
                        SourceSnapshot source, int claimedSession, long claimedAtTicks,
                        List<UUID> recipients, Stage stage, String detail) {
        public Claim {
            ObservationCatalog.requireIdentifier(eventId, "Event occurrence ID");
            Objects.requireNonNull(operationId, "Event occurrence operation ID is required");
            fingerprint(configurationFingerprint);
            Objects.requireNonNull(source, "Event occurrence source snapshot is required");
            if (claimedSession < 1 || claimedAtTicks < 0) throw new IllegalArgumentException("Event occurrence claim time is invalid");
            if (recipients == null || recipients.isEmpty() || recipients.size() > PlayState.MAX_PARTICIPANTS
                    || recipients.stream().anyMatch(Objects::isNull)
                    || recipients.stream().distinct().count() != recipients.size()) {
                throw new IllegalArgumentException("Event occurrence recipients must be distinct and non-empty");
            }
            recipients = List.copyOf(recipients);
            Objects.requireNonNull(stage, "Event occurrence stage is required");
            bounded(detail, 2_048, true, "Event occurrence detail");
        }

        public Claim with(Stage next, String nextDetail) {
            if (next.ordinal() < stage.ordinal()) throw new IllegalArgumentException("Event occurrence stages are monotonic");
            return next == stage && Objects.equals(nextDetail, detail) ? this
                    : new Claim(eventId, operationId, configurationFingerprint, source, claimedSession,
                    claimedAtTicks, recipients, next, nextDetail);
        }
    }

    /** Stable first-source identity; configuration fingerprints are provenance, never the deduplication key. */
    public record ActorConsumption(String eventId, UUID actor, UUID operationId, String sourceId,
                                   String configurationFingerprint) {
        public ActorConsumption {
            ObservationCatalog.requireIdentifier(eventId, "Event actor history ID");
            Objects.requireNonNull(actor, "Event actor history actor is required");
            Objects.requireNonNull(operationId, "Event actor history operation is required");
            bounded(sourceId, 256, false, "Event actor source receipt ID");
            fingerprint(configurationFingerprint);
        }
        public String key() { return actorKey(eventId, actor); }
    }

    /** Immutable terminal decision used for baseline and retired-claim deduplication. */
    public record Decision(Kind kind, String eventId, String configurationFingerprint,
                           SourceSnapshot source, UUID operationId, List<UUID> recipients) {
        public enum Kind { BASELINE, RETIRED }
        public Decision {
            Objects.requireNonNull(kind, "Event occurrence decision kind is required");
            ObservationCatalog.requireIdentifier(eventId, "Event occurrence decision ID");
            fingerprint(configurationFingerprint);
            Objects.requireNonNull(source, "Event occurrence decision source is required");
            if (kind == Kind.RETIRED && operationId == null) throw new IllegalArgumentException("retired occurrence requires an operation ID");
            if (kind == Kind.BASELINE && operationId != null) throw new IllegalArgumentException("baseline occurrence cannot contain an operation ID");
            recipients = List.copyOf(recipients == null ? List.of() : recipients);
            if (recipients.size() > PlayState.MAX_PARTICIPANTS || recipients.stream().anyMatch(Objects::isNull)
                    || recipients.stream().distinct().count() != recipients.size()) {
                throw new IllegalArgumentException("Event occurrence decision recipients are invalid");
            }
        }
    }

    private static void fingerprint(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Event configuration fingerprint is invalid");
    }

    private static void bounded(String value, int maximum, boolean empty, String name) {
        if (value == null || value.length() > maximum || (!empty && value.isEmpty()) || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }
}
