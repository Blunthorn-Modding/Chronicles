package io.github.chronicle.core.spawn;

import io.github.chronicle.core.authoring.AuthoringSite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpawnDestinationValidatorTest {
    private static final AuthoringSite.Location LOCATION = new AuthoringSite.Location("arrival", "minecraft:overworld", 1, 1.4, 64.2, -2.6, 91, -12);
    @Test void acceptsOnlyLoadedBoundedSupportedClearDestination() { assertTrue(SpawnDestinationValidator.validate(new World(), LOCATION).safe()); }
    @Test void identifiesEachUnsafeRuntimeCondition() {
        World absent = new World(); absent.dimension = false; assertEquals("destination dimension is unavailable", SpawnDestinationValidator.validate(absent, LOCATION).reason());
        World unloaded = new World(); unloaded.loaded = false; assertEquals("destination chunk is unloaded", SpawnDestinationValidator.validate(unloaded, LOCATION).reason());
        AuthoringSite.Location corner = new AuthoringSite.Location("corner", "minecraft:overworld", 1, .1, 64, .1, 0, 0);
        World diagonal = new World(); diagonal.unloadedX = 0; diagonal.unloadedZ = 0; assertEquals("destination chunk is unloaded", SpawnDestinationValidator.validate(diagonal, corner).reason());
        World border = new World(); border.border = false; assertEquals("destination is outside the world border", SpawnDestinationValidator.validate(border, LOCATION).reason());
        World support = new World(); support.support = false; assertEquals("destination has no safe support block", SpawnDestinationValidator.validate(support, LOCATION).reason());
        World room = new World(); room.room = false; assertEquals("destination standing room is obstructed", SpawnDestinationValidator.validate(room, LOCATION).reason());
        World hazard = new World(); hazard.hazard = true; assertEquals("destination contains fire, magma, or fluid hazard", SpawnDestinationValidator.validate(hazard, LOCATION).reason());
    }
    private static final class World implements SpawnDestinationValidator.World { boolean dimension=true, loaded=true, border=true, build=true, support=true, room=true, hazard; int unloadedX=Integer.MIN_VALUE, unloadedZ=Integer.MIN_VALUE; public boolean hasDimension(String d){return dimension;} public boolean isChunkLoaded(String d,int x,int z){return loaded && (x != unloadedX || z != unloadedZ);} public boolean withinWorldBorder(String d,int x,int y,int z){return border;} public boolean withinBuildLimits(String d,int x,int y,int z){return build;} public boolean hasSolidSupport(String d,int x,int y,int z){return support;} public boolean hasClearStandingRoom(String d,double x,double y,double z){return room;} public boolean hasHazard(String d,double a,double b,double c,double e,double f,double g){return hazard;} }
}
