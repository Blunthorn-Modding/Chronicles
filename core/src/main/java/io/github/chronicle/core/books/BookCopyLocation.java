package io.github.chronicle.core.books;

import io.github.chronicle.core.content.ChestTarget;

import java.util.Objects;

/** Last authoritative location evidence for a keep-placed copy. */
public record BookCopyLocation(String bindingId, long bindingRevision, ChestTarget target, State state) {
    public enum State { PRESENT, UNRESOLVED, ABSENT }

    public BookCopyLocation {
        BookDeliveryState.requireSimpleId(bindingId, "location binding");
        if (bindingRevision < 1) throw new IllegalArgumentException("location binding revision must be positive");
        Objects.requireNonNull(target, "copy target is required");
        Objects.requireNonNull(state, "copy location state is required");
        if (!bindingId.equals(target.anchorId()) || bindingRevision != target.anchorRevision()) {
            throw new IllegalArgumentException("copy target must pin its binding ID and revision");
        }
    }
}
