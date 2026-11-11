package io.github.chronicle.core.areas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AreaPersistenceStoreTest {
    @TempDir Path temp;

    @Test void catalogAndJournalRoundTripStrictEvidence() throws IOException {
        SnapshotRef ref=AreaSnapshotCodec.encode(AreaSnapshotStoreTest.snapshot()).ref();
        AreaChange area=new AreaChange("area","Area",ref.bounds(),2,Optional.of(ref),Optional.empty());
        AreaCatalog catalog=new AreaCatalog(4,Map.of("area",area),Set.of("old"));
        AreaCatalogStore catalogStore=new AreaCatalogStore(temp.resolve("catalog.bin"));catalogStore.load();catalogStore.save(catalog);
        assertEquals(catalog,new AreaCatalogStore(temp.resolve("catalog.bin")).load());

        AreaOperation operation=AreaOperationServiceTest.operation(UUID.randomUUID(),false);AreaOperationJournal journal=new AreaOperationJournal(1,Map.of(operation.operationId(),operation));
        AreaOperationStore operationStore=new AreaOperationStore(temp.resolve("operations.bin"));operationStore.load();operationStore.save(journal);
        assertEquals(journal,new AreaOperationStore(temp.resolve("operations.bin")).load());
    }

    @Test void malformedJournalIsPreservedAndExternalCatalogChangeBlocksOverwrite() throws IOException {
        Path journalFile=temp.resolve("operations.bin");Files.write(journalFile,new byte[]{1,2,3});byte[] malformed=Files.readAllBytes(journalFile);
        AreaOperationStore invalidStore=new AreaOperationStore(journalFile);assertThrows(IOException.class,invalidStore::load);
        assertThrows(IOException.class,()->invalidStore.save(AreaOperationJournal.empty()));assertArrayEquals(malformed,Files.readAllBytes(journalFile));

        Path catalogFile=temp.resolve("catalog.bin");AreaCatalogStore store=new AreaCatalogStore(catalogFile);store.load();store.save(AreaCatalog.empty());Files.write(catalogFile,new byte[]{9});
        assertThrows(IOException.class,()->store.save(AreaCatalog.empty()));assertArrayEquals(new byte[]{9},Files.readAllBytes(catalogFile));
    }
}
