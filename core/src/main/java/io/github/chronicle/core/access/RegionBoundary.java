package io.github.chronicle.core.access;

import io.github.chronicle.core.authoring.AuthoringSite;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** The union of the Region's inclusive authored blocks, with exact swept hitbox checks for movement. */
public final class RegionBoundary {
    private RegionBoundary() { }

    public static Box volume(AuthoringSite.Region region) {
        return new Box(region.min().x(), region.min().y(), region.min().z(),
                region.max().x() + 1.0, region.max().y() + 1.0, region.max().z() + 1.0);
    }

    public static Box standingAt(AuthoringSite.Location point) {
        return new Box(point.x() - .3, point.y(), point.z() - .3,
                point.x() + .3, point.y() + 1.8, point.z() + .3);
    }

    public static boolean crosses(Box region, Box source, Vec3d change) {
        if (region.intersects(source.offset(change))) return true;
        // Someone already inside may move directly out. Never block a way out based on its origin.
        if (region.intersects(source)) return false;
        if (!region.intersects(source.stretch(change))) return false;
        double[] time = {0.0, 1.0};
        return overlapsDuring(source.minX, source.maxX, region.minX, region.maxX, change.x, time)
                && overlapsDuring(source.minY, source.maxY, region.minY, region.maxY, change.y, time)
                && overlapsDuring(source.minZ, source.maxZ, region.minZ, region.maxZ, change.z, time);
    }

    private static boolean overlapsDuring(double startMin, double startMax, double min, double max,
                                          double change, double[] time) {
        if (change == 0) return startMin < max && startMax > min;
        double a = (min - startMax) / change;
        double b = (max - startMin) / change;
        time[0] = Math.max(time[0], Math.min(a, b));
        time[1] = Math.min(time[1], Math.max(a, b));
        return time[0] < time[1];
    }
}
