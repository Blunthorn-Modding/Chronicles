package io.github.chronicle.core.departures;

import io.github.chronicle.core.areas.AreaState;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DepartureServiceTest {
    private static final UUID ACTOR=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PASSENGER=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OPERATION=UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Test void failedSaveNeverPublishesDefinitionOrIrreversibleClaim()throws IOException{
        Memory memory=new Memory();DepartureService service=new DepartureService(memory);memory.fail=true;
        assertThrows(IOException.class,()->service.create(definition("test:exit",1,false),0));assertEquals(DepartureState.empty(),service.state());
        memory.fail=false;service.create(definition("test:exit",1,false),0);memory.fail=true;
        assertThrows(IOException.class,()->service.begin(intent("test:exit",OPERATION,false,false),service.state().revision()));
        assertTrue(service.state().operations().isEmpty());assertTrue(service.state().departedByPlayer().isEmpty());assertTrue(service.state().usedByDefinition().isEmpty());
    }

    @Test void beginClaimsDefinitionAndEveryPassengerBeforeAnyEffectAndIsIdempotent()throws IOException{
        Memory memory=new Memory();DepartureService service=service(memory,definition("test:exit",1,false));DepartureIntent intent=intent("test:exit",OPERATION,false,false);
        DepartureOperation committed=service.begin(intent,service.state().revision());assertEquals(DepartureOperation.Stage.COMMITTED,committed.stage());assertEquals(OPERATION,service.state().usedByDefinition().get("test:exit"));assertEquals(OPERATION,service.state().departedByPlayer().get(ACTOR));assertEquals(OPERATION,service.state().departedByPlayer().get(PASSENGER));
        long revision=service.state().revision();assertEquals(committed,service.begin(intent,0));assertEquals(revision,service.state().revision());
        DepartureIntent reused=new DepartureIntent(OPERATION,"test:exit",1,ACTOR,List.of(ACTOR),false,Optional.empty(),"2026-10-26T00:00:00Z");assertThrows(IllegalStateException.class,()->service.begin(reused,revision));
        service.create(definition("test:other",1,false),service.state().revision());
        UUID otherPlayer=UUID.randomUUID();DepartureIntent concurrent=new DepartureIntent(UUID.randomUUID(),"test:other",1,otherPlayer,List.of(otherPlayer),false,Optional.empty(),"2026-10-26T00:00:00Z");
        assertThrows(IllegalStateException.class,()->service.begin(concurrent,service.state().revision()));
    }

    @Test void strictStageOrderingAndConclusionObligationSurviveRecovery()throws IOException{
        Memory memory=new Memory();DepartureService service=service(memory,definition("test:exit",1,true));DepartureIntent intent=intent("test:exit",OPERATION,true,true);service.begin(intent,service.state().revision());
        assertThrows(IllegalStateException.class,()->service.recordAreaEffects(OPERATION,"area-proof"));assertThrows(IllegalStateException.class,()->service.complete(OPERATION));
        service.recordPlayerEffects(OPERATION,"players-saved");assertThrows(IllegalStateException.class,()->service.recordNoAreaEffect(OPERATION));service.recordAreaEffects(OPERATION,"area-saved");
        assertThrows(IllegalStateException.class,()->service.recordNoConclusion(OPERATION));assertThrows(IllegalStateException.class,()->service.complete(OPERATION));
        DepartureService recovered=new DepartureService(memory);assertEquals(DepartureOperation.Stage.AREA_EFFECTS_SAVED,recovered.state().operations().get(OPERATION).stage());assertTrue(recovered.state().departedByPlayer().containsKey(PASSENGER));
        recovered.recordConclusion(OPERATION,"campaign-saved","2026-10-26T00:01:00Z");assertTrue(recovered.state().concluded().isPresent());assertThrows(IllegalStateException.class,()->recovered.recordConclusion(OPERATION,"other-proof","2026-10-26T00:01:00Z"));
        recovered.complete(OPERATION);assertEquals(DepartureOperation.Stage.COMPLETE,recovered.state().operations().get(OPERATION).stage());assertEquals(recovered.state().revision(),new DepartureService(memory).state().revision());
        assertThrows(IllegalStateException.class,()->recovered.begin(intent("test:exit",UUID.randomUUID(),false,true),recovered.state().revision()));
    }

    @Test void explicitNoAreaAndNoConclusionReceiptsAreRequired()throws IOException{
        DepartureService service=service(new Memory(),definition("test:exit",1,false));service.begin(intent("test:exit",OPERATION,false,false),service.state().revision());service.recordPlayerEffects(OPERATION,"players");
        assertThrows(IllegalStateException.class,()->service.recordAreaEffects(OPERATION,"invented-area"));service.recordNoAreaEffect(OPERATION);assertThrows(IllegalStateException.class,()->service.recordConclusion(OPERATION,"invented-conclusion","2026-10-26T00:01:00Z"));service.recordNoConclusion(OPERATION);service.complete(OPERATION);
        DepartureOperation operation=service.state().operations().get(OPERATION);assertEquals(DepartureReceipt.notRequired(),operation.areaEffects().orElseThrow());assertEquals(DepartureReceipt.notRequired(),operation.conclusion().orElseThrow());
    }

    @Test void usedDefinitionCannotChangeOrRetireAndOptimisticEditsRaceSafely()throws IOException{
        DepartureService service=service(new Memory(),definition("test:exit",1,false));long revision=service.state().revision();DepartureDefinition changed=definition("test:exit",2,false);
        service.replace(changed,1,revision);assertThrows(IllegalStateException.class,()->service.replace(definition("test:exit",2,false),1,revision));
        service.begin(new DepartureIntent(OPERATION,"test:exit",2,ACTOR,List.of(ACTOR),false,Optional.empty(),"2026-10-26T00:00:00Z"),service.state().revision());
        assertThrows(IllegalStateException.class,()->service.replace(definition("test:exit",3,false),2,service.state().revision()));assertThrows(IllegalStateException.class,()->service.retire("test:exit",2,service.state().revision()));
    }

    @Test void unusedRetirementLeavesPermanentTombstoneAndViewsAreReadOnly()throws IOException{
        DepartureService service=service(new Memory(),definition("test:exit",1,false));service.retire("test:exit",1,service.state().revision());assertTrue(service.state().tombstones().contains("test:exit"));
        assertThrows(IllegalStateException.class,()->service.create(definition("test:exit",1,false),service.state().revision()));assertThrows(UnsupportedOperationException.class,()->service.state().tombstones().clear());assertThrows(UnsupportedOperationException.class,()->service.state().definitions().clear());
    }

    @Test void rosterValidationRejectsEmptyDuplicateAndMissingActor(){
        assertThrows(IllegalArgumentException.class,()->new DepartureIntent(OPERATION,"test:exit",1,ACTOR,List.of(),false,Optional.empty(),"2026-10-26T00:00:00Z"));
        assertThrows(IllegalArgumentException.class,()->new DepartureIntent(OPERATION,"test:exit",1,ACTOR,List.of(ACTOR,ACTOR),false,Optional.empty(),"2026-10-26T00:00:00Z"));
        assertThrows(IllegalArgumentException.class,()->new DepartureIntent(OPERATION,"test:exit",1,ACTOR,List.of(PASSENGER),false,Optional.empty(),"2026-10-26T00:00:00Z"));
    }

    private static DepartureService service(Memory memory,DepartureDefinition definition)throws IOException{DepartureService service=new DepartureService(memory);service.create(definition,0);return service;}
    private static DepartureDefinition definition(String id,long revision,boolean linked){return new DepartureDefinition(id,"Way out",revision,Optional.of(new SiteRef("test:anchor",4)),Optional.of(new SiteRef("test:boarding",7)),"First warning","Final warning",linked?Optional.of(new AreaLink("test:scene",AreaState.END)):Optional.empty(),true);}
    private static DepartureIntent intent(String definition,UUID operation,boolean linked,boolean conclude){Optional<PreparedAreaEffect> effect=linked?Optional.of(new PreparedAreaEffect(UUID.fromString("00000000-0000-0000-0000-000000000020"),"test:scene",AreaState.END,"a".repeat(64))):Optional.empty();return new DepartureIntent(operation,definition,1,ACTOR,List.of(PASSENGER,ACTOR),conclude,effect,"2026-10-26T00:00:00Z");}
    private static final class Memory implements DepartureRepository{DepartureState state=DepartureState.empty();boolean fail;@Override public DepartureState load(){return state;}@Override public void save(DepartureState replacement)throws IOException{if(fail)throw new IOException("injected save failure");state=replacement;}}
}
