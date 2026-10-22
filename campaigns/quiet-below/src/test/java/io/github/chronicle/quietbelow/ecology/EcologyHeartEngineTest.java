package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologyHeartEngineTest {
    @TempDir Path dir;
    private static final Pos ROOT=new Pos(-1,0,0),SOURCE=new Pos(0,0,0);
    private static final State ROOT_STATE=new State("minecraft:reinforced_deepslate",Map.of());
    private static final EcologyEngine.Setup PINS=new EcologyEngine.Setup("city",1,"heart",1,"minecraft:overworld");
    private static final EcologyEngine.Area AREA=new EcologyEngine.Area(ROOT,new Pos(1,0,0));
    private static EcologyEngineTest.World world(){
        var w=new EcologyEngineTest.World();w.blocks.clear();w.blocks.put(ROOT,ROOT_STATE);w.blocks.put(SOURCE,SCULK);return w;
    }
    private static EcologyEngine ready(EcologyJournal j,EcologyEngineTest.World w)throws Exception {
        var e=new EcologyEngine(j);e.configure(PINS,AREA,ROOT);
        for(int n=0;n<20&&!e.view().indexed();n++)e.index(w);
        assertTrue(e.view().indexed());e.mode(true);return e;
    }
    private static void ticks(EcologyEngine e,EcologyEngineTest.World w,int count,boolean active)throws Exception {
        for(int n=0;n<count;n++){e.beginTick();e.tick(w,active);e.heartTick(true,active);}
    }
    @Test void defaultClockIsExactDurableAndPauseDoesNotAdvance()throws Exception {
        try(var j=new EcologyJournal(dir.resolve("clock.jsonl"))){
            var w=world();var e=ready(j,w);ticks(e,w,1199,true);
            assertEquals(1199,e.heartElapsed());assertFalse(e.heartVulnerable(true));
            ticks(e,w,1000,false);assertEquals(1199,e.heartElapsed());assertFalse(e.heartVulnerable(true));
            ticks(e,w,1,true);assertEquals(1200,e.heartElapsed());assertTrue(e.heartVulnerable(true));
            assertEquals(1200,j.entries().getLast().payload().get("elapsed").getAsInt());
            e.heartTick(false,true);assertEquals(0,e.heartElapsed());assertFalse(e.heartVulnerable(true));
        }
    }
    @Test void actualLivingThresholdRulesAndUnloadedDetachedCellsResetWeakness()throws Exception {
        try(var j=new EcologyJournal(dir.resolve("strong.jsonl"))){
            var w=world();w.blocks.put(new Pos(1,0,0),SCULK);var e=ready(j,w);
            ticks(e,w,40,true);assertTrue(e.heartElapsed()>0);
            e.tuneHeart(new EcologyHeart.Rules(1,60));ticks(e,w,40,true);assertEquals(0,e.heartElapsed());
            e.tuneHeart(new EcologyHeart.Rules(3,60));ticks(e,w,40,true);assertTrue(e.heartElapsed()>0);
            // Remove the intermediate sculk, leaving an owned detached cell in the same chunk.
            w.blocks.put(SOURCE,AIR);e.observed(SOURCE,AIR);ticks(e,w,40,true);assertFalse(e.rooted(new Pos(1,0,0)));
            w.unloaded.add(0);e.chunkUnloaded(0,0);assertEquals(0,e.heartElapsed());
            ticks(e,w,1200,true);assertFalse(e.heartVulnerable(true));assertEquals(0,e.heartElapsed());
        }
    }
    @Test void restartCannotReuseElapsedOrProduceDiscontinuousJournalClocks()throws Exception {
        Path file=dir.resolve("restart.jsonl");var w=world();
        try(var j=new EcologyJournal(file)){var e=ready(j,w);ticks(e,w,1200,true);assertTrue(e.heartVulnerable(true));}
        try(var j=new EcologyJournal(file)){
            var e=new EcologyEngine(j);e.attach(PINS,AREA,ROOT);assertFalse(e.heartVulnerable(true));assertEquals(0,e.heartElapsed());
            ticks(e,w,60,true);assertEquals(60,e.heartElapsed());assertFalse(e.heartVulnerable(true));
        }
        try(var j=new EcologyJournal(file)){assertDoesNotThrow(()->new EcologyEngine(j));}
    }
    @Test void endingFreezesOwnershipAndCannotReenableOrRewriteHistory()throws Exception {
        Path file=dir.resolve("ending.jsonl");var w=world();UUID job=UUID.randomUUID();List<EcologyEngine.Cell> frozen;
        try(var j=new EcologyJournal(file)){
            var e=ready(j,w);ticks(e,w,1200,true);frozen=e.cells();e.eradicate(job,ROOT,ROOT_STATE,true);
            assertEquals(job,e.ending());assertFalse(e.enabled());assertFalse(e.living(SOURCE));assertFalse(e.rooted(SOURCE));assertFalse(e.cropsAvailable());
            assertThrows(IllegalStateException.class,()->e.mode(true));assertThrows(IllegalStateException.class,()->e.tuneHeart(EcologyHeart.DEFAULT));
            e.observed(SOURCE,AIR);e.remove(SOURCE);assertEquals(frozen,e.cells());
            ticks(e,w,200,true);assertEquals(frozen,e.cells());assertEquals("HEART_END",j.entries().getLast().type());
        }
        try(var j=new EcologyJournal(file)){
            var e=new EcologyEngine(j);e.attach(PINS,AREA,ROOT);assertEquals(job,e.ending());assertEquals(frozen,e.cells());
            assertThrows(IllegalArgumentException.class,()->e.attach(PINS,AREA,SOURCE));
            var payload=new JsonObject();payload.addProperty("enabled",true);j.append("MODE",payload);
            assertThrows(IllegalArgumentException.class,()->new EcologyEngine(j));
        }
    }
    @Test void terminalCheckpointPreservesFrozenPlanAndCannotRedirectRoot()throws Exception {
        var w=world();UUID job=UUID.randomUUID();
        try(var source=new EcologyJournal(dir.resolve("source.jsonl"))){
            var e=ready(source,w);ticks(e,w,1200,true);e.eradicate(job,ROOT,ROOT_STATE,true);
            try(var replacement=new EcologyJournal(dir.resolve("checkpoint.jsonl"))){e.writeCheckpoint(replacement,source.fingerprint());}
            try(var replacement=new EcologyJournal(dir.resolve("checkpoint.jsonl"))){
                var replay=new EcologyEngine(replacement);replay.attach(PINS,AREA,ROOT);assertEquals(job,replay.ending());assertTrue(e.sameCheckpointState(replay));
                assertThrows(IllegalArgumentException.class,()->replay.attach(PINS,AREA,SOURCE));
            }
        }
    }
    @Test void impossibleClockEndingAndUnknownFieldsRejectBeforeAppend()throws Exception {
        try(var j=new EcologyJournal(dir.resolve("invalid.jsonl"))){
            var e=ready(j,world());long revision=j.revision();
            assertThrows(IllegalStateException.class,()->e.eradicate(UUID.randomUUID(),ROOT,ROOT_STATE,true));assertEquals(revision,j.revision());
            var clock=new JsonObject();clock.addProperty("elapsed",100);j.append("HEART_CLOCK",clock);
            assertThrows(IllegalArgumentException.class,()->new EcologyEngine(j));
        }
    }
}
