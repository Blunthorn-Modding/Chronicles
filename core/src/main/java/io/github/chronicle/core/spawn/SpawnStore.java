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

/** Strict, bounded, atomic schema-1 storage for reusable spawn bindings. */
public final class SpawnStore implements SpawnRepository {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_FILE_BYTES = 256 * 1024;
    private final Path file;
    private Boolean observedPresent;
    private byte[] observedFingerprint;

    public SpawnStore(Path file) {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException("spawn file is required");
        this.file = file.toAbsolutePath().normalize();
    }
    @Override public synchronized SpawnRegistry load() throws IOException {
        if (!Files.exists(file)) { observedPresent = false; observedFingerprint = null; return SpawnRegistry.empty(); }
        byte[] bytes = current();
        try {
            SpawnRegistry result = decode(new String(bytes, StandardCharsets.UTF_8));
            observedPresent = true; observedFingerprint = fingerprint(bytes); return result;
        } catch (RuntimeException exception) {
            throw new IOException("spawn data is invalid and was not replaced: " + file + " (" + exception.getMessage() + ")", exception);
        }
    }
    @Override public synchronized void save(SpawnRegistry registry) throws IOException {
        if (registry == null) throw new IllegalArgumentException("spawn registry is required");
        Files.createDirectories(file.getParent());
        boolean present = Files.exists(file);
        if (observedPresent != null && observedPresent != present) throw new IOException("spawn file presence changed outside this store; refusing to overwrite " + file);
        byte[] old = null;
        if (present) {
            old = current();
            if (observedFingerprint != null && !MessageDigest.isEqual(observedFingerprint, fingerprint(old))) throw new IOException("spawn file changed outside this store; refusing to overwrite " + file);
            try { decode(new String(old, StandardCharsets.UTF_8)); }
            catch (RuntimeException exception) { throw new IOException("spawn data is invalid and was not replaced: " + file, exception); }
        }
        byte[] next = encode(registry).getBytes(StandardCharsets.UTF_8);
        if (next.length > MAX_FILE_BYTES) throw new IOException("spawn serialization exceeds 256 KiB limit: " + file);
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        Path backupTemporary = null;
        try {
            forceWrite(temporary, next);
            if (old != null) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                backupTemporary = Files.createTempFile(file.getParent(), backup.getFileName().toString(), ".tmp");
                forceWrite(backupTemporary, old); moveReplace(backupTemporary, backup); backupTemporary = null;
            }
            moveReplace(temporary, file);
            observedPresent = true; observedFingerprint = fingerprint(next);
        } finally { delete(temporary); delete(backupTemporary); }
    }
    private byte[] current() throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("spawn path is not a regular file: " + file);
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("spawn file exceeds 256 KiB limit: " + file);
        return Files.readAllBytes(file);
    }
    private static void forceWrite(Path file, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
        }
    }
    private static void moveReplace(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException exception) { throw new IOException("atomic replacement is not supported for spawn data: " + to, exception); }
    }
    private static void delete(Path target) { if (target != null) try { Files.deleteIfExists(target); } catch (IOException ignored) { } }
    private static byte[] fingerprint(byte[] bytes) throws IOException { try { return MessageDigest.getInstance("SHA-256").digest(bytes); } catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 unavailable", e); } }

    private static SpawnRegistry decode(String json) {
        JsonElement rootElement;
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT); strictValue(reader, 0); if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("unexpected trailing JSON content");
        } catch (IOException e) { throw new IllegalArgumentException("unable to read spawn JSON", e); }
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT); rootElement = JsonParser.parseReader(reader); if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("unexpected trailing JSON content");
        } catch (IOException e) { throw new IllegalArgumentException("unable to read spawn JSON", e); }
        JsonObject root = object(rootElement, "root"); fields(root, Set.of("schemaVersion", "revision", "bindings"), "root");
        if (integer(required(root, "schemaVersion"), "schemaVersion") != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported spawn schema version");
        JsonArray bindings = array(required(root, "bindings"), "bindings");
        if (bindings.size() > SpawnRegistry.MAX_BINDINGS) throw new IllegalArgumentException("too many spawn bindings");
        Map<String, SpawnBinding> result = new LinkedHashMap<>();
        for (JsonElement element : bindings) {
            JsonObject binding = object(element, "binding"); fields(binding, Set.of("purpose", "locationId", "locationRevision"), "binding");
            var value = new SpawnBinding(string(required(binding, "purpose"), "purpose"), string(required(binding, "locationId"), "locationId"), number(required(binding, "locationRevision"), "locationRevision"));
            if (result.putIfAbsent(value.purpose(), value) != null) throw new IllegalArgumentException("duplicate spawn purpose: " + value.purpose());
        }
        return new SpawnRegistry(number(required(root, "revision"), "revision"), result);
    }
    private static String encode(SpawnRegistry registry) {
        JsonObject root = new JsonObject(); root.addProperty("schemaVersion", SCHEMA_VERSION); root.addProperty("revision", registry.revision()); JsonArray list = new JsonArray();
        registry.bindings().values().forEach(binding -> { JsonObject o = new JsonObject(); o.addProperty("purpose", binding.purpose()); o.addProperty("locationId", binding.locationId()); o.addProperty("locationRevision", binding.locationRevision()); list.add(o); }); root.add("bindings", list); return root.toString();
    }
    private static void strictValue(JsonReader reader, int depth) throws IOException {
        if (depth > 16) throw new IllegalArgumentException("spawn JSON nesting exceeds 16");
        switch (reader.peek()) {
            case BEGIN_OBJECT -> { reader.beginObject(); Set<String> seen = new HashSet<>(); while (reader.hasNext()) { String name = reader.nextName(); if (!seen.add(name)) throw new IllegalArgumentException("duplicate JSON member: " + name); strictValue(reader, depth + 1); } reader.endObject(); }
            case BEGIN_ARRAY -> { reader.beginArray(); while (reader.hasNext()) strictValue(reader, depth + 1); reader.endArray(); }
            case STRING, NUMBER -> reader.nextString(); case BOOLEAN -> reader.nextBoolean(); case NULL -> reader.nextNull(); default -> throw new IllegalArgumentException("unexpected JSON token");
        }
    }
    private static JsonObject object(JsonElement e, String name) { if (e == null || !e.isJsonObject()) throw new IllegalArgumentException(name + " must be an object"); return e.getAsJsonObject(); }
    private static JsonArray array(JsonElement e, String name) { if (e == null || !e.isJsonArray()) throw new IllegalArgumentException(name + " must be an array"); return e.getAsJsonArray(); }
    private static JsonElement required(JsonObject o, String name) { if (!o.has(name) || o.get(name).isJsonNull()) throw new IllegalArgumentException("missing " + name); return o.get(name); }
    private static void fields(JsonObject o, Set<String> allowed, String name) { if (!o.keySet().equals(allowed)) throw new IllegalArgumentException(name + " fields do not match schema"); }
    private static String string(JsonElement e, String name) { if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(name + " must be a string"); return e.getAsString(); }
    private static int integer(JsonElement e, String name) { long n = number(e, name); if (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE) throw new IllegalArgumentException(name + " outside integer range"); return (int) n; }
    private static long number(JsonElement e, String name) { if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || !e.getAsString().matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException(name + " must be an integer"); try { return Long.parseLong(e.getAsString()); } catch (NumberFormatException x) { throw new IllegalArgumentException(name + " outside long range", x); } }
}
