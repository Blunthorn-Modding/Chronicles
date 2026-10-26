package io.github.chronicle.core.areas;

import io.github.chronicle.core.authoring.AuthoringSite;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

/** Save-before-publish definition edits with exact region and area revision pins. */
public final class AreaCatalogService {
    private final AreaCatalogRepository repository; private AreaCatalog state;
    public AreaCatalogService(AreaCatalogRepository repository)throws IOException{this.repository=java.util.Objects.requireNonNull(repository);this.state=repository.load();}
    public synchronized AreaCatalog state(){return state;}
    public synchronized AreaChange create(String id,String name,AuthoringSite.Region currentRegion,AreaState first,SnapshotRef ref,long expectedCatalogRevision)throws IOException{
        expectCatalog(expectedCatalogRevision);if(state.areas().containsKey(id)||state.tombstones().contains(id))throw new IllegalStateException("area ID already exists or is retired");if(state.areas().size()>=AreaCatalog.MAX_AREAS)throw new IllegalStateException("area limit reached");AreaBounds bounds=AreaBounds.from(currentRegion);if(!ref.bounds().equals(bounds))throw new IllegalStateException("capture does not match current Core Region");AreaChange area=new AreaChange(id,name,bounds,1,first==AreaState.START?Optional.of(ref):Optional.empty(),first==AreaState.END?Optional.of(ref):Optional.empty());Map<String,AreaChange> areas=new LinkedHashMap<>(state.areas());areas.put(id,area);publish(new AreaCatalog(state.revision()+1,areas,state.tombstones()));return area;
    }
    public synchronized AreaChange replaceSnapshot(String id,long expectedAreaRevision,AuthoringSite.Region currentRegion,AreaState slot,SnapshotRef ref)throws IOException{
        AreaChange area=require(id,expectedAreaRevision,currentRegion);if(!ref.bounds().equals(area.bounds()))throw new IllegalStateException("capture does not match area bounds");AreaChange next=area.replace(slot,ref);Map<String,AreaChange> areas=new LinkedHashMap<>(state.areas());areas.put(id,next);publish(new AreaCatalog(state.revision()+1,areas,state.tombstones()));return next;
    }
    public synchronized void retire(String id,long expectedAreaRevision,AuthoringSite.Region currentRegion,AreaOperation completedDelete)throws IOException{
        AreaChange area=requireDefinition(id,expectedAreaRevision);if(completedDelete==null||!completedDelete.deletionObligation()||completedDelete.stage()!=AreaOperation.Stage.COMPLETE||!completedDelete.areaId().equals(id)||completedDelete.areaRevision()!=area.revision()||!completedDelete.bounds().equals(area.bounds()))throw new IllegalStateException("a completed matching deletion obligation is required");
        if(completedDelete.intent()!=AreaOperation.Intent.DELETE_APPLY)throw new IllegalStateException("a state-applying deletion obligation is required");
        requireRegion(area,currentRegion);
        if(completedDelete.intent()==AreaOperation.Intent.DELETE_APPLY){AreaState requested=completedDelete.requestedState().orElseThrow();if(!area.snapshot(requested).equals(completedDelete.after()))throw new IllegalStateException("completed deletion did not apply the pinned current area snapshot");}
        retire(area);
    }
    /** Retires without reading terrain; only valid for a completed leave-unchanged deletion. */
    public synchronized void retire(String id,long expectedAreaRevision,AreaOperation completedDelete)throws IOException{
        AreaChange area=requireDefinition(id,expectedAreaRevision);if(completedDelete==null||completedDelete.intent()!=AreaOperation.Intent.DELETE_UNCHANGED||!completedDelete.deletionObligation()||completedDelete.stage()!=AreaOperation.Stage.COMPLETE||!completedDelete.areaId().equals(id)||completedDelete.areaRevision()!=area.revision()||!completedDelete.bounds().equals(area.bounds()))throw new IllegalStateException("a completed matching unchanged deletion obligation is required");
        retire(area);
    }
    private void retire(AreaChange area)throws IOException{
        String id=area.id();
        Map<String,AreaChange> areas=new LinkedHashMap<>(state.areas());areas.remove(id);LinkedHashSet<String> tombstones=new LinkedHashSet<>(state.tombstones());tombstones.add(id);publish(new AreaCatalog(state.revision()+1,areas,tombstones));
    }
    private AreaChange require(String id,long revision,AuthoringSite.Region region){AreaChange area=requireDefinition(id,revision);requireRegion(area,region);return area;}
    private AreaChange requireDefinition(String id,long revision){AreaChange area=state.areas().get(id);if(area==null)throw new IllegalStateException("area is missing");if(area.revision()!=revision)throw new IllegalStateException("area revision is stale");return area;}
    private static void requireRegion(AreaChange area,AuthoringSite.Region region){if(region==null||!area.bounds().matches(region))throw new IllegalStateException("Core Region geometry/revision is stale");}
    private void expectCatalog(long revision){if(state.revision()!=revision)throw new IllegalStateException("area catalog revision is stale");}
    private void publish(AreaCatalog next)throws IOException{repository.save(next);state=next;}
}
