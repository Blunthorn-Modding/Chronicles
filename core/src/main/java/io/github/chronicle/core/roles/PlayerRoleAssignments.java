package io.github.chronicle.core.roles;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/** Revisioned role seats, first-entry markers, and explicit per-player spawn-policy overrides. */
public record PlayerRoleAssignments(long revision, Map<UUID, String> assigned, Set<UUID> started, boolean migratedExisting,
                                    Set<UUID> startingDisabled, Set<UUID> respawnDisabled) {
    /** Compatibility constructor for pre-policy assignment state. */
    public PlayerRoleAssignments(long revision, Map<UUID,String> assigned, Set<UUID> started, boolean migratedExisting) {
        this(revision,assigned,started,migratedExisting,Set.of(),Set.of());
    }
    public PlayerRoleAssignments {
        if (revision < 0) throw new IllegalArgumentException("role assignment revision must not be negative");
        TreeMap<UUID, String> ordered = new TreeMap<>();
        if (assigned == null || assigned.size()>512) throw new IllegalArgumentException("role assignments are required");
        assigned.forEach((id, role) -> { if (id == null || role == null || !io.github.chronicle.core.authoring.AuthoringSite.ID.matcher(role).matches()) throw new IllegalArgumentException("role assignments must map UUIDs to role IDs"); ordered.put(id, role); });
        assigned = Collections.unmodifiableMap(ordered);
        TreeSet<UUID> marks = new TreeSet<>();
        if (started == null || started.size()>4096) throw new IllegalArgumentException("role start markers are required");
        started.forEach(id -> { if (id == null) throw new IllegalArgumentException("role start markers must contain UUIDs"); marks.add(id); });
        started = Collections.unmodifiableSet(marks);
        startingDisabled = uuids(startingDisabled, "starting-policy disabled players");
        respawnDisabled = uuids(respawnDisabled, "respawn-policy disabled players");
    }
    private static Set<UUID> uuids(Set<UUID> values,String field) { if(values==null||values.size()>4096)throw new IllegalArgumentException(field+" are required");TreeSet<UUID> ordered=new TreeSet<>();values.forEach(id->{if(id==null)throw new IllegalArgumentException(field+" must contain UUIDs");ordered.add(id);});return Collections.unmodifiableSet(ordered); }
    public static PlayerRoleAssignments empty() { return new PlayerRoleAssignments(0, Map.of(), Set.of(), false); }
}
