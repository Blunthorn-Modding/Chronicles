package io.github.chronicle.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonNull;
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
import java.util.Map;
import java.util.Set;

/** Strict, atomic schema-2 persistence with read-only schema-1 migration support. */
public final class ChestContentStore implements ChestContentRepository {
    public static final int SCHEMA_VERSION = 2;
    public static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    private static final int MAX_JSON_DEPTH = 32;
    private final Path file;
    private Boolean observedPresent;
    private byte[] observedFingerprint;

    public ChestContentStore(Path file) {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException("chest content file is required");
        this.file = file.toAbsolutePath().normalize();
    }

    @Override
    public synchronized ChestContentState load() throws IOException {
        if (!Files.exists(file)) {
            observedPresent = false;
            observedFingerprint = null;
            return ChestContentState.empty();
        }
        byte[] bytes = readCurrent();
        try {
            ChestContentState state = decode(new String(bytes, StandardCharsets.UTF_8));
            observedPresent = true;
            observedFingerprint = fingerprint(bytes);
            return state;
        } catch (RuntimeException exception) {
            throw new IOException("chest content is invalid and was not replaced: " + file + " (" + exception.getMessage() + ")", exception);
        }
    }

    @Override
    public synchronized void save(ChestContentState state) throws IOException {
        if (state == null) throw new IllegalArgumentException("chest content state is required");
        Path parent = file.getParent();
        Files.createDirectories(parent);
        boolean present = Files.exists(file);
        if (observedPresent != null && observedPresent != present) throw new IOException("chest content file presence changed outside this store; refusing to overwrite " + file);
        byte[] current = null;
        if (present) {
            current = readCurrent();
            if (observedFingerprint != null && !MessageDigest.isEqual(observedFingerprint, fingerprint(current))) {
                throw new IOException("chest content file changed outside this store; refusing to overwrite " + file);
            }
            try { decode(new String(current, StandardCharsets.UTF_8)); }
            catch (RuntimeException exception) { throw new IOException("chest content is invalid and was not replaced: " + file, exception); }
        }
        byte[] encoded = encode(state).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_FILE_BYTES) throw new IOException("chest content serialization exceeds 8 MiB limit: " + file);
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
            throw new IOException("unable to save chest content; in-memory state was not changed", exception);
        } finally {
            deleteQuietly(temporary);
            deleteQuietly(backupTemporary);
        }
    }

    private byte[] readCurrent() throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("chest content path is not a regular file: " + file);
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("chest content file exceeds 8 MiB limit: " + file);
        return Files.readAllBytes(file);
    }

    private static String encode(ChestContentState state) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("revision", state.revision());
        JsonArray templates = new JsonArray();
        state.templates().values().forEach(template -> {
            JsonObject object = new JsonObject();
            object.addProperty("id", template.id()); object.addProperty("revision", template.revision());
            object.add("contents", snapshot(template.contents())); templates.add(object);
        });
        root.add("templates", templates);
        JsonArray bindings = new JsonArray();
        state.bindings().values().forEach(binding -> {
            JsonObject object = new JsonObject();
            object.addProperty("id", binding.id()); object.addProperty("revision", binding.revision());
            object.addProperty("templateId", binding.templateId()); object.addProperty("templateRevision", binding.templateRevision());
            object.addProperty("anchorId", binding.anchorId()); object.addProperty("anchorRevision", binding.anchorRevision());
            object.addProperty("policy", binding.policy().name()); object.addProperty("lootType", binding.lootType());
            object.add("directTarget", binding.directTarget() == null ? JsonNull.INSTANCE : target(binding.directTarget()));
            bindings.add(object);
        });
        root.add("bindings", bindings);
        JsonArray receipts = new JsonArray();
        state.receipts().forEach(receipt -> {
            JsonObject object = new JsonObject();
            object.addProperty("operationId", receipt.operationId()); object.addProperty("bindingId", receipt.bindingId());
            object.addProperty("sessionNumber", receipt.sessionNumber()); object.addProperty("cycle", receipt.cycle());
            object.addProperty("completedAt", receipt.completedAt()); receipts.add(object);
        });
        root.add("receipts", receipts);
        JsonArray pending = new JsonArray();
        state.pending().values().forEach(operation -> pending.add(operation(operation)));
        root.add("pending", pending);
        root.add("schedule", schedule(state.schedule()));
        return root.toString();
    }

    private static JsonObject snapshot(InventorySnapshot snapshot) {
        JsonObject object = new JsonObject(); object.addProperty("size", snapshot.size());
        JsonArray stacks = new JsonArray(); snapshot.stacks().forEach(stacks::add); object.add("stacks", stacks);
        return object;
    }

    private static JsonObject target(ChestTarget target) {
        JsonObject object = new JsonObject();
        object.addProperty("anchorId", target.anchorId()); object.addProperty("anchorRevision", target.anchorRevision());
        object.addProperty("dimension", target.dimension()); object.addProperty("x", target.x()); object.addProperty("y", target.y()); object.addProperty("z", target.z());
        object.addProperty("expectedBlock", target.expectedBlock()); return object;
    }

    private static JsonObject operation(PendingChestOperation operation) {
        JsonObject object = new JsonObject();
        object.addProperty("operationId", operation.operationId()); object.addProperty("actorUuid", operation.actorUuid()); object.addProperty("actorName", operation.actorName());
        object.addProperty("bindingId", operation.bindingId()); object.addProperty("bindingRevision", operation.bindingRevision());
        object.addProperty("sessionNumber", operation.sessionNumber()); object.addProperty("cycle", operation.cycle()); object.add("target", target(operation.target()));
        object.add("before", snapshot(operation.before())); object.add("after", snapshot(operation.after())); object.addProperty("createdAt", operation.createdAt());
        return object;
    }

    private static JsonObject schedule(LootSchedule schedule) {
        JsonObject object = new JsonObject();
        object.addProperty("sessionNumber", schedule.sessionNumber());
        object.addProperty("activeTicks", schedule.activeTicks());
        JsonObject types = new JsonObject();
        schedule.types().forEach((id, cycle) -> {
            JsonObject value = new JsonObject();
            value.addProperty("generation", cycle.generation());
            value.addProperty("nextRefillTick", cycle.nextRefillTick());
            value.addProperty("intervalTicks", cycle.intervalTicks());
            types.add(id, value);
        });
        object.add("types", types);
        return object;
    }

    private static ChestContentState decode(String source) {
        validateStrictJson(source);
        JsonElement parsed;
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT); parsed = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("unexpected trailing JSON content");
        } catch (IOException exception) { throw new IllegalArgumentException("unable to read chest content JSON", exception); }
        JsonObject root = object(parsed, "root");
        int schema = integer(required(root, "schemaVersion"), "schemaVersion");
        if (schema == 1) exact(root, Set.of("schemaVersion", "revision", "templates", "bindings", "receipts", "pending"), "root");
        else if (schema == SCHEMA_VERSION) exact(root, Set.of("schemaVersion", "revision", "templates", "bindings", "receipts", "pending", "schedule"), "root");
        else throw new IllegalArgumentException("unsupported chest content schema");
        long revision = number(required(root, "revision"), "revision");
        Map<String, ChestTemplate> templates = new LinkedHashMap<>();
        for (JsonElement value : array(required(root, "templates"), "templates")) {
            JsonObject item = object(value, "template"); exact(item, Set.of("id", "revision", "contents"), "template");
            ChestTemplate template = new ChestTemplate(string(required(item, "id"), "template id"), number(required(item, "revision"), "template revision"), snapshot(required(item, "contents")));
            unique(templates, template.id(), template, "template");
        }
        Map<String, ChestBinding> bindings = new LinkedHashMap<>();
        for (JsonElement value : array(required(root, "bindings"), "bindings")) {
            JsonObject item = object(value, "binding");
            if (schema == 1) exact(item, Set.of("id", "revision", "templateId", "templateRevision", "anchorId", "anchorRevision", "policy"), "binding");
            else exact(item, Set.of("id", "revision", "templateId", "templateRevision", "anchorId", "anchorRevision", "policy", "lootType", "directTarget"), "binding");
            ChestPolicy policy;
            try { policy = ChestPolicy.valueOf(string(required(item, "policy"), "binding policy")); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("unknown chest policy", exception); }
            ChestBinding binding;
            if (schema == 1) {
                binding = new ChestBinding(string(required(item, "id"), "binding id"), number(required(item, "revision"), "binding revision"),
                        string(required(item, "templateId"), "template ID"), number(required(item, "templateRevision"), "template revision"),
                        string(required(item, "anchorId"), "anchor ID"), number(required(item, "anchorRevision"), "anchor revision"), policy);
            } else {
                String lootType = nullableString(item, "lootType", "loot type");
                ChestTarget directTarget = nullableTarget(item, "directTarget");
                String templateId = nullableString(item, "templateId", "template ID");
                binding = new ChestBinding(string(required(item, "id"), "binding id"), number(required(item, "revision"), "binding revision"),
                        templateId, number(required(item, "templateRevision"), "template revision"),
                        string(required(item, "anchorId"), "anchor ID"), number(required(item, "anchorRevision"), "anchor revision"), policy,
                        lootType, directTarget);
            }
            unique(bindings, binding.id(), binding, "binding");
        }
        var receipts = new ArrayList<ChestReceipt>();
        for (JsonElement value : array(required(root, "receipts"), "receipts")) {
            JsonObject item = object(value, "receipt");
            if (schema == 1) exact(item, Set.of("operationId", "bindingId", "sessionNumber", "completedAt"), "receipt");
            else exact(item, Set.of("operationId", "bindingId", "sessionNumber", "cycle", "completedAt"), "receipt");
            receipts.add(new ChestReceipt(string(required(item, "operationId"), "operation ID"), string(required(item, "bindingId"), "binding ID"),
                    integer(required(item, "sessionNumber"), "session number"), schema == 1 ? -1 : number(required(item, "cycle"), "receipt cycle"),
                    string(required(item, "completedAt"), "completion time")));
        }
        Map<String, PendingChestOperation> pending = new LinkedHashMap<>();
        for (JsonElement value : array(required(root, "pending"), "pending")) {
            PendingChestOperation operation = operation(value, schema); unique(pending, operation.operationId(), operation, "pending operation");
        }
        LootSchedule schedule = schema == 1 ? LootSchedule.empty() : schedule(required(root, "schedule"));
        return new ChestContentState(revision, templates, bindings, receipts, pending, schedule);
    }

    private static PendingChestOperation operation(JsonElement value, int schema) {
        JsonObject item = object(value, "pending operation");
        if (schema == 1) exact(item, Set.of("operationId", "actorUuid", "actorName", "bindingId", "bindingRevision", "sessionNumber", "target", "before", "after", "createdAt"), "pending operation");
        else exact(item, Set.of("operationId", "actorUuid", "actorName", "bindingId", "bindingRevision", "sessionNumber", "cycle", "target", "before", "after", "createdAt"), "pending operation");
        return new PendingChestOperation(string(required(item, "operationId"), "operation ID"), string(required(item, "actorUuid"), "actor UUID"),
                string(required(item, "actorName"), "actor name"), string(required(item, "bindingId"), "binding ID"),
                number(required(item, "bindingRevision"), "binding revision"), integer(required(item, "sessionNumber"), "session number"),
                schema == 1 ? -1 : number(required(item, "cycle"), "operation cycle"),
                target(required(item, "target")), snapshot(required(item, "before")), snapshot(required(item, "after")), string(required(item, "createdAt"), "creation time"));
    }

    private static LootSchedule schedule(JsonElement value) {
        JsonObject item = object(value, "schedule");
        exact(item, Set.of("sessionNumber", "activeTicks", "types"), "schedule");
        JsonObject values = object(required(item, "types"), "schedule types");
        if (values.size() > LootSchedule.MAX_TYPES) throw new IllegalArgumentException("schedule types exceed " + LootSchedule.MAX_TYPES);
        LinkedHashMap<String, LootCycle> cycles = new LinkedHashMap<>();
        values.entrySet().forEach(entry -> {
            ContentIds.requireNamespacedId(entry.getKey(), "loot type");
            JsonObject cycle = object(entry.getValue(), "loot cycle");
            exact(cycle, Set.of("generation", "nextRefillTick", "intervalTicks"), "loot cycle");
            cycles.put(entry.getKey(), new LootCycle(number(required(cycle, "generation"), "loot generation"),
                    number(required(cycle, "nextRefillTick"), "next refill tick"),
                    number(required(cycle, "intervalTicks"), "refill interval")));
        });
        return new LootSchedule(integer(required(item, "sessionNumber"), "schedule session"),
                number(required(item, "activeTicks"), "schedule active ticks"), cycles);
    }

    private static ChestTarget target(JsonElement value) {
        JsonObject item = object(value, "target"); exact(item, Set.of("anchorId", "anchorRevision", "dimension", "x", "y", "z", "expectedBlock"), "target");
        return new ChestTarget(string(required(item, "anchorId"), "anchor ID"), number(required(item, "anchorRevision"), "anchor revision"),
                string(required(item, "dimension"), "dimension"), integer(required(item, "x"), "x"), integer(required(item, "y"), "y"), integer(required(item, "z"), "z"),
                string(required(item, "expectedBlock"), "expected block"));
    }

    private static ChestTarget nullableTarget(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null) throw new IllegalArgumentException("missing " + field);
        return value.isJsonNull() ? null : target(value);
    }

    private static InventorySnapshot snapshot(JsonElement value) {
        JsonObject item = object(value, "snapshot"); exact(item, Set.of("size", "stacks"), "snapshot");
        JsonArray values = array(required(item, "stacks"), "stacks"); var stacks = new ArrayList<String>(values.size());
        values.forEach(stack -> stacks.add(string(stack, "stack payload")));
        return new InventorySnapshot(integer(required(item, "size"), "snapshot size"), stacks);
    }

    private static void validateStrictJson(String source) {
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT); validateValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("unexpected trailing JSON content");
        } catch (IOException exception) { throw new IllegalArgumentException("unable to read chest content JSON", exception); }
    }

    private static void validateValue(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_JSON_DEPTH) throw new IllegalArgumentException("chest content JSON nesting exceeds " + MAX_JSON_DEPTH);
        switch (reader.peek()) {
            case BEGIN_OBJECT -> { reader.beginObject(); Set<String> names = new HashSet<>(); while (reader.hasNext()) { String name = reader.nextName(); if (!names.add(name)) throw new IllegalArgumentException("duplicate JSON member: " + name); validateValue(reader, depth + 1); } reader.endObject(); }
            case BEGIN_ARRAY -> { reader.beginArray(); while (reader.hasNext()) validateValue(reader, depth + 1); reader.endArray(); }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException("unexpected JSON token: " + reader.peek());
        }
    }

    private static JsonElement required(JsonObject object, String field) { if (!object.has(field) || object.get(field).isJsonNull()) throw new IllegalArgumentException("missing " + field); return object.get(field); }
    private static JsonObject object(JsonElement value, String field) { if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(field + " must be an object"); return value.getAsJsonObject(); }
    private static JsonArray array(JsonElement value, String field) { if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(field + " must be an array"); return value.getAsJsonArray(); }
    private static String string(JsonElement value, String field) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(field + " must be a string"); return value.getAsString(); }
    private static String nullableString(JsonObject object, String name, String field) { JsonElement value = object.get(name); if (value == null) throw new IllegalArgumentException("missing " + name); return value.isJsonNull() ? null : string(value, field); }
    private static int integer(JsonElement value, String field) { long result = number(value, field); if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw new IllegalArgumentException(field + " is outside integer range"); return (int) result; }
    private static long number(JsonElement value, String field) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(field + " must be an integer"); String source = value.getAsString(); if (!source.matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException(field + " must be an integer"); try { return Long.parseLong(source); } catch (NumberFormatException exception) { throw new IllegalArgumentException(field + " is outside long range", exception); } }
    private static void exact(JsonObject object, Set<String> fields, String label) { if (!object.keySet().equals(fields)) throw new IllegalArgumentException(label + " fields must be exactly " + fields); }
    private static <T> void unique(Map<String, T> target, String id, T value, String label) { if (target.putIfAbsent(id, value) != null) throw new IllegalArgumentException("duplicate " + label + " ID: " + id); }

    private static void forceWrite(Path target, byte[] bytes) throws IOException { try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) { ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true); } }
    private static void moveReplace(Path source, Path target) throws IOException { try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException exception) { throw new IOException("atomic replacement is not supported for chest content: " + target, exception); } }
    private static void deleteQuietly(Path target) { if (target == null) return; try { Files.deleteIfExists(target); } catch (IOException ignored) { } }
    private static byte[] fingerprint(byte[] bytes) throws IOException { try { return MessageDigest.getInstance("SHA-256").digest(bytes); } catch (NoSuchAlgorithmException exception) { throw new IOException("SHA-256 is unavailable", exception); } }
}
