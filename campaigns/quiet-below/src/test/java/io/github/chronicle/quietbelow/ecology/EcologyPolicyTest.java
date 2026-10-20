package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.AIR;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.SCULK;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.vein;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcologyPolicyTest {
    private static final EcologyEngine.Setup PINS = new EcologyEngine.Setup(
            "city", 1, "heart", 1, "minecraft:overworld");
    private static final EcologyTerrain.Pos HEART = new EcologyTerrain.Pos(0, 0, 0);
    private static final EcologyTerrain.State HEART_BLOCK = new EcologyTerrain.State(
            "minecraft:reinforced_deepslate", Map.of());
    private static final EcologyTerrain.State SENSOR = new EcologyTerrain.State(
            "minecraft:sculk_sensor",
            Map.of("sculk_sensor_phase", "inactive", "power", "0", "waterlogged", "false"));

    @TempDir Path temporaryDirectory;

    @Test
    void rootedThresholdGeneratesSensorWithoutSummoningState() throws Exception {
        assertGeneratedFeature(64, "minecraft:sculk_sensor", "Rooted", false);
    }

    @Test
    void establishedThresholdGeneratesCatalystWithoutSummoningState() throws Exception {
        assertGeneratedFeature(288, "minecraft:sculk_catalyst", "Established", false);
    }

    @Test
    void extensiveThresholdGeneratesSummonCapableShrieker() throws Exception {
        assertGeneratedFeature(2048, "minecraft:sculk_shrieker", "Extensive", true);
    }

    @Test
    void nativeChangesRespectPerTickBudgetAndRejectOutsideSculkAdoption() throws Exception {
        List<EcologyTerrain.Pos> targets = new ArrayList<>();
        for (int y = 1; y <= EcologyEngine.MAX_WRITES + 2; y++) {
            targets.add(new EcologyTerrain.Pos(1, y, 0));
        }
        try (Fixture fixture = ready("native-budget", 64, targets)) {
            fixture.engine.beginTick();
            long before = fixture.journal.revision();
            for (int index = 0; index < EcologyEngine.MAX_WRITES; index++) {
                EcologyTerrain.Pos target = targets.get(index);
                EcologyTerrain.Pos origin = source(index + 1);
                assertTrue(fixture.engine.nativeChange(fixture.terrain, origin, target, AIR, SENSOR));
                fixture.terrain.put(target, SENSOR); // The native caller performs the authorized mutation.
                settleTopologyWithoutResettingWriteBudget(fixture);
            }

            EcologyTerrain.Pos overBudget = targets.get(EcologyEngine.MAX_WRITES);
            assertFalse(fixture.engine.nativeChange(
                    fixture.terrain, source(EcologyEngine.MAX_WRITES + 1), overBudget, AIR, SENSOR));
            assertEquals(before + EcologyEngine.MAX_WRITES, fixture.journal.revision());
            assertFalse(fixture.engine.owns(overBudget));

            fixture.engine.beginTick();
            EcologyTerrain.Pos unrelated = targets.get(EcologyEngine.MAX_WRITES + 1);
            fixture.terrain.put(unrelated, SCULK);
            long beforeUnrelated = fixture.journal.revision();
            assertFalse(fixture.engine.nativeChange(
                    fixture.terrain, source(EcologyEngine.MAX_WRITES + 2), unrelated, SCULK, SENSOR));
            assertEquals(beforeUnrelated, fixture.journal.revision());
            assertFalse(fixture.engine.owns(unrelated), "nearby unrelated sculk must not be adopted");
        }
    }

    @Test
    void partialVeinObservationRetainsOriginalBeforeStateAcrossReplay() throws Exception {
        EcologyTerrain.Pos target = new EcologyTerrain.Pos(1, 1, 0);
        Path file = temporaryDirectory.resolve("partial-vein.jsonl");
        EcologyTerrain.State fullVein = vein(4).with("up", "true");
        EcologyTerrain.State partialVein = vein(4);

        try (Fixture fixture = ready(file, 64, List.of(target))) {
            fixture.engine.beginTick();
            assertTrue(fixture.engine.nativeChange(
                    fixture.terrain, source(1), target, AIR, fullVein));
            fixture.terrain.put(target, fullVein);
            fixture.engine.observed(target, partialVein);

            EcologyEngine.Cell observed = cell(fixture.engine, target);
            assertEquals(AIR, observed.before());
            assertEquals(partialVein, observed.after());
            assertEquals("OBSERVE", fixture.journal.entries().getLast().type());
        }

        try (EcologyJournal journal = new EcologyJournal(file)) {
            EcologyEngine replayed = new EcologyEngine(journal);
            replayed.attach(PINS, area(64), HEART);
            EcologyEngine.Cell observed = cell(replayed, target);
            assertEquals(AIR, observed.before(), "OBSERVE must not replace the original restoration state");
            assertEquals(partialVein, observed.after());
            assertFalse(observed.adopted());
        }
    }

    @Test
    void unloadedOwnedDescendantIsNeverReadOrDiscarded() throws Exception {
        EcologyTerrain.Pos target = new EcologyTerrain.Pos(1, 1, 0);
        try (Fixture fixture = ready("unloaded-descendant", 64, List.of(target))) {
            fixture.engine.beginTick();
            assertTrue(fixture.engine.nativeChange(
                    fixture.terrain, source(1), target, AIR, SENSOR));
            fixture.terrain.put(target, SENSOR);
            fixture.terrain.unload(target);

            for (int index = 0; index < 10; index++) tick(fixture.engine, fixture.terrain, true);

            assertEquals(0, fixture.terrain.unloadedReadAttempts);
            assertTrue(fixture.engine.owns(target));
            assertTrue(fixture.engine.view().unresolved());
        }
    }

    @Test
    void failedWorldWritePersistsModeOffAcrossReplay() throws Exception {
        Path file = temporaryDirectory.resolve("failed-write.jsonl");
        EcologyTerrain.Pos target = new EcologyTerrain.Pos(0, 65, 0);
        try (Fixture fixture = ready(file, 64, List.of(target))) {
            fixture.terrain.failWrites = true;
            IOException failure = assertThrows(IOException.class, () -> tickUntilWriteAttempt(fixture));

            assertNotNull(failure.getMessage());
            assertFalse(fixture.engine.enabled());
            assertNotNull(fixture.engine.failure());
            assertEquals(1, fixture.terrain.writeAttempts);
            EcologyJournal.Entry last = fixture.journal.entries().getLast();
            assertEquals("MODE", last.type());
            assertFalse(last.payload().get("enabled").getAsBoolean());
        }

        try (EcologyJournal journal = new EcologyJournal(file)) {
            EcologyEngine replayed = new EcologyEngine(journal);
            replayed.attach(PINS, area(64), HEART);
            assertFalse(replayed.enabled(), "failed mutation must remain durably disabled after restart");
            assertEquals("MODE", journal.entries().getLast().type());
            assertFalse(journal.entries().getLast().payload().get("enabled").getAsBoolean());
        }
    }

    private void assertGeneratedFeature(int ownedCells, String expectedId, String expectedExtent,
                                        boolean canSummon) throws Exception {
        EcologyTerrain.Pos target = new EcologyTerrain.Pos(0, ownedCells + 1, 0);
        try (Fixture fixture = ready(expectedId.substring(expectedId.lastIndexOf(':') + 1),
                ownedCells, List.of(target))) {
            assertEquals(ownedCells, fixture.engine.view().connected());
            assertEquals(expectedExtent, fixture.engine.view().extent());

            for (int index = 0; index < 25 && fixture.terrain.get(target).air(); index++) {
                tick(fixture.engine, fixture.terrain, true);
            }

            EcologyTerrain.State generated = fixture.terrain.get(target);
            assertEquals(expectedId, generated.id());
            assertTrue(fixture.engine.owns(target));
            assertEquals(canSummon, "true".equals(generated.properties().get("can_summon")));
            assertEquals(switch (expectedId) {
                case "minecraft:sculk_sensor" -> Map.of(
                        "sculk_sensor_phase", "inactive", "power", "0", "waterlogged", "false");
                case "minecraft:sculk_catalyst" -> Map.of("bloom", "false");
                case "minecraft:sculk_shrieker" -> Map.of(
                        "can_summon", "true", "shrieking", "false", "waterlogged", "false");
                default -> throw new AssertionError("unexpected generated feature " + expectedId);
            }, generated.properties());
        }
    }

    private Fixture ready(String name, int ownedCells, List<EcologyTerrain.Pos> targets) throws Exception {
        return ready(temporaryDirectory.resolve(name + ".jsonl"), ownedCells, targets);
    }

    private Fixture ready(Path file, int ownedCells, List<EcologyTerrain.Pos> targets) throws Exception {
        StrictTerrain terrain = new StrictTerrain(ownedCells, targets);
        EcologyJournal journal = new EcologyJournal(file);
        try {
            EcologyEngine engine = new EcologyEngine(journal);
            engine.configure(PINS, area(ownedCells), HEART);
            for (int index = 0; index < ownedCells + 10 && !engine.view().indexed(); index++) {
                engine.index(terrain);
            }
            assertTrue(engine.view().indexed());
            engine.mode(true);
            Fixture fixture = new Fixture(journal, engine, terrain);
            for (int index = 0; index < 100 && fixture.engine.view().unresolved(); index++) {
                tick(fixture.engine, fixture.terrain, true);
            }
            assertFalse(fixture.engine.view().unresolved());
            return fixture;
        } catch (Exception | Error failure) {
            journal.close();
            throw failure;
        }
    }

    private static void settleTopologyWithoutResettingWriteBudget(Fixture fixture) throws IOException {
        for (int index = 0; index < 10 && fixture.engine.view().unresolved(); index++) {
            fixture.engine.tick(fixture.terrain, true);
        }
        assertFalse(fixture.engine.view().unresolved());
    }

    private static void tickUntilWriteAttempt(Fixture fixture) throws IOException {
        for (int index = 0; index < 25; index++) tick(fixture.engine, fixture.terrain, true);
    }

    private static void tick(EcologyEngine engine, StrictTerrain terrain, boolean active) throws IOException {
        engine.beginTick();
        engine.tick(terrain, active);
    }

    private static EcologyEngine.Cell cell(EcologyEngine engine, EcologyTerrain.Pos position) {
        return engine.cells().stream()
                .filter(candidate -> candidate.position().equals(position))
                .findFirst().orElseThrow();
    }

    private static EcologyTerrain.Pos source(int y) {
        return new EcologyTerrain.Pos(0, y, 0);
    }

    private static EcologyEngine.Area area(int ownedCells) {
        return new EcologyEngine.Area(HEART, source(ownedCells));
    }

    private static final class Fixture implements AutoCloseable {
        private final EcologyJournal journal;
        private final EcologyEngine engine;
        private final StrictTerrain terrain;

        private Fixture(EcologyJournal journal, EcologyEngine engine, StrictTerrain terrain) {
            this.journal = journal;
            this.engine = engine;
            this.terrain = terrain;
        }

        @Override
        public void close() throws IOException {
            journal.close();
        }
    }

    private static final class StrictTerrain implements EcologyTerrain {
        private final Map<Pos, State> blocks = new HashMap<>();
        private final Set<Pos> loaded = new HashSet<>();
        private int unloadedReadAttempts;
        private int writeAttempts;
        private boolean failWrites;

        private StrictTerrain(int ownedCells, List<Pos> targets) {
            loaded.add(HEART);
            blocks.put(HEART, HEART_BLOCK);
            for (int y = 1; y <= ownedCells; y++) {
                Pos position = source(y);
                loaded.add(position);
                blocks.put(position, SCULK);
            }
            for (Pos target : targets) {
                loaded.add(target);
                blocks.put(target, AIR);
            }
        }

        private void put(Pos position, State state) {
            blocks.put(position, state);
        }

        private void unload(Pos position) {
            loaded.remove(position);
        }

        @Override
        public boolean loaded(Pos position) {
            return loaded.contains(position);
        }

        @Override
        public State get(Pos position) {
            if (!loaded(position)) {
                unloadedReadAttempts++;
                throw new AssertionError("unloaded terrain read at " + position);
            }
            return blocks.getOrDefault(position, AIR);
        }

        @Override
        public boolean solidFace(Pos position, int face) {
            State state = get(position);
            return !state.air() && !state.vein();
        }

        @Override
        public boolean substrate(Pos position) {
            return get(position).id().equals("minecraft:stone");
        }

        @Override
        public int light(Pos position) {
            get(position);
            return 0;
        }

        @Override
        public boolean set(Pos position, State state) {
            if (!loaded(position)) {
                unloadedReadAttempts++;
                throw new AssertionError("unloaded terrain write at " + position);
            }
            writeAttempts++;
            if (failWrites) return false;
            blocks.put(position, state);
            return true;
        }
    }
}
