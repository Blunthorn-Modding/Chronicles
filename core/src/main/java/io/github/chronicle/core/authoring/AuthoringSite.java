package io.github.chronicle.core.authoring;

import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable authoring definitions shared by campaigns, without Minecraft runtime dependencies. */
public sealed interface AuthoringSite permits AuthoringSite.Region, AuthoringSite.Location, AuthoringSite.Anchor {
    // Legacy plain names stay valid. Registered campaign roles use namespace:name.
    Pattern ID = Pattern.compile("[a-z][a-z0-9_]{0,63}(?::[a-z][a-z0-9_]{0,63})?");
    Pattern NAMESPACED_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    String id();

    String dimension();

    long revision();

    AuthoringSite withRevision(long revision);

    static void validateCommon(String id, String dimension, long revision) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("site ID must match " + ID.pattern());
        }
        if (dimension == null || !NAMESPACED_ID.matcher(dimension).matches()) {
            throw new IllegalArgumentException("dimension must be a namespaced registry ID");
        }
        if (revision < 1) {
            throw new IllegalArgumentException("site revision must be positive");
        }
    }

    /** Inclusive, normalized cuboid. Its volume is saturated at {@link Long#MAX_VALUE}. */
    record Region(String id, String dimension, long revision, BlockPosition min, BlockPosition max) implements AuthoringSite {
        public Region {
            validateCommon(id, dimension, revision);
            Objects.requireNonNull(min, "region minimum is required");
            Objects.requireNonNull(max, "region maximum is required");
            BlockPosition suppliedMin = min;
            BlockPosition suppliedMax = max;
            min = new BlockPosition(Math.min(suppliedMin.x(), suppliedMax.x()), Math.min(suppliedMin.y(), suppliedMax.y()), Math.min(suppliedMin.z(), suppliedMax.z()));
            max = new BlockPosition(Math.max(suppliedMin.x(), suppliedMax.x()), Math.max(suppliedMin.y(), suppliedMax.y()), Math.max(suppliedMin.z(), suppliedMax.z()));
        }

        @Override
        public Region withRevision(long revision) {
            return new Region(id, dimension, revision, min, max);
        }

        public boolean contains(BlockPosition position) {
            Objects.requireNonNull(position, "block position is required");
            return position.x() >= min.x() && position.x() <= max.x()
                    && position.y() >= min.y() && position.y() <= max.y()
                    && position.z() >= min.z() && position.z() <= max.z();
        }

        public long volume() {
            try {
                return Math.multiplyExact(Math.multiplyExact((long) max.x() - min.x() + 1, (long) max.y() - min.y() + 1),
                        (long) max.z() - min.z() + 1);
            } catch (ArithmeticException ignored) {
                return Long.MAX_VALUE;
            }
        }
    }

    /** A precise point and the facing recorded by the builder. */
    record Location(String id, String dimension, long revision, double x, double y, double z, float yaw, float pitch) implements AuthoringSite {
        public Location {
            validateCommon(id, dimension, revision);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                throw new IllegalArgumentException("location coordinates and facing must be finite");
            }
            if (Math.abs(x) > BlockPosition.MAX_HORIZONTAL || Math.abs(z) > BlockPosition.MAX_HORIZONTAL
                    || Math.abs(y) > BlockPosition.MAX_VERTICAL) {
                throw new IllegalArgumentException("location is outside the supported world bounds");
            }
        }

        @Override
        public Location withRevision(long revision) {
            return new Location(id, dimension, revision, x, y, z, yaw, pitch);
        }
    }

    /** A block position with the registry type observed when the builder created the binding. */
    record Anchor(String id, String dimension, long revision, BlockPosition position, String expectedBlock) implements AuthoringSite {
        public Anchor {
            validateCommon(id, dimension, revision);
            Objects.requireNonNull(position, "anchor position is required");
            if (expectedBlock == null || !NAMESPACED_ID.matcher(expectedBlock).matches()) {
                throw new IllegalArgumentException("expected block must be a namespaced registry ID");
            }
        }

        @Override
        public Anchor withRevision(long revision) {
            return new Anchor(id, dimension, revision, position, expectedBlock);
        }
    }
}
