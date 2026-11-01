package io.github.chronicle.core.scenario;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Pure-Java build tool for checking and copying a portable scenario build input. */
public final class ScenarioContentTool {
    private ScenarioContentTool() { }
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "validate".equals(args[0])) { validate(input(args[1])); return; }
        if (args.length == 4 && "import".equals(args[0])) { importFile(input(args[1]), output(args[2]), args[3]); return; }
        throw new IllegalArgumentException("usage: ScenarioContentTool validate <absolute-input.json> | import <absolute-input.json> <absolute-output.json> <expected-scenario-id>");
    }
    public static ScenarioContent validate(Path input) throws IOException {
        byte[] bytes = readRegular(input, "scenario input");
        ScenarioContent content = decode(bytes, input);
        String canonical = ScenarioContentJson.encode(content);
        if (!ScenarioContentJson.decode(canonical).equals(content)) throw new IOException("scenario content codec did not preserve the validated bundle");
        return content;
    }
    /** Writes only a canonical, validated bundle to the Gradle-provided resource target. */
    public static void importFile(Path input, Path target, String expectedScenarioId) throws IOException {
        ScenarioContent content = validate(input); if (!content.scenarioId().equals(expectedScenarioId)) throw new IOException("scenario input ID does not match the controlled build target"); byte[] next = ScenarioContentJson.encode(content).getBytes(StandardCharsets.UTF_8);
        if (next.length > ScenarioContentJson.MAX_BYTES) throw new IOException("scenario content exceeds 16 MiB limit");
        requireAbsolute(target, "scenario output"); Path absolute = target.toAbsolutePath().normalize(); verifyDirectories(absolute.getParent());
        byte[] observed = null;
        if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            observed = readRegular(absolute, "existing scenario output");
            decode(observed, absolute); // Never replace invalid reviewed resource evidence.
        }
        Files.createDirectories(absolute.getParent()); verifyDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), ".scenario-content-", ".tmp");
        try {
            force(temporary, next);
            if (observed != null) {
                if (!Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)
                        || !java.security.MessageDigest.isEqual(observed, readRegular(absolute, "existing scenario output")))
                    throw new IOException("scenario output changed during import; refusing overwrite");
                move(temporary, absolute);
            } else {
                try { Files.createLink(absolute, temporary); }
                catch (FileAlreadyExistsException raced) { throw new IOException("scenario output appeared during import; refusing overwrite", raced); }
            }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static Path input(String value) { return requireAbsolute(Path.of(value), "scenario input"); }
    private static Path output(String value) { return requireAbsolute(Path.of(value), "scenario output"); }
    private static Path requireAbsolute(Path path, String name) { if (path == null || !path.isAbsolute()) throw new IllegalArgumentException(name + " path must be absolute"); return path.toAbsolutePath().normalize(); }
    private static byte[] readRegular(Path path, String name) throws IOException {
        Path absolute = requireAbsolute(path, name); verifyDirectories(absolute.getParent());
        if (Files.isSymbolicLink(absolute) || !Files.isRegularFile(absolute, LinkOption.NOFOLLOW_LINKS) || Files.size(absolute) > ScenarioContentJson.MAX_BYTES) throw new IOException(name + " must be a bounded regular non-link file: " + absolute);
        return Files.readAllBytes(absolute);
    }
    private static ScenarioContent decode(byte[] bytes, Path path) throws IOException { try { return ScenarioContentJson.decode(strictUtf8(bytes)); } catch (RuntimeException | CharacterCodingException exception) { throw new IOException("invalid scenario content was not replaced: " + path, exception); } }
    private static String strictUtf8(byte[] bytes) throws CharacterCodingException { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
    private static void verifyDirectories(Path directory) throws IOException { if (directory == null) throw new IOException("scenario path has no parent directory"); Path absolute = directory.toAbsolutePath().normalize(), cursor = absolute.getRoot(); if (cursor == null) throw new IOException("scenario path has no filesystem root"); for (Path part : absolute) { cursor = cursor.resolve(part); if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(cursor) || !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS))) throw new IOException("scenario path ancestor must be a non-link directory: " + cursor); } }
    private static void force(Path path, byte[] bytes) throws IOException { try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) { ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true); } }
    private static void move(Path source, Path target) throws IOException { try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException exception) { throw new IOException("atomic scenario resource replacement is unsupported: " + target, exception); } }
}
