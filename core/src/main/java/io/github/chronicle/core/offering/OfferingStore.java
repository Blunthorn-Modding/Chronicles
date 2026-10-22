package io.github.chronicle.core.offering;

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
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
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
import java.util.TreeSet;
import java.util.UUID;

/** Strict, bounded, exclusively locked versioned authority for lasting offerings. */
public final class OfferingStore implements AutoCloseable {
    public static final int SCHEMA_VERSION = 2;
    public static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    // Stable initialization sentinel: JSON schema upgrades must not invalidate existing authority.
    private static final byte[] INITIALIZED = "chronicle-offerings-schema-1\n".getBytes(StandardCharsets.UTF_8);
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "revision", "receipts", "closedSources");
    private static final Set<String> RECEIPT_FIELDS = Set.of("transaction", "player", "source", "offering", "terms");
    private static final Set<String> LEGACY_TERMS_FIELDS = Set.of("health", "food", "speedPercent");
    private static final Set<String> TERMS_FIELDS = Set.of("health", "food", "speedPercent", "exhaustionPercent");
    private static final SaveObserver NO_SAVE_OBSERVER = checkpoint -> { };

    enum SaveCheckpoint { BEFORE_BACKUP_PUBLISH, BEFORE_LEDGER_PUBLISH }
    @FunctionalInterface interface SaveObserver { void at(SaveCheckpoint checkpoint) throws IOException; }

    private final Path file;
    private final Path initializedFile;
    private final Path lockFile;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final SaveObserver saveObserver;
    private byte[] observedBytes;
    private OfferingLedger observed;
    private boolean closed;

    public OfferingStore(Path file) throws IOException {
        this(file, NO_SAVE_OBSERVER);
    }

    /** Package-private deterministic interleaving seam used only by persistence fault tests. */
    OfferingStore(Path file, SaveObserver saveObserver) throws IOException {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException("offering ledger file is required");
        if (saveObserver == null) throw new IllegalArgumentException("offering save observer is required");
        this.file = file.toAbsolutePath().normalize();
        this.saveObserver = saveObserver;
        this.initializedFile = sibling(".initialized");
        this.lockFile = sibling(".lock");
        Path parent = parent();
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(lockFile)) throw new IOException("offering lock path must not be a symbolic link: " + lockFile);
        FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        FileLock acquired = null;
        try {
            try { acquired = channel.tryLock(); }
            catch (OverlappingFileLockException exception) { throw new IOException("offering ledger is already open: " + file, exception); }
            if (acquired == null) throw new IOException("offering ledger is already open: " + file);
        } catch (IOException | RuntimeException exception) {
            channel.close();
            throw exception;
        }
        lockChannel = channel;
        lock = acquired;
    }

    /** Creates the authority exactly once, or loads the previously initialized authority. */
    public synchronized OfferingLedger loadOrCreate() throws IOException {
        requireOpen();
        if (observed != null) {
            verifyUnchanged();
            return observed;
        }
        boolean markerPresent = Files.exists(initializedFile, LinkOption.NOFOLLOW_LINKS);
        boolean ledgerPresent = Files.exists(file, LinkOption.NOFOLLOW_LINKS);
        if (!markerPresent) {
            if (ledgerPresent) throw new IOException("offering ledger exists without its initialization marker; refusing it: " + file);
            if (Files.exists(sibling(".bak"), LinkOption.NOFOLLOW_LINKS)) throw new IOException("offering backup exists without its authority; manual recovery is required: " + file);
            writeInitializationMarker();
            byte[] initial = encode(OfferingLedger.EMPTY).getBytes(StandardCharsets.UTF_8);
            writeInitialLedger(initial);
            return observe(initial, OfferingLedger.EMPTY);
        }
        verifyMarker();
        if (!ledgerPresent) throw new IOException("initialized offering ledger is missing; manual recovery is required: " + file);
        byte[] bytes = current();
        return observe(bytes, decodeChecked(bytes));
    }

    /**
     * Publishes a complete replacement only if the expected ledger and exact loaded bytes still
     * match. The exact previous bytes become the sibling {@code .bak} file first.
     */
    public synchronized OfferingLedger save(OfferingLedger replacement, OfferingLedger expected) throws IOException {
        requireOpen();
        if (replacement == null || expected == null) throw new IllegalArgumentException("replacement and expected offering ledgers are required");
        if (observed == null || observedBytes == null) throw new IllegalStateException("offering ledger must be loaded before saving");
        if (!expected.equals(observed)) throw new IllegalStateException("offering ledger changed since this operation began");
        verifyUnchanged();
        byte[] encoded = encode(replacement).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_FILE_BYTES) throw new IOException("offering ledger serialization exceeds 8 MiB limit: " + file);
        replaceDurably(observedBytes, encoded);
        return observe(encoded, replacement);
    }

    /** Refuses a missing, malformed, or byte-changed authority. */
    public synchronized void verifyUnchanged() throws IOException {
        requireOpen();
        if (observed == null || observedBytes == null) throw new IllegalStateException("offering ledger must be loaded before verification");
        verifyMarker();
        byte[] bytes = verifyCurrentBytes(observedBytes);
        decodeChecked(bytes);
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException failure = null;
        try { lock.release(); } catch (IOException exception) { failure = exception; }
        try { lockChannel.close(); } catch (IOException exception) { if (failure == null) failure = exception; else failure.addSuppressed(exception); }
        if (failure != null) throw failure;
    }

    static String encode(OfferingLedger ledger) {
        if (ledger == null) throw new IllegalArgumentException("offering ledger is required");
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("revision", ledger.revision());
        JsonArray receipts = new JsonArray();
        for (OfferingReceipt receipt : ledger.receipts()) {
            JsonObject value = new JsonObject();
            value.addProperty("transaction", receipt.transaction().toString());
            value.addProperty("player", receipt.player().toString());
            value.addProperty("source", receipt.source());
            value.addProperty("offering", receipt.offering());
            JsonObject terms = new JsonObject();
            terms.addProperty("health", receipt.terms().health());
            terms.addProperty("food", receipt.terms().food());
            terms.addProperty("speedPercent", receipt.terms().speedPercent());
            terms.addProperty("exhaustionPercent", receipt.terms().exhaustionPercent());
            value.add("terms", terms);
            receipts.add(value);
        }
        root.add("receipts", receipts);
        JsonArray closedSources = new JsonArray();
        ledger.closedSources().forEach(closedSources::add);
        root.add("closedSources", closedSources);
        return PRETTY_GSON.toJson(root);
    }

    static OfferingLedger decode(String source) {
        JsonObject root = object(parse(source), "offering ledger root");
        exact(root, ROOT_FIELDS, "offering ledger root");
        int schema=integer(required(root,"schemaVersion"),"schemaVersion");
        if (schema!=1 && schema!=SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported offering ledger schemaVersion");
        }
        long revision = number(required(root, "revision"), "revision");
        JsonArray receiptValues = array(required(root, "receipts"), "receipts");
        if (receiptValues.size() > OfferingLedger.MAX_RECEIPTS) throw new IllegalArgumentException("too many offering receipts");
        List<OfferingReceipt> receipts = new ArrayList<>(receiptValues.size());
        for (JsonElement element : receiptValues) {
            JsonObject value = object(element, "offering receipt");
            exact(value, RECEIPT_FIELDS, "offering receipt");
            JsonObject termsValue = object(required(value, "terms"), "offering terms");
            exact(termsValue, schema==1 ? LEGACY_TERMS_FIELDS : TERMS_FIELDS, "offering terms");
            receipts.add(new OfferingReceipt(
                    uuid(required(value, "transaction"), "transaction"),
                    uuid(required(value, "player"), "player"),
                    string(required(value, "source"), "source"),
                    string(required(value, "offering"), "offering"),
                    new OfferingTerms(signedInteger(required(termsValue, "health"), "health"),
                            signedInteger(required(termsValue, "food"), "food"),
                            signedInteger(required(termsValue, "speedPercent"), "speedPercent"),
                            schema==1 ? 0 : signedInteger(required(termsValue,"exhaustionPercent"),"exhaustionPercent"))));
        }
        JsonArray closedValues = array(required(root, "closedSources"), "closedSources");
        if (closedValues.size() > OfferingLedger.MAX_CLOSED_SOURCES) throw new IllegalArgumentException("too many closed offering sources");
        Set<String> closedSources = new TreeSet<>();
        for (JsonElement element : closedValues) {
            String sourceId = string(element, "closed source");
            OfferingDefinition.validId(sourceId);
            if (!closedSources.add(sourceId)) throw new IllegalArgumentException("duplicate closed offering source: " + sourceId);
        }
        return new OfferingLedger(revision, receipts, closedSources);
    }

    private OfferingLedger decodeChecked(byte[] bytes) throws IOException {
        try { return decode(new String(bytes, StandardCharsets.UTF_8)); }
        catch (RuntimeException exception) {
            throw new IOException("offering ledger is invalid and was not replaced: " + file + " (" + exception.getMessage() + ")", exception);
        }
    }

    private byte[] current() throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("offering ledger path is not a regular file: " + file);
        }
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("offering ledger exceeds 8 MiB limit: " + file);
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("offering ledger exceeds 8 MiB limit: " + file);
        return bytes;
    }

    private void writeInitializationMarker() throws IOException {
        if (Files.isSymbolicLink(initializedFile)) throw new IOException("offering initialization marker must not be a symbolic link: " + initializedFile);
        try (FileChannel channel = FileChannel.open(initializedFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            writeAndForce(channel, INITIALIZED);
        } catch (FileAlreadyExistsException exception) {
            throw new IOException("offering initialization marker appeared concurrently; refusing to continue: " + initializedFile, exception);
        }
    }

    private void verifyMarker() throws IOException {
        if (Files.isSymbolicLink(initializedFile) || !Files.isRegularFile(initializedFile, LinkOption.NOFOLLOW_LINKS)
                || Files.size(initializedFile) != INITIALIZED.length
                || !MessageDigest.isEqual(Files.readAllBytes(initializedFile), INITIALIZED)) {
            throw new IOException("offering initialization marker is invalid: " + initializedFile);
        }
    }

    private void writeInitialLedger(byte[] content) throws IOException {
        Path temporary = Files.createTempFile(parent(), file.getFileName().toString(), ".tmp");
        try {
            forceWrite(temporary, content);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE); }
            catch (FileAlreadyExistsException exception) { throw new IOException("offering ledger appeared while creating it; refusing to overwrite: " + file, exception); }
            catch (AtomicMoveNotSupportedException exception) { throw new IOException("atomic creation is not supported for offering ledger: " + file, exception); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private void replaceDurably(byte[] old, byte[] replacement) throws IOException {
        Path parent = parent();
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        Path backupTemporary = null;
        try {
            forceWrite(temporary, replacement);
            Path backup = sibling(".bak");
            if (Files.isSymbolicLink(backup)) throw new IOException("offering backup must not be a symbolic link: " + backup);
            backupTemporary = Files.createTempFile(parent, backup.getFileName().toString(), ".tmp");
            forceWrite(backupTemporary, old);
            saveObserver.at(SaveCheckpoint.BEFORE_BACKUP_PUBLISH);
            verifyCurrentBytes(old);
            moveReplace(backupTemporary, backup);
            backupTemporary = null;
            saveObserver.at(SaveCheckpoint.BEFORE_LEDGER_PUBLISH);
            verifyCurrentBytes(old);
            moveReplace(temporary, file);
        } catch (IOException exception) {
            throw new IOException("unable to save offering ledger; published authority was not changed", exception);
        } finally {
            Files.deleteIfExists(temporary);
            if (backupTemporary != null) Files.deleteIfExists(backupTemporary);
        }
    }

    private OfferingLedger observe(byte[] bytes, OfferingLedger ledger) {
        observedBytes = bytes.clone();
        observed = ledger;
        return ledger;
    }

    private byte[] verifyCurrentBytes(byte[] expected) throws IOException {
        byte[] bytes = current();
        if (!MessageDigest.isEqual(expected, bytes)) {
            throw new IOException("offering ledger changed outside Chronicle; refusing to continue: " + file);
        }
        return bytes;
    }

    private Path sibling(String suffix) { return file.resolveSibling(file.getFileName() + suffix); }
    private Path parent() throws IOException { Path parent = file.getParent(); if (parent == null) throw new IOException("offering ledger has no parent directory: " + file); return parent; }
    private void requireOpen() { if (closed) throw new IllegalStateException("offering store is closed"); }
    private static void forceWrite(Path target, byte[] content) throws IOException { try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) { writeAndForce(channel, content); } }
    private static void writeAndForce(FileChannel channel, byte[] content) throws IOException { ByteBuffer buffer = ByteBuffer.wrap(content); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true); }
    private static void moveReplace(Path source, Path target) throws IOException { try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException exception) { throw new IOException("atomic replacement is not supported for offering ledger: " + target, exception); } }

    private static JsonElement parse(String source) {
        if (source == null) throw new IllegalArgumentException("offering ledger JSON is required");
        validate(source);
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement result = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("offering ledger has trailing JSON content");
            return result;
        } catch (IOException exception) { throw new IllegalArgumentException("unable to read offering ledger", exception); }
    }

    private static void validate(String source) {
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            validateValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("offering ledger has trailing JSON content");
        } catch (IOException exception) { throw new IllegalArgumentException("unable to read offering ledger", exception); }
    }

    private static void validateValue(JsonReader reader, int depth) throws IOException {
        if (depth > 12) throw new IllegalArgumentException("offering ledger nesting exceeds 12");
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) throw new IllegalArgumentException("offering ledger contains duplicate member: " + name);
                    validateValue(reader, depth + 1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> { reader.beginArray(); while (reader.hasNext()) validateValue(reader, depth + 1); reader.endArray(); }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException("unexpected offering JSON token: " + reader.peek());
        }
    }

    private static JsonObject object(JsonElement value, String field) { if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(field + " must be an object"); return value.getAsJsonObject(); }
    private static JsonArray array(JsonElement value, String field) { if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(field + " must be an array"); return value.getAsJsonArray(); }
    private static JsonElement required(JsonObject object, String field) { if (!object.has(field) || object.get(field).isJsonNull()) throw new IllegalArgumentException("missing " + field); return object.get(field); }
    private static void exact(JsonObject object, Set<String> fields, String name) { if (!object.keySet().equals(fields)) throw new IllegalArgumentException(name + " fields do not match schema"); }
    private static String string(JsonElement value, String field) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(field + " must be a string"); return value.getAsString(); }
    private static int integer(JsonElement value, String field) { long result = number(value, field); try { return Math.toIntExact(result); } catch (ArithmeticException exception) { throw new IllegalArgumentException(field + " is outside integer range", exception); } }
    private static int signedInteger(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException(field + " must be a canonical integer");
        try { return Integer.parseInt(value.getAsString()); } catch (NumberFormatException exception) { throw new IllegalArgumentException(field + " is outside integer range", exception); }
    }
    private static long number(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("(0|[1-9][0-9]*)")) throw new IllegalArgumentException(field + " must be a non-negative canonical integer");
        try { return Long.parseLong(value.getAsString()); } catch (NumberFormatException exception) { throw new IllegalArgumentException(field + " is outside long range", exception); }
    }
    private static UUID uuid(JsonElement value, String field) {
        String text = string(value, field);
        try {
            UUID result = UUID.fromString(text);
            if (!result.toString().equals(text)) throw new IllegalArgumentException(field + " must be a canonical UUID");
            return result;
        } catch (IllegalArgumentException exception) { throw new IllegalArgumentException(field + " must be a canonical UUID", exception); }
    }
}
