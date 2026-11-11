package io.github.chronicle.core.loot;

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

/** Shared strict JSON parsing for the bounded loot configuration payloads. */
final class LootJson {
    static final int MAX_DEPTH = 16;

    private LootJson() { }

    static JsonElement parse(String source, String field) {
        if (source == null) throw new IllegalArgumentException(field + " is required");
        validate(source, field);
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement value = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException(field + " has trailing JSON content");
            return value;
        } catch (IOException exception) {
            throw new IllegalArgumentException("unable to read " + field, exception);
        }
    }

    static JsonObject object(String source, String field) {
        JsonElement value = parse(source, field);
        if (!value.isJsonObject()) throw new IllegalArgumentException(field + " must be a JSON object");
        return value.getAsJsonObject();
    }

    private static void validate(String source, String field) {
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            validateValue(reader, 0, field);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException(field + " has trailing JSON content");
        } catch (IOException exception) {
            throw new IllegalArgumentException("unable to read " + field, exception);
        }
    }

    private static void validateValue(JsonReader reader, int depth, String field) throws IOException {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException(field + " nesting exceeds " + MAX_DEPTH);
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) throw new IllegalArgumentException(field + " contains duplicate member: " + name);
                    validateValue(reader, depth + 1, field);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) validateValue(reader, depth + 1, field);
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException("unexpected token in " + field + ": " + reader.peek());
        }
    }
}
