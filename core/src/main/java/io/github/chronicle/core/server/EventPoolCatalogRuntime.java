package io.github.chronicle.core.server;

import io.github.chronicle.core.eventpool.*;
import io.github.chronicle.core.network.*;
import io.github.chronicle.core.play.*;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import java.io.IOException;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-owned reusable definitions, independent from live pool draws. */
public final class EventPoolCatalogRuntime {
    private static final Gson JSON = new Gson();
    private record Session(ServerPlayerEntity player, UUID token) {}
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<String, EventPoolDefinition> DEFAULTS = new TreeMap<>();
    private static final Map<MinecraftServer, EventPoolCatalog> WORLDS = new IdentityHashMap<>();
    private EventPoolCatalogRuntime() {}
    public static synchronized void registerDefaults(Collection<EventPoolDefinition> pools) {
        Objects.requireNonNull(pools); if (!WORLDS.isEmpty()) throw new IllegalStateException("Pool defaults are frozen");
        for (var pool : pools) { Objects.requireNonNull(pool); var old = DEFAULTS.putIfAbsent(pool.id(), pool); if (old != null && !old.equals(pool)) throw new IllegalArgumentException("Conflicting pool default: " + pool.id()); }
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(EventPoolEditorPayloads.Request.ID, (payload, context) -> context.server().execute(() -> receive(context.player(), payload)));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                Collection<EventPoolDefinition> defaults = ScenarioAuthoringProfiles.active().isPresent() ? List.of() : List.copyOf(DEFAULTS.values());
                WORLDS.put(server, new EventPoolCatalog(new EventPoolCatalogStore(ScenarioAuthoringProfiles.contentFile("event-pools.json")), defaults));
            } catch (IOException | RuntimeException failure) { throw new IllegalStateException("Cannot load event pool catalogue; existing data was preserved", failure); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { WORLDS.remove(server); SESSIONS.clear(); });
    }
    private static EventPoolCatalog catalog(MinecraftServer server) { var catalog = WORLDS.get(server); if (catalog == null) throw new IllegalStateException("Event pool catalogue unavailable"); return catalog; }
    public static Map<String, EventPoolDefinition> definitions(MinecraftServer server) { return catalog(server).definitions(); }
    public static long revision(MinecraftServer server) { return catalog(server).revision(); }
    public static void save(ServerPlayerEntity player, long expected, EventPoolDefinition pool) throws IOException { requireEditing(player); catalog(player.getEntityWorld().getServer()).put(expected, pool); }
    public static void delete(ServerPlayerEntity player, long expected, String id) throws IOException { requireEditing(player); catalog(player.getEntityWorld().getServer()).delete(expected, id); }
    private static void receive(ServerPlayerEntity player, EventPoolEditorPayloads.Request request) {
        if (!player.getCommandSource().hasPermissionLevel(2) || player.getEntityWorld().getServer().getPlayerManager().getPlayer(player.getUuid()) != player) return;
        var server = player.getEntityWorld().getServer(); Session session = SESSIONS.get(player.getUuid());
        if (request.action() == 0) { session = new Session(player, request.token()); SESSIONS.put(player.getUuid(), session); }
        else if (session == null || session.player() != player || !session.token().equals(request.token())) return;
        String message = "";
        try {
            if (request.action() == 1) {
                var parsed = EventPoolCatalogStore.decode(request.data().getBytes(StandardCharsets.UTF_8));
                if (parsed.size() != 1) throw new IllegalArgumentException("Submit exactly one pool");
                var pool = parsed.values().iterator().next(); var old = definitions(server).get(pool.id());
                var existing = old == null ? Set.<String>of() : old.entries().stream().map(EventPoolDefinition.Entry::eventId).collect(java.util.stream.Collectors.toSet());
                for (var entry : pool.entries()) if (!existing.contains(entry.eventId()) && !issue(server, entry.eventId()).isEmpty()) throw new IllegalArgumentException("Only enabled manual once-only Events with no delay can be added");
                save(player, request.revision(), pool); message = "Pool saved.";
            } else if (request.action() == 2) { delete(player, request.revision(), request.data()); message = "Pool deleted."; }
        } catch (IOException | RuntimeException error) { message = error.getMessage() == null ? "Pool change was rejected." : error.getMessage(); }
        if (message.length() > 512) message = message.substring(0, 512);
        List<EventPoolEditorView.Option> events = EventRuntime.configurations(server).values().stream().map(c -> new EventPoolEditorView.Option(c.definition().id(), c.definition().label(), issue(server, c.definition().id()))).sorted(Comparator.comparing(EventPoolEditorView.Option::label).thenComparing(EventPoolEditorView.Option::id)).toList();
        String data = JSON.toJson(new EventPoolEditorView(List.copyOf(definitions(server).values()), events));
        boolean editable = AuthoringRuntime.isAuthor(player) && AuthoringRuntime.editable(server) && RuntimeHolds.reason(server) == null;
        ServerPlayNetworking.send(player, new EventPoolEditorPayloads.Response(session.token(), revision(server), data, message, editable));
    }
    public static String issue(MinecraftServer server, String id) {
        var c = EventRuntime.configurations(server).get(id);
        if (c == null) return "Event is missing.";
        if (!c.enabled()) return "Event is disabled.";
        if (c.mode() != PlayState.Mode.MANUAL || c.occurrencePolicy() != EventOccurrencePolicy.ONCE || c.delayTicks() != 0) return "Requires manual, once-only and no delay.";
        return "";
    }    private static void requireEditing(ServerPlayerEntity player) {
        if (player == null || !player.getCommandSource().hasPermissionLevel(2) || !AuthoringRuntime.isAuthor(player) || !AuthoringRuntime.editable(player.getEntityWorld().getServer()) || RuntimeHolds.reason(player.getEntityWorld().getServer()) != null)
            throw new IllegalStateException("Pool edits require non-active operator Editing and no pending operation");
    }
}
