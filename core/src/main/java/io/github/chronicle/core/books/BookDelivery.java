package io.github.chronicle.core.books;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Campaign-wide issuance and first-discovery history for one named book. */
public record BookDelivery(String bookId, String deliveryToken, boolean issued,
                           Set<String> pendingOperationIds, BookDiscovery firstDiscovery) {
    public BookDelivery {
        BookDeliveryState.requireBookId(bookId);
        requireUuid(deliveryToken, "delivery token");
        if (pendingOperationIds == null || pendingOperationIds.size() > BookDeliveryState.MAX_PENDING_PER_BOOK) {
            throw new IllegalArgumentException("pending book operations may contain at most " + BookDeliveryState.MAX_PENDING_PER_BOOK + " entries");
        }
        LinkedHashSet<String> pending = new LinkedHashSet<>();
        for (String operationId : pendingOperationIds) {
            requireUuid(operationId, "pending operation ID");
            if (!pending.add(operationId)) throw new IllegalArgumentException("pending book operation IDs must be unique");
        }
        pendingOperationIds = Collections.unmodifiableSet(pending);
        if (firstDiscovery != null && !issued) throw new IllegalArgumentException("a discovered book must have a completed issuance");
    }

    static void requireUuid(String value, String field) {
        try { UUID.fromString(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException(field + " must be a UUID", exception); }
    }
}
