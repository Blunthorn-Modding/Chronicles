package io.github.chronicle.core.server;

import io.github.chronicle.core.areas.*;
import io.github.chronicle.core.authoring.BlockPosition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventBatchFilesTest {
    @Test void fullHistoryRefusesBeforePublishingAnotherRunAndExistingEntriesRemainUsable(){
        var entries=new java.util.LinkedHashMap<UUID,EventBatchFiles.Entry>();
        for(int i=0;i<256;i++){UUID id=new UUID(0,i);entries.put(id,new EventBatchFiles.Entry(id,"a".repeat(64),EventBatchFiles.Phase.WORLD_COMMITTED,EventBatchFiles.Step.COMMIT,1,0,"",Instant.EPOCH.toString()));}
        var full=new EventBatchFiles.Journal(256,entries);
        assertThrows(IllegalStateException.class,full::requireNewBatchCapacity);
        assertEquals(256,full.entries().size());assertEquals(256,full.put(entries.values().iterator().next()).entries().size());
        entries.remove(entries.keySet().iterator().next());
        assertDoesNotThrow(new EventBatchFiles.Journal(257,entries)::requireNewBatchCapacity);
    }
    @Test void exactPlanAndJournalRoundTrip()throws Exception{
        UUID id=UUID.randomUUID();AreaBounds bounds=new AreaBounds("region","minecraft:overworld",3,new BlockPosition(0,0,0),new BlockPosition(1,0,0));
        AreaSnapshot snapshot=new AreaSnapshot(bounds,List.of("{\"Name\":\"minecraft:stone\"}"),new int[]{0,0},Map.of());SnapshotRef ref=AreaSnapshotCodec.encode(snapshot).ref();
        EventBatchFiles.Target target=new EventBatchFiles.Target("area",5,AreaState.END,bounds,ref,ref);
        EventBatchFiles.Plan plan=new EventBatchFiles.Plan(id,new UUID(0,0),Instant.EPOCH.toString(),"effects-sha256:"+"a".repeat(64),9,decoded(snapshot)*2,2,1,List.of(target));
        assertEquals(plan,EventBatchFiles.decodePlan(EventBatchFiles.encodePlan(plan)));

        EventBatchFiles.Entry entry=new EventBatchFiles.Entry(id,"b".repeat(64),EventBatchFiles.Phase.APPLYING,EventBatchFiles.Step.WRITE,0,1,"",Instant.EPOCH.toString());
        EventBatchFiles.Journal journal=new EventBatchFiles.Journal(2,Map.of(id,entry));assertEquals(journal,EventBatchFiles.decodeJournal(EventBatchFiles.encodeJournal(journal)));
    }

    @Test void rejectsCrossDimensionAndOversizedCombinedEvidence(){
        UUID id=UUID.randomUUID();EventBatchFiles.Target first=target("one","minecraft:overworld",0),second=target("two","minecraft:the_nether",2);
        assertThrows(IllegalArgumentException.class,()->new EventBatchFiles.Plan(id,id,Instant.EPOCH.toString(),"effects-sha256:"+"a".repeat(64),0,16,4,2,List.of(first,second)));
        assertThrows(IllegalArgumentException.class,()->new EventBatchFiles.Plan(id,id,Instant.EPOCH.toString(),"effects-sha256:"+"a".repeat(64),0,64L*1024*1024+1,2,1,List.of(first)));
    }

    @Test void malformedOrTrailingEvidenceFailsClosed()throws Exception{
        assertThrows(IllegalArgumentException.class,()->EventBatchFiles.decodePlan(new byte[]{1,2,3}));
        EventBatchFiles.Journal empty=EventBatchFiles.Journal.empty();byte[] valid=EventBatchFiles.encodeJournal(empty),trailing=java.util.Arrays.copyOf(valid,valid.length+1);
        assertThrows(IllegalArgumentException.class,()->EventBatchFiles.decodeJournal(trailing));
    }

    @Test void restartNeverTreatsTerrainSavedAsMetadataAuthority(){
        UUID id=UUID.randomUUID();EventBatchFiles.Target target=target("restart","minecraft:overworld",0);EventBatchFiles.Plan plan=new EventBatchFiles.Plan(id,id,Instant.EPOCH.toString(),"effects-sha256:"+"a".repeat(64),0,16,2,1,List.of(target));
        EventBatchFiles.Entry saved=new EventBatchFiles.Entry(id,"b".repeat(64),EventBatchFiles.Phase.TERRAIN_SAVED,EventBatchFiles.Step.COMMIT,1,0,"",Instant.EPOCH.toString());
        EventBatchFiles.Entry resumed=EventBatchFiles.restartProof(plan,saved);assertEquals(EventBatchFiles.Phase.APPLYING,resumed.phase());assertEquals(EventBatchFiles.Step.VERIFY_ALL,resumed.step());assertEquals(0,resumed.target());assertEquals(0,resumed.cursor());
    }

    private static EventBatchFiles.Target target(String id,String dimension,int x){AreaBounds b=new AreaBounds("region_"+id,dimension,1,new BlockPosition(x,0,0),new BlockPosition(x+1,0,0));AreaSnapshot s=new AreaSnapshot(b,List.of("{\"Name\":\"minecraft:stone\"}"),new int[]{0,0},Map.of());SnapshotRef r=AreaSnapshotCodec.encode(s).ref();return new EventBatchFiles.Target("area_"+id,1,AreaState.START,b,r,r);}
    private static long decoded(AreaSnapshot snapshot){return (long)snapshot.cells()*Integer.BYTES+snapshot.palette().stream().mapToInt(value->value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum();}
}
