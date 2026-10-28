package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologyExtentObserverTest {
    @TempDir Path directory;
    private static EcologyVitalityTest.Branch city() {
        return new EcologyVitalityTest.Branch(){@Override public State get(Pos p){reads++;return p.equals(HEART)?HEART_BLOCK:p.x()>=0&&p.x()<8&&p.y()>=0&&p.y()<8&&p.z()>=0&&p.z()<8?SCULK:AIR;}};
    }
    private static EcologyEngine engine(EcologyJournal journal,EcologyVitalityTest.Branch world)throws Exception {
        EcologyEngine engine=new EcologyEngine(journal);engine.configure(EcologyVitalityTest.Branch.PINS,new EcologyEngine.Area(new Pos(0,0,0),new Pos(7,7,7)),new Pos(0,0,0));
        for(int i=0;i<100&&!engine.view().indexed();i++)engine.index(world);assertTrue(engine.view().indexed());engine.mode(true);return engine;
    }
    @Test void sourcePreparationPrecedesExactDurableTransitionAndCompletionFollowsIt()throws Exception {
        Path file=directory.resolve("ecology.jsonl");List<EcologyJournal.Entry> completed=new ArrayList<>();
        try(EcologyJournal journal=new EcologyJournal(file)) {
            var world=city();EcologyEngine engine=engine(journal,world);
            engine.observeExtents((previous,next,entry)->{
                assertEquals("Rooted",previous);assertEquals("Established",next);assertEquals(journal.revision()+1,entry.sequence());
                assertFalse(journal.entries().contains(entry));
                return ()->{assertEquals(entry,journal.entries().getLast());assertEquals(next,engine.vitality().extent());completed.add(entry);};
            });
            for(int i=0;i<200&&completed.isEmpty();i++){engine.beginTick();engine.tick(world,true);}
            assertEquals(1,completed.size());assertEquals("Established",engine.vitality().extent());
        }
        try(EcologyJournal journal=new EcologyJournal(file)){assertEquals("Established",new EcologyEngine(journal).vitality().extent());}
        assertEquals(1,completed.size(),"replay does not republish the historical source");
    }
    @Test void rejectedSourcePreparationPreventsTheExtentTransitionFromBeingCommitted()throws Exception {
        try(EcologyJournal journal=new EcologyJournal(directory.resolve("refused.jsonl"))) {
            var world=city();EcologyEngine engine=engine(journal,world);boolean[] attempted={false};
            engine.observeExtents((previous,next,entry)->{attempted[0]=true;throw new IOException("source writer unavailable");});
            assertThrows(IOException.class,()->{for(int i=0;i<200;i++){engine.beginTick();engine.tick(world,true);}});
            assertTrue(attempted[0]);assertEquals("Rooted",engine.vitality().extent());
            assertFalse(journal.entries().stream().anyMatch(entry->entry.type().equals("VITALITY")&&entry.payload().get("ready").getAsInt()>=0));
        }
    }
}
