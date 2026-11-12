package io.github.chronicle.lastcouncil.banquet;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.content.InventorySnapshot;
import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;

/** Private recovery evidence; inventory and ledger recovery are performed by the caller. */
public final class BanquetJournal {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public static final int MAX_RECEIPTS = 4096;
    public enum Stage { COMPLETE, CANCELLED }

    public record Intent(UUID request, UUID actor, UUID owner, String fingerprint, String description,
                         String beforeLedgerHash, BanquetState afterLedger, InventorySnapshot before, InventorySnapshot after) {
        public Intent {
            common(request, actor, owner, fingerprint, description);
            hash(beforeLedgerHash);
            Objects.requireNonNull(afterLedger);
            snapshot(before); snapshot(after);
            if (before.equals(after)) throw new IllegalArgumentException("Inventory evidence must change");
        }
    }
    public record Receipt(UUID request, UUID actor, UUID owner, String fingerprint, String description, Stage stage) {
        public Receipt { common(request, actor, owner, fingerprint, description); Objects.requireNonNull(stage); }
    }

    private final AtomicDataFile file;
    private Intent pending;
    private List<Receipt> receipts;
    private boolean poisoned;

    public BanquetJournal(Path path) throws IOException {
        file = new AtomicDataFile(path, MAX_BYTES);
        byte[] bytes = file.load();
        if (bytes == null) {
            receipts = List.of();
            file.save(encode(null, receipts));
        } else {
            Decoded decoded = decode(bytes);
            pending = decoded.pending(); receipts = decoded.receipts();
        }
    }
    public synchronized void verify() throws IOException { file.verifyUnchanged(); }
    public synchronized Intent pending() { return pending; }
    public synchronized Receipt get(UUID request) { return receipts.stream().filter(r -> r.request().equals(request)).findFirst().orElse(null); }
    public synchronized List<Receipt> receipts() { return receipts; }
    public synchronized boolean poisoned() { return poisoned; }

    public synchronized void claim(Intent intent) throws IOException {
        writable(); Objects.requireNonNull(intent);
        if (pending != null) throw new IllegalStateException("A banquet operation is already pending");
        if (get(intent.request()) != null) throw new IllegalArgumentException("Request already has a receipt");
        if (receipts.size() >= MAX_RECEIPTS) throw new IllegalStateException("Banquet journal is full");
        byte[] bytes = encode(intent, receipts);
        publish(bytes);
        pending = intent;
    }
    public synchronized void finish(UUID request, Stage stage) throws IOException {
        writable(); Objects.requireNonNull(request); Objects.requireNonNull(stage);
        Receipt existing = get(request);
        if (existing != null) {
            if (existing.stage() == stage) return;
            throw new IllegalArgumentException("Request already has a different outcome");
        }
        if (pending == null || !pending.request().equals(request)) throw new IllegalArgumentException("No matching pending request");
        var next = new ArrayList<>(receipts);
        next.add(new Receipt(pending.request(), pending.actor(), pending.owner(), pending.fingerprint(), pending.description(), stage));
        List<Receipt> frozen = List.copyOf(next);
        byte[] bytes = encode(null, frozen);
        publish(bytes);
        pending = null; receipts = frozen;
    }
    private void writable() throws IOException { if (poisoned) throw new IOException("Journal write failed; reload before further writes"); }
    private void publish(byte[] bytes) throws IOException {
        try { file.save(bytes); } catch (IOException failure) { poisoned = true; throw failure; }
    }

    private record Decoded(Intent pending, List<Receipt> receipts) { }
    private static byte[] encode(Intent pending, List<Receipt> receipts) throws IOException {
        JsonObject root = new JsonObject(); root.addProperty("schema", 1);
        if (pending == null) root.add("pending", JsonNull.INSTANCE);
        else {
            JsonObject row = commonJson(pending.request(), pending.actor(), pending.owner(), pending.fingerprint(), pending.description());
            row.addProperty("beforeLedgerHash", pending.beforeLedgerHash());
            row.add("afterLedger", BookConfig.strictObject(new String(BanquetStore.encode(pending.afterLedger()), StandardCharsets.UTF_8)));
            row.add("before", snapshotJson(pending.before())); row.add("after", snapshotJson(pending.after()));
            root.add("pending", row);
        }
        JsonArray array = new JsonArray();
        for (Receipt receipt : receipts) {
            JsonObject row = commonJson(receipt.request(), receipt.actor(), receipt.owner(), receipt.fingerprint(), receipt.description());
            row.addProperty("stage", receipt.stage().name()); array.add(row);
        }
        root.add("receipts", array);
        byte[] bytes = (root + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IllegalStateException("Banquet journal exceeds its size limit");
        return bytes;
    }
    private static Decoded decode(byte[] bytes) throws IOException {
        try {
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid byte bounds");
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root = BookConfig.strictObject(text); fields(root, "schema", "pending", "receipts");
            JsonElement schema = root.get("schema");
            if (!schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isNumber() || !schema.getAsString().equals("1")) throw new IllegalArgumentException("Invalid schema");
            Intent pending = null;
            if (!root.get("pending").isJsonNull()) {
                JsonObject row = root.getAsJsonObject("pending");
                fields(row, "request", "actor", "owner", "fingerprint", "description", "beforeLedgerHash", "afterLedger", "before", "after");
                if (!row.get("afterLedger").isJsonObject()) throw new IllegalArgumentException("Expected ledger object");
                pending = new Intent(uuid(row, "request"), uuid(row, "actor"), uuid(row, "owner"), string(row, "fingerprint"), string(row, "description"),
                        string(row, "beforeLedgerHash"), BanquetStore.decode(row.get("afterLedger").toString().getBytes(StandardCharsets.UTF_8)),
                        snapshot(row, "before"), snapshot(row, "after"));
            }
            JsonArray array = root.getAsJsonArray("receipts");
            if (array.size() > MAX_RECEIPTS) throw new IllegalArgumentException("Too many receipts");
            List<Receipt> receipts = new ArrayList<>(); Set<UUID> ids = new HashSet<>();
            if (pending != null) ids.add(pending.request());
            for (JsonElement value : array) {
                JsonObject row = value.getAsJsonObject(); fields(row, "request", "actor", "owner", "fingerprint", "description", "stage");
                Receipt receipt = new Receipt(uuid(row, "request"), uuid(row, "actor"), uuid(row, "owner"), string(row, "fingerprint"), string(row, "description"), Stage.valueOf(string(row, "stage")));
                if (!ids.add(receipt.request())) throw new IllegalArgumentException("Duplicate request");
                receipts.add(receipt);
            }
            if (pending != null && receipts.size() >= MAX_RECEIPTS) throw new IllegalArgumentException("No room for pending receipt");
            return new Decoded(pending, List.copyOf(receipts));
        } catch (RuntimeException | CharacterCodingException invalid) {
            throw new IOException("Invalid private banquet journal; retained file was preserved", invalid);
        }
    }
    private static JsonObject commonJson(UUID request, UUID actor, UUID owner, String fingerprint, String description) {
        JsonObject row = new JsonObject(); row.addProperty("request", request.toString()); row.addProperty("actor", actor.toString());
        row.addProperty("owner", owner.toString()); row.addProperty("fingerprint", fingerprint); row.addProperty("description", description); return row;
    }
    private static JsonArray snapshotJson(InventorySnapshot snapshot) { JsonArray array = new JsonArray(); snapshot.stacks().forEach(array::add); return array; }
    private static InventorySnapshot snapshot(JsonObject row, String key) {
        JsonArray array = row.getAsJsonArray(key); if (array.size() != 43) throw new IllegalArgumentException("Expected 43 slots");
        List<String> stacks = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected stack string");
            String stack = element.getAsString(); BookConfig.strictObject(stack); stacks.add(stack);
        }
        return new InventorySnapshot(43, stacks);
    }
    private static void snapshot(InventorySnapshot value) {
        Objects.requireNonNull(value); if (value.size() != 43) throw new IllegalArgumentException("Expected 43 slots");
        for (String stack : value.stacks()) BookConfig.strictObject(stack);
    }
    private static void common(UUID request, UUID actor, UUID owner, String fingerprint, String description) {
        Objects.requireNonNull(request); Objects.requireNonNull(actor); Objects.requireNonNull(owner); hash(fingerprint);
        if (description == null || description.length() > 512) throw new IllegalArgumentException("Invalid description");
    }
    private static void hash(String value) { if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Expected lowercase SHA-256 hash"); }
    private static void fields(JsonObject object, String... names) { if (!object.keySet().equals(Set.of(names))) throw new IllegalArgumentException("Incorrect fields"); }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key); if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string"); return value.getAsString();
    }
    private static UUID uuid(JsonObject object, String key) {
        String value = string(object, key); UUID result = UUID.fromString(value);
        if (!result.toString().equals(value)) throw new IllegalArgumentException("Expected canonical UUID"); return result;
    }
}
