package io.github.chronicle.core.authoring;

import java.util.Objects;

/**
 * A transient, per-author edit of one region.  It deliberately carries the saved revision that
 * it was opened from so a later save cannot overwrite a changed region by accident.
 */
public record RegionDraft(String id, String dimension, long baseRevision,
                          BlockPosition first, BlockPosition second) {
    public RegionDraft {
        if (id == null || !AuthoringSite.ID.matcher(id).matches()) {
            throw new IllegalArgumentException("region draft ID must match " + AuthoringSite.ID.pattern());
        }
        if (dimension == null || !AuthoringSite.NAMESPACED_ID.matcher(dimension).matches()) {
            throw new IllegalArgumentException("region draft dimension must be a namespaced registry ID");
        }
        if (baseRevision < 0) throw new IllegalArgumentException("region draft revision cannot be negative");
    }

    /** Starts a new unsaved region in the author's current dimension. */
    public static RegionDraft create(String id, String dimension) {
        return new RegionDraft(id, dimension, 0, null, null);
    }

    /** Loads both exact saved corners into an edit draft. */
    public static RegionDraft edit(AuthoringSite.Region region) {
        Objects.requireNonNull(region, "region is required");
        return new RegionDraft(region.id(), region.dimension(), region.revision(), region.min(), region.max());
    }

    public boolean existing() { return baseRevision > 0; }

    public RegionDraft withCorner(int corner, BlockPosition position) {
        Objects.requireNonNull(position, "region corner is required");
        return switch (corner) {
            case 1 -> new RegionDraft(id, dimension, baseRevision, position, second);
            case 2 -> new RegionDraft(id, dimension, baseRevision, first, position);
            default -> throw new IllegalArgumentException("region corner must be 1 or 2");
        };
    }

    public boolean complete() { return first != null && second != null; }

    /** Creates the normalized persisted representation once both selected corners are present. */
    public AuthoringSite.Region region() {
        if (!complete()) throw new IllegalStateException("Select both region corners first.");
        return new AuthoringSite.Region(id, dimension, 1, first, second);
    }
}
