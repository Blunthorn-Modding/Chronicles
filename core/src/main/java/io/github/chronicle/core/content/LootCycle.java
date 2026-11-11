package io.github.chronicle.core.content;

/** Persisted refill generation and next due active-play tick for one loot type. */
public record LootCycle(long generation, long nextRefillTick, long intervalTicks) {
    public LootCycle {
        if (generation < 0) throw new IllegalArgumentException("loot generation cannot be negative");
        if (nextRefillTick < 0) throw new IllegalArgumentException("next refill tick cannot be negative");
        if (intervalTicks < 0) throw new IllegalArgumentException("refill interval cannot be negative");
        if (intervalTicks == 0 && nextRefillTick != Long.MAX_VALUE) {
            throw new IllegalArgumentException("disabled refill must use Long.MAX_VALUE as its next refill tick");
        }
        if (intervalTicks > 0 && nextRefillTick == Long.MAX_VALUE) {
            throw new IllegalArgumentException("enabled refill must have a finite next refill tick");
        }
    }
}
