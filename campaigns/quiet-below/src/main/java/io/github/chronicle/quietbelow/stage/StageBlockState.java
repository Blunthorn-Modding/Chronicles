package io.github.chronicle.quietbelow.stage;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Registry-stable block state representation; numeric runtime IDs are never persisted. */
public record StageBlockState(String id, Map<String, String> properties) {
    public StageBlockState {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.length() > 128) {
            throw new IllegalArgumentException("block state ID must be a namespaced identifier");
        }
        if (properties == null || properties.size() > 32) {
            throw new IllegalArgumentException("block state properties are required and bounded to 32");
        }
        TreeMap<String, String> copy = new TreeMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[a-z0-9_]+") || entry.getKey().length() > 64) {
                throw new IllegalArgumentException("invalid block state property name");
            }
            if (entry.getValue() == null || entry.getValue().isEmpty() || entry.getValue().length() > 64
                    || !entry.getValue().matches("[a-z0-9_.-]+")) {
                throw new IllegalArgumentException("invalid block state property value");
            }
            if (copy.put(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalArgumentException("duplicate block state property");
            }
        }
        properties = Collections.unmodifiableMap(copy);
    }

    public String display() {
        if (properties.isEmpty()) {
            return id;
        }
        return id + properties;
    }
}
