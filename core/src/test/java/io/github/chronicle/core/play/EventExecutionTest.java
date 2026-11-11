package io.github.chronicle.core.play;

import io.github.chronicle.core.areas.AreaBounds;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.areas.SnapshotRef;
import io.github.chronicle.core.authoring.BlockPosition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventExecutionTest {
    private static final UUID OPERATION = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ACTOR = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void validatesIdentityActorRecipientsAndWorldPlanShape() {
        EventConfiguration media = configuration(definition("quiet_below:media", false), PlayState.Mode.MANUAL,
                Optional.empty(), "", true);
        EventExecution execution = execution(media, EventExecution.Phase.PREPARED, PlayState.DeliverySource.MANUAL,
                ACTOR, "");
        assertEquals(List.of(PLAYER), execution.recipients());
        assertThrows(UnsupportedOperationException.class, () -> execution.recipients().add(ACTOR));

        assertThrows(IllegalArgumentException.class, () -> new EventExecution("quiet_below:other", OPERATION, media,
                EventExecution.Phase.PREPARED, PlayState.DeliverySource.MANUAL, ACTOR, 1, 10, List.of(PLAYER), "", ""));
        assertThrows(IllegalArgumentException.class, () -> new EventExecution(media.definition().id(), OPERATION, media,
                EventExecution.Phase.PREPARED, PlayState.DeliverySource.MANUAL, null, 1, 10, List.of(PLAYER), "", ""));
        assertThrows(IllegalArgumentException.class, () -> new EventExecution(media.definition().id(), OPERATION, media,
                EventExecution.Phase.PREPARED, PlayState.DeliverySource.TIMER, null, 0, 10, List.of(PLAYER), "", ""));
        assertThrows(IllegalArgumentException.class, () -> new EventExecution(media.definition().id(), OPERATION, media,
                EventExecution.Phase.PREPARED, PlayState.DeliverySource.TIMER, null, 1, -1, List.of(PLAYER), "", ""));
        assertThrows(IllegalArgumentException.class, () -> new EventExecution(media.definition().id(), OPERATION, media,
                EventExecution.Phase.PREPARED, PlayState.DeliverySource.TIMER, null, 1, 10, List.of(PLAYER, PLAYER), "", ""));
        assertThrows(IllegalArgumentException.class, () -> new EventExecution(media.definition().id(), OPERATION, media,
                EventExecution.Phase.PREPARED, PlayState.DeliverySource.TIMER, null, 1, 10, List.of(PLAYER), "a".repeat(64), ""));

        EventConfiguration world = configuration(definition("quiet_below:world", true), PlayState.Mode.TIMER,
                Optional.empty(), "", true);
        assertThrows(IllegalArgumentException.class, () -> new EventExecution(world.definition().id(), OPERATION, world,
                EventExecution.Phase.PREPARED, PlayState.DeliverySource.TIMER, null, 1, 10, List.of(PLAYER), "", ""));
        new EventExecution(world.definition().id(), OPERATION, world, EventExecution.Phase.PREPARED,
                PlayState.DeliverySource.TIMER, null, 1, 10, List.of(PLAYER), "a".repeat(64), "");
    }

    @Test
    void mediaOnlyExecutionRejectsTerrainPhasesAndTerminalIsExplicit() {
        EventConfiguration configuration = configuration(definition("quiet_below:media", false), PlayState.Mode.TIMER,
                Optional.empty(), "", true);
        for (EventExecution.Phase phase : List.of(EventExecution.Phase.APPLYING,
                EventExecution.Phase.TERRAIN_SAVED, EventExecution.Phase.CONFLICT)) {
            assertThrows(IllegalArgumentException.class, () -> execution(configuration, phase,
                    PlayState.DeliverySource.TIMER, null, ""));
        }
        EventExecution prepared = execution(configuration, EventExecution.Phase.PREPARED,
                PlayState.DeliverySource.TIMER, null, "");
        assertFalse(prepared.terminal());
        EventExecution complete = prepared.with(EventExecution.Phase.COMPLETE, "Finished");
        assertTrue(complete.terminal());
        assertEquals(prepared.operationId(), complete.operationId());
        assertEquals("Finished", complete.detail());
        assertTrue(prepared.with(EventExecution.Phase.MEDIA_UNCERTAIN, "Delivery uncertain").terminal());
    }

    @Test
    void configurationFingerprintPinsDefinitionAndTriggerFields() {
        EventDefinition definition = definition("quiet_below:entry", false);
        EventRegionReference first = new EventRegionReference("entrance", 2);
        EventRegionReference second = new EventRegionReference("entrance", 3);
        EventConfiguration base = new EventConfiguration(definition, PlayState.Mode.ENTRY, Optional.of(first),
                20, 0, "quiet_below:ready", true);
        EventConfiguration changed = new EventConfiguration(definition, PlayState.Mode.ENTRY, Optional.of(second),
                20, 0, "quiet_below:ready", true);
        EventExecution one = execution(base, EventExecution.Phase.PREPARED, PlayState.DeliverySource.ENTRY, ACTOR, "");
        EventExecution same = execution(base, EventExecution.Phase.COMPLETE, PlayState.DeliverySource.ENTRY, ACTOR, "done");
        EventExecution other = execution(changed, EventExecution.Phase.PREPARED, PlayState.DeliverySource.ENTRY, ACTOR, "");
        assertEquals(one.configurationFingerprint(), same.configurationFingerprint());
        assertNotEquals(one.configurationFingerprint(), other.configurationFingerprint());
    }

    @Test
    void repositoryDefaultFailsClosedForUnsupportedArchive() {
        PlayRepository repository = new PlayRepository() {
            @Override public PlayState load() { return PlayState.empty(); }
            @Override public void save(PlayState state) { }
        };
        EventConfiguration configuration = configuration(definition("quiet_below:media", false), PlayState.Mode.TIMER,
                Optional.empty(), "", true);
        assertThrows(IOException.class, () -> repository.archiveEvent(execution(configuration,
                EventExecution.Phase.COMPLETE, PlayState.DeliverySource.TIMER, null, "done")));
    }

    static EventExecution execution(EventConfiguration configuration, EventExecution.Phase phase,
                                    PlayState.DeliverySource source, UUID actor, String detail) {
        String plan = configuration.definition().changes().isEmpty() ? "" : "f".repeat(64);
        return new EventExecution(configuration.definition().id(), OPERATION, configuration, phase, source, actor,
                1, 10, List.of(PLAYER), plan, detail);
    }

    static EventConfiguration configuration(EventDefinition definition, PlayState.Mode mode,
                                            Optional<EventRegionReference> trigger, String flag, boolean enabled) {
        long delay = mode == PlayState.Mode.TIMER ? 20 : 0;
        return new EventConfiguration(definition, mode, trigger, 5, delay, flag, enabled);
    }

    static EventDefinition definition(String id, boolean worldChange) {
        List<EventRegionChange> changes = worldChange ? List.of(change()) : List.of();
        return new EventDefinition(id, 1, "Event", "", worldChange ? "" : "Message", "",
                ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), changes);
    }

    private static EventRegionChange change() {
        EventRegionReference region = new EventRegionReference("city", 2);
        AreaBounds bounds = new AreaBounds("city", "minecraft:overworld", 2,
                new BlockPosition(0, 64, 0), new BlockPosition(0, 64, 0));
        SnapshotRef snapshot = new SnapshotRef("e".repeat(64), bounds, 1, 100);
        return new EventRegionChange(region, "city_area", 3, AreaState.END, snapshot);
    }
}
