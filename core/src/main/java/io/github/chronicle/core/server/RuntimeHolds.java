package io.github.chronicle.core.server;

import net.minecraft.server.MinecraftServer;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Trusted Core services hold simulation during unresolved durable operations, without changing story phase. */
public final class RuntimeHolds {
    private static final Map<MinecraftServer, Map<String, String>> HOLDS = new IdentityHashMap<>();
    private RuntimeHolds() { }
    public static void hold(MinecraftServer server, String owner, String reason) {
        if (!server.isOnThread()) throw new IllegalStateException("Runtime holds require the server thread.");
        if (owner == null || owner.isBlank() || reason == null || reason.isBlank()) throw new IllegalArgumentException("Hold owner/reason required.");
        HOLDS.computeIfAbsent(server, ignored -> new TreeMap<>()).put(owner, reason);
        SessionPresentation.changed(server);
    }
    public static void release(MinecraftServer server, String owner) {
        if (!server.isOnThread()) throw new IllegalStateException("Runtime holds require the server thread.");
        var holds = HOLDS.get(server);
        if (holds != null) { holds.remove(owner); if (holds.isEmpty()) HOLDS.remove(server); }
        SessionPresentation.changed(server);
    }
    public static String reason(MinecraftServer server) {
        var holds = HOLDS.get(server);
        return holds == null || holds.isEmpty() ? null : holds.values().iterator().next();
    }
    public static void stop(MinecraftServer server) { HOLDS.remove(server); }
}
