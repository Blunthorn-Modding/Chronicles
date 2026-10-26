package io.github.chronicle.core.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.spawn.AutomaticSpawnService;
import io.github.chronicle.core.spawn.AutomaticSpawnState;
import io.github.chronicle.core.spawn.AutomaticSpawnState.Status;
import io.github.chronicle.core.spawn.AutomaticSpawnStore;
import io.github.chronicle.core.spawn.AutomaticArrivalEvents;
import io.github.chronicle.core.spawn.SpawnBinding;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Explicit per-player opt-in policies; no reconnect loop, bed replacement or encounter spawning. */
public final class AutomaticPlayerSpawns {
    private static final Map<MinecraftServer, Runtime> WORLDS = new IdentityHashMap<>();
    private AutomaticPlayerSpawns() { }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var path = server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/automatic-spawns.json");
            try {
                WORLDS.put(server, new Runtime(new AutomaticSpawnService(new AutomaticSpawnStore(path))));
                ChronicleCore.LOGGER.info("Automatic player spawn policies ready: {}", path);
            }
            catch (IOException | IllegalArgumentException exception) { ChronicleCore.LOGGER.error("Automatic player spawns disabled; existing data preserved at {}", path, exception); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var runtime = WORLDS.get(server); if (runtime != null) runtime.events.joined(handler.player.getUuid());
        });
        io.github.chronicle.core.server.ServerDisconnects.register((handler, server) -> {
            var runtime = WORLDS.get(server); if (runtime != null) runtime.events.disconnected(handler.player.getUuid());
        });
        ServerTickEvents.END_SERVER_TICK.register(AutomaticPlayerSpawns::tick);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> { if (!alive) respawn(player); });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(literal("chronicle").then(literal("leave").executes(context -> {
                var source = context.getSource();
                if (source.getPlayer() == null) { source.sendError(Text.literal("Leave requires your player account.")); return 0; }
                try {
                    // Remove the policy capable of moving a player first. A later membership
                    // save failure must not leave a teleport policy waiting after restart.
                    service(source.getServer()).leave(source.getPlayer().getUuid());
                    PlayRuntime.setSelfParticipant(source.getPlayer(), false);
                    source.sendFeedback(() -> Text.literal("You left participation and automatic arrival and respawn policies. History is retained; you were not moved."), false);
                    return 1;
                } catch (IOException | IllegalStateException exception) { disableAfterWriteFailure(source.getServer(), exception); source.sendError(Text.literal("Could not persist leaving. Automatic spawns are disabled; inspect the server log.")); return 0; }
            })));
            var automatic = literal("automatic").requires(source -> source.hasPermissionLevel(2))
                    .executes(context -> AuthoringUi.open(context.getSource(), io.github.chronicle.core.authoring.ui.AuthoringUiModel.Tab.POLICIES));
            for (boolean arrival : new boolean[]{true, false}) {
                final boolean firstArrival = arrival;
                automatic.then(literal(arrival ? "arrival" : "respawn").then(argument("player", EntityArgumentType.player())
                        .then(argument("purpose", StringArgumentType.string()).executes(context -> edit(context.getSource(), () -> {
                            UUID player = EntityArgumentType.getPlayer(context, "player").getUuid();
                            enable(context.getSource(), player, StringArgumentType.getString(context, "purpose"), firstArrival);
                            return "Automatic " + (firstArrival ? "first arrival" : "death respawn") + " enabled. No player was moved.";
                        })))));
                automatic.then(literal(arrival ? "arrival_off" : "respawn_off").then(argument("player", EntityArgumentType.player())
                        .executes(context -> edit(context.getSource(), () -> {
                            disable(context.getSource(), EntityArgumentType.getPlayer(context, "player").getUuid(), firstArrival);
                            return "Automatic policy disabled. Arrival history retained.";
                        }))));
            }
            automatic.then(literal("resetarrival").then(argument("player", EntityArgumentType.player()).executes(context -> edit(context.getSource(), () -> {
                reset(context.getSource(), EntityArgumentType.getPlayer(context, "player").getUuid());
                return "Arrival claim reset. No player was moved; the next eligible join or ACTIVE transition may attempt arrival.";
            }))));
            dispatcher.register(literal("chronicle").then(literal("spawn").then(automatic)));
        });
    }

    public static AutomaticSpawnService service(MinecraftServer server) {
        if (!server.isOnThread()) throw new IllegalStateException("Automatic spawn policies require the server thread.");
        var runtime = WORLDS.get(server);
        if (runtime == null || runtime.failure != null) throw new IllegalStateException("Automatic spawns unavailable; inspect chronicle/automatic-spawns.json and the server log.");
        return runtime.service;
    }
    public static AutomaticSpawnState available(MinecraftServer server) {
        try { return service(server).state(); } catch (IllegalStateException ignored) { return null; }
    }
    public static void enable(ServerCommandSource source, UUID player, String purpose, boolean arrival) throws IOException {
        requireAuthor(source);
        var binding = SpawnAuthoring.service(source.getServer()).state().bindings().get(purpose);
        if (binding == null) throw new IllegalArgumentException("Choose a saved spawn purpose.");
        SpawnAuthoring.service(source.getServer()).resolve(AuthoringRuntime.get(source.getServer()).service().state(), purpose);
        write(source.getServer(), () -> {service(source.getServer()).set(player, binding, arrival);if(PlayerRoles.ready(source.getServer()))PlayerRoles.setPolicyDisabled(source.getServer(),player,arrival,false);});
    }
    public static void disable(ServerCommandSource source, UUID player, boolean arrival) throws IOException {
        requireAuthor(source); write(source.getServer(), () -> {if(PlayerRoles.ready(source.getServer()))PlayerRoles.setPolicyDisabled(source.getServer(),player,arrival,true);service(source.getServer()).disable(player, arrival);});
    }
    public static void useRoleDefault(ServerCommandSource source,UUID player,boolean arrival) throws IOException {
        requireAuthor(source);
        if(!PlayerRoles.ready(source.getServer())||PlayerRoles.roleFor(source.getServer(),player)==null)throw new IllegalStateException("Assign a role before choosing its destination policy.");
        write(source.getServer(),()->{service(source.getServer()).disable(player,arrival);PlayerRoles.setPolicyDisabled(source.getServer(),player,arrival,false);});
    }
    public static void reset(ServerCommandSource source, UUID player) throws IOException {
        requireAuthor(source); write(source.getServer(), () -> service(source.getServer()).reset(player));
    }
    private static void requireAuthor(ServerCommandSource source) {
        if (!source.hasPermissionLevel(2) || source.getPlayer() == null || !AuthoringRuntime.isAuthor(source.getPlayer()) || !AuthoringRuntime.editable(source.getServer()) || RuntimeHolds.reason(source.getServer())!=null)
            throw new IllegalStateException("Automatic policy edits require operator permission and non-active author mode.");
    }
    private static void write(MinecraftServer server, Write operation) throws IOException {
        try { operation.run(); }
        catch (IOException exception) { disableAfterWriteFailure(server, exception); throw exception; }
    }
    private static void disableAfterWriteFailure(MinecraftServer server, Exception exception) {
        var runtime = WORLDS.get(server); if (runtime != null) runtime.failure = exception.toString();
        ChronicleCore.LOGGER.error("Automatic player spawns disabled after a persistence/service failure; no automatic retry", exception);
    }
    private static boolean active(MinecraftServer server) {
        var campaign = ChronicleRuntime.available(server);
        return campaign != null && campaign.state().phase() == SessionPhase.ACTIVE && RuntimeHolds.reason(server) == null;
    }
    private static boolean eligible(ServerPlayerEntity player) { return player.isAlive() && !player.isSpectator() && !AuthoringRuntime.isAuthor(player); }

    private static void tick(MinecraftServer server) {
        var runtime = WORLDS.get(server); if (runtime == null || runtime.failure != null) return;
        var campaign = ChronicleRuntime.available(server);
        boolean phaseActive = campaign != null && campaign.state().phase() == SessionPhase.ACTIVE;
        var queued = runtime.events.next(phaseActive, RuntimeHolds.reason(server) != null,
                server.getPlayerManager().getPlayerList().stream().map(ServerPlayerEntity::getUuid).toList());
        for (UUID id : queued) {
            if (runtime.failure != null) break;
            var player = server.getPlayerManager().getPlayer(id);
            if (player == null || !eligible(player)) continue;
            if(RoleFirstEntry.managesStart(server,id))continue;
            var policy = runtime.service.state().policies().get(id);
            if (policy == null || policy.arrival() == null || runtime.service.state().arrivals().containsKey(id)) continue;
            try { arrival(player, policy.arrival()); }
            catch (IOException | IllegalArgumentException | IllegalStateException exception) { disableAfterWriteFailure(server, exception); }
        }
    }
    private static void arrival(ServerPlayerEntity player, SpawnBinding binding) throws IOException {
        var server = player.getEntityWorld().getServer();
        var service = service(server);
        if (!service.claim(player.getUuid(), binding.purpose())) return;
        try {
            var destination = SpawnAuthoring.safePolicyDestination(server, binding);
            SpawnAuthoring.moveToPolicy(player, destination);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            service.finish(player.getUuid(), Status.FAILED, detail(exception));
            player.sendMessage(Text.literal("Chronicle arrival skipped: " + detail(exception) + ". Ask the GM to review the policy."), false);
            ChronicleCore.LOGGER.warn("Automatic arrival failed: player={} purpose={} reason={}", player.getUuid(), binding.purpose(), detail(exception));
            return;
        }
        service.finish(player.getUuid(), Status.MOVED, "Automatic first arrival moved the player; no automatic repeat.");
        ChronicleCore.LOGGER.info("Automatic first arrival: player={} purpose={}", player.getUuid(), binding.purpose());
    }
    private static void respawn(ServerPlayerEntity player) {
        var server = player.getEntityWorld().getServer();
        var state = available(server);
        if (state == null || !active(server) || !eligible(player)) return;
        var policy = state.policies().get(player.getUuid());
        var role=PlayerRoles.roleFor(server,player.getUuid());
        boolean manual=policy!=null&&policy.respawn()!=null;
        if(!manual&&(role==null||role.respawn()==null||PlayerRoles.assignments(server).respawnDisabled().contains(player.getUuid())||PlayRuntime.available(server)==null||!PlayRuntime.available(server).participants().contains(player.getUuid())))return;
        try {
            var destination = manual?SpawnAuthoring.safePolicyDestination(server, policy.respawn()):SpawnAuthoring.safeRoleDestination(server,role.respawn());
            SpawnAuthoring.moveToPolicy(player, destination);
            ChronicleCore.LOGGER.info("Automatic death respawn: player={} purpose={}", player.getUuid(), manual?policy.respawn().purpose():role.respawn().purpose());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(Text.literal("Chronicle respawn skipped: " + detail(exception) + ". Vanilla respawn retained."), false);
            ChronicleCore.LOGGER.warn("Automatic death respawn skipped: player={} reason={}", player.getUuid(), detail(exception));
        }
    }
    private static String detail(Exception exception) { String text = exception.getMessage() == null ? "spawn unavailable" : exception.getMessage(); return text.replaceAll("[\\p{Cntrl}]", " ").substring(0, Math.min(256, text.length())); }
    private static int edit(ServerCommandSource source, Operation operation) {
        try { String message = operation.run(); source.sendFeedback(() -> Text.literal(message), false); return 1; }
        catch (IOException | com.mojang.brigadier.exceptions.CommandSyntaxException | IllegalArgumentException | IllegalStateException exception) { source.sendError(Text.literal(exception.getMessage())); return 0; }
    }
    @FunctionalInterface private interface Operation { String run() throws IOException, com.mojang.brigadier.exceptions.CommandSyntaxException; }
    @FunctionalInterface private interface Write { void run() throws IOException; }
    private static final class Runtime {
        final AutomaticSpawnService service; final AutomaticArrivalEvents events = new AutomaticArrivalEvents(); String failure;
        Runtime(AutomaticSpawnService service) { this.service = service; }
    }
}
