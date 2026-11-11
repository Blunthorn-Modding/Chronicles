package io.github.chronicle.core.encounters;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** World-owned bindings kept separate from portable encounter compositions. */
public record EncounterBindingRegistry(long revision, Map<String, EncounterBinding> bindings) {
    public EncounterBindingRegistry {
        if (revision < 0) throw new IllegalArgumentException("encounter binding revision must be non-negative");
        if (bindings == null || bindings.size() > EncounterGroupRegistry.MAX_GROUPS)
            throw new IllegalArgumentException("too many encounter bindings");
        TreeMap<String, EncounterBinding> copy = new TreeMap<>();
        for (var entry : bindings.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || !entry.getKey().equals(entry.getValue().groupId()))
                throw new IllegalArgumentException("encounter binding entry is invalid");
            if (copy.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                throw new IllegalArgumentException("duplicate encounter binding");
        }
        bindings = Collections.unmodifiableMap(copy);
    }

    public static EncounterBindingRegistry empty() { return new EncounterBindingRegistry(0, Map.of()); }

    public EncounterBindingRegistry replace(EncounterBinding replacement, EncounterBinding expected) {
        Objects.requireNonNull(replacement, "encounter binding is required");
        EncounterBinding current = bindings.get(replacement.groupId());
        if (!Objects.equals(current, expected)) throw new IllegalStateException("encounter binding changed since this editor opened");
        TreeMap<String, EncounterBinding> next = new TreeMap<>(bindings);
        next.put(replacement.groupId(), replacement);
        return new EncounterBindingRegistry(Math.addExact(revision, 1), next);
    }

    public EncounterBindingRegistry remove(String groupId, EncounterBinding expected) {
        if (!Objects.equals(bindings.get(groupId), expected)) throw new IllegalStateException("encounter binding changed since this editor opened");
        TreeMap<String, EncounterBinding> next = new TreeMap<>(bindings);
        next.remove(groupId);
        return new EncounterBindingRegistry(Math.addExact(revision, 1), next);
    }
}
