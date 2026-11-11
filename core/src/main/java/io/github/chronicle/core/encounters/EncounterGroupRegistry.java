package io.github.chronicle.core.encounters;

import java.util.*;

public record EncounterGroupRegistry(long revision, Map<String, EncounterGroup> groups) {
    public static final int MAX_GROUPS = 256;
    public EncounterGroupRegistry {
        if (revision < 0) throw new IllegalArgumentException("encounter registry revision must be non-negative");
        if (groups == null || groups.size() > MAX_GROUPS) throw new IllegalArgumentException("too many encounter groups");
        TreeMap<String, EncounterGroup> copy = new TreeMap<>();
        for (var entry : groups.entrySet()) { if (entry.getKey() == null || entry.getValue() == null || !entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("encounter registry entry is invalid"); if (copy.putIfAbsent(entry.getKey(), entry.getValue()) != null) throw new IllegalArgumentException("duplicate encounter group ID"); }
        groups = Collections.unmodifiableMap(copy);
    }
    public static EncounterGroupRegistry empty() { return new EncounterGroupRegistry(0, Map.of()); }
    public EncounterGroupRegistry replace(EncounterGroup group, EncounterGroup expected) {
        Objects.requireNonNull(group, "encounter group is required"); if (expected != null && (!group.id().equals(expected.id()) || !expected.equals(groups.get(expected.id())))) throw new IllegalStateException("encounter group changed since this editor opened"); if (expected == null && groups.containsKey(group.id())) throw new IllegalArgumentException("encounter group already exists");
        TreeMap<String, EncounterGroup> next = new TreeMap<>(groups); next.put(group.id(), group); return new EncounterGroupRegistry(Math.addExact(revision, 1), next);
    }
    public EncounterGroupRegistry remove(String id, EncounterGroup expected) {
        if (expected == null || !Objects.equals(groups.get(id), expected)) throw new IllegalStateException("encounter group changed since this editor opened");
        TreeMap<String, EncounterGroup> next = new TreeMap<>(groups); next.remove(id); return new EncounterGroupRegistry(Math.addExact(revision, 1), next);
    }
}
