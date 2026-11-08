package io.github.chronicle.core.eventpool;

import java.io.IOException;
import java.util.*;

/** Revisioned in-memory catalogue, published only after the durable write succeeds. */
public final class EventPoolCatalog {
    private final EventPoolCatalogStore store;
    private Map<String, EventPoolDefinition> definitions;
    private long revision;
    public EventPoolCatalog(EventPoolCatalogStore store, Collection<EventPoolDefinition> defaults) throws IOException {
        this.store = Objects.requireNonNull(store);
        byte[] bytes = store.load();
        if (bytes == null) {
            Map<String, EventPoolDefinition> initial = new TreeMap<>();
            for (var pool : defaults) if (initial.putIfAbsent(pool.id(), pool) != null) throw new IllegalArgumentException("Duplicate default pool");
            bytes = EventPoolCatalogStore.encode(initial); store.save(bytes);
        }
        definitions = EventPoolCatalogStore.decode(bytes);
    }
    public Map<String, EventPoolDefinition> definitions() { return definitions; }
    public long revision() { return revision; }
    public void put(long expected, EventPoolDefinition pool) throws IOException {
        check(expected); Map<String, EventPoolDefinition> next = new TreeMap<>(definitions); next.put(pool.id(), pool); publish(next);
    }
    public void delete(long expected, String id) throws IOException {
        check(expected); Map<String, EventPoolDefinition> next = new TreeMap<>(definitions);
        if (next.remove(id) == null) throw new IllegalArgumentException("Pool no longer exists"); publish(next);
    }
    private void check(long expected) { if (expected != revision) throw new IllegalStateException("Pool catalogue changed; reopen the editor"); }
    private void publish(Map<String, EventPoolDefinition> next) throws IOException {
        byte[] bytes = EventPoolCatalogStore.encode(next); store.save(bytes); definitions = EventPoolCatalogStore.decode(bytes); revision = Math.addExact(revision, 1);
    }
}
