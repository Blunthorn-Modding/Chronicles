package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologySurfaceTest {
    @TempDir Path dir;
    static final Pos HEART=new Pos(0,1,2),SOURCE=new Pos(0,2,2);
    static final EcologyEngine.Setup PINS=new EcologyEngine.Setup("city",1,"heart",1,"minecraft:overworld");
    static final EcologyEngine.Area AREA=new EcologyEngine.Area(HEART,SOURCE);
    static final State STONE=new State("minecraft:stone",Map.of());
    @Test void exposedCubeGrowthTurnsOuterCornersWithoutExcavatingInterior()throws Exception {
        Cube w=new Cube();try(var j=new EcologyJournal(dir.resolve("cube.jsonl"))){
            var e=ready(j,w);ticks(e,w,8000);
            assertEquals(SCULK,w.get(new Pos(2,2,0)),"growth must turn onto the adjacent exposed face");
            assertEquals(SCULK,w.get(new Pos(4,2,2)),"growth must reach the opposite exposed face");
            assertEquals(SCULK,w.get(new Pos(2,4,2)),"growth must turn onto the top face");
            assertEquals(SCULK,w.get(new Pos(2,0,2)),"growth must turn underneath an exposed cube");
            assertEquals(STONE,w.get(new Pos(2,2,2)),"enclosed interior is not an exposed surface");
            assertNull(e.failure());
        }
    }
    @Test void anOldFlatFaceJournalRecoversMissingExposureWithoutResettingOwnership()throws Exception {
        Cube w=new Cube();Path file=dir.resolve("old-flat-face.jsonl");
        try(var j=new EcologyJournal(file)){
            ready(j,w);
            for(int y=0;y<5;y++)for(int z=0;z<5;z++){
                Pos p=new Pos(0,y,z);if(p.equals(HEART)||p.equals(SOURCE))continue;
                JsonObject payload=new JsonObject();payload.add("p",EcologyEngine.pos(p));payload.add("before",EcologyEngine.state(STONE));payload.add("after",EcologyEngine.state(SCULK));
                JsonArray front=new JsonArray();front.add(EcologyEngine.pos(new Pos(-1,y,z)));payload.add("air",front);j.append("GROW",payload);w.blocks.put(p,SCULK);
            }
        }
        try(var j=new EcologyJournal(file)){
            var e=new EcologyEngine(j);e.attach(PINS,AREA,HEART);int owned=e.view().owned();long bytes=j.bytes();
            for(int n=0;n<100;n++){e.beginTick();e.tick(w,false);}assertEquals(bytes,j.bytes(),"pause cannot discover exposure or grow");
            ticks(e,w,8000);assertTrue(e.view().owned()>owned);assertEquals(SCULK,w.get(new Pos(4,2,2)));
            assertTrue(j.entries().stream().anyMatch(entry->entry.type().equals("FRONTIER")));
            assertEquals(STONE,w.get(new Pos(2,2,2)));
        }
    }
    @Test void speedIsBoundedSavedAcrossRestartAndDoesNotSpendTimeWhilePaused()throws Exception {
        Cube w=new Cube();Path file=dir.resolve("speed.jsonl");
        try(var j=new EcologyJournal(file)){
            var e=ready(j,w);assertEquals(100,e.speedPercent());assertEquals(20,e.attemptTicks(false));assertEquals(10,e.attemptTicks(true));
            e.speed(25);assertEquals(80,e.attemptTicks(false));assertEquals(40,e.attemptTicks(true));
            e.speed(1600);assertEquals(1,e.attemptTicks(false));assertEquals(1,e.attemptTicks(true));
            long before=j.revision();assertThrows(IllegalArgumentException.class,()->e.speed(0));assertThrows(IllegalArgumentException.class,()->e.speed(123));assertEquals(before,j.revision());assertNull(e.failure());
            e.speed(200);
        }
        try(var j=new EcologyJournal(file)){
            var e=new EcologyEngine(j);e.attach(PINS,AREA,HEART);assertEquals(200,e.speedPercent());assertEquals(10,e.attemptTicks(false));
            for(int n=0;n<1000;n++){e.beginTick();e.tick(w,false);}assertEquals(1,e.view().owned());
            ticks(e,w,9);assertEquals(1,e.view().owned());ticks(e,w,1);assertEquals(2,e.view().owned());
        }
    }
    @Test void aConvertedWallDoesNotSeedTheFarSideOfASealedPassage()throws Exception {
        var w=new Cube(){
            {blocks.clear();blocks.put(new Pos(-1,0,0),new State("minecraft:reinforced_deepslate",Map.of()));blocks.put(new Pos(0,0,0),vein(0));blocks.put(new Pos(0,-1,0),STONE);blocks.put(new Pos(2,-1,0),new State("minecraft:oak_planks",Map.of()));}
            @Override public State get(Pos p){return blocks.getOrDefault(p,p.x()==1?STONE:AIR);}
        };
        Pos heart=new Pos(-1,0,0),source=new Pos(0,0,0);
        try(var j=new EcologyJournal(dir.resolve("thin-wall.jsonl"))){
            var e=new EcologyEngine(j);e.configure(PINS,new EcologyEngine.Area(heart,source),heart);
            for(int n=0;n<10&&!e.view().indexed();n++)e.index(w);e.mode(true);ticks(e,w,4000);
            assertTrue(e.cells().stream().anyMatch(c->c.position().x()==1),"near-side growth can coat a wall");
            assertTrue(e.cells().stream().noneMatch(c->c.position().x()>1),"a solid wall must not teleport a frontier to its far side");
        }
    }
    static EcologyEngine ready(EcologyJournal j,Cube w)throws Exception {
        var e=new EcologyEngine(j);e.configure(PINS,AREA,HEART);
        for(int n=0;n<10&&!e.view().indexed();n++)e.index(w);
        e.mode(true);return e;
    }
    static void ticks(EcologyEngine e,EcologyTerrain w,int count)throws Exception {for(int n=0;n<count;n++){e.beginTick();e.tick(w,true);}}
    static class Cube implements EcologyTerrain {
        final Map<Pos,State> blocks=new HashMap<>();
        Cube(){
            for(int x=0;x<5;x++)for(int y=0;y<5;y++)for(int z=0;z<5;z++)blocks.put(new Pos(x,y,z),STONE);
            blocks.put(HEART,new State("minecraft:reinforced_deepslate",Map.of()));blocks.put(SOURCE,SCULK);
        }
        public boolean loaded(Pos p){return true;}
        public State get(Pos p){return blocks.getOrDefault(p,AIR);}
        public boolean solidFace(Pos p,int f){State s=get(p);return !s.air()&&!s.vein();}
        public boolean substrate(Pos p){return get(p).equals(STONE);}
        public int light(Pos p){return 0;}
        public boolean set(Pos p,State s){blocks.put(p,s);return true;}
    }
}
