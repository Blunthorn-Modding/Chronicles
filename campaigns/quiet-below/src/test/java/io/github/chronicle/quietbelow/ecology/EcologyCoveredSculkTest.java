package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologyCoveredSculkTest {
    private static final Pos HEART=new Pos(-1,1,1),CONTACT=new Pos(0,1,1),CENTER=new Pos(1,1,1);
    private static final State HEART_BLOCK=new State("minecraft:reinforced_deepslate",Map.of());
    private static final State STONE=new State("minecraft:stone",Map.of());
    @TempDir Path directory;

    @Test void fullyCoveredInteriorStaysNourishedByAnAdjacentOwnedRoot()throws Exception{
        var world=new World();
        try(var journal=new EcologyJournal(directory.resolve("covered.jsonl"))){
            var engine=start(journal,world);ticks(engine,world,1500);
            assertTrue(engine.rooted(CENTER));
            assertEquals(EcologyVitality.Mode.SHADED,engine.vitality().mode(CENTER));
            assertEquals(100,engine.vitality().percent(CENTER));
            assertTrue(engine.living(CENTER));
            assertEquals(0,world.writes,"nourishment must not create a growth route through solid cover");
        }
    }

    @Test void brightAirExposureTakesPriorityOverAdjacentRootNourishment()throws Exception{
        var world=new World();Pos opening=CENTER.add(1,0,0);
        world.blocks.put(opening,AIR);world.bright.add(opening);
        try(var journal=new EcologyJournal(directory.resolve("lit.jsonl"))){
            var engine=start(journal,world);ticks(engine,world,20);
            assertTrue(engine.rooted(CENTER));assertEquals(EcologyVitality.Mode.LIT,engine.vitality().mode(CENTER));
        }
    }

    @Test void coveredClusterDecaysWhenCutAndRecoversFromZeroAfterPhysicalReconnectionAndRestart()throws Exception{
        var world=new World();Path file=directory.resolve("reconnect.jsonl");int recovered;
        try(var journal=new EcologyJournal(file)){
            var engine=start(journal,world);
            world.blocks.put(CONTACT,STONE);engine.observed(CONTACT,STONE);ticks(engine,world,1500);
            assertFalse(engine.rooted(CENTER));assertEquals(EcologyVitality.Mode.DORMANT,engine.vitality().mode(CENTER));assertEquals(0,engine.vitality().percent(CENTER));
            world.blocks.put(CONTACT,SCULK);engine.noticeContact(CONTACT);ticks(engine,world,500);
            assertTrue(engine.rooted(CENTER));assertEquals(EcologyVitality.Mode.SHADED,engine.vitality().mode(CENTER));assertTrue(engine.living(CENTER));
            engine.flushVitality();recovered=engine.vitality().value(CENTER);
        }
        try(var journal=new EcologyJournal(file)){
            var engine=new EcologyEngine(journal);engine.attach(setup(),area(),HEART);
            assertEquals(recovered,engine.vitality().value(CENTER));ticks(engine,world,20);
            assertTrue(engine.rooted(CENTER));assertEquals(EcologyVitality.Mode.SHADED,engine.vitality().mode(CENTER));assertTrue(engine.living(CENTER));
        }
    }

    @Test void unownedAdjacentAndOwnedDiagonalFullSculkDoNotNourishASealedCell()throws Exception{
        Pos diagonal=new Pos(1,2,1);
        var world=new World(false);world.blocks.put(CONTACT,SCULK);world.blocks.put(new Pos(1,1,1),SCULK);
        try(var journal=new EcologyJournal(directory.resolve("unowned.jsonl"))){
            var engine=start(journal,world,new EcologyEngine.Area(HEART,CONTACT));ticks(engine,world,1);
            assertEquals(EcologyVitality.Mode.SEALED,engine.vitality().mode(CONTACT),"unowned sculk must not nourish before its legitimate physical adoption");
        }
        world=new World(false);world.blocks.put(CONTACT,SCULK);world.blocks.put(diagonal,SCULK);
        try(var journal=new EcologyJournal(directory.resolve("diagonal.jsonl"))){
            var engine=start(journal,world,new EcologyEngine.Area(HEART,diagonal));ticks(engine,world,1500);
            assertTrue(engine.owns(diagonal));assertFalse(engine.rooted(diagonal));
            assertEquals(EcologyVitality.Mode.SEALED,engine.vitality().mode(CONTACT));assertEquals(0,engine.vitality().percent(CONTACT));
        }
    }

    private static EcologyEngine start(EcologyJournal journal,World world)throws Exception{
        return start(journal,world,area());
    }
    private static EcologyEngine start(EcologyJournal journal,World world,EcologyEngine.Area area)throws Exception{
        var engine=new EcologyEngine(journal);
        engine.configure(setup(),area,HEART);
        while(!engine.view().indexed())engine.index(world);
        engine.tuneVitality(new EcologyVitality.Rules(1,1,0));engine.mode(true);return engine;
    }
    private static EcologyEngine.Setup setup(){return new EcologyEngine.Setup("city",1,"heart",1,"minecraft:overworld");}
    private static EcologyEngine.Area area(){return new EcologyEngine.Area(HEART,new Pos(2,2,2));}
    private static void ticks(EcologyEngine engine,EcologyTerrain world,int count)throws Exception{
        for(int tick=0;tick<count;tick++){engine.beginTick();engine.tick(world,true);}engine.flushVitality();
    }
    private static final class World implements EcologyTerrain{
        final Map<Pos,State> blocks=new HashMap<>();
        final Set<Pos> bright=new HashSet<>();
        int writes;
        World(){this(true);}
        World(boolean cube){blocks.put(HEART,HEART_BLOCK);if(cube)for(int x=0;x<3;x++)for(int y=0;y<3;y++)for(int z=0;z<3;z++)blocks.put(new Pos(x,y,z),SCULK);}
        public boolean loaded(Pos p){return true;}
        public State get(Pos p){return blocks.getOrDefault(p,STONE);}
        public boolean solidFace(Pos p,int face){return !get(p).air()&&!get(p).vein();}
        public boolean substrate(Pos p){return false;}
        public boolean growthExcluded(Pos p){return true;}
        public int light(Pos p){return bright.contains(p)?15:0;}
        public boolean set(Pos p,State state){writes++;blocks.put(p,state);return true;}
    }
}
