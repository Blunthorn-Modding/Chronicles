package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologyEngineTest {
    @TempDir Path dir;
    private static final State STONE=new State("minecraft:stone",Map.of());
    private static final State WOOD=new State("minecraft:oak_planks",Map.of());
    private static final Pos HEART=new Pos(-1,0,0),SOURCE=new Pos(0,0,0);
    private static final EcologyEngine.Setup PINS=new EcologyEngine.Setup("city",1,"heart",1,"minecraft:overworld");
    private static final EcologyEngine.Area AREA=new EcologyEngine.Area(new Pos(-1,0,0),new Pos(0,2,0));

    @Test void indexesAutomaticallyButDoesNotArmOrChangeTerrain() throws Exception {
        World w=new World();try(var j=new EcologyJournal(dir.resolve("index.jsonl"))){
            EcologyEngine e=new EcologyEngine(j);e.configure(PINS,AREA,HEART);index(e,w);
            assertTrue(e.view().indexed());assertFalse(e.enabled());assertTrue(e.owns(SOURCE));assertEquals(0,w.writes);
            assertTrue(e.cells().getFirst().adopted());assertEquals(AIR,e.cells().getFirst().before());
            ticks(e,w,true,100);assertEquals(1,e.cells().size());
        }
    }
    @Test void incompleteIndexWaitsForChunksAndCannotEnable() throws Exception {
        World w=new World();w.unloaded.add(-1);try(var j=new EcologyJournal(dir.resolve("missing.jsonl"))){
            EcologyEngine e=new EcologyEngine(j);e.configure(PINS,AREA,HEART);
            for(int i=0;i<20;i++)e.index(w);
            assertFalse(e.view().indexed());assertThrows(IllegalStateException.class,()->e.mode(true));
            assertTrue(e.owns(SOURCE),"loaded chunk is processed despite missing earlier chunk");
            w.unloaded.clear();index(e,w);assertTrue(e.view().indexed());
        }
    }
    @Test void growthUsesAirCorridorStopsAtASealedWallAndResumesAfterOpening() throws Exception {
        World w=new World();w.wall(5,true);
        try(var j=new EcologyJournal(dir.resolve("barrier.jsonl"))){
            EcologyEngine e=ready(j,w);ticks(e,w,true,6000);
            assertTrue(e.cells().size()>8,"actual growth should occur");
            assertTrue(e.cells().stream().noneMatch(c->c.position().x()>5),"no far-side patch through sealed wall");
            w.wall(5,false);ticks(e,w,true,6000);
            assertTrue(e.cells().stream().anyMatch(c->c.position().x()>6),"an actual open passage allows exploration");
        }
    }
    @Test void buildingsAreNotProtectedAndWoodCanCarryVeins() throws Exception {
        World w=new World();for(int x=1;x<=12;x++)w.blocks.put(new Pos(x,0,0),WOOD);
        try(var j=new EcologyJournal(dir.resolve("wood.jsonl"))){
            EcologyEngine e=ready(j,w);ticks(e,w,true,3000);
            assertTrue(e.cells().stream().anyMatch(c->c.position().x()>2&&c.after().vein()));
            assertEquals(WOOD,w.get(new Pos(2,0,0)),"non-substrate material supports veins");
        }
    }
    @Test void pausedEmptyOrHeldPlayCannotAdvanceAndHasNoCatchup() throws Exception {
        World w=new World();try(var j=new EcologyJournal(dir.resolve("gating.jsonl"))){
            EcologyEngine e=ready(j,w);ticks(e,w,false,5000);assertEquals(0,w.writes);
            ticks(e,w,true,19);assertEquals(0,w.writes);
            ticks(e,w,false,2000);ticks(e,w,true,19);assertEquals(0,w.writes);
            ticks(e,w,true,1);assertEquals(1,w.writes);
            e.mode(false);ticks(e,w,true,1000);assertEquals(1,w.writes);
        }
    }
    @Test void severedBranchRemainsOwnedButCannotContinueGrowing() throws Exception {
        World w=new World();try(var j=new EcologyJournal(dir.resolve("cut.jsonl"))){
            EcologyEngine e=ready(j,w);ticks(e,w,true,3000);
            assertTrue(e.cells().stream().anyMatch(c->c.position().x()>6));
            // Sever every occupied surface at x=2. Rebuilding the wall is a separate player action.
            for(var c:e.cells())if(c.position().x()==2){w.blocks.put(c.position(),AIR);e.observed(c.position(),AIR);}
            w.wall(2,true);
            ticks(e,w,true,1000);
            assertTrue(e.cells().stream().anyMatch(c->c.position().x()>6),"detached remnants retained for later ending");
            assertTrue(e.view().connected()<e.view().owned());
            assertFalse(e.cells().stream().filter(c->c.position().x()>2).anyMatch(c->e.rooted(c.position())));
        }
    }
    @Test void unloadingNeverCountsAsDeathOrLoadsTerrain() throws Exception {
        World w=new World();try(var j=new EcologyJournal(dir.resolve("unload.jsonl"))){
            EcologyEngine e=ready(j,w);ticks(e,w,true,100);int size=e.cells().size(),writes=w.writes;
            w.unloaded.add(0);ticks(e,w,true,1000);
            assertTrue(e.view().unresolved());assertEquals(size,e.cells().size());assertEquals(writes,w.writes);
            w.unloaded.clear();ticks(e,w,true,200);
            for(int i=0;i<20&&e.view().unresolved();i++)ticks(e,w,true,1);
            assertFalse(e.view().unresolved());assertTrue(w.writes>writes);
        }
    }
    @Test void replayKeepsOriginalStatesAndDoesNotResurrectMissingIntent() throws Exception {
        World w=new World();Path file=dir.resolve("restart.jsonl");Pos grown;
        try(var j=new EcologyJournal(file)){
            EcologyEngine e=ready(j,w);ticks(e,w,true,200);
            var cell=e.cells().stream().filter(c->!c.adopted()&&c.before().equals(STONE)).findFirst().orElseThrow();grown=cell.position();assertEquals(STONE,cell.before());
        }
        // Simulate a crash before terrain persisted, then restart paused.
        w.blocks.put(grown,STONE);int writes=w.writes;
        try(var j=new EcologyJournal(file)){
            EcologyEngine e=new EcologyEngine(j);e.attach(PINS,AREA,HEART);ticks(e,w,false,100);
            assertEquals(STONE,w.get(grown));assertEquals(writes,w.writes);
            ticks(e,w,true,1);assertFalse(e.owns(grown));assertEquals(STONE,w.get(grown));
        }
    }
    @Test void replacementScuIkDoesNotRestorePrunedOwnership() throws Exception {
        World w=new World();try(var j=new EcologyJournal(dir.resolve("placement.jsonl"))){
            EcologyEngine e=ready(j,w);ticks(e,w,true,1);e.observed(SOURCE,AIR);w.blocks.put(SOURCE,SCULK);e.observed(SOURCE,SCULK);
            ticks(e,w,true,100);assertFalse(e.owns(SOURCE));assertTrue(e.view().connected()==0);
        }
    }
    @Test void journalFailurePreventsWorldWrites() throws Exception {
        World w=new World();EcologyJournal j=new EcologyJournal(dir.resolve("failure.jsonl"));EcologyEngine e=ready(j,w);j.close();
        assertThrows(java.io.IOException.class,()->ticks(e,w,true,20));assertEquals(0,w.writes);assertNotNull(e.failure());
    }
    @Test void invalidPublicTransitionDoesNotPoisonJournal() throws Exception {
        try(var j=new EcologyJournal(dir.resolve("invalid-transition.jsonl"))){
            EcologyEngine e=new EcologyEngine(j);assertThrows(IllegalArgumentException.class,()->e.mode(false));
            assertEquals(0,j.revision());assertDoesNotThrow(()->new EcologyEngine(j));
        }
    }
    @Test void forgedCompletionCannotArmAnUnscannedSourceAfterRestart() throws Exception {
        Path file=dir.resolve("false-complete.jsonl");
        try(var j=new EcologyJournal(file)){
            EcologyEngine e=new EcologyEngine(j);e.configure(PINS,AREA,HEART);j.append("INDEX_COMPLETE",new JsonObject());
        }
        try(var j=new EcologyJournal(file)){
            EcologyEngine e=new EcologyEngine(j);assertThrows(IllegalArgumentException.class,()->e.attach(PINS,AREA,HEART));
        }
    }
    @Test void perTickWorldReadsAndWritesRemainBounded() throws Exception {
        World w=new World();try(var j=new EcologyJournal(dir.resolve("budget.jsonl"))){
            EcologyEngine e=ready(j,w);
            for(int i=0;i<2000;i++){
                int before=w.writes;w.reads=0;e.beginTick();e.tick(w,true);
                assertTrue(w.reads<=4096,"read budget: "+w.reads);assertTrue(w.writes-before<=EcologyEngine.MAX_WRITES);
            }
        }
    }
    @Test void staleMapPinsAndUnsupportedPayloadCannotBeSilentlyAdopted() throws Exception {
        World w=new World();try(var j=new EcologyJournal(dir.resolve("pins.jsonl"))){
            EcologyEngine e=ready(j,w);assertThrows(IllegalStateException.class,()->e.attach(new EcologyEngine.Setup("city",2,"heart",1,"minecraft:overworld"),AREA,HEART));
            JsonObject p=new JsonObject();j.append("OBSERVE",p);assertThrows(IllegalArgumentException.class,()->new EcologyEngine(j));
        }
    }
    @Test void geometryFollowsFacesAndEdgesWithoutDiagonalFullBlockBridges() {
        Pos p=new Pos(0,0,0);
        assertTrue(EcologyTerrain.connected(p,SCULK,new Pos(1,0,0),SCULK));
        assertFalse(EcologyTerrain.connected(p,SCULK,new Pos(1,1,0),SCULK));
        assertTrue(EcologyTerrain.connected(p,SCULK,new Pos(1,1,0),vein(0)));
        assertTrue(EcologyTerrain.connected(new Pos(0,1,0),vein(0),new Pos(1,0,0),vein(4)));
        assertTrue(EcologyTerrain.connected(new Pos(0,1,0),vein(0),new Pos(1,1,0),vein(0)));
        assertFalse(EcologyTerrain.connected(new Pos(0,1,0),vein(1),new Pos(1,0,0),vein(5)));
    }
    private EcologyEngine ready(EcologyJournal j,World w) throws Exception {EcologyEngine e=new EcologyEngine(j);e.configure(PINS,AREA,HEART);index(e,w);e.mode(true);return e;}
    private void index(EcologyEngine e,World w) throws Exception {for(int i=0;i<200&&!e.view().indexed();i++)e.index(w);assertTrue(e.view().indexed());}
    private static void ticks(EcologyEngine e,World w,boolean active,int count)throws Exception {for(int i=0;i<count;i++){e.beginTick();e.tick(w,active);}}
    static class World implements EcologyTerrain {
        final Map<Pos,State> blocks=new HashMap<>();final Set<Integer> unloaded=new HashSet<>();int writes,reads;
        World(){for(int x=-1;x<=12;x++)for(int y=1;y<=2;y++)blocks.put(new Pos(x,y,0),AIR);blocks.put(SOURCE,SCULK);blocks.put(HEART,new State("minecraft:reinforced_deepslate",Map.of()));}
        void wall(int x,boolean closed){for(int y=1;y<=2;y++)blocks.put(new Pos(x,y,0),closed?STONE:AIR);}
        public boolean loaded(Pos p){return !unloaded.contains(p.x()>>4);}
        public State get(Pos p){reads++;assertTrue(loaded(p),"unloaded read at "+p);return blocks.getOrDefault(p,STONE);}
        public boolean solidFace(Pos p,int f){State s=get(p);return !s.air()&&!s.vein();}
        public boolean substrate(Pos p){return get(p).equals(STONE);}
        public int light(Pos p){get(p);return 0;}
        public boolean set(Pos p,State s){get(p);writes++;blocks.put(p,s);return true;}
    }
}
