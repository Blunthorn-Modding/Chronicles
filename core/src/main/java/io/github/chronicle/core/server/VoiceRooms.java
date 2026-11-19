package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.voice.VoiceRoomCatalog;
import io.github.chronicle.core.voice.VoiceRoomGroups;
import io.github.chronicle.core.voice.VoiceRoomStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Optional, world-owned voice rooms. Independent of campaign participation and session clocks. */
public final class VoiceRooms {
    public static final String SUBTYPE = "chronicle_core:voice_room";
    private static final String HOLD = "chronicle_core:voice_rooms";
    private static final VoiceRoomGroups.Routing CLOSED = new VoiceRoomGroups.Routing(Map.of(), Map.of(), true);
    private static volatile Runtime current;
    private static volatile VoiceRoomGroups.Backend backend;
    private record Live(VoiceRoomGroups.Backend adapter, VoiceRoomGroups groups) { }

    private static final class Runtime {
        final MinecraftServer server;
        final VoiceRoomStore store;
        volatile Live live;
        final Map<UUID, Integer> transfers = new java.util.HashMap<>();
        final Set<UUID> moving = new java.util.HashSet<>();
        Map<String, VoiceRoomStore.Binding> bindings = Map.of();
        VoiceRoomCatalog catalog;
        volatile boolean failed;
        String issue = "";
        boolean held;
        int verifiedTick = Integer.MIN_VALUE;
        Runtime(MinecraftServer server) {
            this.server = server;
            store = new VoiceRoomStore(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle"));
        }
    }

    private VoiceRooms() { }

    public static void register() {
        SiteSubtypes.register(new SiteSubtypes.Definition(SUBTYPE, "Voice room",
                "Automatically create a private room group. Enter to join; leave to return to proximity voice.",
                Set.of(SiteSubtypes.Kind.REGION), new SiteSubtypes.Handler() {
            public boolean assigned(MinecraftServer s, AuthoringSite site) { return VoiceRooms.assigned(s, site.id()); }
            public String refusal(MinecraftServer s, AuthoringSite site, boolean enabling) {
                return enabling ? enableRefusal(s) : "";
            }
            public SiteSubtypes.Change change(ServerCommandSource source, AuthoringSite site, boolean enabled) throws Exception {
                set(source, site.id(), enabled); return SiteSubtypes.Change.APPLIED;
            }
        }));
        ServerLifecycleEvents.SERVER_STARTED.register(VoiceRooms::start);
        ServerTickEvents.END_SERVER_TICK.register(VoiceRooms::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(VoiceRooms::stop);
    }

    public static boolean assigned(MinecraftServer server, String id) {
        Runtime r = current;
        return r != null && r.server == server && r.bindings.containsKey(id);
    }

    public static String enableRefusal(MinecraftServer server) {
        Runtime r = current;
        if (r == null || r.server != server || r.failed) return "Repair voice-room data while the server is stopped.";
        return backend == null ? "Install and enable compatible Simple Voice Chat on the server and clients first." : "";
    }

    public static String status(MinecraftServer server, String region) {
        Runtime r = current;
        if (r == null || r.server != server || !r.bindings.containsKey(region)) return "Disabled";
        if (r.failed || !r.issue.isEmpty()) return "Unavailable: " + r.issue;
        Live live = r.live;
        if (live == null) return "Waiting for Simple Voice Chat";
        long members = live.groups().routing().membership().values().stream().filter(region::equals).count();
        return VoiceRoomGroups.displayName(region) + " (hidden, isolated); " + members + " player(s) inside";
    }

    /** Called from the optional plugin; no voice API types enter mandatory Core classes. */
    public static void attach(VoiceRoomGroups.Backend adapter) {
        backend = adapter;
        Runtime r = current;
        if (r != null) r.server.execute(() -> refresh(r.server));
    }

    public static void detach(VoiceRoomGroups.Backend adapter) {
        if (backend != adapter) return;
        backend = null;
        Runtime r = current;
        if (r != null) {
            if (r.live != null) r.live.groups().block();
            r.server.execute(() -> refresh(r.server));
        }
    }

    /** Only immutable data is read by voice/network threads. */
    public static VoiceRoomGroups.Routing routing(VoiceRoomGroups.Backend adapter) {
        Runtime r = current;
        if (backend != adapter || r == null || r.failed) return CLOSED;
        Live live = r.live;
        return live == null || live.adapter() != adapter || backend != adapter ? CLOSED : live.groups().routing();
    }

    public static void set(ServerCommandSource source, String regionId, boolean enabled) throws Exception {
        MinecraftServer server = source.getServer(); Runtime r = require(server);
        if (!source.hasPermissionLevel(2) || source.getPlayer() == null || !AuthoringRuntime.isAuthor(source.getPlayer())
                || !AuthoringRuntime.editable(server) || RuntimeHolds.reason(server) != null)
            throw new IllegalStateException("Voice room edits require non-active operator Editing without pending recovery.");
        if (r.failed) throw new IllegalStateException("Repair voice-room data while stopped.");
        if (enabled && !enableRefusal(server).isEmpty()) throw new IllegalStateException(enableRefusal(server));
        if (!(AuthoringRuntime.get(server).service().state().sites().get(regionId) instanceof AuthoringSite.Region region))
            throw new IllegalArgumentException("Choose a saved Region.");
        Map<String, VoiceRoomStore.Binding> next = new TreeMap<>(r.bindings);
        if (enabled) next.put(regionId, new VoiceRoomStore.Binding(regionId, region.revision())); else next.remove(regionId);
        VoiceRoomCatalog catalog = new VoiceRoomCatalog(next, AuthoringRuntime.get(server).service().state());
        try {
            r.store.save(next);
            r.bindings = Map.copyOf(next); r.catalog = catalog;
            reconcile(r);
        } catch (Exception failure) { fail(r, failure); throw failure; }
    }

    private static void start(MinecraftServer server) {
        Runtime r = new Runtime(server); current = r;
        try {
            AuthoringRuntime.get(server).service().addMutationGuard((existing, proposed, operation) -> {
                if (r.failed) throw new IllegalStateException("Repair voice-room data before changing Regions.");
                if (existing != null && r.bindings.containsKey(existing.id()))
                    throw new IllegalStateException("Disable Voice room before changing or deleting this Region.");
            });
            r.bindings = r.store.load();
            r.catalog = new VoiceRoomCatalog(r.bindings, AuthoringRuntime.get(server).service().state());
            reconcile(r);
        } catch (Exception failure) { fail(r, failure); }
    }

    private static void tick(MinecraftServer server) {
        Runtime r = current;
        if (r == null || r.server != server || r.failed) return;
        // Only the true END-tick boundary may retire guards from cancelled setters.
        r.transfers.clear(); r.moving.clear();
        refresh(server);
    }

    private static void refresh(MinecraftServer server) {
        Runtime r = current;
        if (r == null || r.server != server || r.failed) return;
        try {
            if (!r.transfers.isEmpty() || !r.moving.isEmpty()) {
                if (r.live != null) r.live.groups().block();
                return;
            }
            if (server.getTicks() - (long) r.verifiedTick >= 20) {
                r.store.verifyUnchanged();
                r.catalog = new VoiceRoomCatalog(r.bindings, AuthoringRuntime.get(server).service().state());
                r.verifiedTick = server.getTicks();
            }
            reconcile(r);
        } catch (Exception failure) { fail(r, failure); }
    }

    private static void reconcile(Runtime r) throws Exception {
        VoiceRoomGroups.Backend available = backend;
        if (available == null) {
            if (!r.bindings.isEmpty()) hold(r, "Simple Voice Chat is unavailable; room privacy is not ready.");
            return;
        }
        if (r.live == null || r.live.adapter() != available) {
            if (r.live != null) r.live.groups().block();
            VoiceRoomGroups groups = new VoiceRoomGroups(available);
            groups.block();
            r.live = new Live(available, groups);
        }
        Map<UUID, String> membership = new java.util.HashMap<>();
        for (var player : r.server.getPlayerManager().getPlayerList()) {
            var position = player.getEntityPos();
            if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z))
                throw new IllegalStateException("A player has an invalid position.");
            r.catalog.at(player.getEntityWorld().getRegistryKey().getValue().toString(), position.x, position.y, position.z)
                    .ifPresent(room -> membership.put(player.getUuid(), room.id()));
        }
        r.live.groups().reconcile(r.catalog.regions().keySet(), membership);
        r.issue = "";
        if (r.held) { RuntimeHolds.release(r.server, HOLD); r.held = false; }
    }

    private static void hold(Runtime r, String issue) {
        r.issue = issue;
        if (r.live != null) r.live.groups().block();
        if (!r.held) { RuntimeHolds.hold(r.server, HOLD, issue); r.held = true; }
    }
    private static void fail(Runtime r, Exception error) {
        r.failed = true;
        hold(r, "Voice room unavailable. Repair room setup while the server is stopped; see its log for details.");
        ChronicleCore.LOGGER.error("Voice-room setup failed", error);
    }
    private static Runtime require(MinecraftServer s) {
        Runtime r = current;
        if (r == null || r.server != s) throw new IllegalStateException("Voice rooms are unavailable.");
        return r;
    }
    private static void stop(MinecraftServer server) {
        Runtime r = current;
        if (r == null || r.server != server) return;
        r.failed = true; // Keep packet routing closed throughout shutdown cleanup.
        try { if (r.live != null) r.live.groups().close(); }
        catch (Exception e) { ChronicleCore.LOGGER.warn("Voice-room group cleanup failed during shutdown", e); }
        finally { if (r.live != null) r.live.groups().block(); current = null; }
    }

    /** Invalidate before an authoritative coordinate commit, without reading a world on audio threads. */
    public static void beforePosition(ServerPlayerEntity player, double x, double y, double z) {
        Runtime r = movementRuntime(player);
        if (r == null) return;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            r.moving.add(player.getUuid()); r.live.groups().block(); return;
        }
        String previous = r.live.groups().routing().membership().get(player.getUuid());
        String next = r.catalog.at(player.getEntityWorld().getRegistryKey().getValue().toString(), x, y, z)
                .map(AuthoringSite.Region::id).orElse(null);
        if (!java.util.Objects.equals(previous, next) || r.transfers.containsKey(player.getUuid())) {
            r.moving.add(player.getUuid()); r.live.groups().block();
        }
    }

    public static void afterPosition(ServerPlayerEntity player) {
        Runtime r = movementRuntime(player);
        if (r == null || !r.moving.remove(player.getUuid()) || !r.transfers.isEmpty() || !r.moving.isEmpty()) return;
        try { reconcile(r); } catch (Exception e) { fail(r, e); }
    }

    public static void beforeTransfer(ServerPlayerEntity player) {
        Runtime r = movementRuntime(player);
        if (r != null) { r.transfers.merge(player.getUuid(), 1, Integer::sum); r.live.groups().block(); }
    }

    public static void afterTransfer(ServerPlayerEntity player) {
        Runtime r = movementRuntime(player);
        if (r == null) return;
        int depth = r.transfers.getOrDefault(player.getUuid(), 0);
        if (depth == 0) return;
        if (depth == 1) r.transfers.remove(player.getUuid()); else r.transfers.put(player.getUuid(), depth - 1);
        if (!r.transfers.isEmpty()) return;
        r.moving.remove(player.getUuid());
        if (!r.moving.isEmpty()) return;
        try { reconcile(r); } catch (Exception e) { fail(r, e); }
    }

    private static Runtime movementRuntime(ServerPlayerEntity player) {
        Runtime r = current;
        if (r == null || r.failed || r.catalog == null || r.bindings.isEmpty() || r.live == null
                || player.getUuid() == null || player.getEntityWorld() == null
                || player.getEntityWorld().getServer() != r.server || !r.server.isOnThread()) return null;
        return r;
    }

    /** Trusted server-side diagnostics; no remote packet exposes this authority snapshot. */
    public static VoiceRoomGroups.Routing inspect(MinecraftServer server) {
        if (!server.isOnThread()) throw new IllegalStateException("Room diagnostics require the server thread.");
        Runtime r = require(server);
        Live live = r.live;
        return r.failed || live == null || live.adapter() != backend ? CLOSED : live.groups().routing();
    }
}
