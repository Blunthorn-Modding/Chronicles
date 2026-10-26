package io.github.chronicle.core.departures;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;

/**
 * Save-before-publish departure catalog and irreversible ledger.
 * Native callers must durably save the real player/world/campaign effect before attesting it here.
 */
public final class DepartureService {
    private final DepartureRepository repository;
    private DepartureState current;

    public DepartureService(DepartureRepository repository) throws IOException {
        if (repository == null) throw new IllegalArgumentException("departure repository is required");
        this.repository = repository;
        this.current = repository.load();
        if (current == null) throw new IOException("departure repository returned no state");
    }

    public synchronized DepartureState state() { return current; }

    public synchronized DepartureDefinition create(DepartureDefinition definition, long expectedStateRevision) throws IOException {
        requireState(expectedStateRevision);
        if (definition == null || definition.revision() != 1) throw new IllegalArgumentException("a new departure must have revision 1");
        if (current.definitions().containsKey(definition.id()) || current.tombstones().contains(definition.id())) throw new IllegalStateException("departure ID already exists or was retired");
        LinkedHashMap<String,DepartureDefinition> definitions=new LinkedHashMap<>(current.definitions()); definitions.put(definition.id(),definition);
        publish(definitions,current.tombstones(),current.usedByDefinition(),current.operations(),current.departedByPlayer(),current.concluded());
        return definition;
    }

    public synchronized DepartureDefinition replace(DepartureDefinition replacement, long expectedDefinitionRevision, long expectedStateRevision) throws IOException {
        requireState(expectedStateRevision);
        if (replacement == null) throw new IllegalArgumentException("replacement departure is required");
        DepartureDefinition prior=requireDefinition(replacement.id());
        if (prior.revision()!=expectedDefinitionRevision)throw new IllegalStateException("departure definition changed");
        if (replacement.revision()!=Math.addExact(expectedDefinitionRevision,1))throw new IllegalArgumentException("replacement revision must advance exactly once");
        if(current.usedByDefinition().containsKey(prior.id()))throw new IllegalStateException("a used or committed departure definition is immutable");
        LinkedHashMap<String,DepartureDefinition> definitions=new LinkedHashMap<>(current.definitions());definitions.put(replacement.id(),replacement);
        publish(definitions,current.tombstones(),current.usedByDefinition(),current.operations(),current.departedByPlayer(),current.concluded());
        return replacement;
    }

    public synchronized void retire(String id,long expectedDefinitionRevision,long expectedStateRevision)throws IOException{
        requireState(expectedStateRevision);DepartureDefinition prior=requireDefinition(id);
        if(prior.revision()!=expectedDefinitionRevision)throw new IllegalStateException("departure definition changed");
        if(current.usedByDefinition().containsKey(id))throw new IllegalStateException("used departure history cannot be retired");
        LinkedHashMap<String,DepartureDefinition> definitions=new LinkedHashMap<>(current.definitions());definitions.remove(id);
        LinkedHashSet<String> tombstones=new LinkedHashSet<>(current.tombstones());tombstones.add(id);
        publish(definitions,tombstones,current.usedByDefinition(),current.operations(),current.departedByPlayer(),current.concluded());
    }

    /** Atomically commits the used reservation and every passenger's permanent departed flag before effects. */
    public synchronized DepartureOperation begin(DepartureIntent intent,long expectedStateRevision)throws IOException{
        if(intent==null)throw new IllegalArgumentException("departure intent is required");
        DepartureOperation existing=current.operations().get(intent.operationId());
        if(existing!=null){if(!existing.matches(intent))throw new IllegalStateException("departure operation UUID was reused for different intent");return existing;}
        requireState(expectedStateRevision);
        if(current.concluded().isPresent())throw new IllegalStateException("campaign already has a terminal departure outcome");
        if(current.operations().values().stream().anyMatch(operation->operation.stage()!=DepartureOperation.Stage.COMPLETE))throw new IllegalStateException("another departure operation is still pending recovery");
        if(current.operations().size()>=DepartureState.MAX_HISTORY)throw new IllegalStateException("departure operation history is full");
        DepartureDefinition definition=requireDefinition(intent.definitionId());
        if(definition.revision()!=intent.expectedDefinitionRevision())throw new IllegalStateException("departure definition changed");
        definition.requireExecutable();
        if(definition.areaLink().isPresent()!=intent.preparedAreaEffect().isPresent())throw new IllegalStateException("linked area must be prepared exactly; missing is not no link");
        if(intent.preparedAreaEffect().isPresent()&&!intent.preparedAreaEffect().get().matches(definition.areaLink().orElseThrow()))throw new IllegalStateException("prepared area evidence is stale or for another link");
        if(current.usedByDefinition().containsKey(definition.id()))throw new IllegalStateException("departure is already used or committed");
        for(UUID player:intent.onlineParticipants())if(current.departedByPlayer().containsKey(player))throw new IllegalStateException("departure roster contains an already departed player");
        DepartureOperation operation=DepartureOperation.committed(definition,intent);
        LinkedHashMap<String,UUID> used=new LinkedHashMap<>(current.usedByDefinition());used.put(definition.id(),operation.operationId());
        LinkedHashMap<UUID,DepartureOperation> operations=new LinkedHashMap<>(current.operations());operations.put(operation.operationId(),operation);
        LinkedHashMap<UUID,UUID> departed=new LinkedHashMap<>(current.departedByPlayer());for(UUID player:operation.onlineParticipants())departed.put(player,operation.operationId());
        publish(current.definitions(),current.tombstones(),used,operations,departed,current.concluded());
        return operation;
    }

    public synchronized DepartureOperation recordPlayerEffects(UUID operationId,String durableEvidence)throws IOException{
        DepartureReceipt receipt=DepartureReceipt.saved(durableEvidence);DepartureOperation operation=requireOperation(operationId);
        if(operation.stage().ordinal()>=DepartureOperation.Stage.PLAYER_EFFECTS_SAVED.ordinal())return requireSame(operation.playerEffects().orElseThrow(),receipt,operation,"player");
        if(operation.stage()!=DepartureOperation.Stage.COMMITTED)throw new IllegalStateException("player effects are out of order");
        return replaceOperation(operation.withPlayer(receipt),current.concluded());
    }

    public synchronized DepartureOperation recordAreaEffects(UUID operationId,String durableEvidence)throws IOException{
        DepartureOperation operation=requireOperation(operationId);if(operation.preparedAreaEffect().isEmpty())throw new IllegalStateException("operation has no linked area effect");
        return recordArea(operation,DepartureReceipt.saved(durableEvidence));
    }
    public synchronized DepartureOperation recordNoAreaEffect(UUID operationId)throws IOException{
        DepartureOperation operation=requireOperation(operationId);if(operation.preparedAreaEffect().isPresent())throw new IllegalStateException("linked area effect cannot be skipped");
        return recordArea(operation,DepartureReceipt.notRequired());
    }
    private DepartureOperation recordArea(DepartureOperation operation,DepartureReceipt receipt)throws IOException{
        if(operation.stage().ordinal()>=DepartureOperation.Stage.AREA_EFFECTS_SAVED.ordinal())return requireSame(operation.areaEffects().orElseThrow(),receipt,operation,"area");
        if(operation.stage()!=DepartureOperation.Stage.PLAYER_EFFECTS_SAVED)throw new IllegalStateException("area effects are out of order");
        return replaceOperation(operation.withArea(receipt),current.concluded());
    }

    public synchronized DepartureOperation recordConclusion(UUID operationId,String durableEvidence,String concludedAt)throws IOException{
        DepartureOperation operation=requireOperation(operationId);if(!operation.concludeCampaign())throw new IllegalStateException("operation has no campaign conclusion obligation");
        DepartureReceipt receipt=DepartureReceipt.saved(durableEvidence);
        if(operation.stage().ordinal()>=DepartureOperation.Stage.CONCLUSION_SAVED.ordinal()){
            requireSame(operation.conclusion().orElseThrow(),receipt,operation,"conclusion");
            CampaignConclusion outcome=current.concluded().orElseThrow();
            if(!outcome.operationId().equals(operationId)||!outcome.receipt().equals(receipt)||!outcome.concludedAt().equals(concludedAt))throw new IllegalStateException("campaign conclusion evidence changed");
            return operation;
        }
        if(operation.stage()!=DepartureOperation.Stage.AREA_EFFECTS_SAVED)throw new IllegalStateException("campaign conclusion is out of order");
        if(current.concluded().isPresent())throw new IllegalStateException("campaign already has a different terminal outcome");
        CampaignConclusion outcome=new CampaignConclusion(operationId,receipt,concludedAt);
        return replaceOperation(operation.withConclusion(receipt),Optional.of(outcome));
    }
    public synchronized DepartureOperation recordNoConclusion(UUID operationId)throws IOException{
        DepartureOperation operation=requireOperation(operationId);if(operation.concludeCampaign())throw new IllegalStateException("required campaign conclusion cannot be skipped");
        DepartureReceipt receipt=DepartureReceipt.notRequired();
        if(operation.stage().ordinal()>=DepartureOperation.Stage.CONCLUSION_SAVED.ordinal())return requireSame(operation.conclusion().orElseThrow(),receipt,operation,"conclusion");
        if(operation.stage()!=DepartureOperation.Stage.AREA_EFFECTS_SAVED)throw new IllegalStateException("no-conclusion receipt is out of order");
        return replaceOperation(operation.withConclusion(receipt),current.concluded());
    }

    public synchronized DepartureOperation complete(UUID operationId)throws IOException{
        DepartureOperation operation=requireOperation(operationId);if(operation.stage()==DepartureOperation.Stage.COMPLETE)return operation;
        if(operation.stage()!=DepartureOperation.Stage.CONCLUSION_SAVED)throw new IllegalStateException("departure cannot complete with pending obligations");
        if(operation.concludeCampaign()&&(current.concluded().isEmpty()||!current.concluded().get().operationId().equals(operationId)))throw new IllegalStateException("departure cannot complete before its campaign outcome is durable");
        return replaceOperation(operation.complete(),current.concluded());
    }

    private DepartureOperation requireSame(DepartureReceipt actual,DepartureReceipt requested,DepartureOperation operation,String field){if(!actual.equals(requested))throw new IllegalStateException(field+" effect retry used different evidence");return operation;}
    private DepartureOperation requireOperation(UUID id){DepartureOperation operation=current.operations().get(id);if(operation==null)throw new IllegalStateException("departure operation identity changed");return operation;}
    private DepartureDefinition requireDefinition(String id){DepartureDefinition result=current.definitions().get(id);if(result==null)throw new IllegalStateException("departure definition is missing or retired");return result;}
    private void requireState(long expected){if(current.revision()!=expected)throw new IllegalStateException("departure state changed");}
    private DepartureOperation replaceOperation(DepartureOperation operation,Optional<CampaignConclusion> outcome)throws IOException{LinkedHashMap<UUID,DepartureOperation> operations=new LinkedHashMap<>(current.operations());operations.put(operation.operationId(),operation);publish(current.definitions(),current.tombstones(),current.usedByDefinition(),operations,current.departedByPlayer(),outcome);return operation;}
    private void publish(java.util.Map<String,DepartureDefinition> definitions,java.util.Set<String> tombstones,java.util.Map<String,UUID> used,java.util.Map<UUID,DepartureOperation> operations,java.util.Map<UUID,UUID> departed,Optional<CampaignConclusion> conclusion)throws IOException{DepartureState candidate=new DepartureState(Math.addExact(current.revision(),1),definitions,tombstones,used,operations,departed,conclusion);repository.save(candidate);current=candidate;}
}
