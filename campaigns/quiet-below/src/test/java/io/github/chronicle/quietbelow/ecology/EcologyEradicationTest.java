package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcologyEradicationTest {
    private static final UUID JOB = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final EcologyTerrain.Pos ROOT = new EcologyTerrain.Pos(0, 64, 0);
    private static final EcologyTerrain.State HEART = new EcologyTerrain.State("quiet_below:relic_heart", Map.of("charged", "true"));

    @Test void removingWaterloggedSensorsAndShriekersPreservesWater(){
        for(String id:List.of("minecraft:sculk_sensor","minecraft:sculk_shrieker")){
            var expected=new EcologyTerrain.State(id,Map.of("waterlogged","true"));
            var target=new EcologyEradication.Target(new EcologyTerrain.Pos(1,1,1),expected,EcologyTerrain.AIR,false);
            var decision=target.decide(expected);assertEquals(EcologyEradication.Status.PENDING,decision.status());
            assertEquals("minecraft:water",decision.replacement().id());assertEquals(EcologyEradication.Status.DONE,target.decide(decision.replacement()).status());
        }
    }
    @Test void planIsDeterministicPerChunkAndAlwaysStartsWithRoot() {
        EcologyEngine.Cell east = cell(new EcologyTerrain.Pos(18, 65, 1), EcologyTerrain.SCULK, EcologyTerrain.AIR, false);
        EcologyEngine.Cell local = cell(new EcologyTerrain.Pos(2, 64, 2), EcologyTerrain.SCULK, EcologyTerrain.AIR, true);
        EcologyEngine.Cell west = cell(new EcologyTerrain.Pos(-18, 60, 1), EcologyTerrain.SCULK, EcologyTerrain.AIR, false);
        EcologyEradication first = new EcologyEradication(JOB, ROOT, HEART, List.of(east, local, west));
        EcologyEradication second = new EcologyEradication(JOB, ROOT, HEART, List.of(west, east, local));

        assertEquals(4, first.total());
        assertTrue(first.plansForTest().getFirst().targets().getFirst().root());
        assertEquals(ROOT, first.plansForTest().getFirst().targets().getFirst().position());
        assertEquals(first.plansForTest().stream().map(EcologyEradication.ChunkPlan::identity).toList(),
                second.plansForTest().stream().map(EcologyEradication.ChunkPlan::identity).toList());
        assertEquals(first.plansForTest().stream().map(EcologyEradication.ChunkPlan::targets).toList(),
                second.plansForTest().stream().map(EcologyEradication.ChunkPlan::targets).toList());
        assertThrows(IllegalArgumentException.class,
                () -> new EcologyEradication(JOB, ROOT, HEART, List.of(cell(ROOT, EcologyTerrain.SCULK, EcologyTerrain.AIR, false))));
    }

    @Test void indexedCellsUseAirWhileGrownCellsRestoreTheirBeforeState() {
        EcologyTerrain.State stone = new EcologyTerrain.State("minecraft:stone", Map.of());
        EcologyEngine.Cell indexed = cell(new EcologyTerrain.Pos(1, 64, 0), EcologyTerrain.SCULK, stone, true);
        EcologyEngine.Cell grown = cell(new EcologyTerrain.Pos(2, 64, 0), EcologyTerrain.SCULK, stone, false);
        EcologyEradication eradication = new EcologyEradication(JOB, ROOT, HEART, List.of(grown, indexed));
        Map<EcologyTerrain.Pos, EcologyEradication.Target> targets = new java.util.HashMap<>();
        eradication.plansForTest().forEach(plan -> plan.targets().forEach(target -> targets.put(target.position(), target)));
        assertEquals(EcologyTerrain.AIR, targets.get(indexed.position()).restore());
        assertEquals(stone, targets.get(grown.position()).restore());
    }

    @Test void markerRoundTripsImmutableProgressAndChecksum() {
        String plan = "1".repeat(64);
        BitSet done = new BitSet(); done.set(0); done.set(3);
        BitSet conflicts = new BitSet(); conflicts.set(3);
        EcologyEradication.Marker marker = EcologyEradication.Marker.valid(JOB, plan, 5, done, conflicts);
        done.set(4); conflicts.clear();

        JsonElement encoded = EcologyEradication.MARKER_CODEC.encodeStart(JsonOps.INSTANCE, marker).getOrThrow();
        EcologyEradication.Marker decoded = EcologyEradication.MARKER_CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertFalse(decoded.corrupt());
        assertEquals(JOB, decoded.job());
        assertEquals(plan, decoded.plan());
        assertTrue(decoded.done(0));
        assertTrue(decoded.done(3));
        assertFalse(decoded.done(4));
        assertTrue(decoded.conflict(3));
        assertFalse(decoded.complete());
        assertEquals(marker.checksum(), decoded.checksum());

        BitSet leaked = decoded.doneBits(); leaked.set(4);
        assertFalse(decoded.done(4));
        EcologyEradication.Marker complete = decoded.complete(1, false).complete(2, false).complete(4, false);
        assertTrue(complete.complete());
        assertNotEquals(decoded.checksum(), complete.checksum());
    }

    @Test void malformedOrChecksumChangedRawMarkerBecomesPreservedSentinel() {
        EcologyEradication.Marker valid = EcologyEradication.Marker.empty(JOB, "2".repeat(64), 2);
        JsonObject tampered = EcologyEradication.MARKER_CODEC.encodeStart(JsonOps.INSTANCE, valid).getOrThrow().getAsJsonObject();
        tampered.addProperty("checksum", "0".repeat(64));
        EcologyEradication.Marker corrupt = EcologyEradication.MARKER_CODEC.parse(JsonOps.INSTANCE, tampered).getOrThrow();
        assertTrue(corrupt.corrupt());
        assertTrue(corrupt.problem().contains("checksum"));
        assertEquals(tampered, EcologyEradication.MARKER_CODEC.encodeStart(JsonOps.INSTANCE, corrupt).getOrThrow());

        JsonObject unknown = tampered.deepCopy(); unknown.addProperty("future", true);
        EcologyEradication.Marker future = EcologyEradication.MARKER_CODEC.parse(JsonOps.INSTANCE, unknown).getOrThrow();
        assertTrue(future.corrupt());
        assertEquals(unknown, EcologyEradication.MARKER_CODEC.encodeStart(JsonOps.INSTANCE, future).getOrThrow());

        JsonObject paddedBits = EcologyEradication.MARKER_CODEC.encodeStart(JsonOps.INSTANCE, valid).getOrThrow().getAsJsonObject();
        paddedBits.addProperty("done", "AA");
        EcologyEradication.Marker nonCanonical = EcologyEradication.MARKER_CODEC.parse(JsonOps.INSTANCE, paddedBits).getOrThrow();
        assertTrue(nonCanonical.corrupt());
        assertTrue(nonCanonical.problem().contains("canonical"));
        assertEquals(paddedBits, EcologyEradication.MARKER_CODEC.encodeStart(JsonOps.INSTANCE, nonCanonical).getOrThrow());
    }

    @Test void markerRejectsOutOfPlanAndConflictWithoutCompletion() {
        BitSet outside = new BitSet(); outside.set(4);
        assertThrows(IllegalArgumentException.class,
                () -> EcologyEradication.Marker.valid(JOB, "3".repeat(64), 4, outside, new BitSet()));
        BitSet conflict = new BitSet(); conflict.set(1);
        assertThrows(IllegalArgumentException.class,
                () -> EcologyEradication.Marker.valid(JOB, "3".repeat(64), 4, new BitSet(), conflict));
        assertThrows(IllegalArgumentException.class,
                () -> EcologyEradication.Marker.empty(JOB, "not-a-plan", 1));

        EcologyEradication.Marker done = EcologyEradication.Marker.empty(JOB, "4".repeat(64), 1).complete(0, false);
        EcologyEradication.Marker laterPlayerEdit = done.complete(0, true);
        assertTrue(laterPlayerEdit.done(0));
        assertTrue(laterPlayerEdit.conflict(0), "a later different-type edit upgrades completed progress to a conflict");
    }

    @Test void restartUsesSamePlanIdentityButChangedPlanOrJobCannotMatch() {
        EcologyEngine.Cell cell = cell(new EcologyTerrain.Pos(2, 64, 0), EcologyTerrain.SCULK, EcologyTerrain.AIR, false);
        EcologyEradication original = new EcologyEradication(JOB, ROOT, HEART, List.of(cell));
        EcologyEradication restarted = new EcologyEradication(JOB, ROOT, HEART, List.of(cell));
        EcologyEradication.ChunkPlan plan = original.plansForTest().getFirst();
        EcologyEradication.ChunkPlan same = restarted.plansForTest().getFirst();
        EcologyEradication.Marker marker = EcologyEradication.Marker.empty(JOB, plan.identity(), plan.targets().size());
        assertEquals(plan.identity(), same.identity());
        assertEquals(JOB, marker.job());
        assertEquals(same.identity(), marker.plan());

        EcologyTerrain.State changedHeart = new EcologyTerrain.State("quiet_below:relic_heart", Map.of("charged", "false"));
        EcologyEradication changed = new EcologyEradication(JOB, ROOT, changedHeart, List.of(cell));
        assertNotEquals(marker.plan(), changed.plansForTest().getFirst().identity());
        EcologyEradication.Marker otherJob = EcologyEradication.Marker.empty(new UUID(1, 2), plan.identity(), plan.targets().size());
        assertNotEquals(marker.job(), otherJob.job());
    }

    @Test void ordinaryTargetsIgnoreTransientPropertiesButPreserveUnrelatedBlocks() {
        EcologyTerrain.State expected = new EcologyTerrain.State("minecraft:sculk_sensor", Map.of("phase", "inactive", "waterlogged", "false"));
        EcologyTerrain.State restore = new EcologyTerrain.State("minecraft:stone", Map.of());
        EcologyEradication.Target target = new EcologyEradication.Target(new EcologyTerrain.Pos(0, 64, 0), expected, restore, false);
        EcologyTerrain.State transientChange = new EcologyTerrain.State("minecraft:sculk_sensor", Map.of("phase", "active", "waterlogged", "false"));
        assertEquals(EcologyEradication.Status.PENDING, target.decide(transientChange).status());
        assertEquals(restore, target.decide(transientChange).replacement());
        assertEquals(EcologyEradication.Status.DONE, target.decide(restore).status());
        assertEquals(EcologyEradication.Status.CONFLICT,
                target.decide(new EcologyTerrain.State("minecraft:diamond_block", Map.of())).status());
    }

    @Test void postWriteStateMustBeEvaluatedBeforeCompletion() {
        EcologyTerrain.State expected = new EcologyTerrain.State("minecraft:sculk", Map.of());
        EcologyTerrain.State restore = new EcologyTerrain.State("minecraft:stone", Map.of());
        EcologyEradication.Target target = new EcologyEradication.Target(ROOT, expected, restore, false);

        EcologyEradication.Decision attempted = target.decide(expected);
        assertEquals(EcologyEradication.Status.PENDING, attempted.status());
        assertEquals(EcologyEradication.Status.DONE,
                EcologyEradication.postWriteDecision(target, attempted, restore).status());
        assertEquals(EcologyEradication.Status.PENDING,
                EcologyEradication.postWriteDecision(target, attempted, expected).status(),
                "a neighbor cascade restoring sculk must leave the marker pending");
        assertEquals(EcologyEradication.Status.CONFLICT,
                EcologyEradication.postWriteDecision(target, attempted,
                        new EcologyTerrain.State("minecraft:water", Map.of("level", "0"))).status());
        assertThrows(IllegalArgumentException.class,
                () -> EcologyEradication.postWriteDecision(target, target.decide(restore), restore));
    }

    @Test void roundRobinDoesNotLetTheRootChunkStarveOtherChunks() {
        EcologyEradication.PlanSchedule schedule = new EcologyEradication.PlanSchedule(4);
        BitSet completed = new BitSet();
        BitSet visited = new BitSet();

        assertEquals(1, schedule.next(completed, visited)); visited.set(1);
        assertEquals(2, schedule.next(completed, visited)); visited.set(2);
        assertEquals(3, schedule.next(completed, visited)); visited.set(3);
        assertEquals(0, schedule.next(completed, visited)); visited.set(0);
        assertEquals(-1, schedule.next(completed, visited));

        completed.set(1);
        visited.clear();
        assertEquals(2, schedule.next(completed, visited), "a completed non-root plan is skipped in constant time");
    }

    @Test void largeMarkerIsIngestedOnceAndCursorJumpsToFirstPendingTarget() {
        List<EcologyEngine.Cell> cells = new ArrayList<>();
        for (int y = -4096; y < 4096; y++) {
            cells.add(cell(new EcologyTerrain.Pos(1, y, 1), EcologyTerrain.SCULK, EcologyTerrain.AIR, false));
        }
        EcologyEradication eradication = new EcologyEradication(JOB, ROOT, HEART, cells);
        EcologyEradication.ChunkPlan plan = eradication.plansForTest().getFirst();
        assertEquals(8193, plan.targets().size());
        BitSet done = new BitSet(); done.set(0, 8192);
        EcologyEradication.Marker almostComplete = EcologyEradication.Marker.valid(
                JOB, plan.identity(), plan.targets().size(), done, new BitSet());

        eradication.observeForTest(plan, almostComplete);
        assertEquals(8192, eradication.completed());
        assertEquals(8192, eradication.cursorForTest(plan));
        eradication.observeForTest(plan, almostComplete);
        assertEquals(8192, eradication.completed(), "same immutable marker must not be ingested twice");

        EcologyEradication.Marker complete = almostComplete.complete(8192, false);
        eradication.observeForTest(plan, complete);
        assertEquals(8193, eradication.completed());
        assertEquals(8193, eradication.cursorForTest(plan));
        assertEquals(0, eradication.pendingChunks());
        assertThrows(IllegalStateException.class, () -> eradication.observeForTest(plan, almostComplete),
                "a stale marker must not roll durable progress backward");
    }

    @Test void veinCleanupRemovesOnlyRecordedFacesAndPreservesNewFacesAndWater() {
        EcologyTerrain.State expected = vein(true, false, false);
        EcologyEradication.Target target = new EcologyEradication.Target(new EcologyTerrain.Pos(0, 64, 0), expected, EcologyTerrain.AIR, false);
        EcologyTerrain.State extended = vein(true, true, true);
        EcologyEradication.Decision decision = target.decide(extended);
        assertEquals(EcologyEradication.Status.PENDING, decision.status());
        assertEquals("false", decision.replacement().properties().get("north"));
        assertEquals("true", decision.replacement().properties().get("east"));
        assertEquals("true", decision.replacement().properties().get("waterlogged"));

        assertEquals(EcologyEradication.Status.DONE, target.decide(vein(false, true, true)).status());
        EcologyEradication.Decision water = target.decide(vein(true, false, true));
        assertEquals(new EcologyTerrain.State("minecraft:water", Map.of("level", "0")), water.replacement());
        assertEquals(EcologyEradication.Status.CONFLICT,
                target.decide(new EcologyTerrain.State("minecraft:oak_planks", Map.of())).status());
    }

    private static EcologyEngine.Cell cell(EcologyTerrain.Pos position, EcologyTerrain.State after,
            EcologyTerrain.State before, boolean adopted) {
        return new EcologyEngine.Cell(position, before, after, adopted);
    }

    private static EcologyTerrain.State vein(boolean north, boolean east, boolean waterlogged) {
        Map<String, String> properties = new java.util.TreeMap<>();
        for (String face : EcologyTerrain.FACES) properties.put(face, "false");
        properties.put("north", Boolean.toString(north));
        properties.put("east", Boolean.toString(east));
        properties.put("waterlogged", Boolean.toString(waterlogged));
        return new EcologyTerrain.State("minecraft:sculk_vein", properties);
    }
}
