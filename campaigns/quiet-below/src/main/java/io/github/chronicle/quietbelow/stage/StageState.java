package io.github.chronicle.quietbelow.stage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Complete bounded state persisted by the campaign stage service. */
public record StageState(int schemaVersion, Map<String, StageZone> zones,
                         StageOperation operation, List<StageAuditEntry> history, long nextSequence) {
    public static final int SCHEMA_VERSION = 1;

    public StageState {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported stage schema version: " + schemaVersion);
        }
        if (zones == null || zones.size() > StageValidation.MAX_ZONES) {
            throw new IllegalArgumentException("stage zones are required and bounded to " + StageValidation.MAX_ZONES);
        }
        LinkedHashMap<String, StageZone> copy = new LinkedHashMap<>();
        zones.forEach((key, value) -> {
            StageValidation.zoneId(key);
            if (value == null || !key.equals(value.id())) {
                throw new IllegalArgumentException("zone map key must match its zone ID");
            }
            copy.put(key, value);
        });
        zones = Collections.unmodifiableMap(copy);
        if (history == null || history.size() > StageValidation.MAX_HISTORY) {
            throw new IllegalArgumentException("stage audit history is required and bounded");
        }
        history = List.copyOf(history);
        if (nextSequence < 0 || (history.isEmpty() && nextSequence != 0)
                || (!history.isEmpty() && history.getLast().sequence() != nextSequence - 1)) {
            throw new IllegalArgumentException("stage audit sequence is invalid");
        }
        for (int index = 1; index < history.size(); index++) {
            if (history.get(index).sequence() != history.get(index - 1).sequence() + 1) {
                throw new IllegalArgumentException("stage audit history must have increasing consecutive sequences");
            }
        }
        if (operation != null) {
            StageZone referenced = zones.get(operation.zoneId());
            if (referenced == null || !referenced.dimension().equals(operation.dimension())
                    || !referenced.entries().equals(operation.entries())) {
                throw new IllegalArgumentException("stage operation must exactly match its stored zone capture");
            }
        }
    }

    public static StageState newWorld() {
        return new StageState(SCHEMA_VERSION, Map.of(), null, List.of(), 0);
    }
}
