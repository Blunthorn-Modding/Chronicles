package io.github.chronicle.core.departures;

import io.github.chronicle.core.areas.AreaState;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Exact prepared world-operation evidence pinned before departure commitment. */
public record PreparedAreaEffect(UUID operationId, String areaId, AreaState requestedState, String snapshotSha256) {
    public PreparedAreaEffect {
        Objects.requireNonNull(operationId, "prepared area operation ID is required");
        areaId = DepartureValidation.id(areaId, "prepared area ID");
        Objects.requireNonNull(requestedState, "prepared area state is required");
        if (snapshotSha256 == null || !snapshotSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("prepared snapshot hash must be 64 lowercase hexadecimal characters");
        snapshotSha256 = snapshotSha256.toLowerCase(Locale.ROOT);
    }

    boolean matches(AreaLink link) { return areaId.equals(link.areaId()) && requestedState == link.requestedState(); }
}
