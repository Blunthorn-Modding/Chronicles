package io.github.chronicle.quietbelow.ecology;

import com.mojang.brigadier.CommandDispatcher;
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SculkShriekerBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.SculkShriekerBlockEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Read-only assertions for the opt-in, disposable vanilla Warden rehearsal. */
public final class EcologyWardenTrial {
    private static final String ENABLED = System.getenv("CHRONICLE_WARDEN_NATIVE_TRIAL");
    private static final Pattern RUN_NAME = Pattern.compile("[0-9]{8}T[0-9]{12,}Z");
    private static final BlockPos HEART = new BlockPos(0, 50, -2);
    private static final BlockPos ROOT_SHRIEKER = new BlockPos(0, 51, 0);
    private static final BlockPos ROOT_SENSOR = new BlockPos(0, 51, 7);
    private static final BlockPos DISCONNECTED_SHRIEKER = new BlockPos(18, 51, 18);

    private EcologyWardenTrial() {
    }

    public static void register() {
        if (!"1".equals(ENABLED)) {
            return;
        }
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> commands(dispatcher));
    }

    private static void commands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("quiet_below_warden_trial")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("guard").executes(context -> checked(context.getSource(), source ->
                        "strict offline loopback test-runs guard accepted")))
                .then(literal("fixture").executes(context -> checked(context.getSource(), EcologyWardenTrial::fixture)))
                .then(literal("inert").executes(context -> checked(context.getSource(), EcologyWardenTrial::inert)))
                .then(literal("disconnected").executes(context -> checked(context.getSource(), EcologyWardenTrial::disconnected)))
                .then(literal("warning")
                        .then(argument("level", integer(1, 4)).executes(context -> checked(
                                context.getSource(), source -> warning(source, getInteger(context, "level"))))))
                .then(literal("emerging").executes(context -> checked(context.getSource(), EcologyWardenTrial::emerging)))
                .then(literal("digging").executes(context -> checked(context.getSource(), EcologyWardenTrial::digging)))
                .then(literal("gone").executes(context -> checked(context.getSource(), EcologyWardenTrial::gone))));
    }

    private static int checked(ServerCommandSource source, Assertion assertion) {
        try {
            requireFixture(source.getServer());
            require(source.getPlayer() != null, "trial command requires a player source");
            String result = assertion.check(source);
            source.sendFeedback(() -> Text.literal("PASS vanilla Warden: " + result), false);
            return 1;
        } catch (Exception exception) {
            source.sendError(Text.literal("FAIL vanilla Warden trial: " + detail(exception)));
            return 0;
        }
    }

    private static String fixture(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        EcologyEngine engine = engine(source);
        require(world.getDifficulty() != Difficulty.PEACEFUL, "fixture difficulty must allow Warden warnings");
        require(!world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING), "ambient mob spawning must be disabled");
        require(world.getGameRules().getBoolean(GameRules.DO_WARDEN_SPAWNING), "doWardenSpawning must be true");
        require(world.getBlockState(HEART).isOf(Blocks.REINFORCED_DEEPSLATE), "fixture Heart is missing");
        requireShrieker(world, ROOT_SHRIEKER, true);
        requireShrieker(world, DISCONNECTED_SHRIEKER, true);
        require(world.getBlockState(ROOT_SENSOR).isOf(Blocks.SCULK_SENSOR), "fixture source sensor is missing");
        require(engine.owns(QuietBelowEcology.pos(ROOT_SHRIEKER)), "root shrieker is not ecology-owned");
        require(engine.rooted(QuietBelowEcology.pos(ROOT_SHRIEKER)), "root shrieker is not physically rooted");
        require(engine.owns(QuietBelowEcology.pos(ROOT_SENSOR)), "source sensor is not ecology-owned");
        require(engine.rooted(QuietBelowEcology.pos(ROOT_SENSOR)), "source sensor is not physically rooted");
        require(engine.owns(QuietBelowEcology.pos(DISCONNECTED_SHRIEKER)), "disconnected shrieker is not ecology-owned");
        require(!engine.rooted(QuietBelowEcology.pos(DISCONNECTED_SHRIEKER)),
                "disconnected shrieker unexpectedly has a living Heart connection");
        require(warningLevel(player(source)) == 0, "fresh fixture warning level is not zero");
        require(wardens(world).isEmpty(), "fresh fixture already contains a Warden");
        return "fixture is hard, summoning-enabled, owned, rooted only at the source, and warning-free";
    }

    private static String inert(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        require(warningLevel(player(source)) == 0, "suppressed vibration changed the warning level");
        requireQuiet(world, ROOT_SHRIEKER, "root shrieker");
        requireQuiet(world, DISCONNECTED_SHRIEKER, "disconnected shrieker");
        require(wardens(world).isEmpty(), "suppressed vibration created a Warden");
        return "suppressed shrieker intake/queued response left warning 0 and no Warden";
    }

    private static String disconnected(ServerCommandSource source) {
        require(QuietBelowEcology.playing(source.getServer()),
                "disconnected queued-response assertion requires occupied ACTIVE play");
        require(!engine(source).rooted(QuietBelowEcology.pos(ROOT_SHRIEKER)),
                "source shrieker still has a physical Heart connection");
        inert(source);
        return "the physically disconnected queued response left warning 0 and no Warden";
    }

    private static String warning(ServerCommandSource source, int expected) {
        ServerWorld world = source.getWorld();
        require(QuietBelowEcology.playing(source.getServer()), "warning assertion requires occupied ACTIVE play");
        require(engine(source).rooted(QuietBelowEcology.pos(ROOT_SHRIEKER)), "warning shrieker lost its Heart connection");
        require(warningLevel(player(source)) == expected,
                "expected warning " + expected + " but found " + warningLevel(player(source)));
        BlockState state = world.getBlockState(ROOT_SHRIEKER);
        require(state.isOf(Blocks.SCULK_SHRIEKER) && state.get(SculkShriekerBlock.SHRIEKING),
                "warning did not come through the vanilla shrieking state");
        require(wardens(world).isEmpty(), "Warden appeared before the fourth delayed warning completed");
        return "warning level " + expected + " reached through the vanilla shrieker and cooldown path";
    }

    private static String emerging(ServerCommandSource source) {
        WardenEntity warden = onlyWarden(source.getWorld());
        require(warningLevel(player(source)) == 4, "Warden emerged without warning level 4");
        require(warden.getPose() == EntityPose.EMERGING, "expected EMERGING pose but found " + warden.getPose());
        require(warden.getBrain().hasMemoryModule(MemoryModuleType.DIG_COOLDOWN),
                "triggered Warden is missing the vanilla dig cooldown");
        require(!warden.canImmediatelyDespawn(Double.MAX_VALUE), "Warden unexpectedly permits immediate distance despawn");
        return "one triggered Warden is EMERGING with its vanilla dig cooldown and no immediate despawn";
    }

    private static String digging(ServerCommandSource source) {
        WardenEntity warden = onlyWarden(source.getWorld());
        require(warden.getPose() == EntityPose.DIGGING, "expected DIGGING pose but found " + warden.getPose());
        require(warden.getTarget() == null, "quiet creative fixture left the Warden with an attack target");
        require(!warden.canImmediatelyDespawn(Double.MAX_VALUE), "digging was preceded by immediate distance despawn");
        return "the quiet untargeted Warden entered its vanilla DIGGING pose";
    }

    private static String gone(ServerCommandSource source) {
        require(wardens(source.getWorld()).isEmpty(), "Warden still exists after its vanilla digging animation");
        require(warningLevel(player(source)) == 4, "trial changed the player's vanilla warning level");
        return "the digging Warden removed itself without a kill or direct removal";
    }

    private static EcologyEngine engine(ServerCommandSource source) {
        EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
        require(engine.setup() != null, "ecology is not configured");
        require(engine.enabled(), "ecology must be enabled for the owned-shrieker trial");
        require(engine.setup().dimension().equals(source.getWorld().getRegistryKey().getValue().toString()),
                "command source is not in the ecology dimension");
        return engine;
    }

    private static ServerPlayerEntity player(ServerCommandSource source) {
        return source.getPlayer();
    }

    private static int warningLevel(ServerPlayerEntity player) {
        return player.getSculkShriekerWarningManager()
                .orElseThrow(() -> new IllegalStateException("player has no vanilla shrieker warning manager"))
                .getWarningLevel();
    }

    private static SculkShriekerBlockEntity requireShrieker(ServerWorld world, BlockPos pos, boolean canSummon) {
        BlockState state = world.getBlockState(pos);
        require(state.isOf(Blocks.SCULK_SHRIEKER), "fixture shrieker is missing at " + pos.toShortString());
        require(state.get(SculkShriekerBlock.CAN_SUMMON) == canSummon,
                "fixture shrieker CAN_SUMMON mismatch at " + pos.toShortString());
        return world.getBlockEntity(pos, BlockEntityType.SCULK_SHRIEKER)
                .orElseThrow(() -> new IllegalStateException("fixture shrieker block entity is missing at " + pos.toShortString()));
    }

    private static void requireQuiet(ServerWorld world, BlockPos pos, String label) {
        SculkShriekerBlockEntity shrieker = requireShrieker(world, pos, true);
        require(!world.getBlockState(pos).get(SculkShriekerBlock.SHRIEKING), label + " entered shrieking state");
        require(shrieker.getVibrationListenerData().getVibration() == null, label + " retained a queued vibration");
    }

    private static java.util.List<? extends WardenEntity> wardens(ServerWorld world) {
        return world.getEntitiesByType(EntityType.WARDEN, entity -> true);
    }

    private static WardenEntity onlyWarden(ServerWorld world) {
        var wardens = wardens(world);
        require(wardens.size() == 1, "expected exactly one Warden but found " + wardens.size());
        return wardens.getFirst();
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

    @FunctionalInterface
    private interface Assertion {
        String check(ServerCommandSource source) throws Exception;
    }
}
