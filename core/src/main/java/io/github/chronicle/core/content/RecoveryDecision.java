package io.github.chronicle.core.content;

/** The only safe conclusions recovery can draw from an exact live inventory comparison. */
public enum RecoveryDecision {
    ABORT_UNCHANGED,
    COMMIT_APPLIED,
    CONFLICT;

    public static RecoveryDecision compare(PendingChestOperation operation, InventorySnapshot live) {
        if (operation.purpose() == PendingChestOperation.Purpose.MOVE) {
            throw new IllegalArgumentException("MOVE recovery requires both source and destination inventories");
        }
        if (operation.before().equals(live)) return ABORT_UNCHANGED;
        if (operation.after().equals(live)) return COMMIT_APPLIED;
        return CONFLICT;
    }

    /** A transfer is recoverable only when both inventories prove the same side of the intent. */
    public static RecoveryDecision compare(PendingChestOperation operation, InventorySnapshot liveSource,
                                           InventorySnapshot liveDestination) {
        if (operation.purpose() != PendingChestOperation.Purpose.MOVE) {
            throw new IllegalArgumentException("two-inventory recovery is only valid for MOVE operations");
        }
        if (operation.sourceBefore().equals(liveSource) && operation.before().equals(liveDestination)) return ABORT_UNCHANGED;
        if (operation.sourceAfter().equals(liveSource) && operation.after().equals(liveDestination)) return COMMIT_APPLIED;
        return CONFLICT;
    }
}
