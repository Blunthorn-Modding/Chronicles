package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologyEngineTest {
    private static long mutationIntents(EcologyJournal j){return j.entries().stream().filter(e->Set.of("GROW","CONNECT","PURGE").contains(e.type())).count();}
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
    @Test void redundantMiningRetainsCertifiedRootsWithoutWalkingTheWholeNetwork() throws Exception {
        World w=new World();for(int x=0;x<16;x++)for(int z=0;z<40;z++)w.blocks.put(new Pos(x,0,z),SCULK);
        try(var j=new EcologyJournal(dir.resolve("redundant-mining.jsonl"))){
            EcologyEngine e=new EcologyEngine(j);
            e.configure(PINS,new EcologyEngine.Area(HEART,new Pos(15,0,39)),HEART);index(e,w);e.mode(true);ticks(e,w,true,10);
            assertTrue(e.rooted(SOURCE));assertTrue(e.living(SOURCE));
            Pos mined=new Pos(10,0,10);int reads=w.reads;
            w.blocks.put(mined,AIR);e.observed(mined,AIR);
            assertFalse(e.owns(mined));assertFalse(e.rooted(mined));
            assertTrue(e.rooted(SOURCE),"an unrelated redundant removal invalidated the whole network");
            assertTrue(e.living(new Pos(15,0,39)),"a distant intact root path needs no new global walk");
            assertEquals(reads,w.reads,"local topology proof must use certified metadata, not terrain reads");
            e.chunkUnloaded(0,0);Pos another=new Pos(11,0,10);w.blocks.put(another,AIR);e.observed(another,AIR);
            assertFalse(e.rooted(SOURCE),"an edit must never clear an unload's uncertainty");
        }
    }
    @Test void realBridgeRemovalStillInvalidatesTheDetachedSideImmediately() throws Exception {
        World w=new World();for(int x=0;x<=12;x++)w.blocks.put(new Pos(x,0,0),SCULK);
        try(var j=new EcologyJournal(dir.resolve("bridge-mining.jsonl"))){
            EcologyEngine e=new EcologyEngine(j);e.configure(PINS,new EcologyEngine.Area(HEART,new Pos(12,0,0)),HEART);
            index(e,w);e.mode(true);ticks(e,w,true,4);assertTrue(e.living(new Pos(12,0,0)));
            Pos bridge=new Pos(5,0,0);w.blocks.put(bridge,AIR);e.observed(bridge,AIR);
            assertFalse(e.living(new Pos(12,0,0)),"last known connectivity must not bless a cut branch");
            ticks(e,w,true,3);assertTrue(e.living(SOURCE));assertFalse(e.living(new Pos(12,0,0)));
        }
    }
    @Test void faceLossRetainsOnlyActuallyConnectedVeins() throws Exception {
        World w=new World();Pos vein=SOURCE.add(0,1,0);
        State before=EcologyTerrain.vein(0).with("south","true");w.blocks.put(vein,before);
        try(var j=new EcologyJournal(dir.resolve("vein-loss.jsonl"))){
            EcologyEngine e=ready(j,w);ticks(e,w,true,4);assertTrue(e.living(vein));
            State after=before.with("south","false");w.blocks.put(vein,after);e.observed(vein,after);
            assertTrue(e.living(vein));assertTrue(e.living(SOURCE));
        }
        World only=new World();State two=EcologyTerrain.vein(4).with("east","true");only.blocks.put(SOURCE,two);
        try(var j=new EcologyJournal(dir.resolve("last-vein-face.jsonl"))){
            EcologyEngine e=ready(j,only);ticks(e,only,true,4);assertTrue(e.living(SOURCE));
            State after=two.with("west","false");only.blocks.put(SOURCE,after);e.observed(SOURCE,after);
            assertFalse(e.living(SOURCE),"vein retained certification after losing its only root-facing face");
        }
    }
    @Test void restartDoesNotReviveAPreDeletionExtentWhileVitalityIsStillBeingAssessed() throws Exception {
        World w=new World();for(int x=0;x<8;x++)for(int z=0;z<8;z++)w.blocks.put(new Pos(x,0,z),SCULK);
        for(int x=100;x<150;x++)for(int z=100;z<110;z++)w.blocks.put(new Pos(x,0,z),SCULK);
        var area=new EcologyEngine.Area(HEART,new Pos(149,0,109));Path file=dir.resolve("mining-stage-restart.jsonl");
        try(var j=new EcologyJournal(file)){
            EcologyEngine e=new EcologyEngine(j);e.configure(PINS,area,HEART);index(e,w);
            e.tuneVitality(new EcologyVitality.Rules(10,5,0));e.mode(true);ticks(e,w,true,20);assertTrue(e.costsAvailable());
            for(int x=7;x>=0;x--){Pos at=new Pos(x,0,7);w.blocks.put(at,AIR);e.observed(at,AIR);}
            Pos at=new Pos(7,0,6);w.blocks.put(at,AIR);e.observed(at,AIR);assertFalse(e.costsAvailable());e.flushVitality();
        }
        try(var j=new EcologyJournal(file)){
            EcologyEngine e=new EcologyEngine(j);e.attach(PINS,area,HEART);
            ticks(e,w,true,5);assertTrue(e.rooted(SOURCE));assertTrue(e.vitalityPending());
            assertFalse(e.costsAvailable(),"persisted old extent authorized effects before a fresh bounded assessment");
            ticks(e,w,true,10);assertFalse(e.costsAvailable());assertFalse(e.extensive());
        }
    }
    @Test void nativeDescendantsRespectExcludedTargetsAndNewFaceBackings() throws Exception {
        for(int blocked=0;blocked<3;blocked++){
            World w=new World();Pos target=SOURCE.offset(1);
            try(var j=new EcologyJournal(dir.resolve("native-exclusion-"+blocked+".jsonl"))){
                EcologyEngine e=ready(j,w);ticks(e,w,true,1);
                if(blocked==1)w.excluded.add(SOURCE);if(blocked==2)w.excluded.add(target);
                long revision=j.revision();
                assertEquals(blocked==0,e.nativeChange(w,SOURCE,target,AIR,EcologyTerrain.vein(0)));
                if(blocked!=0)assertEquals(revision,j.revision(),"excluded native proposal was journaled");
            }
        }
    }
    @Test void certifiedMiningCannotRetainAnExtentBelowItsExitThreshold() throws Exception {
        World w=new World();for(int x=0;x<16;x++)for(int z=0;z<32;z++)w.blocks.put(new Pos(x,0,z),SCULK);
        try(var j=new EcologyJournal(dir.resolve("mining-extent.jsonl"))){
            EcologyEngine e=new EcologyEngine(j);e.configure(PINS,new EcologyEngine.Area(HEART,new Pos(15,0,31)),HEART);
            index(e,w);e.tuneVitality(new EcologyVitality.Rules(10,5,0));e.mode(true);ticks(e,w,true,20);
            assertTrue(e.extensive());assertTrue(e.costsAvailable());int remaining=512;
            for(int z=31;z>=0;z--)for(int x=15;x>=0;x--){
                if(remaining==55)return;
                Pos mined=new Pos(x,0,z);w.blocks.put(mined,AIR);e.observed(mined,AIR);remaining--;
                assertTrue(e.rooted(SOURCE),"peeling redundant edges should keep the remaining root certificate");
                if(remaining==448)assertTrue(e.extensive());
                if(remaining==447)assertFalse(e.extensive(),"stale summary retained Darkness below the Extensive exit threshold");
                if(remaining==56)assertTrue(e.costsAvailable());
                if(remaining==55)assertFalse(e.costsAvailable(),"stale summary retained Hunger below the Established exit threshold");
            }
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
    @Test void exclusionsPreventReplacementAndVeinAttachmentWithoutProtectingOtherSurfaces() throws Exception {
        World w=new World();
        for(int x=1;x<=12;x++){
            Pos floor=new Pos(x,0,0);w.excluded.add(floor);
            if(x%2==0)w.blocks.put(floor,WOOD);
        }
        try(var j=new EcologyJournal(dir.resolve("excluded.jsonl"))){
            EcologyEngine e=ready(j,w);ticks(e,w,true,3000);
            assertTrue(e.cells().size()>8,"other ordinary cave surfaces remain available");
            for(Pos p:w.excluded)assertEquals(p.x()%2==0?WOOD:STONE,w.get(p));
            for(var c:e.cells())if(c.after().vein())for(int f=0;f<6;f++)
                if(c.after().face(f))assertFalse(w.excluded.contains(c.position().offset(f)),"vein climbed an excluded backing block");
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
    @Test void replacementSculkRestoresAPrunedPhysicalConnection() throws Exception {
        World w=new World();try(var j=new EcologyJournal(dir.resolve("placement.jsonl"))){
            EcologyEngine e=ready(j,w);ticks(e,w,true,300);
            for(var c:e.cells())if(EcologyTerrain.connected(HEART,SCULK,c.position(),c.after())){w.blocks.put(c.position(),AIR);e.observed(c.position(),AIR);}
            ticks(e,w,true,20);assertEquals(0,e.view().connected());
            w.blocks.put(SOURCE,SCULK);e.observed(SOURCE,SCULK);
            ticks(e,w,false,100);assertFalse(e.owns(SOURCE),"paused play cannot reconnect");
            ticks(e,w,true,100);assertTrue(e.owns(SOURCE));assertTrue(e.rooted(SOURCE));
            assertTrue(e.view().connected()>1,"restored bridge wakes the retained branch");
            assertTrue(j.entries().stream().anyMatch(entry->entry.type().equals("CONNECT")));
        }
    }
    @Test void joinsOnlyPhysicalContactAndPreservesItAcrossReplay() throws Exception {
        World w=new World();Path file=dir.resolve("join-replay.jsonl");Pos bridge=new Pos(1,0,0),remote=new Pos(10,0,0),corner=new Pos(-2,1,1);
        try(var j=new EcologyJournal(file)){
            EcologyEngine e=ready(j,w);ticks(e,w,true,1);
            w.blocks.put(bridge,SCULK);w.blocks.put(remote,SCULK);w.blocks.put(corner,SCULK);
            e.mode(false);ticks(e,w,true,100);assertFalse(e.owns(bridge));
            e.mode(true);ticks(e,w,true,2);
            assertTrue(e.owns(bridge));assertFalse(e.owns(remote));assertFalse(e.owns(corner));
            assertEquals(AIR,e.cells().stream().filter(c->c.position().equals(bridge)).findFirst().orElseThrow().before());
        }
        try(var j=new EcologyJournal(file)){
            EcologyEngine e=new EcologyEngine(j);e.attach(PINS,AREA,HEART);ticks(e,w,true,2);
            assertTrue(e.rooted(bridge));assertFalse(e.owns(remote));
        }
    }
    @Test void reconnectionDoesNotReadUnloadedBlocksOrJoinDuringAnUnknownNetwork() throws Exception {
        World w=new World();try(var j=new EcologyJournal(dir.resolve("join-unloaded.jsonl"))){
            EcologyEngine e=ready(j,w);ticks(e,w,true,1);Pos bridge=new Pos(1,0,0);w.blocks.put(bridge,SCULK);
            w.unloaded.add(0);ticks(e,w,true,20);assertFalse(e.owns(bridge));
            w.unloaded.clear();ticks(e,w,true,10);assertTrue(e.rooted(bridge));
        }
    }
    @Test void physicalJoinsShareTheNativeBudgetAndRebuildAfterRootAvailabilityChanges() throws Exception {
        World w=new World();try(var j=new EcologyJournal(dir.resolve("join-budget.jsonl"))){
            EcologyEngine e=ready(j,w);ticks(e,w,true,1);
            for(int x=0;x<8;x++)for(int y=0;y<4;y++)for(int z=-2;z<3;z++)w.blocks.put(new Pos(x,y,z),SCULK);
            long before=mutationIntents(j);e.beginTick();
            for(int i=0;i<EcologyEngine.MAX_WRITES;i++)assertTrue(e.reserveNativeLoss());
            e.tick(w,true);assertEquals(before,mutationIntents(j),"joins cannot bypass a busy native mutation budget");
            for(int i=0;i<30;i++){
                before=mutationIntents(j);e.beginTick();w.reads=0;e.tick(w,true);
                assertTrue(mutationIntents(j)-before<=EcologyEngine.MAX_WRITES);assertTrue(w.reads<=4096);
            }
            assertTrue(e.view().owned()>1);e.invalidateConnections();assertFalse(e.rooted(SOURCE));
            ticks(e,w,true,20);assertTrue(e.rooted(SOURCE));assertNull(e.failure());
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
    @Test void addedGrowthKeepsCertifiedNativeFeaturesActiveButCutsInvalidateImmediately() throws Exception {
        World w=new World();try(var j=new EcologyJournal(dir.resolve("stable-native.jsonl"))){
            EcologyEngine e=ready(j,w);ticks(e,w,true,1);assertTrue(e.rooted(SOURCE));
            Pos target=SOURCE.offset(1);State sensor=new State("minecraft:sculk_sensor",Map.of("power","0","sculk_sensor_phase","inactive","waterlogged","false"));
            assertTrue(e.nativeChange(w,SOURCE,target,AIR,sensor));w.blocks.put(target,sensor);
            assertTrue(e.rooted(SOURCE),"adding a surface must not drop an active vanilla warning/response");
            e.observed(SOURCE,AIR);assertFalse(e.rooted(SOURCE),"a real cut invalidates certified connectivity immediately");
        }
    }
    @Test void initialCertificationWaitsForTheEntireFirstValidationAndThenResolves() throws Exception {
        World w=new World();for(int x=10;x<=29;x++)for(int y=0;y<=15;y++)w.blocks.put(new Pos(x,y,0),SCULK);
        var area=new EcologyEngine.Area(new Pos(-1,0,0),new Pos(29,15,0));
        try(var j=new EcologyJournal(dir.resolve("first-validation.jsonl"))){
            EcologyEngine e=new EcologyEngine(j);e.configure(PINS,area,HEART);index(e,w);e.mode(true);
            ticks(e,w,true,1);assertTrue(e.view().unresolved());assertFalse(e.rooted(SOURCE));
            ticks(e,w,true,10);assertTrue(e.rooted(SOURCE),"completion must trigger certification rather than strand a small rooted component");
        }
    }
    private EcologyEngine ready(EcologyJournal j,World w) throws Exception {EcologyEngine e=new EcologyEngine(j);e.configure(PINS,AREA,HEART);index(e,w);e.mode(true);return e;}
    private void index(EcologyEngine e,World w) throws Exception {for(int i=0;i<200&&!e.view().indexed();i++)e.index(w);assertTrue(e.view().indexed());}
    private static void ticks(EcologyEngine e,World w,boolean active,int count)throws Exception {for(int i=0;i<count;i++){e.beginTick();e.tick(w,active);}}
    static class World implements EcologyTerrain {
        final Map<Pos,State> blocks=new HashMap<>();final Set<Integer> unloaded=new HashSet<>();final Set<Pos> excluded=new HashSet<>();int writes,reads;
        World(){for(int x=-1;x<=12;x++)for(int y=1;y<=2;y++)blocks.put(new Pos(x,y,0),AIR);blocks.put(SOURCE,SCULK);blocks.put(HEART,new State("minecraft:reinforced_deepslate",Map.of()));}
        void wall(int x,boolean closed){for(int y=1;y<=2;y++)blocks.put(new Pos(x,y,0),closed?STONE:AIR);}
        public boolean loaded(Pos p){return !unloaded.contains(p.x()>>4);}
        public State get(Pos p){reads++;assertTrue(loaded(p),"unloaded read at "+p);return blocks.getOrDefault(p,STONE);}
        public boolean solidFace(Pos p,int f){State s=get(p);return !s.air()&&!s.vein();}
        public boolean substrate(Pos p){return get(p).equals(STONE);}
        public boolean growthExcluded(Pos p){return excluded.contains(p);}
        public int light(Pos p){get(p);return 0;}
        public boolean set(Pos p,State s){get(p);writes++;blocks.put(p,s);return true;}
    }
}
