package io.github.chronicle.core.play;

import io.github.chronicle.core.areas.AreaBounds;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.areas.SnapshotRef;
import io.github.chronicle.core.authoring.BlockPosition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventDefinitionTest {
    @Test
    void acceptsMediaOnlyAndWorldOnlyDefinitionsButRequiresAnEffect() {
        EventDefinition message = definition("Line one\nLine two", "", ObservationCatalog.Audience.PARTICIPANTS,
                Optional.empty(), List.of());
        EventDefinition sound = definition("", "minecraft:ambient.cave", ObservationCatalog.Audience.PARTICIPANTS,
                Optional.empty(), List.of());
        EventDefinition world = definition("", "", ObservationCatalog.Audience.REGION,
                Optional.empty(), List.of(change("city", "city_area", "minecraft:overworld", 1,
                        0, 64, 0, 0, 64, 0, 'a')));

        assertEquals("Line one\nLine two", message.text());
        assertEquals("minecraft:ambient.cave", sound.sound());
        assertEquals(1, world.changes().size());
        assertThrows(IllegalArgumentException.class, () -> definition("", "", ObservationCatalog.Audience.PARTICIPANTS,
                Optional.empty(), List.of()));
    }

    @Test
    void validatesFiniteTextIdentifiersAndAudienceShape() {
        assertThrows(IllegalArgumentException.class, () -> new EventDefinition("plain", 1, "Label", "", "Text", "",
                ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EventDefinition("quiet_below:test", 0, "Label", "", "Text", "",
                ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EventDefinition("quiet_below:test", 1, "", "", "Text", "",
                ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EventDefinition("quiet_below:test", 1, "Label", "bad\nsummary", "Text", "",
                ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EventDefinition("quiet_below:test", 1, "Label", "", "bad\ttext", "",
                ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EventDefinition("quiet_below:test", 1, "Label", "", "Text", "not a sound",
                ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EventDefinition("quiet_below:test", 1, "Label", "", "Text", "",
                ObservationCatalog.Audience.PARTICIPANTS, Optional.of(new EventRegionReference("audience", 1)), List.of()));
    }

    @Test
    void rejectsDuplicateOverlappingCrossDimensionAndTooManyTargets() {
        EventRegionChange first = change("first", "first_area", "minecraft:overworld", 1,
                0, 64, 0, 1, 64, 0, 'a');
        EventRegionChange overlap = change("overlap", "overlap_area", "minecraft:overworld", 1,
                1, 64, 0, 2, 64, 0, 'b');
        EventRegionChange otherDimension = change("nether", "nether_area", "minecraft:the_nether", 1,
                2, 64, 0, 2, 64, 0, 'c');
        EventRegionChange duplicateArea = change("second", "first_area", "minecraft:overworld", 1,
                3, 64, 0, 3, 64, 0, 'd');
        EventRegionChange duplicateRegion = change("first", "second_area", "minecraft:overworld", 1,
                4, 64, 0, 4, 64, 0, 'e');

        assertThrows(IllegalArgumentException.class, () -> world(first, overlap));
        assertThrows(IllegalArgumentException.class, () -> world(first, otherDimension));
        assertThrows(IllegalArgumentException.class, () -> world(first, duplicateArea));
        assertThrows(IllegalArgumentException.class, () -> world(first, duplicateRegion));

        List<EventRegionChange> nine = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            nine.add(change("region" + index, "area" + index, "minecraft:overworld", 1,
                    index * 2, 64, 0, index * 2, 64, 0, 'a'));
        }
        assertThrows(IllegalArgumentException.class, () -> definition("", "", ObservationCatalog.Audience.PARTICIPANTS,
                Optional.empty(), nine));
    }

    @Test
    void enforcesCombinedCellAndUniqueChunkCaps() {
        EventRegionChange exactMaximum = change("large", "large_area", "minecraft:overworld", 1,
                0, 0, 0, 127, 15, 127, 'a');
        assertEquals(EventDefinition.MAX_TOTAL_CELLS, exactMaximum.snapshot().cells());
        assertEquals(1, world(exactMaximum).changes().size());

        EventRegionChange oneMoreCell = change("extra", "extra_area", "minecraft:overworld", 1,
                0, 16, 0, 0, 16, 0, 'b');
        assertThrows(IllegalArgumentException.class, () -> world(exactMaximum, oneMoreCell));

        EventRegionChange sixtyFourChunks = change("chunks", "chunks_area", "minecraft:overworld", 1,
                0, 40, 0, 127, 40, 127, 'c');
        EventRegionChange sixtyFifthChunk = change("new_chunk", "new_chunk_area", "minecraft:overworld", 1,
                128, 40, 0, 128, 40, 0, 'd');
        assertThrows(IllegalArgumentException.class, () -> world(sixtyFourChunks, sixtyFifthChunk));
    }

    @Test
    void copiesChangesAndFingerprintsEveryOrderedPin() {
        EventRegionChange first = change("first", "first_area", "minecraft:overworld", 2,
                0, 64, 0, 0, 64, 0, 'a');
        EventRegionChange second = change("second", "second_area", "minecraft:overworld", 3,
                2, 64, 0, 2, 64, 0, 'b');
        ArrayList<EventRegionChange> source = new ArrayList<>(List.of(first, second));
        EventDefinition base = new EventDefinition("quiet_below:test", 4, "Label", "Description", "Message", "minecraft:ambient.cave",
                ObservationCatalog.Audience.REGION, Optional.of(new EventRegionReference("audience", 5)), source);
        source.clear();
        assertEquals(List.of(first, second), base.changes());
        assertThrows(UnsupportedOperationException.class, () -> base.changes().add(first));
        assertEquals(base.fingerprint(), new EventDefinition("quiet_below:test", 4, "Label", "Description", "Message", "minecraft:ambient.cave",
                ObservationCatalog.Audience.REGION, Optional.of(new EventRegionReference("audience", 5)), List.of(first, second)).fingerprint());

        Set<String> variants = new HashSet<>();
        variants.add(new EventDefinition("quiet_below:other", 4, "Label", "Description", "Message", "minecraft:ambient.cave",
                ObservationCatalog.Audience.REGION, Optional.of(new EventRegionReference("audience", 5)), List.of(first, second)).fingerprint());
        variants.add(new EventDefinition("quiet_below:test", 5, "Label", "Description", "Message", "minecraft:ambient.cave",
                ObservationCatalog.Audience.REGION, Optional.of(new EventRegionReference("audience", 5)), List.of(first, second)).fingerprint());
        variants.add(new EventDefinition("quiet_below:test", 4, "Other", "Description", "Message", "minecraft:ambient.cave",
                ObservationCatalog.Audience.REGION, Optional.of(new EventRegionReference("audience", 5)), List.of(first, second)).fingerprint());
        variants.add(new EventDefinition("quiet_below:test", 4, "Label", "Other", "Message", "minecraft:ambient.cave",
                ObservationCatalog.Audience.REGION, Optional.of(new EventRegionReference("audience", 5)), List.of(first, second)).fingerprint());
        variants.add(new EventDefinition("quiet_below:test", 4, "Label", "Description", "Other", "minecraft:ambient.cave",
                ObservationCatalog.Audience.REGION, Optional.of(new EventRegionReference("audience", 5)), List.of(first, second)).fingerprint());
        variants.add(new EventDefinition("quiet_below:test", 4, "Label", "Description", "Message", "minecraft:block.bell.use",
                ObservationCatalog.Audience.REGION, Optional.of(new EventRegionReference("audience", 5)), List.of(first, second)).fingerprint());
        variants.add(new EventDefinition("quiet_below:test", 4, "Label", "Description", "Message", "minecraft:ambient.cave",
                ObservationCatalog.Audience.REGION, Optional.of(new EventRegionReference("audience", 6)), List.of(first, second)).fingerprint());
        variants.add(new EventDefinition("quiet_below:test", 4, "Label", "Description", "Message", "minecraft:ambient.cave",
                ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of(first, second)).fingerprint());
        variants.add(new EventDefinition("quiet_below:test", 4, "Label", "Description", "Message", "minecraft:ambient.cave",
                ObservationCatalog.Audience.REGION, Optional.of(new EventRegionReference("audience", 5)), List.of(second, first)).fingerprint());
        EventRegionChange differentAreaRevision = new EventRegionChange(first.region(), first.areaId(), first.areaRevision() + 1,
                first.state(), first.snapshot());
        variants.add(new EventDefinition("quiet_below:test", 4, "Label", "Description", "Message", "minecraft:ambient.cave",
                ObservationCatalog.Audience.REGION, Optional.of(new EventRegionReference("audience", 5)), List.of(differentAreaRevision, second)).fingerprint());
        EventRegionChange differentState = new EventRegionChange(first.region(), first.areaId(), first.areaRevision(),
                AreaState.START, first.snapshot());
        variants.add(new EventDefinition("quiet_below:test", 4, "Label", "Description", "Message", "minecraft:ambient.cave",
                ObservationCatalog.Audience.REGION, Optional.of(new EventRegionReference("audience", 5)), List.of(differentState, second)).fingerprint());
        EventRegionChange differentSnapshot = new EventRegionChange(first.region(), first.areaId(), first.areaRevision(),
                first.state(), new SnapshotRef("c".repeat(64), first.snapshot().bounds(), first.snapshot().cells(), 2));
        variants.add(new EventDefinition("quiet_below:test", 4, "Label", "Description", "Message", "minecraft:ambient.cave",
                ObservationCatalog.Audience.REGION, Optional.of(new EventRegionReference("audience", 5)), List.of(differentSnapshot, second)).fingerprint());

        assertEquals(12, variants.size());
        for (String variant : variants) assertNotEquals(base.fingerprint(), variant);
    }

    private static EventDefinition world(EventRegionChange... changes) {
        return definition("", "", ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of(changes));
    }

    private static EventDefinition definition(String text, String sound, ObservationCatalog.Audience audience,
                                              Optional<EventRegionReference> audienceRegion,
                                              List<EventRegionChange> changes) {
        return new EventDefinition("quiet_below:test", 1, "Test event", "", text, sound, audience, audienceRegion, changes);
    }

    private static EventRegionChange change(String regionId, String areaId, String dimension, long revision,
                                            int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                            char hashCharacter) {
        EventRegionReference region = new EventRegionReference(regionId, revision);
        AreaBounds bounds = new AreaBounds(regionId, dimension, revision,
                new BlockPosition(minX, minY, minZ), new BlockPosition(maxX, maxY, maxZ));
        SnapshotRef snapshot = new SnapshotRef(String.valueOf(hashCharacter).repeat(64), bounds, bounds.cells(), 1);
        return new EventRegionChange(region, areaId, revision, AreaState.END, snapshot);
    }
}
