package io.github.chronicle.core.offering.temporary;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TemporaryOfferingLedgerTest {
    private static final UUID TX=UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),PLAYER=UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static TemporaryOfferingClaim claim(){var terms=new TemporaryOfferingTerms(100,-2,25,Map.of("item","minecraft:iron_pickaxe"));return new TemporaryOfferingClaim(TX,PLAYER,"quiet_below:network","quiet_below:repair","overworld|1|2|3",40,140,terms,TemporaryOfferingClaim.State.CLAIMED,null);}
    @Test void claimCompletionExpiryAndClosureAreStrict(){var ledger=TemporaryOfferingLedger.EMPTY.add(0,claim());assertEquals(1,ledger.revision());assertTrue(ledger.claim(TX).orElseThrow().unresolved());assertSame(ledger,ledger.add(ledger.revision(),claim()));ledger=ledger.transition(ledger.revision(),TX,TemporaryOfferingClaim.State.CLAIMED,TemporaryOfferingClaim.State.COMPLETE,null);assertTrue(ledger.claim(TX).orElseThrow().active(139));assertSame(ledger,ledger.expire(139));ledger=ledger.expire(140);assertEquals(TemporaryOfferingClaim.State.EXPIRED,ledger.claim(TX).orElseThrow().state());ledger=ledger.close("quiet_below:network");assertTrue(ledger.closedSources().contains("quiet_below:network"));TemporaryOfferingLedger closed=ledger;assertThrows(IllegalStateException.class,()->closed.add(closed.revision(),new TemporaryOfferingClaim(UUID.randomUUID(),PLAYER,"quiet_below:network","quiet_below:repair","station",200,300,claim().terms(),TemporaryOfferingClaim.State.CLAIMED,null)));}
    @Test void closingSourceRetainsAmbiguousClaimForExactReview(){var ledger=TemporaryOfferingLedger.EMPTY.add(0,claim()).close("quiet_below:network");assertEquals(TemporaryOfferingClaim.State.CLAIMED,ledger.claim(TX).orElseThrow().state());assertEquals(1,ledger.unresolved().size());String digest=claim().digest();assertEquals(digest,ledger.claim(TX).orElseThrow().digest());ledger=ledger.transition(ledger.revision(),TX,TemporaryOfferingClaim.State.CLAIMED,TemporaryOfferingClaim.State.REVIEWED,"GM");assertTrue(ledger.unresolved().isEmpty());assertEquals(digest,ledger.claim(TX).orElseThrow().digest());}
    @Test void stateGraphForbidsSkipAndEveryRewind(){var claimed=TemporaryOfferingLedger.EMPTY.add(0,claim());assertThrows(IllegalStateException.class,()->claimed.transition(claimed.revision(),TX,TemporaryOfferingClaim.State.CLAIMED,TemporaryOfferingClaim.State.EXPIRED,null));var complete=claimed.transition(claimed.revision(),TX,TemporaryOfferingClaim.State.CLAIMED,TemporaryOfferingClaim.State.COMPLETE,null);assertThrows(IllegalStateException.class,()->complete.transition(complete.revision(),TX,TemporaryOfferingClaim.State.COMPLETE,TemporaryOfferingClaim.State.CLAIMED,null));var expired=complete.expire(140);assertThrows(IllegalStateException.class,()->expired.transition(expired.revision(),TX,TemporaryOfferingClaim.State.EXPIRED,TemporaryOfferingClaim.State.COMPLETE,null));}
}
