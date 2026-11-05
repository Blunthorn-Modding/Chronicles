package io.github.chronicle.lastcouncil.house;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Domain state only: callers must enforce permissions and ballot gates. */
public final class HouseRosterService {
    private final Map<String, HouseDefinition> definitions;
    private final HouseRosterRepository repository;
    private HouseRoster state;
    public HouseRosterService(Map<String, HouseDefinition> definitions, HouseRosterRepository repository) throws IOException {
        Objects.requireNonNull(definitions, "definitions");
        if (definitions.size() > HouseRoster.MAX_HOUSES) throw new IllegalArgumentException("too many houses");
        definitions.forEach((id, definition) -> { if (definition == null || !definition.id().equals(id)) throw new IllegalArgumentException("house key does not match ID"); });
        this.definitions = Map.copyOf(definitions);
        this.repository = Objects.requireNonNull(repository, "repository");
        this.state = Objects.requireNonNull(repository.load(), "roster");
        for (var assignment : state.assignments().values()) requireKnown(assignment.houseId());
    }
    public synchronized HouseRoster snapshot() { return state; }
    public synchronized HouseRoster assign(long expectedRevision, HouseAssignment assignment) throws IOException {
        checkRevision(expectedRevision);
        Objects.requireNonNull(assignment, "assignment");
        requireKnown(assignment.houseId());
        if (assignment.equals(state.assignments().get(assignment.owner()))) return state;
        var updated = new HashMap<>(state.assignments());
        updated.put(assignment.owner(), assignment);
        return commit(updated);
    }
    public synchronized HouseRoster unassign(long expectedRevision, UUID owner) throws IOException {
        checkRevision(expectedRevision);
        Objects.requireNonNull(owner, "owner");
        if (!state.assignments().containsKey(owner)) return state;
        var updated = new HashMap<>(state.assignments());
        updated.remove(owner);
        return commit(updated);
    }
    private void requireKnown(String id) { if (!definitions.containsKey(id)) throw new IllegalArgumentException("unknown house: " + id); }
    private void checkRevision(long expected) { if (expected != state.revision()) throw new IllegalStateException("stale roster revision"); }
    private HouseRoster commit(Map<UUID, HouseAssignment> assignments) throws IOException {
        if (state.revision() == Long.MAX_VALUE) throw new IllegalStateException("roster revision exhausted");
        HouseRoster next = new HouseRoster(state.revision() + 1, assignments);
        repository.save(next);
        state = next;
        return next;
    }
}
