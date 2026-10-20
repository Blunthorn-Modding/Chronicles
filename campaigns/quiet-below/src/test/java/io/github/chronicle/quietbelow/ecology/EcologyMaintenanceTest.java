package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologyMaintenanceTest {
    @TempDir Path dir;
    private static final Pos HEART=new Pos(-1,0,0),SOURCE=new Pos(0,0,0);
    private static final EcologyEngine.Setup PINS=new EcologyEngine.Setup("city",1,"heart",1,"minecraft:overworld");
    private static final EcologyEngine.Area SOURCE_AREA=new EcologyEngine.Area(HEART,new Pos(0,2,0));
    private EcologyEngine ready(EcologyJournal j,EcologyEngineTest.World w)throws Exception {
        EcologyEngine e=new EcologyEngine(j);e.configure(PINS,SOURCE_AREA,HEART);
        for(int n=0;n<20&&!e.view().indexed();n++)e.index(w);
        e.mode(true);e.beginTick();e.tick(w,true);return e;
    }
    @Test void regionTakeoverClaimsDetachedSculkWithoutChangingTerrainOrOldHistoryAndReplays()throws Exception {
        var w=new EcologyEngineTest.World();var remote=new Pos(10,0,0);w.blocks.put(remote,SCULK);Path file=dir.resolve("claim.jsonl");
        try(var j=new EcologyJournal(file)){
            var e=ready(j,w);var old=e.cells().stream().filter(c->c.position().equals(SOURCE)).findFirst().orElseThrow();
            e.claim("extra",2,new EcologyEngine.Area(SOURCE,remote));
            for(int n=0;n<20&&!e.claim().complete();n++)e.claimTick(w);
            assertTrue(e.claim().complete());assertTrue(e.owns(remote));assertEquals(0,w.writes);
            assertEquals(old,e.cells().stream().filter(c->c.position().equals(SOURCE)).findFirst().orElseThrow());
            assertEquals(AIR,e.cells().stream().filter(c->c.position().equals(remote)).findFirst().orElseThrow().before());
            e.beginTick();e.tick(w,true);assertFalse(e.rooted(remote),"ownership must not manufacture a connection");
            e.cleanup(true);assertFalse(e.purge(w,remote,true),"declared disconnected sculk is campaign-owned");
        }
        try(var j=new EcologyJournal(file)){
            var e=new EcologyEngine(j);e.attach(PINS,SOURCE_AREA,HEART);assertTrue(e.owns(remote));assertTrue(e.cleanup());assertTrue(e.claim().complete());
        }
    }
    @Test void takeoverWaitsForLoadedChunksSurvivesRestartAndCanStopWithoutUndo()throws Exception {
        var w=new EcologyEngineTest.World();Pos remote=new Pos(16,0,0);w.blocks.put(remote,SCULK);w.unloaded.add(1);Path file=dir.resolve("claim-unload.jsonl");
        try(var j=new EcologyJournal(file)){
            var e=ready(j,w);e.claim("extra",1,new EcologyEngine.Area(remote,remote));
            e.claimTick(w);assertFalse(e.claim().complete());assertFalse(e.owns(remote));
            assertThrows(IllegalStateException.class,()->e.claim("other",1,SOURCE_AREA));
        }
        try(var j=new EcologyJournal(file)){
            var e=new EcologyEngine(j);e.attach(PINS,SOURCE_AREA,HEART);w.unloaded.clear();e.claimTick(w);
            assertTrue(e.owns(remote));e.cancelClaim();assertTrue(e.claim().complete());assertTrue(e.owns(remote));
        }
    }
    @Test void purgeIsExplicitPausedSafePreservesOwnedAndProtectsAPendingPhysicalJoin()throws Exception {
        var w=new EcologyEngineTest.World();Pos remote=new Pos(8,0,0),bridge=new Pos(1,0,0);w.blocks.put(remote,SCULK);w.blocks.put(bridge,SCULK);
        try(var j=new EcologyJournal(dir.resolve("purge.jsonl"))){
            var e=ready(j,w);assertFalse(e.purge(w,remote,true));e.cleanup(true);
            assertFalse(e.purge(w,remote,false));assertFalse(e.purge(w,SOURCE,true));assertFalse(e.purge(w,bridge,true));
            for(int n=0;n<EcologyEngine.MAX_WRITES;n++)e.reserveNativeLoss();assertFalse(e.purge(w,remote,true));
            e.beginTick();assertTrue(e.purge(w,remote,true));assertEquals(AIR,w.get(remote));assertEquals("PURGE",j.entries().getLast().type());
            e.cleanup(false);w.blocks.put(remote,SCULK);assertFalse(e.purge(w,remote,true));
        }
    }
    @Test void cleanupSchedulerIsBoundedRescansChangesAndStopsDuringTakeover()throws Exception {
        var w=new EcologyEngineTest.World();Pos a=new Pos(8,0,0),b=new Pos(9,0,0),c=new Pos(10,0,0);
        w.blocks.put(a,SCULK);w.blocks.put(b,new State("minecraft:sculk_catalyst",Map.of("bloom","false")));w.blocks.put(c,new State("minecraft:sculk_shrieker",Map.of("can_summon","true","shrieking","false","waterlogged","false")));
        try(var j=new EcologyJournal(dir.resolve("scheduler.jsonl"))){
            var e=ready(j,w);e.cleanup(true);var q=new EcologyCleanup(0,1);q.load(0,0);
            q.tick(w,e,false);assertEquals(0,w.writes);
            e.beginTick();q.tick(w,e,true);assertEquals(2,w.writes);assertEquals(2,q.removed());
            e.claim("keep",1,new EcologyEngine.Area(c,c));int before=w.writes;e.beginTick();q.tick(w,e,true);assertEquals(before,w.writes);
            e.cancelClaim();e.beginTick();q.tick(w,e,true);assertEquals(3,q.removed());assertTrue(e.owns(SOURCE));
            w.blocks.put(a,SCULK);q.changed(0,0);e.beginTick();q.tick(w,e,true);assertEquals(AIR,w.get(a));
            q.load(1,0);q.unload(1,0);assertEquals(0,q.pending());
        }
    }
    @Test void cleanupWriteFailureDisablesBothMaintenanceAndGrowthAcrossRestart()throws Exception {
        var w=new EcologyEngineTest.World(){@Override public boolean set(Pos p,State s){return false;}};
        Pos target=new Pos(8,0,0);w.blocks.put(target,SCULK);Path file=dir.resolve("failed-purge.jsonl");
        try(var j=new EcologyJournal(file)){
            var e=ready(j,w);e.cleanup(true);e.beginTick();
            assertThrows(java.io.IOException.class,()->e.purge(w,target,true));
            assertFalse(e.cleanup());assertFalse(e.enabled());assertNotNull(e.failure());assertEquals(SCULK,w.get(target));
        }
        try(var j=new EcologyJournal(file)){var e=new EcologyEngine(j);e.attach(PINS,SOURCE_AREA,HEART);assertFalse(e.cleanup());assertFalse(e.enabled());}
    }
}
