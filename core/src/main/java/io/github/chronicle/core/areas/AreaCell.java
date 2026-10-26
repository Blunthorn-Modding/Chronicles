package io.github.chronicle.core.areas;

import java.util.Objects;

/** Exact native-adapter cell evidence. Block-entity SNBT is deliberately opaque to Core. */
public record AreaCell(String blockStateJson, String blockEntityNbt) {
    public static final int MAX_STATE_BYTES = 16 * 1024;
    public static final int MAX_BLOCK_ENTITY_BYTES = 1024 * 1024;
    public AreaCell {
        Objects.requireNonNull(blockStateJson, "block state JSON is required");
        AreaSnapshotCodec.requireUtf8(blockStateJson, 1, MAX_STATE_BYTES, "block state JSON");
        if (blockEntityNbt != null) AreaSnapshotCodec.requireUtf8(blockEntityNbt, 1, MAX_BLOCK_ENTITY_BYTES, "block entity NBT");
    }
}
