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

    public synchronized void setFlag(Actor actor, String id, boolean value) throws IOException {
        requireActor(actor);
        requirePhase(SessionPhase.ACTIVE, "edit flags");
        CampaignValidation.identifier(id, "flag ID");
        Map<String, Boolean> flags = new LinkedHashMap<>(current.flags());
        flags.put(id, value);
        commit(current.phase(), current.sessionNumber(), flags, current.completedEvents(), actor, "flag.set", id,
                Map.of("value", Boolean.toString(value)));
    }

    public synchronized String preview(String eventId) {
        CampaignEvent event = event(eventId);
        return eligibility(event);
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
        CampaignState candidate = new CampaignState(CampaignState.SCHEMA_VERSION, sessionNumber, phase, flags, completedEvents, history, next);
        store.save(candidate);
        current = candidate;
    }
}
