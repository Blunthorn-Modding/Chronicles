package io.github.chronicle.core.progression;

import io.github.chronicle.core.books.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BookSourceRecoveryTest {
    private static final String BOOK="test:recovery_book",TOKEN="d4a18c19-fdc8-46cd-9237-947e30d06788";
    private static final UUID ACTOR=UUID.fromString("d6fa017a-789c-4f25-9910-295ce0054cc4");
    @TempDir Path directory;
    private static SourceLedger.Receipt receipt(String id,UUID actor,boolean gate){return new SourceLedger.Receipt(id,"chronicle_core:book_found",BOOK,"",actor,true,Map.of("test:gate",gate),"book:"+TOKEN,SourceLedger.Stage.PREPARED);}
    private static BookDelivery delivery(BookDiscovery discovery){return new BookDelivery(BOOK,TOKEN,true,Set.of(),discovery);}
    @Test void missingDiscoveryRequiresThePinnedActorsAuthenticInventoryBeforeAnyProgress() {
        var receipt=receipt("source:old",ACTOR,false);var delivery=delivery(null);
        assertThrows(IllegalStateException.class,()->BookSourceRecovery.inspect(receipt,delivery,false));
        assertEquals(BookSourceRecovery.Decision.RECORD_AUTHENTIC_INVENTORY,BookSourceRecovery.inspect(receipt,delivery,true));
        assertNull(delivery.firstDiscovery());assertFalse(receipt.flags().get("test:gate"));
    }
    @Test void exactDurableDiscoveryCanReconcileWithoutReplayingInventory() {
        var delivery=delivery(new BookDiscovery(ACTOR.toString(),"2026-10-28T12:00:00Z"));
        assertEquals(BookSourceRecovery.Decision.ALREADY_RECORDED,BookSourceRecovery.inspect(receipt("source:old",ACTOR,false),delivery,false));
    }
    @Test void recoveryNeverOverwritesAnotherPlayersFirstDiscoveryOrChangedIssuance() {
        var receipt=receipt("source:old",ACTOR,false);
        assertThrows(IllegalStateException.class,()->BookSourceRecovery.inspect(receipt,delivery(new BookDiscovery(UUID.randomUUID().toString(),"2026-10-28T12:00:00Z")),true));
        assertThrows(IllegalStateException.class,()->BookSourceRecovery.inspect(receipt,new BookDelivery(BOOK,UUID.randomUUID().toString(),true,Set.of(),null),true));
        assertThrows(IllegalStateException.class,()->BookSourceRecovery.inspect(receipt,new BookDelivery(BOOK,TOKEN,false,Set.of(),null),true));
    }
    @Test void cancellationAllowsADistinctAttemptWithChangedActorAndFlagsWithoutChangingOldEvidence() {
        var original=receipt("source:old",ACTOR,false);var retry=receipt("source:new",UUID.randomUUID(),true);
        var ledger=SourceLedger.empty().prepare(original).finish(original.id(),false).prepare(retry);
        assertEquals(SourceLedger.Stage.CANCELLED,ledger.receipts().get(original.id()).stage());
        assertFalse(ledger.receipts().get(original.id()).flags().get("test:gate"));
        assertEquals(retry,ledger.receipts().get(retry.id()));assertTrue(ledger.receipts().get(retry.id()).flags().get("test:gate"));
        assertEquals(original.authority(),retry.authority());assertNotEquals(original.id(),retry.id());
    }
    @Test void restartRetainsPendingBookIdentityAndOriginalFlagSnapshot()throws Exception {
        var receipt=receipt("source:old",ACTOR,false);var store=new SourceLedgerStore(directory.resolve("sources.bin"));store.load();store.save(SourceLedger.empty().prepare(receipt));
        var restarted=new SourceLedgerStore(directory.resolve("sources.bin")).load();assertEquals(receipt,restarted.receipts().get(receipt.id()));
        assertThrows(IllegalStateException.class,()->BookSourceRecovery.inspect(restarted.receipts().get(receipt.id()),delivery(null),false));
    }
}
