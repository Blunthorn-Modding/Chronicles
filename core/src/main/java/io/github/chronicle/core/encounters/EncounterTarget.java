package io.github.chronicle.core.encounters;

import io.github.chronicle.core.authoring.AuthoringSite;

import java.util.Objects;

/** Exact map-owned Point or Region pin. Geometry remains in the authoring registry. */
public record EncounterTarget(Kind kind, String siteId, long siteRevision) {
    public enum Kind { POINT, REGION }

    public EncounterTarget {
        Objects.requireNonNull(kind, "encounter target kind is required");
        if (siteId == null || !AuthoringSite.ID.matcher(siteId).matches())
            throw new IllegalArgumentException("encounter target site ID is invalid");
        if (siteRevision < 1) throw new IllegalArgumentException("encounter target site revision must be positive");
    }
}
