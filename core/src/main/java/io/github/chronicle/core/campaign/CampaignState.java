package io.github.chronicle.core.campaign;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, persisted campaign state. */
public record CampaignState(int schemaVersion, int sessionNumber, SessionPhase phase, Map<String, Boolean> flags,
                            Set<String> completedEvents, List<AuditEntry> history, long nextSequence) {
    public static final int SCHEMA_VERSION = 1;

    public CampaignState {
        if (schemaVersion != SCHEMA_VERSION) {
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
    }

    public static CampaignState newWorld() {
        return new CampaignState(SCHEMA_VERSION, 0, SessionPhase.INACTIVE, Map.of(), Set.of(), List.of(), 0);
    }

    public boolean flag(String id) {
        CampaignValidation.identifier(id, "flag ID");
        return flags.getOrDefault(id, false);
    }
}
