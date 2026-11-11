package io.github.chronicle.quietbelow.stage;

/** Narrow world port used by the pure domain and by fake-world tests. */
public interface StageWorld {
    String dimension();
    boolean isLoaded(StagePosition position);
    boolean isInBuildLimit(StagePosition position);
    boolean isInsideWorldBorder(StagePosition position);
    boolean hasBlockEntity(StagePosition position);
    StageBlockState getBlockState(StagePosition position);
    boolean setBlockState(StagePosition position, StageBlockState state);
}
