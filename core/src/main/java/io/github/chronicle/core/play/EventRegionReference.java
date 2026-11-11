package io.github.chronicle.core.play;

import io.github.chronicle.core.authoring.AuthoringSite;

/** An exact revision pin to one Core Region. */
public record EventRegionReference(String id, long revision) {
    public EventRegionReference {
        if (id == null || !AuthoringSite.ID.matcher(id).matches()) {
            throw new IllegalArgumentException("event Region ID is invalid");
        }
        if (revision < 1) throw new IllegalArgumentException("event Region revision must be positive");
    }
}
