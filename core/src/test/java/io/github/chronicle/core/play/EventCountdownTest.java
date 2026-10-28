package io.github.chronicle.core.play;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventCountdownTest {
    private static final Map<String, Long> EPOCHS = Map.of("test:first", 0L, "test:second", 0L);
    private static final Map<String, Boolean> READY = Map.of("test:first", true, "test:second", true);

    @Test void fullIntervalStartsOnlyAfterTriggerAndEveryPrerequisite() {
        EventProgressState.Entry untriggered = entry(-1, false, EPOCHS, false);
        assertEquals(-1, EventCountdown.observe(untriggered, READY, EPOCHS, 200, true).startedAt());
        EventProgressState.Entry waiting = entry(-1, true, EPOCHS, false);
        waiting = EventCountdown.observe(waiting, Map.of("test:first", true, "test:second", false), EPOCHS, 300, true);
        assertEquals(-1, waiting.startedAt());
        assertFalse(EventCountdown.due(waiting, 40, 1_000));
        EventProgressState.Entry started = EventCountdown.observe(waiting, READY, EPOCHS, 700, true);
        assertEquals(700, started.startedAt());
        assertFalse(EventCountdown.due(started, 40, 739));
        assertTrue(EventCountdown.due(started, 40, 740));
        assertEquals(waiting.actor(), started.actor());
        assertEquals(waiting.occurrence(), started.occurrence());
        assertEquals(waiting.seenFacts(), started.seenFacts());
        assertEquals(waiting.source(), started.source());
    }

    @Test void pauseAndEmptyPlayCannotStartCountdownOrCreateClockCredit() {
        EventProgressState.Entry waiting = entry(-1, true, EPOCHS, false);
        assertEquals(waiting, EventCountdown.observe(waiting, READY, EPOCHS, 100, false));
        EventProgressState.Entry started = EventCountdown.observe(waiting, READY, EPOCHS, 100, true);
        assertFalse(EventCountdown.due(started, 40, 120));
        for (int repeat = 0; repeat < 20; repeat++) {
            // Runtime's occupied clock remains fixed through a pause or an empty server.
            started = EventCountdown.observe(started, READY, EPOCHS, 120, false);
            assertEquals(100, started.startedAt());
            assertFalse(EventCountdown.due(started, 40, 120));
        }
        assertFalse(EventCountdown.due(started, 40, 139));
        assertTrue(EventCountdown.due(started, 40, 140));
    }

    @Test void eitherRequiredOffDiscardsAllElapsedTimeAndNextOnStartsFreshInterval() {
        for (String off : EPOCHS.keySet()) {
            EventProgressState.Entry running = entry(100, true, EPOCHS, false);
            Map<String, Boolean> flags = Map.of("test:first", !off.equals("test:first"), "test:second", !off.equals("test:second"));
            Map<String, Long> changed = Map.of("test:first", off.equals("test:first") ? 1L : 0L,
                    "test:second", off.equals("test:second") ? 1L : 0L);
            EventProgressState.Entry reset = EventCountdown.observe(running, flags, changed, 139, true);
            assertEquals(-1, reset.startedAt());
            assertFalse(EventCountdown.due(reset, 40, 1_000));
            EventProgressState.Entry fresh = EventCountdown.observe(reset, READY, changed, 150, true);
            assertEquals(150, fresh.startedAt());
            assertFalse(EventCountdown.due(fresh, 40, 189));
            assertTrue(EventCountdown.due(fresh, 40, 190));
        }
    }

    @Test void briefOffOnBetweenDispatchesResetsUsingDurableEpochEvenWhenCurrentlyOn() {
        EventProgressState.Entry running = entry(100, true, EPOCHS, false);
        Map<String, Long> changed = Map.of("test:first", 5L, "test:second", 0L);
        EventProgressState.Entry fresh = EventCountdown.observe(running, READY, changed, 139, true);
        assertEquals(139, fresh.startedAt());
        assertEquals(changed, fresh.offVersions());
        assertFalse(EventCountdown.due(fresh, 40, 140));
        // A restart preserves the reset and the original trigger receipt.
        EventProgressState state = new EventProgressState(2, Map.of(fresh.eventId(), fresh));
        EventProgressState.Entry reloaded = EventProgressStore.decode(EventProgressStore.encode(state)).entries().get(fresh.eventId());
        assertEquals(fresh, reloaded);
        assertFalse(EventCountdown.due(reloaded, 40, 178));
        assertTrue(EventCountdown.due(reloaded, 40, 179));
    }

    @Test void unrelatedFlagOffAndRepeatedOnDoNotResetCountdown() {
        EventProgressState.Entry running = entry(100, true, EPOCHS, false);
        Map<String, Boolean> unrelatedOff = Map.of("test:first", true, "test:second", true, "test:unrelated", false);
        EventProgressState.Entry unchanged = EventCountdown.observe(running, unrelatedOff, EPOCHS, 139, true);
        assertEquals(running, unchanged);
        assertTrue(EventCountdown.due(unchanged, 40, 140));
    }

    @Test void missingRequiredFlagPreventsStartAndSkippedEntryNeverBecomesDue() {
        EventProgressState.Entry waiting = entry(-1, true, EPOCHS, false);
        assertEquals(-1, EventCountdown.observe(waiting, Map.of("test:first", true), EPOCHS, 100, true).startedAt());
        EventProgressState.Entry skipped = entry(-1, true, EPOCHS, true);
        assertEquals(-1, EventCountdown.observe(skipped, READY, EPOCHS, 100, true).startedAt());
        assertFalse(EventCountdown.due(skipped, 0, 100));
    }

    @Test void noRequirementsStartsAtTriggerAndZeroDelayIsImmediatelyDue() {
        EventProgressState.Entry started = EventCountdown.observe(entry(-1, true, Map.of(), false), Map.of(), Map.of(), 77, true);
        assertEquals(77, started.startedAt());
        assertTrue(EventCountdown.due(started, 0, 77));
        assertFalse(EventCountdown.due(started, 1, 77));
        assertTrue(EventCountdown.due(started, 1, 78));
    }

    @Test void futurePinnedCountdownAndNegativeClockAreRefused() {
        assertThrows(IllegalStateException.class, () -> EventCountdown.observe(entry(101, true, EPOCHS, false), READY, EPOCHS, 100, true));
        Map<String, Long> changed = Map.of("test:first", 1L, "test:second", 0L);
        assertThrows(IllegalStateException.class, () -> EventCountdown.observe(entry(101, true, EPOCHS, false), READY, changed, 100, true));
        assertThrows(IllegalStateException.class, () -> EventCountdown.observe(entry(101, true, EPOCHS, false), Map.of(), EPOCHS, 100, true));
        assertThrows(IllegalArgumentException.class, () -> EventCountdown.observe(entry(-1, true, EPOCHS, false), READY, EPOCHS, -1, true));
        assertFalse(EventCountdown.due(entry(101, true, EPOCHS, false), 0, 100));
    }

    private static EventProgressState.Entry entry(long startedAt, boolean triggered, Map<String, Long> epochs, boolean skipped) {
        return new EventProgressState.Entry("test:event", "a".repeat(64), startedAt, triggered,
                UUID.fromString("11111111-1111-1111-1111-111111111111"), PlayState.DeliverySource.SYSTEM,
                "book:occurrence:42", true, Set.of("book:occurrence:41", "book:occurrence:42"), epochs, skipped);
    }
}
