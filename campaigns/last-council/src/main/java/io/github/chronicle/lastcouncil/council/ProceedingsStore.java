package io.github.chronicle.lastcouncil.council;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;

/** Strict, bounded proceedings journal with observed-byte conflict detection. */
public final class ProceedingsStore implements ProceedingsRepository {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private final AtomicDataFile file;
    private boolean loaded;

    public ProceedingsStore(Path path) { file = new AtomicDataFile(path, MAX_BYTES); }

    @Override public synchronized ProceedingsState load() throws IOException {
        loaded = false;
        byte[] bytes = file.load();
        try {
            ProceedingsState state = bytes == null ? ProceedingsState.empty() : decode(decodeUtf8(bytes));
            loaded = true;
            return state;
        } catch (RuntimeException | CharacterCodingException failure) {
            throw new IOException("Invalid proceedings ledger", failure);
        }
    }

    @Override public synchronized void verifyUnchanged() throws IOException {
        if (!loaded) throw new IOException("Load proceedings before verifying");
        file.verifyUnchanged();
    }

    @Override public synchronized void save(ProceedingsState state) throws IOException {
        if (!loaded) throw new IOException("Load proceedings before saving");
        try {
            JsonObject root = new JsonObject();
            root.addProperty("schemaVersion", 2);
            root.add("state", JSON.toJsonTree(Objects.requireNonNull(state)));
            byte[] bytes = encodeUtf8(root + "\n");
            if (bytes.length > MAX_BYTES || !decode(decodeUtf8(bytes)).equals(state))
                throw new IllegalArgumentException("Proceedings round trip failed or ledger is too large");
            file.save(bytes);
        } catch (RuntimeException | CharacterCodingException failure) {
            throw new IOException("Invalid proceedings state", failure);
        }
    }

    private static ProceedingsState decode(String text) {
        JsonObject root = BookConfig.strictObject(text);
        if (!root.keySet().equals(Set.of("schemaVersion", "state"))) throw new IllegalArgumentException("Unexpected root fields");
        JsonElement version = root.get("schemaVersion");
        if (!version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber() || !Set.of("1","2").contains(version.getAsString()))
            throw new IllegalArgumentException("Unsupported schema version");
        if(version.getAsString().equals("1")) {
            JsonObject state=root.getAsJsonObject("state");
            if(state.has("succession"))throw new IllegalArgumentException("Unexpected legacy succession field");
            state.add("succession",JSON.toJsonTree(SuccessionState.empty()));
            JsonElement pending=state.get("pending");
            if(pending!=null&&!pending.isJsonNull()) {
                if(pending.getAsJsonObject().has("result"))throw new IllegalArgumentException("Unexpected legacy result field");
                pending.getAsJsonObject().add("result",JsonNull.INSTANCE);
            }
        }
        validate(root.get("state"), ProceedingsState.class, 0);
        return Objects.requireNonNull(JSON.fromJson(root.get("state"), ProceedingsState.class));
    }

    /** Only types reachable from the fixed ProceedingsState record are considered. */
    private static void validate(JsonElement element, Type type, int depth) {
        if (depth > 24 || element == null) throw new IllegalArgumentException("Invalid record depth or missing value");
        if (element.isJsonNull()) {
            if (type instanceof Class<?> c && c.isPrimitive()) throw new IllegalArgumentException("Null primitive");
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            Type[] args = parameterized.getActualTypeArguments();
            if (raw == Map.class) {
                if (!element.isJsonObject() || element.getAsJsonObject().size() > 1024) throw new IllegalArgumentException("Invalid map");
                if (args[0] != String.class && args[0] != UUID.class) throw new IllegalArgumentException("Unsupported map key");
                for (var entry : element.getAsJsonObject().entrySet()) {
                    validUnicode(entry.getKey());
                    if (args[0] == UUID.class) canonicalUuid(entry.getKey());
                    validate(entry.getValue(), args[1], depth + 1);
                }
                return;
            }
            if (raw == List.class || raw == Set.class) {
                if (!element.isJsonArray() || element.getAsJsonArray().size() > 1024) throw new IllegalArgumentException("Invalid collection");
                Set<JsonElement> seen = new HashSet<>();
                for (JsonElement value : element.getAsJsonArray()) {
                    if (raw == Set.class && !seen.add(value)) throw new IllegalArgumentException("Duplicate set member");
                    validate(value, args[0], depth + 1);
                }
                return;
            }
            throw new IllegalArgumentException("Unsupported collection type");
        }
        if (!(type instanceof Class<?> c)) throw new IllegalArgumentException("Unsupported type");
        if (c.isRecord()) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Expected record");
            JsonObject object = element.getAsJsonObject();
            Set<String> names = new HashSet<>();
            for (RecordComponent component : c.getRecordComponents()) names.add(component.getName());
            if (!object.keySet().equals(names)) throw new IllegalArgumentException("Unexpected record fields");
            for (RecordComponent component : c.getRecordComponents()) validate(object.get(component.getName()), component.getGenericType(), depth + 1);
        } else if (c == String.class || c == UUID.class || c.isEnum()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string");
            String value = element.getAsString();
            validUnicode(value);
            if (c == UUID.class) canonicalUuid(value);
            if (c.isEnum() && Arrays.stream(c.getEnumConstants()).noneMatch(v -> ((Enum<?>) v).name().equals(value))) throw new IllegalArgumentException("Invalid enum");
        } else if (c == boolean.class) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Expected boolean");
        } else if (c == int.class || c == long.class) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber() || !element.getAsString().matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException("Expected integer");
            if (c == int.class) Integer.parseInt(element.getAsString()); else Long.parseLong(element.getAsString());
        } else throw new IllegalArgumentException("Unsupported record field type: " + c.getName());
    }

    private static void validUnicode(String value) {
        try { encodeUtf8(value); }
        catch (CharacterCodingException failure) { throw new IllegalArgumentException("Invalid Unicode", failure); }
    }
    private static void canonicalUuid(String text) {
        if (!UUID.fromString(text).toString().equals(text)) throw new IllegalArgumentException("Noncanonical UUID");
    }
    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static byte[] encodeUtf8(String value) throws CharacterCodingException {
        ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
        byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); return bytes;
    }
}
