package io.github.chronicle.core.campaign;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Coordinates validated lifecycle and event mutations with durable persistence. */
public final class CampaignService {
    private final CampaignStore store;
    private final CampaignEvents events;
    private final Clock clock;
    private CampaignState current;

    public CampaignService(CampaignStore store, CampaignEvents events, Clock clock) throws IOException {
        if (store == null || events == null || clock == null) {
            throw new IllegalArgumentException("store, events, and clock are required");
        }
        this.store = store;
        this.events = events;
        this.clock = clock;
        this.current = store.load();
    }

    public synchronized CampaignState state() {
        return current;
    }

    public synchronized void start(Actor actor, Integer optionalNumber) throws IOException {
        requireActor(actor);
        if (current.phase() != SessionPhase.INACTIVE && current.phase() != SessionPhase.ENDED) {
            throw new IllegalStateException("a session may start only while inactive or ended");
        }
        int number;
        if (optionalNumber == null) {
            number = current.phase() == SessionPhase.INACTIVE ? 1 : incrementSessionNumber();
        } else {
            CampaignValidation.sessionNumber(optionalNumber);
            number = optionalNumber;
            if (current.phase() == SessionPhase.ENDED && number <= current.sessionNumber()) {
                throw new IllegalArgumentException("a session started after an ended session must use a higher number");
            }
        }
        commit(SessionPhase.ACTIVE, number, current.flags(), current.completedEvents(), actor, "session.start", "session",
                Map.of("number", Integer.toString(number)));
    }

    public synchronized void pause(Actor actor) throws IOException {
        requireActor(actor);
        requirePhase(SessionPhase.ACTIVE, "pause");
        commit(SessionPhase.PAUSED, current.sessionNumber(), current.flags(), current.completedEvents(), actor, "session.pause", "session", Map.of());
    }

    public synchronized void resume(Actor actor) throws IOException {
        requireActor(actor);
        requirePhase(SessionPhase.PAUSED, "resume");
        commit(SessionPhase.ACTIVE, current.sessionNumber(), current.flags(), current.completedEvents(), actor, "session.resume", "session", Map.of());
    }

    public synchronized void end(Actor actor) throws IOException {
        requireActor(actor);
        if (current.phase() != SessionPhase.ACTIVE && current.phase() != SessionPhase.PAUSED) {
            throw new IllegalStateException("a session may end only while active or paused");
        }
        commit(SessionPhase.ENDED, current.sessionNumber(), current.flags(), current.completedEvents(), actor, "session.end", "session", Map.of());
    }

    public synchronized void defineFlag(Actor actor, String id) throws IOException {
        requireActor(actor);
        CampaignValidation.identifier(id, "flag ID");
        if (current.phase() == SessionPhase.ACTIVE) {
            throw new IllegalStateException("cannot define flags while campaign session is active");
        }
        if (current.flags().containsKey(id)) {
            throw new IllegalArgumentException("campaign flag already exists: " + id);
        }
        if (current.flags().size() >= CampaignValidation.MAX_FLAGS) {
            throw new IllegalStateException("campaign may contain at most " + CampaignValidation.MAX_FLAGS + " flags");
        }
        Map<String, Boolean> flags = new LinkedHashMap<>(current.flags());
        flags.put(id, false);
        commit(current.phase(), current.sessionNumber(), flags, current.completedEvents(), actor, "flag.define", id,
                Map.of("value", "false"));
    }

    public synchronized void setFlag(Actor actor, String id, boolean value) throws IOException {
        requireActor(actor);
        requirePhase(SessionPhase.ACTIVE, "edit flags");
        CampaignValidation.identifier(id, "flag ID");
        Map<String, Boolean> flags = new LinkedHashMap<>(current.flags());
        flags.put(id, value);
        commit(current.phase(), current.sessionNumber(), flags, current.completedEvents(), actor, "flag.set", id,
                Map.of("value", Boolean.toString(value)));
    }

    /** Preflight for callers before starting external effects; archived operation IDs remain permanently reserved. */
    public synchronized void requireEventFlagCapacity(UUID operationId) throws IOException {
        if (operationId == null) {
            throw new IllegalArgumentException("operation ID is required");
        }
        if (!current.eventFlagOutcomes().containsKey(operationId) && store.archivedEventFlagOutcome(operationId).isPresent()) {
            throw new IllegalStateException("campaign Event flag outcome operation ID is already archived: " + operationId);
        }
        if (!current.eventFlagOutcomes().containsKey(operationId)
                && current.eventFlagOutcomes().size() >= CampaignState.MAX_EVENT_FLAG_OUTCOMES) {
            throw new IllegalStateException("campaign Event flag outcome receipt capacity is full; operator recovery is required");
        }
    }

    /**
     * Finalize an already-authorized Event outcome. The caller owns eligibility and origin evidence,
     * including outcomes whose original action ended play. An exact retry never reapplies flag values.
     */
    public synchronized boolean applyEventFlags(Actor actor, UUID operationId, String eventId,
                                                 String definitionFingerprint, Map<String, Boolean> changes) throws IOException {
        requireActor(actor);
        FlagOutcomeReceipt receipt = new FlagOutcomeReceipt(operationId, eventId, definitionFingerprint, changes);
        FlagOutcomeReceipt previous = current.eventFlagOutcomes().get(operationId);
        if (previous != null) {
            if (!previous.equals(receipt)) {
                throw new IllegalStateException("Event flag outcome operation ID conflicts with its durable receipt: " + operationId);
            }
            return false;
        }
        FlagOutcomeReceipt archived = store.archivedEventFlagOutcome(operationId).orElse(null);
        if (archived != null) {
            if (!archived.equals(receipt)) {
                throw new IllegalStateException("Event flag outcome operation ID conflicts with its archived receipt: " + operationId);
            }
            return false;
        }
        requireEventFlagCapacity(operationId);
        Map<String, Boolean> flags = new LinkedHashMap<>(current.flags());
        flags.putAll(receipt.changes());
        Map<UUID, FlagOutcomeReceipt> receipts = new LinkedHashMap<>(current.eventFlagOutcomes());
        receipts.put(operationId, receipt);
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("operationId", operationId.toString());
        parameters.put("definitionFingerprint", definitionFingerprint);
        receipt.changes().forEach((id, value) -> parameters.put("flag." + id, value.toString()));
        commit(current.phase(), current.sessionNumber(), flags, current.completedEvents(), receipts,
                actor, "event.flags", eventId, parameters);
        return true;
    }

    /** Explicit non-gameplay maintenance; CampaignStore archives every removed receipt before replacement. */
    public synchronized int retireEventFlagOutcomes(Set<UUID> operationIds) throws IOException {
        if (operationIds == null || operationIds.isEmpty()) return 0;
        if (current.phase() == SessionPhase.ACTIVE) throw new IllegalStateException("Pause or end play before Event flag history maintenance");
        Map<UUID,FlagOutcomeReceipt> receipts = new LinkedHashMap<>(current.eventFlagOutcomes());
        int before = receipts.size();
        for (UUID operationId : operationIds) {
            if (operationId == null || !receipts.containsKey(operationId)) throw new IllegalStateException("Event flag outcome changed during maintenance");
            receipts.remove(operationId);
        }
        CampaignState candidate = new CampaignState(CampaignState.SCHEMA_VERSION, current.sessionNumber(), current.phase(),
                current.flags(), current.completedEvents(), current.history(), current.nextSequence(), current.flagOffVersions(), receipts);
        store.save(candidate); current = candidate; return before - receipts.size();
    }

    public synchronized String preview(String eventId) {
        CampaignEvent event = event(eventId);
        return eligibility(event);
    }

    /** Save an administrative operation's evidence without changing progress or completion. */
    public synchronized void recordOperation(Actor actor, String action, String target, Map<String, String> parameters) throws IOException {
        requireActor(actor);
        commit(current.phase(), current.sessionNumber(), current.flags(), current.completedEvents(), actor, action, target, parameters);
    }

    public synchronized String trigger(Actor actor, String eventId) throws IOException {
        requireActor(actor);
        CampaignEvent event = event(eventId);
        String eligibility = eligibility(event);
        if (!"Ready.".equals(eligibility)) {
            throw new IllegalStateException(eligibility);
        }
        EventOutcome outcome;
        try {
            outcome = event.handler().handle(current);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Campaign event handler failed: " + event.id(), exception);
        }
        if (outcome == null) {
            throw new IllegalStateException("Campaign event handler returned no outcome: " + event.id());
        }
        Map<String, Boolean> flags = new LinkedHashMap<>(current.flags());
        flags.putAll(outcome.flagChanges());
        Set<String> completed = new LinkedHashSet<>(current.completedEvents());
        completed.add(event.id());
        commit(current.phase(), current.sessionNumber(), flags, completed, actor, "event.trigger", event.id(), Map.of());
        return outcome.message();
    }

    private CampaignEvent event(String eventId) {
        CampaignValidation.identifier(eventId, "event ID");
        return events.get(eventId).orElseThrow(() -> new IllegalArgumentException("Unknown campaign event: " + eventId));
    }

    private String eligibility(CampaignEvent event) {
        if (current.phase() != SessionPhase.ACTIVE) {
            return "Campaign session is not active.";
        }
        if (event.onceOnly() && current.completedEvents().contains(event.id())) {
            return "Event has already been completed.";
        }
        for (Map.Entry<String, Boolean> requirement : event.requiredFlags().entrySet()) {
            if (current.flag(requirement.getKey()) != requirement.getValue()) {
                return "Required flag does not match: " + requirement.getKey();
            }
        }
        return "Ready.";
    }

    private int incrementSessionNumber() {
        if (current.sessionNumber() >= CampaignValidation.MAX_SESSION_NUMBER) {
            throw new IllegalStateException("session number cannot exceed " + CampaignValidation.MAX_SESSION_NUMBER);
        }
        return current.sessionNumber() + 1;
    }

    private void requirePhase(SessionPhase expected, String operation) {
        if (current.phase() != expected) {
            throw new IllegalStateException("cannot " + operation + " while campaign session is " + current.phase().name().toLowerCase());
        }
    }

    private static void requireActor(Actor actor) {
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
    }

    private void commit(SessionPhase phase, int sessionNumber, Map<String, Boolean> flags, Set<String> completedEvents,
                        Actor actor, String action, String target, Map<String, String> parameters) throws IOException {
        commit(phase, sessionNumber, flags, completedEvents, current.eventFlagOutcomes(), actor, action, target, parameters);
    }

    private void commit(SessionPhase phase, int sessionNumber, Map<String, Boolean> flags, Set<String> completedEvents,
                        Map<UUID, FlagOutcomeReceipt> eventFlagOutcomes, Actor actor, String action, String target,
                        Map<String, String> parameters) throws IOException {
        if (current.nextSequence() == Long.MAX_VALUE) {
            throw new IllegalStateException("audit sequence cannot exceed " + Long.MAX_VALUE);
        }
        long next = current.nextSequence() + 1;
        AuditEntry audit = new AuditEntry(current.nextSequence(), Instant.now(clock).toString(), actor, action, sessionNumber, target, parameters);
        List<AuditEntry> history = new ArrayList<>(current.history());
        history.add(audit);
        if (history.size() > CampaignValidation.MAX_HISTORY) {
            history.removeFirst();
        }
        Map<String, Long> offVersions = new LinkedHashMap<>(current.flagOffVersions());
        flags.forEach((id, value) -> {
            if (current.flag(id) && !value) {
                offVersions.put(id, current.nextSequence());
            }
        });
        CampaignState candidate = new CampaignState(CampaignState.SCHEMA_VERSION, sessionNumber, phase, flags, completedEvents,
                history, next, offVersions, eventFlagOutcomes);
        store.save(candidate);
        current = candidate;
    }
}
