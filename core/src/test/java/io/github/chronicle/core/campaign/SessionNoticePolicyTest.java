package io.github.chronicle.core.campaign;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionNoticePolicyTest {
    private static SessionNoticePolicy.State state(SessionPhase phase,String hold){return new SessionNoticePolicy.State(phase,1,hold);}
    @Test void activeAnnouncesOnlyPresentationTransitionsAndNeverPeriodicRefreshes(){
        var active=state(SessionPhase.ACTIVE,"");
        assertTrue(active.broadcastAfter(null,false,false));
        assertTrue(active.broadcastAfter(state(SessionPhase.PAUSED,""),false,false));
        assertTrue(active.broadcastAfter(state(SessionPhase.ACTIVE,"saving"),false,false));
        assertFalse(active.broadcastAfter(active,true,false));
        assertFalse(active.broadcastAfter(active,false,true));
        assertFalse(active.broadcastAfter(active,true,true));
        assertFalse(active.persistent());
    }
    @Test void everyFrozenPhaseAndHoldRetainsItsReminder(){
        for(SessionPhase phase:SessionPhase.values()){
            var held=state(phase,"saving");assertTrue(held.persistent());assertTrue(held.broadcastAfter(held,false,true));
            var normal=state(phase,"");assertEquals(phase!=SessionPhase.ACTIVE,normal.persistent());
            assertEquals(phase!=SessionPhase.ACTIVE,normal.broadcastAfter(normal,true,false));
        }
        var unavailable=state(null,"");assertTrue(unavailable.broadcastAfter(unavailable,false,true));
        assertTrue(state(SessionPhase.ACTIVE,"saving").broadcastAfter(state(SessionPhase.ACTIVE,""),false,false));
    }
    @Test void globalBuildingChangesRefreshTheFrozenNotice() {
        var off=new SessionNoticePolicy.State(SessionPhase.PAUSED,1,"",false);
        var on=new SessionNoticePolicy.State(SessionPhase.PAUSED,1,"",true);
        assertTrue(on.broadcastAfter(off,false,false));
        assertTrue(off.broadcastAfter(on,false,false));
        assertTrue(on.persistent());
    }

}
