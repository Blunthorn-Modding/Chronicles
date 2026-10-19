package io.github.chronicle.quietbelow.stage;

/** One exact, GM-approved world mutation. */
public record StageEntry(StagePosition position, StageBlockState before, StageBlockState after) {
    public StageEntry {
        if (position == null || before == null || after == null) {
            throw new IllegalArgumentException("stage entry position and states are required");
        }
        if (before.equals(after)) {
            throw new IllegalArgumentException("stage entry must change its block state");
        }
    }
}
