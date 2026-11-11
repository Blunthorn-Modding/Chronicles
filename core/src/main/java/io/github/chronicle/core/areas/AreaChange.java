package io.github.chronicle.core.areas;

import io.github.chronicle.core.authoring.AuthoringSite;

import java.util.Optional;

/** One revision-pinned Core Region and its independent Start/End capture references. */
public record AreaChange(String id, String name, AreaBounds bounds, long revision,
                         Optional<SnapshotRef> start, Optional<SnapshotRef> end) {
    public static final int MAX_NAME_BYTES = 256;
    public AreaChange {
        if (id == null || !AuthoringSite.ID.matcher(id).matches()) throw new IllegalArgumentException("area ID is invalid");
        AreaSnapshotCodec.requireUtf8(name, 1, MAX_NAME_BYTES, "area name");
        if (bounds == null) throw new IllegalArgumentException("area bounds are required");
        if (revision < 1) throw new IllegalArgumentException("area revision must be positive");
        start = start == null ? Optional.empty() : start;
        end = end == null ? Optional.empty() : end;
        start.ifPresent(ref -> requireBounds(ref, bounds)); end.ifPresent(ref -> requireBounds(ref, bounds));
    }
    public Optional<SnapshotRef> snapshot(AreaState state) { return state == AreaState.START ? start : end; }
    public SnapshotRef requireSnapshot(AreaState state) { return snapshot(state).orElseThrow(() -> new IllegalStateException(state + " snapshot is missing")); }
    public AreaChange replace(AreaState state, SnapshotRef ref) {
        requireBounds(ref, bounds); return state == AreaState.START
                ? new AreaChange(id,name,bounds,revision+1,Optional.of(ref),end)
                : new AreaChange(id,name,bounds,revision+1,start,Optional.of(ref));
    }
    private static void requireBounds(SnapshotRef ref,AreaBounds bounds){if(ref==null||!ref.bounds().equals(bounds))throw new IllegalArgumentException("snapshot reference does not match area bounds");}
}
