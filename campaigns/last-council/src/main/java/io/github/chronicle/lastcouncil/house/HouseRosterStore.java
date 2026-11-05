package io.github.chronicle.lastcouncil.house;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict schema-1 world storage. Invalid input is never repaired or overwritten. */
public final class HouseRosterStore implements HouseRosterRepository {
    private static final long MAX_BYTES = 64 * 1024;
    private final AtomicDataFile file;
    private boolean initialized;
    public HouseRosterStore(Path path) { file = new AtomicDataFile(path, MAX_BYTES); }
    @Override public HouseRoster load() throws IOException {
        initialized = false;
        byte[] bytes = file.load();
        HouseRoster result = bytes == null ? HouseRoster.empty() : decode(bytes);
        initialized = true;
        return result;
    }
    @Override public void save(HouseRoster roster) throws IOException {
        if (!initialized) throw new IOException("roster must be successfully loaded before saving");
        byte[] bytes = encode(roster);
        if (!decode(bytes).equals(roster)) throw new IOException("roster serialization failed validation");
        file.save(bytes);
    }
    private static HouseRoster decode(byte[] bytes) throws IOException {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root = BookConfig.strictObject(text);
            exact(root, Set.of("schema", "revision", "assignments"));
            if (integer(root.get("schema")) != 1) throw new IllegalArgumentException("unsupported schema");
            long revision = integer(root.get("revision"));
            JsonObject entries = object(root.get("assignments"));
            if (entries.size() > HouseRoster.MAX_HOUSES) throw new IllegalArgumentException("too many assignments");
            Map<UUID, HouseAssignment> result = new LinkedHashMap<>();
            for (var entry : entries.entrySet()) {
                UUID key = uuid(entry.getKey());
                JsonObject value = object(entry.getValue());
                exact(value, Set.of("owner", "houseId", "councilMember", "candidate"));
                HouseAssignment assignment = new HouseAssignment(uuid(string(value.get("owner"))), string(value.get("houseId")), bool(value.get("councilMember")), bool(value.get("candidate")));
                if (result.putIfAbsent(key, assignment) != null) throw new IllegalArgumentException("duplicate owner");
            }
            return new HouseRoster(revision, result);
        } catch (CharacterCodingException | RuntimeException e) { throw new IOException("invalid house roster; file preserved", e); }
    }
    private static byte[] encode(HouseRoster roster) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("revision", roster.revision());
        JsonObject entries = new JsonObject();
        roster.assignments().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            HouseAssignment assignment = entry.getValue();
            JsonObject value = new JsonObject();
            value.addProperty("owner", assignment.owner().toString());
            value.addProperty("houseId", assignment.houseId());
            value.addProperty("councilMember", assignment.councilMember());
            value.addProperty("candidate", assignment.candidate());
            entries.add(entry.getKey().toString(), value);
        });
        root.add("assignments", entries);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static JsonObject object(JsonElement e) { if (e == null || !e.isJsonObject()) throw new IllegalArgumentException("expected object"); return e.getAsJsonObject(); }
    private static void exact(JsonObject o, Set<String> fields) { if (!o.keySet().equals(fields)) throw new IllegalArgumentException("incorrect fields"); }
    private static String string(JsonElement e) { if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("expected string"); return e.getAsString(); }
    private static boolean bool(JsonElement e) { if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("expected boolean"); return e.getAsBoolean(); }
    private static long integer(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || !e.getAsString().matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("expected nonnegative integer");
        return Long.parseLong(e.getAsString());
    }
    private static UUID uuid(String text) { UUID result = UUID.fromString(text); if (!result.toString().equals(text)) throw new IllegalArgumentException("UUID must be canonical"); return result; }
}
