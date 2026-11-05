package io.github.chronicle.lastcouncil.house;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record HouseRoster(long revision, Map<UUID, HouseAssignment> assignments) {
    public static final int MAX_HOUSES = 32;
    public HouseRoster {
        if (revision < 0) throw new IllegalArgumentException("negative roster revision");
        Objects.requireNonNull(assignments, "assignments");
        if (assignments.size() > MAX_HOUSES) throw new IllegalArgumentException("too many assignments");
        var houses = new HashSet<String>();
        assignments.forEach((owner, assignment) -> {
            if (owner == null || assignment == null || !owner.equals(assignment.owner())) throw new IllegalArgumentException("assignment owner does not match key");
            if (!houses.add(assignment.houseId())) throw new IllegalArgumentException("house already assigned");
        });
        assignments = Map.copyOf(assignments);
    }
    public static HouseRoster empty() { return new HouseRoster(0, Map.of()); }
}
