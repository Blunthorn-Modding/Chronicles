package io.github.chronicle.core.offering;

/** Reusable lasting trade; campaign source and physical permission are supplied by the caller. */
public record OfferingDefinition(String id, OfferingTerms terms, int maxPurchases) {
    public OfferingDefinition {
        validId(id);
        if (terms == null) throw new IllegalArgumentException("offering terms required");
        terms.validatePurchase();
        if (maxPurchases < 1 || maxPurchases > 16) throw new IllegalArgumentException("offering purchase limit must be 1..16");
    }

    public static void validId(String id) {
        if (id == null || id.length() > 256 || !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("offering/source id must be a bounded lowercase namespaced id");
        }
    }
}
