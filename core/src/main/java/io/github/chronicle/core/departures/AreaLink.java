package io.github.chronicle.core.departures;

import io.github.chronicle.core.areas.AreaState;
import java.util.Objects;

/** Optional requested state of a separately owned, repeatable area change. */
public record AreaLink(String areaId, AreaState requestedState) {
    public AreaLink {
        areaId = DepartureValidation.id(areaId, "area link ID");
        Objects.requireNonNull(requestedState, "requested area state is required");
    }
}
