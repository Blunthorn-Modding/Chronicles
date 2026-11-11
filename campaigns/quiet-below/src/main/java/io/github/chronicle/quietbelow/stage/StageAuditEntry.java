package io.github.chronicle.quietbelow.stage;

import io.github.chronicle.core.campaign.Actor;

/** Bounded campaign-local administrative audit. */
public record StageAuditEntry(long sequence, String timestamp, Actor actor, String action,
                              String target, String operationId, String detail) {
    public StageAuditEntry {
        if (sequence < 0 || actor == null) {
            throw new IllegalArgumentException("audit sequence and actor are required");
        }
        StageValidation.timestamp(timestamp);
        if (action == null || !action.matches("[a-z.]{1,64}")) {
            throw new IllegalArgumentException("invalid audit action");
        }
        if (target == null || target.isEmpty() || target.length() > 128
                || operationId == null || operationId.length() > 64
                || detail == null || detail.length() > 512
                || target.codePoints().anyMatch(c -> c < 0x20)
                || operationId.codePoints().anyMatch(c -> c < 0x20)
                || detail.codePoints().anyMatch(c -> c < 0x20)) {
            throw new IllegalArgumentException("invalid audit target, operation or detail");
        }
    }
}
