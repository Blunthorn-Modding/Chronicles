package io.github.chronicle.core.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pure active-play refill clock. Callers supply elapsed ACTIVE ticks; pauses supply zero. */
public record LootSchedule(int sessionNumber, long activeTicks, Map<String, LootCycle> types) {
    public static final int MAX_TYPES = 128;

    public LootSchedule {
        if (sessionNumber < -1) throw new IllegalArgumentException("schedule session cannot be less than -1");
        if (activeTicks < 0) throw new IllegalArgumentException("schedule active ticks cannot be negative");
        if (sessionNumber == -1 && activeTicks != 0) throw new IllegalArgumentException("empty schedule must have zero active ticks");
        if (types == null) throw new IllegalArgumentException("schedule types are required");
        if (types.size() > MAX_TYPES) throw new IllegalArgumentException("schedule may contain at most " + MAX_TYPES + " types");
        LinkedHashMap<String, LootCycle> copy = new LinkedHashMap<>();
        types.forEach((id, cycle) -> {
            ContentIds.requireNamespacedId(id, "loot type");
            if (cycle == null) throw new IllegalArgumentException("loot cycle is required for " + id);
            copy.put(id, cycle);
        });
        if (sessionNumber == -1 && !copy.isEmpty()) throw new IllegalArgumentException("empty schedule cannot contain loot cycles");
        types = Collections.unmodifiableMap(copy);
    }

    public static LootSchedule empty() { return new LootSchedule(-1, 0, Map.of()); }

    public static LootSchedule advance(LootSchedule previous, int session, long elapsedTicks,
                                       Map<String, Long> intervals) {
        if (previous == null) throw new IllegalArgumentException("previous loot schedule is required");
        if (session < 0) throw new IllegalArgumentException("schedule session cannot be negative");
        if (elapsedTicks < 0) throw new IllegalArgumentException("elapsed active ticks cannot be negative");
        if (previous.sessionNumber() > session) throw new IllegalArgumentException("schedule session cannot move backwards");
        LinkedHashMap<String, Long> bounded = validateIntervals(intervals);
        if (previous.sessionNumber() != session) {
            LinkedHashMap<String, LootCycle> reset = new LinkedHashMap<>();
            bounded.forEach((id, interval) -> reset.put(id, new LootCycle(0, dueAt(0, interval), interval)));
            return new LootSchedule(session, 0, reset);
        }

        long now;
        try { now = Math.addExact(previous.activeTicks(), elapsedTicks); }
        catch (ArithmeticException exception) { throw new IllegalArgumentException("schedule active tick counter is exhausted", exception); }
        LinkedHashMap<String, LootCycle> advanced = new LinkedHashMap<>();
        bounded.forEach((id, interval) -> {
            LootCycle prior = previous.types().get(id);
            if (prior == null) {
                advanced.put(id, new LootCycle(0, dueAt(now, interval), interval));
            } else if (prior.intervalTicks() != interval) {
                advanced.put(id, new LootCycle(prior.generation(), dueAt(now, interval), interval));
            } else if (interval > 0 && now >= prior.nextRefillTick()) {
                long generation;
                try { generation = Math.addExact(prior.generation(), 1); }
                catch (ArithmeticException exception) { throw new IllegalArgumentException("loot generation is exhausted for " + id, exception); }
                advanced.put(id, new LootCycle(generation, dueAt(now, interval), interval));
            } else {
                advanced.put(id, prior);
            }
        });
        return new LootSchedule(session, now, advanced);
    }

    private static LinkedHashMap<String, Long> validateIntervals(Map<String, Long> intervals) {
        if (intervals == null) throw new IllegalArgumentException("loot intervals are required");
        if (intervals.size() > MAX_TYPES) throw new IllegalArgumentException("loot intervals may contain at most " + MAX_TYPES + " types");
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        intervals.forEach((id, interval) -> {
            ContentIds.requireNamespacedId(id, "loot type");
            if (interval == null || interval < 0) throw new IllegalArgumentException("loot interval must be nonnegative for " + id);
            result.put(id, interval);
        });
        return result;
    }

    private static long dueAt(long now, long interval) {
        if (interval == 0) return Long.MAX_VALUE;
        try { return Math.addExact(now, interval); }
        catch (ArithmeticException exception) { throw new IllegalArgumentException("next refill tick is outside long range", exception); }
    }
}
