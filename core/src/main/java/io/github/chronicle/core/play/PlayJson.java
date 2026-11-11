package io.github.chronicle.core.play;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Package-private strict JSON helpers shared by the UI payload and durable store. */
final class PlayJson {
    private static final int MAX_DEPTH = 16;

    private PlayJson() { }

    static JsonObject object(String source, String name) {
        validate(source, name);
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement value = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException(name + " has trailing JSON content");
            return object(value, name);
        } catch (IOException exception) {
            throw new IllegalArgumentException("unable to read " + name, exception);
        }
    }

    static JsonObject object(JsonElement value, String name) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(name + " must be an object");
        return value.getAsJsonObject();
    }

    static JsonArray array(JsonElement value, String name) {
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(name + " must be an array");
        return value.getAsJsonArray();
    }

    static JsonElement required(JsonObject object, String field) {
        if (!object.has(field) || object.get(field).isJsonNull()) throw new IllegalArgumentException("missing " + field);
        return object.get(field);
    }

    static void fields(JsonObject object, Set<String> allowed, String name) {
        if (!object.keySet().equals(allowed)) throw new IllegalArgumentException(name + " fields do not match schema");
    }

    static String string(JsonElement value, String name) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(name + " must be a string");
        return value.getAsString();
    }

    static long number(JsonElement value, String name) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        try { return Long.parseLong(value.getAsString()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " is outside the long range", exception); }
    }

    static int integer(JsonElement value, String name) {
        long result = number(value, name);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw new IllegalArgumentException(name + " is outside the integer range");
        return (int) result;
    }

    static UUID uuid(JsonElement value, String name) {
        String text = string(value, name);
        try {
            UUID result = UUID.fromString(text);
            if (!result.toString().equals(text)) throw new IllegalArgumentException("non-canonical UUID");
            return result;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " must be a canonical UUID", exception);
        }
    }

    static <E extends Enum<E>> E enumeration(JsonElement value, Class<E> type, String name) {
        String text = string(value, name);
        try { return Enum.valueOf(type, text); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("unknown " + name + ": " + text, exception); }
    }

    private static void validate(String source, String name) {
        if (source == null) throw new IllegalArgumentException(name + " is required");
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            validateValue(reader, 0, name);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException(name + " has trailing JSON content");
        } catch (IOException exception) {
            throw new IllegalArgumentException("unable to read " + name, exception);
        }
    }

    private static void validateValue(JsonReader reader, int depth, String name) throws IOException {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException(name + " nesting exceeds " + MAX_DEPTH);
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> members = new HashSet<>();
                while (reader.hasNext()) {
                    String member = reader.nextName();
                    if (!members.add(member)) throw new IllegalArgumentException(name + " contains duplicate member: " + member);
                    validateValue(reader, depth + 1, name);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) validateValue(reader, depth + 1, name);
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException("unexpected token in " + name + ": " + reader.peek());
        }
    }
}
