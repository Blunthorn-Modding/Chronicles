package io.github.chronicle.core.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.chronicle.core.ChronicleCore;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Small local-only preference for the editor presentation; server state is never read or changed. */
public final class EditorMenuPreferences {
    private static final String FILE_NAME = "chronicle/editor-menu.json";
    private static Boolean debug;

    private EditorMenuPreferences() { }

    public static boolean debug() {
        if (debug == null) debug = load();
        return debug;
    }

    public static void setDebug(boolean value) {
        debug = value;
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("debug", value);
            Files.writeString(path, root.toString());
        } catch (Exception error) {
            ChronicleCore.LOGGER.warn("Chronicle editor preference was not saved; using this client session only", error);
        }
    }

    static void resetForTest() { debug = null; }

    private static boolean load() {
        try {
            Path path = path();
            if (!Files.isRegularFile(path)) return false;
            if (Files.size(path) > 4_096) throw new IllegalArgumentException("Preference file is too large");
            var element = JsonParser.parseString(Files.readString(path));
            if (!element.isJsonObject()) throw new IllegalArgumentException("Preference document is not an object");
            JsonObject root = element.getAsJsonObject();
            if (!root.keySet().equals(java.util.Set.of("version", "debug"))
                    || !root.get("version").isJsonPrimitive() || root.get("version").getAsInt() != 1
                    || !root.get("debug").isJsonPrimitive() || !root.get("debug").getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("Unsupported preference document");
            }
            return root.get("debug").getAsBoolean();
        } catch (Exception error) {
            ChronicleCore.LOGGER.warn("Chronicle editor preference ignored; using Clean view", error);
            return false;
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
