package io.github.chronicle.quietbelow.ecology;

import com.google.gson.*;
import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.campaign.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;

class EcologyCompactionTest {
    @TempDir Path dir;
    private static final EcologyEngine.Setup PINS=new EcologyEngine.Setup("city",1,"heart",2,"minecraft:overworld");
    private static final EcologyEngine.Area AREA=new EcologyEngine.Area(new Pos(-1,0,0),new Pos(31,0,0));
    private static final Pos HEART=new Pos(-1,0,0),SOURCE=new Pos(0,0,0);
    private Path file(Path world){return world.resolve("quiet-below/ecology.jsonl");}
    private Path fixture(String name)throws Exception {
        Path world=dir.resolve(name);Files.createDirectories(world);Files.writeString(world.resolve("session.lock"),"lock");
        new CampaignStore(world.resolve("chronicle/campaign-state.json")).save(CampaignState.newWorld());
        var region=new AuthoringSite.Region("city","minecraft:overworld",1,new BlockPosition(-1,0,0),new BlockPosition(31,0,0));
        var heart=new AuthoringSite.Anchor("heart","minecraft:overworld",2,new BlockPosition(-1,0,0),"minecraft:reinforced_deepslate");
        new AuthoringStore(world.resolve("chronicle/authoring.json")).save(new AuthoringRegistry(2,Map.of("city",region,"heart",heart)));
        new SiteAssignmentStore(world.resolve("chronicle/map-assignments.json")).save(new SiteAssignments(1,Map.of(
                "quiet_below:source_city",new SiteAssignments.Binding("quiet_below:source_city","city",1),
                "quiet_below:relic_heart",new SiteAssignments.Binding("quiet_below:relic_heart","heart",2))));
        try(var journal=new EcologyJournal(file(world))){
            EcologyEngine e=new EcologyEngine(journal);e.configure(PINS,AREA,HEART);World w=new World();
            while(!e.view().indexed())e.index(w);e.mode(true);
            for(int n=0;n<160;n++){e.beginTick();e.tick(w,true);}e.flushVitality();
            e.cleanup(true);e.speed(400);e.tuneCrops(new EcologyCrops.Rules(false,40));e.tuneRecovery(new EcologyRecovery.Rules(false,40));e.tuneVitality(new EcologyVitality.Rules(1,20,4));
            e.claim("extra",5,new EcologyEngine.Area(new Pos(0,0,0),new Pos(31,31,0)));e.claimTick(w);
            for(int n=0;n<400;n++)e.speed(n%2==0?25:1600);
        }
        return world;
    }
    private EcologyEngine load(Path path)throws Exception {
        try(var j=new EcologyJournal(path)){EcologyEngine e=new EcologyEngine(j);e.attach(PINS,AREA,HEART);return e;}
    }
    @Test void applyArchivesExactHistoryAndPreservesAllStateThenAllowsAppendsAndRecompaction()throws Exception {
        Path world=fixture("roundtrip");byte[] before=Files.readAllBytes(file(world));EcologyEngine old=load(file(world));
        assertFalse(old.claim().complete());assertTrue(old.cells().stream().anyMatch(c->!c.adopted()&&!c.before().air()));
        var result=EcologyMaintenance.compact(world,true);assertTrue(result.applied());assertTrue(result.afterBytes()<result.beforeBytes()/2);
        assertArrayEquals(before,Files.readAllBytes(Path.of(result.archive())));assertTrue(old.sameCheckpointState(load(file(world))));
        try(var j=new EcologyJournal(file(world))){EcologyEngine e=new EcologyEngine(j);e.attach(PINS,AREA,HEART);
            e.tuneCrops(new EcologyCrops.Rules(true,5));e.tuneRecovery(new EcologyRecovery.Rules(true,2));e.cancelClaim();for(int n=0;n<100;n++)e.speed(n%2==0?50:800);
        }
        EcologyEngine later=load(file(world));assertTrue(EcologyMaintenance.compact(world,true).applied());assertTrue(later.sameCheckpointState(load(file(world))));
    }
    @Test void dryRunAndNoSavingsLeaveWorldBytesUnchanged()throws Exception {
        Path world=fixture("dry");Map<Path,byte[]> before=files(world);var result=EcologyMaintenance.compact(world,false);
        assertFalse(result.applied());assertEquals(before.keySet(),files(world).keySet());for(Path p:before.keySet())assertArrayEquals(before.get(p),Files.readAllBytes(p));
        assertTrue(EcologyMaintenance.compact(world,true).applied());byte[] compacted=Files.readAllBytes(file(world));
        assertFalse(EcologyMaintenance.compact(world,true).applied());assertArrayEquals(compacted,Files.readAllBytes(file(world)));
    }
    @Test void uncertainPromotionRetainsOriginalArchiveAndPreparedCandidate()throws Exception {
        Path world=fixture("failure");byte[] before=Files.readAllBytes(file(world));
        assertThrows(IOException.class,()->EcologyMaintenance.compact(world,true,(a,b)->{throw new AtomicMoveNotSupportedException(a.toString(),b.toString(),"injected");}));
        assertArrayEquals(before,Files.readAllBytes(file(world)));
        try(var paths=Files.list(world.resolve("quiet-below/journal-archives"))){assertArrayEquals(before,Files.readAllBytes(paths.findFirst().orElseThrow()));}
        try(var paths=Files.list(world.resolve("quiet-below"))){assertTrue(paths.anyMatch(p->p.toString().endsWith(".prepared")));}
    }
    @Test void lockedActiveMissingAndCorruptWorldsAreRefused()throws Exception {
        Path world=fixture("guards");byte[] before=Files.readAllBytes(file(world));
        try(var channel=FileChannel.open(world.resolve("session.lock"),StandardOpenOption.WRITE);var lock=channel.lock()){
            assertThrows(IOException.class,()->EcologyMaintenance.compact(world,true));
        }
        try(var journal=new EcologyJournal(file(world))){assertThrows(IOException.class,()->EcologyMaintenance.compact(world,true));}
        var active=new CampaignState(1,1,SessionPhase.ACTIVE,Map.of(),Set.of(),List.of(),0);new CampaignStore(world.resolve("chronicle/campaign-state.json")).save(active);
        assertThrows(IOException.class,()->EcologyMaintenance.compact(world,true));assertArrayEquals(before,Files.readAllBytes(file(world)));
        new CampaignStore(world.resolve("chronicle/campaign-state.json")).save(CampaignState.newWorld());
        Files.writeString(file(world),"broken",StandardOpenOption.APPEND);byte[] corrupt=Files.readAllBytes(file(world));
        assertThrows(IOException.class,()->EcologyMaintenance.compact(world,true));assertArrayEquals(corrupt,Files.readAllBytes(file(world)));
        Files.delete(world.resolve("chronicle/authoring.json"));assertThrows(IOException.class,()->EcologyMaintenance.compact(world,true));
    }
    @Test void sourceGeometryValidationIsNotLaunderedByCheckpoint()throws Exception {
        Path world=fixture("geometry");byte[] before=Files.readAllBytes(file(world));
        var wrong=new AuthoringSite.Region("city","minecraft:overworld",1,new BlockPosition(-1,0,0),new BlockPosition(15,0,0));
        var heart=new AuthoringSite.Anchor("heart","minecraft:overworld",2,new BlockPosition(-1,0,0),"minecraft:reinforced_deepslate");
        new AuthoringStore(world.resolve("chronicle/authoring.json")).save(new AuthoringRegistry(2,Map.of("city",wrong,"heart",heart)));
        assertThrows(IOException.class,()->EcologyMaintenance.compact(world,true));assertArrayEquals(before,Files.readAllBytes(file(world)));
    }
    @Test void lazyUnknownDormantZeroAndEligibleClockSurviveExactly()throws Exception {
        Path world=fixture("health");
        try(var j=new EcologyJournal(file(world))){
            JsonArray states=new JsonArray();states.add(EcologyVitality.change(SOURCE,EcologyVitality.Mode.DORMANT));j.append("VITALITY",EcologyVitality.payload(0,states,-1));
            for(int n=0;n<8;n++)j.append("VITALITY",EcologyVitality.payload(200,new JsonArray(),-1));
            JsonArray unknown=new JsonArray();unknown.add(EcologyVitality.change(SOURCE,EcologyVitality.Mode.UNKNOWN));j.append("VITALITY",EcologyVitality.payload(0,unknown,10000));
        }
        EcologyEngine old=load(file(world));assertEquals(0,old.vitality().value(SOURCE));assertEquals(EcologyVitality.Mode.UNKNOWN,old.vitality().mode(SOURCE));
        assertEquals("Extensive",old.vitality().extent());assertTrue(EcologyMaintenance.compact(world,true).applied());
        EcologyEngine replay=load(file(world));assertTrue(old.sameCheckpointState(replay));assertEquals(0,replay.vitality().value(SOURCE));
    }
    @Test void malformedPagesFailClosedEvenWithValidJournalHashes()throws Exception {
        Path world=fixture("pages");EcologyMaintenance.compact(world,true);List<EcologyJournal.Entry> valid;
        try(var j=new EcologyJournal(file(world))){valid=j.entries();}
        for(String fault:List.of("no-end","duplicate","offset","unknown","source","budget","extra-checkpoint")){
            Path altered=dir.resolve(fault+".jsonl");boolean changed=false;
            try(var j=new EcologyJournal(altered)){
                for(var entry:valid){JsonObject p=entry.payload();String type=entry.type();
                    if(type.equals("CHECKPOINT_END")&&fault.equals("no-end"))continue;
                    if(type.equals("CHECKPOINT_BEGIN")&&fault.equals("unknown"))p.addProperty("extra",true);
                    if(type.equals("CHECKPOINT_CELLS")&&!changed){
                        if(fault.equals("offset"))p.addProperty("offset",1);
                        if(fault.equals("source")){var cell=p.getAsJsonArray("values").get(0).getAsJsonObject();cell.add("p",EcologyEngine.pos(new Pos(100,0,0)));cell.addProperty("adopted",true);}
                        if(fault.equals("budget")){JsonObject bad=new JsonObject();bad.addProperty("value",1);bad.addProperty("mode","UNKNOWN");bad.addProperty("budget",1);p.getAsJsonArray("values").get(0).getAsJsonObject().add("vitality",bad);}
                        changed=true;
                    }
                    j.append(type,p);if(type.equals("CHECKPOINT_CELLS")&&fault.equals("duplicate"))j.append(type,p);
                }
                if(fault.equals("extra-checkpoint"))j.append("CHECKPOINT_BEGIN",valid.stream().filter(e->e.type().equals("CHECKPOINT_BEGIN")).findFirst().orElseThrow().payload());
            }
            assertThrows(IllegalArgumentException.class,()->load(altered),fault);
        }
    }
    @Test void incompleteSourceIndexCannotCompact()throws Exception {
        Path world=fixture("partial");Path partial=dir.resolve("partial.jsonl");
        try(var j=new EcologyJournal(file(world));var p=new EcologyJournal(partial)){
            for(var entry:j.entries()){if(entry.type().equals("INDEX_COMPLETE"))break;p.append(entry.type(),entry.payload());}
        }
        Files.copy(partial,file(world),StandardCopyOption.REPLACE_EXISTING);byte[] before=Files.readAllBytes(file(world));
        assertThrows(IOException.class,()->EcologyMaintenance.compact(world,true));assertArrayEquals(before,Files.readAllBytes(file(world)));
    }
    @Test void worstCaseAcceptedBlockPropertiesStillFitBoundedPages()throws Exception {
        Path world=fixture("properties");Map<String,String> properties=new TreeMap<>();
        for(int n=0;n<32;n++)properties.put("k"+n+"a".repeat(60),"v".repeat(64));
        try(var j=new EcologyJournal(file(world))){
            for(int n=0;n<12;n++){
                JsonObject p=new JsonObject();p.add("p",EcologyEngine.pos(new Pos(100+n,0,0)));
                p.add("before",EcologyEngine.state(new State("minecraft:stone",properties)));p.add("after",EcologyEngine.state(new State("minecraft:sculk",properties)));
                p.add("air",new JsonArray());j.append("GROW",p);
            }
        }
        try(var source=new EcologyJournal(file(world));var candidate=new EcologyJournal(dir.resolve("large-properties.jsonl"))){
            EcologyEngine old=new EcologyEngine(source);old.attach(PINS,AREA,HEART);old.writeCheckpoint(candidate,source.fingerprint());
            EcologyEngine replay=new EcologyEngine(candidate);replay.attach(PINS,AREA,HEART);assertTrue(old.sameCheckpointState(replay));
        }
    }
    @Test void firstCheckpointFormatLoadsWithDefaultHealingAndNewRulesStayStrict()throws Exception {
        Path world=fixture("legacy-checkpoint");EcologyMaintenance.compact(world,true);List<EcologyJournal.Entry> entries;
        try(var j=new EcologyJournal(file(world))){entries=j.entries();}
        Path legacy=dir.resolve("format1.jsonl");
        try(var j=new EcologyJournal(legacy)){
            for(var entry:entries){JsonObject p=entry.payload();if(entry.type().equals("CHECKPOINT_BEGIN")){p.addProperty("format",1);p.remove("recovery");p.remove("costs");}j.append(entry.type(),p);}
        }
        assertEquals(EcologyRecovery.DEFAULT,load(legacy).recoveryRules());
        for(String fault:List.of("interval","enabled","extra")){
            Path altered=dir.resolve("recovery-"+fault+".jsonl");
            try(var j=new EcologyJournal(altered)){
                for(var entry:entries)j.append(entry.type(),entry.payload());
                JsonObject p=new JsonObject();p.addProperty("enabled",true);p.addProperty("seconds",10);
                if(fault.equals("interval"))p.addProperty("seconds",11);
                if(fault.equals("enabled"))p.addProperty("enabled","true");
                if(fault.equals("extra"))p.addProperty("extra",true);j.append("RECOVERY_RULES",p);
            }
            assertThrows(IllegalArgumentException.class,()->load(altered));
        }
    }
    private Map<Path,byte[]> files(Path root)throws IOException {Map<Path,byte[]> result=new HashMap<>();try(var paths=Files.walk(root)){for(Path p:paths.filter(Files::isRegularFile).toList())result.put(p,Files.readAllBytes(p));}return result;}
    private static class World implements EcologyTerrain {
        final Map<Pos,State> blocks=new HashMap<>(Map.of(SOURCE,SCULK));
        public boolean loaded(Pos p){return true;}public State get(Pos p){return blocks.getOrDefault(p,p.y()==0?new State("minecraft:stone",Map.of()):AIR);}
        public boolean solidFace(Pos p,int face){return !get(p).air()&&!get(p).vein();}public boolean substrate(Pos p){return get(p).id().equals("minecraft:stone");}
        public int light(Pos p){return 15;}public boolean set(Pos p,State state){blocks.put(p,state);return true;}
    }
}
