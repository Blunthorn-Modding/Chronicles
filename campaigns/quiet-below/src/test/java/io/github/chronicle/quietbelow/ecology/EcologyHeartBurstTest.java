package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.AIR;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.SCULK;
import static org.junit.jupiter.api.Assertions.*;

class EcologyHeartBurstTest {
    private static final EcologyTerrain.Pos HEART = new EcologyTerrain.Pos(0, 1, 0);
    private static final EcologyTerrain.Pos SOURCE = new EcologyTerrain.Pos(1, 1, 0);
    private static final EcologyTerrain.State HEART_STATE = new EcologyTerrain.State("quiet_below:relic_heart", Map.of());
    private static final EcologyTerrain.State STONE = new EcologyTerrain.State("minecraft:stone", Map.of());
    private static final EcologyEngine.Setup PINS = new EcologyEngine.Setup("city", 1, "heart", 1, "minecraft:overworld");
    private static final EcologyEngine.Area AREA = new EcologyEngine.Area(HEART, SOURCE);

    @TempDir Path dir;

    @Test void openFloorRapidlyReceivesAConnectedFiveByFiveFootprintAndReplaysExactly() throws Exception {
        Path file=dir.resolve("open-floor.jsonl");World world=flatWorld();
        java.util.List<EcologyEngine.Cell> expected;
        try(EcologyJournal journal=new EcologyJournal(file)){
            EcologyEngine engine=emptyEngine(journal,world);
            EcologyHeart.Transition transition=advance(engine,world,1000,true);
            assertTrue(engine.heartLastEffort(world,true,transition));
            drain(engine,world,40,true);

            Set<String> footprint=new HashSet<>(),expectedFootprint=new HashSet<>();
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)if(x!=0||z!=0)expectedFootprint.add(x+":"+z);
            for(EcologyEngine.Cell cell:engine.cells())if(Math.abs(cell.position().x())<=2&&Math.abs(cell.position().z())<=2){
                footprint.add(cell.position().x()+":"+cell.position().z());
                assertTrue(cell.after().network());
            }
            assertTrue(footprint.containsAll(expectedFootprint),"every eligible column in the 5x5 footprint must visibly receive sculk");
            assertTrue(world.maxWritesPerTick<=EcologyEngine.MAX_WRITES);
            assertEquals(world.writes,journal.entries().stream().filter(e->e.type().equals("GROW")).count());
            expected=engine.cells();
        }
        try(EcologyJournal replayedJournal=new EcologyJournal(file)){
            EcologyEngine replayed=new EcologyEngine(replayedJournal);replayed.attach(PINS,AREA,HEART);
            assertEquals(expected,replayed.cells(),"every burst cell and cleanup baseline must come from the journal");
        }
    }

    @Test void pauseSuspendsTheQueueAndRecoveryDoesNotAbortTheAlreadyAuthorizedBurst() throws Exception {
        World world=flatWorld();
        try(EcologyJournal journal=new EcologyJournal(dir.resolve("pause-recovery.jsonl"))){
            EcologyEngine engine=emptyEngine(journal,world);engine.tuneHeart(new EcologyHeart.Rules(1,60));
            EcologyHeart.Transition transition=advance(engine,world,1000,true);
            assertTrue(engine.heartLastEffort(world,true,transition));int initial=world.writes;

            drain(engine,world,12,false);
            assertEquals(initial,world.writes,"paused ticks must neither spend nor discard the burst");

            for(int tick=0;tick<40;tick++){
                world.beginTick();engine.beginTick();engine.tick(world,true);engine.heartTick(true,true);
            }
            assertEquals(0,engine.heartElapsed(),"the first connected cells recover the threshold-one network");
            assertTrue(engine.cells().size()>=24,"recovery must not truncate the one authorized last stand");
            assertTrue(world.maxWritesPerTick<=EcologyEngine.MAX_WRITES);
        }
    }

    @Test void sustainedSharedBudgetContentionDefersRatherThanErasesTheBurst() throws Exception {
        World world=flatWorld();
        try(EcologyJournal journal=new EcologyJournal(dir.resolve("saturated-burst.jsonl"))){
            EcologyEngine engine=emptyEngine(journal,world);var transition=advance(engine,world,1000,true);
            for(int n=0;n<EcologyEngine.MAX_WRITES;n++)assertTrue(engine.reserveNativeLoss());
            assertTrue(engine.heartLastEffort(world,true,transition));
            for(int tick=0;tick<80;tick++){
                world.beginTick();engine.beginTick();for(int n=0;n<EcologyEngine.MAX_WRITES;n++)assertTrue(engine.reserveNativeLoss());
                engine.tick(world,true);
            }
            assertEquals(0,world.writes,"saturated ticks cannot overrun the shared budget");
            drain(engine,world,40,true);
            Set<String> footprint=new HashSet<>();for(var cell:engine.cells())footprint.add(cell.position().x()+":"+cell.position().z());
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)if(x!=0||z!=0)assertTrue(footprint.contains(x+":"+z));
        }
    }

    @Test void exclusionsUnloadedCellsAndASeveredBurstNeverReceiveWrites() throws Exception {
        World world=flatWorld();
        for(int z=-2;z<=2;z++){
            world.excluded.add(new EcologyTerrain.Pos(2,0,z));
            world.unloaded.add(new EcologyTerrain.Pos(-2,0,z));
        }
        try(EcologyJournal journal=new EcologyJournal(dir.resolve("boundaries.jsonl"))){
            EcologyEngine engine=emptyEngine(journal,world);
            EcologyHeart.Transition transition=advance(engine,world,1000,true);
            assertTrue(engine.heartLastEffort(world,true,transition));
            int started=world.writes;

            for(EcologyEngine.Cell cell:java.util.List.copyOf(engine.cells())){
                world.blocks.put(cell.position(),STONE);engine.observed(cell.position(),STONE);
            }
            drain(engine,world,40,true);
            assertEquals(started,world.writes,"a real topology cut cancels queued authority");
            assertTrue(world.written.stream().noneMatch(p->p.x()==2||p.x()==-2));
            assertTrue(world.maxWritesPerTick<=EcologyEngine.MAX_WRITES);
        }
    }

    private static EcologyEngine emptyEngine(EcologyJournal journal,World world)throws Exception{
        EcologyEngine engine=new EcologyEngine(journal);engine.configure(PINS,AREA,HEART);
        for(int tick=0;tick<20&&!engine.view().indexed();tick++)engine.index(world);
        engine.mode(true);world.blocks.put(SOURCE,AIR);engine.observed(SOURCE,AIR);
        for(int tick=0;tick<20&&!engine.heartProof(true).complete();tick++){
            world.beginTick();engine.beginTick();engine.tick(world,true);
        }
        assertTrue(engine.heartProof(true).complete());assertEquals(0,engine.heartProof(true).living());return engine;
    }
    private static EcologyHeart.Transition advance(EcologyEngine engine,World world,int ticks,boolean playing)throws Exception{
        EcologyHeart.Transition transition=EcologyHeart.transition(engine.heartRules(),engine.heartElapsed(),engine.heartElapsed());
        for(int tick=0;tick<ticks;tick++){
            world.beginTick();engine.beginTick();engine.tick(world,playing);transition=engine.heartTick(true,playing);
        }
        return transition;
    }
    private static void drain(EcologyEngine engine,World world,int ticks,boolean playing)throws Exception{
        for(int tick=0;tick<ticks;tick++){
            world.beginTick();engine.beginTick();engine.tick(world,playing);
        }
    }
    private static World flatWorld(){
        World world=new World();
        for(int x=-2;x<=2;x++)for(int y=-2;y<=4;y++)for(int z=-2;z<=2;z++)world.blocks.put(new EcologyTerrain.Pos(x,y,z),y<=0?STONE:AIR);
        world.blocks.put(HEART,HEART_STATE);world.blocks.put(SOURCE,SCULK);return world;
    }

    private static final class World implements EcologyTerrain{
        private final Map<Pos,State> blocks=new HashMap<>();
        private final Set<Pos> unloaded=new HashSet<>(),excluded=new HashSet<>(),written=new HashSet<>();
        private int writes,writesThisTick,maxWritesPerTick;
        void beginTick(){writesThisTick=0;}
        @Override public boolean loaded(Pos pos){return !unloaded.contains(pos);}
        @Override public State get(Pos pos){assertTrue(loaded(pos),"unloaded terrain read at "+pos);return blocks.getOrDefault(pos,STONE);}
        @Override public boolean solidFace(Pos pos,int face){State state=get(pos);return !state.air()&&!state.vein();}
        @Override public boolean substrate(Pos pos){return get(pos).equals(STONE);}
        @Override public boolean growthExcluded(Pos pos){return excluded.contains(pos);}
        @Override public int light(Pos pos){get(pos);return 0;}
        @Override public boolean set(Pos pos,State state){get(pos);writes++;writesThisTick++;maxWritesPerTick=Math.max(maxWritesPerTick,writesThisTick);written.add(pos);blocks.put(pos,state);return true;}
    }
}
