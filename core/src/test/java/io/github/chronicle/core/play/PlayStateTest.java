package io.github.chronicle.core.play;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayStateTest {
    private static final String FINGERPRINT = "0".repeat(64);

    @Test
    void stateDefensivelyOrdersAndCopiesCollections() {
        UUID later = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID earlier = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var participants = new java.util.LinkedHashSet<>(List.of(later, earlier));
        PlayState state = new PlayState(0, 0, participants, Map.of(), Map.of());
        participants.clear();
        assertEquals(List.of(earlier, later), List.copyOf(state.participants()));
        assertThrows(UnsupportedOperationException.class, () -> state.participants().clear());
    }

    @Test
    void rejectsBoundsInvalidPinsAndInvalidUncertainReceipts() {
        Map<String, PlayState.Binding> tooMany = new HashMap<>();
        for (int index = 0; index <= PlayState.MAX_BINDINGS; index++) {
            String id = "test:item" + index;
            tooMany.put(id, new PlayState.Binding(id, 1, FINGERPRINT, PlayState.Mode.MANUAL, "", 0, 0, 0, ""));
        }
        assertThrows(IllegalArgumentException.class, () -> new PlayState(1, 0, Set.of(), tooMany, Map.of()));
        PlayState.Binding future = new PlayState.Binding("test:future", 2, FINGERPRINT, PlayState.Mode.MANUAL, "", 0, 0, 0, "");
        assertThrows(IllegalArgumentException.class, () -> new PlayState(1, 0, Set.of(), Map.of(future.id(), future), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new PlayState.Binding("test:entry", 1, FINGERPRINT, PlayState.Mode.ENTRY, "", 0, 0, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> new PlayState.Delivery("test:manual", PlayState.Status.CLAIMED,
                PlayState.DeliverySource.MANUAL, null, 1, 0, List.of(UUID.randomUUID())));
        UUID player = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new PlayState.Delivery("test:duplicate", PlayState.Status.CLAIMED,
                PlayState.DeliverySource.TIMER, null, 1, 0, List.of(player, player)));
    }

    @Test
    void eventMapsAreOrderedCopiedAndKeepHistoricalRecipientsOutsideCurrentParticipants() {
        EventConfiguration later = configuration("quiet_below:zeta", 1, 5);
        EventConfiguration earlier = configuration("quiet_below:alpha", 1, 5);
        Map<String, EventConfiguration> events = new java.util.LinkedHashMap<>();
        events.put(later.definition().id(), later);
        events.put(earlier.definition().id(), earlier);
        EventExecution execution = EventExecutionTest.execution(earlier, EventExecution.Phase.PREPARED,
                PlayState.DeliverySource.TIMER, null, "pending");
        PlayState state = new PlayState(2, 10, Set.of(), Map.of(), Map.of(), events,
                Map.of(execution.eventId(), execution));
        events.clear();

        assertEquals(List.of("quiet_below:alpha", "quiet_below:zeta"), List.copyOf(state.events().keySet()));
        assertEquals(execution, state.executions().get(execution.eventId()));
        assertThrows(UnsupportedOperationException.class, () -> state.events().clear());
        assertThrows(UnsupportedOperationException.class, () -> state.executions().clear());
    }

    @Test
    void rejectsEventCapsKeysAndFutureDefinitionOrExecutionTimes() {
        Map<String, EventConfiguration> tooManyEvents = new HashMap<>();
        Map<String, EventExecution> tooManyExecutions = new HashMap<>();
        for (int index = 0; index <= PlayState.MAX_EVENTS; index++) {
            EventConfiguration configuration = configuration("test:event" + index, 1, 0);
            tooManyEvents.put(configuration.definition().id(), configuration);
            EventExecution execution = EventExecutionTest.execution(configuration, EventExecution.Phase.PREPARED,
                    PlayState.DeliverySource.TIMER, null, "");
            tooManyExecutions.put(execution.eventId(), execution);
        }
        assertThrows(IllegalArgumentException.class, () -> new PlayState(1, 10, Set.of(), Map.of(), Map.of(),
                tooManyEvents, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new PlayState(1, 10, Set.of(), Map.of(), Map.of(),
                Map.of(), tooManyExecutions));

        EventConfiguration futureRevision = configuration("test:future_revision", 2, 0);
        assertThrows(IllegalArgumentException.class, () -> new PlayState(1, 10, Set.of(), Map.of(), Map.of(),
                Map.of(futureRevision.definition().id(), futureRevision), Map.of()));
        EventConfiguration futureArmed = configuration("test:future_armed", 1, 11);
        assertThrows(IllegalArgumentException.class, () -> new PlayState(1, 10, Set.of(), Map.of(), Map.of(),
                Map.of(futureArmed.definition().id(), futureArmed), Map.of()));
        EventConfiguration current = configuration("test:current", 1, 0);
        assertThrows(IllegalArgumentException.class, () -> new PlayState(1, 10, Set.of(), Map.of(), Map.of(),
                Map.of("test:wrong", current), Map.of()));

        EventExecution late = new EventExecution(current.definition().id(), UUID.randomUUID(), current,
                EventExecution.Phase.PREPARED, PlayState.DeliverySource.TIMER, null, 1, 11,
                List.of(UUID.randomUUID()), "", "");
        assertThrows(IllegalArgumentException.class, () -> new PlayState(1, 10, Set.of(), Map.of(), Map.of(),
                Map.of(), Map.of(late.eventId(), late)));
        EventExecution pending = EventExecutionTest.execution(current, EventExecution.Phase.PREPARED,
                PlayState.DeliverySource.TIMER, null, "");
        assertThrows(IllegalArgumentException.class, () -> new PlayState(1, 10, Set.of(), Map.of(), Map.of(),
                Map.of(), Map.of("test:wrong", pending)));
    }

    private static EventConfiguration configuration(String id, long revision, long armedAtTicks) {
        EventDefinition definition = new EventDefinition(id, revision, "Event", "", "Message", "",
                ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of());
        return new EventConfiguration(definition, PlayState.Mode.TIMER, Optional.empty(), armedAtTicks, 20, "", true);
    }
}
