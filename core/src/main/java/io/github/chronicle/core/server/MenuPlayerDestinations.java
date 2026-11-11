package io.github.chronicle.core.server;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.spawn.SpawnBinding;
import io.github.chronicle.core.spawn.SpawnDestinationValidator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-only destination actions for the Core menu. Automatic policies receive private,
 * revision-pinned purposes; manual movement remains an explicit short-lived review/confirm flow.
 */
public final class MenuPlayerDestinations {
    private static final long REVIEW_NANOS = 60_000_000_000L;
    private static final Map<MinecraftServer, Runtime> WORLDS = new IdentityHashMap<>();
    private static boolean registered;

    private MenuPlayerDestinations() { }

    /** Installs only transient-context cleanup; Core bootstrap must invoke this once. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
        io.github.chronicle.core.server.ServerDisconnects.register((handler, server) -> {
            Runtime runtime = WORLDS.get(server);
            if (runtime != null) runtime.reviews.entrySet().removeIf(entry -> entry.getKey().equals(handler.player.getUuid())
                    || entry.getValue().target().equals(handler.player.getUuid()));
        });
    }

    /**
     * Assigns an automatic first-entry or death-respawn policy without moving a player now.
     * A fresh private purpose protects an existing policy from a later point rebind or failed save.
     */
    public static void set(ServerCommandSource source, UUID player, String pointId, boolean starting) throws IOException {
        requireAuthor(source);
        if (player == null) throw new IllegalArgumentException("player is required");
        requireKnownTarget(source.getServer(), player);
        AuthoringSite.Location point = point(source.getServer(), pointId);
        String purpose = privatePurpose(player, point.id(), point.revision(), starting);
        SpawnBinding desired = new SpawnBinding(purpose, point.id(), point.revision());
        var spawns = SpawnAuthoring.service(source.getServer());
        SpawnBinding existing = spawns.state().bindings().get(purpose);
        if (existing == null) {
            // This purpose cannot be referenced by another player/kind because the UUID and kind
            // are encoded in it. Do not replace an unexpected existing value.
            spawns.bind(AuthoringRuntime.get(source.getServer()).service().state(), purpose, point.id(), false);
        } else if (!existing.equals(desired)) {
            throw new IllegalStateException("Private destination binding conflicts with an existing policy; no policy was changed.");
        }
        // The existing service persists before publishing and leaves the old policy intact on a
        // failed policy write. The new private binding may remain as harmless unused evidence.
        AutomaticPlayerSpawns.enable(source, player, purpose, starting);
    }

    /** Creates a per-operator, short-lived review for one exact online player and saved point. */
    public static String review(ServerCommandSource source, UUID target, String pointId) {
        ServerPlayerEntity actor = requireActiveOperator(source);
        if (target == null) throw new IllegalArgumentException("player is required");
        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(target);
        if (targetPlayer == null) throw new IllegalArgumentException("That player is no longer online.");
        AuthoringSite.Location point = point(source.getServer(), pointId);
        SpawnDestinationValidator.Verdict verdict = SpawnDestinationValidator.validate(new SpawnAuthoring.MinecraftWorld(source.getServer()), point);
        if (!verdict.safe()) throw new IllegalStateException("Cannot review this destination: " + verdict.reason() + ".");
        Runtime runtime = WORLDS.computeIfAbsent(source.getServer(), ignored -> new Runtime());
        runtime.reviews.put(actor.getUuid(), new Review(target, point.id(), point.revision(), ChronicleRuntime.service(source.getServer()).state().sessionNumber(), System.nanoTime()));
        return "Reviewed " + point.id() + " revision " + point.revision() + " for " + targetPlayer.getName().getString()
                + " at " + point.dimension() + " " + point.x() + " " + point.y() + " " + point.z()
                + ". Confirm Move within 60 seconds; vanilla respawn is unchanged.";
    }

    /** Consumes the actor's review and moves its exact target only after all live checks pass. */
    public static String move(ServerCommandSource source, UUID target) {
        ServerPlayerEntity actor = requireActiveOperator(source);
        if (target == null) throw new IllegalArgumentException("player is required");
        Runtime runtime = WORLDS.get(source.getServer());
        Review review = runtime == null ? null : runtime.reviews.remove(actor.getUuid());
        if (review == null || !review.target().equals(target)) throw new IllegalStateException("Review this destination for that player before moving them.");
        if (System.nanoTime() - review.createdAtNanos() > REVIEW_NANOS) throw new IllegalStateException("Destination review expired after 60 seconds; review it again.");
        if (ChronicleRuntime.service(source.getServer()).state().sessionNumber() != review.sessionNumber()) throw new IllegalStateException("Chronicle session changed after review; review the destination again.");
        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(target);
        if (targetPlayer == null) throw new IllegalStateException("That player is no longer online.");
        AuthoringSite.Location point = point(source.getServer(), review.pointId());
        if (point.revision() != review.pointRevision()) throw new IllegalStateException("Destination point changed after review; review it again.");
        SpawnDestinationValidator.Verdict verdict = SpawnDestinationValidator.validate(new SpawnAuthoring.MinecraftWorld(source.getServer()), point);
        if (!verdict.safe()) throw new IllegalStateException("Cannot move the player: " + verdict.reason() + ".");
        SpawnAuthoring.moveToPolicy(targetPlayer, point);
        return "Moved " + targetPlayer.getName().getString() + " to " + point.id() + ". Vanilla respawn was not changed.";
    }

    private static AuthoringSite.Location point(MinecraftServer server, String pointId) {
        if (pointId == null) throw new IllegalArgumentException("Choose a saved point.");
        var site = AuthoringRuntime.get(server).service().state().sites().get(pointId);
        if (!(site instanceof AuthoringSite.Location location)) throw new IllegalArgumentException("Choose a saved oriented point.");
        return location;
    }

    private static void requireAuthor(ServerCommandSource source) {
        if (!source.hasPermissionLevel(2) || source.getPlayer() == null || !AuthoringRuntime.isAuthor(source.getPlayer()) || !AuthoringRuntime.editable(source.getServer())) {
            throw new IllegalStateException("Automatic policy edits require operator permission and non-active Editing.");
        }
    }

    private static ServerPlayerEntity requireActiveOperator(ServerCommandSource source) {
        if (!source.hasPermissionLevel(2)) throw new IllegalStateException("Player movement requires operator permission level 2.");
        if (source.getPlayer() == null) throw new IllegalStateException("Player movement requires the operator who will confirm it.");
        if (ChronicleRuntime.service(source.getServer()).state().phase() != SessionPhase.ACTIVE) {
            throw new IllegalStateException("Player movement requires an ACTIVE Chronicle session.");
        }
        String hold = RuntimeHolds.reason(source.getServer());
        if (hold != null) throw new IllegalStateException("Player movement is blocked by a durable operation hold: " + hold);
        return source.getPlayer();
    }

    private static void requireKnownTarget(MinecraftServer server, UUID player) {
        if (server.getPlayerManager().getPlayer(player) != null) return;
        var state = AutomaticPlayerSpawns.available(server);
        if (state != null && (state.policies().containsKey(player) || state.arrivals().containsKey(player))) return;
        throw new IllegalArgumentException("Choose an online player or a player with existing Chronicle destination history.");
    }

    static String privatePurpose(UUID player, String pointId, long revision, boolean starting) {
        if (player == null || pointId == null || revision < 1) throw new IllegalArgumentException("player, point and positive revision are required");
        String compact = player.toString().replace("-", "");
        String purpose = "chronicle_core:" + (starting ? "s" : "d") + "_" + compact + "_" + Long.toString(revision, 36) + "_" + pointHash(pointId);
        if (!SpawnBinding.ID.matcher(purpose).matches()) throw new IllegalArgumentException("Private destination purpose exceeds the supported identifier bounds.");
        return purpose;
    }

    /** 48 stable hash bits distinguish imported point IDs while keeping the ID under 64 characters. */
    private static String pointHash(String pointId) {
        final byte[] digest;
        try { digest = MessageDigest.getInstance("SHA-256").digest(pointId.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
        StringBuilder result = new StringBuilder(12);
        for (int index = 0; index < 6; index++) result.append(String.format("%02x", digest[index] & 0xff));
        return result.toString();
    }

    private record Review(UUID target, String pointId, long pointRevision, int sessionNumber, long createdAtNanos) { }
    private static final class Runtime { final Map<UUID, Review> reviews = new HashMap<>(); }
}
