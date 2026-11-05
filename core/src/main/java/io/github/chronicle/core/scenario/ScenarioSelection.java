package io.github.chronicle.core.scenario;

import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Startup-only choice of packaged defaults. A custom authoring profile is already an explicit choice. */
public final class ScenarioSelection {
    private ScenarioSelection() { }
    public static Optional<String> resolve(Set<String> installed, Optional<String> requested) {
        Objects.requireNonNull(installed); Objects.requireNonNull(requested);
        if (installed.size() > 16) throw new IllegalArgumentException("At most sixteen scenario providers are supported.");
        installed.forEach(ScenarioSelection::id);
        if (requested.isPresent()) {
            id(requested.get());
            if (!installed.contains(requested.get())) throw new IllegalArgumentException("Selected scenario is not installed: " + requested.get());
            return requested;
        }
        if (installed.size() > 1) throw new IllegalArgumentException("Several scenario mods are installed. Select one in config/chronicle/scenario-selection.json, or install a custom authoring profile.");
        return installed.stream().findFirst();
    }
    public static String decode(byte[] bytes) {
        if (bytes == null || bytes.length < 1 || bytes.length > 4096) throw new IllegalArgumentException("Scenario selection must be bounded JSON.");
        final String text;
        try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException invalid) { throw new IllegalArgumentException("Scenario selection must be UTF-8.", invalid); }
        var root = BookConfig.strictObject(text);
        if (!root.keySet().equals(Set.of("schema", "scenarioId")) || !root.get("schema").isJsonPrimitive()
                || !root.get("schema").getAsJsonPrimitive().isNumber() || !root.get("schema").toString().equals("1")
                || !root.get("scenarioId").isJsonPrimitive() || !root.get("scenarioId").getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Use {\"schema\":1,\"scenarioId\":\"namespace:scenario\"}.");
        return id(root.get("scenarioId").getAsString());
    }
    public static byte[] encode(String selected) {
        JsonObject root = new JsonObject(); root.addProperty("schema", 1); root.addProperty("scenarioId", id(selected));
        return (root + "\n").getBytes(StandardCharsets.UTF_8);
    }
    private static String id(String value) {
        if (value == null || value.length() > 128 || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid scenario ID.");
        return value;
    }
}
