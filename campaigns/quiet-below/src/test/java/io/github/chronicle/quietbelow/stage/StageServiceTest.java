package io.github.chronicle.quietbelow.stage;

import io.github.chronicle.core.campaign.Actor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageServiceTest {
    private static final Actor GM = new Actor("Game Master", "123e4567-e89b-12d3-a456-426614174000");
    private static final Actor OTHER = new Actor("Other GM", "123e4567-e89b-12d3-a456-426614174001");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC);
    private static final StageBlockState STONE = block("minecraft:stone");
    private static final StageBlockState DIRT = block("minecraft:dirt");

    @TempDir Path temporaryDirectory;

    @Test
    void exactZoneIsBoundedGuardedAndAudited() throws IOException {
        FakeWorld world = line(64);
        StageService service = service();

        StageZone zone = service.defineZone(GM, "mine", world, pos(0), pos(63));
        assertEquals(64, zone.entries().size());
        assertEquals(GM, service.state().history().getLast().actor());
        assertThrows(IllegalArgumentException.class,
                () -> service.defineZone(GM, "too_large", line(65), pos(0), pos(64)));

        FakeWorld blockEntity = line(1);
        blockEntity.blockEntities.put(pos(0), true);
        assertThrows(IllegalArgumentException.class,
                () -> service.defineZone(GM, "container", blockEntity, pos(0), pos(0)));
        FakeWorld unsupported = line(1);
        unsupported.blocks.put(pos(0), DIRT);
        assertThrows(IllegalArgumentException.class,
                () -> service.defineZone(GM, "dirt", unsupported, pos(0), pos(0)));
        FakeWorld unloaded = line(1);
        unloaded.loaded = false;
        assertThrows(IllegalStateException.class,
                () -> service.defineZone(GM, "unloaded", unloaded, pos(0), pos(0)));
        assertThrows(IllegalArgumentException.class,
                () -> service.defineZone(GM, "outside_height", line(1), new StagePosition(0, 320, 0), new StagePosition(0, 320, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> service.defineZone(GM, "outside_border", line(1), new StagePosition(1000, 64, 0), new StagePosition(1000, 64, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> service.defineZone(GM, "overflow", line(1),
                        new StagePosition(Integer.MIN_VALUE, 64, 0), new StagePosition(Integer.MAX_VALUE, 64, 0)));
    }

    @Test
    void tokenTracksExactTemplateAndApplyRequiresActiveSessionAndCurrentCapture() throws IOException {
        FakeWorld world = line(2);
        StageService service = service();
        service.defineZone(GM, "mine", world, pos(0), pos(1));
        StagePreview first = service.preview("mine", resolver(world));
        assertEquals(12, first.token().length());
        assertThrows(IllegalStateException.class,
                () -> service.apply(GM, "mine", first.token(), false, resolver(world)));
        assertThrows(IllegalArgumentException.class,
                () -> service.apply(GM, "mine", "000000000000", true, resolver(world)));

        world.blocks.put(pos(1), DIRT);
        assertThrows(IllegalStateException.class,
                () -> service.apply(GM, "mine", first.token(), true, resolver(world)));
        world.blocks.put(pos(1), STONE);
        service.defineZone(GM, "mine", world, pos(0), pos(0));
        assertNotEquals(first.token(), service.preview("mine", resolver(world)).token());
    }

    @Test
    void batchesAtEightAndSuspendsWhileSessionIsNotActive() throws IOException {
        FakeWorld world = line(17);
        StageService service = service();
        service.defineZone(GM, "mine", world, pos(0), pos(16));
        String token = service.preview("mine", resolver(world)).token();
        service.apply(GM, "mine", token, true, resolver(world));

        assertEquals(0, service.tick(false, resolver(world)));
        assertEquals(0, service.state().operation().progress());
        assertEquals(8, service.tick(true, resolver(world)));
        assertEquals(8, service.state().operation().progress());
        assertEquals(8, world.writes);
        assertEquals(8, service.tick(true, resolver(world)));
        assertEquals(1, service.tick(true, resolver(world)));
        assertEquals(StageOperationStatus.APPLIED, service.state().operation().status());
        assertEquals(17, world.writes);
    }

    @Test
    void conflictStopsWithoutOverwritingAndCanResumeAfterRepair() throws IOException {
        FakeWorld world = line(10);
        StageService service = service();
        service.defineZone(GM, "mine", world, pos(0), pos(9));
        service.apply(GM, "mine", service.preview("mine", resolver(world)).token(), true, resolver(world));
        service.tick(true, resolver(world));
        world.blocks.put(pos(8), DIRT);

        assertEquals(0, service.tick(true, resolver(world)));
        assertEquals(StageOperationStatus.CONFLICT, service.state().operation().status());
        assertEquals(DIRT, world.blocks.get(pos(8)));
        assertThrows(IllegalStateException.class, () -> service.resume(GM, true, resolver(world)));
        world.blocks.put(pos(8), STONE);
        service.resume(OTHER, true, resolver(world));
        assertEquals(0, service.state().operation().progress());
        assertEquals(8, service.tick(true, resolver(world)));
        assertEquals(2, service.tick(true, resolver(world)));
        assertEquals(StageOperationStatus.APPLIED, service.state().operation().status());
        assertEquals(OTHER, service.state().history().getLast().actor());
    }

    @Test
    void undoRestoresOnlyLatestExactStatesAndIsAlsoBatchedAndActiveGated() throws IOException {
        FakeWorld world = line(9);
        StageService service = service();
        service.defineZone(GM, "mine", world, pos(0), pos(8));
        service.apply(GM, "mine", service.preview("mine", resolver(world)).token(), true, resolver(world));
        service.tick(true, resolver(world));
        service.tick(true, resolver(world));

        assertThrows(IllegalStateException.class, () -> service.undo(GM, false, resolver(world)));
        service.undo(GM, true, resolver(world));
        assertEquals(0, service.tick(false, resolver(world)));
        assertEquals(8, service.tick(true, resolver(world)));
        assertEquals(1, service.tick(true, resolver(world)));
        assertEquals(StageOperationStatus.UNDONE, service.state().operation().status());
        for (int x = 0; x < 9; x++) assertEquals(STONE, world.blocks.get(pos(x)));
        assertThrows(IllegalStateException.class, () -> service.undo(GM, true, resolver(world)));
    }

    @Test
    void partialApplyCanBeSafelyCancelledButNeverOverwritesAConflict() throws IOException {
        FakeWorld world = line(10);
        StageService service = service();
        service.defineZone(GM, "mine", world, pos(0), pos(9));
        service.apply(GM, "mine", service.preview("mine", resolver(world)).token(), true, resolver(world));
        service.tick(true, resolver(world));
        world.blocks.put(pos(3), DIRT);

        assertThrows(IllegalStateException.class, () -> service.undo(GM, true, resolver(world)));
        assertEquals(DIRT, world.blocks.get(pos(3)));
        assertEquals(StageDirection.APPLY, service.state().operation().direction());
        world.blocks.put(pos(3), StageValidation.SCULK);

        service.undo(GM, true, resolver(world));
        service.tick(true, resolver(world));
        service.tick(true, resolver(world));
        assertEquals(StageOperationStatus.UNDONE, service.state().operation().status());
        for (int x = 0; x < 10; x++) assertEquals(STONE, world.blocks.get(pos(x)));
    }

    @Test
    void interruptedUndoRequiresResumeAndRechecksWholeTemplate() throws IOException {
        FakeWorld world = line(9);
        Path file = temporaryDirectory.resolve("undo-restart.json");
        StageService service = new StageService(new StageStore(file), CLOCK);
        service.defineZone(GM, "mine", world, pos(0), pos(8));
        service.apply(GM, "mine", service.preview("mine", resolver(world)).token(), true, resolver(world));
        service.tick(true, resolver(world));
        service.tick(true, resolver(world));
        service.undo(GM, true, resolver(world));
        service.tick(true, resolver(world));

        StageService restarted = new StageService(new StageStore(file), CLOCK);
        assertEquals(StageDirection.UNDO, restarted.state().operation().direction());
        assertEquals(StageOperationStatus.INTERRUPTED, restarted.state().operation().status());
        assertEquals(0, restarted.tick(true, resolver(world)));
        restarted.resume(GM, true, resolver(world));
        assertEquals(0, restarted.state().operation().progress());
        restarted.tick(true, resolver(world));
        restarted.tick(true, resolver(world));
        assertEquals(StageOperationStatus.UNDONE, restarted.state().operation().status());
        for (int x = 0; x < 9; x++) assertEquals(STONE, world.blocks.get(pos(x)));
    }

    @Test
    void restartInterruptsAndExplicitResumeReconcilesWriteBeforeCheckpoint() throws IOException {
        FakeWorld world = line(9);
        Path file = temporaryDirectory.resolve("restart.json");
        StageService original = new StageService(new StageStore(file), CLOCK);
        original.defineZone(GM, "mine", world, pos(0), pos(8));
        original.apply(GM, "mine", original.preview("mine", resolver(world)).token(), true, resolver(world));
        original.tick(true, resolver(world));
        // Simulate a write that reached the world after the last durable progress value.
        world.blocks.put(pos(8), StageValidation.SCULK);

        StageService restarted = new StageService(new StageStore(file), CLOCK);
        assertEquals(StageOperationStatus.INTERRUPTED, restarted.state().operation().status());
        assertEquals(8, restarted.state().operation().progress());
        assertEquals(0, restarted.tick(true, resolver(world)));
        restarted.resume(GM, true, resolver(world));
        assertEquals(0, restarted.state().operation().progress());
        assertEquals(8, restarted.tick(true, resolver(world)));
        assertEquals(1, restarted.tick(true, resolver(world)));
        assertEquals(StageOperationStatus.APPLIED, restarted.state().operation().status());
        assertEquals(1, restarted.state().history().stream().filter(entry -> entry.action().equals("stage.interrupted")).count());
    }

    @Test
    void checkpointFailureAfterWorldWriteHaltsAndKeepsDurableIntent() throws IOException {
        FakeWorld world = line(1);
        FailingRepository repository = new FailingRepository();
        StageService service = new StageService(repository, CLOCK);
        service.defineZone(GM, "mine", world, pos(0), pos(0));
        service.apply(GM, "mine", service.preview("mine", resolver(world)).token(), true, resolver(world));
        repository.failSaves = true;

        IOException failure = assertThrows(IOException.class, () -> service.tick(true, resolver(world)));
        assertTrue(failure.getMessage().contains("recovery is required"));
        assertEquals(StageValidation.SCULK, world.blocks.get(pos(0)));
        assertEquals(StageOperationStatus.RECOVERY_REQUIRED, service.state().operation().status());
        assertEquals(StageOperationStatus.APPLYING, repository.durable.operation().status());
        assertEquals(0, repository.durable.operation().progress());
        assertEquals(0, service.tick(true, resolver(world)));
    }

    private StageService service() throws IOException {
        return new StageService(new StageStore(temporaryDirectory.resolve("stages-" + System.nanoTime() + ".json")), CLOCK);
    }

    private static StageWorldResolver resolver(FakeWorld world) {
        return dimension -> world.dimension.equals(dimension) ? world : null;
    }

    private static FakeWorld line(int count) {
        FakeWorld world = new FakeWorld("minecraft:overworld");
        for (int x = 0; x < count; x++) world.blocks.put(pos(x), STONE);
        return world;
    }

    private static StagePosition pos(int x) { return new StagePosition(x, 64, 0); }
    private static StageBlockState block(String id) { return new StageBlockState(id, Map.of()); }

    private static final class FakeWorld implements StageWorld {
        private final String dimension;
        private final Map<StagePosition, StageBlockState> blocks = new HashMap<>();
        private final Map<StagePosition, Boolean> blockEntities = new HashMap<>();
        private boolean loaded = true;
        private int writes;

        private FakeWorld(String dimension) { this.dimension = dimension; }
        @Override public String dimension() { return dimension; }
        @Override public boolean isLoaded(StagePosition position) { return loaded; }
        @Override public boolean isInBuildLimit(StagePosition position) { return position.y() >= -64 && position.y() < 320; }
        @Override public boolean isInsideWorldBorder(StagePosition position) { return Math.abs(position.x()) < 1000; }
        @Override public boolean hasBlockEntity(StagePosition position) { return blockEntities.getOrDefault(position, false); }
        @Override public StageBlockState getBlockState(StagePosition position) {
            return blocks.getOrDefault(position, block("minecraft:air"));
        }
        @Override public boolean setBlockState(StagePosition position, StageBlockState state) {
            blocks.put(position, state);
            writes++;
            return true;
        }
    }

    private static final class FailingRepository implements StageRepository {
        private StageState durable = StageState.newWorld();
        private boolean failSaves;
        @Override public StageState load() { return durable; }
        @Override public void save(StageState state) throws IOException {
            if (failSaves) throw new IOException("deliberate checkpoint failure");
            durable = state;
        }
    }
}
