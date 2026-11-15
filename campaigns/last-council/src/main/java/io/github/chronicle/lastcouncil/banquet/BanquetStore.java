package io.github.chronicle.lastcouncil.banquet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import static io.github.chronicle.lastcouncil.banquet.BanquetState.*;

/** Strict private schema-1 codec. Contains spoilers; never send or export its bytes to players. */
public final class BanquetStore implements BanquetRepository {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private final AtomicDataFile file;
    private boolean loaded;

    public BanquetStore(Path path) { file = new AtomicDataFile(path, MAX_BYTES); }

    @Override public synchronized BanquetState load() throws IOException {
        loaded = false;
        byte[] bytes = file.load();
        BanquetState state = bytes == null ? BanquetState.empty() : decode(bytes);
        loaded = true;
        return state;
    }

    @Override public synchronized void verifyUnchanged() throws IOException {
        if (!loaded) throw new IOException("Load banquet records before verifying or saving.");
        file.verifyUnchanged();
    }

    @Override public synchronized void save(BanquetState state) throws IOException {
        if (!loaded) throw new IOException("Load banquet records before saving.");
        byte[] bytes = encode(state);
        if (!decode(bytes).equals(state)) throw new IOException("Banquet serialization failed validation.");
        file.save(bytes);
    }

    public static byte[] encode(BanquetState state) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 2);
        root.addProperty("revision", state.revision());
        root.add("supplies", entries(state.supplies(), (row, supply) -> {
            row.addProperty("kind", supply.kind().name());
            uuid(row, "issuer", supply.issuer());
            uuid(row, "poisoner", supply.poisoner());
            uuid(row, "usedBy", supply.usedBy());
            uuid(row, "useRequest", supply.useRequest());
        }));
        root.add("illnesses", entries(state.illnesses(), (row, illness) -> {
            row.addProperty("onsetTicks", illness.onsetTicks());
            row.addProperty("durationTicks", illness.durationTicks());
            row.addProperty("poisonTicks", illness.poisonTicks());
            row.addProperty("lethal", illness.lethal());
        }));
        root.add("operations", entries(state.operations(), (row, op) -> {
            uuid(row, "actor", op.actor());
            row.addProperty("type", op.type().name());
            uuid(row, "inputToken", op.inputToken());
            uuid(row, "secondToken", op.secondToken());
            row.add("issueKind", op.issueKind() == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(op.issueKind().name()));
            JsonElement timing = JsonNull.INSTANCE;
            if (op.timing() != null) {
                JsonObject bounds = new JsonObject();
                bounds.addProperty("onsetTicks", op.timing().onsetTicks());
                bounds.addProperty("durationTicks", op.timing().durationTicks());
                bounds.addProperty("poisonTicks", op.timing().poisonTicks());
                bounds.addProperty("lethal", op.timing().lethal());
                timing = bounds;
            }
            row.add("timing", timing);
            row.add("repeatPolicy", op.repeatPolicy() == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(op.repeatPolicy().name()));
        }));
        JsonArray eliminated = new JsonArray();
        state.eliminated().stream().sorted().forEach(id -> eliminated.add(id.toString()));
        root.add("eliminated", eliminated);
        byte[] bytes = (root + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Banquet records exceed their size limit.");
        return bytes;
    }

    public static BanquetState decode(byte[] bytes) throws IOException {
        try {
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
                throw new IllegalArgumentException("Invalid byte bounds");
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root = BookConfig.strictObject(text);
            long schema = integer(root.get("schema"));
            if (schema != 1 && schema != 2) throw new IllegalArgumentException("Unsupported schema");
            fields(root, schema == 1 ? new String[] {"schema", "revision", "supplies", "illnesses", "operations"}
                    : new String[] {"schema", "revision", "supplies", "illnesses", "operations", "eliminated"});
            Map<UUID, Supply> supplies = new HashMap<>();
            for (JsonElement value : array(root, "supplies", MAX_SUPPLIES)) {
                JsonObject row = value.getAsJsonObject();
                fields(row, "id", "kind", "issuer", "poisoner", "usedBy", "useRequest");
                unique(supplies, requiredUuid(row, "id"), new Supply(
                    SupplyKind.valueOf(string(row.get("kind"))), requiredUuid(row, "issuer"),
                    nullableUuid(row, "poisoner"), nullableUuid(row, "usedBy"), nullableUuid(row, "useRequest")));
            }
            Map<UUID, Illness> illnesses = new HashMap<>();
            for (JsonElement value : array(root, "illnesses", MAX_ILLNESSES)) {
                JsonObject row = value.getAsJsonObject();
                fields(row, schema == 1 ? new String[] {"id", "onsetTicks", "durationTicks"}
                        : new String[] {"id", "onsetTicks", "durationTicks", "poisonTicks", "lethal"});
                unique(illnesses, requiredUuid(row, "id"), schema == 1
                        ? new Illness(integer(row.get("onsetTicks")), integer(row.get("durationTicks")))
                        : new Illness(integer(row.get("onsetTicks")), integer(row.get("durationTicks")),
                                integer(row.get("poisonTicks")), bool(row.get("lethal"))));
            }
            Map<UUID, Operation> operations = new HashMap<>();
            for (JsonElement value : array(root, "operations", MAX_OPERATIONS)) {
                JsonObject row = value.getAsJsonObject();
                fields(row, "id", "actor", "type", "inputToken", "secondToken", "issueKind", "timing", "repeatPolicy");
                Timing timing = null;
                if (!row.get("timing").isJsonNull()) {
                    JsonObject bounds = row.getAsJsonObject("timing");
                    fields(bounds, schema == 1 ? new String[] {"onsetTicks", "durationTicks"}
                            : new String[] {"onsetTicks", "durationTicks", "poisonTicks", "lethal"});
                    timing = schema == 1 ? new Timing(integer(bounds.get("onsetTicks")), integer(bounds.get("durationTicks")))
                            : new Timing(integer(bounds.get("onsetTicks")), integer(bounds.get("durationTicks")),
                                    integer(bounds.get("poisonTicks")), bool(bounds.get("lethal")));
                }
                unique(operations, requiredUuid(row, "id"), new Operation(requiredUuid(row, "actor"),
                    OperationType.valueOf(string(row.get("type"))), requiredUuid(row, "inputToken"),
                    nullableUuid(row, "secondToken"), row.get("issueKind").isJsonNull() ? null : SupplyKind.valueOf(string(row.get("issueKind"))),
                    timing, row.get("repeatPolicy").isJsonNull() ? null : RepeatPolicy.valueOf(string(row.get("repeatPolicy")))));
            }
            Set<UUID> eliminated = new java.util.HashSet<>();
            if (schema == 2) for (JsonElement value : array(root, "eliminated", MAX_ILLNESSES)) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected eliminated UUID");
                String raw = value.getAsString(); UUID id = UUID.fromString(raw);
                if (!id.toString().equals(raw) || !eliminated.add(id)) throw new IllegalArgumentException("Invalid eliminated UUID");
            }
            return new BanquetState(integer(root.get("revision")), supplies, illnesses, operations, eliminated);
        } catch (RuntimeException | CharacterCodingException invalid) {
            throw new IOException("Invalid private banquet records; retained file was preserved.", invalid);
        }
    }

    private static <T> JsonArray entries(Map<UUID, T> values, BiConsumer<JsonObject, T> write) {
        JsonArray array = new JsonArray();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            JsonObject row = new JsonObject();
            uuid(row, "id", entry.getKey());
            write.accept(row, entry.getValue());
            array.add(row);
        });
        return array;
    }

    private static void fields(JsonObject value, String... expected) {
        if (!value.keySet().equals(Set.of(expected))) throw new IllegalArgumentException("Incorrect fields");
    }
    private static JsonArray array(JsonObject root, String key, int maximum) {
        JsonArray array = root.getAsJsonArray(key);
        if (array.size() > maximum) throw new IllegalArgumentException("Too many records");
        return array;
    }
    private static String string(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string");
        return value.getAsString();
    }
    private static long integer(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("0|[1-9][0-9]*"))
            throw new IllegalArgumentException("Expected nonnegative integer");
        return Long.parseLong(value.getAsString());
    }
    private static boolean bool(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("Expected boolean");
        return value.getAsBoolean();
    }
    private static UUID requiredUuid(JsonObject row, String key) {
        String value = string(row.get(key));
        UUID uuid = UUID.fromString(value);
        if (!uuid.toString().equals(value)) throw new IllegalArgumentException("Expected canonical UUID");
        return uuid;
    }
    private static UUID nullableUuid(JsonObject row, String key) { return row.get(key).isJsonNull() ? null : requiredUuid(row, key); }
    private static void uuid(JsonObject row, String key, UUID value) {
        row.add(key, value == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(value.toString()));
    }
    private static <T> void unique(Map<UUID, T> target, UUID id, T value) {
        if (target.putIfAbsent(id, value) != null) throw new IllegalArgumentException("Duplicate record ID");
    }
}
