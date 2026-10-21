package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologySpreadTest {
    @TempDir Path dir;
    private static final Pos HEART=new Pos(-1,0,0),SOURCE=new Pos(0,0,0);
    private static final EcologyEngine.Setup PINS=new EcologyEngine.Setup("city",1,"heart",1,"minecraft:overworld");
    private static final EcologyEngine.Area AREA=new EcologyEngine.Area(HEART,SOURCE);

    @Test void fixedAndCountModesUseTheBoundedFormulaAndCap(){
        var fixed=new EcologyEngine.SpreadRules(EcologyEngine.GrowthMode.FIXED,100);
        var counted=new EcologyEngine.SpreadRules(EcologyEngine.GrowthMode.SCULK_COUNT,10);
        assertEquals(100,EcologyEngine.effectiveSpeed(100,20_000,fixed));
        assertEquals(110,EcologyEngine.effectiveSpeed(100,1_000,counted));
        assertEquals(1600,EcologyEngine.effectiveSpeed(1600,1_000,counted));
        assertEquals(1600,EcologyEngine.effectiveSpeed(100,2_000_000,counted));
        assertThrows(IllegalArgumentException.class,()->EcologyEngine.effectiveSpeed(99,0,counted));
        assertThrows(IllegalArgumentException.class,()->new EcologyEngine.SpreadRules(EcologyEngine.GrowthMode.SCULK_COUNT,3));
    }

    @Test void savedModesRestartAndConfigurationEdgesResetCadence()throws Exception {
        Path file=dir.resolve("spread.jsonl");World world=new World();
        try(var journal=new EcologyJournal(file)){
            EcologyEngine engine=ready(journal);engine.speed(200);engine.tuneSpread(new EcologyEngine.SpreadRules(EcologyEngine.GrowthMode.SCULK_COUNT,25));
            assertEquals(200,engine.effectiveSpeedPercent());assertEquals(10,engine.attemptTicks(false));
            for(int n=0;n<9;n++){engine.beginTick();engine.tick(world,true);}assertEquals(0,world.writes);
            engine.tuneSpread(EcologyEngine.DEFAULT_SPREAD);
            for(int n=0;n<9;n++){engine.beginTick();engine.tick(world,true);}assertEquals(0,world.writes,"mode changes must not bank a near-ready growth clock");
        }
        try(var journal=new EcologyJournal(file)){
            EcologyEngine replay=new EcologyEngine(journal);replay.attach(PINS,AREA,HEART);
            assertEquals(EcologyEngine.DEFAULT_SPREAD,replay.spreadRules());assertEquals(200,replay.speedPercent());
        }
    }

    @Test void malformedAndPrematureSpreadRulesFailBeforeMutation()throws Exception {
        Path file=dir.resolve("rules.jsonl");try(var journal=new EcologyJournal(file)){
            EcologyEngine engine=new EcologyEngine(journal);assertThrows(IllegalArgumentException.class,()->engine.tuneSpread(EcologyEngine.DEFAULT_SPREAD));assertEquals(0,journal.revision());ready(journal);
        }
        for(String fault:java.util.List.of("mode","amplification","extra","missing")){
            Path altered=dir.resolve("rules-"+fault+".jsonl");
            try(var source=new EcologyJournal(file);var journal=new EcologyJournal(altered)){
                for(EcologyJournal.Entry entry:source.entries())journal.append(entry.type(),entry.payload());
                JsonObject p=EcologyEngine.spreadRulesPayload(new EcologyEngine.SpreadRules(EcologyEngine.GrowthMode.SCULK_COUNT,10));
                if(fault.equals("mode"))p.addProperty("mode","FUTURE");
                if(fault.equals("amplification"))p.addProperty("amplificationPercent",3);
                if(fault.equals("extra"))p.addProperty("extra",true);
                if(fault.equals("missing"))p.remove("mode");
                journal.append("SPREAD_RULES",p);
            }
            assertThrows(IllegalArgumentException.class,()->{try(var journal=new EcologyJournal(altered)){new EcologyEngine(journal);}},fault);
        }
    }

    private EcologyEngine ready(EcologyJournal journal)throws Exception {EcologyEngine engine=new EcologyEngine(journal);engine.configure(PINS,AREA,HEART);World world=new World();while(!engine.view().indexed())engine.index(world);engine.mode(true);return engine;}
    private static final class World implements EcologyTerrain {
        final Map<Pos,State> blocks=new java.util.HashMap<>(Map.of(SOURCE,SCULK));int writes;
        public boolean loaded(Pos p){return true;}public State get(Pos p){return blocks.getOrDefault(p,p.y()==0?new State("minecraft:stone",Map.of()):AIR);}
        public boolean solidFace(Pos p,int face){return !get(p).air()&&!get(p).vein();}public boolean substrate(Pos p){return get(p).id().equals("minecraft:stone");}
        public int light(Pos p){return 0;}public boolean set(Pos p,State state){writes++;blocks.put(p,state);return true;}
    }
}
