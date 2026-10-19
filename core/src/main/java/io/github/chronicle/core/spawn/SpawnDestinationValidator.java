package io.github.chronicle.core.spawn;

import io.github.chronicle.core.authoring.AuthoringSite;

/** Pure validation boundary: callers must adapt only already-loaded Minecraft worlds. */
public final class SpawnDestinationValidator {
    private SpawnDestinationValidator() { }
    public interface World {
        boolean hasDimension(String dimension);
        boolean isChunkLoaded(String dimension, int blockX, int blockZ);
        boolean withinWorldBorder(String dimension, int blockX, int blockY, int blockZ);
        boolean withinBuildLimits(String dimension, int blockX, int blockY, int blockZ);
        boolean hasSolidSupport(String dimension, int blockX, int blockY, int blockZ);
        boolean hasClearStandingRoom(String dimension, double x, double y, double z);
        boolean hasHazard(String dimension, double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
    }
    public record Verdict(boolean safe, String reason) { }
    public static Verdict validate(World world, AuthoringSite.Location destination) {
        if (world == null || destination == null) throw new IllegalArgumentException("world and destination are required");
        int x = (int) Math.floor(destination.x()), y = (int) Math.floor(destination.y()), z = (int) Math.floor(destination.z());
        String dimension = destination.dimension();
        if (!world.hasDimension(dimension)) return new Verdict(false, "destination dimension is unavailable");
        double minX = destination.x() - .3, maxX = destination.x() + .3, minZ = destination.z() - .3, maxZ = destination.z() + .3;
        int lowX = (int) Math.floor(minX), highX = (int) Math.floor(maxX), lowZ = (int) Math.floor(minZ), highZ = (int) Math.floor(maxZ), headY = (int) Math.floor(destination.y() + 1.8);
        for (int checkX : new int[]{lowX, highX}) for (int checkZ : new int[]{lowZ, highZ}) {
            if (!world.isChunkLoaded(dimension, checkX, checkZ)) return new Verdict(false, "destination chunk is unloaded");
            if (!world.withinBuildLimits(dimension, checkX, y, checkZ) || !world.withinBuildLimits(dimension, checkX, headY, checkZ)) return new Verdict(false, "destination is outside build limits");
            if (!world.withinWorldBorder(dimension, checkX, y, checkZ) || !world.withinWorldBorder(dimension, checkX, headY, checkZ)) return new Verdict(false, "destination is outside the world border");
            if (!world.hasSolidSupport(dimension, checkX, y - 1, checkZ)) return new Verdict(false, "destination has no safe support block");
        }
        if (!world.hasClearStandingRoom(dimension, destination.x(), destination.y(), destination.z())) return new Verdict(false, "destination standing room is obstructed");
        if (world.hasHazard(dimension, minX, destination.y() - 1, minZ, maxX, destination.y() + 1.8, maxZ)) return new Verdict(false, "destination contains fire, magma, or fluid hazard");
        return new Verdict(true, "safe");
    }
}
