package io.github.chronicle.core.server.testing;

import io.github.chronicle.core.areas.AreaBounds;
import io.github.chronicle.core.areas.AreaCell;
import io.github.chronicle.core.areas.AreaChange;
import io.github.chronicle.core.areas.AreaSnapshot;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.menu.EventEditorForm;
import io.github.chronicle.core.play.EventExecution;
import io.github.chronicle.core.play.ObservationCatalog;
import io.github.chronicle.core.play.PlayState;
import io.github.chronicle.core.server.AreaChanges;
import io.github.chronicle.core.server.EventRuntime;
import io.github.chronicle.core.server.EventWorldBatches;
import io.github.chronicle.core.server.PlayRuntime;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.literal;

/** Opt-in CF22 conflict/recovery and exact-maximum Event proof for disposable loopback worlds only. */
public final class EventRecoveryTrial {
    public static final String MAX_EVENT = "chronicle_core:trial_max_apply";
    public static final String MAX_MESSAGE = "The maximum saved regions answer together.";

    private static final int CELLS = 262_144;
    private static final int WORK_BUDGET = 4_096;
    private static final AreaBounds MAX_A = bounds("event_max_a", 0, 127);
    private static final AreaBounds MAX_B = bounds("event_max_b", 128, 255);
    private static final BlockPos HOME = new BlockPos(1, 64, 1);
    private static final BlockPos A_CHEST = new BlockPos(4, 64, 4);
    private static final BlockPos A_SIGN = new BlockPos(5, 64, 4);
    private static final BlockPos A_COLOR = new BlockPos(6, 64, 4);
    private static final BlockPos B_CHEST = new BlockPos(33, 64, 4);
    private static final BlockPos B_COLOR = new BlockPos(34, 64, 4);
    private static final int B_COLOR_INDEX = 2 + 32 * 4;
    private static final UUID OPERATOR_A = offlineUuid("ChronicleRecA");
    private static final UUID OPERATOR_B = offlineUuid("ChronicleRecB");

    private static Job job;
    private static UUID conflictOperation;
    private static UUID conflictActor;

    private EventRecoveryTrial() {}

    public static void register() {
        if (!"true".equals(System.getenv("CHRONICLE_EVENT_RECOVERY_TRIAL"))) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                literal("chronicle_event_recovery_trial")
                        .then(literal("run_conflict").executes(c -> command(c.getSource(), Action.RUN_CONFLICT)))
                        .then(literal("recover_conflict").executes(c -> command(c.getSource(), Action.RECOVER_CONFLICT)))
                        .then(literal("max_clear").executes(c -> command(c.getSource(), Action.MAX_CLEAR)))
                        .then(literal("max_fill_configure").executes(c -> command(c.getSource(), Action.MAX_FILL_CONFIGURE)))
                        .then(literal("max_position").executes(c -> command(c.getSource(), Action.MAX_POSITION)))
                        .then(literal("max_run").executes(c -> command(c.getSource(), Action.MAX_RUN)))));
        ServerTickEvents.END_SERVER_TICK.register(EventRecoveryTrial::tick);
    }

    private static int command(ServerCommandSource source, Action action) {
        try {
            fixture(source);
            require(source.hasPermissionLevel(2), "operator permission required");
            ServerPlayerEntity player = source.getPlayerOrThrow();
            require(job == null, "another recovery/maximum proof is running");
            switch (action) {
                case RUN_CONFLICT -> runConflict(source, player);
                case RECOVER_CONFLICT -> recoverConflict(source, player);
                case MAX_CLEAR -> beginMaximumClear(source, player);
                case MAX_FILL_CONFIGURE -> beginMaximumFill(source, player);
                case MAX_POSITION -> positionMaximum(source, player);
                case MAX_RUN -> runMaximum(source, player);
            }
            return 1;
        } catch (Exception failure) {
            source.sendError(Text.literal("Event recovery trial FAIL " + action.wire + ": " + safe(failure)));
            return 0;
        }
    }

    private static void runConflict(ServerCommandSource source, ServerPlayerEntity player) throws Exception {
        require(player.getUuid().equals(OPERATOR_A), "conflict must use the pinned first fixture operator");
        require(EventRuntime.stored(source.getServer(), EventActionsTrial.WORLD_EVENT) != null,
                "configure the Event actions fixture first");
        require(source.getWorld().getBlockState(B_COLOR).isOf(Blocks.STONE),
                "target world is not ready at the second Region conflict cell");
        require(!PlayRuntime.service(source.getServer()).state().deliveries().containsKey(EventActionsTrial.WORLD_EVENT),
                "the conflict Event already has a delivery receipt");
        EventRuntime.Review review = EventRuntime.review(source.getServer(), EventActionsTrial.WORLD_EVENT);
        require(review.recipients().contains(player.getUuid()), "the initiating operator is not a pinned recipient");
        EventRuntime.run(player, EventActionsTrial.WORLD_EVENT, review);
        job = new Job(player.getUuid(), Stage.WAIT_CONFLICT_WINDOW);
    }

    private static void recoverConflict(ServerCommandSource source, ServerPlayerEntity player) throws Exception {
        require(player.getUuid().equals(OPERATOR_B), "recovery must use the pinned second fixture operator");
        EventExecution execution = execution(source.getServer(), EventActionsTrial.WORLD_EVENT);
        UUID operation = requireConflictIdentity(execution);
        require(!player.getUuid().equals(conflictActor), "recovery must use a second operator");
        require(source.getWorld().getBlockState(B_COLOR).isOf(Blocks.DIAMOND_BLOCK),
                "foreign conflict cell was overwritten before explicit recovery");
        require(!PlayRuntime.service(source.getServer()).state().deliveries().containsKey(EventActionsTrial.WORLD_EVENT),
                "Event media completed before conflict recovery");

        put(source.getWorld(), B_COLOR, Blocks.STONE);
        PlayRuntime.setSelfParticipant(player, true);
        player.changeGameMode(GameMode.CREATIVE);
        home(player, source.getWorld());
        EventRuntime.recover(player, EventActionsTrial.WORLD_EVENT);
        EventExecution recovering = execution(source.getServer(), EventActionsTrial.WORLD_EVENT);
        require(recovering.operationId().equals(operation), "recovery replaced the committed operation UUID");
        require(recovering.actor().equals(conflictActor), "recovery replaced the original Event actor");
        job = new Job(player.getUuid(), Stage.WAIT_CONFLICT_RECOVERY);
        job.operation = operation;
    }

    private static void beginMaximumClear(ServerCommandSource source, ServerPlayerEntity player) {
        require(player.getUuid().equals(OPERATOR_A), "maximum proof must use the pinned first fixture operator");
        require(AreaChanges.catalog(source.getServer()).areas().isEmpty(),
                "maximum clear requires a fresh Area catalog");
        player.changeGameMode(GameMode.SPECTATOR);
        player.teleport(source.getWorld(), 128.5, 200, 32.5, Set.of(), 0, 0, false);
        job = new Job(player.getUuid(), Stage.WAIT_MAX_CLEAR_LOAD);
    }

    private static void beginMaximumFill(ServerCommandSource source, ServerPlayerEntity player) throws Exception {
        AreaChange first = area(source.getServer(), "event_max_a");
        AreaChange second = area(source.getServer(), "event_max_b");
        require(first.start().isPresent() && second.start().isPresent(), "maximum saved Regions are incomplete");
        require(first.bounds().cells() + second.bounds().cells() == CELLS, "maximum cell count is not exact");
        require(uniqueChunks(first.bounds(), second.bounds()) == 64, "maximum chunk count is not exact");
        require(loaded(source.getWorld()), "maximum Regions are not player-loaded");
        AreaCell air = AreaChanges.captureCell(source.getServer(), source.getWorld(), position(MAX_A, 0), MAX_A, false);
        require(source.getWorld().getBlockState(position(MAX_A, 0)).isAir(), "maximum clear did not leave air evidence");
        job = new Job(player.getUuid(), Stage.VERIFY_MAX_SNAPSHOTS);
        job.air = air;
    }

    private static void positionMaximum(ServerCommandSource source, ServerPlayerEntity player) {
        player.changeGameMode(GameMode.CREATIVE);
        player.teleport(source.getWorld(), 128.5, 100, 80.5, Set.of(), 0, 0, false);
        player.setVelocity(0, 0, 0);
        player.onLanding();
        job = new Job(player.getUuid(), Stage.WAIT_MAX_RUN_LOAD);
    }

    private static void runMaximum(ServerCommandSource source, ServerPlayerEntity player) throws Exception {
        require(loaded(source.getWorld()), "maximum Regions are not player-loaded");
        require(!inside(player.getBlockPos(), MAX_A) && !inside(player.getBlockPos(), MAX_B),
                "maximum participant must remain outside both target Regions");
        EventRuntime.Review review = EventRuntime.review(source.getServer(), MAX_EVENT);
        require(review.recipients().contains(player.getUuid()), "maximum operator is not an eligible recipient");
        EventRuntime.run(player, MAX_EVENT, review);
        job = new Job(player.getUuid(), Stage.WAIT_MAX_COMPLETE);
    }

    private static void tick(MinecraftServer server) {
        if (job == null) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(job.player);
        if (player == null) {
            job = null;
            return;
        }
        ServerCommandSource source = player.getCommandSource();
        try {
            switch (job.stage) {
                case WAIT_CONFLICT_WINDOW -> conflictWindow(source);
                case WAIT_CONFLICT -> waitConflict(source);
                case WAIT_CONFLICT_RECOVERY -> waitConflictRecovery(source);
                case WAIT_MAX_CLEAR_LOAD -> {
                    if (!loaded(source.getWorld())) return;
                    job.stage = Stage.CLEAR_MAX;
                }
                case CLEAR_MAX -> mutateMaximum(source.getWorld(), Blocks.AIR, Stage.VERIFY_MAX_CLEAR);
                case VERIFY_MAX_CLEAR -> verifyMaximumLive(source, true, Stage.MAX_CLEAR_DONE);
                case MAX_CLEAR_DONE -> finish(source, "max_clear");
                case VERIFY_MAX_SNAPSHOTS -> verifyMaximumSnapshots(source);
                case FILL_MAX -> mutateMaximum(source.getWorld(), Blocks.STONE, Stage.VERIFY_MAX_FILL);
                case VERIFY_MAX_FILL -> verifyMaximumLive(source, false, Stage.SAVE_MAX_EVENT);
                case SAVE_MAX_EVENT -> saveMaximumEvent(source, player);
                case WAIT_MAX_RUN_LOAD -> {
                    if (!loaded(source.getWorld())) return;
                    require(source.getWorld().getForcedChunks().isEmpty(), "maximum preparation introduced forced chunks");
                    finish(source, "max_position");
                }
                case WAIT_MAX_COMPLETE -> waitMaximumComplete(source);
                case VERIFY_MAX_RESULT -> verifyMaximumResult(source);
            }
        } catch (Exception failure) {
            source.sendError(Text.literal("Event recovery trial FAIL " + job.stage.wire + ": " + safe(failure)));
            job = null;
        }
    }

    /** The durable target-1/cursor-0 checkpoint proves target 0 saved; inject before the next WRITE slice. */
    private static void conflictWindow(ServerCommandSource source) throws Exception {
        EventExecution execution = PlayRuntime.service(source.getServer()).state().executions().get(EventActionsTrial.WORLD_EVENT);
        if (execution == null) return;
        conflictOperation = execution.operationId();
        conflictActor = execution.actor();
        JournalEntry entry = journal(source.getServer(), conflictOperation);
        if (entry == null || !entry.phase.equals("APPLYING") || !entry.step.equals("WRITE")
                || entry.target != 1 || entry.cursor != 0) return;
        require(source.getWorld().getBlockState(B_COLOR).isOf(Blocks.STONE),
                "second Region conflict cell changed before injection");
        require(B_COLOR_INDEX < 256, "conflict cell is not in the first target-1 write slice");
        put(source.getWorld(), B_COLOR, Blocks.DIAMOND_BLOCK);
        require(source.getWorld().getBlockState(B_COLOR).isOf(Blocks.DIAMOND_BLOCK),
                "foreign conflict cell was not installed");
        job.operation = conflictOperation;
        job.stage = Stage.WAIT_CONFLICT;
    }

    private static void waitConflict(ServerCommandSource source) {
        EventExecution execution = execution(source.getServer(), EventActionsTrial.WORLD_EVENT);
        if (execution.phase() != EventExecution.Phase.CONFLICT) return;
        require(execution.operationId().equals(job.operation), "conflict replaced the committed operation UUID");
        require(EventWorldBatches.status(source.getServer(), job.operation).orElseThrow() == EventWorldBatches.Status.CONFLICT,
                "world batch did not retain explicit conflict authority");
        require(source.getWorld().getBlockState(B_COLOR).isOf(Blocks.DIAMOND_BLOCK),
                "foreign conflict cell was overwritten");
        require(source.getWorld().getBlockState(A_COLOR).isOf(Blocks.BLUE_WOOL),
                "durably saved first Region did not retain its applied state");
        require(!PlayRuntime.service(source.getServer()).state().deliveries().containsKey(EventActionsTrial.WORLD_EVENT),
                "Event media completed before exact world recovery");
        pass(source, "conflict", job.operation.toString());
        job = null;
    }

    private static void waitConflictRecovery(ServerCommandSource source) {
        EventExecution execution = execution(source.getServer(), EventActionsTrial.WORLD_EVENT);
        if (execution.phase() != EventExecution.Phase.COMPLETE) return;
        require(execution.operationId().equals(job.operation), "completed recovery replaced the operation UUID");
        require(execution.actor().equals(conflictActor), "completed recovery replaced the original actor");
        require(EventWorldBatches.status(source.getServer(), job.operation).orElseThrow() == EventWorldBatches.Status.WORLD_COMMITTED,
                "recovered world batch did not commit");
        require(PlayRuntime.service(source.getServer()).state().deliveries().containsKey(EventActionsTrial.WORLD_EVENT),
                "recovered Event delivery receipt is missing");
        verifyPairedEnd(source.getWorld());
        pass(source, "recover_conflict", job.operation.toString());
        job = null;
    }

    private static void mutateMaximum(ServerWorld world, Block block, Stage next) {
        int end = Math.min(CELLS, job.cursor + WORK_BUDGET);
        for (; job.cursor < end; job.cursor++) put(world, maximumPosition(job.cursor), block);
        if (job.cursor == CELLS) {
            job.cursor = 0;
            job.stage = next;
        }
    }

    private static void verifyMaximumLive(ServerCommandSource source, boolean air, Stage next) {
        if (air && job.air == null) {
            job.air = AreaChanges.captureCell(source.getServer(), source.getWorld(), position(MAX_A, 0), MAX_A, false);
            require(source.getWorld().getBlockState(position(MAX_A, 0)).isAir(),
                    "maximum clear did not produce air evidence");
        }
        int end = Math.min(CELLS, job.cursor + WORK_BUDGET);
        for (; job.cursor < end; job.cursor++) {
            BlockPos pos = maximumPosition(job.cursor);
            AreaBounds bounds = job.cursor < MAX_A.cells() ? MAX_A : MAX_B;
            if (air) {
                require(source.getWorld().getBlockState(pos).isAir(), "maximum clear mismatch at " + pos.toShortString());
                require(AreaChanges.captureCell(source.getServer(), source.getWorld(), pos, bounds, false).equals(job.air),
                        "maximum clear retained non-air cell evidence at " + pos.toShortString());
            } else {
                require(source.getWorld().getBlockState(pos).isOf(Blocks.STONE),
                        "maximum fill mismatch at " + pos.toShortString());
            }
        }
        if (job.cursor == CELLS) {
            job.cursor = 0;
            job.stage = next;
        }
    }

    private static void verifyMaximumSnapshots(ServerCommandSource source) throws Exception {
        AreaChange first = area(source.getServer(), "event_max_a");
        AreaChange second = area(source.getServer(), "event_max_b");
        AreaSnapshot a = AreaChanges.snapshots(source.getServer()).load(first.requireSnapshot(AreaState.START));
        AreaSnapshot b = AreaChanges.snapshots(source.getServer()).load(second.requireSnapshot(AreaState.START));
        int end = Math.min(CELLS, job.cursor + WORK_BUDGET);
        for (; job.cursor < end; job.cursor++) {
            AreaCell cell = job.cursor < MAX_A.cells() ? a.cell(job.cursor) : b.cell(job.cursor - MAX_A.cells());
            require(cell.equals(job.air), "maximum saved Region does not contain exact air at cell " + job.cursor);
        }
        if (job.cursor == CELLS) {
            job.cursor = 0;
            job.stage = Stage.FILL_MAX;
        }
    }

    private static void saveMaximumEvent(ServerCommandSource source, ServerPlayerEntity player) throws Exception {
        require(EventRuntime.stored(source.getServer(), MAX_EVENT) == null, "maximum Event already exists");
        AreaChange a = area(source.getServer(), "event_max_a");
        AreaChange b = area(source.getServer(), "event_max_b");
        EventEditorForm form = new EventEditorForm(MAX_EVENT, 0, "Maximum Region apply", "Native exact-limit proof",
                MAX_MESSAGE, "minecraft:block.note_block.bell", ObservationCatalog.Audience.PARTICIPANTS.name(), "", 0,
                PlayState.Mode.MANUAL.name(), "", 0, 0, "",
                List.of(change(a, AreaState.START), change(b, AreaState.START)), true);
        EventRuntime.save(player, form, null, true);
        require(EventRuntime.configuration(source.getServer(), MAX_EVENT).definition().changes().stream()
                        .mapToInt(c -> c.snapshot().cells()).sum() == CELLS,
                "maximum Event did not retain exactly 262144 cells");
        finish(source, "max_fill_configure");
    }

    private static void waitMaximumComplete(ServerCommandSource source) throws IOException {
        EventExecution execution = PlayRuntime.service(source.getServer()).state().executions().get(MAX_EVENT);
        if (execution == null || execution.phase() != EventExecution.Phase.COMPLETE) return;
        job.operation = execution.operationId();
        require(EventWorldBatches.status(source.getServer(), job.operation).orElseThrow() == EventWorldBatches.Status.WORLD_COMMITTED,
                "maximum world batch did not commit");
        PlanCounts counts = planCounts(source.getServer(), job.operation);
        require(counts.cells == CELLS && counts.chunks == 64 && counts.targets == 2,
                "maximum immutable batch plan counts are not exact");
        require(PlayRuntime.service(source.getServer()).state().deliveries().containsKey(MAX_EVENT),
                "maximum Event delivery receipt is missing");
        require(source.getWorld().getForcedChunks().isEmpty(), "maximum Event introduced forced chunks");
        job.air = AreaChanges.captureCell(source.getServer(), source.getWorld(), position(MAX_A, 0), MAX_A, false);
        require(source.getWorld().getBlockState(position(MAX_A, 0)).isAir(), "maximum result is not air");
        job.cursor = 0;
        job.stage = Stage.VERIFY_MAX_RESULT;
    }

    private static void verifyMaximumResult(ServerCommandSource source) {
        verifyMaximumLive(source, true, Stage.VERIFY_MAX_RESULT);
        if (job != null && job.cursor == 0 && job.stage == Stage.VERIFY_MAX_RESULT) {
            UUID operation = job.operation;
            pass(source, "max_run", operation + " cells=262144 chunks=64");
            job = null;
        }
    }

    private static EventExecution execution(MinecraftServer server, String id) {
        EventExecution execution = PlayRuntime.service(server).state().executions().get(id);
        require(execution != null, "Event execution is missing: " + id);
        return execution;
    }

    private static UUID requireConflictIdentity(EventExecution execution) {
        require(execution.phase() == EventExecution.Phase.CONFLICT, "Event execution is not in conflict");
        if (conflictOperation == null) conflictOperation = execution.operationId();
        if (conflictActor == null) conflictActor = execution.actor();
        require(execution.operationId().equals(conflictOperation), "conflict operation UUID changed");
        require(execution.actor().equals(conflictActor), "conflict actor changed");
        return conflictOperation;
    }

    private static void verifyPairedEnd(ServerWorld world) {
        require(chest(world, A_CHEST).getStack(0).isOf(Items.EMERALD)
                        && chest(world, A_CHEST).getStack(0).getCount() == 4,
                "recovered first Region chest NBT is not exact");
        require(signLine(world, A_SIGN).equals("A END") && world.getBlockState(A_COLOR).isOf(Blocks.BLUE_WOOL),
                "recovered first Region block/sign state is not exact");
        require(world.getBlockState(B_CHEST).isOf(Blocks.STONE), "recovered second Region did not replace its chest");
        Block altar = Registries.BLOCK.get(Identifier.of("quiet_below:ancient_altar"));
        require(world.getBlockState(B_COLOR).isOf(altar), "recovered conflict cell did not reach the exact saved state");
    }

    private static EventEditorForm.Change change(AreaChange area, AreaState state) {
        var ref = area.requireSnapshot(state);
        return new EventEditorForm.Change(area.bounds().regionId(), area.name(), state.name(), area.revision(),
                ref.sha256(), area.bounds().dimension(), true, "");
    }

    private static AreaChange area(MinecraftServer server, String id) {
        AreaChange area = AreaChanges.catalog(server).areas().get(id);
        if (area == null) throw new IllegalStateException("Area missing: " + id);
        return area;
    }

    private static JournalEntry journal(MinecraftServer server, UUID operation) throws IOException {
        Path path = server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/event-batches/journal.bin");
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 4L * 1024 * 1024) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            require(in.readInt() == 0x4345424a && in.readInt() == 1, "invalid Event batch journal magic/schema");
            in.readLong();
            int count = in.readInt();
            require(count >= 0 && count <= 256, "invalid Event batch journal count");
            JournalEntry found = null;
            for (int i = 0; i < count; i++) {
                UUID id = UUID.fromString(text(in, 36));
                text(in, 64);
                String phase = text(in, 32);
                String step = text(in, 32);
                int target = in.readInt();
                int cursor = in.readInt();
                text(in, 4096);
                text(in, 128);
                if (id.equals(operation)) found = new JournalEntry(phase, step, target, cursor);
            }
            require(in.read() == -1, "Event batch journal has trailing bytes");
            return found;
        } catch (EOFException failure) {
            throw new IOException("Event batch journal is truncated", failure);
        }
    }

    private static PlanCounts planCounts(MinecraftServer server, UUID operation) throws IOException {
        Path path = server.getSavePath(WorldSavePath.ROOT)
                .resolve("chronicle/event-batches/" + operation + ".plan.bin");
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= 4L * 1024 * 1024,
                "maximum immutable batch plan is missing or oversized");
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            require(in.readInt() == 0x43454250 && in.readInt() == 1,
                    "invalid Event batch plan magic/schema");
            require(UUID.fromString(text(in, 36)).equals(operation), "Event batch plan UUID changed");
            UUID.fromString(text(in, 36));
            text(in, 128);
            text(in, 96);
            in.readLong();
            in.readLong();
            return new PlanCounts(in.readInt(), in.readInt(), in.readInt());
        } catch (EOFException failure) {
            throw new IOException("Event batch plan is truncated", failure);
        }
    }

    private static String text(DataInputStream in, int maximum) throws IOException {
        int length = in.readInt();
        require(length >= 0 && length <= maximum, "invalid Event batch journal text length");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException("truncated Event batch journal text");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void finish(ServerCommandSource source, String action) {
        pass(source, action, "");
        job = null;
    }

    private static void pass(ServerCommandSource source, String action, String detail) {
        String suffix = detail.isBlank() ? "" : " " + detail;
        source.sendFeedback(() -> Text.literal("Event recovery trial PASS " + action + suffix), false);
    }

    private static void fixture(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        Path root = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
        require(root != null && root.getParent() != null && root.getParent().getFileName().toString().equals("test-runs")
                        && root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")
                        && Files.isRegularFile(root.resolve(".event-actions-fixture"), LinkOption.NOFOLLOW_LINKS)
                        && Files.isRegularFile(root.resolve(".event-recovery-fixture"), LinkOption.NOFOLLOW_LINKS)
                        && server.isDedicated() && !server.isOnlineMode() && server.getServerPort() == 25575
                        && "127.0.0.1".equals(server.getServerIp())
                        && source.getWorld().getRegistryKey() == net.minecraft.world.World.OVERWORLD,
                "unsafe Event recovery fixture");
    }

    private static AreaBounds bounds(String id, int minX, int maxX) {
        return new AreaBounds(id, "minecraft:overworld", 1,
                new BlockPosition(minX, 64, 0), new BlockPosition(maxX, 79, 63));
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static BlockPos maximumPosition(int index) {
        return index < MAX_A.cells() ? position(MAX_A, index) : position(MAX_B, index - MAX_A.cells());
    }

    private static BlockPos position(AreaBounds bounds, int index) {
        int x = index % bounds.sizeX();
        int rest = index / bounds.sizeX();
        int z = rest % bounds.sizeZ();
        int y = rest / bounds.sizeZ();
        return new BlockPos(bounds.min().x() + x, bounds.min().y() + y, bounds.min().z() + z);
    }

    private static boolean inside(BlockPos pos, AreaBounds bounds) {
        return pos.getX() >= bounds.min().x() && pos.getX() <= bounds.max().x()
                && pos.getY() >= bounds.min().y() && pos.getY() <= bounds.max().y()
                && pos.getZ() >= bounds.min().z() && pos.getZ() <= bounds.max().z();
    }

    private static boolean loaded(ServerWorld world) {
        for (int x = 0; x <= 15; x++) for (int z = 0; z <= 3; z++)
            if (!world.getChunkManager().isChunkLoaded(x, z)) return false;
        return true;
    }

    private static int uniqueChunks(AreaBounds... values) {
        java.util.HashSet<Long> chunks = new java.util.HashSet<>();
        for (AreaBounds bounds : values)
            for (int x = Math.floorDiv(bounds.min().x(), 16); x <= Math.floorDiv(bounds.max().x(), 16); x++)
                for (int z = Math.floorDiv(bounds.min().z(), 16); z <= Math.floorDiv(bounds.max().z(), 16); z++)
                    chunks.add(((long) x << 32) ^ (z & 0xffffffffL));
        return chunks.size();
    }

    private static void put(ServerWorld world, BlockPos pos, Block block) {
        world.setBlockState(pos, block.getDefaultState(),
                Block.FORCE_STATE_AND_SKIP_CALLBACKS_AND_DROPS | Block.NOTIFY_LISTENERS);
    }

    private static void home(ServerPlayerEntity player, ServerWorld world) {
        player.teleport(world, HOME.getX() + .5, 64, HOME.getZ() + .5, Set.of(), 0, 0, false);
        player.setVelocity(0, 0, 0);
        player.onLanding();
    }

    private static ChestBlockEntity chest(ServerWorld world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof ChestBlockEntity chest))
            throw new IllegalStateException("chest missing at " + pos.toShortString());
        return chest;
    }

    private static String signLine(ServerWorld world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof SignBlockEntity sign))
            throw new IllegalStateException("sign missing at " + pos.toShortString());
        return sign.getFrontText().getMessage(0, false).getString();
    }

    private static String safe(Throwable failure) {
        String value = failure.getMessage();
        if (value == null || value.isBlank()) value = failure.getClass().getSimpleName();
        return value.substring(0, Math.min(value.length(), 480));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private enum Action {
        RUN_CONFLICT("run_conflict"), RECOVER_CONFLICT("recover_conflict"), MAX_CLEAR("max_clear"),
        MAX_FILL_CONFIGURE("max_fill_configure"), MAX_POSITION("max_position"), MAX_RUN("max_run");
        final String wire;
        Action(String wire) { this.wire = wire; }
    }

    private enum Stage {
        WAIT_CONFLICT_WINDOW("run_conflict"), WAIT_CONFLICT("run_conflict"),
        WAIT_CONFLICT_RECOVERY("recover_conflict"), WAIT_MAX_CLEAR_LOAD("max_clear"),
        CLEAR_MAX("max_clear"), VERIFY_MAX_CLEAR("max_clear"), MAX_CLEAR_DONE("max_clear"),
        VERIFY_MAX_SNAPSHOTS("max_fill_configure"), FILL_MAX("max_fill_configure"),
        VERIFY_MAX_FILL("max_fill_configure"), SAVE_MAX_EVENT("max_fill_configure"),
        WAIT_MAX_RUN_LOAD("max_position"), WAIT_MAX_COMPLETE("max_run"), VERIFY_MAX_RESULT("max_run");
        final String wire;
        Stage(String wire) { this.wire = wire; }
    }

    private static final class Job {
        final UUID player;
        Stage stage;
        UUID operation;
        AreaCell air;
        int cursor;
        Job(UUID player, Stage stage) { this.player = player; this.stage = stage; }
    }

    private record JournalEntry(String phase, String step, int target, int cursor) {}
    private record PlanCounts(int cells, int chunks, int targets) {}
}
