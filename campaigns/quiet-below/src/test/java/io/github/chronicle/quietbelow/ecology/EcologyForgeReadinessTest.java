package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologyForgeReadinessTest {
    private static final Pos HEART = new Pos(-1, 0, 0);
    private static final EcologyEngine.Setup PINS = new EcologyEngine.Setup("city", 1, "heart", 1, "minecraft:overworld");
    @TempDir Path directory;
    static final class World extends EcologyEngineTest.World {
        @Override public boolean growthExcluded(Pos at) { return true; }
    }
    private World line(int count) {
        var world = new World();
        for (int x = 0; x < count; x++) {
            world.blocks.put(new Pos(x, 0, 0), SCULK);
            world.blocks.put(new Pos(x, 1, 0), AIR);
        }
        return world;
    }
    private EcologyEngine start(EcologyJournal journal, World world, int width) throws Exception {
        var engine = new EcologyEngine(journal);
        engine.configure(PINS, new EcologyEngine.Area(HEART, new Pos(width - 1, 0, 0)), HEART);
        for (int i = 0; i < 100 && !engine.view().indexed(); i++) engine.index(world);
        assertTrue(engine.view().indexed());
        engine.tuneVitality(new EcologyVitality.Rules(10, 5, 0));
        engine.mode(true);
        return engine;
    }
    private static void tick(EcologyEngine engine, World world, int count) throws Exception {
        for (int i = 0; i < count; i++) { engine.beginTick(); engine.tick(world, true); }
    }
    @Test void halfwayThresholdAndEstablishedReadinessAreDistinct() throws Exception {
        for (int count : new int[]{31, 32, 63, 64}) {
            var world = line(count);
            try (var journal = new EcologyJournal(directory.resolve("threshold-" + count + ".jsonl"))) {
                var engine = start(journal, world, count);
                assertEquals(0, engine.forgeHeat(), "an unassessed network must not advertise readiness");
                tick(engine, world, 12);
                assertEquals(count, engine.vitalitySummary().ready());
                assertEquals(count < 32 ? 0 : count < 64 ? 1 : 2, engine.forgeHeat());
                assertEquals(0, world.writes, "appearance classification must not create growth");
            }
        }
    }
    @Test void kindlingFollowsTheConfiguredEstablishedThreshold() throws Exception {
        var world = line(64);
        try (var journal = new EcologyJournal(directory.resolve("tuning.jsonl"))) {
            var engine = start(journal, world, 64); tick(engine, world, 12); assertEquals(2, engine.forgeHeat());
            engine.tuneVitality(new EcologyVitality.Rules(10, 5, 1)); tick(engine, world, 12);
            assertEquals(1, engine.forgeHeat(), "64 living cells are halfway to the tuned128 threshold");
            engine.tuneVitality(new EcologyVitality.Rules(10, 5, 2)); tick(engine, world, 12);
            assertEquals(0, engine.forgeHeat(), "64 cells are below halfway to256");
        }
    }
    @Test void detachedCellsDoNotWarmTheConnectedNetworkAndCutsInvalidateImmediately() throws Exception {
        var world = line(32);
        for (int x = 100; x < 200; x++) world.blocks.put(new Pos(x, 0, 0), SCULK);
        try (var journal = new EcologyJournal(directory.resolve("detached.jsonl"))) {
            var engine = start(journal, world, 200); tick(engine, world, 20);
            assertEquals(32, engine.vitalitySummary().ready()); assertEquals(1, engine.forgeHeat());
            Pos cut = new Pos(15, 0, 0); world.blocks.put(cut, AIR); engine.observed(cut, AIR);
            assertEquals(0, engine.forgeHeat()); tick(engine, world, 20); assertEquals(0, engine.forgeHeat());
        }
    }
    @Test void certifiedRemovalsCannotKeepTheKindlingCountAboveItsBoundary() throws Exception {
        var world = new World();
        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) world.blocks.put(new Pos(x, 0, z), SCULK);
        try (var journal = new EcologyJournal(directory.resolve("losses.jsonl"))) {
            var engine = new EcologyEngine(journal);
            engine.configure(PINS, new EcologyEngine.Area(HEART, new Pos(5, 0, 5)), HEART);
            while (!engine.view().indexed()) engine.index(world);
            engine.tuneVitality(new EcologyVitality.Rules(10, 5, 0)); engine.mode(true); tick(engine, world, 12);
            assertEquals(1, engine.forgeHeat());
            for (int x = 5; x >= 1; x--) { Pos at = new Pos(x, 0, 5); world.blocks.put(at, AIR); engine.observed(at, AIR); }
            assertTrue(engine.rooted(new Pos(0, 0, 0)));
            assertEquals(0, engine.forgeHeat(), "use the conservative loss-adjusted count before the next assessment");
        }
    }
    @Test void restartUncertaintyAndDisabledEcologyCannotAdvertisePower() throws Exception {
        var world = line(64); var file = directory.resolve("restart.jsonl");
        try (var journal = new EcologyJournal(file)) {
            var engine = start(journal, world, 64); tick(engine, world, 12); assertEquals(2, engine.forgeHeat()); engine.flushVitality();
        }
        try (var journal = new EcologyJournal(file)) {
            var engine = new EcologyEngine(journal); engine.attach(PINS, new EcologyEngine.Area(HEART, new Pos(63, 0, 0)), HEART);
            assertEquals(0, engine.forgeHeat()); tick(engine, world, 20); assertEquals(2, engine.forgeHeat());
            engine.mode(false); assertEquals(0, engine.forgeHeat());
        }
    }
}
