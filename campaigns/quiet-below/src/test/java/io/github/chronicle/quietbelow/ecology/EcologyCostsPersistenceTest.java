package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonObject;
import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.campaign.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologyCostsPersistenceTest {
    @TempDir Path dir;
    private static final EcologyEngine.Setup PINS=new EcologyEngine.Setup("city",1,"heart",2,"minecraft:overworld");
    private static final EcologyEngine.Area AREA=new EcologyEngine.Area(new Pos(-1,0,0),new Pos(3,0,0));
    private static final Pos HEART=new Pos(-1,0,0),SOURCE=new Pos(0,0,0);
    private static final EcologyCosts.Rules CUSTOM=new EcologyCosts.Rules(new EcologyCosts.Rule(false,60),new EcologyCosts.Rule(true,120));

    @Test void strictCostRulesReplayAndCheckpointMaintenancePreserveBothRules()throws Exception {
        Path world=world("roundtrip"),source=file(world);EcologyEngine old;
        try(var journal=new EcologyJournal(source)){
            old=prepared(journal);costHistory(old);
            EcologyJournal.Entry entry=journal.entries().getLast();assertEquals("COST_RULES",entry.type());assertEquals(Set.of("hunger","darkness"),entry.payload().keySet());
            assertEquals(Set.of("enabled","seconds"),entry.payload().getAsJsonObject("hunger").keySet());
            assertFalse(entry.payload().has("effects"));
            old.speed(200);old.tuneSpread(new EcologyEngine.SpreadRules(EcologyEngine.GrowthMode.SCULK_COUNT,25));
        }
        assertEquals(CUSTOM,load(source).costRules());
        assertEquals(new EcologyEngine.SpreadRules(EcologyEngine.GrowthMode.SCULK_COUNT,25),load(source).spreadRules());
        assertEquals(200,load(source).speedPercent());
        assertTrue(EcologyMaintenance.compact(world,true).applied());EcologyEngine compacted=load(source);
        assertEquals(CUSTOM,compacted.costRules());assertTrue(old.sameCheckpointState(compacted));
        assertEquals(new EcologyEngine.SpreadRules(EcologyEngine.GrowthMode.SCULK_COUNT,25),compacted.spreadRules());
        assertEquals(200,compacted.speedPercent());
        try(var journal=new EcologyJournal(source)){
            JsonObject header=journal.entries().stream().filter(e->e.type().equals("CHECKPOINT_BEGIN")).findFirst().orElseThrow().payload();
            assertEquals(4,header.get("format").getAsInt());assertEquals(Set.of("hunger","darkness"),header.getAsJsonObject("costs").keySet());assertEquals(Set.of("mode","amplificationPercent"),header.getAsJsonObject("spread").keySet());
            assertFalse(header.has("effects"));
        }
    }

    @Test void legacyCheckpointsDefaultCostsAndSpread()throws Exception {
        Path world=world("legacy"),source=file(world);try(var journal=new EcologyJournal(source)){costHistory(prepared(journal));}EcologyMaintenance.compact(world,true);
        List<EcologyJournal.Entry> entries;try(var journal=new EcologyJournal(source)){entries=journal.entries();}
        for(int format:List.of(1,2,3)){
            Path legacy=dir.resolve("format"+format+".jsonl");try(var journal=new EcologyJournal(legacy)){
                for(EcologyJournal.Entry entry:entries){JsonObject p=entry.payload();if(entry.type().equals("CHECKPOINT_BEGIN")){p.addProperty("format",format);p.remove("spread");if(format<3)p.remove("costs");if(format==1)p.remove("recovery");}journal.append(entry.type(),p);}
            }
            assertEquals(format<3?EcologyCosts.DEFAULT:CUSTOM,load(legacy).costRules());assertEquals(EcologyEngine.DEFAULT_SPREAD,load(legacy).spreadRules());
        }
    }

    @Test void malformedCostRulesAndCheckpointNestedObjectsFailClosed()throws Exception {
        Path world=world("malformed"),source=file(world);try(var journal=new EcologyJournal(source)){costHistory(prepared(journal));}
        for(String fault:List.of("extra","type","interval","missing")){
            Path altered=dir.resolve("journal-"+fault+".jsonl");copyEntries(source,altered);
            try(var journal=new EcologyJournal(altered)){
                JsonObject p=rulesPayload();JsonObject hunger=p.getAsJsonObject("hunger");
                if(fault.equals("extra"))hunger.addProperty("extra",true);
                if(fault.equals("type"))hunger.addProperty("enabled","false");
                if(fault.equals("interval"))hunger.addProperty("seconds",11);
                if(fault.equals("missing"))p.remove("darkness");
                journal.append("COST_RULES",p);
            }
            assertThrows(IllegalArgumentException.class,()->load(altered),fault);
        }
        EcologyMaintenance.compact(world,true);List<EcologyJournal.Entry> compacted;try(var journal=new EcologyJournal(source)){compacted=journal.entries();}
        for(String fault:List.of("extra","type","interval","missing")){
            Path altered=dir.resolve("checkpoint-"+fault+".jsonl");try(var journal=new EcologyJournal(altered)){
                for(EcologyJournal.Entry entry:compacted){JsonObject p=entry.payload();if(entry.type().equals("CHECKPOINT_BEGIN")){
                    JsonObject hunger=p.getAsJsonObject("costs").getAsJsonObject("hunger");
                    if(fault.equals("extra"))hunger.addProperty("extra",true);
                    if(fault.equals("type"))hunger.addProperty("enabled","true");
                    if(fault.equals("interval"))hunger.addProperty("seconds",11);
                    if(fault.equals("missing"))p.remove("costs");
                }journal.append(entry.type(),p);}
            }
            assertThrows(IllegalArgumentException.class,()->load(altered),"checkpoint "+fault);
        }
        for(String fault:List.of("mode","amplification","extra","missing")){
            Path altered=dir.resolve("checkpoint-spread-"+fault+".jsonl");try(var journal=new EcologyJournal(altered)){
                for(EcologyJournal.Entry entry:compacted){JsonObject p=entry.payload();if(entry.type().equals("CHECKPOINT_BEGIN")){
                    JsonObject spread=p.getAsJsonObject("spread");
                    if(fault.equals("mode"))spread.addProperty("mode","FUTURE");
                    if(fault.equals("amplification"))spread.addProperty("amplificationPercent",3);
                    if(fault.equals("extra"))spread.addProperty("extra",true);
                    if(fault.equals("missing"))p.remove("spread");
                }journal.append(entry.type(),p);}
            }
            assertThrows(IllegalArgumentException.class,()->load(altered),"checkpoint spread "+fault);
        }
    }

    private Path world(String name)throws Exception {
        Path world=dir.resolve(name);Files.createDirectories(world);Files.writeString(world.resolve("session.lock"),"lock");
        new CampaignStore(world.resolve("chronicle/campaign-state.json")).save(CampaignState.newWorld());
        var city=new AuthoringSite.Region("city","minecraft:overworld",1,new BlockPosition(-1,0,0),new BlockPosition(3,0,0));
        var heart=new AuthoringSite.Anchor("heart","minecraft:overworld",2,new BlockPosition(-1,0,0),"minecraft:reinforced_deepslate");
        new AuthoringStore(world.resolve("chronicle/authoring.json")).save(new AuthoringRegistry(2,Map.of("city",city,"heart",heart)));
        new SiteAssignmentStore(world.resolve("chronicle/map-assignments.json")).save(new SiteAssignments(1,Map.of(
                "quiet_below:source_city",new SiteAssignments.Binding("quiet_below:source_city","city",1),
                "quiet_below:relic_heart",new SiteAssignments.Binding("quiet_below:relic_heart","heart",2))));
        return world;
    }
    private Path file(Path world){return world.resolve("quiet-below/ecology.jsonl");}
    private EcologyEngine prepared(EcologyJournal journal)throws Exception {
        EcologyEngine engine=new EcologyEngine(journal);engine.configure(PINS,AREA,HEART);World world=new World();while(!engine.view().indexed())engine.index(world);engine.mode(true);return engine;
    }
    private void costHistory(EcologyEngine engine)throws Exception {
        EcologyCosts.Rules first=new EcologyCosts.Rules(new EcologyCosts.Rule(true,10),new EcologyCosts.Rule(true,20));
        EcologyCosts.Rules second=new EcologyCosts.Rules(new EcologyCosts.Rule(false,20),new EcologyCosts.Rule(true,40));
        for(int n=0;n<200;n++)engine.tuneCosts(n%2==0?first:second);
        engine.tuneCosts(CUSTOM);
    }
    private EcologyEngine load(Path file)throws Exception {try(var journal=new EcologyJournal(file)){EcologyEngine engine=new EcologyEngine(journal);engine.attach(PINS,AREA,HEART);return engine;}}
    private void copyEntries(Path from,Path to)throws Exception {try(var source=new EcologyJournal(from);var target=new EcologyJournal(to)){for(EcologyJournal.Entry entry:source.entries())target.append(entry.type(),entry.payload());}}
    private static JsonObject rulesPayload(){
        JsonObject p=new JsonObject(),hunger=new JsonObject(),darkness=new JsonObject();hunger.addProperty("enabled",true);hunger.addProperty("seconds",40);darkness.addProperty("enabled",true);darkness.addProperty("seconds",40);p.add("hunger",hunger);p.add("darkness",darkness);return p;
    }
    private static final class World implements EcologyTerrain {
        final Map<Pos,State> blocks=new HashMap<>(Map.of(SOURCE,SCULK));
        public boolean loaded(Pos p){return true;}public State get(Pos p){return blocks.getOrDefault(p,p.y()==0?new State("minecraft:stone",Map.of()):AIR);}
        public boolean solidFace(Pos p,int face){return !get(p).air()&&!get(p).vein();}public boolean substrate(Pos p){return get(p).id().equals("minecraft:stone");}
        public int light(Pos p){return 15;}public boolean set(Pos p,State state){blocks.put(p,state);return true;}
    }
}
