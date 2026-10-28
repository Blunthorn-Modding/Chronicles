package io.github.chronicle.core.campaign;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable, persisted campaign state. */
public record CampaignState(int schemaVersion, int sessionNumber, SessionPhase phase, Map<String, Boolean> flags,
                            Set<String> completedEvents, List<AuditEntry> history, long nextSequence,
                            Map<String, Long> flagOffVersions, Map<UUID, FlagOutcomeReceipt> eventFlagOutcomes) {
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_EVENT_FLAG_OUTCOMES = 256;

    public CampaignState(int schemaVersion, int sessionNumber, SessionPhase phase, Map<String, Boolean> flags,
                         Set<String> completedEvents, List<AuditEntry> history, long nextSequence) {
        this(schemaVersion, sessionNumber, phase, flags, completedEvents, history, nextSequence, Map.of(), Map.of());
    }

    public CampaignState {
        if (schemaVersion != 1 && schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported campaign schema version: " + schemaVersion);
        }
        CampaignValidation.sessionNumber(sessionNumber);
        if (phase == null) {
            throw new IllegalArgumentException("phase is required");
        }
        if (phase == SessionPhase.INACTIVE && sessionNumber != 0) {
            throw new IllegalArgumentException("inactive campaign state must use session 0");
        }
        CampaignValidation.flagMap(flags, "flags");
        flags = Map.copyOf(new LinkedHashMap<>(flags));
        if (completedEvents == null || completedEvents.size() > CampaignValidation.MAX_COMPLETED_EVENTS) {
            throw new IllegalArgumentException("completedEvents may contain at most " + CampaignValidation.MAX_COMPLETED_EVENTS + " entries");
        }
        for (String eventId : completedEvents) {
            CampaignValidation.identifier(eventId, "completed event ID");
        }
        completedEvents = Set.copyOf(new LinkedHashSet<>(completedEvents));
        if (history == null || history.size() > CampaignValidation.MAX_HISTORY) {
            throw new IllegalArgumentException("history may contain at most " + CampaignValidation.MAX_HISTORY + " entries");
        }
        long previous = -1;
        for (AuditEntry entry : history) {
            if (entry == null || entry.sequence() <= previous) {
                throw new IllegalArgumentException("audit history sequence must be strictly increasing");
            }
            previous = entry.sequence();
        }
        history = List.copyOf(history);
        if (nextSequence < 0 || nextSequence <= previous) {
            throw new IllegalArgumentException("nextSequence must be greater than every audit sequence");
        }
        if (flagOffVersions == null || flagOffVersions.size() > CampaignValidation.MAX_FLAGS) {
            throw new IllegalArgumentException("flagOffVersions may contain at most " + CampaignValidation.MAX_FLAGS + " entries");
        }
        for (Map.Entry<String, Long> entry : flagOffVersions.entrySet()) {
            CampaignValidation.identifier(entry.getKey(), "flag Off version ID");
            if (!flags.containsKey(entry.getKey()) || entry.getValue() == null || entry.getValue() < 0 || entry.getValue() >= nextSequence) {
                throw new IllegalArgumentException("flag Off versions must refer to existing flags and committed audit sequences");
            }
        }
        flagOffVersions = Map.copyOf(new LinkedHashMap<>(flagOffVersions));
        if (eventFlagOutcomes == null || eventFlagOutcomes.size() > MAX_EVENT_FLAG_OUTCOMES) {
            throw new IllegalArgumentException("eventFlagOutcomes may contain at most " + MAX_EVENT_FLAG_OUTCOMES + " entries");
        }
        for (Map.Entry<UUID, FlagOutcomeReceipt> entry : eventFlagOutcomes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || !entry.getKey().equals(entry.getValue().operationId())) {
                throw new IllegalArgumentException("event outcome receipt keys must match their operation IDs");
            }
        }
        eventFlagOutcomes = Map.copyOf(new LinkedHashMap<>(eventFlagOutcomes));
        if (schemaVersion == 1 && (!flagOffVersions.isEmpty() || !eventFlagOutcomes.isEmpty())) {
            throw new IllegalArgumentException("campaign schema 1 cannot contain Event progression metadata");
        }
    }

    public static CampaignState newWorld() {
        return new CampaignState(SCHEMA_VERSION, 0, SessionPhase.INACTIVE, Map.of(), Set.of(), List.of(), 0);
    }

    public boolean flag(String id) {
        CampaignValidation.identifier(id, "flag ID");
        return flags.getOrDefault(id, false);
    }

    public long flagOffVersion(String id) {
        CampaignValidation.identifier(id, "flag ID");
        return flagOffVersions.getOrDefault(id, 0L);
    }
}
