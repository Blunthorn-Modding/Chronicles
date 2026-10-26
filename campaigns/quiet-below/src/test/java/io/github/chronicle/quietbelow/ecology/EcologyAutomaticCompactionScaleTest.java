package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

/** Maximum ownership and ~50 MB of journal history under the explicit task's 1 GiB JVM heap cap.
 * Never a 1 GiB journal, terrain, native tick-rate, or peak-resident-memory claim. */
@Tag("scale")
class EcologyAutomaticCompactionScaleTest {
    @TempDir Path directory;

    @Test void fullOwnershipRetainsIrreducibleIndexThenCompactsAccumulatedHistory() throws Exception {
        var heart=new Pos(-1,0,0);
        var pins=new EcologyEngine.Setup("city",1,"heart",1,"minecraft:overworld");
        var area=new EcologyEngine.Area(heart,new Pos(127,15,63));
        var terrain=new EcologyTerrain(){
            public boolean loaded(Pos p){return true;}
            public State get(Pos p){return p.x()>=0&&p.x()<128&&p.y()>=0&&p.y()<16&&p.z()>=0&&p.z()<64?SCULK:new State("minecraft:stone",Map.of());}
            public boolean solidFace(Pos p,int face){return true;}
            public boolean substrate(Pos p){return false;}
            public int light(Pos p){return 0;}
            public boolean set(Pos p,State state){throw new AssertionError("Storage rehearsal must not mutate terrain");}
        };
        Path file=directory.resolve("ecology.jsonl");EcologyJournal journal=new EcologyJournal(file);
        try {
            var live=new EcologyEngine(journal);live.configure(pins,area,heart);
            while(!live.view().indexed())live.index(terrain);
            assertEquals(EcologyEngine.MAX_CELLS,live.view().owned());
            long indexBytes=journal.bytes(),started=System.nanoTime();
            journal.beginMaintenance();var noSaving=EcologyAutomaticCompaction.compact(journal,file,pins,area,heart);
            assertFalse(noSaving.applied());assertSame(journal,noSaving.journal());assertFalse(journal.maintenanceActive());
            long noSavingMillis=(System.nanoTime()-started)/1_000_000;
            int writes=0;
            var firstRules=new EcologyCosts.Rules(new EcologyCosts.Rule(true,10),new EcologyCosts.Rule(true,40));
            var secondRules=new EcologyCosts.Rules(new EcologyCosts.Rule(true,20),new EcologyCosts.Rule(true,60));
            while(journal.bytes()<50_000_000L&&journal.revision()<EcologyJournal.MAX_ENTRIES-1){live.tuneCosts(writes++%2==0?firstRules:secondRules);}
            assertTrue(journal.bytes()>=50_000_000L,"Fixture must accumulate sufficient bounded history: "+journal.bytes()+" bytes / "+journal.revision()+" records");
            long sourceBytes=journal.bytes(),sourceRecords=journal.revision();started=System.nanoTime();
            journal.beginMaintenance();var replaced=EcologyAutomaticCompaction.compact(journal,file,pins,area,heart);
            live.replaceJournal(journal,replaced.journal());journal=replaced.journal();
            assertTrue(replaced.applied());assertEquals(EcologyEngine.MAX_CELLS,live.view().owned());
            assertTrue(replaced.afterBytes()<sourceBytes-4096);live.speed(50);
            var metrics=new JsonObject();metrics.addProperty("cells",EcologyEngine.MAX_CELLS);
            metrics.addProperty("indexBytes",indexBytes);metrics.addProperty("noSavingMillis",noSavingMillis);
            metrics.addProperty("sourceBytes",sourceBytes);metrics.addProperty("sourceRecords",sourceRecords);
            metrics.addProperty("checkpointBytes",replaced.afterBytes());metrics.addProperty("checkpointRecords",replaced.afterRecords());
            metrics.addProperty("compactionMillis",(System.nanoTime()-started)/1_000_000);
            metrics.addProperty("heapLimitBytes",Runtime.getRuntime().maxMemory());
            Path report=Path.of("build/reports/automatic-compaction-scale.json");Files.createDirectories(report.getParent());Files.writeString(report,metrics+System.lineSeparator());
        } finally {journal.close();}
        try(var replay=new EcologyJournal(file)){
            var engine=new EcologyEngine(replay);engine.attach(pins,area,heart);
            assertEquals(EcologyEngine.MAX_CELLS,engine.view().owned());assertNull(engine.failure());
        }
    }
}
