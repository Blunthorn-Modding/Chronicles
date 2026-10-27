package io.github.chronicle.quietbelow.stage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.quietbelow.QuietBelow;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.time.Clock;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Fabric lifecycle, commands, and Minecraft world adapter for the bounded stage domain. */
public final class QuietBelowStages {
    private static final Map<MinecraftServer, StageService> SERVICES = new IdentityHashMap<>();
    private static boolean registered;

    private QuietBelowStages() { }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(QuietBelowStages::start);
        ServerTickEvents.END_SERVER_TICK.register(QuietBelowStages::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(SERVICES::remove);
    }

    private static void start(MinecraftServer server) {
        var file = server.getSavePath(WorldSavePath.ROOT).resolve("quiet-below").resolve("stages.json");
        try {
            SERVICES.put(server, new StageService(new StageStore(file), Clock.systemUTC()));
            QuietBelow.LOGGER.info("Quiet Below environmental stages ready: {}", file);
        } catch (IOException | IllegalArgumentException exception) {
            SERVICES.remove(server);
            QuietBelow.LOGGER.error("Quiet Below stage data could not be loaded and was not replaced. Restore {} from a verified backup while stopped.", file, exception);
        }
    }

    private static void tick(MinecraftServer server) {
        StageService service = SERVICES.get(server);
        if (service == null) return;
        try {
            int processed = service.tick(active(server), dimension -> world(server, dimension));
            if (processed > 0 && service.state().operation().status() != StageOperationStatus.APPLYING
                    && service.state().operation().status() != StageOperationStatus.UNDOING) {
                QuietBelow.LOGGER.info("Quiet Below stage operation reached {}: {}",
                        service.state().operation().status(), service.status());
            }
        } catch (IOException exception) {
            QuietBelow.LOGGER.error("Quiet Below changed stage blocks but could not checkpoint progress; automatic work is halted and explicit recovery is required", exception);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            QuietBelow.LOGGER.error("Quiet Below stage tick halted safely: {}", exception.getMessage(), exception);
        }
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        var zone = literal("zone").then(literal("define")
                .then(argument("id", StringArgumentType.word())
                        .then(argument("from", BlockPosArgumentType.blockPos())
                                .then(argument("to", BlockPosArgumentType.blockPos())
                                        .executes(context -> define(context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                BlockPosArgumentType.getLoadedBlockPos(context, "from"),
                                                BlockPosArgumentType.getLoadedBlockPos(context, "to")))))));

        var stage = literal("stage")
                .then(literal("preview").then(argument("zone", StringArgumentType.word())
                        .executes(context -> preview(context.getSource(), StringArgumentType.getString(context, "zone")))))
                .then(literal("apply").then(argument("zone", StringArgumentType.word())
                        .then(argument("token", StringArgumentType.word())
                                .executes(context -> apply(context.getSource(), StringArgumentType.getString(context, "zone"),
                                        StringArgumentType.getString(context, "token"))))))
                .then(literal("status").executes(context -> query(context.getSource(), StageService::status)))
                .then(literal("resume").executes(context -> resume(context.getSource())))
                .then(literal("undo").executes(context -> undo(context.getSource())));

        dispatcher.register(literal("chronicle").then(literal("game").then(literal("quiet-below")
                .requires(source -> source.hasPermissionLevel(2)).then(zone).then(stage))));
    }

    private static int define(ServerCommandSource source, String id, BlockPos first, BlockPos second) {
        return mutate(source, service -> {
            StageZone zone = service.defineZone(actor(source), id, new MinecraftStageWorld(source.getWorld()),
                    position(first), position(second));
            return "Defined stage zone " + zone.id() + " in " + zone.dimension() + " with " + zone.entries().size() + " positions.";
        });
    }

    private static int preview(ServerCommandSource source, String id) {
        try {
            StageService service = service(source.getServer());
            StagePreview preview = service.preview(id, dimension -> world(source.getServer(), dimension));
            for (StageEntry entry : preview.entries()) {
                source.sendFeedback(() -> Text.literal(entry.position().display() + ": " + entry.before().display()
                        + " -> " + entry.after().display()), false);
            }
            source.sendFeedback(() -> Text.literal("Stage preview " + preview.zoneId() + ": positions="
                    + preview.entries().size() + "; token=" + preview.token() + "."), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendError(Text.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int apply(ServerCommandSource source, String id, String token) {
        return mutate(source, service -> {
            StageOperation operation = service.apply(actor(source), id, token, active(source.getServer()),
                    dimension -> world(source.getServer(), dimension));
            return "Stage apply queued: operation=" + operation.operationId() + "; zone=" + operation.zoneId()
                    + "; positions=" + operation.entries().size() + ".";
        });
    }

    private static int undo(ServerCommandSource source) {
        return mutate(source, service -> {
            StageOperation operation = service.undo(actor(source), active(source.getServer()),
                    dimension -> world(source.getServer(), dimension));
            return "Stage undo queued: operation=" + operation.operationId() + "; zone=" + operation.zoneId()
                    + "; positions=" + operation.entries().size() + ".";
        });
    }

    private static int resume(ServerCommandSource source) {
        return mutate(source, service -> {
            StageOperation operation = service.resume(actor(source), active(source.getServer()),
                    dimension -> world(source.getServer(), dimension));
            return "Stage resumed: operation=" + operation.operationId() + "; progress=" + operation.progress()
                    + "/" + operation.entries().size() + ".";
        });
    }

    private static int query(ServerCommandSource source, Query query) {
        try {
            String message = query.run(service(source.getServer()));
            source.sendFeedback(() -> Text.literal(message), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendError(Text.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int mutate(ServerCommandSource source, Mutation mutation) {
        if (!source.hasPermissionLevel(2)) {
            source.sendError(Text.literal("Quiet Below stage administration requires operator permission level 2."));
            return 0;
        }
        try {
            String message = mutation.run(service(source.getServer()));
            source.sendFeedback(() -> Text.literal(message), false);
            QuietBelow.LOGGER.info("Quiet Below stage command by {}: {}", source.getName(), message);
            return 1;
        } catch (IOException exception) {
            QuietBelow.LOGGER.error("Quiet Below stage command could not persist its requested state", exception);
            source.sendError(Text.literal("Stage data could not be saved; the requested command was not started. Check the server log."));
            return 0;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendError(Text.literal(exception.getMessage()));
            return 0;
        }
    }

    private static StageService service(MinecraftServer server) {
        if (!server.isOnThread()) throw new IllegalStateException("stage operations must run on the server thread");
        StageService service = SERVICES.get(server);
        if (service == null) throw new IllegalStateException("Quiet Below stage state is unavailable; inspect the server log and restore stages.json while stopped");
        return service;
    }

    private static boolean active(MinecraftServer server) {
        try {
            return ChronicleRuntime.service(server).state().phase() == SessionPhase.ACTIVE;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private static Actor actor(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        return new Actor(source.getName(), player == null ? null : player.getUuidAsString());
    }

    private static StageWorld world(MinecraftServer server, String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        if (id == null) return null;
        ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
        return world == null ? null : new MinecraftStageWorld(world);
    }

    private static StagePosition position(BlockPos value) {
        return new StagePosition(value.getX(), value.getY(), value.getZ());
    }

    @FunctionalInterface
    private interface Query { String run(StageService service); }
    @FunctionalInterface
    private interface Mutation { String run(StageService service) throws IOException; }

    private static final class MinecraftStageWorld implements StageWorld {
        private final ServerWorld world;

        private MinecraftStageWorld(ServerWorld world) { this.world = world; }

        @Override public String dimension() { return world.getRegistryKey().getValue().toString(); }
        @Override public boolean isLoaded(StagePosition position) {
            return world.getChunkManager().isChunkLoaded(position.x() >> 4, position.z() >> 4);
        }
        @Override public boolean isInBuildLimit(StagePosition position) { return world.isInBuildLimit(blockPos(position)); }
        @Override public boolean isInsideWorldBorder(StagePosition position) { return world.getWorldBorder().contains(blockPos(position)); }
        @Override public boolean hasBlockEntity(StagePosition position) { return world.getBlockEntity(blockPos(position)) != null; }
        @Override public StageBlockState getBlockState(StagePosition position) { return encode(world.getBlockState(blockPos(position))); }
        @Override public boolean setBlockState(StagePosition position, StageBlockState state) {
            return world.setBlockState(blockPos(position), decode(state), Block.NOTIFY_ALL);
        }

        private static BlockPos blockPos(StagePosition value) { return new BlockPos(value.x(), value.y(), value.z()); }

        private static StageBlockState encode(BlockState state) {
            Map<String, String> properties = new TreeMap<>();
            state.getEntries().forEach((property, value) -> properties.put(property.getName(), propertyName(property, value)));
            // Historical stage journals predate the cosmetic vitality property. Only healthy
            // sculk is equivalent to that canonical state; changed bands must still conflict.
            if (state.isOf(net.minecraft.block.Blocks.SCULK)
                    && state.get(io.github.chronicle.quietbelow.content.SculkAppearance.VITALITY)
                    == io.github.chronicle.quietbelow.ecology.EcologyAppearance.HEALTHY) {
                properties.remove(io.github.chronicle.quietbelow.ecology.EcologyAppearance.PROPERTY);
            }
            return new StageBlockState(Registries.BLOCK.getId(state.getBlock()).toString(), properties);
        }

        private static BlockState decode(StageBlockState encoded) {
            Identifier id = Identifier.tryParse(encoded.id());
            Block block = id == null ? null : Registries.BLOCK.getOptionalValue(id).orElse(null);
            if (block == null) throw new IllegalStateException("recorded block is unavailable: " + encoded.id());
            BlockState state = block.getDefaultState();
            for (Map.Entry<String, String> entry : encoded.properties().entrySet()) {
                Property<?> property = state.getProperties().stream().filter(value -> value.getName().equals(entry.getKey()))
                        .findFirst().orElseThrow(() -> new IllegalStateException("recorded property is unavailable: " + entry.getKey()));
                state = withParsed(state, property, entry.getValue());
            }
            if (encoded.equals(StageValidation.SCULK)
                    && state.getProperties().size() == 1
                    && state.contains(io.github.chronicle.quietbelow.content.SculkAppearance.VITALITY)) {
                return state.with(io.github.chronicle.quietbelow.content.SculkAppearance.VITALITY,
                        io.github.chronicle.quietbelow.ecology.EcologyAppearance.HEALTHY);
            }
            if (state.getProperties().size() != encoded.properties().size()) {
                throw new IllegalStateException("recorded state omits properties for " + encoded.id());
            }
            return state;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static String propertyName(Property property, Comparable value) { return property.name(value); }

        private static <T extends Comparable<T>> BlockState withParsed(BlockState state, Property<T> property, String value) {
            var parsed = property.parse(value);
            if (parsed.isEmpty()) {
                throw new IllegalStateException("recorded property value is unavailable: " + property.getName() + "=" + value);
            }
            return state.with(property, parsed.get());
        }
    }
}
