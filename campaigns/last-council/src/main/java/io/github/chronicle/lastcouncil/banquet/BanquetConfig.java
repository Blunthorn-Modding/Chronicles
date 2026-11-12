package io.github.chronicle.lastcouncil.banquet;

/** Reusable scenario settings, separate from private world state. */
public record BanquetConfig(boolean enabled, int onsetMinutes, int durationMinutes) {
    public BanquetConfig {
        if (onsetMinutes < 1 || onsetMinutes > 60 || durationMinutes < 1 || durationMinutes > 60)
            throw new IllegalArgumentException("Banquet timing must be between 1 and 60 minutes");
    }

    public static BanquetConfig defaults() { return new BanquetConfig(false, 2, 8); }
    public BanquetState.Timing timing() { return new BanquetState.Timing(onsetMinutes * 1200L, durationMinutes * 1200L); }
}
