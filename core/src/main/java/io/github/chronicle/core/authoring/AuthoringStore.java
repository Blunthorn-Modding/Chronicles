package io.github.chronicle.core.authoring;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict schema-1 JSON persistence for {@link AuthoringRegistry}.
 *
 * <p>The root has {@code schemaVersion}, {@code revision}, and {@code sites}; each site is a
 * discriminated {@code region}, {@code location}, or {@code anchor} object. Unknown and missing
 * fields are rejected so a typo cannot silently move a map binding.</p>
 */
public final class AuthoringStore implements AuthoringRepository {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_FILE_BYTES = 1024L * 1024;
    private static final int MAX_JSON_DEPTH = 32;
    private final Path file;
    private Boolean observedPresent;
    private byte[] observedFingerprint;

    public AuthoringStore(Path file) {
        if (file == null || file.getFileName() == null) {
            throw new IllegalArgumentException("authoring file is required");
        }
        this.file = file.toAbsolutePath().normalize();
    }

    @Override
    public synchronized AuthoringRegistry load() throws IOException {
        if (!Files.exists(file)) {
            observedPresent = false;
            observedFingerprint = null;
            return AuthoringRegistry.empty();
        }
        byte[] bytes = readCurrent();
        AuthoringRegistry decoded;
        try {
            decoded = decode(new String(bytes, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new IOException("authoring data is invalid and was not replaced: " + file + " (" + exception.getMessage() + ")", exception);
        }
        observedPresent = true;
        observedFingerprint = fingerprint(bytes);
        return decoded;
    }

    @Override
    public synchronized void save(AuthoringRegistry registry) throws IOException {
        if (registry == null) {
            throw new IllegalArgumentException("authoring registry is required");
        }
        Path parent = file.getParent();
        Files.createDirectories(parent);
        byte[] current = null;
        boolean present = Files.exists(file);
        if (observedPresent != null && observedPresent != present) {
            throw new IOException("authoring file presence changed outside this store; refusing to overwrite " + file);
        }
        if (present) {
            current = readCurrent();
            byte[] actualFingerprint = fingerprint(current);
            if (observedFingerprint != null && !MessageDigest.isEqual(observedFingerprint, actualFingerprint)) {
                throw new IOException("authoring file changed outside this store; refusing to overwrite " + file);
            }
            // Validate the exact file that is about to become the backup before touching it.
            try {
                decode(new String(current, StandardCharsets.UTF_8));
            } catch (RuntimeException exception) {
                throw new IOException("authoring data is invalid and was not replaced: " + file + " (" + exception.getMessage() + ")", exception);
            }
        }
        byte[] encoded = encode(registry).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_FILE_BYTES) {
            throw new IOException("authoring serialization exceeds 1 MiB limit: " + file);
        }
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        Path backupTemporary = null;
        try {
            forceWrite(temporary, encoded);
            if (current != null) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                backupTemporary = Files.createTempFile(parent, backup.getFileName().toString(), ".tmp");
                forceWrite(backupTemporary, current);
                moveReplace(backupTemporary, backup);
                backupTemporary = null;
            }
            moveReplace(temporary, file);
            observedPresent = true;
            observedFingerprint = fingerprint(encoded);
        } catch (IOException exception) {
            throw new IOException("unable to save authoring registry to " + file + "; in-memory state was not changed", exception);
        } finally {
            deleteQuietly(temporary);
            deleteQuietly(backupTemporary);
        }
    }

    private byte[] readCurrent() throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("authoring path is not a regular file: " + file);
        }
        long size = Files.size(file);
        if (size > MAX_FILE_BYTES) {
            throw new IOException("authoring file exceeds 1 MiB limit: " + file);
        }
        return Files.readAllBytes(file);
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

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("atomic replacement is not supported for authoring data: " + target, exception);
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

    private static byte[] fingerprint(byte[] bytes) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }

    private static AuthoringRegistry decode(String source) {
        validateStrictJson(source);
        JsonElement parsed;
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            parsed = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("unexpected trailing JSON content");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("unable to read authoring JSON", exception);
        }
        JsonObject root = object(parsed, "root");
        exactFields(root, Set.of("schemaVersion", "revision", "sites"), "root");
        int schema = integer(required(root, "schemaVersion"), "schemaVersion");
        if (schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported authoring schema version: " + schema);
        }
        long revision = longNumber(required(root, "revision"), "revision");
        JsonArray encodedSites = array(required(root, "sites"), "sites");
        if (encodedSites.size() > AuthoringRegistry.MAX_SITES) {
            throw new IllegalArgumentException("sites may contain at most " + AuthoringRegistry.MAX_SITES + " entries");
        }
        Map<String, AuthoringSite> sites = new LinkedHashMap<>();
        for (JsonElement element : encodedSites) {
            AuthoringSite site = decodeSite(object(element, "site"));
            if (sites.putIfAbsent(site.id(), site) != null) {
                throw new IllegalArgumentException("sites may not contain duplicate IDs");
            }
        }
        return new AuthoringRegistry(revision, sites);
    }

    /**
     * Gson's tree model retains only the last member for duplicate object names. Validate the raw
     * stream first so a duplicate cannot silently alter a persisted map binding.
     */
    private static void validateStrictJson(String source) {
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            validateValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("unexpected trailing JSON content");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("unable to read authoring JSON", exception);
        }
    }

    private static void validateValue(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_JSON_DEPTH) {
            throw new IllegalArgumentException("authoring JSON nesting exceeds " + MAX_JSON_DEPTH);
        }
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) {
                        throw new IllegalArgumentException("duplicate JSON member: " + name);
                    }
                    validateValue(reader, depth + 1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) {
                    validateValue(reader, depth + 1);
                }
                reader.endArray();
            }
            case STRING -> reader.nextString();
            case NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException("unexpected JSON token: " + reader.peek());
        }
    }

    private static AuthoringSite decodeSite(JsonObject object) {
        String type = string(required(object, "type"), "site type");
        return switch (type) {
            case "region" -> {
                exactFields(object, Set.of("type", "id", "dimension", "revision", "min", "max"), "region");
                yield new AuthoringSite.Region(string(required(object, "id"), "region id"), string(required(object, "dimension"), "region dimension"),
                        longNumber(required(object, "revision"), "region revision"), position(required(object, "min"), "region min"), position(required(object, "max"), "region max"));
            }
            case "location" -> {
                exactFields(object, Set.of("type", "id", "dimension", "revision", "x", "y", "z", "yaw", "pitch"), "location");
                yield new AuthoringSite.Location(string(required(object, "id"), "location id"), string(required(object, "dimension"), "location dimension"),
                        longNumber(required(object, "revision"), "location revision"), decimal(required(object, "x"), "location x"), decimal(required(object, "y"), "location y"),
                        decimal(required(object, "z"), "location z"), (float) decimal(required(object, "yaw"), "location yaw"), (float) decimal(required(object, "pitch"), "location pitch"));
            }
            case "anchor" -> {
                exactFields(object, Set.of("type", "id", "dimension", "revision", "position", "expectedBlock"), "anchor");
                yield new AuthoringSite.Anchor(string(required(object, "id"), "anchor id"), string(required(object, "dimension"), "anchor dimension"),
                        longNumber(required(object, "revision"), "anchor revision"), position(required(object, "position"), "anchor position"),
                        string(required(object, "expectedBlock"), "expected block"));
            }
            default -> throw new IllegalArgumentException("unknown authoring site type: " + type);
        };
    }

    private static String encode(AuthoringRegistry registry) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("revision", registry.revision());
        JsonArray sites = new JsonArray();
        for (AuthoringSite site : registry.sites().values()) {
            sites.add(encodeSite(site));
        }
        root.add("sites", sites);
        return root.toString();
    }

    private static JsonObject encodeSite(AuthoringSite site) {
        JsonObject result = new JsonObject();
        result.addProperty("id", site.id());
        result.addProperty("dimension", site.dimension());
        result.addProperty("revision", site.revision());
        if (site instanceof AuthoringSite.Region region) {
            result.addProperty("type", "region");
            result.add("min", encodePosition(region.min()));
            result.add("max", encodePosition(region.max()));
        } else if (site instanceof AuthoringSite.Location location) {
            result.addProperty("type", "location");
            result.addProperty("x", location.x());
            result.addProperty("y", location.y());
            result.addProperty("z", location.z());
            result.addProperty("yaw", location.yaw());
            result.addProperty("pitch", location.pitch());
        } else if (site instanceof AuthoringSite.Anchor anchor) {
            result.addProperty("type", "anchor");
            result.add("position", encodePosition(anchor.position()));
            result.addProperty("expectedBlock", anchor.expectedBlock());
        } else {
            throw new IllegalArgumentException("unsupported authoring site: " + site.getClass().getName());
        }
        return result;
    }

    private static JsonObject encodePosition(BlockPosition position) {
        JsonObject result = new JsonObject();
        result.addProperty("x", position.x());
        result.addProperty("y", position.y());
        result.addProperty("z", position.z());
        return result;
    }

    private static BlockPosition position(JsonElement value, String field) {
        JsonObject object = object(value, field);
        exactFields(object, Set.of("x", "y", "z"), field);
        return new BlockPosition(integer(required(object, "x"), field + " x"), integer(required(object, "y"), field + " y"), integer(required(object, "z"), field + " z"));
    }

    private static void exactFields(JsonObject object, Set<String> allowed, String field) {
        if (!object.keySet().equals(allowed)) {
            List<String> unexpected = new ArrayList<>(object.keySet());
            unexpected.removeAll(allowed);
            List<String> missing = new ArrayList<>(allowed);
            missing.removeAll(object.keySet());
            throw new IllegalArgumentException(field + " fields do not match schema (unknown=" + unexpected + ", missing=" + missing + ")");
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

    private static JsonArray array(JsonElement value, String field) {
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value.getAsJsonArray();
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
        String text = value.getAsString();
        if (!text.matches("-?(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " is outside long range", exception);
        }
    }

    private static double decimal(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        try {
            double result = Double.parseDouble(value.getAsString());
            if (!Double.isFinite(result)) {
                throw new IllegalArgumentException(field + " must be finite");
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a finite number", exception);
        }
    }
}
