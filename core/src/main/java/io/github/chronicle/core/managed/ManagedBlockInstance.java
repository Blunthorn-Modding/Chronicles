package io.github.chronicle.core.managed;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import java.util.Objects;
import java.util.UUID;

/** Durable active physical-instance claim. Historical campaign bindings live outside this model. */
public record ManagedBlockInstance(UUID id, String typeId, String blockId, String dimension,
                                   BlockPosition position, Registration registration, Origin origin,
                                   long revision) {
    public ManagedBlockInstance {
        Objects.requireNonNull(id, "managed instance ID is required");
        if (typeId == null || !AuthoringSite.NAMESPACED_ID.matcher(typeId).matches())
            throw new IllegalArgumentException("managed instance type must be namespaced");
        if (blockId == null || !AuthoringSite.NAMESPACED_ID.matcher(blockId).matches())
            throw new IllegalArgumentException("managed instance block must be namespaced");
        if (dimension == null || !AuthoringSite.NAMESPACED_ID.matcher(dimension).matches())
            throw new IllegalArgumentException("managed instance dimension must be namespaced");
        Objects.requireNonNull(position, "managed instance position is required");
        Objects.requireNonNull(registration, "managed instance registration is required");
        Objects.requireNonNull(origin, "managed instance origin is required");
        if (revision < 1) throw new IllegalArgumentException("managed instance revision must be positive");
    }

    public ManagedBlockInstance confirmed(long revision) {
        return new ManagedBlockInstance(id, typeId, blockId, dimension, position, Registration.CONFIRMED, origin, revision);
    }

    public enum Registration { RESERVED, CONFIRMED }
    public enum Origin { ITEM, LEGACY, DISCOVERED }
}
