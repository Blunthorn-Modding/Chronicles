package io.github.chronicle.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/** Slot-aligned, registry-aware ItemStack codec payloads. */
public record InventorySnapshot(int size, List<String> stacks) {
    public static final int MAX_SLOTS = 54;
    public static final int MAX_STACK_JSON_CHARS = 65_536;

    public InventorySnapshot {
        if (size < 1 || size > MAX_SLOTS) {
            throw new IllegalArgumentException("inventory size must be between 1 and " + MAX_SLOTS);
        }
        Objects.requireNonNull(stacks, "inventory stacks are required");
        stacks = List.copyOf(stacks);
        if (stacks.size() != size) {
            throw new IllegalArgumentException("inventory snapshot must contain exactly " + size + " slots");
        }
        ArrayList<String> canonical = new ArrayList<>(size);
        for (String stack : stacks) {
            if (stack == null || stack.isBlank() || stack.length() > MAX_STACK_JSON_CHARS) {
                throw new IllegalArgumentException("inventory stack payload must be non-blank and at most " + MAX_STACK_JSON_CHARS + " characters");
            }
            try { canonical.add(canonical(JsonParser.parseString(stack)).toString()); }
            catch (RuntimeException exception) { throw new IllegalArgumentException("inventory stack payload must be valid JSON", exception); }
        }
        stacks = List.copyOf(canonical);
    }

    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            new TreeMap<>(value.getAsJsonObject().asMap()).forEach((key, member) -> sorted.add(key, canonical(member)));
            return sorted;
        }
        if (value.isJsonArray()) {
            JsonArray ordered = new JsonArray();
            value.getAsJsonArray().forEach(member -> ordered.add(canonical(member)));
            return ordered;
        }
        return value.deepCopy();
    }
}
