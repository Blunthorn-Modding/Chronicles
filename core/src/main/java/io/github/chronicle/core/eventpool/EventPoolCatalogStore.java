package io.github.chronicle.core.eventpool;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;

/** Portable pool definitions. The file contains no draw or world progress. */
public final class EventPoolCatalogStore {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private final AtomicDataFile file;
    public EventPoolCatalogStore(Path path) { file = new AtomicDataFile(path, MAX_BYTES); }
    public byte[] load() throws IOException { return file.load(); }
    public void save(byte[] bytes) throws IOException { decode(bytes); file.save(bytes); }
    public static byte[] encode(Map<String, EventPoolDefinition> pools) throws IOException {
        if (pools.size() > 64) throw new IOException("Too many event pools");
        JsonObject root = new JsonObject(); root.addProperty("schema", 1); JsonArray items = new JsonArray();
        for (var pair : new TreeMap<>(pools).entrySet()) {
            var pool = pair.getValue(); if (!pair.getKey().equals(pool.id())) throw new IOException("Pool key mismatch");
            JsonObject item = new JsonObject(); item.addProperty("id", pool.id()); item.addProperty("label", pool.label());
            JsonArray entries = new JsonArray(); for (var entry : pool.entries()) { JsonObject e = new JsonObject(); e.addProperty("eventId", entry.eventId()); e.addProperty("weight", entry.weight()); entries.add(e); }
            item.add("entries", entries); items.add(item);
        }
        root.add("pools", items); byte[] bytes = (root + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES || !decode(bytes).equals(pools)) throw new IOException("Invalid serialized pool catalogue");
        return bytes;
    }
    public static Map<String, EventPoolDefinition> decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Pool catalogue outside bounds");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root = BookConfig.strictObject(text); exact(root, "schema", "pools"); if (integer(root.get("schema")) != 1) throw new IllegalArgumentException("Unsupported schema");
            JsonArray pools = array(root.get("pools")); if (pools.size() > 64) throw new IllegalArgumentException("Too many pools");
            Map<String, EventPoolDefinition> result = new TreeMap<>();
            for (JsonElement value : pools) {
                JsonObject item = object(value); exact(item, "id", "label", "entries"); JsonArray all = array(item.get("entries")); if (all.size() > 64) throw new IllegalArgumentException("Too many entries");
                List<EventPoolDefinition.Entry> entries = new ArrayList<>();
                for (JsonElement v : all) { JsonObject e = object(v); exact(e, "eventId", "weight"); entries.add(new EventPoolDefinition.Entry(string(e.get("eventId")), integer(e.get("weight")))); }
                var pool = new EventPoolDefinition(string(item.get("id")), string(item.get("label")), entries);
                if (result.putIfAbsent(pool.id(), pool) != null) throw new IllegalArgumentException("Duplicate pool");
            }
            return Collections.unmodifiableMap(result);
        } catch (CharacterCodingException | RuntimeException invalid) { throw new IOException("Invalid pool catalogue; preserved", invalid); }
    }
    private static void exact(JsonObject o, String... names) { if (!o.keySet().equals(Set.of(names))) throw new IllegalArgumentException("Unexpected fields"); }
    private static JsonObject object(JsonElement e) { if (e == null || !e.isJsonObject()) throw new IllegalArgumentException("Expected object"); return e.getAsJsonObject(); }
    private static JsonArray array(JsonElement e) { if (e == null || !e.isJsonArray()) throw new IllegalArgumentException("Expected array"); return e.getAsJsonArray(); }
    private static String string(JsonElement e) { if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string"); return e.getAsString(); }
    private static int integer(JsonElement e) { if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || !e.getAsString().matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("Expected integer"); return Integer.parseInt(e.getAsString()); }
}
