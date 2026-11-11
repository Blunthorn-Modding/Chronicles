package io.github.chronicle.core.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Strict bounded wire codec for replacing one complete loot table from an authoring screen.
 * Components remain JSON objects and are never silently dropped or converted to strings.
 */
public final class LootTableDraftJson {
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private LootTableDraftJson() { }

    public static String encode(LootType type) {
        if (type == null) throw new IllegalArgumentException("loot table is required");
        String encoded = encodeObject(type).toString();
        requireBound(encoded);
        return encoded;
    }

    public static LootType decode(String source) {
        requireBound(source);
        return decodeObject(object(LootJson.parse(source, "loot table"), "loot table"), "loot table");
    }

    static LootType decodeObject(JsonObject object, String field) {
        var allowed = Set.of("id", "label", "description", "recommendedChests", "refillMinutes", "entries", "books");
        if(object.keySet().stream().anyMatch(k->!allowed.contains(k)))throw new IllegalArgumentException(field+" has unknown fields");
        var bookEntries = new ArrayList<io.github.chronicle.core.books.BookChance>();
        if(object.has("books"))for(var element:array(object.get("books"),field+".books")){
            var b=object(element,field+".book");exactFields(b,Set.of("bookId","chancePercent"),field+".book");
            bookEntries.add(new io.github.chronicle.core.books.BookChance(string(required(b,"bookId"),"bookId"),decimal(required(b,"chancePercent"),"chancePercent")));
        }
        JsonArray entries = array(required(object, "entries"), field + ".entries");
        if (entries.size() > LootType.MAX_ENTRIES) throw new IllegalArgumentException(field + ".entries may contain at most " + LootType.MAX_ENTRIES + " entries");
        List<LootEntry> decoded = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) decoded.add(decodeEntry(object(entries.get(i), field + ".entries[" + i + "]"), field + ".entries[" + i + "]"));
        return new LootType(string(required(object, "id"), field + ".id"), string(required(object, "label"), field + ".label"),
                string(required(object, "description"), field + ".description"), integer(required(object, "recommendedChests"), field + ".recommendedChests"),
                decimal(required(object, "refillMinutes"), field + ".refillMinutes"), decoded,bookEntries);
    }

    static JsonObject encodeObject(LootType type) {
        JsonObject result = new JsonObject();
        result.addProperty("id", type.id()); result.addProperty("label", type.label()); result.addProperty("description", type.description());
        result.addProperty("recommendedChests", type.recommendedChests()); result.addProperty("refillMinutes", type.refillMinutes());
        JsonArray entries = new JsonArray();
        for (LootEntry entry : type.entries()) {
            JsonObject value = new JsonObject();
            value.addProperty("item", entry.item()); value.addProperty("chancePercent", entry.chancePercent());
            value.addProperty("minCount", entry.minCount()); value.addProperty("maxCount", entry.maxCount());
            value.add("components", LootJson.object(entry.componentsJson(), "loot components"));
            entries.add(value);
        }
        result.add("entries", entries);
        if(!type.books().isEmpty()){JsonArray books=new JsonArray();for(var b:type.books()){JsonObject value=new JsonObject();value.addProperty("bookId",b.bookId());value.addProperty("chancePercent",b.chancePercent());books.add(value);}result.add("books",books);}
        return result;
    }

    private static LootEntry decodeEntry(JsonObject object, String field) {
        Set<String> allowed = Set.of("item", "chancePercent", "minCount", "maxCount", "components");
        for (String name : object.keySet()) if (!allowed.contains(name)) throw new IllegalArgumentException(field + " has unknown field: " + name);
        for (String required : Set.of("item", "chancePercent", "minCount", "maxCount")) required(object, required);
        JsonElement components = object.has("components") ? object.get("components") : null;
        if (components != null && (components.isJsonNull() || !components.isJsonObject())) throw new IllegalArgumentException(field + ".components must be a JSON object");
        return new LootEntry(string(required(object, "item"), field + ".item"), decimal(required(object, "chancePercent"), field + ".chancePercent"),
                integer(required(object, "minCount"), field + ".minCount"), integer(required(object, "maxCount"), field + ".maxCount"), components == null ? "{}" : components.toString());
    }

    private static void requireBound(String source) {
        if (source == null) throw new IllegalArgumentException("loot table is required");
        if (source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("loot table exceeds 2 MiB limit");
    }
    private static JsonObject object(JsonElement value, String field) { if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(field + " must be an object"); return value.getAsJsonObject(); }
    private static JsonArray array(JsonElement value, String field) { if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(field + " must be an array"); return value.getAsJsonArray(); }
    private static JsonElement required(JsonObject object, String field) { if (!object.has(field) || object.get(field).isJsonNull()) throw new IllegalArgumentException("missing " + field); return object.get(field); }
    private static void exactFields(JsonObject object, Set<String> allowed, String field) {
        if (!object.keySet().equals(allowed)) {
            List<String> unknown = object.keySet().stream().filter(name -> !allowed.contains(name)).sorted().toList();
            List<String> missing = allowed.stream().filter(name -> !object.has(name)).sorted().toList();
            throw new IllegalArgumentException(field + " fields do not match schema (unknown=" + unknown + ", missing=" + missing + ")");
        }
    }
    private static String string(JsonElement value, String field) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(field + " must be a string"); return value.getAsString(); }
    private static int integer(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException(field + " must be an integer");
        try { return Math.toIntExact(Long.parseLong(value.getAsString())); } catch (NumberFormatException | ArithmeticException exception) { throw new IllegalArgumentException(field + " is outside integer range", exception); }
    }
    private static double decimal(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(field + " must be a number");
        try { double result = Double.parseDouble(value.getAsString()); if (!Double.isFinite(result)) throw new IllegalArgumentException(field + " must be finite"); return result; }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(field + " must be a finite number", exception); }
    }
}
