package io.github.chronicle.core.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Strict schema-1 storage for the campaign-editable loot-type configuration. */
public final class LootConfig {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    /** The exact disk bytes and effective types accepted by the last successful load/save. */
    private byte[] observedBytes;
    private Map<String, LootType> observedTypes;

    public LootConfig(Path file) {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException("loot config file is required");
        this.file = file.toAbsolutePath().normalize();
    }

    /**
     * Creates the initial file from campaign defaults, or overlays complete configured types on
     * those defaults. Existing invalid files are never changed.
     */
    /** Profile-owned catalogs are complete overrides: never recreate or merge removed definitions. */
    public synchronized Map<String,LootType> loadExisting()throws IOException{
        byte[] bytes=current();try{return observe(bytes,decode(new String(bytes,StandardCharsets.UTF_8)));}
        catch(RuntimeException error){throw new IOException("Existing profile loot catalog is invalid and was preserved: "+file,error);}
    }
    public synchronized Map<String, LootType> loadOrCreate(Map<String, LootType> defaults) throws IOException {
        Map<String, LootType> validatedDefaults = defaults(defaults);
        if (!Files.exists(file)) {
            byte[] initial = writeInitial(validatedDefaults);
            return observe(initial, validatedDefaults);
        }
        byte[] bytes = current();
        try {
            Map<String, LootType> configured = decode(new String(bytes, StandardCharsets.UTF_8));
            TreeMap<String, LootType> merged = new TreeMap<>(validatedDefaults);
            merged.putAll(configured);
            if (merged.size() > LootCatalog.MAX_TYPES) throw new IllegalArgumentException("merged loot types exceed " + LootCatalog.MAX_TYPES);
            return observe(bytes, merged);
        } catch (RuntimeException exception) {
            throw new IOException("loot config is invalid and was not replaced: " + file + " (" + exception.getMessage() + ")", exception);
        }
    }

    /**
     * Replaces one complete effective type after an optimistic-concurrency check. The complete
     * effective catalog is written so custom types and all unrelated configured definitions stay
     * intact. Callers must perform Minecraft registry/component validation before this method.
     */
    public synchronized Map<String, LootType> replaceType(LootType replacement, LootType expected) throws IOException {
        if (replacement == null || expected == null) throw new IllegalArgumentException("replacement and expected loot types are required");
        if (!replacement.id().equals(expected.id())) throw new IllegalArgumentException("loot type id cannot change while editing");
        if (observedTypes == null || observedBytes == null) throw new IllegalStateException("Loot configuration must be loaded before editing");
        if (!expected.equals(observedTypes.get(expected.id()))) throw new IllegalStateException("Loot table changed since this editor opened; reopen it before saving");

        return writeType(replacement);
    }

    /** Adds a new effective type without allowing a default or custom type to be overwritten. */
    public synchronized Map<String, LootType> createType(LootType addition) throws IOException {
        if (addition == null) throw new IllegalArgumentException("new loot type is required");
        if (observedTypes == null || observedBytes == null) throw new IllegalStateException("Loot configuration must be loaded before editing");
        if (observedTypes.containsKey(addition.id())) throw new IllegalStateException("A loot table with this ID already exists; choose another ID.");
        return writeType(addition);
    }

    private Map<String, LootType> writeType(LootType replacement) throws IOException {
        byte[] current = current();
        if (!MessageDigest.isEqual(observedBytes, current)) {
            throw new IOException("loot config changed outside Chronicle; reload it before saving " + file);
        }
        try {
            // Revalidate the exact old bytes before preserving them as the rollback copy.
            decode(new String(current, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new IOException("loot config is invalid and was not replaced: " + file, exception);
        }

        TreeMap<String, LootType> candidate = new TreeMap<>(observedTypes);
        candidate.put(replacement.id(), replacement);
        if (candidate.size() > LootCatalog.MAX_TYPES) throw new IllegalArgumentException("loot types exceed " + LootCatalog.MAX_TYPES);
        Map<String, LootType> immutableCandidate = immutable(candidate);
        byte[] encoded = PRETTY_GSON.toJson(encode(immutableCandidate)).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_FILE_BYTES) throw new IOException("loot config serialization exceeds 2 MiB limit: " + file);
        replaceDurably(current, encoded);
        return observe(encoded, immutableCandidate);
    }

    private Map<String, LootType> defaults(Map<String, LootType> defaults) {
        if (defaults == null) throw new IllegalArgumentException("loot defaults are required");
        if (defaults.size() > LootCatalog.MAX_TYPES) throw new IllegalArgumentException("loot defaults exceed " + LootCatalog.MAX_TYPES);
        TreeMap<String, LootType> result = new TreeMap<>();
        for (Map.Entry<String, LootType> entry : defaults.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || !entry.getKey().equals(entry.getValue().id())) {
                throw new IllegalArgumentException("loot defaults must map each type id to the matching loot type");
            }
            if (result.putIfAbsent(entry.getKey(), entry.getValue()) != null) throw new IllegalArgumentException("duplicate loot default: " + entry.getKey());
        }
        return immutable(result);
    }

    private byte[] current() throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("loot config path is not a regular file: " + file);
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("loot config exceeds 2 MiB limit: " + file);
        return Files.readAllBytes(file);
    }

    private byte[] writeInitial(Map<String, LootType> defaults) throws IOException {
        Path parent = parent();
        Files.createDirectories(parent);
        byte[] content = PRETTY_GSON.toJson(encode(defaults)).getBytes(StandardCharsets.UTF_8);
        if (content.length > MAX_FILE_BYTES) throw new IOException("initial loot config exceeds 2 MiB limit: " + file);
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try {
            forceWrite(temporary, content);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException exception) {
                throw new IOException("loot config appeared while creating it; refusing to overwrite " + file, exception);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic creation is not supported for loot config: " + file, exception);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return content;
    }

    private void replaceDurably(byte[] old, byte[] encoded) throws IOException {
        Path parent = parent();
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        Path backupTemporary = null;
        try {
            forceWrite(temporary, encoded);
            Path backup = file.resolveSibling(file.getFileName() + ".bak");
            backupTemporary = Files.createTempFile(parent, backup.getFileName().toString(), ".tmp");
            forceWrite(backupTemporary, old);
            moveReplace(backupTemporary, backup);
            backupTemporary = null;
            moveReplace(temporary, file);
        } catch (IOException exception) {
            throw new IOException("unable to save loot config; published loot types were not changed", exception);
        } finally {
            Files.deleteIfExists(temporary);
            if (backupTemporary != null) Files.deleteIfExists(backupTemporary);
        }
    }

    private Map<String, LootType> observe(byte[] bytes, Map<String, LootType> types) {
        observedBytes = bytes.clone();
        observedTypes = immutable(types);
        return observedTypes;
    }

    private Path parent() throws IOException {
        Path parent = file.getParent();
        if (parent == null) throw new IOException("loot config has no parent directory: " + file);
        return parent;
    }

    private static void forceWrite(Path target, byte[] content) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("atomic replacement is not supported for loot config: " + target, exception);
        }
    }

    private static Map<String, LootType> decode(String source) {
        JsonObject root = object(LootJson.parse(source, "loot config"), "root");
        exactFields(root, Set.of("schemaVersion", "types"), "root");
        if (integer(required(root, "schemaVersion"), "schemaVersion") != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported loot config schemaVersion");
        JsonArray types = array(required(root, "types"), "types");
        if (types.size() > LootCatalog.MAX_TYPES) throw new IllegalArgumentException("types may contain at most " + LootCatalog.MAX_TYPES + " entries");
        Map<String, LootType> result = new LinkedHashMap<>();
        for (int i = 0; i < types.size(); i++) {
            LootType type = decodeType(object(types.get(i), "types[" + i + "]"), "types[" + i + "]");
            if (result.putIfAbsent(type.id(), type) != null) throw new IllegalArgumentException("types contains duplicate id: " + type.id());
        }
        return result;
    }

    private static LootType decodeType(JsonObject object, String field) {
        return LootTableDraftJson.decodeObject(object, field);
    }

    private static JsonObject encode(Map<String, LootType> types) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray values = new JsonArray();
        for (LootType type : types.values()) values.add(encodeType(type));
        root.add("types", values);
        return root;
    }

    private static JsonObject encodeType(LootType type) {
        return LootTableDraftJson.encodeObject(type);
    }

    private static Map<String, LootType> immutable(Map<String, LootType> types) { return Collections.unmodifiableMap(new TreeMap<>(types)); }
    private static JsonObject object(JsonElement value, String field) { if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(field + " must be an object"); return value.getAsJsonObject(); }
    private static JsonArray array(JsonElement value, String field) { if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(field + " must be an array"); return value.getAsJsonArray(); }
    private static JsonElement required(JsonObject object, String field) { if (!object.has(field) || object.get(field).isJsonNull()) throw new IllegalArgumentException("missing " + field); return object.get(field); }
    private static void exactFields(JsonObject object, Set<String> allowed, String field) {
        if (!object.keySet().equals(allowed)) {
            List<String> unknown = object.keySet().stream().filter(name -> !allowed.contains(name)).sorted().toList();
            List<String> missing = allowed.stream().filter(name -> !object.has(name)).sorted().toList();
            throw new IllegalArgumentException(field + " fields do not match schema (unknown=" + unknown + ", missing=" + missing + ")");
        }
    }
    private static int integer(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException(field + " must be an integer");
        try { return Math.toIntExact(Long.parseLong(value.getAsString())); } catch (NumberFormatException | ArithmeticException exception) { throw new IllegalArgumentException(field + " is outside integer range", exception); }
    }
}
