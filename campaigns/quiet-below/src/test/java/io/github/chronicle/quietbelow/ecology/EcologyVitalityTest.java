package io.github.chronicle.quietbelow.ecology;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologyVitalityTest {
    @TempDir Path dir;
    static final Pos P=new Pos(0,0,0);
    static void mode(EcologyVitality v,EcologyVitality.Mode m){JsonArray states=new JsonArray();states.add(EcologyVitality.change(P,m));apply(v,EcologyVitality.payload(0,states,-1));}
    static void apply(EcologyVitality v,JsonObject p){v.validate(p,P::equals);v.apply(p);}
    static void time(EcologyVitality v,int ticks){while(ticks>0){int n=Math.min(200,ticks);apply(v,EcologyVitality.payload(n,new JsonArray(),-1));ticks-=n;}}
    @Test void detachedCellsLoseVitalityAndRecoverEvenFromZero(){
        var v=new EcologyVitality();mode(v,EcologyVitality.Mode.DORMANT);time(v,6000);assertEquals(50,v.percent(P));assertTrue(v.ready(P));
        time(v,4000);assertFalse(v.ready(P));time(v,2000);assertEquals(0,v.value(P));
        mode(v,EcologyVitality.Mode.SHADED);time(v,1500);assertEquals(25,v.percent(P));assertTrue(v.ready(P));time(v,4500);assertEquals(100,v.percent(P));
    }
    @Test void brightRecoveryIsTwiceAsFastAndUnknownExposureNeverDrains(){
        var v=new EcologyVitality();mode(v,EcologyVitality.Mode.SEALED);time(v,12000);assertEquals(0,v.value(P));
        mode(v,EcologyVitality.Mode.LIT);time(v,1500);assertEquals(50,v.percent(P));
        mode(v,EcologyVitality.Mode.UNKNOWN);time(v,24000);assertEquals(50,v.percent(P));
        mode(v,EcologyVitality.Mode.SHADED);time(v,3000);assertEquals(100,v.percent(P));
    }
    @Test void rateChangesAreNotRetroactiveAndUnchangedModesNeedNoPerCellWrites(){
        var v=new EcologyVitality();mode(v,EcologyVitality.Mode.DORMANT);time(v,6000);assertEquals(50,v.percent(P));
        v.applyRules(EcologyVitality.rulesPayload(new EcologyVitality.Rules(20,10,2)));time(v,6000);assertEquals(25,v.percent(P));
        mode(v,EcologyVitality.Mode.SHADED);time(v,3000);assertEquals(50,v.percent(P));
        v.applyRules(EcologyVitality.rulesPayload(new EcologyVitality.Rules(20,5,2)));time(v,3000);assertEquals(100,v.percent(P));
        v.forget(P);assertEquals(100,v.percent(P));assertNull(v.mode(P));
    }
    @Test void extentHasAnExitBufferAndTunableThresholds(){
        var r=EcologyVitality.DEFAULT;assertEquals("Rooted",EcologyVitality.nextExtent("Rooted",255,r));
        assertEquals("Established",EcologyVitality.nextExtent("Rooted",256,r));assertEquals("Established",EcologyVitality.nextExtent("Established",224,r));
        assertEquals("Rooted",EcologyVitality.nextExtent("Established",223,r));assertEquals("Extensive",EcologyVitality.nextExtent("Extensive",1792,r));
        assertEquals("Established",EcologyVitality.nextExtent("Extensive",1791,r));assertEquals("Extensive",EcologyVitality.nextExtent("Rooted",512,new EcologyVitality.Rules(10,5,0)));
    }
    @Test void malformedTransitionsAreRejectedBeforePublication(){
        var v=new EcologyVitality();assertThrows(IllegalArgumentException.class,()->apply(v,EcologyVitality.payload(201,new JsonArray(),-1)));
        assertThrows(IllegalArgumentException.class,()->apply(v,EcologyVitality.payload(0,new JsonArray(),-1)));
        JsonArray cells=new JsonArray();cells.add(EcologyVitality.change(P,EcologyVitality.Mode.DORMANT));cells.add(EcologyVitality.change(P,EcologyVitality.Mode.LIT));
        assertThrows(IllegalArgumentException.class,()->apply(v,EcologyVitality.payload(1,cells,-1)));
        assertThrows(IllegalArgumentException.class,()->new EcologyVitality.Rules(0,5,2));assertThrows(IllegalArgumentException.class,()->new EcologyVitality.Rules(10,5,5));
        JsonObject bad=EcologyVitality.rulesPayload(EcologyVitality.DEFAULT);bad.addProperty("loss",1.5);assertThrows(IllegalArgumentException.class,()->EcologyVitality.parseRules(bad));assertEquals(0,v.ticks());
    }
    @Test void worldDecayKeepsDormantTerrainPausesAndRestoresAfterPhysicalReconnection()throws Exception{
        var w=new Branch();Path file=dir.resolve("recovery.jsonl");long savedTicks;int health;
        try(var j=new EcologyJournal(file)){
            var e=ready(j,w);e.tuneVitality(new EcologyVitality.Rules(1,1,2));ticks(e,w,20,true);assertTrue(e.living(Branch.END));
            w.blocks.put(Branch.BRIDGE,AIR);e.observed(Branch.BRIDGE,AIR);ticks(e,w,1400,true);
            assertFalse(e.rooted(Branch.END));assertEquals(0,e.vitality().percent(Branch.END));assertEquals(SCULK,w.get(Branch.END));assertTrue(e.owns(Branch.END));
            e.flushVitality();savedTicks=e.vitality().ticks();health=e.vitality().value(Branch.END);ticks(e,w,2000,false);assertEquals(savedTicks,e.vitality().ticks());assertEquals(health,e.vitality().value(Branch.END));
            w.blocks.put(Branch.BRIDGE,SCULK);e.noticeContact(Branch.BRIDGE);ticks(e,w,5,true);assertTrue(e.rooted(Branch.END));assertFalse(e.living(Branch.END));
            ticks(e,w,500,true);e.flushVitality();assertTrue(e.living(Branch.END));assertTrue(e.vitality().percent(Branch.END)>=25);
            e.flushVitality();savedTicks=e.vitality().ticks();health=e.vitality().value(Branch.END);
        }
        try(var j=new EcologyJournal(file)){
            var e=new EcologyEngine(j);e.attach(Branch.PINS,Branch.AREA,Branch.HEART);assertEquals(new EcologyVitality.Rules(1,1,2),e.vitality().rules());
            assertEquals(savedTicks,e.vitality().ticks());assertEquals(health,e.vitality().value(Branch.END));long bytes=j.bytes();ticks(e,w,2000,false);assertEquals(bytes,j.bytes());
        }
    }
    @Test void unloadingHoldsVitalityAndSealingStopsRecoveryWithoutTerrainDecay()throws Exception{
        var w=new Branch();w.blocks.put(Branch.END,Branch.STONE);try(var j=new EcologyJournal(dir.resolve("unknown.jsonl"))){
            var e=ready(j,w);e.tuneVitality(new EcologyVitality.Rules(1,1,2));ticks(e,w,5,true);
            w.hidden=true;ticks(e,w,2000,true);long clock=e.vitality().ticks();ticks(e,w,2000,true);assertEquals(clock,e.vitality().ticks());assertEquals(100,e.vitality().percent(Branch.BRIDGE));
            w.hidden=false;w.sealed=true;ticks(e,w,1400,true);assertEquals(0,e.vitality().percent(Branch.BRIDGE));assertEquals(SCULK,w.get(Branch.BRIDGE));
            w.sealed=false;ticks(e,w,500,true);e.flushVitality();assertTrue(e.living(Branch.BRIDGE));assertNull(e.failure());
        }
    }
    @Test void maximumSourceKeepsFixedReadWorkAndDoesNotWriteHealthEveryCellEverySecond()throws Exception{
        var w=new Branch(){
            @Override public State get(Pos p){reads++;return p.equals(HEART)?HEART_BLOCK:p.x()>=0&&p.x()<16&&p.y()>=0&&p.y()<128&&p.z()>=0&&p.z()<16?SCULK:AIR;}
        };
        var area=new EcologyEngine.Area(new Pos(0,0,0),new Pos(15,127,15));
        try(var j=new EcologyJournal(dir.resolve("maximum.jsonl"))){
            var e=new EcologyEngine(j);e.configure(Branch.PINS,area,Branch.HEART);for(int n=0;n<2000&&!e.view().indexed();n++)e.index(w);
            assertEquals(32767,e.view().owned());e.mode(true);long before=j.revision();long worst=0;
            for(int n=0;n<1400;n++){w.reads=0;e.beginTick();e.tick(w,true);worst=Math.max(worst,w.reads);assertTrue(w.reads<=4096,"reads="+w.reads);}
            assertTrue(j.revision()-before<300,"health modes are bounded batches; steady time uses shared checkpoints");assertEquals(0,w.writes);assertNull(e.failure());
            System.out.println("maximum city owned="+e.view().owned()+", worst reads/tick="+worst+", new records="+(j.revision()-before));
        }
    }
    @Test void chunkUnloadFlushesSpentTicksAndImmediatelyInvalidatesTheLivingGraph()throws Exception{
        var w=new Branch();try(var j=new EcologyJournal(dir.resolve("chunk-edge.jsonl"))){
            var e=ready(j,w);ticks(e,w,17,true);assertTrue(e.living(Branch.END));long before=e.vitality().ticks();
            e.chunkUnloaded(99,99);assertTrue(e.living(Branch.END));assertEquals(before,e.vitality().ticks());
            e.chunkUnloaded(0,0);assertFalse(e.rooted(Branch.END));assertTrue(e.vitalityPending());assertTrue(e.vitality().ticks()>=before);
            long flushed=e.vitality().ticks();w.hidden=true;ticks(e,w,1000,true);assertEquals(flushed,e.vitality().ticks());
            w.hidden=false;ticks(e,w,10,true);assertTrue(e.living(Branch.END));assertNull(e.failure());
        }
    }
    @Test void repeatedExposureChurnIsBoundedAndNeverDeletesExhaustedSculk()throws Exception{
        var w=new Branch(){
            @Override public State get(Pos p){reads++;return p.equals(HEART)?HEART_BLOCK:p.x()>=0&&p.x()<8&&p.y()>=0&&p.y()<8&&p.z()>=0&&p.z()<8?SCULK:sealed?HEART_BLOCK:AIR;}
        };
        try(var j=new EcologyJournal(dir.resolve("churn.jsonl"))){
            var e=new EcologyEngine(j);e.configure(Branch.PINS,new EcologyEngine.Area(Branch.HEART,new Pos(7,7,7)),Branch.HEART);
            for(int n=0;n<100&&!e.view().indexed();n++)e.index(w);e.mode(true);e.tuneVitality(new EcologyVitality.Rules(1,1,2));
            long before=j.revision();int worst=0;
            for(int n=0;n<10000;n++){w.sealed=(n/400)%2==1;w.reads=0;e.beginTick();e.tick(w,true);worst=Math.max(worst,w.reads);assertTrue(w.reads<=4096);}
            e.flushVitality();assertEquals(511,e.view().owned());assertEquals(0,w.writes);assertNull(e.failure());
            assertEquals(EcologyVitality.FULL,e.vitality().value(new Pos(3,3,3)),"covered interior stays nourished by adjacent owned roots");
            assertTrue(j.revision()-before<1500);assertTrue(j.bytes()<2*1024*1024);
            System.out.println("exposure churn ticks=10000, worst reads="+worst+", records="+(j.revision()-before)+", bytes="+j.bytes());
        }
    }
    static EcologyEngine ready(EcologyJournal j,Branch w)throws Exception{var e=new EcologyEngine(j);e.configure(Branch.PINS,Branch.AREA,Branch.HEART);for(int n=0;n<20&&!e.view().indexed();n++)e.index(w);e.mode(true);return e;}
    static void ticks(EcologyEngine e,EcologyTerrain w,int n,boolean play)throws Exception{for(int k=0;k<n;k++){e.beginTick();e.tick(w,play);}}
    static class Branch implements EcologyTerrain {
        static final Pos HEART=new Pos(0,0,0),BRIDGE=new Pos(1,0,0),END=new Pos(2,0,0);
        static final State HEART_BLOCK=new State("minecraft:reinforced_deepslate",Map.of());
        static final State STONE=new State("minecraft:stone",Map.of());
        static final EcologyEngine.Setup PINS=new EcologyEngine.Setup("city",1,"heart",1,"minecraft:overworld");
        static final EcologyEngine.Area AREA=new EcologyEngine.Area(HEART,END);
        final Map<Pos,State> blocks=new HashMap<>();boolean hidden,sealed;int reads,writes;
        Branch(){blocks.put(HEART,HEART_BLOCK);blocks.put(BRIDGE,SCULK);blocks.put(END,SCULK);}
        public boolean loaded(Pos p){return !hidden;}
        public State get(Pos p){reads++;return blocks.getOrDefault(p,sealed?HEART_BLOCK:AIR);}
        // Isolate health from expansion: this port offers no eligible substrate or vein supports.
        public boolean solidFace(Pos p,int f){return false;}public boolean substrate(Pos p){return false;}
        public int light(Pos p){return 0;}public boolean set(Pos p,State s){writes++;blocks.put(p,s);return true;}
    }
}
