package io.github.chronicle.core.books;

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
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Strict, bounded schema-1 storage for complete campaign book-definition overrides. */
public final class BookConfig {
    public static final int SCHEMA_VERSION = 1;
    /** Aggregate encoded file cap; large catalogs may reach it before per-definition limits. */
    public static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    private static final int MAX_JSON_DEPTH = 16;
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final CommitCheck beforeCommit;
    @FunctionalInterface interface CommitCheck { void run() throws IOException; }
    private byte[] observedBytes;
    private Map<String, BookDefinition> observedBooks;

    public BookConfig(Path file) { this(file, () -> { }); }
    BookConfig(Path file, CommitCheck beforeCommit) {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException("book config file is required");
        this.file = file.toAbsolutePath().normalize();
        this.beforeCommit = java.util.Objects.requireNonNull(beforeCommit);
    }

    public synchronized Map<String, BookDefinition> loadOrCreate(Map<String, BookDefinition> defaults) throws IOException {
        Map<String, BookDefinition> validatedDefaults = defaults(defaults);
        if (!Files.exists(file)) return observe(writeInitial(validatedDefaults), validatedDefaults);
        byte[] bytes = current();
        try {
            TreeMap<String, BookDefinition> merged = new TreeMap<>(validatedDefaults);
            merged.putAll(decode(new String(bytes, StandardCharsets.UTF_8)));
            if (merged.size() > BookCatalog.MAX_BOOKS) throw new IllegalArgumentException("merged books exceed " + BookCatalog.MAX_BOOKS);
            return observe(bytes, merged);
        } catch (RuntimeException exception) {
            throw new IOException("book config is invalid and was not replaced: " + file + " (" + exception.getMessage() + ")", exception);
        }
    }

    /**
     * Saves a complete replacement after matching both the editor's expected definition and exact
     * on-disk bytes. A null expected value creates only a server-generated ID under the book root.
     */
    public synchronized Map<String, BookDefinition> replace(BookDefinition replacement, BookDefinition expected) throws IOException {
        if (replacement == null) throw new IllegalArgumentException("replacement book is required");
        if (observedBooks == null || observedBytes == null) throw new IllegalStateException("Book configuration must be loaded before editing");
        if (expected == null) {
            if (!BookDefinition.isGeneratedId(replacement.id()) || observedBooks.containsKey(replacement.id())) {
                throw new IllegalArgumentException("only a new server-generated book ID may be created without an expected definition");
            }
        } else {
            if (!replacement.id().equals(expected.id())) throw new IllegalArgumentException("book id cannot change while editing");
            if (!expected.equals(observedBooks.get(expected.id()))) throw new IllegalStateException("Book changed since this editor opened; reopen it before saving");
        }
        byte[] current = current();
        if (!MessageDigest.isEqual(observedBytes, current)) throw new IOException("book config changed outside Chronicle; reload it before saving " + file);
        try { decode(new String(current, StandardCharsets.UTF_8)); }
        catch (RuntimeException exception) { throw new IOException("book config is invalid and was not replaced: " + file, exception); }
        TreeMap<String, BookDefinition> candidate = new TreeMap<>(observedBooks);
        candidate.put(replacement.id(), replacement);
        if (candidate.size() > BookCatalog.MAX_BOOKS) throw new IllegalArgumentException("books exceed " + BookCatalog.MAX_BOOKS);
        Map<String, BookDefinition> immutableCandidate = immutable(candidate);
        byte[] encoded = encode(immutableCandidate).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_FILE_BYTES) throw new IOException("book config serialization exceeds 8 MiB limit: " + file);
        replaceDurably(current, encoded);
        return observe(encoded, immutableCandidate);
    }

    public static String encode(Map<String, BookDefinition> books) {
        Map<String, BookDefinition> validated = validated(books, "books");
        JsonObject root = new JsonObject(); root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray values = new JsonArray();
        for (BookDefinition book : validated.values()) values.add(encodeBook(book));
        root.add("books", values);
        return PRETTY_GSON.toJson(root);
    }

    public static JsonObject strictObject(String source) { return object(parse(source,"book menu"),"book menu"); }

    public static Map<String, BookDefinition> decode(String source) {
        JsonObject root = object(parse(source, "book config"), "root");
        exactFields(root, Set.of("schemaVersion", "books"), "root");
        if (integer(required(root, "schemaVersion"), "schemaVersion") != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported book config schemaVersion");
        JsonArray books = array(required(root, "books"), "books");
        if (books.size() > BookCatalog.MAX_BOOKS) throw new IllegalArgumentException("books may contain at most " + BookCatalog.MAX_BOOKS + " entries");
        Map<String, BookDefinition> result = new LinkedHashMap<>();
        for (int i = 0; i < books.size(); i++) {
            BookDefinition book = decodeBook(object(books.get(i), "books[" + i + "]"), "books[" + i + "]");
            if (result.putIfAbsent(book.id(), book) != null) throw new IllegalArgumentException("books contains duplicate id: " + book.id());
        }
        return immutable(result);
    }

    private Map<String, BookDefinition> defaults(Map<String, BookDefinition> defaults) { return validated(defaults, "book defaults"); }
    private static Map<String, BookDefinition> validated(Map<String, BookDefinition> books, String field) {
        if (books == null || books.size() > BookCatalog.MAX_BOOKS) throw new IllegalArgumentException(field + " may contain at most " + BookCatalog.MAX_BOOKS + " definitions");
        TreeMap<String, BookDefinition> result = new TreeMap<>();
        for (Map.Entry<String, BookDefinition> entry : books.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || !entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException(field + " must map IDs to matching definitions");
            if (result.putIfAbsent(entry.getKey(), entry.getValue()) != null) throw new IllegalArgumentException("duplicate " + field + " id: " + entry.getKey());
        }
        return immutable(result);
    }
    private byte[] current() throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("book config path is not a regular file: " + file);
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("book config exceeds 8 MiB limit: " + file);
        return Files.readAllBytes(file);
    }
    private byte[] writeInitial(Map<String, BookDefinition> defaults) throws IOException {
        Path parent = parent(); Files.createDirectories(parent);
        byte[] content = encode(defaults).getBytes(StandardCharsets.UTF_8);
        if (content.length > MAX_FILE_BYTES) throw new IOException("initial book config exceeds 8 MiB limit: " + file);
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try { forceWrite(temporary, content); Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE); }
        catch (FileAlreadyExistsException exception) { throw new IOException("book config appeared while creating it; refusing to overwrite " + file, exception); }
        catch (AtomicMoveNotSupportedException exception) { throw new IOException("atomic creation is not supported for book config: " + file, exception); }
        finally { Files.deleteIfExists(temporary); }
        return content;
    }
    private void replaceDurably(byte[] old, byte[] encoded) throws IOException {
        Path parent = parent(); Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp"); Path backupTemporary = null;
        try {
            forceWrite(temporary, encoded);
            beforeCommit.run();
            verifyObserved(old);
            Path backup = file.resolveSibling(file.getFileName() + ".bak"); backupTemporary = Files.createTempFile(parent, backup.getFileName().toString(), ".tmp");
            forceWrite(backupTemporary, old); moveReplace(backupTemporary, backup); backupTemporary = null; verifyObserved(old); moveReplace(temporary, file);
        } catch (IOException exception) { throw new IOException("unable to save book config; published books were not changed", exception); }
        finally { Files.deleteIfExists(temporary); if (backupTemporary != null) Files.deleteIfExists(backupTemporary); }
    }
    // Recheck at the commit boundary. Arbitrary external writers still must be stopped during saves:
    // the filesystem offers no atomic compare-bytes-and-rename against noncooperating processes.
    private void verifyObserved(byte[] expected) throws IOException {
        if (!MessageDigest.isEqual(expected, current())) throw new IOException("book config changed outside Chronicle during save; reload before retrying");
    }
    private Map<String, BookDefinition> observe(byte[] bytes, Map<String, BookDefinition> books) { observedBytes = bytes.clone(); observedBooks = immutable(books); return observedBooks; }
    private Path parent() throws IOException { Path parent = file.getParent(); if (parent == null) throw new IOException("book config has no parent directory: " + file); return parent; }
    private static void forceWrite(Path target, byte[] content) throws IOException { try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) { ByteBuffer buffer = ByteBuffer.wrap(content); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true); } }
    private static void moveReplace(Path source, Path target) throws IOException { try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException exception) { throw new IOException("atomic replacement is not supported for book config: " + target, exception); } }
    private static JsonObject encodeBook(BookDefinition book) { JsonObject value = new JsonObject(); value.addProperty("id", book.id()); value.addProperty("title", book.title()); value.addProperty("author", book.author()); value.addProperty("description", book.description()); JsonArray pages = new JsonArray(); for (String page : book.pages()) pages.add(page); value.add("pages", pages); value.addProperty("rule", book.rule().name()); return value; }
    private static BookDefinition decodeBook(JsonObject value, String field) { exactFields(value, Set.of("id", "title", "author", "description", "pages", "rule"), field); JsonArray pages = array(required(value, "pages"), field + ".pages"); java.util.ArrayList<String> decoded = new java.util.ArrayList<>(); for (int i = 0; i < pages.size(); i++) decoded.add(string(pages.get(i), field + ".pages[" + i + "]")); try { return new BookDefinition(string(required(value, "id"), field + ".id"), string(required(value, "title"), field + ".title"), string(required(value, "author"), field + ".author"), string(required(value, "description"), field + ".description"), decoded, BookDefinition.Rule.valueOf(string(required(value, "rule"), field + ".rule"))); } catch (IllegalArgumentException exception) { throw new IllegalArgumentException(field + " is invalid: " + exception.getMessage(), exception); } }
    private static Map<String, BookDefinition> immutable(Map<String, BookDefinition> books) { return Collections.unmodifiableMap(new TreeMap<>(books)); }
    private static JsonElement parse(String source, String field) { if (source == null) throw new IllegalArgumentException(field + " is required"); try (JsonReader reader = new JsonReader(new StringReader(source))) { reader.setStrictness(Strictness.STRICT); validate(reader, 0, field); if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException(field + " has trailing JSON content"); } catch (IOException exception) { throw new IllegalArgumentException("unable to read " + field, exception); } try (JsonReader reader = new JsonReader(new StringReader(source))) { reader.setStrictness(Strictness.STRICT); JsonElement result = JsonParser.parseReader(reader); if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException(field + " has trailing JSON content"); return result; } catch (IOException exception) { throw new IllegalArgumentException("unable to read " + field, exception); } }
    private static void validate(JsonReader reader, int depth, String field) throws IOException { if (depth > MAX_JSON_DEPTH) throw new IllegalArgumentException(field + " nesting exceeds " + MAX_JSON_DEPTH); switch (reader.peek()) { case BEGIN_OBJECT -> { reader.beginObject(); Set<String> names = new HashSet<>(); while (reader.hasNext()) { String name = reader.nextName(); if (!names.add(name)) throw new IllegalArgumentException(field + " contains duplicate member: " + name); validate(reader, depth + 1, field); } reader.endObject(); } case BEGIN_ARRAY -> { reader.beginArray(); while (reader.hasNext()) validate(reader, depth + 1, field); reader.endArray(); } case STRING, NUMBER -> reader.nextString(); case BOOLEAN -> reader.nextBoolean(); case NULL -> reader.nextNull(); default -> throw new IllegalArgumentException("unexpected token in " + field + ": " + reader.peek()); } }
    private static JsonObject object(JsonElement value, String field) { if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(field + " must be an object"); return value.getAsJsonObject(); }
    private static JsonArray array(JsonElement value, String field) { if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(field + " must be an array"); return value.getAsJsonArray(); }
    private static JsonElement required(JsonObject object, String field) { if (!object.has(field) || object.get(field).isJsonNull()) throw new IllegalArgumentException("missing " + field); return object.get(field); }
    private static String string(JsonElement value, String field) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(field + " must be a string"); return value.getAsString(); }
    private static void exactFields(JsonObject object, Set<String> allowed, String field) { if (!object.keySet().equals(allowed)) { List<String> unknown = object.keySet().stream().filter(name -> !allowed.contains(name)).sorted().toList(); List<String> missing = allowed.stream().filter(name -> !object.has(name)).sorted().toList(); throw new IllegalArgumentException(field + " fields do not match schema (unknown=" + unknown + ", missing=" + missing + ")"); } }
    private static int integer(JsonElement value, String field) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException(field + " must be an integer"); try { return Math.toIntExact(Long.parseLong(value.getAsString())); } catch (NumberFormatException | ArithmeticException exception) { throw new IllegalArgumentException(field + " is outside integer range", exception); } }
}
