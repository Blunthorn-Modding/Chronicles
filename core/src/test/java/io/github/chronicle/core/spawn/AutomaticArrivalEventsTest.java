package io.github.chronicle.core.spawn;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticArrivalEventsTest {
    private final UUID player = UUID.randomUUID();
    @Test void joinsAndRealActiveTransitionsAreConsumedOnce() {
        var events = new AutomaticArrivalEvents();
        assertEquals(Set.of(player), events.next(true, false, Set.of(player)));
        assertTrue(events.next(true, false, Set.of(player)).isEmpty());
        events.joined(player);
        assertEquals(Set.of(player), events.next(true, false, Set.of(player)));
        events.next(false, false, Set.of(player));
        assertEquals(Set.of(player), events.next(true, false, Set.of(player)));
    }
    @Test void holdReleaseDoesNotRequeueSkippedPlayersOrDelayHeldJoins() {
        var events = new AutomaticArrivalEvents();
        assertEquals(Set.of(player), events.next(true, false, Set.of(player)));
        assertTrue(events.next(true, true, Set.of(player)).isEmpty());
        assertTrue(events.next(true, false, Set.of(player)).isEmpty());
        events.joined(player);
        assertTrue(events.next(true, true, Set.of(player)).isEmpty());
        assertTrue(events.next(true, false, Set.of(player)).isEmpty());
        var startedHeld = new AutomaticArrivalEvents();
        assertTrue(startedHeld.next(true, true, Set.of(player)).isEmpty());
        assertTrue(startedHeld.next(true, false, Set.of(player)).isEmpty());
    }
    @Test void disconnectAndInactiveJoinDoNotCreateLaterSyntheticEvents() {
        var events = new AutomaticArrivalEvents();
        events.next(true, false, Set.of());
        events.joined(player); events.disconnected(player);
        assertTrue(events.next(true, false, Set.of()).isEmpty());
        events.joined(player); events.next(false, false, Set.of(player));
        assertTrue(events.next(false, false, Set.of(player)).isEmpty());
    }
}
