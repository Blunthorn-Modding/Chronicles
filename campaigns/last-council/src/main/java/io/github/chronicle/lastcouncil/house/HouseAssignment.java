package io.github.chronicle.lastcouncil.house;

import java.util.Objects;
import java.util.UUID;

/** Owner is the existing character profile owner UUID. */
public record HouseAssignment(UUID owner, String houseId, boolean councilMember, boolean candidate) {
    public HouseAssignment {
        Objects.requireNonNull(owner, "owner");
        HouseDefinition.validId(houseId);
        if (candidate && !councilMember) throw new IllegalArgumentException("candidate must be a Council member");
    }
}
