package io.github.chronicle.core.play;

import io.github.chronicle.core.areas.AreaBounds;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.areas.SnapshotRef;
import io.github.chronicle.core.authoring.BlockPosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventRegionChangeTest {
    @Test
    void referenceAndChangeRequireExactPositivePins() {
        EventRegionReference region = new EventRegionReference("city", 4);
        SnapshotRef snapshot = snapshot("city", 4);
        EventRegionChange change = new EventRegionChange(region, "city_state", 7, AreaState.END, snapshot);
        assertEquals(snapshot, change.snapshot());

        assertThrows(IllegalArgumentException.class, () -> new EventRegionReference("bad id", 4));
        assertThrows(IllegalArgumentException.class, () -> new EventRegionReference("city", 0));
        assertThrows(IllegalArgumentException.class, () -> new EventRegionChange(region, "bad id", 7, AreaState.END, snapshot));
        assertThrows(IllegalArgumentException.class, () -> new EventRegionChange(region, "city_state", 0, AreaState.END, snapshot));
        assertThrows(IllegalArgumentException.class, () -> new EventRegionChange(region, "city_state", 7, AreaState.END, snapshot("other", 4)));
        assertThrows(IllegalArgumentException.class, () -> new EventRegionChange(region, "city_state", 7, AreaState.END, snapshot("city", 5)));
        assertThrows(NullPointerException.class, () -> new EventRegionChange(null, "city_state", 7, AreaState.END, snapshot));
        assertThrows(NullPointerException.class, () -> new EventRegionChange(region, "city_state", 7, null, snapshot));
        assertThrows(NullPointerException.class, () -> new EventRegionChange(region, "city_state", 7, AreaState.END, null));
    }

    private static SnapshotRef snapshot(String regionId, long revision) {
        AreaBounds bounds = new AreaBounds(regionId, "minecraft:overworld", revision,
                new BlockPosition(0, 64, 0), new BlockPosition(0, 64, 0));
        return new SnapshotRef("a".repeat(64), bounds, bounds.cells(), 1);
    }
}
