package io.github.chronicle.core.enchanting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict, bounded, atomic schema-1 storage for complete ingredient enchantment overrides. */
public final class IngredientEnchantmentConfig {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_FILE_BYTES = 128L * 1024;
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private byte[] observedBytes;
    private List<IngredientEnchantmentRecipe> observed;

    public IngredientEnchantmentConfig(Path file) {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException("ingredient enchantment config file is required");
        this.file = file.toAbsolutePath().normalize();
    }

    /** Creates an absent file from defaults; existing files are complete operator overrides. */
    public synchronized List<IngredientEnchantmentRecipe> loadOrCreate(List<IngredientEnchantmentRecipe> defaults) throws IOException {
        List<IngredientEnchantmentRecipe> validatedDefaults = IngredientEnchantmentCatalog.immutableValidated(defaults, "ingredient enchantment defaults");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return observe(writeInitial(validatedDefaults), validatedDefaults);
        byte[] bytes = current();
        try {
            return observe(bytes, decode(new String(bytes, StandardCharsets.UTF_8)));
        } catch (RuntimeException exception) {
            throw new IOException("ingredient enchantment config is invalid and was not replaced: " + file + " (" + exception.getMessage() + ")", exception);
        }
    }

    /** Saves a complete replacement only if the semantic expected value and exact bytes still match. */
    public synchronized List<IngredientEnchantmentRecipe> save(List<IngredientEnchantmentRecipe> replacement, List<IngredientEnchantmentRecipe> expected) throws IOException {
        List<IngredientEnchantmentRecipe> candidate = IngredientEnchantmentCatalog.immutableValidated(replacement, "ingredient enchantment replacement");
        List<IngredientEnchantmentRecipe> expectedValidated = IngredientEnchantmentCatalog.immutableValidated(expected, "ingredient enchantment expected policy");
        if (observed == null || observedBytes == null) throw new IllegalStateException("ingredient enchantment configuration must be loaded before saving");
        if (!expectedValidated.equals(observed)) throw new IllegalStateException("ingredient enchantment recipes changed since this editor opened; reopen it before saving");
        byte[] current = current();
        if (!MessageDigest.isEqual(observedBytes, current)) throw new IOException("ingredient enchantment config changed outside Chronicle; reload it before saving " + file);
        try {
            decode(new String(current, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new IOException("ingredient enchantment config is invalid and was not replaced: " + file, exception);
        }
        byte[] encoded = encode(candidate).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_FILE_BYTES) throw new IOException("ingredient enchantment config serialization exceeds 128 KiB limit: " + file);
        replaceDurably(current, encoded);
        return observe(encoded, candidate);
    }

    public static String encode(List<IngredientEnchantmentRecipe> recipes) {
        List<IngredientEnchantmentRecipe> validated = IngredientEnchantmentCatalog.immutableValidated(recipes, "ingredient enchantment recipes");
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray values = new JsonArray();
        for (IngredientEnchantmentRecipe recipe : validated) values.add(encode(recipe));
        root.add("recipes", values);
        return PRETTY_GSON.toJson(root);
    }

    public static List<IngredientEnchantmentRecipe> decode(String source) {
        JsonObject root = object(parse(source), "root");
        exact(root, Set.of("schemaVersion", "recipes"), "root");
        if (integer(required(root, "schemaVersion"), "schemaVersion") != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported ingredient enchantment schemaVersion");
        JsonArray values = array(required(root, "recipes"), "recipes");
        if (values.size() > IngredientEnchantmentCatalog.MAX_RECIPES) throw new IllegalArgumentException("recipes may contain at most " + IngredientEnchantmentCatalog.MAX_RECIPES + " entries");
        List<IngredientEnchantmentRecipe> recipes = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) recipes.add(decodeRecipe(object(values.get(index), "recipes[" + index + "]"), "recipes[" + index + "]"));
        return IngredientEnchantmentCatalog.immutableValidated(recipes, "recipes");
    }

    private static IngredientEnchantmentRecipe decodeRecipe(JsonObject object, String field) {
        exact(object, Set.of("id", "group", "ingredient", "enchantment", "level", "count", "enabled"), field);
        return new IngredientEnchantmentRecipe(string(required(object, "id"), field + ".id"), string(required(object, "group"), field + ".group"),
                string(required(object, "ingredient"), field + ".ingredient"), string(required(object, "enchantment"), field + ".enchantment"),
                integer(required(object, "level"), field + ".level"), integer(required(object, "count"), field + ".count"), bool(required(object, "enabled"), field + ".enabled"));
    }

    private static JsonObject encode(IngredientEnchantmentRecipe recipe) {
        JsonObject object = new JsonObject();
        object.addProperty("id", recipe.id()); object.addProperty("group", recipe.group()); object.addProperty("ingredient", recipe.ingredient());
        object.addProperty("enchantment", recipe.enchantment()); object.addProperty("level", recipe.level()); object.addProperty("count", recipe.count()); object.addProperty("enabled", recipe.enabled());
        return object;
    }

    private byte[] current() throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("ingredient enchantment config path is not a regular file: " + file);
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("ingredient enchantment config exceeds 128 KiB limit: " + file);
        return Files.readAllBytes(file);
    }

    private byte[] writeInitial(List<IngredientEnchantmentRecipe> defaults) throws IOException {
        Path parent = parent(); Files.createDirectories(parent);
        byte[] content = encode(defaults).getBytes(StandardCharsets.UTF_8);
        if (content.length > MAX_FILE_BYTES) throw new IOException("initial ingredient enchantment config exceeds 128 KiB limit: " + file);
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try {
            forceWrite(temporary, content);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE); }
            catch (FileAlreadyExistsException exception) { throw new IOException("ingredient enchantment config appeared while creating it; refusing to overwrite " + file, exception); }
            catch (AtomicMoveNotSupportedException exception) { throw new IOException("atomic creation is not supported for ingredient enchantment config: " + file, exception); }
        } finally { Files.deleteIfExists(temporary); }
        return content;
    }

    private void replaceDurably(byte[] old, byte[] encoded) throws IOException {
        Path parent = parent(); Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp"); Path backupTemporary = null;
        try {
            forceWrite(temporary, encoded);
            Path backup = file.resolveSibling(file.getFileName() + ".bak"); backupTemporary = Files.createTempFile(parent, backup.getFileName().toString(), ".tmp");
            forceWrite(backupTemporary, old); moveReplace(backupTemporary, backup); backupTemporary = null; moveReplace(temporary, file);
        } catch (IOException exception) { throw new IOException("unable to save ingredient enchantment config; published recipes were not changed", exception); }
        finally { Files.deleteIfExists(temporary); if (backupTemporary != null) Files.deleteIfExists(backupTemporary); }
    }

    private List<IngredientEnchantmentRecipe> observe(byte[] bytes, List<IngredientEnchantmentRecipe> recipes) { observedBytes = bytes.clone(); observed = recipes; return recipes; }
    private Path parent() throws IOException { Path parent = file.getParent(); if (parent == null) throw new IOException("ingredient enchantment config has no parent directory: " + file); return parent; }
    private static void forceWrite(Path target, byte[] content) throws IOException { try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) { ByteBuffer buffer = ByteBuffer.wrap(content); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true); } }
    private static void moveReplace(Path source, Path target) throws IOException { try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException exception) { throw new IOException("atomic replacement is not supported for ingredient enchantment config: " + target, exception); } }
    private static JsonElement parse(String source) {
        if (source == null) throw new IllegalArgumentException("ingredient enchantment config is required"); validate(source);
        try (JsonReader reader = new JsonReader(new StringReader(source))) { reader.setStrictness(Strictness.STRICT); JsonElement result = JsonParser.parseReader(reader); if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("ingredient enchantment config has trailing JSON content"); return result; }
        catch (IOException exception) { throw new IllegalArgumentException("unable to read ingredient enchantment config", exception); }
    }
    private static void validate(String source) {
        try (JsonReader reader = new JsonReader(new StringReader(source))) { reader.setStrictness(Strictness.STRICT); validateValue(reader, 0); if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("ingredient enchantment config has trailing JSON content"); }
        catch (IOException exception) { throw new IllegalArgumentException("unable to read ingredient enchantment config", exception); }
    }
    private static void validateValue(JsonReader reader, int depth) throws IOException {
        if (depth > 16) throw new IllegalArgumentException("ingredient enchantment config nesting exceeds 16");
        switch (reader.peek()) {
            case BEGIN_OBJECT -> { reader.beginObject(); Set<String> names = new HashSet<>(); while (reader.hasNext()) { String name = reader.nextName(); if (!names.add(name)) throw new IllegalArgumentException("ingredient enchantment config contains duplicate member: " + name); validateValue(reader, depth + 1); } reader.endObject(); }
            case BEGIN_ARRAY -> { reader.beginArray(); while (reader.hasNext()) validateValue(reader, depth + 1); reader.endArray(); }
            case STRING, NUMBER -> reader.nextString(); case BOOLEAN -> reader.nextBoolean(); case NULL -> reader.nextNull(); default -> throw new IllegalArgumentException("unexpected JSON token: " + reader.peek());
        }
    }
    private static JsonObject object(JsonElement value, String field) { if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(field + " must be an object"); return value.getAsJsonObject(); }
    private static JsonArray array(JsonElement value, String field) { if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(field + " must be an array"); return value.getAsJsonArray(); }
    private static JsonElement required(JsonObject object, String field) { if (!object.has(field) || object.get(field).isJsonNull()) throw new IllegalArgumentException("missing " + field); return object.get(field); }
    private static void exact(JsonObject object, Set<String> allowed, String field) { if (!object.keySet().equals(allowed)) throw new IllegalArgumentException(field + " fields do not match schema"); }
    private static String string(JsonElement value, String field) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(field + " must be a string"); return value.getAsString(); }
    private static boolean bool(JsonElement value, String field) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(field + " must be a boolean"); return value.getAsBoolean(); }
    private static int integer(JsonElement value, String field) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException(field + " must be an integer"); try { return Math.toIntExact(Long.parseLong(value.getAsString())); } catch (NumberFormatException | ArithmeticException exception) { throw new IllegalArgumentException(field + " is outside integer range", exception); } }
}
