package io.github.chronicle.core.spawn;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Applies automatic-player-spawn policy changes only after their durable save succeeds. */
public final class AutomaticSpawnService {
    private final AutomaticSpawnRepository repository;
    private AutomaticSpawnState current;

    public AutomaticSpawnService(AutomaticSpawnRepository repository) throws IOException {
        if (repository == null) throw new IllegalArgumentException("automatic spawn repository is required");
        this.repository = repository;
        this.current = repository.load();
        if (current == null) throw new IOException("automatic spawn repository returned no state");
    }

    public synchronized AutomaticSpawnState state() { return current; }

    /** Enables one independently configured automatic policy using the exact supplied pinned binding. */
    public synchronized void set(UUID player, SpawnBinding binding, boolean arrival) throws IOException {
        requirePlayer(player);
        if (binding == null) throw new IllegalArgumentException("spawn binding is required");
        AutomaticSpawnState.Policy prior = current.policies().get(player);
        AutomaticSpawnState.Policy policy = arrival
                ? new AutomaticSpawnState.Policy(binding, prior == null ? null : prior.respawn())
                : new AutomaticSpawnState.Policy(prior == null ? null : prior.arrival(), binding);
        LinkedHashMap<UUID, AutomaticSpawnState.Policy> policies = new LinkedHashMap<>(current.policies());
        policies.put(player, policy);
        commit(policies, current.arrivals());
    }

    /** Disables only the selected automatic policy. An already-disabled setting does not write state. */
    public synchronized void disable(UUID player, boolean arrival) throws IOException {
        requirePlayer(player);
        AutomaticSpawnState.Policy prior = current.policies().get(player);
        if (prior == null || (arrival ? prior.arrival() : prior.respawn()) == null) return;
        LinkedHashMap<UUID, AutomaticSpawnState.Policy> policies = new LinkedHashMap<>(current.policies());
        SpawnBinding first = arrival ? null : prior.arrival();
        SpawnBinding second = arrival ? prior.respawn() : null;
        if (first == null && second == null) policies.remove(player);
        else policies.put(player, new AutomaticSpawnState.Policy(first, second));
        commit(policies, current.arrivals());
    }

    /** Removes both automatic policies while retaining the player's durable arrival receipt. */
    public synchronized void leave(UUID player) throws IOException {
        requirePlayer(player);
        if (!current.policies().containsKey(player)) return;
        LinkedHashMap<UUID, AutomaticSpawnState.Policy> policies = new LinkedHashMap<>(current.policies());
        policies.remove(player);
        commit(policies, current.arrivals());
    }

    /** Creates the once-only claim before runtime movement; any existing receipt prevents retries. */
    public synchronized boolean claim(UUID player, String purpose) throws IOException {
        requirePlayer(player);
        if (purpose == null || !SpawnBinding.ID.matcher(purpose).matches()) {
            throw new IllegalArgumentException("arrival purpose must match " + SpawnBinding.ID.pattern());
        }
        if (current.arrivals().containsKey(player)) return false;
        LinkedHashMap<UUID, AutomaticSpawnState.Arrival> arrivals = new LinkedHashMap<>(current.arrivals());
        arrivals.put(player, new AutomaticSpawnState.Arrival(purpose, AutomaticSpawnState.Status.CLAIMED, ""));
        commit(current.policies(), arrivals);
        return true;
    }

    /** Completes an existing claim without permitting an uncertain claim to be silently re-claimed. */
    public synchronized void finish(UUID player, AutomaticSpawnState.Status status, String detail) throws IOException {
        requirePlayer(player);
        if (status == null || status == AutomaticSpawnState.Status.CLAIMED) {
            throw new IllegalArgumentException("arrival completion status must be MOVED or FAILED");
        }
        AutomaticSpawnState.Arrival prior = current.arrivals().get(player);
        if (prior == null) throw new IllegalStateException("automatic arrival has not been claimed for player");
        if (prior.status() != AutomaticSpawnState.Status.CLAIMED) throw new IllegalStateException("automatic arrival is already finished for player");
        LinkedHashMap<UUID, AutomaticSpawnState.Arrival> arrivals = new LinkedHashMap<>(current.arrivals());
        arrivals.put(player, new AutomaticSpawnState.Arrival(prior.purpose(), status, detail));
        commit(current.policies(), arrivals);
    }

    /** Explicit GM recovery: removes one arrival receipt but does not change either policy. */
    public synchronized void reset(UUID player) throws IOException {
        requirePlayer(player);
        if (!current.arrivals().containsKey(player)) throw new IllegalStateException("automatic arrival does not exist for player");
        LinkedHashMap<UUID, AutomaticSpawnState.Arrival> arrivals = new LinkedHashMap<>(current.arrivals());
        arrivals.remove(player);
        commit(current.policies(), arrivals);
    }

    private void commit(java.util.Map<UUID, AutomaticSpawnState.Policy> policies, java.util.Map<UUID, AutomaticSpawnState.Arrival> arrivals) throws IOException {
        AutomaticSpawnState candidate = new AutomaticSpawnState(nextRevision(), policies, arrivals);
        repository.save(candidate);
        current = candidate;
    }

    private long nextRevision() {
        try { return Math.addExact(current.revision(), 1); }
        catch (ArithmeticException exception) { throw new IllegalStateException("automatic spawn revision is exhausted", exception); }
    }

    private static void requirePlayer(UUID player) {
        if (player == null) throw new IllegalArgumentException("player UUID is required");
    }
}
