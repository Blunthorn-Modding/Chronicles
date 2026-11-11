package io.github.chronicle.quietbelow.ecology;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.*;
import static org.junit.jupiter.api.Assertions.*;

class EcologyLiveCompactionHeartTest {
    @TempDir Path dir;
    @Test void liveClockContinuesAndImmediateDestructionRemainsReplayableAfterCheckpoint() throws Exception {
        Pos root=new Pos(-1,0,0),source=new Pos(0,0,0);State rootState=new State("minecraft:reinforced_deepslate",Map.of());
        var pins=new EcologyEngine.Setup("city",1,"heart",1,"minecraft:overworld");var area=new EcologyEngine.Area(root,new Pos(1,0,0));
        var world=new EcologyEngineTest.World();world.blocks.clear();world.blocks.put(root,rootState);world.blocks.put(source,SCULK);
        Path file=dir.resolve("live-heart.jsonl");EcologyJournal journal=new EcologyJournal(file);
        try {
            var engine=new EcologyEngine(journal);engine.configure(pins,area,root);
            for(int n=0;n<20&&!engine.view().indexed();n++)engine.index(world);engine.mode(true);
            for(int n=0;n<400;n++){engine.beginTick();engine.tick(world,true);engine.heartTick(true,true);}
            assertEquals(400,engine.heartElapsed());
            for(int n=0;n<400;n++)engine.speed(n%2==0?25:400);
            engine.flushHeart();engine.flushVitality();journal.beginMaintenance();
            var first=EcologyAutomaticCompaction.compact(journal,file,pins,area,root);assertTrue(first.applied());
            engine.replaceJournal(journal,first.journal());journal=first.journal();assertEquals(400,engine.heartElapsed());
            var restart=new EcologyEngine(journal);restart.attach(pins,area,root);assertEquals(0,restart.heartElapsed(),"A real restart still requires fresh weakness proof");
            for(int n=0;n<800;n++){engine.beginTick();engine.tick(world,true);engine.heartTick(true,true);}
            assertEquals(1200,engine.heartElapsed());assertTrue(engine.heartVulnerable(true));
            for(int n=0;n<400;n++)engine.speed(n%2==0?50:800);
            engine.flushHeart();engine.flushVitality();journal.beginMaintenance();
            var second=EcologyAutomaticCompaction.compact(journal,file,pins,area,root);assertTrue(second.applied());
            engine.replaceJournal(journal,second.journal());journal=second.journal();assertTrue(engine.heartVulnerable(true));
            engine.eradicate(UUID.randomUUID(),root,rootState,true);
        } finally {journal.close();}
        try(var reopened=new EcologyJournal(file)){var ending=new EcologyEngine(reopened);ending.attach(pins,area,root);assertNotNull(ending.ending(),"Immediate Heart ending must replay from its compacted clock baseline");}
    }
}
