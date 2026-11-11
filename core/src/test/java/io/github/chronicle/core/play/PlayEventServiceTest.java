package io.github.chronicle.core.play;

import io.github.chronicle.core.areas.AreaBounds;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.areas.SnapshotRef;
import io.github.chronicle.core.authoring.AuthoringRegistry;
import io.github.chronicle.core.authoring.BlockPosition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayEventServiceTest {
    private static final UUID PLAYER=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String ID="test:event",HASH="a".repeat(64);

    @Test void silentRunUsesExecutionReceiptAndResetPreservesAllCurrentFields() throws Exception {
        Memory repository = new Memory();
        PlayService service = new PlayService(repository);
        EventSource source = new EventSource("chronicle:book_opened", "test:book", "read");
        EventDefinition definition = new EventDefinition(ID, service.nextEventRevision(), "Silent", "", "", "",
                ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of(), Map.of("test:done", true));
        EventConfiguration configuration = new EventConfiguration(definition, PlayState.Mode.SYSTEM, Optional.empty(),
                service.elapsedTicks(), 40, List.of("test:b", "test:a"), true, source);
        service.putEvent(configuration, null, true);
        EventExecution run = new EventExecution(ID, UUID.randomUUID(), configuration, EventExecution.Phase.EVENT_OUTCOME,
                PlayState.DeliverySource.SYSTEM, null, 1, service.elapsedTicks(), List.of(), "", "");
        service.beginExecution(run);
        repository.saveFails = true;
        assertThrows(IOException.class, () -> service.advanceExecution(ID, run.operationId(), EventExecution.Phase.COMPLETE, ""));
        assertEquals(EventExecution.Phase.EVENT_OUTCOME, service.state().executions().get(ID).phase());
        repository.saveFails = false;
        service.advanceExecution(ID, run.operationId(), EventExecution.Phase.COMPLETE, "Silent outcome saved.");
        assertTrue(service.state().deliveries().isEmpty());
        assertEquals(EventExecution.Phase.COMPLETE, service.state().executions().get(ID).phase());
        service.reset(ID);
        EventConfiguration reset = service.state().events().get(ID);
        assertEquals(configuration.requiredFlags(), reset.requiredFlags());
        assertEquals(source, reset.source());
        assertEquals(40, reset.delayTicks());
        assertEquals(2, reset.formatVersion());
        assertEquals(2, reset.definition().formatVersion());
        assertEquals(definition.flagChanges(), reset.definition().flagChanges());
        assertEquals(List.of(run.with(EventExecution.Phase.COMPLETE, "Silent outcome saved.")), repository.archives);
        assertTrue(service.state().executions().isEmpty());
    }

    @Test void mediaRunCannotSkipDurableMediaClaim() throws Exception {
        Memory repository = new Memory(); PlayService service = new PlayService(repository); service.setParticipant(PLAYER, true);
        EventConfiguration configuration = put(service, List.of(), PlayState.Mode.TIMER, 20);
        EventExecution run = run(configuration, EventExecution.Phase.EVENT_OUTCOME, 0, PlayState.DeliverySource.TIMER, null);
        service.beginExecution(run);
        assertThrows(IllegalStateException.class, () -> service.advanceExecution(ID, run.operationId(), EventExecution.Phase.COMPLETE, ""));
    }

    @Test void ordinaryClockAndMembershipWritesPreserveEventsAndResetArchivesBeforeRearming() throws Exception {
        Memory repository=new Memory();PlayService service=new PlayService(repository);service.setParticipant(PLAYER,true);
        EventConfiguration configuration=put(service,List.of(),PlayState.Mode.TIMER,20);
        service.setParticipant(OTHER,true);for(int i=0;i<20;i++)service.advance(true);service.checkpoint();
        assertEquals(configuration,service.state().events().get(ID));
        EventExecution run=run(configuration,EventExecution.Phase.EVENT_OUTCOME,service.elapsedTicks(),PlayState.DeliverySource.TIMER,null);
        service.beginExecution(run);assertThrows(IllegalStateException.class,()->service.beginExecution(run));
        service.advanceExecution(ID,run.operationId(),EventExecution.Phase.MEDIA_CLAIMED,"");
        service.advanceExecution(ID,run.operationId(),EventExecution.Phase.COMPLETE,"");
        assertEquals(PlayState.Status.DELIVERED,service.state().deliveries().get(ID).status());
        PlayState complete=service.state();repository.archiveFails=true;
        assertThrows(IOException.class,()->service.reset(ID));assertEquals(complete,service.state());
        repository.archiveFails=false;service.reset(ID);
        assertEquals(1,repository.archives.size());assertFalse(service.state().executions().containsKey(ID));
        assertFalse(service.state().deliveries().containsKey(ID));assertEquals(20,service.state().events().get(ID).armedAtTicks());
        assertTrue(service.state().participants().contains(OTHER));
    }

    @Test void worldWorkCannotSkipOutcomeProofOrBeResetDeletedOrEditedWhilePending() throws Exception {
        Memory repository=new Memory();PlayService service=new PlayService(repository);service.setParticipant(PLAYER,true);
        EventConfiguration configuration=put(service,List.of(change()),PlayState.Mode.MANUAL,0);
        EventExecution run=run(configuration,EventExecution.Phase.PREPARED,0,PlayState.DeliverySource.MANUAL,PLAYER);
        service.beginExecution(run);
        assertThrows(IllegalStateException.class,()->service.reset(ID));
        assertThrows(IllegalStateException.class,()->service.deleteEvent(ID,configuration));
        assertThrows(IllegalStateException.class,()->service.putEvent(configuration,configuration,false));
        assertThrows(IllegalStateException.class,()->service.advanceExecution(ID,run.operationId(),EventExecution.Phase.EVENT_OUTCOME,""));
        service.advanceExecution(ID,run.operationId(),EventExecution.Phase.APPLYING,"");
        service.advanceExecution(ID,run.operationId(),EventExecution.Phase.WORLD_COMMITTED,"");
        service.advanceExecution(ID,run.operationId(),EventExecution.Phase.EVENT_OUTCOME,"");
        service.advanceExecution(ID,run.operationId(),EventExecution.Phase.MEDIA_CLAIMED,"");
        service.advanceExecution(ID,run.operationId(),EventExecution.Phase.MEDIA_UNCERTAIN,"Restart after media claim.");
        assertThrows(IllegalStateException.class,()->service.reset(ID));
        service.acknowledgeEventMedia(ID);
        assertEquals(PlayState.Status.CLAIMED,service.state().deliveries().get(ID).status());
        assertTrue(service.state().executions().get(ID).detail().contains("no message, sound or Region change was replayed"));
        service.reset(ID);assertEquals(1,repository.archives.size());
    }

    @Test void failedMediaClaimNeverPublishesItsInMemoryPhaseAndDeletingRetainsDeliveryEvidence() throws Exception {
        Memory repository=new Memory();PlayService service=new PlayService(repository);service.setParticipant(PLAYER,true);
        EventConfiguration configuration=put(service,List.of(),PlayState.Mode.MANUAL,0);
        EventExecution run=run(configuration,EventExecution.Phase.EVENT_OUTCOME,0,PlayState.DeliverySource.MANUAL,PLAYER);
        service.beginExecution(run);repository.saveFails=true;
        assertThrows(IOException.class,()->service.advanceExecution(ID,run.operationId(),EventExecution.Phase.MEDIA_CLAIMED,""));
        assertEquals(EventExecution.Phase.EVENT_OUTCOME,service.state().executions().get(ID).phase());
        repository.saveFails=false;
        service.advanceExecution(ID,run.operationId(),EventExecution.Phase.MEDIA_CLAIMED,"");
        service.advanceExecution(ID,run.operationId(),EventExecution.Phase.COMPLETE,"");
        service.deleteEvent(ID,configuration);
        assertFalse(service.state().events().containsKey(ID));assertEquals(1,repository.archives.size());
        assertTrue(service.state().deliveries().containsKey(ID));
        assertThrows(IllegalStateException.class,()->put(service,List.of(),PlayState.Mode.MANUAL,0));
    }

    @Test void legacyCueUsesTheSameExecutionGateWithoutDuplicateLegacyDelivery() throws Exception {
        Memory repository=new Memory();PlayService service=new PlayService(repository);service.setParticipant(PLAYER,true);
        ObservationCatalog.Definition cue=new ObservationCatalog.Definition(ID,"Cue","Text","",ObservationCatalog.Audience.PARTICIPANTS);
        var registry=AuthoringRegistry.empty();
        var binding=service.bind(cue,registry,new ObservationConfiguration(PlayState.Mode.MANUAL,"","",0),false);
        EventDefinition definition=new EventDefinition(ID,binding.revision(),"Cue","","Text","",cue.audience(),Optional.empty(),List.of());
        EventConfiguration configuration=new EventConfiguration(definition,binding.mode(),Optional.empty(),0,0,"",true);
        EventExecution run=run(configuration,EventExecution.Phase.EVENT_OUTCOME,0,PlayState.DeliverySource.MANUAL,PLAYER);
        service.beginExecution(run);
        assertFalse(service.claim(cue,registry,ID,PlayState.DeliverySource.MANUAL,PLAYER,1,List.of(PLAYER),Map.of()));
        assertThrows(IllegalStateException.class,()->service.finish(ID));
        assertThrows(IllegalStateException.class,()->service.off(ID));
    }

    private static EventConfiguration put(PlayService service,List<EventRegionChange> changes,PlayState.Mode mode,long delay)throws IOException {
        EventDefinition definition=new EventDefinition(ID,service.nextEventRevision(),"An event","","Text","",ObservationCatalog.Audience.PARTICIPANTS,Optional.empty(),changes);
        EventConfiguration configuration=new EventConfiguration(definition,mode,Optional.empty(),service.elapsedTicks(),delay,"",true);
        service.putEvent(configuration,null,true);return configuration;
    }
    private static EventRegionChange change(){
        var bounds=new AreaBounds("region","minecraft:overworld",1,new BlockPosition(0,64,0),new BlockPosition(0,64,0));
        return new EventRegionChange(new EventRegionReference("region",1),"area",1,AreaState.END,new SnapshotRef(HASH,bounds,1,64));
    }
    private static EventExecution run(EventConfiguration configuration,EventExecution.Phase phase,long ticks,PlayState.DeliverySource source,UUID actor){
        return new EventExecution(ID,UUID.randomUUID(),configuration,phase,source,actor,1,ticks,List.of(PLAYER),configuration.definition().changes().isEmpty()?"":HASH,"");
    }
    private static final class Memory implements PlayRepository {
        PlayState state=PlayState.empty();boolean saveFails,archiveFails;List<EventExecution> archives=new ArrayList<>();
        @Override public PlayState load(){return state;}
        @Override public void save(PlayState value)throws IOException{if(saveFails)throw new IOException("Simulated save failure");state=value;}
        @Override public void archiveEvent(EventExecution execution)throws IOException{if(archiveFails)throw new IOException("Simulated archive failure");archives.add(execution);}
    }
}
