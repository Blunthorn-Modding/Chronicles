package io.github.chronicle.core.books;

import java.util.Objects;

/** One bounded, table-scoped keep-placed request for a named book. */
public record BookKeepRequest(String bookId, String lootTableId, String ruleFingerprint,
                              BookDefinition.Rule approvedRule, boolean overrideRule, boolean ownIssuance,
                              Status status, BookCopyLocation location, int lastSessionNumber, long lastCycle,
                              String requestedAt, String statusAt) {
    public enum Status { ACTIVE, SUSPENDED_RULE, SUSPENDED_ONCE, COMPLETED_FOUND, CANCELLED }

    public BookKeepRequest {
        BookDeliveryState.requireBookId(bookId);
        BookDeliveryState.requireBookId(lootTableId);
        BookDeliveryState.requireText(ruleFingerprint, "rule fingerprint", 128);
        Objects.requireNonNull(approvedRule, "approved book rule is required");
        Objects.requireNonNull(status, "keep request status is required");
        if ((lastSessionNumber == -1) != (lastCycle == -1) || lastSessionNumber < -1 || lastCycle < -1) {
            throw new IllegalArgumentException("keep reconciliation session and cycle must both be -1 or nonnegative");
        }
        BookDeliveryState.requireText(requestedAt, "keep request time", 128);
        BookDeliveryState.requireText(statusAt, "keep status time", 128);
    }

    public boolean active() { return status == Status.ACTIVE; }
}
