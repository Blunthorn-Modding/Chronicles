package io.github.chronicle.core.history;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/** Create-only, exact per-ID evidence. Publication is safe to retry before a live record is pruned. */
public final class ImmutableHistoryArchive {
    private static final int MAGIC = 0x43484953;
    private static final int SCHEMA = 1;
    private static final int MAX_ID_BYTES = 1024;
    private static final int OVERHEAD_BYTES = 2048;

    private final Path directory;
    private final String kind;
    private final int maxPayloadBytes;

    public ImmutableHistoryArchive(Path directory, String kind, int maxPayloadBytes) {
        this.directory = Objects.requireNonNull(directory, "history directory is required").toAbsolutePath().normalize();
        if (kind == null || !kind.matches("[a-z0-9_.-]{1,64}")) throw new IllegalArgumentException("history kind is invalid");
        if (maxPayloadBytes < 1) throw new IllegalArgumentException("history payload limit must be positive");
        this.kind = kind;
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public synchronized void publish(String id, byte[] payload) throws IOException {
        validateId(id);
        byte[] value = checkedPayload(payload);
        Path target = path(id);
        validatePath();
        Files.createDirectories(directory);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            requireExact(target, id, value);
            return;
        }
        byte[] encoded = encode(id, value);
        Path temporary = Files.createTempFile(directory, ".history-", ".tmp");
        try {
            force(temporary, encoded);
            try { Files.createLink(target, temporary); }
            catch (FileAlreadyExistsException race) { requireExact(target, id, value); }
            requireExact(target, id, value);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public synchronized Optional<byte[]> read(String id) throws IOException {
        validateId(id);
        Path target = path(id);
        validatePath();
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        return Optional.of(decode(target, id));
    }

    private void requireExact(Path target, String id, byte[] expected) throws IOException {
        byte[] actual = decode(target, id);
        if (!MessageDigest.isEqual(actual, expected)) {
            throw new IOException("immutable " + kind + " history conflicts with operation ID " + id);
        }
    }

    private byte[] decode(Path target, String expectedId) throws IOException {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("immutable " + kind + " history is not a regular file: " + target);
        }
        long size = Files.size(target);
        if (size < 1 || size > (long) maxPayloadBytes + OVERHEAD_BYTES) {
            throw new IOException("immutable " + kind + " history size is invalid: " + target);
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(target)))) {
            if (input.readInt() != MAGIC || input.readInt() != SCHEMA) throw new IOException("immutable history magic/schema is invalid");
            String actualKind = text(input, 64, "history kind");
            String actualId = text(input, MAX_ID_BYTES, "history ID");
            int length = input.readInt();
            if (length < 0 || length > maxPayloadBytes) throw new IOException("immutable history payload length is invalid");
            byte[] payload = input.readNBytes(length);
            byte[] expectedHash = input.readNBytes(32);
            if (payload.length != length || expectedHash.length != 32 || input.read() != -1) throw new IOException("immutable history is truncated or has trailing data");
            if (!kind.equals(actualKind) || !expectedId.equals(actualId)) throw new IOException("immutable history identity does not match its lookup");
            if (!MessageDigest.isEqual(expectedHash, sha(payload))) throw new IOException("immutable history payload hash does not match");
            return payload;
        } catch (IllegalArgumentException failure) {
            throw new IOException("immutable " + kind + " history is invalid", failure);
        }
    }

    private byte[] encode(String id, byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC); output.writeInt(SCHEMA); text(output, kind); text(output, id);
            output.writeInt(payload.length); output.write(payload); output.write(sha(payload));
        }
        return bytes.toByteArray();
    }

    private Path path(String id) throws IOException {
        return directory.resolve(HexFormat.of().formatHex(sha(id.getBytes(StandardCharsets.UTF_8))) + ".bin");
    }

    private void validatePath() throws IOException {
        for (Path path = directory; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) throw new IOException("immutable " + kind + " history path cannot contain symbolic links");
        }
    }

    private byte[] checkedPayload(byte[] payload) {
        if (payload == null || payload.length < 1 || payload.length > maxPayloadBytes) {
            throw new IllegalArgumentException("immutable history payload is outside limits");
        }
        return Arrays.copyOf(payload, payload.length);
    }

    private static void validateId(String id) {
        if (id == null || id.isEmpty() || id.getBytes(StandardCharsets.UTF_8).length > MAX_ID_BYTES || id.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("immutable history ID is invalid");
        }
    }

    private static void force(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static void text(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); output.writeInt(bytes.length); output.write(bytes);
    }

    private static String text(DataInputStream input, int maximumBytes, String label) throws IOException {
        int length = input.readInt();
        if (length < 1 || length > maximumBytes) throw new IOException(label + " length is invalid");
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException(label + " is truncated");
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) throw new IOException(label + " is not valid UTF-8");
        return value;
    }

    private static byte[] sha(byte[] bytes) throws IOException {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new IOException("SHA-256 is unavailable", impossible); }
    }
}
