package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;

import java.util.*;

import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.Pos;
import static org.junit.jupiter.api.Assertions.*;

class EcologyCostsTest {
    private static final Pos NEAR=new Pos(2,0,0),FAR=new Pos(5,0,0);
    private static final UUID A=new UUID(0,1),B=new UUID(0,2);

    static final class Network implements EcologyCosts.Network {
        Set<Pos> cells=denseCells();boolean available=true,extensive=true,living=true;
        public boolean costsAvailable(){return available;}
        public boolean extensive(){return extensive;}
        public boolean owns(Pos p){return cells.contains(p);}
        public boolean living(Pos p){return living&&cells.contains(p);}
    }
    static final class World implements EcologyCosts.World {
        List<EcologyCosts.Player> players=new ArrayList<>(List.of(new EcologyCosts.Player(A,NEAR)));
        List<EcologyCosts.Kind> kinds=new ArrayList<>();List<UUID> ids=new ArrayList<>();boolean guarded=true;
        public List<EcologyCosts.Player> eligiblePlayers(){return List.copyOf(players);}
        public boolean apply(UUID id,EcologyCosts.Kind kind){ids.add(id);kinds.add(kind);return guarded;}
    }
    static Set<Pos> denseCells(){
        Set<Pos> cells=new LinkedHashSet<>();
        for(int x=-2;x<=2&&cells.size()<EcologyCosts.DENSITY;x++)for(int y=-2;y<=2&&cells.size()<EcologyCosts.DENSITY;y++)for(int z=-2;z<=2&&cells.size()<EcologyCosts.DENSITY;z++)
            if((x-NEAR.x())*(x-NEAR.x())+(y-NEAR.y())*(y-NEAR.y())+(z-NEAR.z())*(z-NEAR.z())<=16)cells.add(new Pos(x,y,z));
        return cells;
    }
    static EcologyCosts.Rules fast(boolean hunger,boolean darkness){return new EcologyCosts.Rules(new EcologyCosts.Rule(hunger,10),new EcologyCosts.Rule(darkness,10));}
    static void ticks(EcologyCosts costs,World world,Network network,int count,boolean playing){
        for(int i=0;i<count;i++){
            int before=world.ids.size();costs.tick(world,network,playing);
            assertTrue(costs.checks()<=EcologyCosts.MAX_METADATA_CHECKS);
            assertTrue(costs.attempts()<=EcologyCosts.MAX_APPLICATIONS);
            assertTrue(world.ids.size()-before<=EcologyCosts.MAX_APPLICATIONS);
        }
    }

    @Test void firstEntryWaitsAFullIndependentIntervalThenAppliesBoth(){
        var world=new World();var network=new Network();var costs=new EcologyCosts(fast(true,true));
        ticks(costs,world,network,200,true);assertTrue(world.kinds.isEmpty());
        ticks(costs,world,network,1,true);assertEquals(List.of(EcologyCosts.Kind.HUNGER,EcologyCosts.Kind.DARKNESS),world.kinds);
        ticks(costs,world,network,200,true);assertEquals(4,world.kinds.size());
    }
    @Test void denseProofIsEightDistinctCellsWithinEuclideanRadius(){
        var network=new Network();assertTrue(EcologyCosts.dense(network,NEAR));
        network.cells.remove(network.cells.iterator().next());assertFalse(EcologyCosts.dense(network,NEAR));
        assertFalse(EcologyCosts.dense(network,FAR));network.living=false;assertFalse(EcologyCosts.dense(network,NEAR));
    }
    @Test void proofAvailabilityAndParticipationLossDiscardCredit(){
        var world=new World();var network=new Network();var costs=new EcologyCosts(fast(true,false));
        ticks(costs,world,network,100,true);network.cells.remove(network.cells.iterator().next());ticks(costs,world,network,1,true);
        network.cells=denseCells();ticks(costs,world,network,200,true);assertTrue(world.kinds.isEmpty());ticks(costs,world,network,1,true);assertEquals(1,world.kinds.size());
        network.available=false;ticks(costs,world,network,20,true);network.available=true;ticks(costs,world,network,200,true);assertEquals(1,world.kinds.size());
        world.players=List.of();ticks(costs,world,network,10,true);world.players=List.of(new EcologyCosts.Player(A,NEAR));ticks(costs,world,network,201,true);assertEquals(2,world.kinds.size());
    }
    @Test void darknessNeedsExtensiveAndStartsFreshWhenItReturns(){
        var world=new World();var network=new Network();var costs=new EcologyCosts(fast(false,true));network.extensive=false;
        ticks(costs,world,network,500,true);assertTrue(world.kinds.isEmpty());network.extensive=true;
        ticks(costs,world,network,200,true);assertTrue(world.kinds.isEmpty());ticks(costs,world,network,1,true);assertEquals(List.of(EcologyCosts.Kind.DARKNESS),world.kinds);
    }
    @Test void nativeSkipStillConsumesIntervalAndRulesResetCooldowns(){
        var world=new World();var network=new Network();var costs=new EcologyCosts(fast(true,false));world.guarded=false;
        ticks(costs,world,network,201,true);assertEquals(1,costs.applications());ticks(costs,world,network,199,true);assertEquals(1,costs.applications());ticks(costs,world,network,1,true);assertEquals(2,costs.applications());
        costs.rules(fast(true,false));ticks(costs,world,network,200,true);assertEquals(2,costs.applications());ticks(costs,world,network,1,true);assertEquals(3,costs.applications());
    }
    @Test void rotatesOneSearchAndLimitsAttemptsAndCapacity(){
        var world=new World();var network=new Network();var costs=new EcologyCosts(fast(true,true));
        world.players=List.of(new EcologyCosts.Player(A,NEAR),new EcologyCosts.Player(B,NEAR));
        ticks(costs,world,network,202,true);assertEquals(Set.of(A,B),new HashSet<>(world.ids));assertTrue(costs.attempts()<=2);
        List<EcologyCosts.Player> tooMany=new ArrayList<>();for(int i=0;i<=EcologyCosts.MAX_PLAYERS;i++)tooMany.add(new EcologyCosts.Player(new UUID(1,i),NEAR));world.players=tooMany;
        costs.tick(world,network,true);assertNotNull(costs.failure());assertEquals(0,costs.tracked());
    }
    @Test void fullParticipantCapacityRetainsBoundedProofWorkAndNoSourceStacking(){
        var world=new World();var network=new Network();var costs=new EcologyCosts(fast(true,true));
        List<EcologyCosts.Player> group=new ArrayList<>();
        for(int n=0;n<EcologyCosts.MAX_PLAYERS;n++)group.add(new EcologyCosts.Player(new UUID(2,n),NEAR));
        world.players=group;ticks(costs,world,network,EcologyCosts.MAX_PLAYERS*2,true);
        assertNull(costs.failure());assertEquals(EcologyCosts.MAX_PLAYERS,costs.tracked());
        assertTrue(costs.checks()<=EcologyCosts.MAX_PLAYERS*EcologyCosts.DENSITY);
        assertEquals(EcologyCosts.MAX_PLAYERS,new HashSet<>(world.ids).size());
        // All cached proofs fail on a one-tick exit. Only one bounded fresh search is attempted.
        world.players=group.stream().map(p->new EcologyCosts.Player(p.id(),FAR)).toList();
        ticks(costs,world,network,1,true);assertEquals(0,costs.tracked());assertEquals(0,costs.attempts());
    }
    @Test void malformedRulesAndExplicitFailureFailClosed(){
        assertThrows(IllegalArgumentException.class,()->new EcologyCosts.Rule(true,2));
        var world=new World();var network=new Network();var costs=new EcologyCosts(EcologyCosts.DEFAULT);costs.fail("native error");
        ticks(costs,world,network,1000,true);assertEquals("native error",costs.failure());assertTrue(world.ids.isEmpty());
    }
    @Test void pauseMovementAndWeaknessDiscardCredit(){
        var world=new World();var network=new Network();var costs=new EcologyCosts(fast(true,false));
        ticks(costs,world,network,100,true);ticks(costs,world,network,1,false);ticks(costs,world,network,200,true);assertTrue(world.kinds.isEmpty());ticks(costs,world,network,1,true);assertEquals(1,world.kinds.size());
        world.players=List.of(new EcologyCosts.Player(A,FAR));ticks(costs,world,network,1,true);world.players=List.of(new EcologyCosts.Player(A,NEAR));ticks(costs,world,network,200,true);assertEquals(1,world.kinds.size());ticks(costs,world,network,1,true);assertEquals(2,world.kinds.size());
        network.living=false;ticks(costs,world,network,1,true);network.living=true;ticks(costs,world,network,200,true);assertEquals(2,world.kinds.size());ticks(costs,world,network,1,true);assertEquals(3,world.kinds.size());
    }
    @Test void transientExtensiveLossResetsDarknessForEveryTrackedParticipant(){
        var world=new World();var network=new Network();var costs=new EcologyCosts(fast(false,true));
        world.players=List.of(new EcologyCosts.Player(A,NEAR),new EcologyCosts.Player(B,NEAR));ticks(costs,world,network,102,true);assertEquals(2,costs.tracked());
        network.extensive=false;ticks(costs,world,network,1,true);network.extensive=true;
        ticks(costs,world,network,198,true);assertTrue(world.kinds.isEmpty(),"an unselected participant retained Darkness credit across an Extensive loss");
    }
}
