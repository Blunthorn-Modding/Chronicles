package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.campaign.SessionPhase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

import java.util.IdentityHashMap;
import java.util.Map;

/** Deliberate global map-work permission. Never persisted or inferred from a player flag. */
public final class SessionBuilding {
    private static final Map<MinecraftServer, Window> ENABLED = new IdentityHashMap<>();
    private record Window(SessionPhase phase, int session) { }
    private SessionBuilding() { }

    public static boolean enabled(MinecraftServer server) {
        requireThread(server);
        var service = ChronicleRuntime.available(server);
        var state = service == null ? null : service.state();
        Window window = ENABLED.get(server);
        if (state == null || state.phase() == SessionPhase.ACTIVE
                || window == null || window.phase() != state.phase() || window.session() != state.sessionNumber()) {
            ENABLED.remove(server);
            return false;
        }
        // A temporary operation suspends building without discarding the operator's choice.
        return RuntimeHolds.reason(server) == null;
    }

    public static String set(ServerCommandSource source, boolean value) {
        var server = source.getServer();
        requireThread(server);
        if (!source.hasPermissionLevel(2)) throw new IllegalStateException("Building Mode requires operator permission level 2.");
        var service = ChronicleRuntime.available(server);
        if (service == null) throw new IllegalStateException("Chronicle is unavailable; Building Mode cannot be changed.");
        if (RuntimeHolds.reason(server) != null) throw new IllegalStateException("Resolve the active Chronicle operation hold first.");
        var state = service.state();
        if (state.phase() == SessionPhase.ACTIVE) throw new IllegalStateException("Building Mode requires a non-active Chronicle play state.");
        if (value) ENABLED.put(server, new Window(state.phase(), state.sessionNumber()));
        else ENABLED.remove(server);
        SessionPresentation.changed(server);
        ChronicleCore.LOGGER.info("Building Mode {} by {}; player survival remains held", value ? "enabled for everyone" : "disabled", source.getName());
        return value ? "Building Mode enabled for everyone. World running; player health and hunger held."
                : "Building Mode disabled. World and map interactions are frozen.";
    }

    public static void stop(MinecraftServer server) { ENABLED.remove(server); }
    private static void requireThread(MinecraftServer server) {
        if (!server.isOnThread()) throw new IllegalStateException("Building Mode requires the server thread.");
    }
}
