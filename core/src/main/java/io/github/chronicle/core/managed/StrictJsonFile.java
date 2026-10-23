package io.github.chronicle.core.managed;

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
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/** Package-local strict, conflict-aware atomic JSON persistence used by both managed stores. */
final class StrictJsonFile {
    static final int MAX_BYTES = 1_048_576;
    private final Path file;
    private Boolean observedPresent;
    private byte[] observedFingerprint;

    StrictJsonFile(Path file) {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException("managed JSON file is required");
        this.file = file.toAbsolutePath().normalize();
    }

    synchronized <T> T load(T absent, Decoder<T> decoder) throws IOException {
        if (!Files.exists(file)) {
            observedPresent = false;
            observedFingerprint = null;
            return absent;
        }
        byte[] bytes = readCurrent();
        T result = decode(bytes, decoder);
        observedPresent = true;
        observedFingerprint = fingerprint(bytes);
        return result;
    }

    synchronized <T> void save(String encoded, Decoder<T> decoder) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);
        boolean present = Files.exists(file);
        if (observedPresent != null && observedPresent != present)
            throw new IOException("managed JSON file presence changed outside this store; refusing to overwrite " + file);
        byte[] current = null;
        if (present) {
            current = readCurrent();
            if (observedFingerprint != null && !MessageDigest.isEqual(observedFingerprint, fingerprint(current)))
                throw new IOException("managed JSON file changed outside this store; refusing to overwrite " + file);
            decode(current, decoder);
        }
        byte[] next = encoded.getBytes(StandardCharsets.UTF_8);
        if (next.length > MAX_BYTES) throw new IOException("managed JSON serialization exceeds 1 MiB: " + file);
        decode(next, decoder);
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        Path backupTemporary = null;
        try {
            forceWrite(temporary, next);
            if (current != null) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                backupTemporary = Files.createTempFile(parent, backup.getFileName().toString(), ".tmp");
                forceWrite(backupTemporary, current);
                moveReplace(backupTemporary, backup);
                backupTemporary = null;
            }
            moveReplace(temporary, file);
            observedPresent = true;
            observedFingerprint = fingerprint(next);
        } finally {
            deleteQuietly(temporary);
            deleteQuietly(backupTemporary);
        }
    }

    private byte[] readCurrent() throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("managed JSON path is not a regular file: " + file);
        if (Files.size(file) > MAX_BYTES) throw new IOException("managed JSON file exceeds 1 MiB: " + file);
        return Files.readAllBytes(file);
    }

    private static <T> T decode(byte[] bytes, Decoder<T> decoder) throws IOException {
        try {
            String source = new String(bytes, StandardCharsets.UTF_8);
            validateStrictJson(source);
            JsonElement parsed;
            try (JsonReader reader = new JsonReader(new StringReader(source))) {
                reader.setStrictness(Strictness.STRICT);
                parsed = JsonParser.parseReader(reader);
                if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("unexpected trailing JSON content");
            }
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("root must be an object");
            return decoder.decode(parsed.getAsJsonObject());
        } catch (RuntimeException exception) {
            throw new IOException("managed JSON is invalid (" + exception.getMessage() + ")", exception);
        }
    }

    private static void validateStrictJson(String source) {
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            validateValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("unexpected trailing JSON content");
        } catch (IOException exception) {
            throw new IllegalArgumentException("unable to read JSON", exception);
        }
    }

    private static void validateValue(JsonReader reader, int depth) throws IOException {
        if (depth > 32) throw new IllegalArgumentException("JSON nesting exceeds 32");
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
            case STRING -> reader.nextString();
            case NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException("unexpected JSON token: " + reader.peek());
        }
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
        catch (AtomicMoveNotSupportedException exception) { throw new IOException("atomic replacement unavailable: " + target, exception); }
    }
    private static void deleteQuietly(Path target) { if (target != null) try { Files.deleteIfExists(target); } catch (IOException ignored) { } }
    private static byte[] fingerprint(byte[] bytes) throws IOException {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException exception) { throw new IOException("SHA-256 unavailable", exception); }
    }

    interface Decoder<T> { T decode(JsonObject object); }
}
