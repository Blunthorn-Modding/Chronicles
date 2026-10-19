package io.github.chronicle.core.content;

/** Durable evidence of one completed fill. */
public record ChestReceipt(String operationId, String bindingId, int sessionNumber, String completedAt) {
    public ChestReceipt {
        ContentIds.requireOperationId(operationId);
        ContentIds.requireId(bindingId, "binding");
        if (sessionNumber < 0) throw new IllegalArgumentException("receipt session number cannot be negative");
        ContentIds.requireText(completedAt, "receipt completion time", 128);
    }
}
