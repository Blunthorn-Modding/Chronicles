package io.github.chronicle.core.entry;

import java.util.UUID;

/** Pinned named-book identity for one personal role entitlement. */
public record RoleBookGrant(String bookId, String deliveryToken) {
    public RoleBookGrant {
        if (bookId == null || bookId.length() > 160 || !bookId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("role book ID must be a namespaced identifier");
        }
        try {
            UUID parsed = UUID.fromString(deliveryToken);
            if (!parsed.toString().equals(deliveryToken)) throw new IllegalArgumentException("non-canonical UUID");
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("role book delivery token must be a canonical UUID", exception);
        }
    }
}
