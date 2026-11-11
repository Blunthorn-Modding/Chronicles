package io.github.chronicle.core.areas;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;

import java.util.Objects;

/** Exact revision-pinned Core Region geometry used by captures and operations. */
public record AreaBounds(String regionId, String dimension, long regionRevision,
                         BlockPosition min, BlockPosition max) {
    public static final int MAX_AXIS = 512;
    public static final int MAX_CELLS = 262_144;
    public static final int MAX_CHUNKS = 64;

    public AreaBounds {
        AuthoringSite.validateCommon(regionId, dimension, regionRevision);
        Objects.requireNonNull(min, "minimum is required");
        Objects.requireNonNull(max, "maximum is required");
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new IllegalArgumentException("area bounds must be normalized");
        }
        int x = axis(min.x(), max.x(), "x");
        int y = axis(min.y(), max.y(), "y");
        int z = axis(min.z(), max.z(), "z");
        long cells = Math.multiplyExact(Math.multiplyExact((long) x, y), z);
        if (cells > MAX_CELLS) throw new IllegalArgumentException("area exceeds " + MAX_CELLS + " cells");
        long chunks = Math.multiplyExact((long) chunkSpan(min.x(), max.x()), chunkSpan(min.z(), max.z()));
        if (chunks > MAX_CHUNKS) throw new IllegalArgumentException("area intersects more than " + MAX_CHUNKS + " chunks");
    }

    public static AreaBounds from(AuthoringSite.Region region) {
        Objects.requireNonNull(region, "region is required");
        return new AreaBounds(region.id(), region.dimension(), region.revision(), region.min(), region.max());
    }

    public boolean matches(AuthoringSite.Region region) { return equals(from(region)); }
    public int sizeX() { return max.x() - min.x() + 1; }
    public int sizeY() { return max.y() - min.y() + 1; }
    public int sizeZ() { return max.z() - min.z() + 1; }
    public int cells() { return Math.multiplyExact(Math.multiplyExact(sizeX(), sizeY()), sizeZ()); }
    public int chunks() { return chunkSpan(min.x(), max.x()) * chunkSpan(min.z(), max.z()); }

    private static int axis(int min, int max, String name) {
        long size = (long) max - min + 1;
        if (size < 1 || size > MAX_AXIS) throw new IllegalArgumentException(name + " axis must be 1.." + MAX_AXIS);
        return (int) size;
    }

    private static int chunkSpan(int min, int max) { return Math.floorDiv(max, 16) - Math.floorDiv(min, 16) + 1; }
}
