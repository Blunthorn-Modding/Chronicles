package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologyStationFeedingTest {
    private static final Pos HEART = new Pos(-1,0,0), SUPPORT = new Pos(0,0,0), STATION = new Pos(0,1,0);
    private static final State STONE = new State("minecraft:stone", Map.of());
    @TempDir Path directory;
    static class World implements EcologyTerrain {
        final Map<Pos,State> blocks = new java.util.HashMap<>();
        final Set<Pos> hidden = new HashSet<>();
        World(String station) {
            blocks.put(HEART, new State("minecraft:reinforced_deepslate",Map.of()));
            for (int x=0;x<64;x++) { blocks.put(new Pos(x,0,0),SCULK); if(x>0) blocks.put(new Pos(x,1,0),AIR); }
            blocks.put(STATION,new State(station,Map.of()));
            blocks.put(STATION.add(0,0,1),AIR);
        }
        public boolean loaded(Pos p){return !hidden.contains(p);}
        public State get(Pos p){assertTrue(loaded(p),"unloaded read: "+p);return blocks.getOrDefault(p,STONE);}
        public boolean solidFace(Pos p,int f){return !get(p).air()&&!get(p).vein();}
        public boolean substrate(Pos p){return false;}
        public boolean growthExcluded(Pos p){return true;}
        public boolean feedingPassage(Pos p){return Set.of("quiet_below:ancient_forge","quiet_below:ancient_altar").contains(get(p).id());}
        public int light(Pos p){get(p);return 15;}
        public boolean set(Pos p,State state){blocks.put(p,state);return true;}
    }
    private EcologyEngine start(EcologyJournal journal,World world)throws Exception{
        var e=new EcologyEngine(journal);
        e.configure(new EcologyEngine.Setup("city",1,"heart",1,"minecraft:overworld"),new EcologyEngine.Area(HEART,new Pos(63,0,0)),HEART);
        while(!e.view().indexed())e.index(world);
        e.tuneVitality(new EcologyVitality.Rules(1,1,0));e.mode(true);return e;
    }
    private static void ticks(EcologyEngine e,World w,int n)throws Exception{for(int i=0;i<n;i++){e.beginTick();e.tick(w,true);}e.flushVitality();}
    @Test void exposedForgeAndAltarDoNotStarveTheirOnlyLivingSupport()throws Exception{
        for(String station:ListHolder.STATIONS){
            var w=new World(station);
            try(var j=new EcologyJournal(directory.resolve(station.substring(station.indexOf(':')+1)+".jsonl"))){
                var e=start(j,w);ticks(e,w,1500);
                assertEquals(EcologyVitality.Mode.SHADED,e.vitality().mode(SUPPORT));
                assertEquals(100,e.vitality().percent(SUPPORT));assertTrue(e.living(SUPPORT));assertEquals(2,e.forgeHeat());
            }
        }
    }
    @Test void coveredForgeAndAltarSupportStayNourishedByAnAdjacentOwnedRoot()throws Exception{
        for(String station:ListHolder.STATIONS){
            var w=new World(station);
            for(int[] d:DIRECTIONS)if(!STATION.add(d[0],d[1],d[2]).equals(SUPPORT))w.blocks.put(STATION.add(d[0],d[1],d[2]),STONE);
            try(var j=new EcologyJournal(directory.resolve("covered-"+station.substring(station.indexOf(':')+1)+".jsonl"))){
                var e=start(j,w);ticks(e,w,1500);assertEquals(EcologyVitality.Mode.SHADED,e.vitality().mode(SUPPORT));assertEquals(100,e.vitality().percent(SUPPORT));assertTrue(e.living(SUPPORT));
            }
        }
    }
    @Test void enclosedStationAndOrdinarySolidBlocksStillSealAnIsolatedSupport()throws Exception{
        for(String station:new String[]{"quiet_below:ancient_forge","minecraft:stone"}){
            var w=new World(station);isolateSupport(w);
            if(station.startsWith("quiet_below:"))for(int[] d:DIRECTIONS)if(!STATION.add(d[0],d[1],d[2]).equals(SUPPORT))w.blocks.put(STATION.add(d[0],d[1],d[2]),STONE);
            try(var j=new EcologyJournal(directory.resolve("sealed-"+station.substring(station.indexOf(':')+1)+".jsonl"))){
                var e=start(j,w);ticks(e,w,1500);assertEquals(EcologyVitality.Mode.SEALED,e.vitality().mode(SUPPORT));assertEquals(0,e.vitality().percent(SUPPORT));assertFalse(e.living(SUPPORT));
            }
        }
    }
    @Test void unloadedStationOutletsAreUnknownAndNeverReadOrTreatedAsAir()throws Exception{
        var w=new World("quiet_below:ancient_forge");isolateSupport(w);
        for(int[] d:DIRECTIONS){Pos p=STATION.add(d[0],d[1],d[2]);if(!p.equals(SUPPORT)){w.blocks.put(p,STONE);w.hidden.add(p);}}
        // Keep all owned cells loaded; only the station's non-network outlet cells are hidden.
        try(var j=new EcologyJournal(directory.resolve("unknown.jsonl"))){var e=start(j,w);ticks(e,w,50);assertEquals(EcologyVitality.Mode.UNKNOWN,e.vitality().mode(SUPPORT));assertEquals(0,e.forgeHeat());}
    }
    @Test void exhaustedIsolatedSupportRecoversThroughAnExposedStation()throws Exception{
        var w=new World("quiet_below:ancient_forge");isolateSupport(w);
        for(int[] d:DIRECTIONS){Pos p=STATION.add(d[0],d[1],d[2]);if(!p.equals(SUPPORT))w.blocks.put(p,STONE);}
        try(var j=new EcologyJournal(directory.resolve("recover.jsonl"))){
            var e=start(j,w);ticks(e,w,1500);assertFalse(e.living(SUPPORT));
            w.blocks.put(STATION.add(0,0,1),AIR);ticks(e,w,400);assertEquals(EcologyVitality.Mode.SHADED,e.vitality().mode(SUPPORT));assertTrue(e.living(SUPPORT));
        }
    }
    private static void isolateSupport(World world){for(int x=1;x<64;x++)world.blocks.put(new Pos(x,0,0),STONE);}
    private static class ListHolder { static final String[] STATIONS={"quiet_below:ancient_forge","quiet_below:ancient_altar"}; }
}
