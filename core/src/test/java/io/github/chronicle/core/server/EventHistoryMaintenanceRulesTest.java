package io.github.chronicle.core.server;

import io.github.chronicle.core.play.*;
import io.github.chronicle.core.progression.SourceLedger;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EventHistoryMaintenanceRulesTest {
    @Test void everyMatchingConfiguredEventMustHaveObservedTheExactSourceFact() {
        SourceLedger.Receipt receipt=receipt("source:one");
        EventConfiguration first=configuration("test:first",new EventSource(receipt.type(),receipt.target(),""));
        EventConfiguration second=configuration("test:second",new EventSource(receipt.type(),"*",""));
        EventConfiguration unrelated=configuration("test:unrelated",new EventSource(receipt.type(),"other",""));
        Map<String,EventProgressState.Entry> onlyFirst=Map.of(first.definition().id(),waiting(first,Set.of(receipt.id())));
        assertFalse(EventProgressRuntime.observedByEverySubscriber(List.of(first,second,unrelated),onlyFirst,receipt));
        Map<String,EventProgressState.Entry> all=new HashMap<>(onlyFirst);all.put(second.definition().id(),waiting(second,Set.of(receipt.id())));
        assertTrue(EventProgressRuntime.observedByEverySubscriber(List.of(first,second,unrelated),all,receipt));
    }

    @Test void latchedOccurrenceRemainsSufficientWhenSeenSetWasAlreadyCompacted() {
        SourceLedger.Receipt receipt=receipt("source:latched");
        EventConfiguration configuration=configuration("test:latched",new EventSource(receipt.type(),receipt.target(),receipt.activity()));
        EventProgressState.Entry latched=new EventProgressState.Entry(configuration.definition().id(),configuration.fingerprint(),50,true,
                receipt.actor(),PlayState.DeliverySource.SYSTEM,receipt.id(),true,Set.of(),Map.of(),false);
        assertTrue(EventProgressRuntime.observedByEverySubscriber(List.of(configuration),Map.of(configuration.definition().id(),latched),receipt));
        EventProgressState.Entry stale=new EventProgressState.Entry(configuration.definition().id(),"f".repeat(64),50,true,
                receipt.actor(),PlayState.DeliverySource.SYSTEM,receipt.id(),true,Set.of(),Map.of(),false);
        assertFalse(EventProgressRuntime.observedByEverySubscriber(List.of(configuration),Map.of(configuration.definition().id(),stale),receipt));
    }

    private static SourceLedger.Receipt receipt(String id){return new SourceLedger.Receipt(id,"chronicle_core:block_opened","site:test","OPEN",
            UUID.fromString("11111111-1111-1111-1111-111111111111"),true,Map.of(),"transient:test",SourceLedger.Stage.COMMITTED);}
    private static EventConfiguration configuration(String id,EventSource source){
        EventDefinition definition=new EventDefinition(id,1,"Test","","","",ObservationCatalog.Audience.PARTICIPANTS,
                Optional.empty(),List.of(),Map.of("test:done",true));
        return new EventConfiguration(definition,PlayState.Mode.SYSTEM,Optional.empty(),0,0,List.of(),true,source);
    }
    private static EventProgressState.Entry waiting(EventConfiguration configuration,Set<String> facts){return new EventProgressState.Entry(
            configuration.definition().id(),configuration.fingerprint(),-1,false,null,PlayState.DeliverySource.SYSTEM,"",false,facts,Map.of(),false);}
}
