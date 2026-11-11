package io.github.chronicle.quietbelow.stage;

import io.github.chronicle.core.campaign.Actor;

import java.util.List;
import java.util.UUID;

/** Durable intent and checkpoint for the one latest operation supported by the pilot. */
public record StageOperation(String operationId, String zoneId, String dimension, List<StageEntry> entries,
                             Actor actor, String startedAt, StageDirection direction,
                             StageOperationStatus status, int progress, String detail) {
    public StageOperation {
        try {
            UUID.fromString(operationId);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("operation ID must be a UUID", exception);
        }
        StageValidation.zoneId(zoneId);
        StageValidation.dimension(dimension);
        if (entries == null || entries.isEmpty() || entries.size() > StageValidation.MAX_POSITIONS) {
            throw new IllegalArgumentException("operation entries are required and bounded");
        }
        entries = List.copyOf(entries);
        StageValidation.stageEntries(entries);
        if (actor == null || direction == null || status == null) {
            throw new IllegalArgumentException("operation actor, direction and status are required");
        }
        StageValidation.timestamp(startedAt);
        if (progress < 0 || progress > entries.size()) {
            throw new IllegalArgumentException("operation progress is outside its entry count");
        }
        if ((status == StageOperationStatus.APPLIED || status == StageOperationStatus.UNDONE) && progress != entries.size()) {
            throw new IllegalArgumentException("completed operation must include every entry");
        }
        if ((status == StageOperationStatus.APPLIED || status == StageOperationStatus.APPLYING) && direction != StageDirection.APPLY) {
            throw new IllegalArgumentException("apply status requires apply direction");
        }
        if ((status == StageOperationStatus.UNDONE || status == StageOperationStatus.UNDOING) && direction != StageDirection.UNDO) {
            throw new IllegalArgumentException("undo status requires undo direction");
        }
        if (detail == null || detail.length() > 512 || detail.codePoints().anyMatch(c -> c < 0x20)) {
            throw new IllegalArgumentException("operation detail is invalid");
        }
    }

    public StageOperation checkpoint(StageOperationStatus nextStatus, int nextProgress, String nextDetail) {
        return new StageOperation(operationId, zoneId, dimension, entries, actor, startedAt,
                direction, nextStatus, nextProgress, nextDetail);
    }

    public StageEntry entryAtProgress(int offset) {
        int index = direction == StageDirection.APPLY ? offset : entries.size() - 1 - offset;
        return entries.get(index);
    }
}
