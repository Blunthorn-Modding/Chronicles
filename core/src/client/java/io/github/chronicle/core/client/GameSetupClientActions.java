package io.github.chronicle.core.client;

import java.util.*;

/** Optional client-side entry points for server-authorized game setup tools. */
public final class GameSetupClientActions {
    public interface Handler {
        void open(CoreMenuScreen parent);
        void cancel(CoreMenuScreen parent);
    }
    private static final Map<String, Handler> HANDLERS = new HashMap<>();
    private GameSetupClientActions() {}
    public static void register(String toolId, String verb, Handler handler) {
        if (HANDLERS.putIfAbsent(toolId + "\n" + verb, Objects.requireNonNull(handler)) != null)
            throw new IllegalArgumentException("Duplicate game setup client action");
    }
    static boolean open(CoreMenuScreen parent, String toolId, String verb) {
        Handler handler = HANDLERS.get(toolId + "\n" + verb);
        if (handler == null) return false;
        handler.open(parent); return true;
    }
    static void cancel(CoreMenuScreen parent) { HANDLERS.values().forEach(handler -> handler.cancel(parent)); }
}
