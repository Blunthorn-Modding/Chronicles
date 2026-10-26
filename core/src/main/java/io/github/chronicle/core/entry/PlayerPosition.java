package io.github.chronicle.core.entry;

import java.util.Objects;

/** Exact player position evidence used only to recognize a durable role-entry outcome. */
public record PlayerPosition(String dimension, double x, double y, double z, float yaw, float pitch) {
    public PlayerPosition {
        Objects.requireNonNull(dimension, "position dimension is required");
        if (dimension.isBlank() || dimension.length() > 160 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("position dimension must be a namespaced identifier");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("position coordinates and rotation must be finite");
        }
    }

    /** Deliberately bit-exact: approximate matches are not sufficient crash-recovery evidence. */
    public boolean exactly(PlayerPosition other) {
        return other != null && dimension.equals(other.dimension)
                && Double.doubleToLongBits(x) == Double.doubleToLongBits(other.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(other.y)
                && Double.doubleToLongBits(z) == Double.doubleToLongBits(other.z)
                && Float.floatToIntBits(yaw) == Float.floatToIntBits(other.yaw)
                && Float.floatToIntBits(pitch) == Float.floatToIntBits(other.pitch);
    }
}
