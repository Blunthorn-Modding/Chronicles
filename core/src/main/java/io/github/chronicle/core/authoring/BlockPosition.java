package io.github.chronicle.core.authoring;

/**
 * An immutable block coordinate accepted by Chronicle authoring. Horizontal coordinates use the
 * vanilla world-border limit; the deliberately smaller vertical limit keeps persisted cuboids
 * practical for custom dimensions without depending on a Minecraft class.
 */
public record BlockPosition(int x, int y, int z) {
    public static final int MAX_HORIZONTAL = 30_000_000;
    public static final int MAX_VERTICAL = 2_000_000;

    public BlockPosition {
        if (Math.abs((long) x) > MAX_HORIZONTAL || Math.abs((long) z) > MAX_HORIZONTAL) {
            throw new IllegalArgumentException("block position is outside the Minecraft horizontal bounds");
        }
        if (Math.abs((long) y) > MAX_VERTICAL) {
            throw new IllegalArgumentException("block position is outside the supported vertical bounds");
        }
    }
}
