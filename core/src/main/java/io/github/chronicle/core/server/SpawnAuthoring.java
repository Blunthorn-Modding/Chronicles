package io.github.chronicle.core.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.spawn.SpawnDestinationValidator;
import io.github.chronicle.core.spawn.SpawnService;
import io.github.chronicle.core.spawn.SpawnStore;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.block.Blocks;

import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Reusable, manual arrival/respawn binding tools. Bindings never alter vanilla respawn state. */
public final class SpawnAuthoring {
    private static final Map<MinecraftServer, Runtime> WORLDS = new IdentityHashMap<>();
    private static boolean registered;
    private SpawnAuthoring() { }

    /** Called once by Core bootstrap; installs commands and independent per-world persistence. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> commands(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(SpawnAuthoring::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(SpawnAuthoring::stop);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> disconnected(handler.player));
    }
    private static void start(MinecraftServer server) {
        var file = server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/spawns.json");
        try { WORLDS.put(server, new Runtime(new SpawnService(new SpawnStore(file)))); ChronicleCore.LOGGER.info("Chronicle spawn authoring ready: {}", file); }
        catch (IOException | IllegalArgumentException exception) { WORLDS.remove(server); ChronicleCore.LOGGER.error("Spawn authoring unavailable; existing data was not replaced. Restore {} while stopped.", file, exception); }
    }
    private static void stop(MinecraftServer server) { WORLDS.remove(server); }
    private static void disconnected(ServerPlayerEntity player) {
        var runtime = WORLDS.get(player.getEntityWorld().getServer());
        if (runtime != null) runtime.previews.entrySet().removeIf(entry -> entry.getKey().actor.equals(player.getUuid()) || entry.getKey().target.equals(player.getUuid()));
    }
    public static SpawnService service(MinecraftServer server) {
        if (!server.isOnThread()) throw new IllegalStateException("Spawn authoring requires the server thread.");
        var runtime = WORLDS.get(server); if (runtime == null) throw new IllegalStateException("Spawn authoring unavailable; inspect chronicle/spawns.json while stopped."); return runtime.service;
    }

    /** Resolves a policy's exact pinned binding without silently following a later rebind. */
    static AuthoringSite.Location safePolicyDestination(MinecraftServer server, io.github.chronicle.core.spawn.SpawnBinding pinned) {
        if (!pinned.equals(service(server).state().bindings().get(pinned.purpose()))) throw new IllegalStateException("Spawn policy is stale; assign the current purpose again.");
        var location = service(server).resolve(AuthoringRuntime.get(server).service().state(), pinned.purpose());
        var verdict = SpawnDestinationValidator.validate(new MinecraftWorld(server), location);
        if (!verdict.safe()) throw new IllegalStateException(verdict.reason());
        return location;
    }

    static void moveToPolicy(ServerPlayerEntity player, AuthoringSite.Location destination) {
        var world = world(player.getEntityWorld().getServer(), destination.dimension());
        if (world == null || !player.teleport(world, destination.x(), destination.y(), destination.z(), Set.<PositionFlag>of(), destination.yaw(), destination.pitch(), false))
            throw new IllegalStateException("Minecraft rejected the automatic spawn move.");
    }

    private static void commands(CommandDispatcher<ServerCommandSource> dispatcher) {
        var spawn = literal("spawn").requires(source -> source.hasPermissionLevel(2))
                .executes(context -> AuthoringUi.open(context.getSource(), io.github.chronicle.core.authoring.ui.AuthoringUiModel.Tab.SPAWNS));
        for (boolean replace : new boolean[]{false, true}) {
            String verb = replace ? "rebind" : "bind";
            final boolean replacement = replace;
            var location = argument("location", StringArgumentType.string())
                    .suggests((context, builder) -> locations(context.getSource(), builder))
                    .executes(context -> edit(context.getSource(), () -> {
                        String purpose = StringArgumentType.getString(context, "purpose");
                        String locationId = StringArgumentType.getString(context, "location");
                        var binding = service(context.getSource().getServer()).bind(AuthoringRuntime.get(context.getSource().getServer()).service().state(), purpose, locationId, replacement);
                        return "Bound " + binding.purpose() + " to location " + binding.locationId() + " revision " + binding.locationRevision() + ". No player was moved.";
                    }));
            var purpose = argument("purpose", StringArgumentType.string()).suggests((context, builder) -> purposeChoices(context.getSource(), builder, replacement)).then(location);
            spawn.then(literal(verb).then(purpose));
        }
        spawn.then(literal("remove").then(argument("purpose", StringArgumentType.string()).suggests((c, b) -> purposes(c.getSource(), b))
                .executes(context -> edit(context.getSource(), () -> { String purpose = StringArgumentType.getString(context, "purpose"); service(context.getSource().getServer()).remove(purpose); return "Removed spawn purpose " + purpose + "."; }))));
        spawn.then(literal("list").executes(context -> read(context.getSource(), () -> {
            var state = service(context.getSource().getServer()).state(); return "Spawn revision=" + state.revision() + "; purposes=" + String.join(", ", state.bindings().keySet());
        })));
        spawn.then(literal("inspect").then(argument("purpose", StringArgumentType.string()).suggests((c, b) -> purposes(c.getSource(), b))
                .executes(context -> read(context.getSource(), () -> inspect(context.getSource(), StringArgumentType.getString(context, "purpose"))))));
        var target = argument("player", EntityArgumentType.player()).executes(context -> {
            return read(context.getSource(), () -> preview(context.getSource(), StringArgumentType.getString(context, "purpose"), EntityArgumentType.getPlayer(context, "player")));
        });
        var previewPurpose = argument("purpose", StringArgumentType.string()).suggests((c, b) -> purposes(c.getSource(), b)).then(target);
        spawn.then(literal("preview").then(previewPurpose));
        spawn.then(literal("confirm").then(argument("player", EntityArgumentType.player()).executes(context -> execute(context.getSource(), EntityArgumentType.getPlayer(context, "player")))));
        dispatcher.register(literal("chronicle").then(spawn));
    }
    private static CompletableFuture<Suggestions> locations(ServerCommandSource source, SuggestionsBuilder builder) {
        try { return suggestValues(AuthoringRuntime.get(source.getServer()).service().state().sites().values().stream().filter(AuthoringSite.Location.class::isInstance).map(AuthoringSite::id).toList(), builder); }
        catch (IllegalStateException exception) { return builder.buildFuture(); }
    }
    private static CompletableFuture<Suggestions> purposes(ServerCommandSource source, SuggestionsBuilder builder) {
        try { return suggestValues(service(source.getServer()).state().bindings().keySet(), builder); }
        catch (IllegalStateException exception) { return builder.buildFuture(); }
    }
    private static CompletableFuture<Suggestions> purposeChoices(ServerCommandSource source, SuggestionsBuilder builder, boolean replace) {
        if (replace) return purposes(source, builder);
        return suggestValues(java.util.List.of("chronicle_core:arrival", "chronicle_core:respawn"), builder);
    }
    private static CompletableFuture<Suggestions> suggestValues(Iterable<String> values, SuggestionsBuilder builder) {
        String prefix = builder.getRemainingLowerCase();
        for (String value : values) { String escaped = StringArgumentType.escapeIfRequired(value); if (value.startsWith(prefix) || escaped.startsWith(prefix)) builder.suggest(escaped); }
        return builder.buildFuture();
    }
    private static String inspect(ServerCommandSource source, String purpose) {
        var location = service(source.getServer()).resolve(AuthoringRuntime.get(source.getServer()).service().state(), purpose);
        var verdict = SpawnDestinationValidator.validate(new MinecraftWorld(source.getServer()), location);
        return purpose + " -> " + location.id() + " revision " + location.revision() + " at " + location.dimension() + " " + location.x() + " " + location.y() + " " + location.z() + "; safety=" + verdict.reason() + ".";
    }
    private static String preview(ServerCommandSource source, String purpose, ServerPlayerEntity player) {
        requireActiveAndClear(source.getServer());
        ServerPlayerEntity actor = previewActor(source);
        var location = service(source.getServer()).resolve(AuthoringRuntime.get(source.getServer()).service().state(), purpose);
        var verdict = SpawnDestinationValidator.validate(new MinecraftWorld(source.getServer()), location);
        if (!verdict.safe()) throw new IllegalStateException("Cannot preview " + purpose + ": " + verdict.reason() + ".");
        runtime(source.getServer()).previews.put(new PreviewKey(actor.getUuid(), player.getUuid()), new Preview(purpose, location, service(source.getServer()).state().bindings().get(purpose), ChronicleRuntime.service(source.getServer()).state().sessionNumber(), System.nanoTime()));
        return "Previewed " + purpose + " for " + player.getName().getString() + ": " + location.dimension() + " " + location.x() + " " + location.y() + " " + location.z() + " yaw=" + location.yaw() + " pitch=" + location.pitch() + ". Run /chronicle spawn confirm " + player.getName().getString() + " to move that exact online player.";
    }
    private static int execute(ServerCommandSource source, ServerPlayerEntity player) {
        if (!source.hasPermissionLevel(2)) { source.sendError(Text.literal("Spawn authoring requires operator permission level 2.")); return 0; }
        try {
            requireActiveAndClear(source.getServer());
            ServerPlayerEntity actor = previewActor(source);
            var preview = runtime(source.getServer()).previews.remove(new PreviewKey(actor.getUuid(), player.getUuid()));
            if (preview == null) throw new IllegalStateException("Preview this destination for " + player.getName().getString() + " first.");
            if (System.nanoTime() - preview.createdAt > 60_000_000_000L) throw new IllegalStateException("Spawn preview expired after 60 seconds; preview it again.");
            if (ChronicleRuntime.service(source.getServer()).state().sessionNumber() != preview.sessionNumber) throw new IllegalStateException("Chronicle session changed after preview; preview it again.");
            var current = service(source.getServer()).resolve(AuthoringRuntime.get(source.getServer()).service().state(), preview.purpose);
            if (!current.equals(preview.location) || !service(source.getServer()).state().bindings().get(preview.purpose).equals(preview.binding)) throw new IllegalStateException("Spawn binding changed after preview; preview it again.");
            var verdict = SpawnDestinationValidator.validate(new MinecraftWorld(source.getServer()), current);
            if (!verdict.safe()) throw new IllegalStateException("Cannot execute " + preview.purpose + ": " + verdict.reason() + ".");
            ServerWorld world = world(source.getServer(), current.dimension());
            if (world == null) throw new IllegalStateException("destination dimension is unavailable");
            if (!player.teleport(world, current.x(), current.y(), current.z(), Set.<PositionFlag>of(), current.yaw(), current.pitch(), false)) throw new IllegalStateException("Minecraft rejected the teleport; no spawn move was recorded.");
            ChronicleCore.LOGGER.info("Manual spawn move: purpose={} actor={} target={} location={} revision={}", preview.purpose, actor.getUuid(), player.getUuid(), current.id(), current.revision());
            source.sendFeedback(() -> Text.literal("Moved " + player.getName().getString() + " to " + preview.purpose + ". Vanilla respawn was not changed."), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) { source.sendError(Text.literal(exception.getMessage())); return 0; }
    }
    private static Runtime runtime(MinecraftServer server) { var result = WORLDS.get(server); if (result == null) throw new IllegalStateException("Spawn authoring unavailable."); return result; }
    private static void requireActiveAndClear(MinecraftServer server) {
        if (ChronicleRuntime.service(server).state().phase() != io.github.chronicle.core.campaign.SessionPhase.ACTIVE) throw new IllegalStateException("Spawn preview/confirm requires an ACTIVE Chronicle session.");
        String hold = RuntimeHolds.reason(server); if (hold != null) throw new IllegalStateException("Spawn preview/confirm blocked by durable operation hold: " + hold);
    }
    private static void requireEditor(ServerCommandSource source) {
        if (!source.hasPermissionLevel(2)) throw new IllegalStateException("Spawn authoring requires operator permission level 2.");
        if (source.getPlayer() == null || !AuthoringRuntime.isAuthor(source.getPlayer())) throw new IllegalStateException("Enable Core author mode before editing spawn bindings.");
    }
    private static ServerPlayerEntity previewActor(ServerCommandSource source) {
        if (source.getPlayer() == null) throw new IllegalStateException("Spawn preview/confirm requires the operator player who will confirm it.");
        return source.getPlayer();
    }
    private static int edit(ServerCommandSource source, ThrowingOperation operation) { try { requireEditor(source); String message = operation.run(); source.sendFeedback(() -> Text.literal(message), false); return 1; } catch (IOException | com.mojang.brigadier.exceptions.CommandSyntaxException | IllegalArgumentException | IllegalStateException ex) { source.sendError(Text.literal(ex.getMessage())); return 0; } }
    private static int read(ServerCommandSource source, ThrowingOperation operation) { if (!source.hasPermissionLevel(2)) { source.sendError(Text.literal("Spawn authoring requires operator permission level 2.")); return 0; } try { String message = operation.run(); source.sendFeedback(() -> Text.literal(message), false); return 1; } catch (IOException | com.mojang.brigadier.exceptions.CommandSyntaxException | IllegalArgumentException | IllegalStateException ex) { source.sendError(Text.literal(ex.getMessage())); return 0; } }
    @FunctionalInterface private interface ThrowingOperation { String run() throws IOException, com.mojang.brigadier.exceptions.CommandSyntaxException; }
    private record PreviewKey(UUID actor, UUID target) { }
    private record Preview(String purpose, AuthoringSite.Location location, io.github.chronicle.core.spawn.SpawnBinding binding, int sessionNumber, long createdAt) { }
    private static final class Runtime { final SpawnService service; final Map<PreviewKey, Preview> previews = new HashMap<>(); Runtime(SpawnService service) { this.service = service; } }
    private static ServerWorld world(MinecraftServer server, String dimension) { try { return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dimension))); } catch (IllegalArgumentException ignored) { return null; } }
    /** Package-visible adapter so menu actions use the same loaded-world safety checks as spawn commands. */
    static final class MinecraftWorld implements SpawnDestinationValidator.World {
        private final MinecraftServer server; MinecraftWorld(MinecraftServer server) { this.server = server; }
        @Override public boolean hasDimension(String dimension) { return world(server, dimension) != null; }
        @Override public boolean isChunkLoaded(String dimension, int x, int z) { ServerWorld world = world(server, dimension); return world != null && world.getChunkManager().isChunkLoaded(x >> 4, z >> 4); }
        @Override public boolean withinWorldBorder(String dimension, int x, int y, int z) { ServerWorld world = world(server, dimension); return world != null && world.getWorldBorder().contains(new BlockPos(x, y, z)); }
        @Override public boolean withinBuildLimits(String dimension, int x, int y, int z) { ServerWorld world = world(server, dimension); return world != null && world.isInBuildLimit(new BlockPos(x, y, z)); }
        @Override public boolean hasSolidSupport(String dimension, int x, int y, int z) { ServerWorld world = world(server, dimension); BlockPos pos = new BlockPos(x, y, z); return world != null && world.getBlockState(pos).isSolidBlock(world, pos); }
        @Override public boolean hasClearStandingRoom(String dimension, double x, double y, double z) { ServerWorld world = world(server, dimension); return world != null && world.isSpaceEmpty(new Box(x - .3, y, z - .3, x + .3, y + 1.8, z + .3)); }
        @Override public boolean hasHazard(String dimension, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            ServerWorld world = world(server, dimension); if (world == null) return true;
            for (int x = (int) Math.floor(minX); x <= (int) Math.floor(maxX); x++) for (int y = (int) Math.floor(minY); y <= (int) Math.floor(maxY); y++) for (int z = (int) Math.floor(minZ); z <= (int) Math.floor(maxZ); z++) {
                var state = world.getBlockState(new BlockPos(x, y, z));
                if (!state.getFluidState().isEmpty() || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.MAGMA_BLOCK)) return true;
            }
            return false;
        }
    }
}
