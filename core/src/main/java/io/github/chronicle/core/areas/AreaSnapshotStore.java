package io.github.chronicle.core.areas;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Content-addressed immutable payload store. Existing payloads are verified, never overwritten. */
public final class AreaSnapshotStore {
    private final Path directory;
    public AreaSnapshotStore(Path directory) {
        if (directory == null) throw new IllegalArgumentException("snapshot directory is required");
        this.directory = directory.toAbsolutePath().normalize();
    }

    public SnapshotRef put(AreaSnapshot snapshot) throws IOException {
        AreaSnapshotCodec.Encoded encoded = AreaSnapshotCodec.encode(snapshot);
        SnapshotRef ref = encoded.ref(); Path target = path(ref);
        Files.createDirectories(directory);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { verifyExisting(target, ref); return ref; }
        Path temporary = Files.createTempFile(directory, ".snapshot-", ".tmp");
        try {
            forceWrite(temporary, encoded.bytes());
            // A same-directory hard link is an atomic create-new operation. Unlike move on
            // some providers it cannot replace a payload that won the publication race.
            try { Files.createLink(target, temporary); }
            catch (FileAlreadyExistsException race) { verifyExisting(target, ref); }
            verifyExisting(target, ref); return ref;
        } finally { Files.deleteIfExists(temporary); }
    }

    public AreaSnapshot load(SnapshotRef ref) throws IOException {
        Path file = path(ref);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("snapshot payload is missing: " + ref.sha256());
        if (Files.size(file) != ref.encodedBytes()) throw new IOException("snapshot payload length is corrupt: " + ref.sha256());
        try { return AreaSnapshotCodec.decode(Files.readAllBytes(file), ref); }
        catch (RuntimeException invalid) { throw new IOException("snapshot payload is corrupt: " + ref.sha256(), invalid); }
    }

    public Path path(SnapshotRef ref) { return directory.resolve(ref.sha256() + ".bin"); }
    private void verifyExisting(Path target, SnapshotRef ref) throws IOException { load(ref); }
    private static void forceWrite(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer=ByteBuffer.wrap(bytes); while(buffer.hasRemaining())channel.write(buffer); channel.force(true);
        }
    }
}
