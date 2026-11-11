package io.github.chronicle.core.areas;

import java.io.IOException;import java.util.LinkedHashMap;import java.util.Map;import java.util.UUID;
/** Journal-first lifecycle. Callers must durably flush native cell writes before advancing cursor/completing. */
public final class AreaOperationService{
    private final AreaOperationRepository repository;private AreaOperationJournal state;
    public AreaOperationService(AreaOperationRepository repository)throws IOException{this.repository=java.util.Objects.requireNonNull(repository);state=repository.load();}
    public synchronized AreaOperationJournal state(){return state;}
    public synchronized void verifyUnchanged()throws IOException{repository.verifyUnchanged();}
    public synchronized AreaOperation prepare(AreaOperation operation)throws IOException{
        if(operation.stage()!=AreaOperation.Stage.PREPARED||operation.cursor()!=0)throw new IllegalArgumentException("new operation must be prepared at cursor zero");AreaOperation existing=state.operations().get(operation.operationId());if(existing!=null){if(existing.equals(operation))return existing;throw new IllegalStateException("operation UUID already identifies different evidence");}
        if(state.operations().size()>=AreaOperationJournal.MAX_OPERATIONS)throw new IllegalStateException("operation journal limit reached");for(AreaOperation candidate:state.operations().values())if(candidate.unresolved()&&candidate.areaId().equals(operation.areaId()))throw new IllegalStateException("area already has an unresolved operation");return publish(operation);
    }
    public synchronized AreaOperation begin(UUID id)throws IOException{AreaOperation o=require(id);if(o.stage()==AreaOperation.Stage.APPLYING)return o;if(o.stage()!=AreaOperation.Stage.PREPARED)throw new IllegalStateException("operation cannot begin from "+o.stage());return publish(o.with(AreaOperation.Stage.APPLYING,o.cursor(),o.detail()));}
    public synchronized AreaOperation checkpoint(UUID id,int cursor)throws IOException{AreaOperation o=require(id);if(o.stage()!=AreaOperation.Stage.APPLYING)throw new IllegalStateException("only applying operations can checkpoint");if(o.intent()==AreaOperation.Intent.DELETE_UNCHANGED||cursor<o.cursor()||cursor>o.bounds().cells())throw new IllegalArgumentException("checkpoint cursor is invalid");return cursor==o.cursor()?o:publish(o.with(o.stage(),cursor,o.detail()));}
    public synchronized AreaOperation conflict(UUID id,int cursor,String detail)throws IOException{AreaOperation o=require(id);if(o.stage()!=AreaOperation.Stage.APPLYING)throw new IllegalStateException("only applying operations can conflict");if(cursor<o.cursor()||cursor>=o.bounds().cells())throw new IllegalArgumentException("conflict cursor is invalid");return publish(o.with(AreaOperation.Stage.CONFLICT,cursor,detail));}
    /** Caller must revalidate every cell against the pinned before/after evidence first. */
    public synchronized AreaOperation resumeConflict(UUID id)throws IOException{AreaOperation o=require(id);if(o.stage()!=AreaOperation.Stage.CONFLICT)throw new IllegalStateException("only conflicted operations can resume");return publish(o.with(AreaOperation.Stage.APPLYING,o.cursor(),o.detail()));}
    public synchronized AreaOperation complete(UUID id)throws IOException{AreaOperation o=require(id);if(o.stage()==AreaOperation.Stage.COMPLETE)return o;if(o.intent()==AreaOperation.Intent.DELETE_UNCHANGED){if(o.stage()!=AreaOperation.Stage.PREPARED)throw new IllegalStateException("unchanged deletion has invalid stage");return publish(o.with(AreaOperation.Stage.COMPLETE,0,o.detail()));}if(o.stage()!=AreaOperation.Stage.APPLYING||o.cursor()!=o.bounds().cells())throw new IllegalStateException("operation is not durably checkpointed through all cells");return publish(o.with(AreaOperation.Stage.COMPLETE,o.cursor(),o.detail()));}
    private AreaOperation require(UUID id){AreaOperation o=state.operations().get(id);if(o==null)throw new IllegalStateException("operation is missing");return o;}
    private AreaOperation publish(AreaOperation o)throws IOException{Map<UUID,AreaOperation> map=new LinkedHashMap<>(state.operations());map.put(o.operationId(),o);AreaOperationJournal next=new AreaOperationJournal(state.revision()+1,map);repository.save(next);state=next;return o;}
}
