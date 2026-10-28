package io.github.chronicle.core.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceBatchAbsenceTest {
    @TempDir Path directory;
    @Test void observedFreshBatchAbsenceCanBeVerifiedWithoutCreatingAFile()throws IOException {
        Path file=directory.resolve("journal.bin");var store=new EventBatchFiles.Store(file);
        assertEquals(EventBatchFiles.Journal.empty(),store.load());store.verifyUnchanged();assertFalse(Files.exists(file));
        Files.writeString(file,"competing writer");assertThrows(IOException.class,store::verifyUnchanged);
    }
    @Test void removedOrChangedPreviouslyPresentBatchJournalIsNotProofOfRefusal()throws IOException {
        Path file=directory.resolve("journal.bin");var store=new EventBatchFiles.Store(file);store.load();store.save(EventBatchFiles.Journal.empty());store.verifyUnchanged();
        Files.delete(file);assertThrows(IOException.class,store::verifyUnchanged);Files.writeString(file,"corrupt");assertThrows(IOException.class,store::verifyUnchanged);
    }
}
