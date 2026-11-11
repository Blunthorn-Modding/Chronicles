package io.github.chronicle.core.spawn;

import java.util.Objects;

/** A named, revision-pinned arrival or respawn destination. Binding it has no world effect. */
public record SpawnBinding(String purpose, String locationId, long locationRevision) {
    /** Accept legacy plain names and catalog-style namespace:name purposes. */
    public static final java.util.regex.Pattern ID = io.github.chronicle.core.authoring.AuthoringSite.ID;

    public SpawnBinding {
        if (purpose == null || !ID.matcher(purpose).matches()) throw new IllegalArgumentException("spawn purpose must match " + ID.pattern());
        if (locationId == null || !ID.matcher(locationId).matches()) throw new IllegalArgumentException("location ID must match " + ID.pattern());
        if (locationRevision < 1) throw new IllegalArgumentException("location revision must be positive");
    }
}
