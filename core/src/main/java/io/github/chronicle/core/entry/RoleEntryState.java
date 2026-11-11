package io.github.chronicle.core.entry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Bounded per-account first-entry operation evidence. */
public record RoleEntryState(long revision, Map<UUID, RoleEntryOperation> operations) {
    public static final int MAX_OPERATIONS = 512;

    public RoleEntryState {
        if (revision < 0) throw new IllegalArgumentException("role-entry revision must not be negative");
        if (operations == null || operations.size() > MAX_OPERATIONS) {
            throw new IllegalArgumentException("role-entry operations may contain at most " + MAX_OPERATIONS + " accounts");
        }
        LinkedHashMap<UUID, RoleEntryOperation> ordered = new LinkedHashMap<>();
        for (Map.Entry<UUID, RoleEntryOperation> entry : new TreeMap<>(operations).entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || !entry.getKey().equals(entry.getValue().playerId())) {
                throw new IllegalArgumentException("role-entry operation key must match its player");
            }
            ordered.put(entry.getKey(), entry.getValue());
        }
        operations = Collections.unmodifiableMap(ordered);
    }

    public static RoleEntryState empty() { return new RoleEntryState(0, Map.of()); }
}
