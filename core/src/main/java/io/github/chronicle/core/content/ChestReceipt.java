package io.github.chronicle.core.content;

/** Durable evidence of one completed fill. Typed fills use a nonnegative generation cycle. */
public record ChestReceipt(String operationId, String bindingId, int sessionNumber, long cycle, String completedAt) {
    public ChestReceipt {
        ContentIds.requireOperationId(operationId);
        ContentIds.requireId(bindingId, "binding");
        if (sessionNumber < 0) throw new IllegalArgumentException("receipt session number cannot be negative");
        if (cycle < -1) throw new IllegalArgumentException("receipt cycle cannot be less than -1");
        ContentIds.requireText(completedAt, "receipt completion time", 128);
    }

    /** Source-compatible constructor for schema-1/template receipts. */
    public ChestReceipt(String operationId, String bindingId, int sessionNumber, String completedAt) {
        this(operationId, bindingId, sessionNumber, -1, completedAt);
    }
}
