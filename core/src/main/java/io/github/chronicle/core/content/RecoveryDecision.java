package io.github.chronicle.core.content;

/** The only safe conclusions recovery can draw from an exact live inventory comparison. */
public enum RecoveryDecision {
    ABORT_UNCHANGED,
    COMMIT_APPLIED,
    CONFLICT;

    public static RecoveryDecision compare(PendingChestOperation operation, InventorySnapshot live) {
        if (operation.before().equals(live)) return ABORT_UNCHANGED;
        if (operation.after().equals(live)) return COMMIT_APPLIED;
        return CONFLICT;
    }
}
