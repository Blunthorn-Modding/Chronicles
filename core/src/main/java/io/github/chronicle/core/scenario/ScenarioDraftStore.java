package io.github.chronicle.core.scenario;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/** Controlled, content-only scenario draft storage. Callers never supply a filesystem destination. */
public final class ScenarioDraftStore {
    public static final long MAX_FILE_BYTES = ScenarioContentJson.MAX_BYTES;
    private final Path root; private final String scenarioId; private byte[] observed;
    public ScenarioDraftStore(Path controlledRoot, String scenarioId) {
        if (controlledRoot == null || scenarioId == null) throw new IllegalArgumentException("controlled root and scenario ID are required");
        // ScenarioContent owns the canonical ID validation.
        new ScenarioContent(scenarioId, java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());
        root = controlledRoot.toAbsolutePath().normalize(); this.scenarioId = scenarioId;
    }
    public synchronized Optional<ScenarioContent> load() throws IOException {
        Path target = draftPath(); verifyExistingDirectories(target.getParent()); if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { observed = null; return Optional.empty(); }
        byte[] bytes = readRegular(target); ScenarioContent content = decode(bytes, target); if (!scenarioId.equals(content.scenarioId())) throw new IOException("scenario draft identity does not match controlled path"); observed = bytes.clone(); return Optional.of(content);
    }
    public synchronized void save(ScenarioContent content) throws IOException {
        requireIdentity(content); Path target = draftPath(); prepare(target.getParent()); byte[] next = bytes(content); boolean present = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if ((observed == null) != !present) throw new IOException("scenario draft presence changed outside this store; reload before saving");
        byte[] old = present ? readRegular(target) : null; if (present && !MessageDigest.isEqual(observed, old)) throw new IOException("scenario draft changed outside this store; reload before saving");
        Path temp = Files.createTempFile(target.getParent(), ".scenario-draft-", ".tmp"), backupTemp = null;
        try { force(temp, next); if (old != null) { Path backup = target.resolveSibling(target.getFileName() + ".bak"); if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) rejectExistingFile(backup); backupTemp = Files.createTempFile(target.getParent(), ".scenario-backup-", ".tmp"); force(backupTemp, old); move(backupTemp, backup); backupTemp = null; verify(target, old); move(temp, target); } else { try { Files.createLink(target, temp); } catch (java.nio.file.FileAlreadyExistsException race) { throw new IOException("scenario draft appeared before publication; refusing overwrite", race); } } observed = next.clone(); }
        finally { delete(temp); delete(backupTemp); }
    }
    /** Atomically creates a deterministic immutable build-input export; an existing unequal hash name is never replaced. */
    public synchronized Path export(ScenarioContent content) throws IOException {
        requireIdentity(content); byte[] bytes = bytes(content); String hash = hash(bytes); Path target = exportPath(hash); prepare(target.getParent());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { byte[] current = readRegular(target); if (!MessageDigest.isEqual(current, bytes)) throw new IOException("immutable scenario export hash collision"); return target; }
        Path temp = Files.createTempFile(target.getParent(), ".scenario-export-", ".tmp");
        try { force(temp, bytes); try { Files.createLink(target, temp); } catch (java.nio.file.FileAlreadyExistsException race) { byte[] current = readRegular(target); if (!MessageDigest.isEqual(current, bytes)) throw new IOException("immutable scenario export appeared with different bytes", race); } return target; }
        finally { delete(temp); }
    }
    public Path draftFile() { return draftPath(); }
    private Path draftPath() { return inside(root.resolve("scenario-content").resolve(token()).resolve("draft.json")); }
    private Path exportPath(String hash) { return inside(root.resolve("scenario-content").resolve(token()).resolve("exports").resolve(hash + ".json")); }
    private String token() { return hash(scenarioId.getBytes(StandardCharsets.UTF_8)); }
    private Path inside(Path value) { Path result = value.toAbsolutePath().normalize(); if (!result.startsWith(root)) throw new IllegalArgumentException("scenario storage path escapes its controlled root"); return result; }
    private void prepare(Path directory) throws IOException { verifyExistingDirectories(root); if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) { if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("scenario storage root must be a directory, not a symbolic link: " + root); } else Files.createDirectories(root); Path relative = root.relativize(directory); Path cursor = root; for (Path part : relative) { cursor = cursor.resolve(part); if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) { if (Files.isSymbolicLink(cursor) || !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)) throw new IOException("scenario storage directory must not be a symbolic link or special file: " + cursor); } else Files.createDirectory(cursor); } }
    private static void verifyExistingDirectories(Path directory) throws IOException { Path absolute = directory.toAbsolutePath().normalize(), cursor = absolute.getRoot(); if (cursor == null) throw new IOException("scenario storage path has no filesystem root: " + absolute); for (Path part : absolute) { cursor = cursor.resolve(part); if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(cursor) || !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS))) throw new IOException("scenario storage ancestor must be a non-link directory: " + cursor); } }
    private static void rejectExistingFile(Path path) throws IOException { if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("scenario backup must be a regular non-link file: " + path); }
    private static byte[] readRegular(Path path) throws IOException { if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_FILE_BYTES) throw new IOException("scenario content path is not a bounded regular file: " + path); return Files.readAllBytes(path); }
    private static ScenarioContent decode(byte[] bytes, Path path) throws IOException { try { return ScenarioContentJson.decode(strictUtf8(bytes)); } catch (RuntimeException | CharacterCodingException exception) { throw new IOException("scenario content is invalid and was not replaced: " + path, exception); } }
    private static String strictUtf8(byte[] bytes) throws CharacterCodingException { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
    private static byte[] bytes(ScenarioContent content) throws IOException { String encoded = ScenarioContentJson.encode(content); byte[] bytes = encoded.getBytes(StandardCharsets.UTF_8); if (bytes.length > MAX_FILE_BYTES) throw new IOException("scenario content exceeds 16 MiB limit"); return bytes; }
    private void requireIdentity(ScenarioContent content) { if (content == null || !scenarioId.equals(content.scenarioId())) throw new IllegalArgumentException("scenario content identity does not match this controlled store"); }
    private static void force(Path path, byte[] bytes) throws IOException { try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) { ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true); } }
    private static void move(Path source, Path target) throws IOException { try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException exception) { throw new IOException("atomic scenario content replacement is unsupported: " + target, exception); } }
    private static void verify(Path target, byte[] expected) throws IOException { if (!MessageDigest.isEqual(expected, readRegular(target))) throw new IOException("scenario draft changed before publication; refusing overwrite"); }
    private static String hash(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); } }
    private static void delete(Path path) { if (path != null) try { Files.deleteIfExists(path); } catch (IOException ignored) { } }
}
