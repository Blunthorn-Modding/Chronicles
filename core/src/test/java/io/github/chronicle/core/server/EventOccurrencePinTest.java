package io.github.chronicle.core.server;

import io.github.chronicle.core.play.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EventOccurrencePinTest {
    @Test void actorlessOrOriginallyUnqualifiedFactsCannotPinTheSourceLedgerForever() {
        var definition=new EventDefinition("test:first",1,"First","","Private","",ObservationCatalog.Audience.SOURCE_ACTOR,Optional.empty(),List.of(),Map.of(),List.of(),4);
        var configuration=new EventConfiguration(definition,PlayState.Mode.SYSTEM,Optional.empty(),0,0,List.of("test:ready"),true,new EventSource("test:mixed_actor","*",""),EventOccurrencePolicy.ONCE_PER_ACTOR);
        var actor=UUID.randomUUID();
        assertTrue(EventOccurrenceRuntime.inapplicable(configuration,new EventSources.Occurrence("a","test:mixed_actor","*","",null,true,Map.of("test:ready",true),"trial:a")));
        assertTrue(EventOccurrenceRuntime.inapplicable(configuration,new EventSources.Occurrence("b","test:mixed_actor","*","",actor,true,Map.of("test:ready",false),"trial:b")));
        assertFalse(EventOccurrenceRuntime.inapplicable(configuration,new EventSources.Occurrence("c","test:mixed_actor","*","",actor,true,Map.of("test:ready",true),"trial:c")));
    }
    @Test void pressureAlsoBoundsStableActorIndexBeforeItCanLoseAnIdentity() {
        var actors=new HashMap<String,EventOccurrenceState.ActorConsumption>();
        for(int i=0;i<EventOccurrenceState.MAX_LIVE_ACTORS;i++){
            if(i==EventOccurrenceState.MAX_LIVE_ACTORS-1)assertFalse(EventOccurrenceRuntime.atCapacity(new EventOccurrenceState(0,Map.of(),actors)));
            var actor=new EventOccurrenceState.ActorConsumption("test:actor_cue",new UUID(0,i),UUID.randomUUID(),"source:"+i,"a".repeat(64));actors.put(actor.key(),actor);
        }
        assertTrue(EventOccurrenceRuntime.atCapacity(new EventOccurrenceState(0,Map.of(),actors)));
    }
    @Test void pressureStartsAtExactQueueBoundWithoutEvictingItsClaims() {
        var claims=new LinkedHashMap<UUID,EventOccurrenceState.Claim>();UUID actor=UUID.randomUUID();
        for(int i=0;i<EventOccurrenceState.MAX_LIVE_CLAIMS;i++){
            if(i==EventOccurrenceState.MAX_LIVE_CLAIMS-1)assertFalse(EventOccurrenceRuntime.atCapacity(new EventOccurrenceState(0,claims,Map.of())));
            UUID operation=UUID.randomUUID();var source=new EventOccurrenceState.SourceSnapshot("source:"+i,"test:source","*","",actor,true,Map.of(),"trial:"+i);
            claims.put(operation,new EventOccurrenceState.Claim("test:event",operation,"a".repeat(64),source,1,0,List.of(actor),EventOccurrenceState.Stage.CLAIMED,""));
        }
        var full=new EventOccurrenceState(0,claims,Map.of());assertTrue(EventOccurrenceRuntime.atCapacity(full));assertEquals(EventOccurrenceState.MAX_LIVE_CLAIMS,full.claims().size());
    }
    @Test void runtimeRejectsWrongActorRecipientsOperationAndConfiguration() throws Exception {
        UUID actor = UUID.randomUUID(), operation = UUID.randomUUID();
        var definition = new EventDefinition("test:prayer", 1, "Prayer", "", "Private", "", ObservationCatalog.Audience.SOURCE_ACTOR, Optional.empty(), List.of(), Map.of(), List.of(), 4);
        var configuration = new EventConfiguration(definition, PlayState.Mode.SYSTEM, Optional.empty(), 0, 0, List.of(), true, new EventSource("test:prayer_done", "*", ""), EventOccurrencePolicy.ONCE_PER_ACTOR);
        var source = new EventOccurrenceState.SourceSnapshot("source:one", "test:prayer_done", "test:altar", "", actor, true, Map.of(), "prayer:one");
        var claim = new EventOccurrenceState.Claim(definition.id(), operation, configuration.fingerprint(), source, 1, 0, List.of(actor), EventOccurrenceState.Stage.CLAIMED, "");
        var execution = new EventExecution(definition.id(), operation, configuration, EventExecution.Phase.COMPLETE, PlayState.DeliverySource.SYSTEM, actor, 1, 0, List.of(actor), "", "");
        EventOccurrenceRuntime.requireExact(claim, execution);
        assertThrows(IOException.class, () -> EventOccurrenceRuntime.requireExact(claim, new EventExecution(definition.id(), UUID.randomUUID(), configuration, execution.phase(), execution.source(), actor, 1, 0, List.of(actor), "", "")));
        assertThrows(IOException.class, () -> EventOccurrenceRuntime.requireExact(claim, new EventExecution(definition.id(), operation, configuration, execution.phase(), execution.source(), UUID.randomUUID(), 1, 0, List.of(actor), "", "")));
        assertThrows(IOException.class, () -> EventOccurrenceRuntime.requireExact(claim, new EventExecution(definition.id(), operation, configuration, execution.phase(), execution.source(), actor, 1, 0, List.of(UUID.randomUUID()), "", "")));
    }
}
