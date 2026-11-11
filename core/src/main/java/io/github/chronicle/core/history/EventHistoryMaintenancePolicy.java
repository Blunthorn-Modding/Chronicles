package io.github.chronicle.core.history;

import java.util.Objects;

/**
 * Pure, per-server quiet-period gate for automatic Event-history maintenance.
 * Callers provide a monotonic server tick and must reset it after an explicit reload.
 */
public final class EventHistoryMaintenancePolicy {
    public static final int TICKS_PER_SECOND = 20;
    private long quietSince = -1;
    private long lastAttempt = -1;

    public record Decision(boolean attempt, long quietElapsedTicks, long waitTicks) { }

    /** A non-eligible or work-free observation discards quiet credit. */
    public Decision observe(long tick, EventHistoryMaintenanceConfig config, boolean eligible, boolean hasWork) {
        Objects.requireNonNull(config, "event history maintenance config is required");
        if (tick < 0) throw new IllegalArgumentException("server tick must not be negative");
        if (!config.enabled() || !eligible || !hasWork) {
            quietSince = -1;
            return new Decision(false, 0, 0);
        }
        if (quietSince < 0) {
            quietSince = tick;
            return new Decision(false, 0, quietTicks(config));
        }
        long quietElapsed = Math.max(0, tick - quietSince);
        long remainingQuiet = Math.max(0, quietTicks(config) - quietElapsed);
        if (remainingQuiet > 0) return new Decision(false, quietElapsed, remainingQuiet);
        long remainingInterval = lastAttempt < 0 ? 0 : Math.max(0, intervalTicks(config) - Math.max(0, tick - lastAttempt));
        if (remainingInterval > 0) return new Decision(false, quietElapsed, remainingInterval);
        lastAttempt = tick;
        return new Decision(true, quietElapsed, 0);
    }

    public void reset() { quietSince = -1; lastAttempt = -1; }
    /** Brief activity between coarse polls cancels quiet credit without bypassing attempt spacing. */
    public void interruptQuiet() { quietSince = -1; }
    public long quietElapsedTicks(long tick) { return quietSince < 0 ? 0 : Math.max(0, tick - quietSince); }
    private static long quietTicks(EventHistoryMaintenanceConfig config) { return Math.multiplyExact((long) config.quietSeconds(), TICKS_PER_SECOND); }
    private static long intervalTicks(EventHistoryMaintenanceConfig config) { return Math.multiplyExact((long) config.intervalSeconds(), TICKS_PER_SECOND); }
}
