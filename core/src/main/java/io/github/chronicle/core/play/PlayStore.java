package io.github.chronicle.core.play;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict, bounded, atomic storage for the independent play and unified Event runtime. */
public final class PlayStore implements PlayRepository {
    public static final int SCHEMA_VERSION = 3;
    public static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
    private static final Set<String> ROOT_FIELDS_V1 = Set.of("schemaVersion", "revision", "elapsedTicks", "participants", "bindings", "deliveries");
    private static final Set<String> ROOT_FIELDS_V2 = Set.of("schemaVersion", "revision", "elapsedTicks", "participants", "bindings", "deliveries", "events", "executions");
    private static final Set<String> BINDING_FIELDS = Set.of("id", "revision", "definitionFingerprint", "mode", "regionId", "regionRevision", "armedAtTicks", "delayTicks", "requiredFlag");
    private static final Set<String> DELIVERY_FIELDS = Set.of("id", "status", "source", "actor", "session", "elapsedTicks", "recipients");

    private final Path file;
    private Boolean observedPresent;
    private byte[] observedFingerprint;

    public PlayStore(Path file) {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException("play runtime file is required");
        this.file = file.toAbsolutePath().normalize();
    }

    @Override
    public synchronized PlayState load() throws IOException {
        if (!Files.exists(file)) {
            observedPresent = false;
            observedFingerprint = null;
            return PlayState.empty();
        }
        byte[] bytes = current();
        try {
            PlayState state = decode(new String(bytes, StandardCharsets.UTF_8));
            observedPresent = true;
            observedFingerprint = fingerprint(bytes);
            return state;
        } catch (RuntimeException exception) {
            throw invalid(exception);
        }
    }

    @Override
    public synchronized void save(PlayState state) throws IOException {
        if (state == null) throw new IllegalArgumentException("play state is required");
        Files.createDirectories(file.getParent());
        boolean present = Files.exists(file);
        if (observedPresent != null && observedPresent != present) {
            throw new IOException("play runtime file presence changed outside this store; refusing to overwrite " + file);
        }
        byte[] old = null;
        if (present) {
            old = current();
            if (observedFingerprint != null && !MessageDigest.isEqual(observedFingerprint, fingerprint(old))) {
                throw new IOException("play runtime file changed outside this store; refusing to overwrite " + file);
            }
            try { decode(new String(old, StandardCharsets.UTF_8)); }
            catch (RuntimeException exception) { throw invalid(exception); }
        }
        byte[] next = encode(state).getBytes(StandardCharsets.UTF_8);
        if (next.length > MAX_FILE_BYTES) throw new IOException("play runtime serialization exceeds 16 MiB limit: " + file);
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        Path backupTemporary = null;
        try {
            forceWrite(temporary, next);
            if (old != null) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                backupTemporary = Files.createTempFile(file.getParent(), backup.getFileName().toString(), ".tmp");
                forceWrite(backupTemporary, old);
                moveReplace(backupTemporary, backup);
                backupTemporary = null;
            }
            moveReplace(temporary, file);
            observedPresent = true;
            observedFingerprint = fingerprint(next);
        } finally {
            delete(temporary);
            delete(backupTemporary);
        }
    }

    @Override
    public synchronized void archiveEvent(EventExecution execution) throws IOException {
        if (execution == null) throw new IllegalArgumentException("event execution is required");
        byte[] bytes = EventJson.encodeExecution(execution).toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > EventJson.MAX_ENCODED_BYTES) throw new IOException("event execution archive exceeds 64 KiB");
        Path history = file.resolveSibling("event-history");
        Files.createDirectories(history);
        Path target = history.resolve(execution.operationId() + ".json");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            verifyArchive(target, execution, bytes);
            return;
        }
        Path temporary = Files.createTempFile(history, ".event-", ".tmp");
        try {
            forceWrite(temporary, bytes);
            try { Files.createLink(target, temporary); }
            catch (FileAlreadyExistsException race) { verifyArchive(target, execution, bytes); }
            verifyArchive(target, execution, bytes);
        } finally {
            delete(temporary);
        }
    }

    private byte[] current() throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("play runtime path is not a regular file: " + file);
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("play runtime file exceeds 16 MiB limit: " + file);
        return Files.readAllBytes(file);
    }

    private IOException invalid(RuntimeException exception) {
        return new IOException("play runtime data is invalid and was not replaced: " + file + " (" + exception.getMessage() + ")", exception);
    }

    private static PlayState decode(String json) {
        JsonObject root = PlayJson.object(json, "play runtime JSON");
        int schema = PlayJson.integer(PlayJson.required(root, "schemaVersion"), "schemaVersion");
        if (schema == 1) PlayJson.fields(root, ROOT_FIELDS_V1, "play runtime root");
        else if (schema == 2 || schema == SCHEMA_VERSION) PlayJson.fields(root, ROOT_FIELDS_V2, "play runtime root");
        else throw new IllegalArgumentException("unsupported play runtime schema version");
        long revision = PlayJson.number(PlayJson.required(root, "revision"), "play revision");
        long elapsedTicks = PlayJson.number(PlayJson.required(root, "elapsedTicks"), "elapsed play ticks");

        JsonArray participantValues = PlayJson.array(PlayJson.required(root, "participants"), "participants");
        if (participantValues.size() > PlayState.MAX_PARTICIPANTS) throw new IllegalArgumentException("too many participants");
        Set<UUID> participants = new LinkedHashSet<>();
        for (JsonElement value : participantValues) {
            UUID player = PlayJson.uuid(value, "participant");
            if (!participants.add(player)) throw new IllegalArgumentException("duplicate participant: " + player);
        }

        JsonArray bindingValues = PlayJson.array(PlayJson.required(root, "bindings"), "bindings");
        if (bindingValues.size() > PlayState.MAX_BINDINGS) throw new IllegalArgumentException("too many observation bindings");
        Map<String, PlayState.Binding> bindings = new LinkedHashMap<>();
        for (JsonElement value : bindingValues) {
            JsonObject object = PlayJson.object(value, "observation binding");
            PlayJson.fields(object, BINDING_FIELDS, "observation binding");
            PlayState.Binding binding = new PlayState.Binding(
                    PlayJson.string(PlayJson.required(object, "id"), "observation binding ID"),
                    PlayJson.number(PlayJson.required(object, "revision"), "observation binding revision"),
                    PlayJson.string(PlayJson.required(object, "definitionFingerprint"), "observation definition fingerprint"),
                    PlayJson.enumeration(PlayJson.required(object, "mode"), PlayState.Mode.class, "observation mode"),
                    PlayJson.string(PlayJson.required(object, "regionId"), "observation region ID"),
                    PlayJson.number(PlayJson.required(object, "regionRevision"), "observation region revision"),
                    PlayJson.number(PlayJson.required(object, "armedAtTicks"), "observation armed time"),
                    PlayJson.number(PlayJson.required(object, "delayTicks"), "observation delay"),
                    PlayJson.string(PlayJson.required(object, "requiredFlag"), "required flag"));
            if (bindings.putIfAbsent(binding.id(), binding) != null) throw new IllegalArgumentException("duplicate observation binding: " + binding.id());
        }

        JsonArray deliveryValues = PlayJson.array(PlayJson.required(root, "deliveries"), "deliveries");
        if (deliveryValues.size() > PlayState.MAX_DELIVERIES) throw new IllegalArgumentException("too many observation deliveries");
        Map<String, PlayState.Delivery> deliveries = new LinkedHashMap<>();
        for (JsonElement value : deliveryValues) {
            JsonObject object = PlayJson.object(value, "observation delivery");
            PlayJson.fields(object, DELIVERY_FIELDS, "observation delivery");
            JsonElement actorValue = object.get("actor");
            UUID actor = actorValue == null || actorValue.isJsonNull() ? null : PlayJson.uuid(actorValue, "observation actor");
            JsonArray recipientValues = PlayJson.array(PlayJson.required(object, "recipients"), "observation recipients");
            if (recipientValues.size() > PlayState.MAX_PARTICIPANTS) throw new IllegalArgumentException("too many observation recipients");
            List<UUID> recipients = new ArrayList<>();
            for (JsonElement recipient : recipientValues) recipients.add(PlayJson.uuid(recipient, "observation recipient"));
            PlayState.Delivery delivery = new PlayState.Delivery(
                    PlayJson.string(PlayJson.required(object, "id"), "observation delivery ID"),
                    PlayJson.enumeration(PlayJson.required(object, "status"), PlayState.Status.class, "observation status"),
                    PlayJson.enumeration(PlayJson.required(object, "source"), PlayState.DeliverySource.class, "observation source"),
                    actor,
                    PlayJson.integer(PlayJson.required(object, "session"), "observation session"),
                    PlayJson.number(PlayJson.required(object, "elapsedTicks"), "observation delivery time"),
                    recipients);
            if (deliveries.putIfAbsent(delivery.id(), delivery) != null) throw new IllegalArgumentException("duplicate observation delivery: " + delivery.id());
        }
        Map<String, EventConfiguration> events = new LinkedHashMap<>();
        Map<String, EventExecution> executions = new LinkedHashMap<>();
        if (schema >= 2) {
            JsonArray eventValues = PlayJson.array(PlayJson.required(root, "events"), "events");
            if (eventValues.size() > PlayState.MAX_EVENTS) throw new IllegalArgumentException("too many events");
            for (JsonElement value : eventValues) {
                EventConfiguration configuration = EventJson.decodeConfiguration(PlayJson.object(value, "event configuration"));
                String id = configuration.definition().id();
                if (events.putIfAbsent(id, configuration) != null) throw new IllegalArgumentException("duplicate event: " + id);
            }

            JsonArray executionValues = PlayJson.array(PlayJson.required(root, "executions"), "event executions");
            if (executionValues.size() > PlayState.MAX_EXECUTIONS) throw new IllegalArgumentException("too many event executions");
            for (JsonElement value : executionValues) {
                EventExecution execution = EventJson.decodeExecution(PlayJson.object(value, "event execution"));
                if (executions.putIfAbsent(execution.eventId(), execution) != null) {
                    throw new IllegalArgumentException("duplicate event execution: " + execution.eventId());
                }
            }
        }
        return new PlayState(revision, elapsedTicks, participants, bindings, deliveries, events, executions);
    }

    private static String encode(PlayState state) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("revision", state.revision());
        root.addProperty("elapsedTicks", state.elapsedTicks());
        JsonArray participants = new JsonArray();
        state.participants().forEach(player -> participants.add(player.toString()));
        root.add("participants", participants);

        JsonArray bindings = new JsonArray();
        state.bindings().values().forEach(binding -> {
            JsonObject object = new JsonObject();
            object.addProperty("id", binding.id());
            object.addProperty("revision", binding.revision());
            object.addProperty("definitionFingerprint", binding.definitionFingerprint());
            object.addProperty("mode", binding.mode().name());
            object.addProperty("regionId", binding.regionId());
            object.addProperty("regionRevision", binding.regionRevision());
            object.addProperty("armedAtTicks", binding.armedAtTicks());
            object.addProperty("delayTicks", binding.delayTicks());
            object.addProperty("requiredFlag", binding.requiredFlag());
            bindings.add(object);
        });
        root.add("bindings", bindings);

        JsonArray deliveries = new JsonArray();
        state.deliveries().values().forEach(delivery -> {
            JsonObject object = new JsonObject();
            object.addProperty("id", delivery.id());
            object.addProperty("status", delivery.status().name());
            object.addProperty("source", delivery.source().name());
            if (delivery.actor() == null) object.add("actor", JsonNull.INSTANCE);
            else object.addProperty("actor", delivery.actor().toString());
            object.addProperty("session", delivery.session());
            object.addProperty("elapsedTicks", delivery.elapsedTicks());
            JsonArray recipients = new JsonArray();
            delivery.recipients().forEach(player -> recipients.add(player.toString()));
            object.add("recipients", recipients);
            deliveries.add(object);
        });
        root.add("deliveries", deliveries);

        JsonArray events = new JsonArray();
        state.events().values().forEach(configuration -> events.add(EventJson.encodeConfiguration(configuration)));
        root.add("events", events);

        JsonArray executions = new JsonArray();
        state.executions().values().forEach(execution -> executions.add(EventJson.encodeExecution(execution)));
        root.add("executions", executions);
        return root.toString();
    }

    private static void verifyArchive(Path target, EventExecution expected, byte[] expectedBytes) throws IOException {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("event execution archive is not a regular file: " + target);
        }
        long size = Files.size(target);
        if (size < 1 || size > EventJson.MAX_ENCODED_BYTES) {
            throw new IOException("event execution archive size is outside limits: " + target);
        }
        byte[] actual = Files.readAllBytes(target);
        EventExecution decoded;
        try { decoded = EventJson.decodeExecution(PlayJson.object(new String(actual, StandardCharsets.UTF_8), "event execution archive")); }
        catch (RuntimeException exception) { throw new IOException("event execution archive is invalid: " + target, exception); }
        if (!decoded.operationId().equals(expected.operationId())) {
            throw new IOException("event execution archive operation ID does not match its file: " + target);
        }
        if (!Arrays.equals(actual, expectedBytes)) {
            throw new IOException("event execution archive already exists with different contents: " + target);
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
        catch (AtomicMoveNotSupportedException exception) { throw new IOException("atomic replacement is not supported for play runtime data: " + target, exception); }
    }

    private static void delete(Path target) { if (target != null) try { Files.deleteIfExists(target); } catch (IOException ignored) { } }

    private static byte[] fingerprint(byte[] bytes) throws IOException {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException exception) { throw new IOException("SHA-256 unavailable", exception); }
    }
}
