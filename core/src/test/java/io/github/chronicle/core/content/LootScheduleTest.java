package io.github.chronicle.core.content;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LootScheduleTest {
    @Test
    void newSessionAndPauseUseOnlySuppliedActiveTicks() {
        LootSchedule first = LootSchedule.advance(LootSchedule.empty(), 4, 900, Map.of("quiet_below:mine", 100L));
        assertEquals(0, first.activeTicks());
        assertEquals(new LootCycle(0, 100, 100), first.types().get("quiet_below:mine"));

        LootSchedule active = LootSchedule.advance(first, 4, 75, Map.of("quiet_below:mine", 100L));
        LootSchedule paused = LootSchedule.advance(active, 4, 0, Map.of("quiet_below:mine", 100L));
        assertEquals(active, paused);

        LootSchedule nextSession = LootSchedule.advance(paused, 5, 50, Map.of("quiet_below:mine", 100L));
        assertEquals(0, nextSession.activeTicks());
        assertEquals(new LootCycle(0, 100, 100), nextSession.types().get("quiet_below:mine"));
    }

    @Test
    void restartIntervalChangesAndNewTypesScheduleFromCurrentClock() {
        LootSchedule loaded = new LootSchedule(2, 40, Map.of("quiet_below:mine", new LootCycle(3, 60, 20)));
        LootSchedule changed = LootSchedule.advance(loaded, 2, 5,
                Map.of("quiet_below:mine", 30L, "quiet_below:city", 10L));
        assertEquals(45, changed.activeTicks());
        assertEquals(new LootCycle(3, 75, 30), changed.types().get("quiet_below:mine"));
        assertEquals(new LootCycle(0, 55, 10), changed.types().get("quiet_below:city"));

        LootSchedule disabled = LootSchedule.advance(changed, 2, 5,
                Map.of("quiet_below:mine", 0L, "quiet_below:city", 10L));
        assertEquals(new LootCycle(3, Long.MAX_VALUE, 0), disabled.types().get("quiet_below:mine"));
    }

    @Test
    void overdueIntervalsAdvanceOnceAndSkipBacklogBursts() {
        LootSchedule start = new LootSchedule(7, 5, Map.of("quiet_below:mine", new LootCycle(2, 10, 5)));
        LootSchedule delayed = LootSchedule.advance(start, 7, 1_000, Map.of("quiet_below:mine", 5L));
        assertEquals(new LootCycle(3, 1_010, 5), delayed.types().get("quiet_below:mine"));
        assertEquals(delayed, LootSchedule.advance(delayed, 7, 0, Map.of("quiet_below:mine", 5L)));
    }

    @Test
    void boundsAndCheckedCountersFailClosed() {
        LinkedHashMap<String, Long> tooMany = new LinkedHashMap<>();
        for (int index = 0; index <= LootSchedule.MAX_TYPES; index++) tooMany.put("test:type_" + index, 1L);
        assertThrows(IllegalArgumentException.class, () -> LootSchedule.advance(LootSchedule.empty(), 1, 0, tooMany));
        assertThrows(IllegalArgumentException.class, () -> LootSchedule.advance(
                new LootSchedule(1, Long.MAX_VALUE, Map.of()), 1, 1, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> LootSchedule.advance(
                new LootSchedule(1, 1, Map.of("test:type", new LootCycle(Long.MAX_VALUE, 1, 1))),
                1, 0, Map.of("test:type", 1L)));
    }
}
