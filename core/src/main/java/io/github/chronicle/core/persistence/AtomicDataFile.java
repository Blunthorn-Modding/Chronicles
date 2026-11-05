package io.github.chronicle.core.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Bounded observed-byte store. World/configuration files must be edited only while stopped. */
public final class AtomicDataFile {
    private final Path file;
    private final int maxBytes;
    private final TempCleanup cleanup;
    @FunctionalInterface interface TempCleanup { void delete(Path path) throws IOException; }
    private Boolean observedPresent;
    private byte[] observedHash;

    public AtomicDataFile(Path file, long maxBytes) {
        this(file, maxBytes, Files::deleteIfExists);
    }

    AtomicDataFile(Path file, long maxBytes, TempCleanup cleanup) {
        if (file == null || file.getFileName() == null || maxBytes < 1 || maxBytes > 16 * 1024 * 1024)
            throw new IllegalArgumentException("A data file and byte limit between 1 byte and 16 MiB are required.");
        this.file = file.toAbsolutePath().normalize();
        this.maxBytes = Math.toIntExact(maxBytes);
        this.cleanup = java.util.Objects.requireNonNull(cleanup);
    }

    /** Null means absent. A failed read never establishes permission to overwrite corrupt data. */
    public synchronized byte[] load() throws IOException {
        directories(file.getParent());
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            observedPresent = false; observedHash = null; return null;
        }
        byte[] bytes = read();
        observedPresent = true; observedHash = hash(bytes); return bytes;
    }

    public synchronized void verifyUnchanged() throws IOException {
        if (observedPresent == null) throw new IOException("Read the current data before saving.");
        directories(file.getParent());
        boolean present = Files.exists(file, LinkOption.NOFOLLOW_LINKS);
        if (present != observedPresent || present && !MessageDigest.isEqual(observedHash, hash(read())))
            throw new IOException("Data changed outside this store; existing file was preserved: " + file);
    }

    /** The caller validates its codec before calling this method. Publish before updating memory. */
    public synchronized void save(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 1 || bytes.length > maxBytes)
            throw new IOException("Serialized data exceeds its byte bounds.");
        verifyUnchanged();
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        boolean published = false;
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            verifyUnchanged();
            if (observedPresent) {
                try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) { throw new IOException("Atomic replacement is unavailable: " + file, unsupported); }
            } else {
                // Create-only publication cannot overwrite a file created after the last observation.
                Files.createLink(file, temporary);
            }
            observedPresent = true; observedHash = hash(bytes);
            published = true;
        } finally {
            // Once published, cleanup must not report the authoritative write as failed.
            try { cleanup.delete(temporary); }
            catch (IOException cleanup) { if (!published) throw cleanup; }
        }
    }

    private byte[] read() throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Data must be a regular file: " + file);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long length = channel.size();
            if (length < 1 || length > maxBytes) throw new IOException("Data size is outside its bounds: " + file);
            ByteBuffer buffer = ByteBuffer.allocate((int) length);
            while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw new IOException("Data changed during read: " + file);
            if (channel.size() != length) throw new IOException("Data changed during read: " + file);
            return buffer.array();
        }
    }

    private static void directories(Path directory) throws IOException {
        if (directory == null) throw new IOException("A parent directory is required.");
        Path cursor = directory.getRoot();
        for (Path part : directory) {
            cursor = cursor.resolve(part);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Data directory contains a link or non-directory: " + cursor);
        }
    }

    private static byte[] hash(byte[] bytes) throws IOException {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new IOException("SHA-256 unavailable", impossible); }
    }
}
