package io.github.chronicle.core.spawn;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Immutable, revisioned automatic player-arrival and respawn policy state for one world. */
public record AutomaticSpawnState(long revision, Map<UUID, Policy> policies, Map<UUID, Arrival> arrivals) {
    public static final int MAX_POLICIES = 512;
    public static final int MAX_ARRIVALS = 4_096;

    public AutomaticSpawnState {
        if (revision < 0) throw new IllegalArgumentException("automatic spawn revision must not be negative");
        policies = immutable(policies, MAX_POLICIES, "policies");
        arrivals = immutable(arrivals, MAX_ARRIVALS, "arrivals");
    }

    public static AutomaticSpawnState empty() {
        return new AutomaticSpawnState(0, Map.of(), Map.of());
    }

    public record Policy(SpawnBinding arrival, SpawnBinding respawn) {
        public Policy {
            if (arrival == null && respawn == null) {
                throw new IllegalArgumentException("automatic spawn policy must enable arrival or respawn");
            }
        }
    }

    public record Arrival(String purpose, Status status, String detail) {
        public static final int MAX_DETAIL_LENGTH = 256;

        public Arrival {
            if (purpose == null || !SpawnBinding.ID.matcher(purpose).matches()) {
                throw new IllegalArgumentException("arrival purpose must match " + SpawnBinding.ID.pattern());
            }
            if (status == null) throw new IllegalArgumentException("arrival status is required");
            if (detail == null || detail.length() > MAX_DETAIL_LENGTH || detail.indexOf('\n') >= 0 || detail.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("arrival detail must be plain text of at most " + MAX_DETAIL_LENGTH + " characters without newlines");
            }
            for (int index = 0; index < detail.length(); index++) {
                if (Character.isISOControl(detail.charAt(index))) {
                    throw new IllegalArgumentException("arrival detail must be plain text");
                }
            }
        }
    }

    public enum Status { CLAIMED, MOVED, FAILED }

    private static <T> Map<UUID, T> immutable(Map<UUID, T> values, int maximum, String name) {
        if (values == null || values.size() > maximum) {
            throw new IllegalArgumentException(name + " may contain at most " + maximum + " entries");
        }
        TreeMap<UUID, T> ordered = new TreeMap<>();
        values.forEach((player, value) -> {
            if (player == null || value == null) throw new IllegalArgumentException(name + " must contain UUID keys and non-null values");
            ordered.put(player, value);
        });
        return Collections.unmodifiableMap(ordered);
    }
}
