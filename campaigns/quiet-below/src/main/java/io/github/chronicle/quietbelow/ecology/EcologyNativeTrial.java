package io.github.chronicle.quietbelow.ecology;

import com.mojang.brigadier.CommandDispatcher;
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.SculkSensorBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.SculkCatalystBlockEntity;
import net.minecraft.block.entity.SculkSensorBlockEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

import static net.minecraft.server.command.CommandManager.literal;

/** Opt-in real-vanilla hook trial. It is unavailable outside the disposable loopback fixture. */
public final class EcologyNativeTrial {
    private static final String ENABLED = System.getenv("CHRONICLE_ECOLOGY_NATIVE_TRIAL");
    private static final Pattern RUN_NAME = Pattern.compile("[0-9]{8}T[0-9]{12,}Z");
    private static final BlockPos HEART = new BlockPos(0, 64, 1);
    private static final BlockPos SENSOR = new BlockPos(1, 65, 1);
    private static final BlockPos CATALYST = new BlockPos(2, 65, 1);
    private static final BlockPos DEATH = new BlockPos(3, 65, 1);

    private EcologyNativeTrial() {
    }

    public static void register() {
        if (ENABLED == null || ENABLED.isBlank()) {
            return;
        }
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> commands(dispatcher));
    }

    private static void commands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("quiet_below_native_trial")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("inactive").executes(context -> run(context.getSource(), false)))
                .then(literal("active").executes(context -> run(context.getSource(), true))));
    }

    private static int run(ServerCommandSource source, boolean active) {
        try {
            requireFixture(source.getServer());
            EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
            ServerWorld world = source.getWorld();
            require(engine.setup() != null, "ecology is not configured");
            require(engine.setup().dimension().equals(world.getRegistryKey().getValue().toString()),
                    "command source is not in the ecology dimension");
            require(engine.owns(QuietBelowEcology.pos(CATALYST)), "fixture catalyst is not ecology-owned");
            require(engine.owns(QuietBelowEcology.pos(SENSOR)), "fixture sensor is not ecology-owned");
            require(world.getBlockState(HEART).isOf(net.minecraft.block.Blocks.REINFORCED_DEEPSLATE),
                    "fixture Heart is missing");
            require(source.getPlayer() != null, "trial command requires a player source");
            require(QuietBelowEcology.playing(source.getServer()) == active,
                    active ? "active trial requires occupied ACTIVE play" : "inactive trial requires paused or unoccupied play");
            if (active) {
                return startActive(source, world, engine);
            }
            inactive(source, world);
            source.sendFeedback(() -> Text.literal(
                    "PASS native inactive hooks: catalyst intake/charge and sensor activation were blocked; no world mutation requested."), false);
            return 1;
        } catch (Exception exception) {
            source.sendError(Text.literal("FAIL native ecology trial: " + detail(exception)));
            return 0;
        }
    }

    private static void inactive(ServerCommandSource source, ServerWorld world) {
        SculkCatalystBlockEntity catalyst = catalyst(world);
        int cursors = catalyst.getEventListener().getSpreadManager().getCursors().size();
        int charge = charge(catalyst);
        TrialZombie victim = new TrialZombie(world);
        boolean accepted = catalyst.getEventListener().listen(
                world, GameEvent.ENTITY_DIE, GameEvent.Emitter.of(victim), Vec3d.ofBottomCenter(DEATH));
        require(!accepted, "inactive owned catalyst accepted ENTITY_DIE");
        require(!victim.isExperienceDroppingDisabled(), "inactive catalyst disabled victim XP");
        require(catalyst.getEventListener().getSpreadManager().getCursors().size() == cursors,
                "inactive catalyst added a spread cursor");
        require(charge(catalyst) == charge, "inactive catalyst changed queued charge");

        SculkSensorBlockEntity sensor = world.getBlockEntity(SENSOR, BlockEntityType.SCULK_SENSOR)
                .orElseThrow(() -> new IllegalStateException("fixture sensor block entity is missing"));
        var before = world.getBlockState(SENSOR);
        int frequency = sensor.getLastVibrationFrequency();
        require(SculkSensorBlock.isInactive(before), "fixture sensor must begin inactive");
        var player = source.getPlayer();
        require(player != null, "trial command requires a player source");
        sensor.getVibrationCallback().accept(
                world, DEATH, GameEvent.STEP, player, player, 1.0F);
        require(world.getBlockState(SENSOR).equals(before), "inactive owned sensor changed block state");
        require(sensor.getLastVibrationFrequency() == frequency, "inactive owned sensor accepted a vibration");
    }

    private static int startActive(ServerCommandSource source, ServerWorld world, EcologyEngine engine) {
        require(engine.rooted(QuietBelowEcology.pos(CATALYST)), "fixture catalyst is not rooted yet; retry after topology resolves");
        SculkCatalystBlockEntity catalyst = catalyst(world);
        int beforeCharge = charge(catalyst);
        int cursors = catalyst.getEventListener().getSpreadManager().getCursors().size();
        TrialZombie victim = new TrialZombie(world);
        boolean accepted = catalyst.getEventListener().listen(
                world, GameEvent.ENTITY_DIE, GameEvent.Emitter.of(victim), Vec3d.ofBottomCenter(DEATH));
        require(!accepted, "active campaign catalyst accepted ENTITY_DIE");
        require(!victim.isExperienceDroppingDisabled(), "campaign catalyst consumed victim XP");
        require(charge(catalyst) == beforeCharge && catalyst.getEventListener().getSpreadManager().getCursors().size() == cursors,
                "campaign catalyst queued death/XP spread");
        // A legacy queue is retained but cannot tick, even during occupied active play.
        catalyst.getEventListener().getSpreadManager().spread(DEATH, 32);
        int queued = charge(catalyst);
        long revision = engine.view().journalBytes();
        SculkCatalystBlockEntity.tick(world, CATALYST, world.getBlockState(CATALYST), catalyst);
        require(charge(catalyst) == queued, "campaign catalyst advanced legacy queued charge");
        require(engine.view().journalBytes() == revision, "campaign catalyst journaled native growth");
        var unowned=new SculkCatalystBlockEntity(new BlockPos(12,65,1),net.minecraft.block.Blocks.SCULK_CATALYST.getDefaultState());
        TrialZombie second=new TrialZombie(world);
        require(!unowned.getEventListener().listen(world,GameEvent.ENTITY_DIE,GameEvent.Emitter.of(second),Vec3d.ofBottomCenter(DEATH)),"unowned catalyst bypassed the configured campaign growth policy");
        require(!second.isExperienceDroppingDisabled(),"unowned catalyst consumed XP in the campaign dimension");
        source.sendFeedback(() -> Text.literal("PASS native active hooks: deaths/XP ignored and legacy charge frozen; ecology is the only campaign growth system."), false);
        return 1;
    }

    private static SculkCatalystBlockEntity catalyst(ServerWorld world) {
        return world.getBlockEntity(CATALYST, BlockEntityType.SCULK_CATALYST)
                .orElseThrow(() -> new IllegalStateException("fixture catalyst block entity is missing"));
    }

    private static int charge(SculkCatalystBlockEntity catalyst) {
        return catalyst.getEventListener().getSpreadManager().getCursors().stream()
                .mapToInt(cursor -> cursor.getCharge()).sum();
    }

    private static void requireFixture(MinecraftServer server) throws IOException {
        Path run = server.getRunDirectory().toRealPath();
        Path parent = run.getParent();
        require(server.isDedicated(), "trial requires a dedicated server");
        require(!server.isOnlineMode(), "trial requires the offline disposable server");
        require("127.0.0.1".equals(server.getServerIp()) && server.getServerPort() == 25575,
                "trial requires loopback 127.0.0.1:25575");
        require(parent != null && parent.getFileName() != null && "test-runs".equals(parent.getFileName().toString())
                        && run.getFileName() != null && RUN_NAME.matcher(run.getFileName().toString()).matches(),
                "trial server root must be a timestamped direct child of test-runs");
    }

    private static String detail(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class TrialZombie extends ZombieEntity {
        private TrialZombie(World world) {
            super(world);
        }

        @Override
        public boolean shouldDropExperience() {
            return true;
        }

        @Override
        protected int getExperienceToDrop(ServerWorld world) {
            return 32;
        }
    }

}
