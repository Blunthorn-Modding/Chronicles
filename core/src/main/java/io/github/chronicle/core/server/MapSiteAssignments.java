package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.util.*;

/** Server-owned assignment index for a campaign's registered map requirements. */
public final class MapSiteAssignments {
    private static final Map<MinecraftServer, SiteAssignmentService> WORLDS = new IdentityHashMap<>();
    private MapSiteAssignments() { }

    /** Starts an independent strict store and adopts compatible legacy same-ID sites once. */
    public static void start(MinecraftServer server) {
        var file = server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/map-assignments.json");
        try {
            SiteAssignmentStore store = new SiteAssignmentStore(file);
            SiteAssignmentService service = new SiteAssignmentService(store, ChronicleCore.authoring());
            if (store.wasAbsent()) service.establishFromExisting(AuthoringRuntime.get(server).service().state());
            WORLDS.put(server, service);
            ChronicleCore.LOGGER.info("Chronicle map assignments ready: {}", file);
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            WORLDS.remove(server);
            ChronicleCore.LOGGER.error("Map assignments unavailable; existing data was not replaced. Restore {} while stopped.", file, exception);
        }
    }

    public static void stop(MinecraftServer server) { WORLDS.remove(server); }

    public static Map<String, SiteAssignments.Binding> bindings(MinecraftServer server) {
        return service(server).state().bindings();
    }

    /** Monotonic state revision for menu-frame invalidation, including clear/reassign of the same link. */
    public static long revision(MinecraftServer server) { return service(server).state().revision(); }

    public static SiteAssignments.Binding assign(ServerCommandSource source, String requirementId, String siteId) throws IOException {
        ServerPlayerEntity player = editor(source);
        SiteAssignments.Binding binding = service(source.getServer()).assign(requirementId, siteId,
                AuthoringRuntime.get(source.getServer()).service().state());
        ChronicleCore.LOGGER.info("Map assignment {} -> {} by {} ({})", requirementId, siteId, source.getName(), player.getUuid());
        return binding;
    }

    public static void clear(ServerCommandSource source, String requirementId) throws IOException {
        ServerPlayerEntity player = editor(source);
        service(source.getServer()).clear(requirementId);
        ChronicleCore.LOGGER.info("Map assignment cleared {} by {} ({})", requirementId, source.getName(), player.getUuid());
    }

    /** Returns no site when unassigned and fails closed when its saved-site revision is stale. */
    public static Optional<AuthoringSite> resolve(MinecraftServer server, String requirementId) {
        return service(server).resolve(requirementId, AuthoringRuntime.get(server).service().state());
    }

    private static SiteAssignmentService service(MinecraftServer server) {
        if (!server.isOnThread()) throw new IllegalStateException("Map assignments require the server thread.");
        SiteAssignmentService service = WORLDS.get(server);
        if (service == null) throw new IllegalStateException("Map assignments unavailable; inspect map-assignments.json while stopped.");
        return service;
    }

    private static ServerPlayerEntity editor(ServerCommandSource source) {
        if (!source.hasPermissionLevel(2)) throw new IllegalStateException("Map assignment requires operator permission level 2.");
        if (!AuthoringRuntime.editable(source.getServer())) throw new IllegalStateException("Map assignments require a non-active Chronicle session.");
        ServerPlayerEntity player = source.getPlayer();
        if (player == null || !AuthoringRuntime.isAuthor(player)) throw new IllegalStateException("Enable author mode before changing map assignments.");
        return player;
    }

}
