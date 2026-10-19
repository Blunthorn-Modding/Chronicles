package io.github.chronicle.core.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.StringReader;

/** Versioned JSON persistence for campaign state. */
public final class CampaignStore {
    public static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private final Path file;

    public CampaignStore(Path file) {
        if (file == null || file.getFileName() == null) {
            throw new IllegalArgumentException("campaign state file is required");
        }
        this.file = file.toAbsolutePath().normalize();
    }

    public CampaignState load() throws IOException {
        if (!Files.exists(file)) {
            return CampaignState.newWorld();
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("Campaign state path is not a regular file: " + file);
        }
        long size = Files.size(file);
        if (size > MAX_FILE_BYTES) {
            throw new IOException("Campaign state exceeds 2 MiB limit: " + file);
        }
        try {
            return decode(Files.readString(file, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new IOException("Campaign state is invalid and was not replaced: " + file + " (" + exception.getMessage() + ")", exception);
        }
    }

    public void save(CampaignState state) throws IOException {
        if (state == null) {
            throw new IllegalArgumentException("campaign state is required");
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(file)) {
            // Refuse to replace an invalid current file; its evidence must remain available to an operator.
            load();
        }
        byte[] bytes = encode(state).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Campaign state serialization exceeds 2 MiB limit: " + file);
        }
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        Path backupTemporary = null;
        try {
            forceWrite(temporary, bytes);
            if (Files.exists(file)) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                backupTemporary = Files.createTempFile(parent, backup.getFileName().toString(), ".tmp");
                Files.copy(file, backupTemporary, StandardCopyOption.REPLACE_EXISTING);
                force(backupTemporary);
                moveReplace(backupTemporary, backup);
                backupTemporary = null;
            }
            moveReplace(temporary, file);
        } catch (IOException exception) {
            throw new IOException("Unable to save campaign state to " + file + "; current in-memory state was not changed", exception);
        } finally {
            deleteQuietly(temporary);
            deleteQuietly(backupTemporary);
        }
    }

    private static void forceWrite(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void force(Path target) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic replacement is not supported for campaign state: " + target, exception);
        }
    }

    private static void deleteQuietly(Path target) {
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // A leftover temporary file cannot invalidate a completed atomic replacement.
        }
    }

    private static CampaignState decode(String source) {
        JsonElement parsed;
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            parsed = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("unexpected trailing JSON content");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("unable to read campaign JSON", exception);
        }
        JsonObject root = object(parsed, "root");
        int schemaVersion = integer(required(root, "schemaVersion"), "schemaVersion");
        if (schemaVersion > CampaignState.SCHEMA_VERSION) {
            throw new IllegalArgumentException("campaign schema " + schemaVersion + " is newer than supported version " + CampaignState.SCHEMA_VERSION);
        }
        SessionPhase phase;
        try {
            phase = SessionPhase.valueOf(string(required(root, "phase"), "phase"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown campaign phase", exception);
        }
        Map<String, Boolean> flags = booleans(object(required(root, "flags"), "flags"), "flags");
        Set<String> completedEvents = strings(array(required(root, "completedEvents"), "completedEvents"), "completedEvents");
        List<AuditEntry> history = history(array(required(root, "history"), "history"));
        return new CampaignState(schemaVersion, integer(required(root, "sessionNumber"), "sessionNumber"), phase,
                flags, completedEvents, history, longNumber(required(root, "nextSequence"), "nextSequence"));
    }

    private static String encode(CampaignState state) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", state.schemaVersion());
        root.addProperty("sessionNumber", state.sessionNumber());
        root.addProperty("phase", state.phase().name());
        root.add("flags", booleans(state.flags()));
        JsonArray completed = new JsonArray();
        state.completedEvents().stream().sorted().forEach(completed::add);
        root.add("completedEvents", completed);
        JsonArray history = new JsonArray();
        for (AuditEntry entry : state.history()) {
            JsonObject encoded = new JsonObject();
            encoded.addProperty("sequence", entry.sequence());
            encoded.addProperty("timestamp", entry.timestamp());
            JsonObject actor = new JsonObject();
            actor.addProperty("name", entry.actor().name());
            if (entry.actor().uuid() != null) {
                actor.addProperty("uuid", entry.actor().uuid());
            }
            encoded.add("actor", actor);
            encoded.addProperty("action", entry.action());
            encoded.addProperty("sessionNumber", entry.sessionNumber());
            encoded.addProperty("target", entry.target());
            JsonObject parameters = new JsonObject();
            entry.parameters().forEach(parameters::addProperty);
            encoded.add("parameters", parameters);
            history.add(encoded);
        }
        root.add("history", history);
        root.addProperty("nextSequence", state.nextSequence());
        return root.toString();
    }

    private static List<AuditEntry> history(JsonArray values) {
        List<AuditEntry> result = new ArrayList<>();
        for (JsonElement value : values) {
            JsonObject entry = object(value, "history entry");
            JsonObject actor = object(required(entry, "actor"), "audit actor");
            String uuid = actor.has("uuid") && !actor.get("uuid").isJsonNull() ? string(actor.get("uuid"), "actor uuid") : null;
            result.add(new AuditEntry(longNumber(required(entry, "sequence"), "sequence"),
                    string(required(entry, "timestamp"), "timestamp"),
                    new Actor(string(required(actor, "name"), "actor name"), uuid),
                    string(required(entry, "action"), "action"), integer(required(entry, "sessionNumber"), "sessionNumber"),
                    string(required(entry, "target"), "target"), stringsMap(object(required(entry, "parameters"), "parameters"))));
        }
        return result;
    }

    private static JsonObject booleans(Map<String, Boolean> values) {
        JsonObject object = new JsonObject();
        values.forEach(object::addProperty);
        return object;
    }

    private static Map<String, Boolean> booleans(JsonObject values, String field) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException(field + " values must be boolean");
            }
            result.put(entry.getKey(), entry.getValue().getAsBoolean());
        }
        return result;
    }

    private static Set<String> strings(JsonArray values, String field) {
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement value : values) {
            result.add(string(value, field + " entry"));
        }
        if (result.size() != values.size()) {
            throw new IllegalArgumentException(field + " may not contain duplicates");
        }
        return result;
    }

    private static Map<String, String> stringsMap(JsonObject values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            result.put(entry.getKey(), string(entry.getValue(), "parameter value"));
        }
        return result;
    }

    private static JsonElement required(JsonObject object, String field) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return object.get(field);
    }

    private static JsonObject object(JsonElement value, String field) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonElement value, String field) {
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static String string(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static int integer(JsonElement value, String field) {
        long result = longNumber(value, field);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " is outside integer range");
        }
        return (int) result;
    }

    private static long longNumber(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        String source = value.getAsString();
        if (!source.matches("-?(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            return Long.parseLong(source);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " is outside long range", exception);
        }
    }
}
