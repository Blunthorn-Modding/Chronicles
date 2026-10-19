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
        if (!current.bindings().containsKey(id)) return;
        Map<String, PlayState.Binding> bindings = new LinkedHashMap<>(current.bindings());
        bindings.remove(id);
        commit(current.participants(), bindings, current.deliveries());
    }

    /** Explicitly clears one receipt and freshly arms its binding, if still configured. */
    public synchronized void reset(String id) throws IOException {
        requireId(id);
        if (!current.deliveries().containsKey(id)) throw new IllegalStateException("observation delivery does not exist: " + id);
        long nextRevision = nextRevision();
        Map<String, PlayState.Binding> bindings = new LinkedHashMap<>(current.bindings());
        PlayState.Binding binding = bindings.get(id);
        if (binding != null) {
            bindings.put(id, new PlayState.Binding(binding.id(), nextRevision, binding.definitionFingerprint(), binding.mode(),
                    binding.regionId(), binding.regionRevision(), liveTicks, binding.delayTicks(), binding.requiredFlag()));
        }
        Map<String, PlayState.Delivery> deliveries = new LinkedHashMap<>(current.deliveries());
        deliveries.remove(id);
        commit(nextRevision, current.participants(), bindings, deliveries);
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
            case MANUAL -> false;
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
        PlayState.Delivery prior = current.deliveries().get(id);
        if (prior == null) throw new IllegalStateException("observation delivery has not been claimed: " + id);
        if (prior.status() != PlayState.Status.CLAIMED) throw new IllegalStateException("observation delivery is already finished: " + id);
        Map<String, PlayState.Delivery> deliveries = new LinkedHashMap<>(current.deliveries());
        deliveries.put(id, new PlayState.Delivery(prior.id(), PlayState.Status.DELIVERED, prior.source(), prior.actor(),
                prior.session(), prior.elapsedTicks(), prior.recipients()));
        commit(current.participants(), current.bindings(), deliveries);
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
        PlayState candidate = new PlayState(revision, liveTicks, participants, bindings, deliveries);
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
