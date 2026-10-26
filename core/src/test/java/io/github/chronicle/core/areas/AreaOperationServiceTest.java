package io.github.chronicle.core.areas;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AreaOperationServiceTest {
    @Test void sameOperationRetryIsIdempotentButFreshDeliberateApplyIsAllowedAfterCompletion() throws IOException {
        Memory memory=new Memory();AreaOperationService service=new AreaOperationService(memory);AreaOperation first=operation(UUID.randomUUID(),false);
        assertEquals(first,service.prepare(first));assertEquals(first,service.prepare(first));
        service.begin(first.operationId());service.checkpoint(first.operationId(),2);service.complete(first.operationId());
        AreaOperation second=operation(UUID.randomUUID(),false);service.prepare(second);assertEquals(2,service.state().operations().size());
        assertThrows(IllegalStateException.class,()->service.prepare(operation(second.operationId(),true)));
    }

    @Test void unresolvedRaceAndFailedSavePublishNoFalseProgress() throws IOException {
        Memory memory=new Memory();AreaOperationService service=new AreaOperationService(memory);AreaOperation first=operation(UUID.randomUUID(),false);service.prepare(first);
        assertThrows(IllegalStateException.class,()->service.prepare(operation(UUID.randomUUID(),false)));
        memory.fail=true;assertThrows(IOException.class,()->service.begin(first.operationId()));
        assertEquals(AreaOperation.Stage.PREPARED,service.state().operations().get(first.operationId()).stage());
    }

    @Test void checkpointCannotMoveBackwardOrCompleteBeforeAllCells() throws IOException {
        AreaOperationService service=new AreaOperationService(new Memory());AreaOperation operation=operation(UUID.randomUUID(),false);service.prepare(operation);service.begin(operation.operationId());service.checkpoint(operation.operationId(),1);
        assertThrows(IllegalArgumentException.class,()->service.checkpoint(operation.operationId(),0));assertThrows(IllegalStateException.class,()->service.complete(operation.operationId()));
    }

    @Test void conflictResumePreservesPinnedEvidenceAndCursor() throws IOException {
        AreaOperationService service=new AreaOperationService(new Memory());AreaOperation operation=operation(UUID.randomUUID(),false);service.prepare(operation);service.begin(operation.operationId());service.checkpoint(operation.operationId(),1);AreaOperation conflict=service.conflict(operation.operationId(),1,"third state");
        AreaOperation resumed=service.resumeConflict(operation.operationId());assertEquals(AreaOperation.Stage.APPLYING,resumed.stage());assertEquals(conflict.cursor(),resumed.cursor());assertEquals(conflict.before(),resumed.before());assertEquals(conflict.after(),resumed.after());assertEquals(conflict.detail(),resumed.detail());assertEquals(conflict.operationId(),resumed.operationId());
        assertThrows(IllegalStateException.class,()->service.resumeConflict(operation.operationId()));
    }

    @Test void recoveryRecognizesBeforeAfterAndThirdStateWithoutRollback() {
        AreaSnapshot before=AreaSnapshotStoreTest.snapshot();AreaSnapshot after=new AreaSnapshot(before.bounds(),java.util.List.of("{\"Name\":\"minecraft:dirt\"}"),new int[]{0,0},Map.of());
        AreaOperation op=operation(UUID.randomUUID(),false,before,after);
        assertEquals(AreaRecovery.CellStatus.BEFORE,AreaRecovery.classify(op,0,before.cell(0),before,after));
        assertEquals(AreaRecovery.CellStatus.AFTER,AreaRecovery.classify(op,0,after.cell(0),before,after));
        assertEquals(AreaRecovery.CellStatus.CONFLICT,AreaRecovery.classify(op,0,new AreaCell("{\"Name\":\"minecraft:gold_block\"}",null),before,after));
    }

    static AreaOperation operation(UUID id,boolean deletion){AreaSnapshot s=AreaSnapshotStoreTest.snapshot();return operation(id,deletion,s,s);}
    static AreaOperation operation(UUID id,boolean deletion,AreaSnapshot before,AreaSnapshot after){return new AreaOperation(id,deletion?AreaOperation.Intent.DELETE_APPLY:AreaOperation.Intent.APPLY,"area","Test area",1,before.bounds(),Optional.of(AreaState.END),Optional.of(AreaSnapshotCodec.encode(before).ref()),Optional.of(AreaSnapshotCodec.encode(after).ref()),UUID.fromString("11111111-1111-1111-1111-111111111111"),0,AreaOperation.Stage.PREPARED,deletion,"",Instant.EPOCH.toString());}
    private static final class Memory implements AreaOperationRepository{AreaOperationJournal state=AreaOperationJournal.empty();boolean fail;public AreaOperationJournal load(){return state;}public void save(AreaOperationJournal value)throws IOException{if(fail)throw new IOException("injected");state=value;}}
}
