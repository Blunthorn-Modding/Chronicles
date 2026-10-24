package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.AIR;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.SCULK;
import static org.junit.jupiter.api.Assertions.*;

class EcologyHeartDefianceTest {
    private static final EcologyTerrain.Pos HEART = new EcologyTerrain.Pos(0, 0, 0);
    private static final EcologyTerrain.Pos SOURCE = new EcologyTerrain.Pos(1, 0, 0);
    private static final EcologyTerrain.Pos SEED = new EcologyTerrain.Pos(0, -1, 0);
    private static final EcologyTerrain.Pos SEED_AIR = new EcologyTerrain.Pos(0, -1, -1);
    private static final EcologyTerrain.State HEART_STATE = new EcologyTerrain.State("quiet_below:relic_heart", Map.of());
    private static final EcologyTerrain.State STONE = new EcologyTerrain.State("minecraft:stone", Map.of());
    private static final EcologyEngine.Setup PINS = new EcologyEngine.Setup("city", 1, "heart", 1, "minecraft:overworld");
    private static final EcologyEngine.Area AREA = new EcologyEngine.Area(HEART, SOURCE);

    @TempDir Path dir;

    @Test void pureMilestonesPulseSixTimesAndMakeOneEffortAtFiftySeconds() {
        EcologyHeart.Rules rules = EcologyHeart.DEFAULT;
        assertEquals(1000, EcologyHeart.lastEffortTick(rules));
        int pulses = 0, efforts = 0;
        for (int tick = 1; tick <= rules.requiredTicks(); tick++) {
            EcologyHeart.Transition transition = EcologyHeart.transition(rules, tick - 1, tick);
            if (transition.pulse()) pulses++;
            if (transition.lastEffort()) efforts++;
        }
        assertEquals(6, pulses);
        assertEquals(1, efforts);
        assertTrue(EcologyHeart.transition(rules, 999, 1000).lastEffort());
        assertFalse(EcologyHeart.transition(rules, 1000, 1001).lastEffort());

        EcologyHeart.Rules oneSecond = new EcologyHeart.Rules(1, 1);
        assertEquals(16, EcologyHeart.lastEffortTick(oneSecond));
        assertTrue(EcologyHeart.transition(oneSecond, 15, 16).lastEffort());
        assertTrue(EcologyHeart.transition(oneSecond, 19, 20).pulse());
    }

    @Test void emptyNetworkMakesOneJournaledLocalEffortAndStillMaturesAtSixtySeconds() throws Exception {
        World world = exposedWorld();
        try (EcologyJournal journal = new EcologyJournal(dir.resolve("empty-effort.jsonl"))) {
            EcologyEngine engine = emptyEngine(journal, world);
            EcologyHeart.Transition transition = advance(engine, world, 1000, true);
            assertTrue(transition.lastEffort());
            int writes = world.writes;
            long grows = journal.entries().stream().filter(entry -> entry.type().equals("GROW")).count();

            assertTrue(engine.heartLastEffort(world, true, transition));
            assertTrue(world.writes > writes);
            assertEquals(world.writes - writes, journal.entries().stream().filter(entry -> entry.type().equals("GROW")).count() - grows);
            assertTrue(world.writes - writes <= EcologyEngine.MAX_WRITES);
            EcologyEngine.Cell seeded = engine.cells().stream().filter(cell -> cell.position().equals(SEED)).findFirst().orElseThrow();
            assertEquals(STONE, seeded.before(), "cleanup must retain the exact terrain replaced by the final effort");
            assertEquals(SCULK, seeded.after());
            assertFalse(engine.heartLastEffort(world, true, transition), "one weakness cycle gets only one final effort");
            assertTrue(world.writes > writes);

            EcologyHeart.Transition completed = advance(engine, world, 200, true);
            assertEquals(1200, completed.current());
            assertEquals(1200, engine.heartElapsed(), "sub-threshold final growth must not restart the weakness clock");
            assertTrue(engine.heartVulnerable(true));
        }
    }

    @Test void emptyNetworkMayAttachOneVeinWithoutReplacingItsBacking() throws Exception {
        World world = sealedWorld();
        EcologyTerrain.Pos backing = SEED.offset(2);
        world.blocks.put(SEED, AIR);
        world.blocks.put(backing, STONE);
        try (EcologyJournal journal = new EcologyJournal(dir.resolve("empty-vein-effort.jsonl"))) {
            EcologyEngine engine = emptyEngine(journal, world);
            EcologyHeart.Transition transition = advance(engine, world, 1000, true);

            assertTrue(engine.heartLastEffort(world, true, transition));
            assertTrue(world.blocks.get(SEED).vein());
            assertEquals(STONE, world.blocks.get(backing));
            assertTrue(world.writes >= 1 && world.writes <= EcologyEngine.MAX_WRITES);
            assertEquals(world.writes, journal.entries().stream().filter(entry -> entry.type().equals("GROW")).count());
        }
    }

    @Test void pausedUnknownAndProtectedStatesCannotSignalOrGrow() throws Exception {
        World world = exposedWorld();
        try (EcologyJournal journal = new EcologyJournal(dir.resolve("gates.jsonl"))) {
            EcologyEngine engine = emptyEngine(journal, world);
            EcologyHeart.Transition paused = advance(engine, world, 1100, false);
            assertEquals(0, engine.heartElapsed());
            assertFalse(paused.pulse());assertFalse(paused.lastEffort());

            engine.invalidateConnections();
            EcologyHeart.Transition unknown = engine.heartTick(true, true);
            assertFalse(unknown.pulse());assertFalse(unknown.lastEffort());
        }

        World protectedWorld = exposedWorld();
        try (EcologyJournal journal = new EcologyJournal(dir.resolve("protected.jsonl"))) {
            EcologyEngine engine = ready(journal, protectedWorld);
            engine.tuneHeart(new EcologyHeart.Rules(1, 60));
            EcologyHeart.Transition protectedTransition = advance(engine, protectedWorld, 1200, true);
            assertEquals(0, engine.heartElapsed());
            assertFalse(protectedTransition.pulse());assertFalse(protectedTransition.lastEffort());
        }
    }

    @Test void sealedExcludedUnloadedAndUnownedShellsFailWithoutAWorldWrite() throws Exception {
        assertRejected("sealed", world -> { }, Set.of());
        assertRejected("one-block-shell", world -> {
            for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)for(int z=-2;z<=2;z++)
                if(Math.abs(x)+Math.abs(y)+Math.abs(z)>=2)world.blocks.put(HEART.add(x,y,z),AIR);
        }, Set.of());
        assertRejected("excluded", world -> {
            world.blocks.put(SEED_AIR, AIR);
            for (int face = 0; face < 6; face++) world.excluded.add(HEART.offset(face));
        }, Set.of());
        assertRejected("unloaded", world -> {
            world.blocks.put(SEED_AIR, AIR);
            for (int face = 0; face < 6; face++) world.unloaded.add(HEART.offset(face));
        }, Set.of());
        assertRejected("unowned", world -> {
            for (int face = 0; face < 6; face++) world.blocks.put(HEART.offset(face), SCULK);
        }, Set.of(SCULK));
    }

    @Test void finalEffortSharesTheTickWriteBudget() throws Exception {
        World world = exposedWorld();
        try (EcologyJournal journal = new EcologyJournal(dir.resolve("budget.jsonl"))) {
            EcologyEngine engine = emptyEngine(journal, world);
            EcologyHeart.Transition transition = advance(engine, world, 1000, true);
            for (int write = 0; write < EcologyEngine.MAX_WRITES; write++) assertTrue(engine.reserveNativeLoss());
            int before = world.writes;
            assertTrue(engine.heartLastEffort(world, true, transition), "a busy trigger tick must queue rather than lose the burst");
            assertEquals(before, world.writes);
            assertEquals(0, journal.entries().stream().filter(entry -> entry.type().equals("GROW")).count());
            engine.beginTick();engine.tick(world, true);
            assertTrue(world.writes > before);
            assertTrue(world.writes - before <= EcologyEngine.MAX_WRITES);
        }
    }

    @Test void callerCannotForgeAnEarlyLastEffortCrossing() throws Exception {
        World world = exposedWorld();
        try (EcologyJournal journal = new EcologyJournal(dir.resolve("forged-effort.jsonl"))) {
            EcologyEngine engine = emptyEngine(journal, world);
            advance(engine, world, 20, true);
            EcologyHeart.Transition forged = new EcologyHeart.Transition(19, 20, true, true);

            assertFalse(engine.heartLastEffort(world, true, forged));
            assertEquals(0, world.writes);
            assertTrue(engine.cells().isEmpty());
        }
    }

    @Test void corruptOrUnreadableDefianceReceiptDoesNotBlockIndependentWeaknessOrVulnerability() throws Exception {
        Path receipt = dir.resolve("corrupt-defiance.json");
        Files.writeString(receipt, "{broken");
        assertThrows(java.io.IOException.class, () -> new HeartDefianceStore(receipt).load());
        Path unreadable = Files.createDirectory(dir.resolve("defiance-is-not-a-file.json"));
        assertThrows(java.io.IOException.class, () -> new HeartDefianceStore(unreadable).load());

        World world = exposedWorld();
        try (EcologyJournal journal = new EcologyJournal(dir.resolve("corrupt-store-heart.jsonl"))) {
            EcologyEngine engine = emptyEngine(journal, world);
            advance(engine, world, 1200, true);
            assertEquals(1200, engine.heartElapsed());
            assertTrue(engine.heartVulnerable(true), "optional last-stand persistence cannot hold the mining gate");
        }
    }

    @Test void optionalEffectRuntimeFailureDeniesItsPhaseButNotHeartWeakness() throws Exception {
        RuntimeException fault = new IllegalStateException("simulated optional effect fault");
        QuietBelowEcology.DefianceEffectResult result = QuietBelowEcology.optionalDefianceEffect(
                QuietBelowEcology.DefiancePhase.AUTHORIZED, () -> { throw fault; });
        assertEquals(QuietBelowEcology.DefiancePhase.DENIED, result.phase());
        assertSame(fault, result.failure());
        assertFalse(result.value());
        assertFalse(QuietBelowEcology.optionalDefianceEffect(result.phase(), () -> true).value(),
                "the rest of the failed phase must stay suppressed");

        World world = exposedWorld();
        try (EcologyJournal journal = new EcologyJournal(dir.resolve("optional-fault-heart.jsonl"))) {
            EcologyEngine engine = emptyEngine(journal, world);
            advance(engine, world, 1200, true);
            assertTrue(engine.heartVulnerable(true), "optional sound or spawn failure cannot hold the mining gate");
        }
    }

    private void assertRejected(String name, java.util.function.Consumer<World> shell, Set<EcologyTerrain.State> preserved) throws Exception {
        World world = sealedWorld();
        try (EcologyJournal journal = new EcologyJournal(dir.resolve(name + ".jsonl"))) {
            EcologyEngine engine = emptyEngine(journal, world);
            EcologyHeart.Transition transition = advance(engine, world, 1000, true);
            // Apply the hostile shell after the normal tick, so an unowned sculk candidate cannot
            // be adopted by the ordinary placed-sculk contact scan before the final effort.
            shell.accept(world);
            int before = world.writes;
            assertFalse(engine.heartLastEffort(world, true, transition), name);
            assertEquals(before, world.writes, name);
            assertEquals(0, journal.entries().stream().filter(entry -> entry.type().equals("GROW")).count(), name);
            for (EcologyTerrain.State state : preserved)
                assertTrue(world.blocks.values().stream().anyMatch(state::equals), name + " shell was replaced");
        }
    }

    private static EcologyHeart.Transition advance(EcologyEngine engine, World world, int ticks, boolean active) throws Exception {
        EcologyHeart.Transition transition = EcologyHeart.transition(engine.heartRules(), engine.heartElapsed(), engine.heartElapsed());
        for (int tick = 0; tick < ticks; tick++) {
            engine.beginTick();
            engine.tick(world, active);
            transition = engine.heartTick(true, active);
        }
        return transition;
    }

    private static EcologyEngine emptyEngine(EcologyJournal journal, World world) throws Exception {
        EcologyEngine engine = ready(journal, world);
        world.blocks.put(SOURCE, STONE);
        engine.observed(SOURCE, STONE);
        for (int tick = 0; tick < 10 && !engine.heartProof(true).complete(); tick++) {
            engine.beginTick();engine.tick(world, true);
        }
        assertTrue(engine.cells().isEmpty());
        assertTrue(engine.heartProof(true).complete());
        assertEquals(0, engine.heartProof(true).living());
        return engine;
    }

    private static EcologyEngine ready(EcologyJournal journal, World world) throws Exception {
        EcologyEngine engine = new EcologyEngine(journal);
        engine.configure(PINS, AREA, HEART);
        for (int tick = 0; tick < 20 && !engine.view().indexed(); tick++) engine.index(world);
        assertTrue(engine.view().indexed());
        engine.mode(true);
        for (int tick = 0; tick < 10 && !engine.heartProof(true).complete(); tick++) {
            engine.beginTick();engine.tick(world, true);
        }
        assertTrue(engine.heartProof(true).complete());
        return engine;
    }

    private static World exposedWorld() {
        World world = sealedWorld();
        world.blocks.put(HEART.offset(2), AIR);
        world.blocks.put(SEED_AIR, AIR);
        return world;
    }

    private static World sealedWorld() {
        World world = new World();
        world.blocks.put(HEART, HEART_STATE);
        world.blocks.put(SOURCE, SCULK);
        return world;
    }

    private static final class World implements EcologyTerrain {
        private final Map<Pos, State> blocks = new HashMap<>();
        private final Set<Pos> unloaded = new HashSet<>();
        private final Set<Pos> excluded = new HashSet<>();
        private int writes;

        @Override public boolean loaded(Pos pos) { return !unloaded.contains(pos); }
        @Override public State get(Pos pos) {
            assertTrue(loaded(pos), "unloaded terrain read at " + pos);
            return blocks.getOrDefault(pos, STONE);
        }
        @Override public boolean solidFace(Pos pos, int face) { State state = get(pos);return !state.air() && !state.vein(); }
        @Override public boolean substrate(Pos pos) { return get(pos).equals(STONE); }
        @Override public boolean growthExcluded(Pos pos) { return excluded.contains(pos); }
        @Override public int light(Pos pos) { get(pos);return 0; }
        @Override public boolean set(Pos pos, State state) { get(pos);writes++;blocks.put(pos, state);return true; }
    }
}
