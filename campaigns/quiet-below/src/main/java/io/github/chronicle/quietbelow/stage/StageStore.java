package io.github.chronicle.quietbelow.stage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.github.chronicle.core.campaign.Actor;

import java.io.IOException;
import java.io.StringReader;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict, versioned, atomic JSON journal with a previous-file backup. */
public final class StageStore implements StageRepository {
    static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private final Path file;

    public StageStore(Path file) {
        if (file == null || file.getFileName() == null) {
            throw new IllegalArgumentException("stage state file is required");
        }
        this.file = file.toAbsolutePath().normalize();
    }

    @Override
    public StageState load() throws IOException {
        if (!Files.exists(file)) {
            return StageState.newWorld();
        }
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("Stage state must be a regular file no larger than 2 MiB: " + file);
        }
        try {
            return decode(Files.readString(file, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new IOException("Stage state is invalid and was not replaced: " + file + " (" + exception.getMessage() + ")", exception);
        }
    }

    @Override
    public void save(StageState state) throws IOException {
        if (state == null) {
            throw new IllegalArgumentException("stage state is required");
        }
        Path parent = file.getParent();
        if (parent == null) {
            throw new IOException("Stage state requires a parent directory: " + file);
        }
        Files.createDirectories(parent);
        if (Files.exists(file)) {
            load(); // Preserve corrupt/future evidence instead of replacing it.
        }
        byte[] bytes = encode(state).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Stage state serialization exceeds 2 MiB: " + file);
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
        } finally {
            deleteQuietly(temporary);
            deleteQuietly(backupTemporary);
        }
    }

    static String encode(StageState state) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", state.schemaVersion());
        JsonObject zones = new JsonObject();
        state.zones().forEach((id, zone) -> zones.add(id, encodeZone(zone)));
        root.add("zones", zones);
        if (state.operation() == null) {
            root.add("operation", com.google.gson.JsonNull.INSTANCE);
        } else {
            root.add("operation", encodeOperation(state.operation()));
        }
        JsonArray history = new JsonArray();
        state.history().forEach(entry -> history.add(encodeAudit(entry)));
        root.add("history", history);
        root.addProperty("nextSequence", state.nextSequence());
        return root.toString();
    }

    static StageState decode(String source) {
        JsonElement parsed;
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            parsed = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("unexpected trailing JSON content");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("unable to read stage JSON", exception);
        }
        JsonObject root = object(parsed, "root", Set.of("schemaVersion", "zones", "operation", "history", "nextSequence"));
        int schema = integer(required(root, "schemaVersion"), "schemaVersion");
        if (schema != StageState.SCHEMA_VERSION) {
            throw new IllegalArgumentException("stage schema " + schema + " is not supported");
        }
        Map<String, StageZone> zones = new LinkedHashMap<>();
        JsonObject encodedZones = object(required(root, "zones"), "zones", null);
        encodedZones.entrySet().forEach(entry -> zones.put(entry.getKey(), decodeZone(entry.getValue())));
        StageOperation operation = root.get("operation").isJsonNull() ? null : decodeOperation(root.get("operation"));
        List<StageAuditEntry> history = new ArrayList<>();
        for (JsonElement entry : array(required(root, "history"), "history")) {
            history.add(decodeAudit(entry));
        }
        return new StageState(schema, zones, operation, history, longNumber(required(root, "nextSequence"), "nextSequence"));
    }

    private static JsonObject encodeZone(StageZone zone) {
        JsonObject value = new JsonObject();
        value.addProperty("id", zone.id());
        value.addProperty("dimension", zone.dimension());
        value.add("entries", encodeEntries(zone.entries()));
        value.add("definedBy", encodeActor(zone.definedBy()));
        value.addProperty("definedAt", zone.definedAt());
        return value;
    }

    private static StageZone decodeZone(JsonElement element) {
        JsonObject value = object(element, "zone", Set.of("id", "dimension", "entries", "definedBy", "definedAt"));
        return new StageZone(string(required(value, "id"), "zone id"),
                string(required(value, "dimension"), "dimension"), decodeEntries(required(value, "entries")),
                decodeActor(required(value, "definedBy")), string(required(value, "definedAt"), "definedAt"));
    }

    private static JsonObject encodeOperation(StageOperation operation) {
        JsonObject value = new JsonObject();
        value.addProperty("operationId", operation.operationId());
        value.addProperty("zoneId", operation.zoneId());
        value.addProperty("dimension", operation.dimension());
        value.add("entries", encodeEntries(operation.entries()));
        value.add("actor", encodeActor(operation.actor()));
        value.addProperty("startedAt", operation.startedAt());
        value.addProperty("direction", operation.direction().name());
        value.addProperty("status", operation.status().name());
        value.addProperty("progress", operation.progress());
        value.addProperty("detail", operation.detail());
        return value;
    }

    private static StageOperation decodeOperation(JsonElement element) {
        JsonObject value = object(element, "operation", Set.of("operationId", "zoneId", "dimension", "entries",
                "actor", "startedAt", "direction", "status", "progress", "detail"));
        return new StageOperation(string(required(value, "operationId"), "operationId"),
                string(required(value, "zoneId"), "zoneId"), string(required(value, "dimension"), "dimension"),
                decodeEntries(required(value, "entries")), decodeActor(required(value, "actor")),
                string(required(value, "startedAt"), "startedAt"),
                enumValue(StageDirection.class, required(value, "direction"), "direction"),
                enumValue(StageOperationStatus.class, required(value, "status"), "status"),
                integer(required(value, "progress"), "progress"), string(required(value, "detail"), "detail"));
    }

    private static JsonArray encodeEntries(List<StageEntry> entries) {
        JsonArray values = new JsonArray();
        for (StageEntry entry : entries) {
            JsonObject value = new JsonObject();
            value.addProperty("x", entry.position().x());
            value.addProperty("y", entry.position().y());
            value.addProperty("z", entry.position().z());
            value.add("before", encodeBlock(entry.before()));
            value.add("after", encodeBlock(entry.after()));
            values.add(value);
        }
        return values;
    }

    private static List<StageEntry> decodeEntries(JsonElement element) {
        List<StageEntry> entries = new ArrayList<>();
        for (JsonElement encoded : array(element, "entries")) {
            JsonObject value = object(encoded, "entry", Set.of("x", "y", "z", "before", "after"));
            entries.add(new StageEntry(new StagePosition(integer(required(value, "x"), "x"),
                    integer(required(value, "y"), "y"), integer(required(value, "z"), "z")),
                    decodeBlock(required(value, "before")), decodeBlock(required(value, "after"))));
        }
        return entries;
    }

    private static JsonObject encodeBlock(StageBlockState state) {
        JsonObject value = new JsonObject();
        value.addProperty("id", state.id());
        JsonObject properties = new JsonObject();
        state.properties().forEach(properties::addProperty);
        value.add("properties", properties);
        return value;
    }

    private static StageBlockState decodeBlock(JsonElement element) {
        JsonObject value = object(element, "block state", Set.of("id", "properties"));
        JsonObject properties = object(required(value, "properties"), "properties", null);
        Map<String, String> decoded = new LinkedHashMap<>();
        properties.entrySet().forEach(entry -> decoded.put(entry.getKey(), string(entry.getValue(), "property value")));
        return new StageBlockState(string(required(value, "id"), "block id"), decoded);
    }

    private static JsonObject encodeActor(Actor actor) {
        JsonObject value = new JsonObject();
        value.addProperty("name", actor.name());
        if (actor.uuid() != null) {
            value.addProperty("uuid", actor.uuid());
        }
        return value;
    }

    private static Actor decodeActor(JsonElement element) {
        JsonObject value = object(element, "actor", null);
        rejectUnknown(value, Set.of("name", "uuid"), "actor");
        String uuid = value.has("uuid") && !value.get("uuid").isJsonNull() ? string(value.get("uuid"), "actor uuid") : null;
        return new Actor(string(required(value, "name"), "actor name"), uuid);
    }

    private static JsonObject encodeAudit(StageAuditEntry entry) {
        JsonObject value = new JsonObject();
        value.addProperty("sequence", entry.sequence());
        value.addProperty("timestamp", entry.timestamp());
        value.add("actor", encodeActor(entry.actor()));
        value.addProperty("action", entry.action());
        value.addProperty("target", entry.target());
        value.addProperty("operationId", entry.operationId());
        value.addProperty("detail", entry.detail());
        return value;
    }

    private static StageAuditEntry decodeAudit(JsonElement element) {
        JsonObject value = object(element, "audit", Set.of("sequence", "timestamp", "actor", "action", "target", "operationId", "detail"));
        return new StageAuditEntry(longNumber(required(value, "sequence"), "sequence"),
                string(required(value, "timestamp"), "timestamp"), decodeActor(required(value, "actor")),
                string(required(value, "action"), "action"), string(required(value, "target"), "target"),
                string(required(value, "operationId"), "operationId"), string(required(value, "detail"), "detail"));
    }

    private static void forceWrite(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static void force(Path target) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) { channel.force(true); }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic replacement is not supported for stage state: " + target, exception);
        }
    }

    private static void deleteQuietly(Path value) {
        if (value == null) return;
        try { Files.deleteIfExists(value); } catch (IOException ignored) { }
    }

    private static JsonElement required(JsonObject value, String field) {
        if (!value.has(field) || value.get(field).isJsonNull()) throw new IllegalArgumentException("missing " + field);
        return value.get(field);
    }

    private static JsonObject object(JsonElement value, String field, Set<String> fields) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(field + " must be an object");
        JsonObject result = value.getAsJsonObject();
        if (fields != null) rejectUnknown(result, fields, field);
        return result;
    }

    private static void rejectUnknown(JsonObject value, Set<String> fields, String label) {
        for (String key : value.keySet()) {
            if (!fields.contains(key)) throw new IllegalArgumentException(label + " has unknown field " + key);
        }
    }

    private static JsonArray array(JsonElement value, String field) {
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(field + " must be an array");
        return value.getAsJsonArray();
    }

    private static String string(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException(field + " must be a string");
        return value.getAsString();
    }

    private static int integer(JsonElement value, String field) {
        long number = longNumber(value, field);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw new IllegalArgumentException(field + " is outside integer range");
        return (int) number;
    }

    private static long longNumber(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(field + " must be an integer");
        String text = value.getAsString();
        if (!text.matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException(field + " must be an integer");
        try { return Long.parseLong(text); } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " is outside long range", exception);
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, JsonElement value, String field) {
        try { return Enum.valueOf(type, string(value, field)); } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown " + field, exception);
        }
    }
}
