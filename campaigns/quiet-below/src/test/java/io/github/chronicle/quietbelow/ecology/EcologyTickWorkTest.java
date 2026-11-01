package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologyTickWorkTest {
    @TempDir Path directory;
    private static final Pos HEART=new Pos(-1,0,0), SOURCE=new Pos(0,0,0);

    @Test void growthThatQueuesAnExtensionIsStillRecordedAsGrowth() throws Exception {
        try(var journal=new EcologyJournal(directory.resolve("growth.jsonl"))) {
            var world=new EcologyEngineTest.World();var engine=ready(journal,world);
            boolean witnessed=false;
            for(int tick=0;tick<2000;tick++) {
                long before=engine.spreadAttempts();
                engine.beginTick();engine.tick(world,true);
                if(engine.spreadAttempts()>before) {
                    assertEquals(EcologyTickWork.GROWTH_ATTEMPT,engine.tickWorkForTest());
                    assertTrue(engine.tickWorkForTest().active());
                    if(engine.incrementalExtensionForTest()) {
                        assertTrue(engine.view().unresolved(),"old counter must reproduce the queued-extension false positive");
                        assertTrue(engine.rooted(SOURCE),"existing certified cells retain functionality");
                        witnessed=true;break;
                    }
                }
            }
            assertTrue(witnessed,"fixture must exercise actual growth with queued incremental graph work");
        }
    }

    @Test void genuinelyUnloadedTopologyIsNotCountedAsActive() throws Exception {
        try(var journal=new EcologyJournal(directory.resolve("unload.jsonl"))) {
            var world=new EcologyEngineTest.World();var engine=ready(journal,world);
            for(int n=0;n<10;n++){engine.beginTick();engine.tick(world,true);}
            world.unloaded.add(0);engine.chunkUnloaded(0,0);
            engine.beginTick();engine.tick(world,true);
            assertFalse(engine.tickWorkForTest().active());
            assertTrue(engine.view().unresolved());
            assertTrue(engine.tickWorkForTest()==EcologyTickWork.TOPOLOGY_UNKNOWN
                    ||engine.tickWorkForTest()==EcologyTickWork.TOPOLOGY_REBUILD);
            world.unloaded.clear();engine.invalidateConnections();
            for(int n=0;n<20;n++){engine.beginTick();engine.tick(world,true);}
            assertTrue(engine.tickWorkForTest().active());
        }
    }

    @Test void resetAndPauseCannotReuseThePreviousActiveTick() throws Exception {
        try(var journal=new EcologyJournal(directory.resolve("pause.jsonl"))) {
            var world=new EcologyEngineTest.World();var engine=ready(journal,world);
            for(int n=0;n<10;n++){engine.beginTick();engine.tick(world,true);}
            assertTrue(engine.tickWorkForTest().active());
            engine.beginTick();assertEquals(EcologyTickWork.NOT_TICKED,engine.tickWorkForTest());
            engine.tick(world,false);assertEquals(EcologyTickWork.NOT_PLAYING,engine.tickWorkForTest());
            assertFalse(engine.tickWorkForTest().active());
        }
    }

    private EcologyEngine ready(EcologyJournal journal,EcologyEngineTest.World world) throws Exception {
        var engine=new EcologyEngine(journal);
        engine.configure(new EcologyEngine.Setup("city",1,"heart",1,"minecraft:overworld"),
                new EcologyEngine.Area(HEART,new Pos(0,2,0)),HEART);
        for(int n=0;n<100&&!engine.view().indexed();n++)engine.index(world);
        assertTrue(engine.view().indexed());engine.mode(true);return engine;
    }
}
