package io.github.chronicle.core.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImmutableHistoryArchiveTest {
    @TempDir Path directory;

    @Test void exactRetryIsIdempotentAndConflictingIdentityIsRefused() throws Exception {
        var archive = new ImmutableHistoryArchive(directory.resolve("history"), "test-record", 1024);
        archive.publish("source:one", new byte[]{1,2,3});
        archive.publish("source:one", new byte[]{1,2,3});
        assertArrayEquals(new byte[]{1,2,3}, archive.read("source:one").orElseThrow());
        assertThrows(IOException.class, () -> archive.publish("source:one", new byte[]{3,2,1}));
    }

    @Test void corruptArchiveRefusesLookupAndCannotBeSilentlyReplaced() throws Exception {
        Path root = directory.resolve("history");
        var archive = new ImmutableHistoryArchive(root, "test-record", 1024);
        archive.publish("source:one", new byte[]{1,2,3});
        Path file;
        try (var files = Files.list(root)) { file = files.findFirst().orElseThrow(); }
        byte[] corrupt = Files.readAllBytes(file); corrupt[corrupt.length - 1] ^= 1; Files.write(file, corrupt);
        assertThrows(IOException.class, () -> archive.read("source:one"));
        assertThrows(IOException.class, () -> archive.publish("source:one", new byte[]{1,2,3}));
        assertArrayEquals(corrupt, Files.readAllBytes(file));
    }
}
