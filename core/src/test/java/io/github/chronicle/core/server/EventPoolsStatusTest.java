package io.github.chronicle.core.server;

import io.github.chronicle.core.eventpool.EventPoolDraw;
import io.github.chronicle.core.play.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EventPoolsStatusTest {
    private final UUID operation=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
    private EventConfiguration config() {
        var d=new EventDefinition("test:pool",1,"Pool test","","Message","",ObservationCatalog.Audience.PARTICIPANTS,Optional.empty(),List.of());
        return new EventConfiguration(d,PlayState.Mode.MANUAL,Optional.empty(),0,0,"",true);
    }
    private EventPoolDraw draw() {return new EventPoolDraw("slot-1","test:pool","a".repeat(64),operation,config(),1,null,List.of(a,b));}
    private EventExecution execution(UUID id,EventExecution.Phase phase,List<UUID> recipients) {
        return new EventExecution("test:pool",id,config(),phase,PlayState.DeliverySource.POOL,null,1,0,recipients,"","");
    }
    @Test void uncertainMediaNeverCountsAsCompletion() {
        assertEquals(EventPools.Status.RESERVED,EventPools.executionStatus(draw(),null,null));
        assertEquals(EventPools.Status.MEDIA_UNCERTAIN,EventPools.executionStatus(draw(),execution(operation,EventExecution.Phase.MEDIA_UNCERTAIN,List.of(a,b)),null));
        assertEquals(EventPools.Status.COMPLETE,EventPools.executionStatus(draw(),execution(operation,EventExecution.Phase.COMPLETE,List.of(a,b)),null));
        assertEquals(EventPools.Status.RUNNING,EventPools.executionStatus(draw(),execution(operation,EventExecution.Phase.EVENT_OUTCOME,List.of(a,b)),null));
    }
    @Test void archiveRetainsAuthorityAfterAnotherLiveInvocation() {
        var newer=execution(UUID.randomUUID(),EventExecution.Phase.COMPLETE,List.of(a,b));
        var original=execution(operation,EventExecution.Phase.COMPLETE,List.of(a,b));
        assertEquals(EventPools.Status.CONFLICT,EventPools.executionStatus(draw(),newer,null));
        assertEquals(EventPools.Status.COMPLETE,EventPools.executionStatus(draw(),newer,original));
    }
    @Test void reconnectOrderIsNotAChangedAudienceButMembershipIs() {
        assertEquals(EventPools.Status.COMPLETE,EventPools.executionStatus(draw(),execution(operation,EventExecution.Phase.COMPLETE,List.of(b,a)),null));
        assertEquals(EventPools.Status.CONFLICT,EventPools.executionStatus(draw(),execution(operation,EventExecution.Phase.COMPLETE,List.of(a)),null));
        var manual=new EventExecution("test:pool",operation,config(),EventExecution.Phase.COMPLETE,PlayState.DeliverySource.MANUAL,a,1,0,List.of(a,b),"","");
        assertEquals(EventPools.Status.CONFLICT,EventPools.executionStatus(draw(),manual,null));
    }
}
