package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bounded append-only evidence for Quiet Below ecology mutations.
 *
 * <p>Each LF-terminated line is a canonical schema-1 JSON envelope. Its hash covers every envelope
 * field except {@code hash}, and {@code previousHash} links it to the preceding record. Invalid or
 * externally changed evidence is never repaired, truncated, or replaced.</p>
 */
public final class EcologyJournal implements AutoCloseable {
    static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    static final int MAX_LINE_BYTES = 64 * 1024;
    static final int MAX_ENTRIES = 150_000;
    static final int MAX_JSON_DEPTH = 32;
    static final int MAX_KEY_CODEPOINTS = 128;
    static final int MAX_STRING_CODEPOINTS = 16_384;

    private static final int SCHEMA = 1;
    private static final int HASH_BYTES = 32;
    private static final int HASH_TEXT_LENGTH = HASH_BYTES * 2;
    private static final int MAX_NUMBER_CHARACTERS = 128;
    private static final int TAIL_BYTES = MAX_LINE_BYTES;
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final boolean WINDOWS = "\\".equals(FileSystems.getDefault().getSeparator());
    private static final OpenOption WINDOWS_NO_SHARE_DELETE = windowsNoShareDelete();
    private static final String GENESIS_HASH = "0".repeat(HASH_TEXT_LENGTH);
    private static final Set<String> TYPES = Set.of(
            "CONFIGURE", "INDEX", "INDEX_COMPLETE", "MODE", "GROW", "REMOVE", "OBSERVE", "CONNECT",
            "CLEANUP_MODE", "PURGE", "CLAIM_START", "CLAIM_SCAN", "CLAIM_DONE", "SPEED", "FRONTIER", "VITALITY", "VITALITY_RULES", "CROP_RULES",
            "CHECKPOINT_BEGIN", "CHECKPOINT_CELLS", "CHECKPOINT_AIR", "CHECKPOINT_END", "RECOVERY_RULES", "COST_RULES", "SPREAD_RULES");
    private static final Set<String> ENVELOPE_FIELDS = Set.of(
            "schema", "sequence", "type", "payload", "previousHash", "hash");

    private final Path file;
    private final FileChannel channel;
    private final FileLock lock;
    private final ArrayList<Entry> entries;
    private final Object fileKey;
    // Windows Java 21 can return no fileKey. NOSHARE_DELETE then pins the opened file at this path;
    // creation time supplies an additional stable attribute for the bounded stat/tail checks.
    private final FileTime creationTime;
    private final boolean windowsNullKeyIdentity;
    private long byteCount;
    private FileTime lastModified;
    private byte[] expectedTail;
    private String lastHash;
    private boolean poisoned;
    private IOException poisonCause;
    private boolean closed;

    /** Opens and strictly replays an existing journal, or durably creates a new empty journal. */
    public EcologyJournal(Path file) throws IOException {
        if (file == null || file.getFileName() == null) {
            throw new IllegalArgumentException("ecology journal file is required");
        }
        this.file = file.toAbsolutePath().normalize();
        Path parent = this.file.getParent();
        if (parent == null) {
            throw new IOException("Ecology journal requires a parent directory: " + this.file);
        }
        Files.createDirectories(parent);

        FileChannel opened = null;
        FileLock acquired = null;
        try {
            boolean created = false;
            BasicFileAttributes pathBeforeOpen = null;
            try {
                opened = openChannel(this.file, true);
                created = true;
            } catch (FileAlreadyExistsException exception) {
                pathBeforeOpen = attributes(this.file);
                validateRegularAndBounded(pathBeforeOpen, this.file);
                opened = openChannel(this.file, false);
            }
            try {
                acquired = opened.tryLock();
            } catch (OverlappingFileLockException exception) {
                throw new IOException("Ecology journal is already open in this process: " + this.file, exception);
            }
            if (acquired == null) {
                throw new IOException("Ecology journal is locked by another process: " + this.file);
            }
            if (created) {
                opened.force(true);
            }

            BasicFileAttributes before = attributes(this.file);
            validateRegularAndBounded(before, this.file);
            boolean nullKeyIdentity = before.fileKey() == null;
            if (nullKeyIdentity && (!WINDOWS || WINDOWS_NO_SHARE_DELETE == null)) {
                throw new IOException(
                        "Ecology journal filesystem does not expose stable file identity: " + this.file);
            }
            if (pathBeforeOpen != null) {
                requireSameFileState(pathBeforeOpen, before, nullKeyIdentity, this.file,
                        "while it was being opened");
            }
            if (opened.size() != before.size()) {
                throw new IOException("Ecology journal changed while it was being opened: " + this.file);
            }

            Replay replay = replay(opened, before.size(), this.file);
            BasicFileAttributes after = attributes(this.file);
            requireSameFileState(before, after, nullKeyIdentity, this.file, "while it was being replayed");
            if (opened.size() != before.size()
                    || !Arrays.equals(replay.tail(), readTail(opened, before.size()))) {
                throw new IOException("Ecology journal changed while it was being replayed: " + this.file);
            }

            this.channel = opened;
            this.lock = acquired;
            this.entries = new ArrayList<>(replay.entries());
            this.fileKey = before.fileKey();
            this.creationTime = before.creationTime();
            this.windowsNullKeyIdentity = nullKeyIdentity;
            this.byteCount = before.size();
            this.lastModified = before.lastModifiedTime();
            this.expectedTail = replay.tail();
            this.lastHash = replay.lastHash();
        } catch (IOException | RuntimeException exception) {
            releaseQuietly(acquired);
            closeQuietly(opened);
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    /** Returns an immutable snapshot in durable sequence order. */
    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** Returns the last durable sequence, or zero for an empty journal. */
    public synchronized long revision() {
        return entries.size();
    }

    /** Returns the exact durable journal length observed by this instance. */
    public synchronized long bytes() {
        return byteCount;
    }

    /** Maintenance reads through the locked channel (Windows forbids a second file handle). */
    synchronized String fingerprint() throws IOException {
        ensureAppendable();verifyUnchanged();MessageDigest digest;
        try { digest=MessageDigest.getInstance("SHA-256"); } catch(NoSuchAlgorithmException e){throw new IOException(e);}
        ByteBuffer buffer=ByteBuffer.allocate(64*1024);long position=0;
        while(position<byteCount){buffer.clear();buffer.limit((int)Math.min(buffer.capacity(),byteCount-position));
            int read=channel.read(buffer,position);if(read<=0)throw new IOException("Journal changed during maintenance read");
            position+=read;buffer.flip();digest.update(buffer);
        }
        verifyUnchanged();return java.util.HexFormat.of().formatHex(digest.digest());
    }
    synchronized void archiveTo(Path target) throws IOException {
        ensureAppendable();verifyUnchanged();
        try(FileChannel output=FileChannel.open(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            ByteBuffer buffer=ByteBuffer.allocate(64*1024);long position=0;
            while(position<byteCount){buffer.clear();buffer.limit((int)Math.min(buffer.capacity(),byteCount-position));
                int read=channel.read(buffer,position);if(read<=0)throw new IOException("Journal changed during archive");
                position+=read;buffer.flip();while(buffer.hasRemaining())output.write(buffer);
            }
            output.force(true);verifyUnchanged();
        }
    }

    /**
     * Appends and forces one record before publishing it through {@link #entries()} or
     * {@link #revision()}. Any I/O failure or uncertain write poisons this instance.
     */
    public synchronized Entry append(String type, JsonObject payload) throws IOException {
        ensureAppendable();
        if (type == null || !TYPES.contains(type)) {
            throw new IllegalArgumentException("unknown ecology journal type: " + type);
        }
        if (payload == null) {
            throw new IllegalArgumentException("ecology journal payload is required");
        }
        JsonObject canonicalPayload = object(canonicalize(payload, 1, new JsonBudget()), "payload");
        if (entries.size() >= MAX_ENTRIES) {
            throw new IOException("Ecology journal entry capacity is exhausted; ecology must remain held: " + file);
        }

        long sequence = entries.size() + 1L;
        Encoded encoded = encode(sequence, type, canonicalPayload, lastHash);
        byte[] record = (encoded.line() + "\n").getBytes(StandardCharsets.UTF_8);
        if (record.length > MAX_LINE_BYTES) {
            throw new IllegalArgumentException("ecology journal line exceeds 64 KiB");
        }
        if (byteCount > MAX_FILE_BYTES - record.length) {
            throw new IOException("Ecology journal byte capacity is exhausted; ecology must remain held: " + file);
        }

        boolean writeStarted = false;
        try {
            verifyUnchanged();
            channel.position(byteCount);
            ByteBuffer buffer = ByteBuffer.wrap(record);
            while (buffer.hasRemaining()) {
                writeStarted = true;
                channel.write(buffer);
            }
            channel.force(true);

            long newByteCount = byteCount + record.length;
            byte[] newTail = appendTail(expectedTail, record);
            BasicFileAttributes after = attributes(file);
            validateRegularAndBounded(after, file);
            if (!sameIdentity(fileKey, creationTime, after, windowsNullKeyIdentity)
                    || after.size() != newByteCount
                    || channel.size() != newByteCount
                    || !Arrays.equals(newTail, readTail(channel, newByteCount))) {
                throw new IOException("Ecology journal changed during append: " + file);
            }

            Entry entry = new Entry(sequence, type, canonicalPayload);
            entries.add(entry);
            byteCount = newByteCount;
            lastModified = after.lastModifiedTime();
            expectedTail = newTail;
            lastHash = encoded.hash();
            return entry;
        } catch (IOException exception) {
            poison(exception);
            throw exception;
        } catch (RuntimeException exception) {
            if (writeStarted) {
                IOException failure = new IOException(
                        "Ecology journal append has an uncertain outcome: " + file, exception);
                poison(failure);
                throw failure;
            }
            throw exception;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        try {
            if (lock.isValid()) {
                lock.release();
            }
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            channel.close();
        } catch (IOException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void ensureAppendable() throws IOException {
        if (closed) {
            throw new IOException("Ecology journal is closed: " + file);
        }
        if (poisoned) {
            IOException failure = new IOException(
                    "Ecology journal is poisoned after an uncertain append; reopen and inspect it: " + file);
            if (poisonCause != null) failure.initCause(poisonCause);
            throw failure;
        }
    }

    private void verifyUnchanged() throws IOException {
        try {
            if (!lock.isValid()) {
                throw new IOException("Ecology journal lock was lost: " + file);
            }
            BasicFileAttributes current = attributes(file);
            if (!current.isRegularFile()
                    || !sameIdentity(fileKey, creationTime, current, windowsNullKeyIdentity)
                    || current.size() != byteCount || !current.lastModifiedTime().equals(lastModified)
                    || channel.size() != byteCount
                    || !Arrays.equals(expectedTail, readTail(channel, byteCount))) {
                throw new IOException(
                        "Ecology journal was changed outside this instance; ecology must remain held: " + file);
            }
        } catch (IOException exception) {
            poison(exception);
            throw exception;
        }
    }

    private void poison(IOException cause) {
        poisoned = true;
        if (poisonCause == null) poisonCause = cause;
    }

    private static Replay replay(FileChannel channel, long size, Path file) throws IOException {
        ArrayList<Entry> entries = new ArrayList<>();
        String previousHash = GENESIS_HASH;
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        TailBuffer tail = new TailBuffer(TAIL_BYTES);
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        channel.position(0);
        long consumed = 0;
        try {
            while (true) {
                buffer.clear();
                int read = channel.read(buffer);
                if (read < 0) break;
                if (read == 0) continue;
                consumed += read;
                buffer.flip();
                while (buffer.hasRemaining()) {
                    byte value = buffer.get();
                    tail.accept(value);
                    if (value == '\n') {
                        if (line.size() == 0) {
                            throw invalid(file, "empty line");
                        }
                        if (line.size() + 1 > MAX_LINE_BYTES) {
                            throw invalid(file, "line exceeds 64 KiB");
                        }
                        if (entries.size() >= MAX_ENTRIES) {
                            throw invalid(file, "entry count exceeds 150000");
                        }
                        String text = decodeUtf8(line.toByteArray(), file);
                        Decoded decoded = decode(text, entries.size() + 1L, previousHash, file);
                        entries.add(decoded.entry());
                        previousHash = decoded.hash();
                        line.reset();
                    } else {
                        if (line.size() + 2 > MAX_LINE_BYTES) {
                            throw invalid(file, "line exceeds 64 KiB");
                        }
                        line.write(value);
                    }
                }
            }
            if (consumed != size) {
                throw new IOException("Ecology journal changed while it was being replayed: " + file);
            }
            if (line.size() != 0) {
                throw invalid(file, "truncated final line");
            }
        } catch (IllegalArgumentException exception) {
            throw invalid(file, exception.getMessage(), exception);
        }
        return new Replay(List.copyOf(entries), previousHash, tail.bytes());
    }

    private static Decoded decode(String text, long expectedSequence, String expectedPreviousHash, Path file)
            throws IOException {
        JsonElement parsed;
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            parsed = readJson(reader, 0, new JsonBudget());
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("unexpected trailing JSON content");
            }
        } catch (IOException exception) {
            throw invalid(file, "malformed JSON", exception);
        }
        JsonObject envelope = object(parsed, "envelope");
        if (!envelope.keySet().equals(ENVELOPE_FIELDS)) {
            throw invalid(file, "envelope fields must be exactly " + ENVELOPE_FIELDS);
        }
        long schema = integer(envelope.get("schema"), "schema");
        if (schema != SCHEMA) {
            throw invalid(file, "ecology journal schema " + schema + " is not supported");
        }
        long sequence = integer(envelope.get("sequence"), "sequence");
        if (sequence != expectedSequence) {
            throw invalid(file, "sequence must be contiguous at " + expectedSequence);
        }
        String type = string(envelope.get("type"), "type");
        if (!TYPES.contains(type)) {
            throw invalid(file, "unknown ecology journal type: " + type);
        }
        JsonObject payload = object(envelope.get("payload"), "payload");
        String previousHash = hash(envelope.get("previousHash"), "previousHash");
        if (!constantTimeEquals(previousHash, expectedPreviousHash)) {
            throw invalid(file, "previousHash does not match the preceding entry");
        }
        String storedHash = hash(envelope.get("hash"), "hash");
        Encoded encoded = encode(sequence, type, payload, previousHash);
        if (!constantTimeEquals(storedHash, encoded.hash())) {
            throw invalid(file, "hash does not match the envelope");
        }
        if (!text.equals(encoded.line())) {
            throw invalid(file, "line is not canonical JSON");
        }
        return new Decoded(new Entry(sequence, type, payload), storedHash);
    }

    private static Encoded encode(long sequence, String type, JsonObject payload, String previousHash)
            throws IOException {
        JsonObject unsigned = new JsonObject();
        unsigned.addProperty("schema", SCHEMA);
        unsigned.addProperty("sequence", sequence);
        unsigned.addProperty("type", type);
        unsigned.add("payload", payload.deepCopy());
        unsigned.addProperty("previousHash", previousHash);
        String digest = sha256(canonicalJson(unsigned));
        JsonObject complete = unsigned.deepCopy();
        complete.addProperty("hash", digest);
        return new Encoded(canonicalJson(complete), digest);
    }

    private static JsonElement readJson(JsonReader reader, int depth, JsonBudget budget) throws IOException {
        if (depth > MAX_JSON_DEPTH) {
            throw new IllegalArgumentException("JSON depth exceeds " + MAX_JSON_DEPTH);
        }
        budget.node();
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                JsonObject object = new JsonObject();
                HashSet<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    boundedText(name, MAX_KEY_CODEPOINTS, "JSON key");
                    if (!names.add(name)) {
                        throw new IllegalArgumentException("duplicate JSON field: " + name);
                    }
                    object.add(name, readJson(reader, depth + 1, budget));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                JsonArray array = new JsonArray();
                while (reader.hasNext()) array.add(readJson(reader, depth + 1, budget));
                reader.endArray();
                yield array;
            }
            case STRING -> {
                String value = reader.nextString();
                boundedText(value, MAX_STRING_CODEPOINTS, "JSON string");
                yield new JsonPrimitive(value);
            }
            case NUMBER -> canonicalNumber(reader.nextString());
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new IllegalArgumentException("unexpected JSON token " + reader.peek());
        };
    }

    private static JsonElement canonicalize(JsonElement value, int depth, JsonBudget budget) {
        if (value == null) {
            throw new IllegalArgumentException("JSON value is required");
        }
        if (depth > MAX_JSON_DEPTH) {
            throw new IllegalArgumentException("JSON depth exceeds " + MAX_JSON_DEPTH);
        }
        budget.node();
        if (value.isJsonObject()) {
            TreeMap<String, JsonElement> sorted = new TreeMap<>();
            for (var member : value.getAsJsonObject().entrySet()) {
                boundedText(member.getKey(), MAX_KEY_CODEPOINTS, "JSON key");
                sorted.put(member.getKey(), canonicalize(member.getValue(), depth + 1, budget));
            }
            JsonObject result = new JsonObject();
            sorted.forEach(result::add);
            return result;
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement member : value.getAsJsonArray()) {
                result.add(canonicalize(member, depth + 1, budget));
            }
            return result;
        }
        if (value.isJsonNull()) return JsonNull.INSTANCE;
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isString()) {
            String text = primitive.getAsString();
            boundedText(text, MAX_STRING_CODEPOINTS, "JSON string");
            return new JsonPrimitive(text);
        }
        if (primitive.isBoolean()) return new JsonPrimitive(primitive.getAsBoolean());
        if (primitive.isNumber()) return canonicalNumber(primitive.getAsString());
        throw new IllegalArgumentException("unsupported JSON primitive");
    }

    private static JsonPrimitive canonicalNumber(String text) {
        if (text == null || text.length() > MAX_NUMBER_CHARACTERS) {
            throw new IllegalArgumentException("JSON number is too long");
        }
        try {
            BigDecimal number = new BigDecimal(text);
            BigDecimal normalized = number.signum() == 0 ? BigDecimal.ZERO : number.stripTrailingZeros();
            String canonical = normalized.toPlainString();
            if (canonical.length() > MAX_NUMBER_CHARACTERS) {
                throw new IllegalArgumentException("canonical JSON number is too long");
            }
            return new JsonPrimitive(new BigDecimal(canonical));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid JSON number", exception);
        }
    }

    private static String canonicalJson(JsonElement value) {
        return canonicalize(value, 0, new JsonBudget()).toString();
    }

    private static String sha256(String value) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(HASH_TEXT_LENGTH);
            for (byte item : digest) {
                int unsigned = item & 0xff;
                result.append(HEX[unsigned >>> 4]).append(HEX[unsigned & 0x0f]);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }

    private static String hash(JsonElement value, String field) {
        String result = string(value, field);
        if (result.length() != HASH_TEXT_LENGTH) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 hash");
        }
        for (int index = 0; index < result.length(); index++) {
            char character = result.charAt(index);
            if (!((character >= '0' && character <= '9') || (character >= 'a' && character <= 'f'))) {
                throw new IllegalArgumentException(field + " must be a lowercase SHA-256 hash");
            }
        }
        return result;
    }

    private static boolean constantTimeEquals(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.US_ASCII),
                second.getBytes(StandardCharsets.US_ASCII));
    }

    private static JsonObject object(JsonElement value, String field) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(field + " must be a JSON object");
        }
        return value.getAsJsonObject();
    }

    private static String string(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static long integer(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        String text = value.getAsString();
        if (text.isEmpty() || (text.length() > 1 && text.charAt(0) == '0')) {
            throw new IllegalArgumentException(field + " must be a non-negative canonical integer");
        }
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) < '0' || text.charAt(index) > '9') {
                throw new IllegalArgumentException(field + " must be a non-negative canonical integer");
            }
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " is outside long range", exception);
        }
    }

    private static void boundedText(String value, int limit, String field) {
        if (value.codePointCount(0, value.length()) > limit) {
            throw new IllegalArgumentException(field + " exceeds " + limit + " codepoints");
        }
    }

    private static String decodeUtf8(byte[] bytes, Path file) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw invalid(file, "line is not valid UTF-8", exception);
        }
    }

    private static BasicFileAttributes attributes(Path file) throws IOException {
        return Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static void validateRegularAndBounded(BasicFileAttributes attributes, Path file) throws IOException {
        if (!attributes.isRegularFile()) {
            throw new IOException("Ecology journal must be a regular non-symlink file: " + file);
        }
        if (attributes.size() > MAX_FILE_BYTES) {
            throw new IOException("Ecology journal exceeds 64 MiB and was not changed: " + file);
        }
    }

    private static void requireSameFileState(BasicFileAttributes before, BasicFileAttributes after,
                                             boolean windowsNullKeyIdentity, Path file, String context)
            throws IOException {
        if (!after.isRegularFile()
                || !sameIdentity(before.fileKey(), before.creationTime(), after, windowsNullKeyIdentity)
                || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
            throw new IOException("Ecology journal changed " + context + ": " + file);
        }
    }

    private static boolean sameIdentity(Object expectedFileKey, FileTime expectedCreationTime,
                                        BasicFileAttributes current, boolean windowsNullKeyIdentity) {
        if (expectedFileKey != null) {
            return expectedFileKey.equals(current.fileKey());
        }
        return windowsNullKeyIdentity && current.fileKey() == null
                && expectedCreationTime.equals(current.creationTime());
    }

    private static FileChannel openChannel(Path file, boolean create) throws IOException {
        Set<OpenOption> options = new LinkedHashSet<>();
        if (create) options.add(StandardOpenOption.CREATE_NEW);
        options.add(StandardOpenOption.READ);
        options.add(StandardOpenOption.WRITE);
        options.add(LinkOption.NOFOLLOW_LINKS);
        if (WINDOWS_NO_SHARE_DELETE != null) options.add(WINDOWS_NO_SHARE_DELETE);
        return FileChannel.open(file, options);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static OpenOption windowsNoShareDelete() {
        if (!WINDOWS) return null;
        try {
            Class type = Class.forName("com.sun.nio.file.ExtendedOpenOption")
                    .asSubclass(Enum.class);
            Object option = Enum.valueOf(type, "NOSHARE_DELETE");
            return option instanceof OpenOption openOption ? openOption : null;
        } catch (ClassNotFoundException | IllegalArgumentException | SecurityException | LinkageError exception) {
            return null;
        }
    }

    static boolean windowsDeleteShareProtectionAvailable() {
        return WINDOWS && WINDOWS_NO_SHARE_DELETE != null;
    }

    private static byte[] readTail(FileChannel channel, long size) throws IOException {
        int length = (int) Math.min(size, TAIL_BYTES);
        byte[] result = new byte[length];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        long offset = size - length;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, offset + buffer.position());
            if (read < 0) throw new IOException("Ecology journal ended while verifying its tail");
            if (read == 0) continue;
        }
        return result;
    }

    private static byte[] appendTail(byte[] oldTail, byte[] appended) {
        int length = Math.min(TAIL_BYTES, oldTail.length + appended.length);
        byte[] result = new byte[length];
        int combinedLength = oldTail.length + appended.length;
        int start = combinedLength - length;
        for (int index = 0; index < length; index++) {
            int source = start + index;
            result[index] = source < oldTail.length ? oldTail[source] : appended[source - oldTail.length];
        }
        return result;
    }

    private static IOException invalid(Path file, String detail) {
        return new IOException("Ecology journal is invalid and was not changed: " + file + " (" + detail + ")");
    }

    private static IOException invalid(Path file, String detail, Throwable cause) {
        return new IOException("Ecology journal is invalid and was not changed: " + file + " (" + detail + ")", cause);
    }

    private static void releaseQuietly(FileLock lock) {
        if (lock == null) return;
        try { lock.release(); } catch (IOException ignored) { }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) return;
        try { channel.close(); } catch (IOException ignored) { }
    }

    /** Immutable compact evidence. Parsed payload trees live only while a consumer uses them. */
    public static final class Entry {
        private final long sequence;
        private final String type;
        private final String payloadJson;

        public Entry(long sequence, String type, JsonObject payload) {
            if (sequence < 1) throw new IllegalArgumentException("entry sequence must be positive");
            if (type == null || !TYPES.contains(type)) {
                throw new IllegalArgumentException("unknown ecology journal type: " + type);
            }
            if (payload == null) throw new IllegalArgumentException("entry payload is required");
            this.sequence=sequence;this.type=type;
            this.payloadJson=canonicalJson(canonicalize(payload,1,new JsonBudget()));
        }
        public long sequence(){return sequence;}
        public String type(){return type;}
        public JsonObject payload() {
            // This immutable string was bounded and canonicalized at construction; it is never
            // supplied by a caller or a file without strict validation of the full envelope.
            return com.google.gson.JsonParser.parseString(payloadJson).getAsJsonObject();
        }
        @Override public boolean equals(Object other){return other instanceof Entry e&&sequence==e.sequence&&type.equals(e.type)&&payloadJson.equals(e.payloadJson);}
        @Override public int hashCode(){return java.util.Objects.hash(sequence,type,payloadJson);}
        @Override public String toString(){return "Entry[sequence="+sequence+", type="+type+", payload="+payloadJson+"]";}
    }

    private record Encoded(String line, String hash) { }
    private record Decoded(Entry entry, String hash) { }
    private record Replay(List<Entry> entries, String lastHash, byte[] tail) { }

    private static final class JsonBudget {
        private static final int MAX_NODES = 8_192;
        private int nodes;

        private void node() {
            nodes++;
            if (nodes > MAX_NODES) throw new IllegalArgumentException("JSON node count exceeds " + MAX_NODES);
        }
    }

    private static final class TailBuffer {
        private final byte[] values;
        private int start;
        private int size;

        private TailBuffer(int capacity) {
            values = new byte[capacity];
        }

        private void accept(byte value) {
            if (size < values.length) {
                values[(start + size) % values.length] = value;
                size++;
            } else {
                values[start] = value;
                start = (start + 1) % values.length;
            }
        }

        private byte[] bytes() {
            byte[] result = new byte[size];
            for (int index = 0; index < size; index++) {
                result[index] = values[(start + index) % values.length];
            }
            return result;
        }
    }
}
