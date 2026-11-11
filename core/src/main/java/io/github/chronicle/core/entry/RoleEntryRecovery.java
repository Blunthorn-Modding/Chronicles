package io.github.chronicle.core.entry;

import io.github.chronicle.core.content.InventorySnapshot;

/** Pure conservative classifier for restart recovery; it never infers an uncertain side effect. */
public final class RoleEntryRecovery {
    private RoleEntryRecovery() { }
    public enum Decision { ABORT_BEFORE_EFFECTS, FINISH_EXACT_POST_STATE, CONFLICT }

    public static Decision inspect(RoleEntryOperation operation, InventorySnapshot inventory, PlayerPosition position) {
        if (operation == null || inventory == null || position == null) throw new IllegalArgumentException("operation and live evidence are required");
        boolean before = operation.beforeInventory().equals(inventory) && operation.beforePosition().exactly(position);
        boolean after = operation.afterInventory().equals(inventory) && operation.afterPosition().exactly(position);
        return switch (operation.stage()) {
            case PREPARED -> before ? Decision.ABORT_BEFORE_EFFECTS : Decision.CONFLICT;
            case APPLYING -> after ? Decision.FINISH_EXACT_POST_STATE : Decision.CONFLICT;
            case COMPLETE, ABORTED, CONFLICT -> Decision.CONFLICT;
        };
    }
}
