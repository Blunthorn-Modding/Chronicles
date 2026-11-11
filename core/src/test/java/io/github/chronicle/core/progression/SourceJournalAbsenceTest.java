package io.github.chronicle.core.progression;

import io.github.chronicle.core.areas.*;
import io.github.chronicle.core.offering.OfferingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SourceJournalAbsenceTest {
    @TempDir Path directory;
    @Test void freshObservedAreaAbsenceIsProofUntilACompetingFileAppears()throws IOException {
        Path file=directory.resolve("operations.bin");var store=new AreaOperationStore(file);var service=new AreaOperationService(store);
        assertEquals(AreaOperationJournal.empty(),service.state());service.verifyUnchanged();
        Files.writeString(file,"external replacement");assertThrows(IOException.class,service::verifyUnchanged);
    }
    @Test void deletingOrCorruptingPreviouslyPresentAreaAuthorityIsNeverAbsenceProof()throws IOException {
        Path file=directory.resolve("operations.bin");var store=new AreaOperationStore(file);store.load();store.save(AreaOperationJournal.empty());store.verifyUnchanged();
        Files.delete(file);assertThrows(IOException.class,store::verifyUnchanged);
        Files.writeString(file,"corrupt");assertThrows(IOException.class,store::verifyUnchanged);
    }
    @Test void validOfferingAbsenceIsProofButRemovalOrCorruptionRequiresRecovery()throws IOException {
        Path file=directory.resolve("offerings.json");
        try(var store=new OfferingStore(file)) {
            var ledger=store.loadOrCreate();UUID pending=UUID.randomUUID();store.verifyUnchanged();assertTrue(ledger.receipts().stream().noneMatch(receipt->receipt.transaction().equals(pending)));
            byte[] original=Files.readAllBytes(file);Files.delete(file);assertThrows(IOException.class,store::verifyUnchanged);
            Files.writeString(file,"corrupt");assertThrows(IOException.class,store::verifyUnchanged);Files.write(file,original);store.verifyUnchanged();
        }
    }
}
