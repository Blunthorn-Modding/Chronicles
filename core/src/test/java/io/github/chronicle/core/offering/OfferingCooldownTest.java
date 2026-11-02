package io.github.chronicle.core.offering;

import io.github.chronicle.core.offering.temporary.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OfferingCooldownTest {
    @TempDir Path directory;
    private static final UUID PLAYER=UUID.randomUUID(),TX=UUID.randomUUID();
    private static final String SOURCE="test:source";
    private static final OfferingDefinition LIFE=new OfferingDefinition("test:life",new OfferingTerms(2,0,0),3);
    @Test void lastingReceiptCapturesCooldownAndRetryRetainsItsOriginalTerms(){
        var ledger=OfferingLedger.EMPTY.purchase(0,TX,PLAYER,SOURCE,LIFE,100,18000);
        assertEquals(18100,ledger.receipts().getFirst().cooldownUntilTicks());
        assertSame(ledger,ledger.purchase(0,TX,PLAYER,SOURCE,LIFE,200,1));
        assertEquals(18100,OfferingCooldown.until(ledger,TemporaryOfferingLedger.EMPTY,PLAYER,SOURCE));
        assertEquals(0,OfferingCooldown.until(ledger,TemporaryOfferingLedger.EMPTY,UUID.randomUUID(),SOURCE));
        assertEquals(0,OfferingCooldown.until(ledger,TemporaryOfferingLedger.EMPTY,PLAYER,"test:other"));
        assertEquals(ledger,OfferingStore.decode(OfferingStore.encode(ledger)));
    }
    @Test void temporaryCapturedCooldownCountsOnlyProvenSuccess(){
        var terms=OfferingCooldown.capture(new TemporaryOfferingTerms(0,0,0,Map.of("price","original")),100,18000);
        var claim=new TemporaryOfferingClaim(TX,PLAYER,SOURCE,"test:instant","station",100,100,terms,TemporaryOfferingClaim.State.CLAIMED,null);
        var ledger=new TemporaryOfferingLedger(1,List.of(claim),Set.of());
        assertEquals(0,OfferingCooldown.until(OfferingLedger.EMPTY,ledger,PLAYER,SOURCE));
        var complete=claim.with(TemporaryOfferingClaim.State.COMPLETE,null);
        assertEquals(18100,OfferingCooldown.until(OfferingLedger.EMPTY,new TemporaryOfferingLedger(2,List.of(complete),Set.of()),PLAYER,SOURCE));
        assertFalse(OfferingCooldown.archivable(complete,18099));
        assertTrue(OfferingCooldown.archivable(complete,18100));
        var reviewed=claim.with(TemporaryOfferingClaim.State.REVIEWED,"operator");
        assertEquals(0,OfferingCooldown.until(OfferingLedger.EMPTY,new TemporaryOfferingLedger(2,List.of(reviewed),Set.of()),PLAYER,SOURCE));
        assertFalse(reviewed.active(100));
    }
    @Test void legacyTermsAndDigestsRemainExactAndReservedMetadataCannotBeForged(){
        var terms=new TemporaryOfferingTerms(20,0,0,Map.of("legacy","unchanged"));
        var claim=new TemporaryOfferingClaim(TX,PLAYER,SOURCE,"test:legacy","station",100,120,terms,TemporaryOfferingClaim.State.COMPLETE,null);
        String digest=claim.digest(),encoded=TemporaryOfferingStore.encode(new TemporaryOfferingLedger(1,List.of(claim),Set.of()));
        assertSame(terms,OfferingCooldown.capture(terms,100,0));
        assertEquals(digest,TemporaryOfferingStore.decode(encoded).claims().getFirst().digest());
        assertEquals(0,OfferingCooldown.until(claim));
        assertThrows(IllegalArgumentException.class,()->OfferingCooldown.capture(OfferingCooldown.capture(terms,100,20),100,20));
        assertThrows(IllegalArgumentException.class,()->new OfferingReceipt(TX,PLAYER,SOURCE,LIFE.id(),LIFE.terms(),10,9));
    }
    @Test void terminalInstantCooldownSurvivesReopenAndCannotBeArchivedBeforeDurableDeadline()throws Exception{
        Path file=directory.resolve("temporary-offerings.json");
        var terms=OfferingCooldown.capture(new TemporaryOfferingTerms(0,0,0,Map.of()),100,18000);
        var claimed=new TemporaryOfferingClaim(TX,PLAYER,SOURCE,"test:instant","station",100,100,terms,TemporaryOfferingClaim.State.CLAIMED,null);
        try(var store=new TemporaryOfferingStore(file)){
            var empty=store.loadOrCreate();var ledger=store.save(empty.add(0,claimed),empty);
            var complete=ledger.transition(ledger.revision(),TX,TemporaryOfferingClaim.State.CLAIMED,TemporaryOfferingClaim.State.COMPLETE,null);store.save(complete,ledger);
        }
        try(var store=new TemporaryOfferingStore(file)){
            var ledger=store.loadOrCreate();
            assertEquals(18100,OfferingCooldown.until(OfferingLedger.EMPTY,ledger,PLAYER,SOURCE));
            assertSame(ledger,store.archiveTerminals(ledger,c->OfferingCooldown.archivable(c,18099)));
            var archived=store.archiveTerminals(ledger,c->OfferingCooldown.archivable(c,18100));assertTrue(archived.claims().isEmpty());
            assertEquals(18100,OfferingCooldown.until(store.archived(TX).orElseThrow()));
            assertEquals(claimed.digest(),store.archived(TX).orElseThrow().digest());
        }
    }
    @Test void bothLedgersContributeTheLatestCapturedDeadlineAndSourceClosureStaysTerminal(){
        var lasting=OfferingLedger.EMPTY.purchase(0,TX,PLAYER,SOURCE,LIFE,100,1000);
        var terms=OfferingCooldown.capture(new TemporaryOfferingTerms(10,0,0,Map.of()),200,18000);
        var claim=new TemporaryOfferingClaim(UUID.randomUUID(),PLAYER,SOURCE,"test:temporary","station2",200,210,terms,TemporaryOfferingClaim.State.EXPIRED,null);
        var temporary=new TemporaryOfferingLedger(2,List.of(claim),Set.of());
        assertEquals(18200,OfferingCooldown.until(lasting,temporary,PLAYER,SOURCE));
        assertEquals(1100,OfferingCooldown.until(lasting,TemporaryOfferingLedger.EMPTY,PLAYER,SOURCE));
        var closed=lasting.closeSource(SOURCE);assertThrows(IllegalStateException.class,()->closed.purchase(closed.revision(),TX,PLAYER,SOURCE,LIFE,300,1));
        assertEquals(18200,OfferingCooldown.until(claim));
    }
    @Test void schemaTwoReadsWithoutInventingHistoricalCooldown(){
        String legacy="""
                {"schemaVersion":2,"revision":1,"receipts":[{"transaction":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","player":"11111111-1111-1111-1111-111111111111","source":"test:source","offering":"test:life","terms":{"health":2,"food":0,"speedPercent":0,"exhaustionPercent":25}}],"closedSources":[]}
                """;
        var receipt=OfferingStore.decode(legacy).receipts().getFirst();
        assertEquals(0,receipt.acceptedAtTicks());assertEquals(0,receipt.cooldownUntilTicks());assertEquals(25,receipt.terms().exhaustionPercent());
        String schema3=OfferingStore.encode(OfferingStore.decode(legacy));
        assertThrows(IllegalArgumentException.class,()->OfferingStore.decode(schema3.replace("\"cooldownUntilTicks\": 0","\"cooldownUntilTicks\": 0.0")));
    }
}
