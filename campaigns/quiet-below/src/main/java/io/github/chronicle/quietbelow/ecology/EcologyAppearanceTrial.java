package io.github.chronicle.quietbelow.ecology;

import com.mojang.brigadier.CommandDispatcher;
import io.github.chronicle.quietbelow.content.SculkAppearance;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MultifaceBlock;
import net.minecraft.block.SculkCatalystBlock;
import net.minecraft.block.SculkSensorBlock;
import net.minecraft.block.SculkShriekerBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import static net.minecraft.server.command.CommandManager.literal;

/** Opt-in native and legacy-world assertions for the cosmetic sculk vitality property. */
public final class EcologyAppearanceTrial {
    private static final String ENABLED = System.getenv("CHRONICLE_ECOLOGY_NATIVE_TRIAL");
    private static final Pattern RUN_NAME = Pattern.compile("[0-9]{8}T[0-9]{12,}Z");

    private static final BlockPos NATIVE_SCULK = new BlockPos(0, 60, 12);
    private static final BlockPos NATIVE_VEIN = new BlockPos(1, 60, 12);
    private static final BlockPos NATIVE_SENSOR = new BlockPos(2, 60, 12);
    private static final BlockPos NATIVE_CATALYST = new BlockPos(3, 60, 12);
    private static final BlockPos NATIVE_SHRIEKER = new BlockPos(4, 60, 12);
    private static final BlockPos NATIVE_UNRELATED = new BlockPos(5, 60, 12);

    private static final EcologyTerrain.Pos LEGACY_END = new EcologyTerrain.Pos(4, 64, 0);
    private static final EcologyTerrain.Pos LEGACY_SENSOR = new EcologyTerrain.Pos(4, 65, 0);
    private static final List<EcologyTerrain.Pos> LEGACY_CORE = List.of(
            new EcologyTerrain.Pos(1, 64, 0), new EcologyTerrain.Pos(2, 64, 0),
            new EcologyTerrain.Pos(3, 64, 0), LEGACY_END, LEGACY_SENSOR);

    private EcologyAppearanceTrial() { }

    public static void register() {
        if (ENABLED == null || ENABLED.isBlank()) {
            return;
        }
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> commands(dispatcher));
    }

    private static void commands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("quiet_below_appearance_trial")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("native").executes(context -> execute(context.getSource(), "native")))
                .then(literal("legacy").executes(context -> execute(context.getSource(), "legacy")))
                .then(literal("legacy_live").executes(context -> execute(context.getSource(), "legacy live")))
                .then(literal("healthy").executes(context -> execute(context.getSource(), "healthy"))));
    }

    private static int execute(ServerCommandSource source, String branch) {
        try {
            requireFixture(source.getServer());
            require(source.getPlayer() != null, "trial command requires a player source");
            String detail = switch (branch) {
                case "native" -> nativeStates(source.getWorld());
                case "legacy" -> legacy(source, false);
                case "legacy live" -> legacy(source, true);
                case "healthy" -> healthy(source);
                default -> throw new IllegalArgumentException("unknown appearance trial branch");
            };
            source.sendFeedback(() -> Text.literal("PASS sculk appearance " + branch + ": " + detail), false);
            return 1;
        } catch (Exception exception) {
            source.sendError(Text.literal("FAIL sculk appearance trial: " + detail(exception)));
            return 0;
        }
    }

    private static String nativeStates(ServerWorld world) {
        var adapter = new QuietBelowEcology.Adapter(world);
        for (BlockPos pos : List.of(NATIVE_SCULK, NATIVE_VEIN, NATIVE_SENSOR, NATIVE_CATALYST,
                NATIVE_SHRIEKER, NATIVE_UNRELATED, NATIVE_VEIN.offset(Direction.NORTH))) {
            require(adapter.loaded(QuietBelowEcology.pos(pos)), "native appearance fixture position is not loaded: " + pos.toShortString());
        }

        List<Block> nativeBlocks = List.of(Blocks.SCULK, Blocks.SCULK_VEIN, Blocks.SCULK_SENSOR,
                Blocks.SCULK_CATALYST, Blocks.SCULK_SHRIEKER);
        for (Block block : nativeBlocks) {
            BlockState state = block.getDefaultState();
            require(state.contains(SculkAppearance.VITALITY), "native sculk block lacks the vitality property: " + block);
            require(state.get(SculkAppearance.VITALITY) == EcologyAppearance.HEALTHY,
                    "native sculk block does not default to healthy appearance: " + block);
        }

        for (int x = 0; x <= 5; x++) {
            put(world, new BlockPos(x, 59, 12), Blocks.DEEPSLATE.getDefaultState());
        }
        put(world, NATIVE_VEIN.offset(Direction.NORTH), Blocks.DEEPSLATE.getDefaultState());
        put(world, NATIVE_SCULK, Blocks.SCULK.getDefaultState());
        put(world, NATIVE_VEIN, Blocks.SCULK_VEIN.getDefaultState()
                .with(MultifaceBlock.getProperty(Direction.DOWN), true)
                .with(MultifaceBlock.getProperty(Direction.NORTH), true)
                .with(Properties.WATERLOGGED, true));
        put(world, NATIVE_SENSOR, Blocks.SCULK_SENSOR.getDefaultState()
                .with(SculkSensorBlock.POWER, 7)
                .with(SculkSensorBlock.SCULK_SENSOR_PHASE, net.minecraft.block.enums.SculkSensorPhase.COOLDOWN));
        put(world, NATIVE_CATALYST, Blocks.SCULK_CATALYST.getDefaultState()
                .with(SculkCatalystBlock.BLOOM, true));
        put(world, NATIVE_SHRIEKER, Blocks.SCULK_SHRIEKER.getDefaultState()
                .with(SculkShriekerBlock.CAN_SUMMON, true)
                .with(SculkShriekerBlock.SHRIEKING, true));
        put(world, NATIVE_UNRELATED, Blocks.STONE.getDefaultState());

        require(world.getBlockEntity(NATIVE_SCULK) == null && world.getBlockEntity(NATIVE_VEIN) == null,
                "plain sculk or vein unexpectedly created a block entity");
        BlockEntity sensor = requireEntity(world, NATIVE_SENSOR, BlockEntityType.SCULK_SENSOR);
        BlockEntity catalyst = requireEntity(world, NATIVE_CATALYST, BlockEntityType.SCULK_CATALYST);
        BlockEntity shrieker = requireEntity(world, NATIVE_SHRIEKER, BlockEntityType.SCULK_SHRIEKER);

        for (BlockPos pos : List.of(NATIVE_SCULK, NATIVE_VEIN, NATIVE_SENSOR, NATIVE_CATALYST, NATIVE_SHRIEKER)) {
            BlockState before = world.getBlockState(pos);
            EcologyTerrain.State logical = QuietBelowEcology.encode(before);
            require(!logical.properties().containsKey(EcologyAppearance.PROPERTY),
                    "logical encoding retained the cosmetic vitality property at " + pos.toShortString());
            BlockState decodedLegacy = QuietBelowEcology.decode(logical);
            require(decodedLegacy.contains(SculkAppearance.VITALITY)
                            && decodedLegacy.get(SculkAppearance.VITALITY) == EcologyAppearance.HEALTHY,
                    "legacy logical state did not decode with healthy appearance at " + pos.toShortString());
            require(QuietBelowEcology.encode(decodedLegacy).equals(logical),
                    "legacy logical state lost native properties during decode at " + pos.toShortString());
            require(adapter.appearance(QuietBelowEcology.pos(pos), logical, EcologyAppearance.EXHAUSTED),
                    "appearance adapter did not recolor the expected native state at " + pos.toShortString());
            BlockState after = world.getBlockState(pos);
            require(after.getBlock() == before.getBlock()
                            && after.get(SculkAppearance.VITALITY) == EcologyAppearance.EXHAUSTED,
                    "appearance update replaced the native block or wrong stage at " + pos.toShortString());
            require(QuietBelowEcology.encode(after).equals(logical),
                    "appearance update changed a native non-visual property at " + pos.toShortString());
        }

        require(world.getBlockEntity(NATIVE_SENSOR) == sensor
                        && world.getBlockEntity(NATIVE_CATALYST) == catalyst
                        && world.getBlockEntity(NATIVE_SHRIEKER) == shrieker,
                "appearance update replaced a native sculk block-entity object");
        require(world.getBlockEntity(NATIVE_SCULK) == null && world.getBlockEntity(NATIVE_VEIN) == null,
                "appearance update created a block entity for plain sculk or vein");

        BlockState vein = world.getBlockState(NATIVE_VEIN);
        require(MultifaceBlock.collectDirections(vein).equals(Set.of(Direction.DOWN, Direction.NORTH)),
                "two-face vein geometry changed during appearance update");
        require(vein.get(Properties.WATERLOGGED) && vein.getFluidState().isOf(Fluids.WATER),
                "waterlogged vein lost its still-water fluid state");

        BlockState unrelated = world.getBlockState(NATIVE_UNRELATED);
        require(!adapter.appearance(QuietBelowEcology.pos(NATIVE_UNRELATED), QuietBelowEcology.encode(unrelated),
                        EcologyAppearance.EXHAUSTED),
                "unrelated block accepted sculk appearance projection");
        require(world.getBlockState(NATIVE_UNRELATED).equals(unrelated)
                        && !unrelated.contains(SculkAppearance.VITALITY),
                "unrelated block changed during sculk appearance projection");
        return "five native states retained properties, fluids, and block-entity identities; legacy decode and unrelated skip verified.";
    }

    private static String legacy(ServerCommandSource source, boolean live) {
        EcologyEngine engine = legacyEngine(source);
        ServerWorld world = source.getWorld();
        require(QuietBelowEcology.playing(source.getServer()) == live,
                live ? "legacy live trial requires occupied ACTIVE play" : "legacy trial requires paused play");
        require(engine.vitality().rules().equals(new EcologyVitality.Rules(1, 1, 0)),
                "legacy vitality tuning was not retained as 1/1/preset 0");
        require(engine.vitality().ticks() > 0, "legacy durable vitality clock was not retained");
        int retained = engine.vitality().value(LEGACY_END);
        require(retained > 0 && retained < EcologyVitality.FULL,
                "legacy branch vitality did not retain its non-default recovered value");
        require(engine.vitality().mode(LEGACY_END) != null && engine.vitality().mode(LEGACY_SENSOR) != null,
                "legacy branch feeding modes were not retained");
        require(engine.cells().stream().noneMatch(cell ->
                        cell.after().properties().containsKey(EcologyAppearance.PROPERTY)
                                || cell.before().properties().containsKey(EcologyAppearance.PROPERTY)),
                "legacy logical cells unexpectedly contain the cosmetic property");
        assertLegacyBaselines(engine);
        assertLegacyGeometry(world, engine, !live);

        if (!live) {
            return "dev.18 setup, tuning, durable vitality, logical baselines, and property-0 native geometry retained.";
        }
        require(engine.rooted(LEGACY_END), "legacy branch end is not physically rooted");
        require(!engine.view().unresolved(), "legacy topology is still unresolved");
        assertProjectedStage(world, engine, LEGACY_END);
        assertProjectedStage(world, engine, LEGACY_SENSOR);
        requireEntity(world, QuietBelowEcology.blockPos(LEGACY_SENSOR), BlockEntityType.SCULK_SENSOR);
        return "saved vitality/model stages match native states after physical recertification; ownership and sensor retained.";
    }

    private static String healthy(ServerCommandSource source) {
        EcologyEngine engine = legacyEngine(source);
        ServerWorld world = source.getWorld();
        require(QuietBelowEcology.playing(source.getServer()), "healthy trial requires occupied ACTIVE play");
        for (EcologyTerrain.Pos pos : List.of(LEGACY_END, LEGACY_SENSOR)) {
            require(engine.owns(pos), "healthy fixture lost ownership at " + pos);
            require(engine.living(pos), "healthy fixture cell is not living at " + pos);
            require(engine.vitality().value(pos) >= EcologyVitality.FULL * 3 / 4,
                    "healthy fixture cell has not reached 75 percent vitality at " + pos);
            int expected = EcologyAppearance.stage(engine.vitality().value(pos), engine.rooted(pos),
                    engine.vitality().mode(pos));
            require(expected == EcologyAppearance.HEALTHY,
                    "healthy fixture model did not select healthy appearance at " + pos);
            BlockState actual = world.getBlockState(QuietBelowEcology.blockPos(pos));
            require(actual.contains(SculkAppearance.VITALITY)
                            && actual.get(SculkAppearance.VITALITY) == EcologyAppearance.HEALTHY,
                    "healthy native state was not projected to visual stage 0 at " + pos);
        }
        requireEntity(world, QuietBelowEcology.blockPos(LEGACY_SENSOR), BlockEntityType.SCULK_SENSOR);
        return "owned branch end and sensor reached real 75-percent living vitality and native visual stage 0.";
    }

    private static EcologyEngine legacyEngine(ServerCommandSource source) {
        EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
        require(engine.setup() != null && "vitality_source".equals(engine.setup().city()),
                "wrong legacy vitality fixture");
        require(engine.view().indexed(), "legacy vitality fixture is not indexed");
        require(engine.setup().dimension().equals(source.getWorld().getRegistryKey().getValue().toString()),
                "command source is not in the legacy ecology dimension");
        for (EcologyTerrain.Pos pos : LEGACY_CORE) {
            require(engine.owns(pos), "legacy fixture lost ownership at " + pos);
        }
        return engine;
    }

    private static void assertLegacyBaselines(EcologyEngine engine) {
        require(engine.cells().stream().allMatch(cell -> cell.before().equals(EcologyTerrain.AIR)),
                "legacy fixture changed a recorded air cleanup baseline");
        for (EcologyTerrain.Pos pos : LEGACY_CORE) {
            EcologyEngine.Cell cell = engine.cells().stream()
                    .filter(candidate -> candidate.position().equals(pos))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("legacy fixture lost its recorded cell at " + pos));
            String expected = pos.equals(LEGACY_SENSOR) ? "minecraft:sculk_sensor" : "minecraft:sculk";
            boolean expectedAdopted = pos.x() != 1;
            require(cell.adopted() == expectedAdopted,
                    "legacy source cell changed its adopted/reconnected provenance at " + pos);
            require(cell.before().equals(EcologyTerrain.AIR), "legacy source cleanup baseline changed at " + pos);
            require(expected.equals(cell.after().id()), "legacy source recorded the wrong native block at " + pos);
        }
    }

    private static void assertLegacyGeometry(ServerWorld world, EcologyEngine engine, boolean expectDefaultAppearance) {
        for (EcologyEngine.Cell cell : engine.cells()) {
            EcologyTerrain.Pos pos = cell.position();
            BlockPos nativePos = QuietBelowEcology.blockPos(pos);
            if (!world.getChunkManager().isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                continue;
            }
            BlockState actual = world.getBlockState(nativePos);
            require(actual.contains(SculkAppearance.VITALITY),
                    "loaded legacy cell lacks the native visual property at " + pos);
            if (expectDefaultAppearance) {
                require(actual.get(SculkAppearance.VITALITY) == EcologyAppearance.HEALTHY,
                        "loaded legacy cell did not default to visual property 0 at " + pos);
            }
            require(QuietBelowEcology.encode(actual).sameOwnedShape(cell.after()),
                    "loaded legacy cell lost recorded native geometry at " + pos);
        }
        require(world.getBlockState(QuietBelowEcology.blockPos(LEGACY_END)).isOf(Blocks.SCULK),
                "legacy branch end block is missing");
        require(world.getBlockState(QuietBelowEcology.blockPos(LEGACY_SENSOR)).isOf(Blocks.SCULK_SENSOR),
                "legacy sensor block is missing");
    }

    private static void assertProjectedStage(ServerWorld world, EcologyEngine engine, EcologyTerrain.Pos pos) {
        int expected = EcologyAppearance.stage(engine.vitality().value(pos), engine.rooted(pos),
                engine.vitality().mode(pos));
        require(expected >= 0, "appearance model has unknown evidence at " + pos);
        BlockState actual = world.getBlockState(QuietBelowEcology.blockPos(pos));
        require(actual.contains(SculkAppearance.VITALITY)
                        && actual.get(SculkAppearance.VITALITY) == expected,
                "native appearance does not match saved health/mode model at " + pos);
    }

    private static void put(ServerWorld world, BlockPos pos, BlockState state) {
        boolean changed = world.setBlockState(pos, state, Block.NOTIFY_ALL);
        require(changed || world.getBlockState(pos).equals(state), "could not prepare native appearance fixture at " + pos.toShortString());
    }

    private static BlockEntity requireEntity(ServerWorld world, BlockPos pos, BlockEntityType<?> type) {
        BlockEntity entity = world.getBlockEntity(pos);
        require(entity != null && entity.getType() == type, "native block entity is missing or wrong at " + pos.toShortString());
        return entity;
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
}
