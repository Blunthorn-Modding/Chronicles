package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("scale")
class EcologyScaleTest {
    private static final Pos HEART=new Pos(-1,0,0), EXTRA=new Pos(128,0,0);
    private static final EcologyEngine.Setup PINS=new EcologyEngine.Setup("city",1,"heart",1,"minecraft:overworld");
    private static final EcologyEngine.Area AREA=new EcologyEngine.Area(HEART,new Pos(127,15,63));

    @TempDir Path dir;

    @Test void exactCapacityIndexCertifiesCheckpointsAndHoldsPhysicalExpansion() throws Exception {
        long started=System.nanoTime();
        var world=new DenseTerrain();Path source=dir.resolve("source.jsonl"),checkpoint=dir.resolve("checkpoint.jsonl");
        int indexCalls=0,settleTicks=0,checkpointEntries;
        long checkpointBytes;
        try(var journal=new EcologyJournal(source)) {
            var engine=new EcologyEngine(journal);engine.configure(PINS,AREA,HEART);
            long indexingStarted=System.nanoTime();
            while(!engine.view().indexed()&&indexCalls++<5000)engine.index(world);
            long indexMillis=elapsedMillis(indexingStarted);
            assertTrue(engine.view().indexed(),"dense source index did not complete");
            assertEquals(EcologyEngine.MAX_CELLS,engine.view().owned());engine.mode(true);

            long settlingStarted=System.nanoTime();
            while((engine.view().unresolved()||engine.vitalityPending())&&settleTicks++<3500)tick(engine,world);
            long settleMillis=elapsedMillis(settlingStarted);
            assertFalse(engine.view().unresolved(),"dense topology did not settle");
            assertFalse(engine.vitalityPending(),"dense vitality scan did not settle");
            assertEquals(EcologyEngine.MAX_CELLS,engine.view().connected());
            assertEquals(EcologyEngine.MAX_CELLS,engine.cells().stream().filter(cell->engine.rooted(cell.position())).count());
            engine.flushVitality();

            long checkpointStarted=System.nanoTime();
            try(var target=new EcologyJournal(checkpoint)) {
                engine.writeCheckpoint(target,journal.fingerprint());
                checkpointBytes=target.bytes();checkpointEntries=target.entries().size();
            }
            try(var target=new EcologyJournal(checkpoint)) {
                var replay=new EcologyEngine(target);replay.attach(PINS,AREA,HEART);
                assertTrue(engine.sameCheckpointState(replay),"checkpoint replay changed dense ecology state");
            }
            long checkpointMillis=elapsedMillis(checkpointStarted);

            assertJournalBound(journal.bytes(),journal.entries().size());
            assertJournalBound(checkpointBytes,checkpointEntries);
            world.extra=true;engine.noticeContact(EXTRA);engine.beginTick();
            assertThrows(IllegalStateException.class,()->engine.tick(world,true));
            assertEquals(EcologyEngine.MAX_CELLS,engine.view().owned());assertFalse(engine.owns(EXTRA));
            assertNotNull(engine.failure());assertTrue(engine.failure().contains("Owned-cell capacity reached"));

            System.gc();
            writeMetrics(indexCalls,settleTicks,indexMillis,settleMillis,checkpointMillis,elapsedMillis(started),heapUsed(),journal.bytes(),journal.entries().size(),
                    checkpointBytes,checkpointEntries,engine.failure());
        }
    }

    private static void tick(EcologyEngine engine,DenseTerrain world) throws Exception {
        world.reads=world.mutations=0;engine.beginTick();engine.tick(world,true);
        assertTrue(world.reads<=20_000,"per-tick terrain reads: "+world.reads);
        assertTrue(world.mutations<=EcologyEngine.MAX_WRITES,"per-tick terrain mutations: "+world.mutations);
    }

    private static void assertJournalBound(long bytes,int entries) {
        assertTrue(bytes<=EcologyJournal.MAX_FILE_BYTES,"journal bytes: "+bytes);
        assertTrue(entries<=EcologyJournal.MAX_ENTRIES,"journal entries: "+entries);
    }

    private static long elapsedMillis(long started) { return (System.nanoTime()-started)/1_000_000; }
    private static long heapUsed() { return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(); }

    private static void writeMetrics(int indexCalls,int settleTicks,long indexMillis,long settleMillis,long checkpointMillis,long elapsedMillis,long heapBytes,long sourceBytes,
                                     int sourceEntries,long checkpointBytes,int checkpointEntries,String failure) throws Exception {
        JsonObject metrics=new JsonObject();metrics.addProperty("cells",EcologyEngine.MAX_CELLS);metrics.addProperty("indexCalls",indexCalls);
        metrics.addProperty("settleTicks",settleTicks);metrics.addProperty("indexMillis",indexMillis);metrics.addProperty("settleMillis",settleMillis);
        metrics.addProperty("checkpointMillis",checkpointMillis);metrics.addProperty("elapsedMillis",elapsedMillis);metrics.addProperty("retainedHeapBytes",heapBytes);
        metrics.addProperty("sourceJournalBytes",sourceBytes);metrics.addProperty("sourceJournalEntries",sourceEntries);
        metrics.addProperty("checkpointJournalBytes",checkpointBytes);metrics.addProperty("checkpointJournalEntries",checkpointEntries);
        metrics.addProperty("capacityHold",failure);
        Path report=Path.of("build","reports","ecology-scale.json");Files.createDirectories(report.getParent());Files.writeString(report,metrics+System.lineSeparator());
    }

    private static final class DenseTerrain implements EcologyTerrain {
        private static final State STONE=new State("minecraft:stone",Map.of());
        int reads,mutations;boolean extra;

        @Override public boolean loaded(Pos p) { return true; }
        @Override public State get(Pos p) {
            reads++;
            return extra&&p.equals(EXTRA)||p.x()>=0&&p.x()<=127&&p.y()>=0&&p.y()<=15&&p.z()>=0&&p.z()<=63?SCULK:STONE;
        }
        @Override public boolean solidFace(Pos p,int face) { State state=get(p);return !state.air()&&!state.vein(); }
        @Override public boolean substrate(Pos p) { return false; }
        @Override public int light(Pos p) { return 0; }
        @Override public boolean set(Pos p,State state) { mutations++;return true; }
    }
}
