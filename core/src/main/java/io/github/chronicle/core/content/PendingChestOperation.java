package io.github.chronicle.core.content;

import java.util.Objects;

/** Durable intent retained until the live inventory proves whether mutation completed. */
public record PendingChestOperation(String operationId, String actorUuid, String actorName,
                                    String bindingId, long bindingRevision, int sessionNumber,
                                    long cycle,
                                    ChestTarget target, InventorySnapshot before, InventorySnapshot after,
                                    String createdAt) {
    public PendingChestOperation {
        ContentIds.requireOperationId(operationId);
        ContentIds.requireUuid(actorUuid);
        ContentIds.requireText(actorName, "actor name", 64);
        ContentIds.requireId(bindingId, "binding");
        ContentIds.requireRevision(bindingRevision, "binding");
        if (sessionNumber < 0) throw new IllegalArgumentException("operation session number cannot be negative");
        if (cycle < -1) throw new IllegalArgumentException("operation cycle cannot be less than -1");
        Objects.requireNonNull(target, "operation target is required");
        Objects.requireNonNull(before, "operation before snapshot is required");
        Objects.requireNonNull(after, "operation after snapshot is required");
        if (before.size() != after.size()) throw new IllegalArgumentException("operation snapshots must have the same size");
        ContentIds.requireText(createdAt, "operation creation time", 128);
    }

    /** Source-compatible constructor for schema-1/template operations. */
    public PendingChestOperation(String operationId, String actorUuid, String actorName,
                                 String bindingId, long bindingRevision, int sessionNumber,
                                 ChestTarget target, InventorySnapshot before, InventorySnapshot after,
                                 String createdAt) {
        this(operationId, actorUuid, actorName, bindingId, bindingRevision, sessionNumber, -1,
                target, before, after, createdAt);
    }
}
