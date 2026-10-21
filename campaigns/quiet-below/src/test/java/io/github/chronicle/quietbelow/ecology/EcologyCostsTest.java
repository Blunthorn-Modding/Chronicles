package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.Pos;
import static org.junit.jupiter.api.Assertions.*;

class EcologyCostsTest {
    private static final Pos FOOTING=new Pos(0,0,0),OTHER=new Pos(1,0,0),OFF=new Pos(0,1,0),PILLAR=new Pos(0,2,0);
    private static final UUID A=new UUID(0,1),B=new UUID(0,2);
    static final class Network implements EcologyCosts.Network {Set<Pos> cells=new HashSet<>(Set.of(FOOTING,OTHER));boolean available=true,extensive=true,living=true;public boolean costsAvailable(){return available;}public boolean extensive(){return extensive;}public boolean owns(Pos p){return cells.contains(p);}public boolean living(Pos p){return living&&cells.contains(p);}}
    static final class World implements EcologyCosts.World {List<EcologyCosts.Player> players=new ArrayList<>(List.of(new EcologyCosts.Player(A,FOOTING)));Map<UUID,Set<EcologyCosts.Kind>> active=Map.of();int syncs;public List<EcologyCosts.Player> eligiblePlayers(){return List.copyOf(players);}public void synchronize(Map<UUID,Set<EcologyCosts.Kind>> next){active=next;syncs++;}}
    static EcologyCosts.Rules fast(boolean hunger,boolean darkness){return new EcologyCosts.Rules(new EcologyCosts.Rule(hunger,10),new EcologyCosts.Rule(darkness,10));}
    static void ticks(EcologyCosts c,World w,Network n,int count,boolean play){for(int i=0;i<count;i++){int before=w.syncs;c.tick(w,n,play);assertEquals(before+1,w.syncs);assertTrue(c.checks()<=EcologyCosts.MAX_METADATA_CHECKS);assertTrue(c.attempts()<=EcologyCosts.MAX_APPLICATIONS);}}

    @Test void fullWaitThenSustainsPastOldPulsePeriods(){var w=new World();var n=new Network();var c=new EcologyCosts(fast(true,true));ticks(c,w,n,200,true);assertTrue(w.active.isEmpty());ticks(c,w,n,1,true);assertEquals(Set.of(EcologyCosts.Kind.HUNGER,EcologyCosts.Kind.DARKNESS),w.active.get(A));assertEquals(2,c.applications());ticks(c,w,n,800,true);assertEquals(Set.of(EcologyCosts.Kind.HUNGER,EcologyCosts.Kind.DARKNESS),w.active.get(A));assertEquals(2,c.applications());assertEquals(2,c.attempts());}
    @Test void livingFootingMovesPreserveButOffAndPillarReset(){var w=new World();var n=new Network();var c=new EcologyCosts(fast(true,false));ticks(c,w,n,100,true);w.players=List.of(new EcologyCosts.Player(A,OTHER));ticks(c,w,n,101,true);assertEquals(Set.of(EcologyCosts.Kind.HUNGER),w.active.get(A));w.players=List.of(new EcologyCosts.Player(A,OFF));ticks(c,w,n,1,true);assertTrue(w.active.isEmpty());w.players=List.of(new EcologyCosts.Player(A,FOOTING));ticks(c,w,n,200,true);assertTrue(w.active.isEmpty());w.players=List.of(new EcologyCosts.Player(A,PILLAR));ticks(c,w,n,1,true);w.players=List.of(new EcologyCosts.Player(A,FOOTING));ticks(c,w,n,200,true);assertTrue(w.active.isEmpty());ticks(c,w,n,1,true);assertEquals(Set.of(EcologyCosts.Kind.HUNGER),w.active.get(A));}
    @Test void jumpingBetweenLivingFootingsRetainsButLandingOffResets(){
        var w=new World();var n=new Network();var c=new EcologyCosts(fast(true,false));
        ticks(c,w,n,100,true);assertEquals(FOOTING,c.footing(A));w.players=List.of(EcologyCosts.Player.airborne(A));ticks(c,w,n,101,true);assertEquals(Set.of(EcologyCosts.Kind.HUNGER),w.active.get(A));assertEquals(FOOTING,c.footing(A));
        w.players=List.of(new EcologyCosts.Player(A,OTHER));ticks(c,w,n,1,true);assertEquals(OTHER,c.footing(A));
        w.players=List.of(new EcologyCosts.Player(A,OFF));ticks(c,w,n,1,true);assertTrue(w.active.isEmpty());assertNull(c.footing(A));
        w.players=List.of(new EcologyCosts.Player(A,FOOTING));ticks(c,w,n,200,true);assertTrue(w.active.isEmpty());ticks(c,w,n,1,true);assertTrue(w.active.containsKey(A));
    }
    @Test void newAirbornePlayerCannotStartAndCutFootingWhileAirborneClears(){
        var w=new World();var n=new Network();var c=new EcologyCosts(fast(true,false));
        w.players=List.of(EcologyCosts.Player.airborne(A));ticks(c,w,n,400,true);assertTrue(w.active.isEmpty());assertEquals(0,c.tracked());
        w.players=List.of(new EcologyCosts.Player(A,FOOTING));ticks(c,w,n,100,true);assertEquals(FOOTING,c.footing(A));w.players=List.of(EcologyCosts.Player.airborne(A));n.cells.remove(FOOTING);ticks(c,w,n,1,true);
        assertTrue(w.active.isEmpty());assertEquals(0,c.tracked());assertNull(c.footing(A));
    }
    @Test void kindsAndExtensiveEdgesHaveFreshIndependentWaits(){var w=new World();var n=new Network();var c=new EcologyCosts(fast(true,true));n.extensive=false;ticks(c,w,n,201,true);assertEquals(Set.of(EcologyCosts.Kind.HUNGER),w.active.get(A));n.extensive=true;ticks(c,w,n,200,true);assertEquals(Set.of(EcologyCosts.Kind.HUNGER),w.active.get(A));ticks(c,w,n,1,true);assertEquals(Set.of(EcologyCosts.Kind.HUNGER,EcologyCosts.Kind.DARKNESS),w.active.get(A));n.extensive=false;ticks(c,w,n,1,true);assertEquals(Set.of(EcologyCosts.Kind.HUNGER),w.active.get(A));n.extensive=true;ticks(c,w,n,200,true);assertEquals(Set.of(EcologyCosts.Kind.HUNGER),w.active.get(A));}
    @Test void pauseAvailabilityLeaveRulesAndFailureSynchronizeEmpty(){var w=new World();var n=new Network();var c=new EcologyCosts(fast(true,false));ticks(c,w,n,100,true);ticks(c,w,n,1,false);assertTrue(w.active.isEmpty());ticks(c,w,n,200,true);assertTrue(w.active.isEmpty());n.available=false;ticks(c,w,n,1,true);n.available=true;w.players=List.of();ticks(c,w,n,1,true);w.players=List.of(new EcologyCosts.Player(A,FOOTING));c.rules(fast(false,false));ticks(c,w,n,1,true);assertTrue(w.active.isEmpty());c.fail("native failure");ticks(c,w,n,1,true);assertTrue(w.active.isEmpty());assertEquals("native failure",c.failure());}
    @Test void capacityProofAndActiveSnapshotAreBounded(){var w=new World();var n=new Network();var c=new EcologyCosts(fast(true,true));List<EcologyCosts.Player> group=new ArrayList<>();for(int i=0;i<EcologyCosts.MAX_PLAYERS;i++)group.add(new EcologyCosts.Player(new UUID(2,i),FOOTING));w.players=group;ticks(c,w,n,201,true);assertEquals(EcologyCosts.MAX_PLAYERS,c.tracked());assertEquals(EcologyCosts.MAX_PLAYERS*2,c.attempts());UUID activeId=group.getFirst().id();assertTrue(w.active.containsKey(activeId));assertThrows(UnsupportedOperationException.class,()->w.active.put(A,Set.of()));assertThrows(UnsupportedOperationException.class,()->w.active.get(activeId).add(EcologyCosts.Kind.HUNGER));List<EcologyCosts.Player> tooMany=new ArrayList<>(group);tooMany.add(new EcologyCosts.Player(B,FOOTING));w.players=tooMany;ticks(c,w,n,1,true);assertNotNull(c.failure());assertTrue(w.active.isEmpty());assertEquals(0,c.tracked());}
    @Test void weakContactAndRestartNeedFreshFullWait(){var w=new World();var n=new Network();var c=new EcologyCosts(fast(true,false));ticks(c,w,n,100,true);n.living=false;ticks(c,w,n,1,true);n.living=true;ticks(c,w,n,200,true);assertTrue(w.active.isEmpty());c=new EcologyCosts(fast(true,false));ticks(c,w,n,200,true);assertTrue(w.active.isEmpty());ticks(c,w,n,1,true);assertEquals(Set.of(EcologyCosts.Kind.HUNGER),w.active.get(A));}
    @Test void malformedRulesFailClosed(){assertThrows(IllegalArgumentException.class,()->new EcologyCosts.Rule(true,2));}
    @Test void replacementAndReconnectResetOnlyThatPlayersWait(){
        var w=new World();var n=new Network();var c=new EcologyCosts(fast(true,true));
        w.players=List.of(new EcologyCosts.Player(A,FOOTING),new EcologyCosts.Player(B,OTHER));
        ticks(c,w,n,201,true);assertEquals(2,w.active.size());
        c.resetPlayer(A);ticks(c,w,n,200,true);assertFalse(w.active.containsKey(A));assertTrue(w.active.containsKey(B));
        ticks(c,w,n,1,true);assertTrue(w.active.containsKey(A));
        c.resetPlayer(A);w.players=List.of(new EcologyCosts.Player(B,OTHER));ticks(c,w,n,1,true);
        w.players=List.of(new EcologyCosts.Player(A,FOOTING),new EcologyCosts.Player(B,OTHER));
        ticks(c,w,n,200,true);assertFalse(w.active.containsKey(A));assertTrue(w.active.containsKey(B));
    }
}
