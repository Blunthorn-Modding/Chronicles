package io.github.chronicle.core.server;

import io.github.chronicle.core.areas.*;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.history.ImmutableHistoryArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventBatchFilesTest {
    @TempDir Path directory;
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

    @Test void committedHistoryArchivesAndFreesMoreThanTheLiveCapacity()throws Exception{
        Path journal=directory.resolve("event-batches/journal.bin");EventBatchFiles.Store store=new EventBatchFiles.Store(journal);store.load();
        Map<UUID,EventBatchFiles.Entry> entries=new java.util.LinkedHashMap<>();
        for(int i=0;i<256;i++){UUID id=new UUID(0,i);entries.put(id,committed(id,"a".repeat(64)));}
        EventBatchFiles.Journal full=new EventBatchFiles.Journal(1,entries);store.save(full);store.save(full.retire(entries.keySet()));
        UUID old=entries.keySet().iterator().next();assertEquals(entries.get(old),store.archived(old).orElseThrow());
        UUID fresh=UUID.randomUUID();EventBatchFiles.Entry freshEntry=committed(fresh,"b".repeat(64));EventBatchFiles.Journal next=new EventBatchFiles.Journal(3,Map.of(fresh,freshEntry));store.save(next);
        Map<UUID,EventBatchFiles.Entry> replay=new java.util.LinkedHashMap<>(next.entries());replay.put(old,entries.get(old));
        assertThrows(IOException.class,()->store.save(new EventBatchFiles.Journal(4,replay)));
        assertEquals(Map.of(fresh,freshEntry),store.load().entries());
    }

    @Test void conflictingArchiveStopsBeforeCommittedJournalPrune()throws Exception{
        Path journal=directory.resolve("event-batches/journal.bin");EventBatchFiles.Store store=new EventBatchFiles.Store(journal);store.load();
        UUID id=UUID.randomUUID();EventBatchFiles.Entry entry=committed(id,"a".repeat(64));EventBatchFiles.Journal live=new EventBatchFiles.Journal(1,Map.of(id,entry));store.save(live);
        new ImmutableHistoryArchive(journal.getParent().resolve("history"),"event-batch",4*1024*1024).publish(id.toString(),new byte[]{1});
        assertThrows(IOException.class,()->store.save(live.retire(Set.of(id))));
        assertEquals(entry,new EventBatchFiles.Store(journal).load().entries().get(id));
    }

    private static EventBatchFiles.Target target(String id,String dimension,int x){AreaBounds b=new AreaBounds("region_"+id,dimension,1,new BlockPosition(x,0,0),new BlockPosition(x+1,0,0));AreaSnapshot s=new AreaSnapshot(b,List.of("{\"Name\":\"minecraft:stone\"}"),new int[]{0,0},Map.of());SnapshotRef r=AreaSnapshotCodec.encode(s).ref();return new EventBatchFiles.Target("area_"+id,1,AreaState.START,b,r,r);}
    private static EventBatchFiles.Entry committed(UUID id,String hash){return new EventBatchFiles.Entry(id,hash,EventBatchFiles.Phase.WORLD_COMMITTED,EventBatchFiles.Step.COMMIT,1,0,"",Instant.EPOCH.toString());}
    private static long decoded(AreaSnapshot snapshot){return (long)snapshot.cells()*Integer.BYTES+snapshot.palette().stream().mapToInt(value->value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum();}
}
