package io.github.chronicle.lastcouncil.complications;

import java.util.*;

/** Reusable settings. Times are active Council ticks, not wall-clock time. */
public record ComplicationConfig(String poolId, int total, List<Integer> meetings,
                                 long earliestTicks, long latestTicks, long spacingTicks) {
    public static final long DAY = 20L * 60 * 60 * 24;
    public ComplicationConfig {
        if (poolId == null || !poolId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || poolId.length() > 128 || total < 0 || total > 32)
            throw new IllegalArgumentException("Invalid complication configuration");
        meetings = List.copyOf(meetings);
        if (meetings.size() > 64 || earliestTicks < 0 || latestTicks < earliestTicks || latestTicks > DAY || spacingTicks < 0 || spacingTicks > DAY)
            throw new IllegalArgumentException("Invalid complication bounds");
        int previous = 0;
        for (int meeting : meetings) {
            if (meeting <= previous || meeting > 64) throw new IllegalArgumentException("Meetings must be sorted and unique");
            previous = meeting;
        }
        if (total > 0 && (meetings.isEmpty() || (long) meetings.size() * (spacingTicks == 0 ? 32 : Math.min(32, 1 + (latestTicks - earliestTicks) / spacingTicks)) < total))
            throw new IllegalArgumentException("Insufficient meeting capacity");
    }
    public int capacity() { return spacingTicks == 0 ? 32 : (int)Math.min(32, 1 + (latestTicks - earliestTicks) / spacingTicks); }
    public static ComplicationConfig defaults() { return new ComplicationConfig("last_council:complications", 2, List.of(1, 2, 3), 6000, 30000, 6000); }
}
