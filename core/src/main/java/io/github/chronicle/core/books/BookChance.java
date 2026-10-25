package io.github.chronicle.core.books;

/** A named-book reference and its independent percentage chance. */
public record BookChance(String bookId, double chancePercent) {
    public BookChance {
        BookDeliveryState.requireBookId(bookId);
        if (!Double.isFinite(chancePercent) || chancePercent < 0.0 || chancePercent > 100.0) {
            throw new IllegalArgumentException("book chance must be finite and between 0 and 100 percent");
        }
    }
}
