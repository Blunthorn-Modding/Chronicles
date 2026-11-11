package io.github.chronicle.quietbelow.stage;

import io.github.chronicle.core.campaign.Actor;

import java.util.List;

/** Immutable capture of exact approved coordinates and their original states. */
public record StageZone(String id, String dimension, List<StageEntry> entries,
                        Actor definedBy, String definedAt) {
    public StageZone {
        StageValidation.zoneId(id);
        StageValidation.dimension(dimension);
        if (entries == null || entries.isEmpty() || entries.size() > StageValidation.MAX_POSITIONS) {
            throw new IllegalArgumentException("zone must contain between 1 and " + StageValidation.MAX_POSITIONS + " positions");
        }
        entries = List.copyOf(entries);
        StageValidation.stageEntries(entries);
        if (entries.stream().map(StageEntry::position).distinct().count() != entries.size()) {
            throw new IllegalArgumentException("zone may not contain duplicate positions");
        }
        if (definedBy == null) {
            throw new IllegalArgumentException("zone actor is required");
        }
        StageValidation.timestamp(definedAt);
    }
}
