package io.github.chronicle.core.areas;

import java.util.Locale;

/** A content address plus enough metadata to reject implausible files before decoding them. */
public record SnapshotRef(String sha256, AreaBounds bounds, int cells, long encodedBytes) {
    public static final long MAX_ENCODED_BYTES = 64L * 1024 * 1024;
    public SnapshotRef {
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("snapshot SHA-256 must be 64 lowercase hexadecimal characters");
        sha256 = sha256.toLowerCase(Locale.ROOT);
        if (bounds == null) throw new IllegalArgumentException("snapshot bounds are required");
        if (cells < 1 || cells > AreaBounds.MAX_CELLS) throw new IllegalArgumentException("snapshot cell count is outside limits");
        if (cells != bounds.cells()) throw new IllegalArgumentException("snapshot cell count does not match bounds");
        if (encodedBytes < 1 || encodedBytes > MAX_ENCODED_BYTES) throw new IllegalArgumentException("snapshot payload exceeds 64 MiB");
    }
}
