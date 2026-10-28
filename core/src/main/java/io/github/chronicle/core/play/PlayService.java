package io.github.chronicle.core.play;

import io.github.chronicle.core.authoring.AuthoringRegistry;
import io.github.chronicle.core.authoring.AuthoringSite;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Save-before-publish domain service for shared active time and once-only observations. */
public final class PlayService {
    private final PlayRepository repository;
    private PlayState current;
    private long liveTicks;

    public PlayService(PlayRepository repository) throws IOException {
        if (repository == null) throw new IllegalArgumentException("play repository is required");
        this.repository = repository;
        this.current = repository.load();
        if (current == null) throw new IOException("play repository returned no state");
        this.liveTicks = current.elapsedTicks();
    }

    /** Returns the last durable state. The active clock may be newer until the next checkpoint. */
    public synchronized PlayState state() { return current; }

    public synchronized long elapsedTicks() { return liveTicks; }

    /** Advances only the in-memory shared clock; runtime checkpoints bound crash-time loss. */
    public synchronized void advance(boolean activeOccupied) {
        if (!activeOccupied) return;
        try { liveTicks = Math.addExact(liveTicks, 1); }
        catch (ArithmeticException exception) { throw new IllegalStateException("elapsed play time is exhausted", exception); }
    }

    /** Saves the live clock when it changed. */
    public synchronized void checkpoint() throws IOException {
        if (liveTicks == current.elapsedTicks()) return;
        commit(current.participants(), current.bindings(), current.deliveries());
    }

    public synchronized void setParticipant(UUID player, boolean participating) throws IOException {
        requirePlayer(player);
        boolean prior = current.participants().contains(player);
        if (prior == participating) return;
        Set<UUID> participants = new LinkedHashSet<>(current.participants());
        if (participating) participants.add(player);
        else participants.remove(player);
        commit(participants, current.bindings(), current.deliveries());
    }

    /** Saves a new binding or explicitly replaces an existing one, pinning all referenced revisions. */
    public synchronized PlayState.Binding bind(ObservationCatalog.Definition definition, AuthoringRegistry registry,
                                                ObservationConfiguration configuration, boolean replace) throws IOException {
        requireDefinition(definition);
        requireRegistry(registry);
        if (configuration == null) throw new IllegalArgumentException("observation configuration is required");
        if (current.events().containsKey(definition.id())) throw new IllegalStateException("Use the unified Events editor to change this Event.");
        requireEditableEvent(definition.id());
        PlayState.Binding prior = current.bindings().get(definition.id());
        if (replace && prior == null) throw new IllegalStateException("observation binding does not exist: " + definition.id());
        if (!replace && prior != null) throw new IllegalStateException("observation binding already exists: " + definition.id());

        long regionRevision = 0;
        if (!configuration.regionId().isEmpty()) {
            AuthoringSite site = registry.sites().get(configuration.regionId());
            if (!(site instanceof AuthoringSite.Region)) throw new IllegalArgumentException("observation region is missing or is not a region: " + configuration.regionId());
            regionRevision = site.revision();
        } else if (definition.audience() == ObservationCatalog.Audience.REGION) {
            throw new IllegalArgumentException("region-audience observation requires a bound region");
        }

        long nextRevision = nextRevision();
        long delayTicks = configuration.mode() == PlayState.Mode.TIMER
                ? Math.multiplyExact(configuration.delaySeconds(), 20L) : 0;
        PlayState.Binding binding = new PlayState.Binding(definition.id(), nextRevision, definition.fingerprint(),
                configuration.mode(), configuration.regionId(), regionRevision, liveTicks, delayTicks, configuration.requiredFlag());
        Map<String, PlayState.Binding> bindings = new LinkedHashMap<>(current.bindings());
        bindings.put(binding.id(), binding);
        commit(nextRevision, current.participants(), bindings, current.deliveries());
        return binding;
    }

    /** Turns one trigger off without deleting its delivery evidence. */
    public synchronized void off(String id) throws IOException {
        requireId(id);
        if (current.events().containsKey(id)) throw new IllegalStateException("Use the unified Events editor to remove this Event's configuration.");
        requireEditableEvent(id);
        if (!current.bindings().containsKey(id)) return;
        Map<String, PlayState.Binding> bindings = new LinkedHashMap<>(current.bindings());
        bindings.remove(id);
        commit(current.participants(), bindings, current.deliveries());
    }

    /** Explicitly clears one receipt and freshly arms its binding, if still configured. */
    public synchronized void reset(String id) throws IOException {
        requireId(id);
        EventExecution execution = current.executions().get(id);
        if (execution != null) {
            if (execution.phase() != EventExecution.Phase.COMPLETE)
                throw new IllegalStateException("Resolve the existing Event execution before allowing another run.");
            repository.archiveEvent(execution);
        }
        if (!current.deliveries().containsKey(id) && execution == null)
            throw new IllegalStateException("observation delivery does not exist: " + id);
        long nextRevision = nextRevision();
        Map<String, PlayState.Binding> bindings = new LinkedHashMap<>(current.bindings());
        PlayState.Binding binding = bindings.get(id);
        if (binding != null) {
            bindings.put(id, new PlayState.Binding(binding.id(), nextRevision, binding.definitionFingerprint(), binding.mode(),
                    binding.regionId(), binding.regionRevision(), liveTicks, binding.delayTicks(), binding.requiredFlag()));
        }
        Map<String, PlayState.Delivery> deliveries = new LinkedHashMap<>(current.deliveries());
        deliveries.remove(id);
        Map<String, EventConfiguration> events = new LinkedHashMap<>(current.events());
        EventConfiguration event = events.get(id);
        if (event != null) events.put(id, revised(event, nextRevision, liveTicks, event.mode(), event.enabled()));
        Map<String, EventExecution> executions = new LinkedHashMap<>(current.executions());
        executions.remove(id);
        commit(nextRevision, current.participants(), bindings, deliveries, events, executions);
    }

    /** Resolves a configured binding only while its campaign definition and authoring region are exact. */
    public synchronized PlayState.Binding validate(ObservationCatalog.Definition definition, AuthoringRegistry registry, String id) {
        requireDefinition(definition);
        requireRegistry(registry);
        requireId(id);
        if (!definition.id().equals(id)) throw new IllegalArgumentException("observation definition does not match requested ID");
        PlayState.Binding binding = current.bindings().get(id);
        if (binding == null) throw new IllegalStateException("observation binding does not exist: " + id);
        if (!binding.definitionFingerprint().equals(definition.fingerprint())) {
            throw new IllegalStateException("observation definition changed after binding: " + id);
        }
        if (!binding.regionId().isEmpty()) {
            AuthoringSite site = registry.sites().get(binding.regionId());
            if (!(site instanceof AuthoringSite.Region region) || region.revision() != binding.regionRevision()) {
                throw new IllegalStateException("observation region changed after binding: " + binding.regionId());
            }
        } else if (definition.audience() == ObservationCatalog.Audience.REGION) {
            throw new IllegalStateException("region-audience observation has no bound region: " + id);
        }
        return binding;
    }

    /** Evaluates only the finite trigger and optional flag prerequisite; runtime supplies region presence. */
    public synchronized boolean due(PlayState.Binding binding, boolean present, Map<String, Boolean> flags) {
        if (binding == null) throw new IllegalArgumentException("observation binding is required");
        requireFlags(flags);
        if (!flagSatisfied(binding, flags)) return false;
        return switch (binding.mode()) {
            case MANUAL, SYSTEM -> false;
            case ENTRY -> present;
            case TIMER -> liveTicks >= binding.armedAtTicks()
                    && liveTicks - binding.armedAtTicks() >= binding.delayTicks();
            case CONDITION -> true;
        };
    }

    /** Saves a once-only claim before any runtime effect. Manual may override timing, never prerequisites. */
    public synchronized boolean claim(ObservationCatalog.Definition definition, AuthoringRegistry registry, String id,
                                      PlayState.DeliverySource source, UUID actor, int session,
                                      List<UUID> recipients, Map<String, Boolean> flags) throws IOException {
        if (current.executions().containsKey(id)) return false;
        PlayState.Binding binding = validate(definition, registry, id);
        if (source == null) throw new IllegalArgumentException("observation delivery source is required");
        requireFlags(flags);
        if (!flagSatisfied(binding, flags)) throw new IllegalStateException("observation flag prerequisite is not satisfied: " + binding.requiredFlag());
        if (source != PlayState.DeliverySource.MANUAL && !source.name().equals(binding.mode().name())) {
            throw new IllegalArgumentException("automatic delivery source does not match observation mode");
        }
        if (current.deliveries().containsKey(id)) return false;
        if ((source == PlayState.DeliverySource.TIMER || source == PlayState.DeliverySource.CONDITION)
                && !due(binding, false, flags)) {
            throw new IllegalStateException("automatic observation is not due: " + id);
        }
        if (recipients == null) throw new IllegalArgumentException("observation recipients are required");
        for (UUID recipient : recipients) {
            if (recipient == null || !current.participants().contains(recipient)) {
                throw new IllegalArgumentException("observation recipients must be enrolled participants");
            }
        }
        if (source == PlayState.DeliverySource.ENTRY && (actor == null || !current.participants().contains(actor))) {
            throw new IllegalArgumentException("entry observation actor must be an enrolled participant");
        }
        PlayState.Delivery delivery = new PlayState.Delivery(id, PlayState.Status.CLAIMED, source, actor, session, liveTicks, recipients);
        Map<String, PlayState.Delivery> deliveries = new LinkedHashMap<>(current.deliveries());
        deliveries.put(id, delivery);
        commit(current.participants(), current.bindings(), deliveries);
        return true;
    }

    public synchronized void finish(String id) throws IOException {
        requireId(id);
        if (current.executions().containsKey(id)) throw new IllegalStateException("This Event uses coordinated execution completion.");
        PlayState.Delivery prior = current.deliveries().get(id);
        if (prior == null) throw new IllegalStateException("observation delivery has not been claimed: " + id);
        if (prior.status() != PlayState.Status.CLAIMED) throw new IllegalStateException("observation delivery is already finished: " + id);
        Map<String, PlayState.Delivery> deliveries = new LinkedHashMap<>(current.deliveries());
        deliveries.put(id, new PlayState.Delivery(prior.id(), PlayState.Status.DELIVERED, prior.source(), prior.actor(),
                prior.session(), prior.elapsedTicks(), prior.recipients()));
        commit(current.participants(), current.bindings(), deliveries);
    }

    /** The caller pins this revision while resolving current Region and snapshot references. */
    public synchronized long nextEventRevision() { return nextRevision(); }

    /** One whole-event save; replacing a legacy cue migrates only that setup, preserving its receipts. */
    public synchronized void putEvent(EventConfiguration event, EventConfiguration expected, boolean creating) throws IOException {
        if (event == null) throw new IllegalArgumentException("Event configuration is required.");
        String id = event.definition().id();
        EventConfiguration old = current.events().get(id);
        if (!java.util.Objects.equals(old, expected)) throw new IllegalStateException("The Event changed; reopen its editor.");
        if (creating && (old != null || current.bindings().containsKey(id) || current.deliveries().containsKey(id)
                || current.executions().containsKey(id))) throw new IllegalStateException("That Event ID already exists or has execution history.");
        requireEditableEvent(id);
        long revision = nextRevision();
        if (event.definition().revision() != revision || event.armedAtTicks() != liveTicks)
            throw new IllegalStateException("The Event revision or play clock changed before saving.");
        Map<String, EventConfiguration> events = new LinkedHashMap<>(current.events());
        events.put(id, event);
        Map<String, PlayState.Binding> bindings = new LinkedHashMap<>(current.bindings());
        bindings.remove(id);
        commit(revision, current.participants(), bindings, current.deliveries(), events, current.executions());
    }

    /** Removes a custom definition, retaining legacy delivery evidence and an immutable final run archive. */
    public synchronized void deleteEvent(String id, EventConfiguration expected) throws IOException {
        requireId(id);
        EventConfiguration old = current.events().get(id);
        if (old == null || !old.equals(expected)) throw new IllegalStateException("The Event changed; reopen it before deleting.");
        EventExecution execution = current.executions().get(id);
        if (execution != null) {
            if (execution.phase() != EventExecution.Phase.COMPLETE)
                throw new IllegalStateException("Resolve the existing Event execution before deleting its definition.");
            repository.archiveEvent(execution);
        }
        Map<String, EventConfiguration> events = new LinkedHashMap<>(current.events());
        events.remove(id);
        Map<String, EventExecution> executions = new LinkedHashMap<>(current.executions());
        executions.remove(id);
        Map<String, PlayState.Binding> bindings = new LinkedHashMap<>(current.bindings());
        bindings.remove(id);
        commit(nextRevision(), current.participants(), bindings, current.deliveries(), events, executions);
    }

    /** Publishes the sole current run pointer after immutable batch evidence exists, before any effects. */
    public synchronized void beginExecution(EventExecution execution) throws IOException {
        if (execution == null) throw new IllegalArgumentException("Event execution is required.");
        String id = execution.eventId();
        if (current.executions().containsKey(id) || current.deliveries().containsKey(id))
            throw new IllegalStateException("This Event already has an execution or delivery record.");
        EventConfiguration configured = current.events().get(id);
        if (configured != null) {
            if (!configured.equals(execution.configuration())) throw new IllegalStateException("Event setup changed during preparation.");
        } else if (!matchesLegacy(execution.configuration(), current.bindings().get(id))) {
            throw new IllegalStateException("This Event has no current, matching configuration.");
        }
        if (!execution.configuration().enabled()) throw new IllegalStateException("This Event is not configured for live execution.");
        if (execution.elapsedTicks() != liveTicks) throw new IllegalStateException("Play time changed during Event preparation.");
        EventExecution.Phase initial = execution.configuration().definition().changes().isEmpty()
                ? EventExecution.Phase.EVENT_OUTCOME : EventExecution.Phase.PREPARED;
        if (execution.phase() != initial) throw new IllegalArgumentException("Invalid initial Event execution phase.");
        if (execution.source() != PlayState.DeliverySource.MANUAL
                && !execution.source().name().equals(execution.configuration().mode().name()))
            throw new IllegalArgumentException("Automatic Event source does not match its trigger.");
        if (execution.source() == PlayState.DeliverySource.ENTRY && execution.configuration().formatVersion()==1 && !current.participants().contains(execution.actor()))
            throw new IllegalStateException("The entering Event participant left during preparation.");
        for (UUID recipient : execution.recipients()) if (!current.participants().contains(recipient))
            throw new IllegalStateException("An Event recipient left during preparation; review the audience again.");
        Map<String, EventExecution> executions = new LinkedHashMap<>(current.executions());
        executions.put(id, execution);
        commit(nextRevision(), current.participants(), current.bindings(), current.deliveries(), current.events(), executions);
    }

    /** Advances only the pinned run; external world evidence is checked by the trusted coordinator. */
    public synchronized EventExecution advanceExecution(String id, UUID operationId, EventExecution.Phase phase, String detail) throws IOException {
        requireId(id);
        EventExecution old = current.executions().get(id);
        if (old == null || !old.operationId().equals(operationId)) throw new IllegalStateException("The Event execution changed.");
        if (phase == old.phase()) return old;
        if (old.phase() == EventExecution.Phase.EVENT_OUTCOME && phase == EventExecution.Phase.COMPLETE
                && old.configuration().definition().hasMedia()) throw new IllegalStateException("Event media must be claimed before completion.");
        if (!transitionAllowed(old.phase(), phase)) throw new IllegalStateException("Invalid Event execution transition: " + old.phase() + " to " + phase);
        EventExecution next = old.with(phase, detail);
        Map<String, EventExecution> executions = new LinkedHashMap<>(current.executions());
        executions.put(id, next);
        Map<String, PlayState.Delivery> deliveries = new LinkedHashMap<>(current.deliveries());
        if (phase == EventExecution.Phase.COMPLETE && !old.recipients().isEmpty()) {
            PlayState.Status status = old.phase() == EventExecution.Phase.MEDIA_UNCERTAIN
                    ? PlayState.Status.CLAIMED : PlayState.Status.DELIVERED;
            deliveries.put(id, new PlayState.Delivery(id, status, old.source(), old.actor(), old.session(), old.elapsedTicks(), old.recipients()));
        }
        commit(nextRevision(), current.participants(), current.bindings(), deliveries, current.events(), executions);
        return next;
    }

    /** An explicit GM acknowledgement finishes uncertain media without replaying it or terrain. */
    public synchronized void acknowledgeEventMedia(String id) throws IOException {
        EventExecution execution = current.executions().get(id);
        if (execution == null || execution.phase() != EventExecution.Phase.MEDIA_UNCERTAIN)
            throw new IllegalStateException("This Event has no uncertain media to acknowledge.");
        advanceExecution(id, execution.operationId(), EventExecution.Phase.COMPLETE,
                "Media uncertainty acknowledged; no message, sound or Region change was replayed.");
    }

    private void requireEditableEvent(String id) {
        EventExecution execution = current.executions().get(id);
        if (execution != null && execution.phase() != EventExecution.Phase.COMPLETE) throw new IllegalStateException("This Event has an unresolved execution; finish or acknowledge it first.");
    }

    private static boolean matchesLegacy(EventConfiguration event, PlayState.Binding binding) {
        if (binding == null || !binding.id().equals(event.definition().id()) || binding.revision() != event.definition().revision()
                || binding.mode() != event.mode() || binding.armedAtTicks() != event.armedAtTicks()
                || binding.delayTicks() != event.delayTicks() || !binding.requiredFlag().equals(event.requiredFlag())) return false;
        for (var pin : List.of(event.triggerRegion(), event.definition().audienceRegion())) {
            if (pin.isPresent() && (!pin.get().id().equals(binding.regionId()) || pin.get().revision() != binding.regionRevision())) return false;
        }
        return binding.regionId().isEmpty() || event.triggerRegion().isPresent() || event.definition().audienceRegion().isPresent();
    }

    private static EventConfiguration revised(EventConfiguration event, long revision, long armedAt, PlayState.Mode mode, boolean enabled) {
        EventDefinition d = event.definition();
        EventDefinition next = new EventDefinition(d.id(), revision, d.label(), d.description(), d.text(), d.sound(), d.audience(), d.audienceRegion(), d.changes(), d.flagChanges(), d.formatVersion());
        return new EventConfiguration(next, mode, event.triggerRegion(), armedAt, event.delayTicks(), event.requiredFlags(), enabled, event.source(), event.formatVersion());
    }

    private static boolean transitionAllowed(EventExecution.Phase from, EventExecution.Phase to) {
        return switch (from) {
            case PREPARED -> to == EventExecution.Phase.APPLYING || to == EventExecution.Phase.TERRAIN_SAVED
                    || to == EventExecution.Phase.WORLD_COMMITTED || to == EventExecution.Phase.CONFLICT;
            case APPLYING -> to == EventExecution.Phase.TERRAIN_SAVED || to == EventExecution.Phase.WORLD_COMMITTED || to == EventExecution.Phase.CONFLICT;
            case TERRAIN_SAVED -> to == EventExecution.Phase.WORLD_COMMITTED || to == EventExecution.Phase.CONFLICT;
            case WORLD_COMMITTED -> to == EventExecution.Phase.EVENT_OUTCOME || to == EventExecution.Phase.CONFLICT;
            case EVENT_OUTCOME -> to == EventExecution.Phase.MEDIA_CLAIMED || to == EventExecution.Phase.COMPLETE;
            case MEDIA_CLAIMED -> to == EventExecution.Phase.COMPLETE || to == EventExecution.Phase.MEDIA_UNCERTAIN;
            case MEDIA_UNCERTAIN -> to == EventExecution.Phase.COMPLETE;
            case CONFLICT -> to == EventExecution.Phase.PREPARED || to == EventExecution.Phase.APPLYING
                    || to == EventExecution.Phase.TERRAIN_SAVED || to == EventExecution.Phase.WORLD_COMMITTED;
            case COMPLETE -> false;
        };
    }

    private boolean flagSatisfied(PlayState.Binding binding, Map<String, Boolean> flags) {
        return binding.requiredFlag().isEmpty() || Boolean.TRUE.equals(flags.get(binding.requiredFlag()));
    }

    private void commit(Set<UUID> participants, Map<String, PlayState.Binding> bindings,
                        Map<String, PlayState.Delivery> deliveries) throws IOException {
        commit(nextRevision(), participants, bindings, deliveries);
    }

    private void commit(long revision, Set<UUID> participants, Map<String, PlayState.Binding> bindings,
                        Map<String, PlayState.Delivery> deliveries) throws IOException {
        commit(revision, participants, bindings, deliveries, current.events(), current.executions());
    }

    private void commit(long revision, Set<UUID> participants, Map<String, PlayState.Binding> bindings,
                        Map<String, PlayState.Delivery> deliveries, Map<String, EventConfiguration> events,
                        Map<String, EventExecution> executions) throws IOException {
        PlayState candidate = new PlayState(revision, liveTicks, participants, bindings, deliveries, events, executions);
        repository.save(candidate);
        current = candidate;
    }

    private long nextRevision() {
        try { return Math.addExact(current.revision(), 1); }
        catch (ArithmeticException exception) { throw new IllegalStateException("play revision is exhausted", exception); }
    }

    private static void requirePlayer(UUID player) {
        if (player == null) throw new IllegalArgumentException("player UUID is required");
    }

    private static void requireDefinition(ObservationCatalog.Definition definition) {
        if (definition == null) throw new IllegalArgumentException("observation definition is required");
    }

    private static void requireRegistry(AuthoringRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("authoring registry is required");
    }

    private static void requireId(String id) { ObservationCatalog.requireIdentifier(id, "observation ID"); }

    private static void requireFlags(Map<String, Boolean> flags) {
        if (flags == null) throw new IllegalArgumentException("campaign flags are required");
    }
}
