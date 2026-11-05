package io.github.chronicle.core.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AtomicDataFileTest {
    @TempDir Path root;
    @Test void readBeforeWriteAndAtomicRestart() throws Exception {
        Path file=root.resolve("nested/data.json");var store=new AtomicDataFile(file,100);
        assertThrows(IOException.class,()->store.save(new byte[]{1}));
        assertNull(store.load());store.save(new byte[]{1,2});
        assertArrayEquals(new byte[]{1,2},new AtomicDataFile(file,100).load());
        store.save(new byte[]{3});assertArrayEquals(new byte[]{3},store.load());
    }
    @Test void externalCreationAndReplacementArePreserved() throws Exception {
        Path file=root.resolve("data");var store=new AtomicDataFile(file,100);assertNull(store.load());
        Files.write(file,new byte[]{7});assertThrows(IOException.class,()->store.save(new byte[]{1}));
        assertArrayEquals(new byte[]{7},Files.readAllBytes(file));
        assertArrayEquals(new byte[]{7},store.load());Files.write(file,new byte[]{8});
        assertThrows(IOException.class,()->store.save(new byte[]{2}));assertArrayEquals(new byte[]{8},Files.readAllBytes(file));
    }
    @Test void boundedReadsWritesAndNonFiles() throws Exception {
        Path file=root.resolve("data");var store=new AtomicDataFile(file,2);store.load();
        assertThrows(IOException.class,()->store.save(new byte[3]));assertFalse(Files.exists(file));
        Files.write(file,new byte[]{1,2,3});assertThrows(IOException.class,store::load);
        Path directory=root.resolve("dir");Files.createDirectory(directory);
        assertThrows(IOException.class,()->new AtomicDataFile(directory,10).load());
    }
    @Test void cleanupFailureAfterPublicationIsStillSuccessful() throws Exception {
        Path file=root.resolve("data");
        var store=new AtomicDataFile(file,100,path->{throw new IOException("cleanup failure");});
        assertNull(store.load());
        assertDoesNotThrow(()->store.save(new byte[]{1}));
        assertArrayEquals(new byte[]{1},Files.readAllBytes(file));
        assertDoesNotThrow(()->store.save(new byte[]{2}));
        assertArrayEquals(new byte[]{2},new AtomicDataFile(file,100).load());
    }
}
