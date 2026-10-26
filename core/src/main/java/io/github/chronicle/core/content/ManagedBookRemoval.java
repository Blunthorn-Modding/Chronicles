package io.github.chronicle.core.content;

/** Managed-book provenance observed in the native target-before snapshot. */
public record ManagedBookRemoval(String bookId, String deliveryToken) {
    public ManagedBookRemoval {
        ContentIds.requireNamespacedId(bookId, "managed book");
        ContentIds.requireOperationId(deliveryToken);
    }
}
