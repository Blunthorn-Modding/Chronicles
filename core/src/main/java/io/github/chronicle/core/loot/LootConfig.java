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
import java.util.ArrayList;
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

    public LootConfig(Path file) {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException("loot config file is required");
        this.file = file.toAbsolutePath().normalize();
    }

    /**
     * Creates the initial file from campaign defaults, or overlays complete configured types on
     * those defaults. Existing invalid files are never changed.
     */
    public Map<String, LootType> loadOrCreate(Map<String, LootType> defaults) throws IOException {
        Map<String, LootType> validatedDefaults = defaults(defaults);
        if (!Files.exists(file)) {
            writeInitial(validatedDefaults);
            return validatedDefaults;
        }
        byte[] bytes = current();
        try {
            Map<String, LootType> configured = decode(new String(bytes, StandardCharsets.UTF_8));
            TreeMap<String, LootType> merged = new TreeMap<>(validatedDefaults);
            merged.putAll(configured);
            if (merged.size() > LootCatalog.MAX_TYPES) throw new IllegalArgumentException("merged loot types exceed " + LootCatalog.MAX_TYPES);
            return immutable(merged);
        } catch (RuntimeException exception) {
            throw new IOException("loot config is invalid and was not replaced: " + file + " (" + exception.getMessage() + ")", exception);
        }
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

    private void writeInitial(Map<String, LootType> defaults) throws IOException {
        Files.createDirectories(file.getParent());
        byte[] content = PRETTY_GSON.toJson(encode(defaults)).getBytes(StandardCharsets.UTF_8);
        if (content.length > MAX_FILE_BYTES) throw new IOException("initial loot config exceeds 2 MiB limit: " + file);
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
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
    }

    private static void forceWrite(Path target, byte[] content) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
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
        exactFields(object, Set.of("id", "label", "description", "recommendedChests", "refillMinutes", "entries"), field);
        JsonArray entries = array(required(object, "entries"), field + ".entries");
        if (entries.size() > LootType.MAX_ENTRIES) throw new IllegalArgumentException(field + ".entries may contain at most " + LootType.MAX_ENTRIES + " entries");
        List<LootEntry> decoded = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) decoded.add(decodeEntry(object(entries.get(i), field + ".entries[" + i + "]"), field + ".entries[" + i + "]"));
        return new LootType(string(required(object, "id"), field + ".id"), string(required(object, "label"), field + ".label"),
                string(required(object, "description"), field + ".description"), integer(required(object, "recommendedChests"), field + ".recommendedChests"),
                decimal(required(object, "refillMinutes"), field + ".refillMinutes"), decoded);
    }

    private static LootEntry decodeEntry(JsonObject object, String field) {
        Set<String> allowed = Set.of("item", "chancePercent", "minCount", "maxCount", "components");
        for (String name : object.keySet()) if (!allowed.contains(name)) throw new IllegalArgumentException(field + " has unknown field: " + name);
        for (String required : Set.of("item", "chancePercent", "minCount", "maxCount")) required(object, required);
        JsonElement components = object.has("components") ? object.get("components") : null;
        if (components != null && (components.isJsonNull() || !components.isJsonObject())) throw new IllegalArgumentException(field + ".components must be a JSON object");
        return new LootEntry(string(required(object, "item"), field + ".item"), decimal(required(object, "chancePercent"), field + ".chancePercent"),
                integer(required(object, "minCount"), field + ".minCount"), integer(required(object, "maxCount"), field + ".maxCount"), components == null ? "{}" : components.toString());
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
        JsonObject result = new JsonObject();
        result.addProperty("id", type.id()); result.addProperty("label", type.label()); result.addProperty("description", type.description());
        result.addProperty("recommendedChests", type.recommendedChests()); result.addProperty("refillMinutes", type.refillMinutes());
        JsonArray entries = new JsonArray();
        for (LootEntry entry : type.entries()) {
            JsonObject value = new JsonObject();
            value.addProperty("item", entry.item()); value.addProperty("chancePercent", entry.chancePercent());
            value.addProperty("minCount", entry.minCount()); value.addProperty("maxCount", entry.maxCount());
            value.add("components", LootJson.object(entry.componentsJson(), "loot components")); entries.add(value);
        }
        result.add("entries", entries);
        return result;
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
    private static String string(JsonElement value, String field) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(field + " must be a string"); return value.getAsString(); }
    private static int integer(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException(field + " must be an integer");
        try { return Math.toIntExact(Long.parseLong(value.getAsString())); } catch (NumberFormatException | ArithmeticException exception) { throw new IllegalArgumentException(field + " is outside integer range", exception); }
    }
    private static double decimal(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(field + " must be a number");
        try { double result = Double.parseDouble(value.getAsString()); if (!Double.isFinite(result)) throw new IllegalArgumentException(field + " must be finite"); return result; }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(field + " must be a finite number", exception); }
    }
}
