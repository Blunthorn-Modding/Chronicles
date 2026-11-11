package io.github.chronicle.core.enchanting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Strict, bounded, atomic schema-1 storage for the global enchantment-cap policy. */
public final class EnchantmentCapConfig {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_FILE_BYTES = 128L * 1024;
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private byte[] observedBytes;
    private EnchantmentCaps observed;

    public EnchantmentCapConfig(Path file) {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException("enchantment cap config file is required");
        this.file = file.toAbsolutePath().normalize();
    }

    /** Creates an absent file from defaults, or loads its complete strict policy. */
    public synchronized EnchantmentCaps loadOrCreate(EnchantmentCaps defaults) throws IOException {
        if (defaults == null) throw new IllegalArgumentException("enchantment cap defaults are required");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] initial = writeInitial(defaults);
            return observe(initial, defaults);
        }
        byte[] bytes = current();
        try {
            return observe(bytes, decode(new String(bytes, StandardCharsets.UTF_8)));
        } catch (RuntimeException exception) {
            throw new IOException("enchantment cap config is invalid and was not replaced: " + file + " (" + exception.getMessage() + ")", exception);
        }
    }

    /**
     * Saves a complete replacement only when the expected policy and exact observed disk bytes
     * still match. The prior valid bytes become the atomically replaced {@code .bak} rollback copy.
     */
    public synchronized EnchantmentCaps save(EnchantmentCaps replacement, EnchantmentCaps expected) throws IOException {
        if (replacement == null || expected == null) throw new IllegalArgumentException("replacement and expected enchantment caps are required");
        if (observed == null || observedBytes == null) throw new IllegalStateException("enchantment cap configuration must be loaded before saving");
        if (!expected.equals(observed)) throw new IllegalStateException("enchantment cap policy changed since this editor opened; reopen it before saving");
        byte[] current = current();
        if (!MessageDigest.isEqual(observedBytes, current)) throw new IOException("enchantment cap config changed outside Chronicle; reload it before saving " + file);
        try {
            decode(new String(current, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new IOException("enchantment cap config is invalid and was not replaced: " + file, exception);
        }
        byte[] encoded = encode(replacement).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_FILE_BYTES) throw new IOException("enchantment cap config serialization exceeds 128 KiB limit: " + file);
        replaceDurably(current, encoded);
        return observe(encoded, replacement);
    }

    public static String encode(EnchantmentCaps caps) {
        if (caps == null) throw new IllegalArgumentException("enchantment caps are required");
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("enabled", caps.enabled());
        root.addProperty("defaultMaxLevel", caps.defaultMaxLevel());
        JsonObject overrides = new JsonObject();
        for (Map.Entry<String, Integer> entry : caps.overrides().entrySet()) overrides.addProperty(entry.getKey(), entry.getValue());
        root.add("overrides", overrides);
        return PRETTY_GSON.toJson(root);
    }

    public static EnchantmentCaps decode(String source) {
        JsonObject root = object(parse(source), "root");
        exact(root, Set.of("schemaVersion", "enabled", "defaultMaxLevel", "overrides"), "root");
        if (integer(required(root, "schemaVersion"), "schemaVersion") != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported enchantment cap schemaVersion");
        boolean enabled = bool(required(root, "enabled"), "enabled");
        int defaultMaxLevel = integer(required(root, "defaultMaxLevel"), "defaultMaxLevel");
        JsonObject rawOverrides = object(required(root, "overrides"), "overrides");
        if (rawOverrides.size() > EnchantmentCaps.MAX_OVERRIDES) throw new IllegalArgumentException("overrides may contain at most " + EnchantmentCaps.MAX_OVERRIDES + " entries");
        TreeMap<String, Integer> overrides = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : rawOverrides.entrySet()) {
            EnchantmentCaps.validId(entry.getKey());
            overrides.put(entry.getKey(), integer(entry.getValue(), "overrides." + entry.getKey()));
        }
        return new EnchantmentCaps(enabled, defaultMaxLevel, overrides);
    }

    private byte[] current() throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("enchantment cap config path is not a regular file: " + file);
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("enchantment cap config exceeds 128 KiB limit: " + file);
        return Files.readAllBytes(file);
    }

    private byte[] writeInitial(EnchantmentCaps defaults) throws IOException {
        Path parent = parent();
        Files.createDirectories(parent);
        byte[] content = encode(defaults).getBytes(StandardCharsets.UTF_8);
        if (content.length > MAX_FILE_BYTES) throw new IOException("initial enchantment cap config exceeds 128 KiB limit: " + file);
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try {
            forceWrite(temporary, content);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException exception) {
                throw new IOException("enchantment cap config appeared while creating it; refusing to overwrite " + file, exception);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic creation is not supported for enchantment cap config: " + file, exception);
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
            throw new IOException("unable to save enchantment cap config; published policy was not changed", exception);
        } finally {
            Files.deleteIfExists(temporary);
            if (backupTemporary != null) Files.deleteIfExists(backupTemporary);
        }
    }

    private EnchantmentCaps observe(byte[] bytes, EnchantmentCaps caps) { observedBytes = bytes.clone(); observed = caps; return caps; }
    private Path parent() throws IOException { Path parent = file.getParent(); if (parent == null) throw new IOException("enchantment cap config has no parent directory: " + file); return parent; }
    private static void forceWrite(Path target, byte[] content) throws IOException { try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) { ByteBuffer buffer = ByteBuffer.wrap(content); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true); } }
    private static void moveReplace(Path source, Path target) throws IOException { try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException exception) { throw new IOException("atomic replacement is not supported for enchantment cap config: " + target, exception); } }
    private static JsonElement parse(String source) {
        if (source == null) throw new IllegalArgumentException("enchantment cap config is required");
        validate(source);
        try (JsonReader reader = new JsonReader(new StringReader(source))) { reader.setStrictness(Strictness.STRICT); JsonElement result = JsonParser.parseReader(reader); if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("enchantment cap config has trailing JSON content"); return result; }
        catch (IOException exception) { throw new IllegalArgumentException("unable to read enchantment cap config", exception); }
    }
    private static void validate(String source) {
        try (JsonReader reader = new JsonReader(new StringReader(source))) { reader.setStrictness(Strictness.STRICT); validateValue(reader, 0); if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("enchantment cap config has trailing JSON content"); }
        catch (IOException exception) { throw new IllegalArgumentException("unable to read enchantment cap config", exception); }
    }
    private static void validateValue(JsonReader reader, int depth) throws IOException {
        if (depth > 16) throw new IllegalArgumentException("enchantment cap config nesting exceeds 16");
        switch (reader.peek()) {
            case BEGIN_OBJECT -> { reader.beginObject(); Set<String> names = new HashSet<>(); while (reader.hasNext()) { String name = reader.nextName(); if (!names.add(name)) throw new IllegalArgumentException("enchantment cap config contains duplicate member: " + name); validateValue(reader, depth + 1); } reader.endObject(); }
            case BEGIN_ARRAY -> { reader.beginArray(); while (reader.hasNext()) validateValue(reader, depth + 1); reader.endArray(); }
            case STRING, NUMBER -> reader.nextString(); case BOOLEAN -> reader.nextBoolean(); case NULL -> reader.nextNull(); default -> throw new IllegalArgumentException("unexpected JSON token: " + reader.peek());
        }
    }
    private static JsonObject object(JsonElement value, String field) { if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(field + " must be an object"); return value.getAsJsonObject(); }
    private static JsonElement required(JsonObject object, String field) { if (!object.has(field) || object.get(field).isJsonNull()) throw new IllegalArgumentException("missing " + field); return object.get(field); }
    private static void exact(JsonObject object, Set<String> allowed, String field) { if (!object.keySet().equals(allowed)) throw new IllegalArgumentException(field + " fields do not match schema"); }
    private static boolean bool(JsonElement value, String field) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(field + " must be a boolean"); return value.getAsBoolean(); }
    private static int integer(JsonElement value, String field) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException(field + " must be an integer"); try { return Math.toIntExact(Long.parseLong(value.getAsString())); } catch (NumberFormatException | ArithmeticException exception) { throw new IllegalArgumentException(field + " is outside integer range", exception); } }
}
