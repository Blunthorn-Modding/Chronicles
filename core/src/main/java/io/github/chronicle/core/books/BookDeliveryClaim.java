package io.github.chronicle.core.books;

/** A durable pending-operation claim for one managed book token. */
public record BookDeliveryClaim(String bookId, String deliveryToken, boolean keepRequest) {
    public BookDeliveryClaim {
        BookDeliveryState.requireBookId(bookId);
        BookDelivery.requireUuid(deliveryToken, "delivery token");
    }
}
