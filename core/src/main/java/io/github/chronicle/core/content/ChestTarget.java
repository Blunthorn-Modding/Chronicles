package io.github.chronicle.core.content;

/** Immutable recovery address copied from the anchor before an operation starts. */
public record ChestTarget(String anchorId, long anchorRevision, String dimension,
                          int x, int y, int z, String expectedBlock) {
    public ChestTarget {
        ContentIds.requireId(anchorId, "anchor");
        ContentIds.requireRevision(anchorRevision, "anchor");
        ContentIds.requireNamespacedId(dimension, "dimension");
        ContentIds.requireNamespacedId(expectedBlock, "expected block");
    }
}
