package io.github.chronicle.core.profile;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Strict, bounded JSON persistence for UUID-keyed character profiles. */
public final class ProfileStore {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_FILE_BYTES = 16L * 1024;
    private static final Set<String> FIELDS = Set.of("schemaVersion", "owner", "name", "pronouns", "description", "revision");
    private final Path directory;

    public ProfileStore(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("profile directory is required");
        }
        this.directory = directory.toAbsolutePath().normalize();
    }

    public Optional<CharacterProfile> load(UUID owner) throws IOException {
        requireOwner(owner);
        Path file = file(owner);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("Profile state path is not a regular file: " + file);
        }
        if (Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("Profile state exceeds 16 KiB limit: " + file);
        }
        try {
            CharacterProfile profile = decode(Files.readString(file, StandardCharsets.UTF_8));
            if (!profile.owner().equals(owner)) {
                throw new IllegalArgumentException("profile owner does not match requested UUID");
            }
            return Optional.of(profile);
        } catch (RuntimeException exception) {
            throw new IOException("Profile state is invalid and was not replaced: " + file + " (" + exception.getMessage() + ")", exception);
        }
    }

    public void save(CharacterProfile profile) throws IOException {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required");
        }
        Path file = file(profile.owner());
        Files.createDirectories(directory);
        if (Files.exists(file)) {
            load(profile.owner());
        }
        byte[] bytes = encode(profile).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Profile serialization exceeds 16 KiB limit: " + file);
        }
        Path temporary = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
        Path backupTemporary = null;
        try {
            forceWrite(temporary, bytes);
            if (Files.exists(file)) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                backupTemporary = Files.createTempFile(directory, backup.getFileName().toString(), ".tmp");
                Files.copy(file, backupTemporary, StandardCopyOption.REPLACE_EXISTING);
                force(backupTemporary);
                moveReplace(backupTemporary, backup);
                backupTemporary = null;
            }
            moveReplace(temporary, file);
        } catch (IOException exception) {
            throw new IOException("Unable to save profile state to " + file + "; current profile was not changed", exception);
        } finally {
            deleteQuietly(temporary);
            deleteQuietly(backupTemporary);
        }
    }

    private Path file(UUID owner) {
        return directory.resolve(owner.toString() + ".json");
    }

    private static void requireOwner(UUID owner) {
        if (owner == null) {
            throw new IllegalArgumentException("profile owner is required");
        }
    }

    private static void forceWrite(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void force(Path target) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic replacement is not supported for profile state: " + target, exception);
        }
    }

    private static void deleteQuietly(Path target) {
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // A leftover temporary file cannot invalidate a completed atomic replacement.
        }
    }

    private static String encode(CharacterProfile profile) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("owner", profile.owner().toString());
        root.addProperty("name", profile.name());
        root.addProperty("pronouns", profile.pronouns());
        root.addProperty("description", profile.description());
        root.addProperty("revision", profile.revision());
        return root.toString();
    }

    private static CharacterProfile decode(String source) {
        JsonElement parsed;
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            parsed = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("unexpected trailing JSON content");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("unable to read profile JSON", exception);
        }
        JsonObject root = object(parsed, "root");
        if (!root.keySet().equals(FIELDS)) {
            throw new IllegalArgumentException("profile contains unsupported or missing fields");
        }
        int schemaVersion = integer(required(root, "schemaVersion"), "schemaVersion");
        if (schemaVersion > SCHEMA_VERSION) {
            throw new IllegalArgumentException("profile schema " + schemaVersion + " is newer than supported version " + SCHEMA_VERSION);
        }
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported profile schema version: " + schemaVersion);
        }
        try {
            return new CharacterProfile(UUID.fromString(string(required(root, "owner"), "owner")),
                    string(required(root, "name"), "name"), string(required(root, "pronouns"), "pronouns"),
                    string(required(root, "description"), "description"), longNumber(required(root, "revision"), "revision"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid profile fields", exception);
        }
    }

    private static JsonElement required(JsonObject object, String field) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return object.get(field);
    }

    private static JsonObject object(JsonElement value, String field) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static String string(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static int integer(JsonElement value, String field) {
        long result = longNumber(value, field);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " is outside integer range");
        }
        return (int) result;
    }

    private static long longNumber(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        String source = value.getAsString();
        if (!source.matches("-?(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            return Long.parseLong(source);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " is outside long range", exception);
        }
    }
}
