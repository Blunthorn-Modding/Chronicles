package io.github.chronicle.core.spawn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict, bounded, atomic schema-1 storage for automatic player spawn policies. */
public final class AutomaticSpawnStore implements AutomaticSpawnRepository {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_JSON_DEPTH = 16;
    private final Path file;
    private Boolean observedPresent;
    private byte[] observedFingerprint;

    public AutomaticSpawnStore(Path file) {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException("automatic spawn file is required");
        this.file = file.toAbsolutePath().normalize();
    }

    @Override public synchronized AutomaticSpawnState load() throws IOException {
        if (!Files.exists(file)) {
            observedPresent = false;
            observedFingerprint = null;
            return AutomaticSpawnState.empty();
        }
        byte[] bytes = current();
        try {
            AutomaticSpawnState state = decode(new String(bytes, StandardCharsets.UTF_8));
            observedPresent = true;
            observedFingerprint = fingerprint(bytes);
            return state;
        } catch (RuntimeException exception) {
            throw invalid(exception);
        }
    }

    @Override public synchronized void save(AutomaticSpawnState state) throws IOException {
        if (state == null) throw new IllegalArgumentException("automatic spawn state is required");
        Files.createDirectories(file.getParent());
        boolean present = Files.exists(file);
        if (observedPresent != null && observedPresent != present) {
            throw new IOException("automatic spawn file presence changed outside this store; refusing to overwrite " + file);
        }
        byte[] old = null;
        if (present) {
            old = current();
            if (observedFingerprint != null && !MessageDigest.isEqual(observedFingerprint, fingerprint(old))) {
                throw new IOException("automatic spawn file changed outside this store; refusing to overwrite " + file);
            }
            try { decode(new String(old, StandardCharsets.UTF_8)); }
            catch (RuntimeException exception) { throw invalid(exception); }
        }
        byte[] next = encode(state).getBytes(StandardCharsets.UTF_8);
        if (next.length > MAX_FILE_BYTES) throw new IOException("automatic spawn serialization exceeds 2 MiB limit: " + file);
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        Path backupTemporary = null;
        try {
            forceWrite(temporary, next);
            if (old != null) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                backupTemporary = Files.createTempFile(file.getParent(), backup.getFileName().toString(), ".tmp");
                forceWrite(backupTemporary, old);
                moveReplace(backupTemporary, backup);
                backupTemporary = null;
            }
            moveReplace(temporary, file);
            observedPresent = true;
            observedFingerprint = fingerprint(next);
        } finally {
            delete(temporary);
            delete(backupTemporary);
        }
    }

    private IOException invalid(RuntimeException exception) {
        return new IOException("automatic spawn data is invalid and was not replaced: " + file + " (" + exception.getMessage() + ")", exception);
    }

    private byte[] current() throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("automatic spawn path is not a regular file: " + file);
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("automatic spawn file exceeds 2 MiB limit: " + file);
        return Files.readAllBytes(file);
    }

    private static void forceWrite(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException exception) { throw new IOException("atomic replacement is not supported for automatic spawn data: " + target, exception); }
    }

    private static void delete(Path target) { if (target != null) try { Files.deleteIfExists(target); } catch (IOException ignored) { } }

    private static byte[] fingerprint(byte[] bytes) throws IOException {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException exception) { throw new IOException("SHA-256 unavailable", exception); }
    }

    private static AutomaticSpawnState decode(String json) {
        validateStrictJson(json);
        JsonElement parsed;
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            parsed = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("unexpected trailing JSON content");
        } catch (IOException exception) {
            throw new IllegalArgumentException("unable to read automatic spawn JSON", exception);
        }
        JsonObject root = object(parsed, "root");
        fields(root, Set.of("schemaVersion", "revision", "policies", "arrivals"), "root");
        if (integer(required(root, "schemaVersion"), "schemaVersion") != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported automatic spawn schema version");
        JsonArray policies = array(required(root, "policies"), "policies");
        JsonArray arrivals = array(required(root, "arrivals"), "arrivals");
        if (policies.size() > AutomaticSpawnState.MAX_POLICIES) throw new IllegalArgumentException("too many automatic spawn policies");
        if (arrivals.size() > AutomaticSpawnState.MAX_ARRIVALS) throw new IllegalArgumentException("too many automatic spawn arrivals");
        Map<UUID, AutomaticSpawnState.Policy> decodedPolicies = new LinkedHashMap<>();
        for (JsonElement item : policies) {
            JsonObject policy = object(item, "policy");
            fields(policy, Set.of("player", "arrival", "respawn"), "policy");
            UUID player = uuid(required(policy, "player"), "policy player");
            AutomaticSpawnState.Policy value = new AutomaticSpawnState.Policy(bindingOrNull(policy.get("arrival"), "policy arrival"), bindingOrNull(policy.get("respawn"), "policy respawn"));
            if (decodedPolicies.putIfAbsent(player, value) != null) throw new IllegalArgumentException("duplicate automatic spawn policy player: " + player);
        }
        Map<UUID, AutomaticSpawnState.Arrival> decodedArrivals = new LinkedHashMap<>();
        for (JsonElement item : arrivals) {
            JsonObject arrival = object(item, "arrival");
            fields(arrival, Set.of("player", "purpose", "status", "detail"), "arrival");
            UUID player = uuid(required(arrival, "player"), "arrival player");
            AutomaticSpawnState.Arrival value = new AutomaticSpawnState.Arrival(string(required(arrival, "purpose"), "arrival purpose"),
                    status(required(arrival, "status")), string(required(arrival, "detail"), "arrival detail"));
            if (decodedArrivals.putIfAbsent(player, value) != null) throw new IllegalArgumentException("duplicate automatic spawn arrival player: " + player);
        }
        return new AutomaticSpawnState(number(required(root, "revision"), "revision"), decodedPolicies, decodedArrivals);
    }

    private static String encode(AutomaticSpawnState state) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("revision", state.revision());
        JsonArray policies = new JsonArray();
        state.policies().forEach((player, policy) -> {
            JsonObject encoded = new JsonObject();
            encoded.addProperty("player", player.toString());
            encoded.add("arrival", binding(policy.arrival()));
            encoded.add("respawn", binding(policy.respawn()));
            policies.add(encoded);
        });
        JsonArray arrivals = new JsonArray();
        state.arrivals().forEach((player, arrival) -> {
            JsonObject encoded = new JsonObject();
            encoded.addProperty("player", player.toString());
            encoded.addProperty("purpose", arrival.purpose());
            encoded.addProperty("status", arrival.status().name());
            encoded.addProperty("detail", arrival.detail());
            arrivals.add(encoded);
        });
        root.add("policies", policies);
        root.add("arrivals", arrivals);
        return root.toString();
    }

    private static JsonElement binding(SpawnBinding value) {
        if (value == null) return com.google.gson.JsonNull.INSTANCE;
        JsonObject encoded = new JsonObject();
        encoded.addProperty("purpose", value.purpose());
        encoded.addProperty("locationId", value.locationId());
        encoded.addProperty("locationRevision", value.locationRevision());
        return encoded;
    }

    private static SpawnBinding bindingOrNull(JsonElement value, String name) {
        if (value == null || value.isJsonNull()) return null;
        JsonObject binding = object(value, name);
        fields(binding, Set.of("purpose", "locationId", "locationRevision"), name);
        return new SpawnBinding(string(required(binding, "purpose"), name + " purpose"), string(required(binding, "locationId"), name + " location ID"), number(required(binding, "locationRevision"), name + " location revision"));
    }

    private static AutomaticSpawnState.Status status(JsonElement value) {
        String text = string(value, "arrival status");
        try { return AutomaticSpawnState.Status.valueOf(text); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("unknown arrival status: " + text, exception); }
    }

    private static UUID uuid(JsonElement value, String name) {
        String text = string(value, name);
        try {
            UUID result = UUID.fromString(text);
            if (!result.toString().equals(text)) throw new IllegalArgumentException("non-canonical UUID");
            return result;
        } catch (IllegalArgumentException exception) { throw new IllegalArgumentException(name + " must be a canonical UUID", exception); }
    }

    private static void validateStrictJson(String json) {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            validateValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("unexpected trailing JSON content");
        } catch (IOException exception) {
            throw new IllegalArgumentException("unable to read automatic spawn JSON", exception);
        }
    }

    private static void validateValue(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_JSON_DEPTH) throw new IllegalArgumentException("automatic spawn JSON nesting exceeds " + MAX_JSON_DEPTH);
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) throw new IllegalArgumentException("duplicate JSON member: " + name);
                    validateValue(reader, depth + 1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> { reader.beginArray(); while (reader.hasNext()) validateValue(reader, depth + 1); reader.endArray(); }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException("unexpected JSON token: " + reader.peek());
        }
    }

    private static JsonObject object(JsonElement value, String name) { if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(name + " must be an object"); return value.getAsJsonObject(); }
    private static JsonArray array(JsonElement value, String name) { if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(name + " must be an array"); return value.getAsJsonArray(); }
    private static JsonElement required(JsonObject object, String name) { if (!object.has(name) || object.get(name).isJsonNull()) throw new IllegalArgumentException("missing " + name); return object.get(name); }
    private static void fields(JsonObject object, Set<String> allowed, String name) { if (!object.keySet().equals(allowed)) throw new IllegalArgumentException(name + " fields do not match schema"); }
    private static String string(JsonElement value, String name) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(name + " must be a string"); return value.getAsString(); }
    private static int integer(JsonElement value, String name) { long result = number(value, name); if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw new IllegalArgumentException(name + " outside integer range"); return (int) result; }
    private static long number(JsonElement value, String name) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException(name + " must be an integer");
        try { return Long.parseLong(value.getAsString()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " outside long range", exception); }
    }
}
