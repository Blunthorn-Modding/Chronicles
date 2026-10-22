package io.github.chronicle.core.server;

import io.github.chronicle.core.network.OfferingPayloads;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OfferingWorkstationPayloadTest {
    private static final OfferingPayloads.Stats BEFORE=new OfferingPayloads.Stats(20,20,.1);
    private static OfferingPayloads.Choice choice(int count,OfferingPayloads.Stats after){
        return new OfferingPayloads.Choice("Heart",count,3,"Complete trade","Food capacity +2","Health +2",BEFORE,after,"");
    }
    @Test void statsRejectNonfiniteAndInvalidNativeFoodRanges(){
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Stats(Double.NaN,20,.1));
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Stats(20,20,Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Stats(-1,20,.1));
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Stats(20,1,.1));
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Stats(20,41,.1));
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Stats(20,20,-.1));
    }
    @Test void nativeStatsRemainTypedAndUnrounded(){
        var after=new OfferingPayloads.Stats(22.75,26,.1375);var c=choice(1,after);
        assertEquals(22.75,c.after().health());assertEquals(26,c.after().food());assertEquals(.1375,c.after().speed());
        assertEquals(BEFORE,c.before());
    }
    @Test void exhaustedCardRetainsCurrentStatsWithoutInventingAnotherPurchase(){
        var c=choice(3,null);assertEquals(3,c.count());assertEquals(3,c.limit());assertNull(c.after());assertEquals(BEFORE,c.before());
    }
    @Test void unboundedTermsAndInvalidChoiceCountsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Choice("x",0,3,"details","x".repeat(33),"b",BEFORE,null,""));
        assertThrows(IllegalArgumentException.class,()->choice(129,null));
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Choice("x",0,0,"d","c","b",BEFORE,null,""));
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Choice("x",0,3,"d","c","b",null,null,""));
    }
    @Test void compactTermsFitTheDefaultFontBudgetAndRejectNonportableGlyphs(){
        assertDoesNotThrow(()->OfferingPayloads.term("W".repeat(32)));
        assertThrows(IllegalArgumentException.class,()->OfferingPayloads.term(""));
        assertThrows(IllegalArgumentException.class,()->OfferingPayloads.term("Hidden\nterms"));
        assertThrows(IllegalArgumentException.class,()->OfferingPayloads.term("\u25a0"));
        assertThrows(IllegalArgumentException.class,()->new LastingBargains.Choice(
                new io.github.chronicle.core.offering.OfferingDefinition("test:heart",new io.github.chronicle.core.offering.OfferingTerms(2,2,0),3),
                "Heart","Details","W".repeat(33),"Heart +1"));
    }
    @Test void actionRejectsInvalidActionsAndIndices(){
        var token=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Action(token,4,0));
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Action(token,1,16));
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Action(null,1,0));
    }
}
