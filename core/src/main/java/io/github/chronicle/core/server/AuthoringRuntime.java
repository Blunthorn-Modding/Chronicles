package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.AuthoringService;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.AuthoringStore;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.network.AuthoringPreviewPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread-only per-world authoring state. Geometry never activates gameplay. */
public final class AuthoringRuntime {
    private static final Map<MinecraftServer, AuthoringRuntime> WORLDS = new IdentityHashMap<>();
    private final AuthoringService service;
    private final Map<UUID, Selection> authors = new HashMap<>();

    private AuthoringRuntime(AuthoringService service) { this.service = service; }

    public static void start(MinecraftServer server) {
        var file = server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/authoring.json");
        try {
            WORLDS.put(server, new AuthoringRuntime(new AuthoringService(new AuthoringStore(file))));
            ChronicleCore.LOGGER.info("Chronicle authoring ready: {}", file);
        } catch (IOException | IllegalArgumentException exception) {
            WORLDS.remove(server);
            ChronicleCore.LOGGER.error("Authoring unavailable; existing data was not replaced. Restore {} while stopped.", file, exception);
        }
    }

    public static AuthoringRuntime get(MinecraftServer server) {
        if (!server.isOnThread()) throw new IllegalStateException("Authoring requires the server thread.");
        var runtime = WORLDS.get(server);
        if (runtime == null) throw new IllegalStateException("Authoring unavailable; inspect the server log and authoring.json while stopped.");
        return runtime;
    }

    public static void stop(MinecraftServer server) { WORLDS.remove(server); }

    public static void disconnected(ServerPlayerEntity player) {
        var runtime = WORLDS.get(player.getEntityWorld().getServer());
        if (runtime != null) {
            runtime.authors.remove(player.getUuid());
        }
    }

    /** Future condition services use this instead of letting builder walks consume discoveries. */
    public static boolean isAuthor(ServerPlayerEntity player) {
        var runtime = WORLDS.get(player.getEntityWorld().getServer());
        return runtime != null && runtime.authors.containsKey(player.getUuid()) && editable(player.getEntityWorld().getServer());
    }

    public static boolean editable(MinecraftServer server) {
        try { return ChronicleRuntime.service(server).state().phase() != SessionPhase.ACTIVE; }
        catch (IllegalStateException exception) { return false; }
    }

    public AuthoringService service() { return service; }
    public void enable(UUID player) { authors.putIfAbsent(player, new Selection(null, null, null)); }
    public void disable(UUID player) { authors.remove(player); }
    public Selection selection(UUID player) {
        var selection = authors.get(player);
        if (selection == null) throw new IllegalStateException("Enable author mode first: /chronicle author mode on");
        return selection;
    }
    public void selection(UUID player, Selection selection) { selection(player); authors.put(player, selection); }
    public void preview(ServerPlayerEntity player, AuthoringSite site) {
        if (!ServerPlayNetworking.canSend(player, AuthoringPreviewPayload.ID)) {
            player.sendMessage(Text.literal("Preview requires the matching Chronicle M6 client pack."), false);
            return;
        }
        AuthoringPreviewPayload payload;
        if (site instanceof AuthoringSite.Region region) {
            payload = new AuthoringPreviewPayload(site.dimension(), true,
                    region.min().x(), region.min().y(), region.min().z(),
                    region.max().x() + 1.0, region.max().y() + 1.0, region.max().z() + 1.0, false, 0, 0);
        } else if (site instanceof AuthoringSite.Location point) {
            payload = new AuthoringPreviewPayload(site.dimension(), true, point.x() - .1, point.y() - .1, point.z() - .1,
                    point.x() + .1, point.y() + .1, point.z() + .1, true, point.yaw(), point.pitch());
        } else {
            var anchor = (AuthoringSite.Anchor) site;
            var pos = anchor.position();
            payload = new AuthoringPreviewPayload(site.dimension(), true, pos.x(), pos.y(), pos.z(), pos.x() + 1.0, pos.y() + 1.0, pos.z() + 1.0, false, 0, 0);
        }
        ServerPlayNetworking.send(player, payload);
    }

    public static void clearPreview(ServerPlayerEntity player) {
        if (ServerPlayNetworking.canSend(player, AuthoringPreviewPayload.ID)) {
            ServerPlayNetworking.send(player, AuthoringPreviewPayload.clear(player.getEntityWorld().getRegistryKey().getValue().toString()));
        }
    }

    public static void tick(MinecraftServer server) {
        var runtime = WORLDS.get(server);
        if (runtime != null && !editable(server)) runtime.authors.clear();
    }

    public record Selection(String dimension, BlockPosition first, BlockPosition second) { }
}
