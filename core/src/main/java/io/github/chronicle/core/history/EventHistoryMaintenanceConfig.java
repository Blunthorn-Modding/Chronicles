package io.github.chronicle.core.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/** Strict, create-only global policy for automatic Event-history maintenance. */
public record EventHistoryMaintenanceConfig(boolean enabled, int quietSeconds, int intervalSeconds) {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_FILE_BYTES = 64L * 1024;
    public static final EventHistoryMaintenanceConfig DEFAULT = new EventHistoryMaintenanceConfig(true, 60, 300);
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public EventHistoryMaintenanceConfig {
        if (quietSeconds < 1 || quietSeconds > 3600) throw new IllegalArgumentException("quietSeconds must be between 1 and 3600");
        if (intervalSeconds < quietSeconds || intervalSeconds > 86400) throw new IllegalArgumentException("intervalSeconds must be between quietSeconds and 86400");
    }

    /** Loads an existing complete policy, or atomically creates defaults without replacing any evidence. */
    public static EventHistoryMaintenanceConfig loadOrCreate(Path file) throws IOException {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException("event history maintenance config file is required");
        Path target = file.toAbsolutePath().normalize();
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return read(target);
        return create(target);
    }

    public static String encode(EventHistoryMaintenanceConfig config) {
        if (config == null) throw new IllegalArgumentException("event history maintenance config is required");
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("enabled", config.enabled());
        root.addProperty("quietSeconds", config.quietSeconds());
        root.addProperty("intervalSeconds", config.intervalSeconds());
        return JSON.toJson(root);
    }

    public static EventHistoryMaintenanceConfig decode(String source) {
        JsonObject root = BookConfig.strictObject(source);
        if (!root.keySet().equals(Set.of("schemaVersion", "enabled", "quietSeconds", "intervalSeconds")))
            throw new IllegalArgumentException("event history maintenance config fields do not match schema");
        if (integer(root.get("schemaVersion"), "schemaVersion") != SCHEMA_VERSION)
            throw new IllegalArgumentException("unsupported event history maintenance schemaVersion");
        return new EventHistoryMaintenanceConfig(bool(root.get("enabled"), "enabled"), integer(root.get("quietSeconds"), "quietSeconds"), integer(root.get("intervalSeconds"), "intervalSeconds"));
    }

    private static EventHistoryMaintenanceConfig read(Path file) throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("event history maintenance config must be a regular non-link file: " + file);
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("event history maintenance config exceeds 64 KiB: " + file);
        verifyParents(file.getParent());
        try(var channel=FileChannel.open(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            if(channel.size()>MAX_FILE_BYTES)throw new IOException("event history maintenance config exceeds 64 KiB");
            ByteBuffer bytes=ByteBuffer.allocate((int)channel.size());while(bytes.hasRemaining())if(channel.read(bytes)<0)throw new IOException("event history config changed during read");if(channel.size()!=bytes.capacity())throw new IOException("event history config changed during read");bytes.flip();
            return decode(StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(bytes).toString());
        }
        catch (RuntimeException failure) { throw new IOException("event history maintenance config is invalid and was preserved: " + file + " (" + failure.getMessage() + ")", failure); }
    }

    private static EventHistoryMaintenanceConfig create(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent == null) throw new IOException("event history maintenance config has no parent directory");
        verifyParents(parent);
        if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) throw new IOException("event history maintenance config directory must be a non-link directory: " + parent);
        } else {
            Files.createDirectories(parent);
            if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) throw new IOException("event history maintenance config directory creation was unsafe: " + parent);
        }
        byte[] content = encode(DEFAULT).getBytes(StandardCharsets.UTF_8);
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try {
            forceWrite(temporary, content);
            try { Files.createLink(file,temporary); }
            catch (FileAlreadyExistsException appeared) { throw new IOException("event history maintenance config appeared while creating it; refusing to overwrite " + file, appeared); }
            catch (UnsupportedOperationException unsupported) { throw new IOException("create-only config publication is not supported: " + file, unsupported); }
        } finally { Files.deleteIfExists(temporary); }
        return DEFAULT;
    }

    private static void verifyParents(Path parent)throws IOException{
        Path cursor=parent.toAbsolutePath().getRoot();for(Path part:parent.toAbsolutePath()){cursor=cursor.resolve(part);if(Files.exists(cursor,LinkOption.NOFOLLOW_LINKS)&&(Files.isSymbolicLink(cursor)||!Files.isDirectory(cursor,LinkOption.NOFOLLOW_LINKS)))throw new IOException("Event history config parents must be directories without links: "+cursor);}
    }

    private static void forceWrite(Path file, byte[] content) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer bytes = ByteBuffer.wrap(content);
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
    }
    private static boolean bool(JsonElement value, String field) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(field + " must be a boolean"); return value.getAsBoolean(); }
    private static int integer(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException(field + " must be an integer");
        try { return Math.toIntExact(Long.parseLong(value.getAsString())); } catch (NumberFormatException | ArithmeticException failure) { throw new IllegalArgumentException(field + " is outside integer range", failure); }
    }
}
