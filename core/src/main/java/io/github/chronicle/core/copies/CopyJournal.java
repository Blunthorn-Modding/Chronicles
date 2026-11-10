package io.github.chronicle.core.copies;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.content.InventorySnapshot;
import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Durable nonce journal. A failed publication poisons this instance until an explicit reload. */
public final class CopyJournal {
    public static final int MAX_BYTES = 16 * 1024 * 1024, MAX_OPERATIONS = 4096, MAX_PENDING = 32;
    private static final Gson JSON = new Gson();
    public interface Storage { byte[] load() throws IOException; void save(byte[] bytes) throws IOException; }
    public static Storage file(Path path) {
        AtomicDataFile file = new AtomicDataFile(path, MAX_BYTES);
        return new Storage() { public byte[] load() throws IOException { return file.load(); } public void save(byte[] bytes) throws IOException { file.save(bytes); } };
    }
    public enum Stage { CLAIMED, COMPLETE, CANCELLED }
    public enum Evidence { BEFORE, AFTER, MIXED }
    public static Evidence inspect(Operation operation, InventorySnapshot live) {
        if (operation == null || operation.stage() != Stage.CLAIMED || live == null) throw new IllegalArgumentException("Pending operation and inventory required.");
        return live.equals(operation.before()) ? Evidence.BEFORE : live.equals(operation.after()) ? Evidence.AFTER : Evidence.MIXED;
    }
    public record Operation(UUID request, UUID actor, String provider, String source, String hash, int count,
                            Stage stage, InventorySnapshot before, InventorySnapshot after) {
        public Operation {
            Objects.requireNonNull(request); Objects.requireNonNull(actor); Objects.requireNonNull(stage);
            if (provider == null || !provider.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || provider.length() > 160 || source == null || source.isBlank() || source.length() > 256 || hash == null || !hash.matches("[0-9a-f]{64}") || count < 1 || count > 36) throw new IllegalArgumentException("Invalid copy operation metadata.");
            if (stage == Stage.CLAIMED) {
                if (before == null || after == null || before.size() != 36 || after.size() != 36 || before.equals(after)) throw new IllegalArgumentException("Invalid pending inventory evidence.");
            } else if (before != null || after != null) throw new IllegalArgumentException("Terminal copy receipts cannot retain inventory evidence.");
        }
        public Operation terminal(Stage next) { if (next == Stage.CLAIMED) throw new IllegalArgumentException(); return new Operation(request, actor, provider, source, hash, count, next, null, null); }
    }
    private final Storage storage;
    private Map<UUID, Operation> operations = Map.of();
    private List<Operation> pending = List.of();
    private boolean poisoned = true;
    public CopyJournal(Storage storage) throws IOException { this.storage = Objects.requireNonNull(storage); reload(); }
    public void reload() throws IOException {
        poisoned = true;
        byte[] bytes = storage.load();
        try { replace(bytes == null ? Map.of() : decode(bytes)); poisoned = false; }
        catch (RuntimeException failure) { throw new IOException("Invalid personal-copy journal; evidence preserved.", failure); }
    }
    public boolean poisoned() { return poisoned; }
    public Map<UUID, Operation> operations() { return operations; }
    public Operation get(UUID request) { return operations.get(request); }
    public List<Operation> pending() { return pending; }
    public void claim(Operation operation) throws IOException {
        ready(); if (operation.stage() != Stage.CLAIMED || operations.containsKey(operation.request())) throw new IllegalArgumentException("Request already recorded or invalid.");
        if (operations.size() >= MAX_OPERATIONS) throw new IllegalStateException("Personal-copy history is full; ask an operator to inspect it.");
        if (pending.size() >= MAX_PENDING || pending.stream().anyMatch(o -> o.actor().equals(operation.actor()))) throw new IllegalStateException("A personal-copy operation is already pending or the pending limit is full.");
        var next = new LinkedHashMap<>(operations); next.put(operation.request(), operation); publish(next);
    }
    public void finish(UUID request, Stage stage) throws IOException {
        ready(); Operation old = operations.get(request); if (old == null || old.stage() != Stage.CLAIMED || stage == Stage.CLAIMED) throw new IllegalStateException("No matching pending copy operation.");
        var next = new LinkedHashMap<>(operations); next.put(request, old.terminal(stage)); publish(next);
    }
    private void ready() throws IOException { if (poisoned) throw new IOException("Personal-copy storage outcome is uncertain; reload evidence before continuing."); }
    private void publish(Map<UUID, Operation> next) throws IOException {
        byte[] bytes = encode(next); // Capacity refusal happens before storage mutation and does not poison.
        if (bytes.length > MAX_BYTES) throw new IllegalStateException("Personal-copy evidence exceeds the storage limit.");
        try { storage.save(bytes); } catch (IOException | RuntimeException failure) { poisoned = true; throw failure; }
        replace(Collections.unmodifiableMap(new LinkedHashMap<>(next)));
    }
    private void replace(Map<UUID, Operation> next) {
        List<Operation> nextPending = next.values().stream().filter(o -> o.stage() == Stage.CLAIMED).toList();
        operations = next; pending = nextPending;
    }
    private static byte[] encode(Map<UUID, Operation> operations) {
        JsonObject root = new JsonObject(); root.addProperty("schemaVersion", 1); JsonArray list = new JsonArray();
        operations.values().forEach(o -> list.add(JSON.toJsonTree(o))); root.add("operations", list);
        try {
            java.nio.ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(root.toString()));
            byte[] bytes = new byte[encoded.remaining()]; encoded.get(bytes); return bytes;
        } catch (java.nio.charset.CharacterCodingException e) { throw new IllegalArgumentException("Journal contains invalid Unicode.", e); }
    }
    private static Map<UUID, Operation> decode(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid journal size");
        String text;
        try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString(); }
        catch (java.nio.charset.CharacterCodingException e) { throw new IllegalArgumentException("Invalid UTF-8", e); }
        JsonObject root = BookConfig.strictObject(text);
        if (!root.keySet().equals(Set.of("schemaVersion", "operations")) || integer(root.get("schemaVersion"), "schemaVersion") != 1 || !root.get("operations").isJsonArray()) throw new IllegalArgumentException("Invalid journal schema");
        JsonArray values = root.getAsJsonArray("operations"); if (values.size() > MAX_OPERATIONS) throw new IllegalArgumentException("Too many operations");
        Map<UUID, Operation> result = new LinkedHashMap<>(); Set<UUID> pendingActors = new HashSet<>(); int pending = 0;
        for (var value : values) {
            if (!value.isJsonObject()) throw new IllegalArgumentException("Invalid operation");
            JsonObject obj = value.getAsJsonObject();
            Set<String> fields = Set.of("request", "actor", "provider", "source", "hash", "count", "stage");
            Set<String> all = new HashSet<>(fields); all.add("before"); all.add("after");
            if (!obj.keySet().equals(fields) && !obj.keySet().equals(all)) throw new IllegalArgumentException("Invalid operation fields");
            Stage stage;
            try { stage = Stage.valueOf(string(obj.get("stage"), "stage")); }
            catch (RuntimeException e) { throw new IllegalArgumentException("Invalid operation stage", e); }
            boolean hasEvidence = obj.keySet().equals(all);
            if ((stage == Stage.CLAIMED) != hasEvidence) throw new IllegalArgumentException("Invalid operation evidence fields");
            Operation op = new Operation(uuid(obj.get("request"), "request"), uuid(obj.get("actor"), "actor"),
                    string(obj.get("provider"), "provider"), string(obj.get("source"), "source"), string(obj.get("hash"), "hash"),
                    integer(obj.get("count"), "count"), stage,
                    hasEvidence ? snapshot(obj.get("before")) : null, hasEvidence ? snapshot(obj.get("after")) : null);
            if (result.putIfAbsent(op.request(), op) != null) throw new IllegalArgumentException("Duplicate request");
            if (op.stage() == Stage.CLAIMED && (++pending > MAX_PENDING || !pendingActors.add(op.actor()))) throw new IllegalArgumentException("Invalid pending operations");
        }
        return Collections.unmodifiableMap(result);
    }
    private static String string(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(field + " must be a string");
        String text = value.getAsString();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) { if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw new IllegalArgumentException(field + " has invalid Unicode"); }
            else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException(field + " has invalid Unicode");
        }
        return text;
    }
    private static int integer(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("0|[1-9][0-9]*")) throw new IllegalArgumentException(field + " must be a canonical non-negative integer");
        try { return Integer.parseInt(value.getAsString()); } catch (NumberFormatException e) { throw new IllegalArgumentException(field + " out of range", e); }
    }
    private static UUID uuid(JsonElement value, String field) {
        String text = string(value, field);
        try { UUID uuid = UUID.fromString(text); if (!uuid.toString().equals(text)) throw new IllegalArgumentException("Noncanonical UUID"); return uuid; }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException(field + " must be a canonical UUID", e); }
    }
    private static InventorySnapshot snapshot(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Inventory snapshot must be an object");
        JsonObject obj = value.getAsJsonObject();
        if (!obj.keySet().equals(Set.of("size", "stacks")) || !obj.get("stacks").isJsonArray()) throw new IllegalArgumentException("Invalid inventory snapshot fields");
        int size = integer(obj.get("size"), "size"); JsonArray values = obj.getAsJsonArray("stacks");
        if (values.size() != size || size != 36) throw new IllegalArgumentException("Invalid inventory snapshot size");
        List<String> stacks = new ArrayList<>(size);
        for (JsonElement item : values) {
            String stack = string(item, "stack");
            BookConfig.strictObject(stack); // Validate the original before permissive canonicalization can discard evidence.
            stacks.add(stack);
        }
        InventorySnapshot snapshot = new InventorySnapshot(size, stacks);
        for (String stack : snapshot.stacks()) string(new com.google.gson.JsonPrimitive(stack), "canonical stack");
        return snapshot;
    }

}
