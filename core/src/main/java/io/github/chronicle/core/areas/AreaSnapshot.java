package io.github.chronicle.core.areas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable dense palette snapshot. Native adapters own registry/NBT type validation. */
public final class AreaSnapshot {
    public static final int MAX_PALETTE = 65_536;
    public static final int MAX_BLOCK_ENTITIES = 65_536;
    public static final long MAX_DECODED_BYTES = 64L * 1024 * 1024;
    private final AreaBounds bounds;
    private final List<String> palette;
    private final int[] paletteIndices;
    private final Map<Integer, String> blockEntities;

    public AreaSnapshot(AreaBounds bounds, List<String> palette, int[] paletteIndices, Map<Integer, String> blockEntities) {
        this.bounds = Objects.requireNonNull(bounds, "bounds are required");
        Objects.requireNonNull(palette, "palette is required");
        Objects.requireNonNull(paletteIndices, "palette indices are required");
        Objects.requireNonNull(blockEntities, "block entities are required");
        if (palette.isEmpty() || palette.size() > MAX_PALETTE) throw new IllegalArgumentException("palette size is outside limits");
        long decodedBytes = (long) bounds.cells() * Integer.BYTES;
        ArrayList<String> checkedPalette = new ArrayList<>(palette.size());
        for (String state : palette) {
            Objects.requireNonNull(state, "palette state is required");
            AreaSnapshotCodec.requireCanonicalJsonObject(state, AreaCell.MAX_STATE_BYTES, "block state JSON");
            decodedBytes = Math.addExact(decodedBytes, AreaSnapshotCodec.requireUtf8(state, 1, AreaCell.MAX_STATE_BYTES, "block state JSON"));
            if (decodedBytes > MAX_DECODED_BYTES) throw new IllegalArgumentException("decoded snapshot exceeds 64 MiB");
            checkedPalette.add(state);
        }
        if (paletteIndices.length != bounds.cells()) throw new IllegalArgumentException("dense palette indices must match bounds volume");
        this.paletteIndices = paletteIndices.clone();
        for (int index : this.paletteIndices) if (index < 0 || index >= palette.size()) throw new IllegalArgumentException("palette index is outside palette");
        if (blockEntities.size() > MAX_BLOCK_ENTITIES || blockEntities.size() > bounds.cells()) throw new IllegalArgumentException("too many block entities");
        LinkedHashMap<Integer, String> checkedEntities = new LinkedHashMap<>();
        for (Map.Entry<Integer,String> entry : blockEntities.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (entry.getKey() == null || entry.getKey() < 0 || entry.getKey() >= bounds.cells()) throw new IllegalArgumentException("block entity cell is outside bounds");
            String nbt = Objects.requireNonNull(entry.getValue(), "block entity NBT is required");
            decodedBytes = Math.addExact(decodedBytes, Integer.BYTES + AreaSnapshotCodec.requireUtf8(nbt, 1, AreaCell.MAX_BLOCK_ENTITY_BYTES, "block entity NBT"));
            if (decodedBytes > MAX_DECODED_BYTES) throw new IllegalArgumentException("decoded snapshot exceeds 64 MiB");
            checkedEntities.put(entry.getKey(), nbt);
        }
        if (decodedBytes > MAX_DECODED_BYTES) throw new IllegalArgumentException("decoded snapshot exceeds 64 MiB");
        this.palette = List.copyOf(checkedPalette);
        this.blockEntities = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(checkedEntities));
    }

    public AreaBounds bounds() { return bounds; }
    public List<String> palette() { return palette; }
    public int[] paletteIndices() { return paletteIndices.clone(); }
    public Map<Integer, String> blockEntities() { return blockEntities; }
    public int cells() { return paletteIndices.length; }
    public AreaCell cell(int index) {
        if (index < 0 || index >= cells()) throw new IndexOutOfBoundsException(index);
        return new AreaCell(palette.get(paletteIndices[index]), blockEntities.get(index));
    }

    @Override public boolean equals(Object other) {
        return other instanceof AreaSnapshot value && bounds.equals(value.bounds) && palette.equals(value.palette)
                && Arrays.equals(paletteIndices, value.paletteIndices) && blockEntities.equals(value.blockEntities);
    }
    @Override public int hashCode() { return 31 * Objects.hash(bounds, palette, blockEntities) + Arrays.hashCode(paletteIndices); }
}
