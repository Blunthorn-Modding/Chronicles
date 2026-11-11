package io.github.chronicle.core.spawn;

import io.github.chronicle.core.authoring.AuthoringRegistry;
import io.github.chronicle.core.authoring.AuthoringSite;

import java.io.IOException;
import java.util.LinkedHashMap;

/** Publishes spawn bindings only after a durable save and never follows a moved location silently. */
public final class SpawnService {
    private final SpawnRepository repository;
    private SpawnRegistry current;

    public SpawnService(SpawnRepository repository) throws IOException {
        if (repository == null) throw new IllegalArgumentException("spawn repository is required");
        this.repository = repository;
        this.current = repository.load();
        if (current == null) throw new IOException("spawn repository returned no registry");
    }

    public synchronized SpawnRegistry state() { return current; }

    public synchronized SpawnBinding bind(AuthoringRegistry authoring, String purpose, String locationId, boolean replace) throws IOException {
        if (authoring == null) throw new IllegalArgumentException("authoring registry is required");
        var site = authoring.sites().get(locationId);
        if (!(site instanceof AuthoringSite.Location location)) throw new IllegalArgumentException("spawn binding requires a saved Core location: " + locationId);
        boolean exists = current.bindings().containsKey(purpose);
        if (exists && !replace) throw new IllegalStateException("spawn purpose already exists: " + purpose);
        if (!exists && replace) throw new IllegalStateException("spawn purpose does not exist for replacement: " + purpose);
        var binding = new SpawnBinding(purpose, location.id(), location.revision());
        var next = new LinkedHashMap<>(current.bindings());
        next.put(purpose, binding);
        commit(new SpawnRegistry(nextRevision(), next));
        return binding;
    }

    public synchronized void remove(String purpose) throws IOException {
        if (purpose == null || !SpawnBinding.ID.matcher(purpose).matches()) throw new IllegalArgumentException("spawn purpose must match " + SpawnBinding.ID.pattern());
        if (!current.bindings().containsKey(purpose)) throw new IllegalStateException("spawn purpose does not exist: " + purpose);
        var next = new LinkedHashMap<>(current.bindings());
        next.remove(purpose);
        commit(new SpawnRegistry(nextRevision(), next));
    }

    /** Resolves exactly the location that was bound; replacement/removal invalidates the binding. */
    public synchronized AuthoringSite.Location resolve(AuthoringRegistry authoring, String purpose) {
        if (authoring == null) throw new IllegalArgumentException("authoring registry is required");
        var binding = current.bindings().get(purpose);
        if (binding == null) throw new IllegalArgumentException("unknown spawn purpose: " + purpose);
        var site = authoring.sites().get(binding.locationId());
        if (!(site instanceof AuthoringSite.Location location) || location.revision() != binding.locationRevision()) {
            throw new IllegalStateException("spawn binding is stale; rebind " + purpose + " to a current Core location");
        }
        return location;
    }

    private long nextRevision() {
        try { return Math.addExact(current.revision(), 1); }
        catch (ArithmeticException exception) { throw new IllegalStateException("spawn registry revision is exhausted", exception); }
    }
    private void commit(SpawnRegistry next) throws IOException { repository.save(next); current = next; }
}
