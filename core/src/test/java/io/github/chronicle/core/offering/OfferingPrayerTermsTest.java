package io.github.chronicle.core.offering;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OfferingPrayerTermsTest {
    @TempDir Path directory;
    @Test void lifePricesAccumulateWithoutGrantingFoodCapacity(){
        var terms=new OfferingTerms(2,0,0,25);
        assertEquals(new OfferingTerms(6,0,0,75),terms.plus(terms).plus(terms));
        assertEquals(0,new OfferingTerms(2,2,0).exhaustionPercent());
    }
    @Test void invalidExhaustionTermsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new OfferingTerms(2,0,0,-1));
        assertThrows(IllegalArgumentException.class,()->new OfferingTerms(2,0,0,16385));
        assertThrows(IllegalArgumentException.class,()->new OfferingTerms(2,0,0,101).validatePurchase());
        assertDoesNotThrow(()->new OfferingTerms(0,0,0,25).validatePurchase());
    }
    @Test void schemaOneKeepsItsOriginalFoodPriceAndInitializationSentinel()throws Exception{
        Path file=directory.resolve("offerings.json");
        String legacy="""
                {"schemaVersion":1,"revision":1,"receipts":[{"transaction":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","player":"11111111-1111-1111-1111-111111111111","source":"quiet_below:network","offering":"quiet_below:extra_heart","terms":{"health":2,"food":2,"speedPercent":0}}],"closedSources":[]}
                """;
        Files.writeString(file,legacy);Files.writeString(file.resolveSibling("offerings.json.initialized"),"chronicle-offerings-schema-1\n");
        byte[] sentinel=Files.readAllBytes(file.resolveSibling("offerings.json.initialized"));
        try(var store=new OfferingStore(file)){
            var old=store.loadOrCreate();assertEquals(new OfferingTerms(2,2,0,0),old.receipts().getFirst().terms());
            var definition=new OfferingDefinition("quiet_below:extra_heart",new OfferingTerms(2,0,0,25),3);
            var next=old.purchase(old.revision(),UUID.randomUUID(),old.receipts().getFirst().player(),"quiet_below:network",definition);
            store.save(next,old);assertEquals(next,OfferingStore.decode(Files.readString(file)));
            assertEquals(new OfferingTerms(4,2,0,25),next.totals(old.receipts().getFirst().player()).get("quiet_below:network"));
        }
        assertArrayEquals(sentinel,Files.readAllBytes(file.resolveSibling("offerings.json.initialized")));
        assertTrue(Files.readString(file).contains("\"schemaVersion\": 2"));
    }
    @Test void schemaTwoRequiresExactlyItsNewIntegerTerm(){
        String valid=OfferingStore.encode(OfferingLedger.EMPTY.purchase(0,UUID.randomUUID(),UUID.randomUUID(),"test:source",new OfferingDefinition("test:life",new OfferingTerms(2,0,0,25),3)));
        assertEquals(25,OfferingStore.decode(valid).receipts().getFirst().terms().exhaustionPercent());
        assertThrows(IllegalArgumentException.class,()->OfferingStore.decode(valid.replace("\"exhaustionPercent\": 25","\"exhaustionPercent\": 25.0")));
        assertThrows(IllegalArgumentException.class,()->OfferingStore.decode(valid.replace("\"exhaustionPercent\": 25","\"other\": 25")));
        assertThrows(IllegalArgumentException.class,()->OfferingStore.decode(valid.replace("\"schemaVersion\": 2","\"schemaVersion\": 3")));
    }
}
