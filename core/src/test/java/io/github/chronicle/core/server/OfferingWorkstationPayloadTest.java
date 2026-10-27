package io.github.chronicle.core.server;

import io.github.chronicle.core.network.OfferingPayloads;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class OfferingWorkstationPayloadTest {
    @Test void availableChoiceCannotAlsoBeComplete(){
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Choice("Prayer",true,true));
        assertDoesNotThrow(()->new OfferingPayloads.Choice("Prayer",false,true));
    }
    @Test void labelsAreBoundedAndNonblank(){
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Choice(" ",false,false));
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Choice("x".repeat(65),false,false));
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Choice(null,false,false));
    }
    @Test void playerChoiceWireShapeHasNoMechanicsOrPurchaseCounts(){
        assertEquals(Set.of("label","available","complete"),fields(OfferingPayloads.Choice.class));
    }
    @Test void playerViewWireShapeHasNoStatsTermsOrRawGateDiagnostics(){
        assertEquals(Set.of("syncId","token","parent","title","state","choices","opening","presentation"),fields(OfferingPayloads.View.class));
    }
    @Test void campaignPresentationContainsOnlyBoundedDisplayStrings(){
        assertEquals(Set.of("pending","unavailable","ready","partial","exhausted","expired","answered"),fields(OfferingPayloads.Presentation.class));
        assertDoesNotThrow(OfferingPayloads.Presentation::neutral);
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Presentation("x".repeat(81),"a","b","c","d","e","f"));
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Presentation(null,"a","b","c","d","e","f"));
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Presentation(" ","a","b","c","d","e","f"));
    }
    @Test void actionRejectsInvalidActionsAndIndices(){
        var token=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Action(token,4,0));
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Action(token,1,16));
        assertThrows(IllegalArgumentException.class,()->new OfferingPayloads.Action(null,1,0));
    }
    @Test void compactServerOnlyRegistrationTermsRemainBounded(){
        assertDoesNotThrow(()->OfferingPayloads.term("x".repeat(32)));
        assertThrows(IllegalArgumentException.class,()->OfferingPayloads.term("x".repeat(33)));
        assertThrows(IllegalArgumentException.class,()->OfferingPayloads.term("Hidden\nterms"));
    }
    private static Set<String> fields(Class<?> type){return Arrays.stream(type.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet());}
}
