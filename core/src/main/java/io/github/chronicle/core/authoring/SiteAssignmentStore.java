package io.github.chronicle.core.authoring;

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
import java.util.*;

/** Strict, atomic schema-1 persistence for map-site requirement assignments. */
public final class SiteAssignmentStore implements SiteAssignmentRepository {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_FILE_BYTES = 1024L * 1024;
    private static final int MAX_JSON_DEPTH = 16;
    private final Path file;
    private Boolean observedPresent;
    private byte[] observedFingerprint;

    public SiteAssignmentStore(Path file) {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException("assignment file is required");
        this.file = file.toAbsolutePath().normalize();
    }

    /** True only immediately after load found no file, allowing one-time legacy adoption. */
    public synchronized boolean wasAbsent() { return Boolean.FALSE.equals(observedPresent); }

    @Override public synchronized SiteAssignments load() throws IOException {
        if (!Files.exists(file)) {
            observedPresent = false; observedFingerprint = null;
            return SiteAssignments.empty();
        }
        byte[] bytes = readCurrent();
        try {
            SiteAssignments result = decode(new String(bytes, StandardCharsets.UTF_8));
            observedPresent = true; observedFingerprint = fingerprint(bytes);
            return result;
        } catch (RuntimeException exception) {
            throw new IOException("map assignments are invalid and were not replaced: " + file + " (" + exception.getMessage() + ")", exception);
        }
    }

    @Override public synchronized void save(SiteAssignments assignments) throws IOException {
        Objects.requireNonNull(assignments, "assignments are required");
        Files.createDirectories(file.getParent());
        boolean present = Files.exists(file);
        if (observedPresent != null && observedPresent != present) throw new IOException("assignment file presence changed outside this store; refusing to overwrite " + file);
        byte[] old = null;
        if (present) {
            old = readCurrent();
            if (observedFingerprint != null && !MessageDigest.isEqual(observedFingerprint, fingerprint(old))) {
                throw new IOException("assignment file changed outside this store; refusing to overwrite " + file);
            }
            try { decode(new String(old, StandardCharsets.UTF_8)); }
            catch (RuntimeException exception) { throw new IOException("map assignments are invalid and were not replaced: " + file, exception); }
        }
        byte[] encoded = encode(assignments).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_FILE_BYTES) throw new IOException("map assignment serialization exceeds 1 MiB limit: " + file);
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        Path backupTemporary = null;
        try {
            forceWrite(temporary, encoded);
            if (old != null) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                backupTemporary = Files.createTempFile(file.getParent(), backup.getFileName().toString(), ".tmp");
                forceWrite(backupTemporary, old);
                moveReplace(backupTemporary, backup); backupTemporary = null;
            }
            moveReplace(temporary, file);
            observedPresent = true; observedFingerprint = fingerprint(encoded);
        } catch (IOException exception) {
            throw new IOException("unable to save map assignments to " + file + "; in-memory state was not changed", exception);
        } finally { delete(temporary); delete(backupTemporary); }
    }

    private byte[] readCurrent() throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("assignment path is not a regular file: " + file);
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("assignment file exceeds 1 MiB limit: " + file);
        return Files.readAllBytes(file);
    }
    private static void forceWrite(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
        }
    }
    private static void moveReplace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException exception) { throw new IOException("atomic replacement is not supported for map assignments: " + target, exception); }
    }
    private static void delete(Path path) { if (path != null) try { Files.deleteIfExists(path); } catch (IOException ignored) { } }
    private static byte[] fingerprint(byte[] bytes) throws IOException {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException exception) { throw new IOException("SHA-256 is unavailable", exception); }
    }

    private static SiteAssignments decode(String source) {
        strict(source);
        JsonObject root = object(parse(source), "root");
        exact(root, Set.of("schemaVersion", "revision", "bindings"), "root");
        if (integer(required(root, "schemaVersion"), "schemaVersion") != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported map assignment schema version");
        long revision = number(required(root, "revision"), "revision");
        JsonArray array = array(required(root, "bindings"), "bindings");
        if (array.size() > SiteAssignments.MAX_BINDINGS) throw new IllegalArgumentException("too many map assignments");
        Map<String, SiteAssignments.Binding> bindings = new LinkedHashMap<>();
        for (JsonElement element : array) {
            JsonObject binding = object(element, "binding");
            exact(binding, Set.of("requirementId", "siteId", "siteRevision"), "binding");
            SiteAssignments.Binding decoded = new SiteAssignments.Binding(string(required(binding, "requirementId"), "requirementId"),
                    string(required(binding, "siteId"), "siteId"), number(required(binding, "siteRevision"), "siteRevision"));
            if (bindings.putIfAbsent(decoded.requirementId(), decoded) != null) throw new IllegalArgumentException("duplicate assignment requirement: " + decoded.requirementId());
        }
        return new SiteAssignments(revision, bindings);
    }
    private static String encode(SiteAssignments assignments) {
        JsonObject root = new JsonObject(); root.addProperty("schemaVersion", SCHEMA_VERSION); root.addProperty("revision", assignments.revision());
        JsonArray array = new JsonArray();
        for (SiteAssignments.Binding binding : assignments.bindings().values()) {
            JsonObject item = new JsonObject(); item.addProperty("requirementId", binding.requirementId()); item.addProperty("siteId", binding.siteId()); item.addProperty("siteRevision", binding.siteRevision()); array.add(item);
        }
        root.add("bindings", array); return root.toString();
    }
    private static JsonElement parse(String source) {
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT); JsonElement value = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("unexpected trailing JSON content");
            return value;
        } catch (IOException exception) { throw new IllegalArgumentException("unable to read map assignment JSON", exception); }
    }
    private static void strict(String source) {
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT); strictValue(reader, 0); if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("unexpected trailing JSON content");
        } catch (IOException exception) { throw new IllegalArgumentException("unable to read map assignment JSON", exception); }
    }
    private static void strictValue(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_JSON_DEPTH) throw new IllegalArgumentException("map assignment JSON nesting exceeds " + MAX_JSON_DEPTH);
        switch (reader.peek()) {
            case BEGIN_OBJECT -> { reader.beginObject(); Set<String> names = new HashSet<>(); while (reader.hasNext()) { String name = reader.nextName(); if (!names.add(name)) throw new IllegalArgumentException("duplicate JSON member: " + name); strictValue(reader, depth + 1); } reader.endObject(); }
            case BEGIN_ARRAY -> { reader.beginArray(); while (reader.hasNext()) strictValue(reader, depth + 1); reader.endArray(); }
            case STRING, NUMBER -> reader.nextString(); case BOOLEAN -> reader.nextBoolean(); case NULL -> reader.nextNull(); default -> throw new IllegalArgumentException("unexpected JSON token: " + reader.peek());
        }
    }
    private static void exact(JsonObject object, Set<String> fields, String name) { if (!object.keySet().equals(fields)) throw new IllegalArgumentException(name + " fields do not match schema"); }
    private static JsonElement required(JsonObject object, String name) { if (!object.has(name) || object.get(name).isJsonNull()) throw new IllegalArgumentException("missing " + name); return object.get(name); }
    private static JsonObject object(JsonElement value, String name) { if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(name + " must be an object"); return value.getAsJsonObject(); }
    private static JsonArray array(JsonElement value, String name) { if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(name + " must be an array"); return value.getAsJsonArray(); }
    private static String string(JsonElement value, String name) { if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(name + " must be a string"); return value.getAsString(); }
    private static int integer(JsonElement value, String name) { long result = number(value, name); if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw new IllegalArgumentException(name + " is outside integer range"); return (int) result; }
    private static long number(JsonElement value, String name) { if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException(name + " must be an integer"); try { return Long.parseLong(value.getAsString()); } catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " is outside long range", exception); } }
}
