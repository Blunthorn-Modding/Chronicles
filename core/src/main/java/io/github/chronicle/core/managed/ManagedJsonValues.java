package io.github.chronicle.core.managed;

import com.google.gson.*;
import io.github.chronicle.core.authoring.BlockPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class ManagedJsonValues {
    private ManagedJsonValues() { }
    static JsonElement required(JsonObject object, String field) {
        if (!object.has(field) || object.get(field).isJsonNull()) throw new IllegalArgumentException("missing " + field);
        return object.get(field);
    }
    static void exact(JsonObject object, Set<String> fields, String name) {
        if (!object.keySet().equals(fields)) {
            List<String> unknown = new ArrayList<>(object.keySet()); unknown.removeAll(fields);
            List<String> missing = new ArrayList<>(fields); missing.removeAll(object.keySet());
            throw new IllegalArgumentException(name + " fields do not match schema (unknown=" + unknown + ", missing=" + missing + ")");
        }
    }
    static String string(JsonElement value, String field) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(field + " must be a string");
        return value.getAsString();
    }
    static long longNumber(JsonElement value, String field) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(field + " must be an integer");
        String text = value.getAsString();
        if (!text.matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException(field + " must be an integer");
        try { return Long.parseLong(text); } catch (NumberFormatException exception) { throw new IllegalArgumentException(field + " outside long range", exception); }
    }
    static int integer(JsonElement value, String field) {
        long result = longNumber(value, field);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw new IllegalArgumentException(field + " outside integer range");
        return (int) result;
    }
    static JsonObject object(JsonElement value, String field) {
        if (!value.isJsonObject()) throw new IllegalArgumentException(field + " must be an object");
        return value.getAsJsonObject();
    }
    static JsonArray array(JsonElement value, String field) {
        if (!value.isJsonArray()) throw new IllegalArgumentException(field + " must be an array");
        return value.getAsJsonArray();
    }
    static BlockPosition position(JsonElement value, String field) {
        JsonObject object = object(value, field);
        exact(object, Set.of("x", "y", "z"), field);
        return new BlockPosition(integer(required(object, "x"), field + " x"), integer(required(object, "y"), field + " y"), integer(required(object, "z"), field + " z"));
    }
    static JsonObject position(BlockPosition value) {
        JsonObject object = new JsonObject(); object.addProperty("x", value.x()); object.addProperty("y", value.y()); object.addProperty("z", value.z()); return object;
    }
}
