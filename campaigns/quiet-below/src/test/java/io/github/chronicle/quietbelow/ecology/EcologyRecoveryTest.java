package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.Pos;
import static org.junit.jupiter.api.Assertions.*;

class EcologyRecoveryTest {
    private static final Pos SCULK=new Pos(0,0,0), NEAR=new Pos(2,0,0), FAR=new Pos(5,0,0);
    private static final UUID A=new UUID(0,1),B=new UUID(0,2);
    static final class Network implements EcologyRecovery.Network {
        Set<Pos> owned=new HashSet<>(Set.of(SCULK));boolean available=true,living=true;
        public boolean recoveryAvailable(){return available;}
        public boolean owns(Pos p){return owned.contains(p);}
        public boolean living(Pos p){return living&&owned.contains(p);}
    }
    static final class World implements EcologyRecovery.World {
        List<EcologyRecovery.Player> players=new ArrayList<>(List.of(new EcologyRecovery.Player(A,NEAR)));
        Set<UUID> hurt=new HashSet<>(Set.of(A));List<UUID> healed=new ArrayList<>();int calls;boolean guarded=true;
        public List<EcologyRecovery.Player> eligiblePlayers(){return List.copyOf(players);}
        public boolean needsHealing(UUID p){return hurt.contains(p);}
        public boolean heal(UUID p,float points){calls++;if(!guarded||!hurt.contains(p))return false;assertEquals(1.0F,points);healed.add(p);return true;}
    }
    static void ticks(EcologyRecovery recovery,World world,Network network,int count,boolean play){
        for(int i=0;i<count;i++){
            int calls=world.calls;recovery.tick(world,network,play);
            assertTrue(recovery.checks()<=EcologyRecovery.GUARDED_CHECKS);
            assertTrue(recovery.heals()<=EcologyRecovery.HEALS);
            assertTrue(world.calls-calls<=EcologyRecovery.HEALS);
        }
    }

    @Test void healsOneHalfHeartOnlyAfterAFreshInterval(){
        var world=new World();var net=new Network();var recovery=new EcologyRecovery(new EcologyRecovery.Rules(true,2));
        ticks(recovery,world,net,40,true);assertEquals(0,world.calls);ticks(recovery,world,net,1,true);assertEquals(1,world.calls);assertEquals(1,recovery.healed());
        ticks(recovery,world,net,40,true);assertEquals(2,world.calls);
    }
    @Test void pausePowerLossAndLeavingReachClearTheCooldown(){
        var world=new World();var net=new Network();var recovery=new EcologyRecovery(new EcologyRecovery.Rules(true,2));
        ticks(recovery,world,net,20,true);long clock=recovery.ticks();ticks(recovery,world,net,100,false);assertEquals(clock,recovery.ticks());
        ticks(recovery,world,net,39,true);assertEquals(0,world.calls);net.available=false;ticks(recovery,world,net,10,true);assertEquals(0,recovery.tracked());net.available=true;
        ticks(recovery,world,net,40,true);assertEquals(0,world.calls);ticks(recovery,world,net,1,true);assertEquals(1,world.calls);
        world.players=List.of(new EcologyRecovery.Player(A,FAR));ticks(recovery,world,net,20,true);world.players=List.of(new EcologyRecovery.Player(A,NEAR));ticks(recovery,world,net,40,true);assertEquals(1,world.calls);ticks(recovery,world,net,1,true);assertEquals(2,world.calls);
    }
    @Test void healthyPlayersCannotBankHealingAndGuardedNativeRecheckCanReject(){
        var world=new World();var net=new Network();var recovery=new EcologyRecovery(new EcologyRecovery.Rules(true,2));
        world.hurt.clear();ticks(recovery,world,net,200,true);assertEquals(0,world.calls);world.hurt.add(A);ticks(recovery,world,net,40,true);assertEquals(0,world.calls);world.guarded=false;ticks(recovery,world,net,1,true);assertEquals(1,world.calls);assertEquals(0,recovery.healed());
    }
    @Test void requiresOwnedLivingSculkWithinEuclideanRadiusAndSourcesDoNotMultiply(){
        var net=new Network();var recovery=new EcologyRecovery(EcologyRecovery.DEFAULT);
        assertTrue(recovery.nearLiving(net,new Pos(4,0,0)));assertFalse(recovery.nearLiving(net,new Pos(4,1,0)));assertTrue(recovery.checks()<=EcologyRecovery.GUARDED_CHECKS);
        net.owned.add(new Pos(1,0,0));var world=new World();ticks(recovery,world,net,201,true);assertEquals(1,world.calls);
        net.living=false;assertFalse(recovery.nearLiving(net,NEAR));net.living=true;net.owned.clear();assertFalse(recovery.nearLiving(net,NEAR));
    }
    @Test void evaluatesAtMostOnePlayerAndFailsClosedAtCapacity(){
        var world=new World();var net=new Network();var recovery=new EcologyRecovery(EcologyRecovery.DEFAULT);
        world.players=List.of(new EcologyRecovery.Player(A,NEAR),new EcologyRecovery.Player(B,NEAR));world.hurt.add(B);recovery.tick(world,net,true);assertEquals(0,world.calls);assertTrue(recovery.checks()<=EcologyRecovery.CUBE_CHECKS);assertTrue(recovery.tracked()<=2);
        var tooMany=new ArrayList<EcologyRecovery.Player>();for(int i=0;i<=EcologyRecovery.MAX_PLAYERS;i++)tooMany.add(new EcologyRecovery.Player(new UUID(1,i),NEAR));world.players=tooMany;recovery.tick(world,net,true);assertNotNull(recovery.failure());assertEquals(0,recovery.tracked());
    }
    @Test void roundRobinSurvivesListReorderingAndGivesEachEligiblePlayerAFreshRate(){
        var world=new World();var net=new Network();var recovery=new EcologyRecovery(new EcologyRecovery.Rules(true,2));
        world.players=List.of(new EcologyRecovery.Player(A,NEAR),new EcologyRecovery.Player(B,NEAR));world.hurt.add(B);
        for(int tick=0;tick<42;tick++){
            world.players=tick%2==0?List.of(new EcologyRecovery.Player(B,NEAR),new EcologyRecovery.Player(A,NEAR)):List.of(new EcologyRecovery.Player(A,NEAR),new EcologyRecovery.Player(B,NEAR));
            ticks(recovery,world,net,1,true);
        }
        assertEquals(2,world.healed.size());assertEquals(Set.of(A,B),new HashSet<>(world.healed));
    }
    @Test void onlyNativeSuppliedEligiblePlayersAreConsideredAndEntryStartsFresh(){
        var world=new World();var net=new Network();var recovery=new EcologyRecovery(new EcologyRecovery.Rules(true,2));
        world.hurt.clear();world.hurt.add(B);ticks(recovery,world,net,200,true);assertEquals(0,world.calls);
        world.players=List.of(new EcologyRecovery.Player(B,NEAR));ticks(recovery,world,net,40,true);assertEquals(0,world.calls);ticks(recovery,world,net,1,true);assertEquals(List.of(B),world.healed);
    }
    @Test void unsampledReachAndHealthChangesInvalidateTheOldDeadline(){
        var world=new World();var net=new Network();var recovery=new EcologyRecovery(new EcologyRecovery.Rules(true,2));
        world.players=List.of(new EcologyRecovery.Player(A,NEAR),new EcologyRecovery.Player(B,NEAR));world.hurt.add(B);
        ticks(recovery,world,net,1,true); // A enters with a deadline at tick 41.
        world.players=List.of(new EcologyRecovery.Player(A,FAR),new EcologyRecovery.Player(B,NEAR));world.hurt.clear();world.hurt.add(B);
        ticks(recovery,world,net,1,true); // B is selected; A is invalidated before its turn.
        world.players=List.of(new EcologyRecovery.Player(A,NEAR),new EcologyRecovery.Player(B,NEAR));world.hurt.clear();world.hurt.add(A);
        ticks(recovery,world,net,1,true); // A re-enters at tick 3, with a fresh deadline at 43.
        ticks(recovery,world,net,39,true);assertEquals(0,world.calls);
        ticks(recovery,world,net,1,true);assertEquals(List.of(A),world.healed);
    }
    @Test void rejectsMalformedRulesAndDisabledRulesClearTiming(){
        assertThrows(IllegalArgumentException.class,()->new EcologyRecovery.Rules(true,1));
        var world=new World();var net=new Network();var recovery=new EcologyRecovery(new EcologyRecovery.Rules(true,2));ticks(recovery,world,net,20,true);recovery.rules(new EcologyRecovery.Rules(false,2));ticks(recovery,world,net,100,true);assertEquals(0,world.calls);recovery.rules(new EcologyRecovery.Rules(true,2));ticks(recovery,world,net,40,true);assertEquals(0,world.calls);ticks(recovery,world,net,1,true);assertEquals(1,world.calls);
    }
}
