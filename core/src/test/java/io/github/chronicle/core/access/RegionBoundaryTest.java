package io.github.chronicle.core.access;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionBoundaryTest {
    private static final Box GUARDED = RegionBoundary.volume(new AuthoringSite.Region("gate", "minecraft:overworld", 1,
            new BlockPosition(0, 64, 0), new BlockPosition(0, 65, 0)));

    @Test void bodyAndSweptMovementCannotCrossTheIncludedEdgeBlocks() {
        var body = new Box(-1.6, 64, .2, -1.0, 65.8, .8);
        assertTrue(RegionBoundary.crosses(GUARDED, body, new Vec3d(1.1, 0, 0)));
        assertTrue(RegionBoundary.crosses(GUARDED, body, new Vec3d(3, 0, 0)));
        assertFalse(RegionBoundary.crosses(GUARDED, body, new Vec3d(1, 0, 0)));
        assertFalse(RegionBoundary.crosses(GUARDED, body.offset(0, 2, 0), new Vec3d(3, 0, 0)));
    }

    @Test void occupantsCanLeaveWithoutTheSweptCheckHoldingThemInPlace() {
        var body = new Box(.1, 64, .1, .7, 65.8, .7);
        assertTrue(RegionBoundary.crosses(GUARDED, body, new Vec3d(.1, 0, 0)));
        assertFalse(RegionBoundary.crosses(GUARDED, body, new Vec3d(1, 0, 0)));
    }

    @Test void diagonalPathMustOverlapAllAxesAtTheSameTime() {
        var body = new Box(-2, 64, -.15, -1.4, 65.8, .45);
        assertFalse(RegionBoundary.crosses(GUARDED, body, new Vec3d(3.5, 0, 3.5)));
        assertTrue(RegionBoundary.crosses(GUARDED, body, new Vec3d(3.5, 0, .1)));
    }

    @Test void pointFeetOutsideCanStillPutStandingBodyInside() {
        var tooClose = new AuthoringSite.Location("exit", "minecraft:overworld", 2, 1.1, 64, .5, 0, 0);
        var clear = new AuthoringSite.Location("exit", "minecraft:overworld", 2, 1.5, 64, .5, 0, 0);
        assertTrue(GUARDED.intersects(RegionBoundary.standingAt(tooClose)));
        assertFalse(GUARDED.intersects(RegionBoundary.standingAt(clear)));
    }
}
