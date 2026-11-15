package io.github.chronicle.lastcouncil.banquet;

/** Reusable scenario settings, separate from private world state. */
public record BanquetConfig(boolean enabled, int onsetMinutes, int symptomMinutes, int poisonMinutes) {
    public BanquetConfig(boolean enabled, int onsetMinutes, int legacyDurationMinutes) {
        this(enabled, onsetMinutes, legacyStage(onsetMinutes, legacyDurationMinutes, true),
                legacyStage(onsetMinutes, legacyDurationMinutes, false));
    }

    private static int legacyStage(int onset, int duration, boolean mild) {
        if (duration < 1 || duration > 60) throw new IllegalArgumentException("Invalid legacy duration");
        if (onset == 2 && duration == 8) return 2;
        return Math.max(1, mild ? (duration + 1) / 2 : duration / 2);
    }

    public BanquetConfig {
        if (onsetMinutes < 1 || onsetMinutes > 60 || symptomMinutes < 1 || symptomMinutes > 60
                || poisonMinutes < 1 || poisonMinutes > 60)
            throw new IllegalArgumentException("Banquet timing must be between 1 and 60 minutes");
    }

    public static BanquetConfig defaults() { return new BanquetConfig(false, 2, 2, 2); }
    /** Compatibility for screens built before the separate symptom and poison settings. */
    public int durationMinutes() { return symptomMinutes + poisonMinutes; }
    public BanquetState.Timing timing() {
        long symptoms = symptomMinutes * 1200L;
        long poison = poisonMinutes * 1200L;
        return new BanquetState.Timing(onsetMinutes * 1200L, symptoms + poison, poison, true);
    }
}
