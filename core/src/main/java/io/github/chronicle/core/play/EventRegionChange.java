package io.github.chronicle.core.play;

import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.areas.SnapshotRef;
import io.github.chronicle.core.authoring.AuthoringSite;

import java.util.Objects;

/** One immutable saved-state target in an Event definition. */
public record EventRegionChange(EventRegionReference region, String areaId, long areaRevision,
                                AreaState state, SnapshotRef snapshot) {
    public EventRegionChange {
        Objects.requireNonNull(region, "event Region reference is required");
        if (areaId == null || !AuthoringSite.ID.matcher(areaId).matches()) {
            throw new IllegalArgumentException("event Area ID is invalid");
        }
        if (areaRevision < 1) throw new IllegalArgumentException("event Area revision must be positive");
        Objects.requireNonNull(state, "event Area state is required");
        Objects.requireNonNull(snapshot, "event Area snapshot is required");
        if (!snapshot.bounds().regionId().equals(region.id())
                || snapshot.bounds().regionRevision() != region.revision()) {
            throw new IllegalArgumentException("event Area snapshot does not match its Region revision pin");
        }
    }
}
