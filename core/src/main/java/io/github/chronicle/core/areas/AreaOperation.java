package io.github.chronicle.core.areas;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Durable application/deletion evidence. A fresh UUID means a fresh deliberate application. */
public record AreaOperation(UUID operationId, Intent intent, String areaId, String areaName, long areaRevision,
                            AreaBounds bounds, Optional<AreaState> requestedState,
                            Optional<SnapshotRef> before, Optional<SnapshotRef> after,
                            UUID actor, int cursor, Stage stage, boolean deletionObligation,
                            String detail, String createdAt) {
    public enum Intent { APPLY, DELETE_APPLY, DELETE_UNCHANGED }
    public enum Stage { PREPARED, APPLYING, CONFLICT, COMPLETE }
    public AreaOperation {
        Objects.requireNonNull(operationId,"operation ID is required");Objects.requireNonNull(intent,"intent is required");
        if(areaId==null||!io.github.chronicle.core.authoring.AuthoringSite.ID.matcher(areaId).matches())throw new IllegalArgumentException("area ID is invalid");
        AreaSnapshotCodec.requireUtf8(areaName,1,AreaChange.MAX_NAME_BYTES,"area name");
        if(areaRevision<1)throw new IllegalArgumentException("area revision must be positive");Objects.requireNonNull(bounds,"bounds are required");
        requestedState=requestedState==null?Optional.empty():requestedState;before=before==null?Optional.empty():before;after=after==null?Optional.empty():after;
        Objects.requireNonNull(actor,"actor is required");Objects.requireNonNull(stage,"stage is required");
        AreaSnapshotCodec.requireUtf8(detail==null?"":detail,0,4096,"operation detail");detail=detail==null?"":detail;
        AreaSnapshotCodec.requireUtf8(createdAt,1,128,"created time");
        if(intent==Intent.DELETE_UNCHANGED){if(!deletionObligation||requestedState.isPresent()||before.isPresent()||after.isPresent()||cursor!=0)throw new IllegalArgumentException("unchanged deletion cannot contain world snapshots");}
        else {if(intent==Intent.DELETE_APPLY&&!deletionObligation||intent==Intent.APPLY&&deletionObligation)throw new IllegalArgumentException("deletion obligation does not match intent");if(requestedState.isEmpty()||before.isEmpty()||after.isEmpty())throw new IllegalArgumentException("apply operation requires requested, before and after snapshots");requireRef(before.get(),bounds);requireRef(after.get(),bounds);if(cursor<0||cursor>bounds.cells())throw new IllegalArgumentException("operation cursor is outside bounds");}
        if(intent==Intent.DELETE_UNCHANGED&&stage!=Stage.PREPARED&&stage!=Stage.COMPLETE)throw new IllegalArgumentException("unchanged deletion has no applying stage");
        if(stage==Stage.PREPARED&&cursor!=0)throw new IllegalArgumentException("prepared operation cursor must be zero");
        if(stage==Stage.CONFLICT&&cursor>=bounds.cells())throw new IllegalArgumentException("conflict cursor must identify a cell");
        if(stage==Stage.COMPLETE&&intent!=Intent.DELETE_UNCHANGED&&cursor!=bounds.cells())throw new IllegalArgumentException("completed apply must cover every cell");
    }
    public boolean unresolved(){return stage!=Stage.COMPLETE;}
    public AreaOperation with(Stage next,int nextCursor,String nextDetail){return new AreaOperation(operationId,intent,areaId,areaName,areaRevision,bounds,requestedState,before,after,actor,nextCursor,next,deletionObligation,nextDetail,createdAt);}
    private static void requireRef(SnapshotRef ref,AreaBounds bounds){if(!ref.bounds().equals(bounds))throw new IllegalArgumentException("operation snapshot bounds do not match area");}
}
