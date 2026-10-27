package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.areas.*;
import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.content.*;
import io.github.chronicle.core.managed.*;
import io.github.chronicle.core.play.EventRegionChange;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One aggregate, loaded-only transaction for all saved Region changes owned by an Event run. */
public final class EventWorldBatches {
    public static final String HOLD="chronicle_core:event-world";
    private static final String PARENT_HOLD="chronicle_core:events",CONTENT_HOLD="chronicle_core:chests";
    private static final int BUDGET=256,CHECKPOINT_CELLS=4096;
    private static final long MAX_DECODED=64L*1024*1024;
    private static final Map<MinecraftServer,Runtime> WORLDS=new IdentityHashMap<>();
    private static boolean registered;
    private EventWorldBatches(){}

    public enum Status{PREPARED,APPLYING,TERRAIN_SAVED,WORLD_COMMITTED,CONFLICT}
    public record Prepared(UUID id,String sha256){public Prepared{Objects.requireNonNull(id);if(sha256==null||!sha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("event batch hash is invalid");}}

    public static synchronized void register(){
        if(registered)return;registered=true;
        ServerLifecycleEvents.SERVER_STARTED.register(EventWorldBatches::start);
        ServerTickEvents.END_SERVER_TICK.register(EventWorldBatches::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
    }

    public static void preflight(MinecraftServer server,UUID batchId,UUID actor,List<EventRegionChange> changes,
                                 BooleanSupplier stillEligible,Consumer<Prepared> ready,Consumer<String> refused){
        Runtime runtime=runtime(server);Objects.requireNonNull(batchId,"event batch ID is required");Objects.requireNonNull(actor,"event batch actor is required");
        Objects.requireNonNull(stillEligible,"event batch eligibility callback is required");Objects.requireNonNull(ready,"event batch ready callback is required");Objects.requireNonNull(refused,"event batch refusal callback is required");
        if(!runtime.ready)throw new IllegalStateException("Event Region batches are unavailable: "+runtime.problem);
        if(runtime.preparation!=null||runtime.applying!=null||runtime.journal.entries().values().stream().anyMatch(e->e.phase()!=EventBatchFiles.Phase.WORLD_COMMITTED))throw new IllegalStateException("Resolve the current Event Region batch first.");
        if(runtime.journal.entries().containsKey(batchId))throw new IllegalStateException("Event batch UUID is already committed.");
        if(WorldAreaOperations.hasReservation(server)||AreaChanges.busy(server))throw new IllegalStateException("Resolve the current Area operation before reviewing this Event.");
        if(RuntimeHolds.heldByOthers(server,Set.of(PARENT_HOLD)))throw new IllegalStateException("Another Chronicle operation currently holds world changes.");
        if(!stillEligible.getAsBoolean())throw new IllegalStateException("Event conditions changed before Region review.");
        try{
            Preparation preparation=prepare(server,batchId,actor,changes,stillEligible,ready,refused);
            runtime.preparation=preparation;runtime.problem="";RuntimeHolds.hold(server,HOLD,"Event Region changes are being captured and verified.");WorldAreaOperations.closeContainers(server);
        }catch(RuntimeException|IOException failure){throw new IllegalStateException(message(failure),failure);}
    }

    /** Creates durable execution authority. Call only after the parent EventExecution PREPARED pointer is durable. */
    public static void commit(MinecraftServer server,UUID id,String sha256,BooleanSupplier stillEligible)throws IOException{
        Runtime runtime=runtime(server);Objects.requireNonNull(stillEligible,"event batch eligibility callback is required");
        EventBatchFiles.Entry existing=runtime.journal.entries().get(id);
        if(existing!=null){if(!existing.planSha256().equals(sha256))throw new IllegalStateException("Event batch plan hash changed.");resume(server,id,sha256,stillEligible);return;}
        if(runtime.applying!=null||runtime.journal.entries().values().stream().anyMatch(e->e.phase()!=EventBatchFiles.Phase.WORLD_COMMITTED))throw new IllegalStateException("Resolve the current Event Region batch first.");
        EventBatchFiles.Plan plan=EventBatchFiles.loadPlan(server,id,sha256);if(!stillEligible.getAsBoolean())throw new IllegalStateException("Event conditions changed before the Region batch was committed.");
        EventBatchFiles.Entry entry=new EventBatchFiles.Entry(id,sha256,EventBatchFiles.Phase.PREPARED,EventBatchFiles.Step.PREVERIFY,0,0,"",Instant.now().toString());
        publish(runtime,entry);RuntimeHolds.hold(server,HOLD,"A committed Event Region batch is awaiting exact world completion.");
        try{loadApplying(server,runtime,plan,entry,stillEligible,true);}catch(Exception failure){runtime.problem=message(failure);ChronicleCore.LOGGER.error("Committed Event Region batch {} could not start; evidence retained",id,failure);if(failure instanceof IOException io)throw io;throw new IllegalStateException(runtime.problem,failure);}
    }

    /** Reattaches current phase/session/prerequisite authority after restart or a deliberate recovery attempt. */
    public static void resume(MinecraftServer server,UUID id,String sha256,BooleanSupplier stillEligible)throws IOException{
        Runtime runtime=runtime(server);Objects.requireNonNull(stillEligible,"event batch eligibility callback is required");EventBatchFiles.Entry entry=runtime.journal.entries().get(id);
        if(entry==null)throw new IllegalStateException("Event batch is not committed.");if(!entry.planSha256().equals(sha256))throw new IllegalStateException("Event batch plan hash changed.");if(entry.phase()==EventBatchFiles.Phase.WORLD_COMMITTED)return;
        if(runtime.applying!=null&&!runtime.applying.plan.id().equals(id))throw new IllegalStateException("Another Event batch is already running.");
        if(!stillEligible.getAsBoolean())throw new IllegalStateException("Event conditions are no longer eligible for Region recovery.");
        EventBatchFiles.Plan plan=EventBatchFiles.loadPlan(server,id,sha256);
        if(entry.phase()==EventBatchFiles.Phase.CONFLICT){entry=entry.at(EventBatchFiles.Phase.PREPARED,EventBatchFiles.Step.PREVERIFY,0,0,"");publish(runtime,entry);}
        else if(entry.phase()==EventBatchFiles.Phase.TERRAIN_SAVED){entry=EventBatchFiles.restartProof(plan,entry);publish(runtime,entry);}
        RuntimeHolds.hold(server,HOLD,"A committed Event Region batch is awaiting exact world completion.");loadApplying(server,runtime,plan,entry,stillEligible,false);runtime.problem="";
    }

    public static Optional<Status> status(MinecraftServer server,UUID id){Runtime runtime=runtime(server);EventBatchFiles.Entry entry=runtime.journal.entries().get(id);return entry==null?Optional.empty():Optional.of(Status.valueOf(entry.phase().name()));}
    public static void cancelUncommitted(MinecraftServer server,UUID id){Runtime runtime=runtime(server);if(runtime.journal.entries().containsKey(id))throw new IllegalStateException("A committed Event batch cannot be cancelled.");if(runtime.preparation!=null&&runtime.preparation.id.equals(id)){runtime.preparation=null;runtime.problem="";RuntimeHolds.release(server,HOLD);}}
    public static boolean ready(MinecraftServer server){Runtime runtime=WORLDS.get(server);return runtime!=null&&runtime.ready;}
    public static String problem(MinecraftServer server){Runtime runtime=WORLDS.get(server);return runtime==null?"Event Region batches are unavailable.":runtime.problem;}
    public static boolean hasReservation(MinecraftServer server){Runtime runtime=WORLDS.get(server);if(runtime==null)return true;return runtime.preparation!=null||runtime.applying!=null||runtime.journal.entries().values().stream().anyMatch(e->e.phase()!=EventBatchFiles.Phase.WORLD_COMMITTED)||!runtime.problem.isBlank();}

    static boolean reservedRegion(MinecraftServer server,String regionId){Runtime runtime=WORLDS.get(server);if(runtime==null)return false;if(!runtime.problem.isBlank())return true;if(runtime.preparation!=null&&runtime.preparation.targets.stream().anyMatch(t->t.bounds.regionId().equals(regionId)))return true;return plans(runtime).stream().flatMap(p->p.targets().stream()).anyMatch(t->t.bounds().regionId().equals(regionId));}
    static boolean reserved(MinecraftServer server,String dimension,BlockPosition position){Runtime runtime=WORLDS.get(server);if(runtime==null)return false;if(!runtime.problem.isBlank())return true;if(runtime.preparation!=null&&runtime.preparation.targets.stream().anyMatch(t->WorldAreaOperations.contains(t.bounds,dimension,position)))return true;return plans(runtime).stream().flatMap(p->p.targets().stream()).anyMatch(t->WorldAreaOperations.contains(t.bounds(),dimension,position));}

    private static void start(MinecraftServer server){
        Runtime runtime=new Runtime(new EventBatchFiles.Store(server));WORLDS.put(server,runtime);
        try{
            runtime.journal=runtime.store.load();List<EventBatchFiles.Entry> unresolved=runtime.journal.entries().values().stream().filter(e->e.phase()!=EventBatchFiles.Phase.WORLD_COMMITTED).toList();
            if(unresolved.size()>1)throw new IllegalStateException("Multiple unresolved Event Region batches require stopped-world repair.");
            if(!unresolved.isEmpty()){
                EventBatchFiles.Entry entry=unresolved.getFirst();EventBatchFiles.Plan plan=EventBatchFiles.loadPlan(server,entry.id(),entry.planSha256());AreaEffectFiles.Effects effects=loadEffects(server,plan);
                if(effects.managedPlan()!=null)ManagedBlocks.reinstateAreaReplacement(server,effects.managedPlan());reserveContent(server,plan,effects);
                RuntimeHolds.hold(server,HOLD,"A committed Event Region batch is awaiting coordinator recovery.");runtime.recoveredPlan=plan;
            }
            runtime.ready=true;
        }catch(Exception failure){runtime.problem=message(failure);RuntimeHolds.hold(server,HOLD,"Event Region batch evidence is unavailable; inspect the server log.");ChronicleCore.LOGGER.error("Event Region batch recovery unavailable; evidence preserved",failure);}
    }

    private static Preparation prepare(MinecraftServer server,UUID id,UUID actor,List<EventRegionChange> requested,BooleanSupplier eligible,Consumer<Prepared> ready,Consumer<String> refused)throws IOException{
        List<EventRegionChange> changes=List.copyOf(requested);if(changes.isEmpty()||changes.size()>8)throw new IllegalArgumentException("An Event may replace 1-8 saved Regions.");
        List<PreparationTarget> targets=new ArrayList<>();Set<String> areaIds=new HashSet<>(),regionIds=new HashSet<>();Set<Long> chunks=new HashSet<>();String dimension=null;int cells=0;long decoded=0;
        for(EventRegionChange change:changes){
            AreaChange area=exactArea(server,change);AreaBounds bounds=area.bounds();if(dimension==null)dimension=bounds.dimension();else if(!dimension.equals(bounds.dimension()))throw new IllegalArgumentException("All Event Region changes must be in one dimension.");
            if(!areaIds.add(area.id())||!regionIds.add(bounds.regionId()))throw new IllegalArgumentException("An Event Region target is duplicated.");
            for(PreparationTarget old:targets)if(overlaps(old.bounds,bounds))throw new IllegalArgumentException("Event Region targets overlap: "+old.area.id()+" and "+area.id()+".");
            cells=Math.addExact(cells,bounds.cells());if(cells>262_144)throw new IllegalArgumentException("Event Region changes exceed 262144 total cells.");
            addChunks(chunks,bounds);if(chunks.size()>64)throw new IllegalArgumentException("Event Region changes exceed 64 unique chunks.");
            ServerWorld world=WorldAreaOperations.world(server,bounds);WorldAreaOperations.loaded(world,bounds);WorldAreaOperations.clearOfPlayers(world,bounds,Set.of());AreaWorldPolicies.validate(server,bounds);
            AreaSnapshot after=AreaChanges.snapshots(server).load(change.snapshot());if(!AreaSnapshotCodec.encode(after).ref().equals(change.snapshot()))throw new IllegalStateException("Saved Region evidence changed: "+area.id());
            decoded=Math.addExact(decoded,decodedBytes(after));if(decoded>MAX_DECODED)throw new IllegalArgumentException("Event Region before-plus-after evidence exceeds 64 MiB.");
            targets.add(new PreparationTarget(change,area,bounds,after));
        }
        return new Preparation(id,actor,targets,eligible,ready,refused,ChestAuthoring.service(server).state().revision(),cells,chunks.size(),decoded);
    }

    private static void tick(MinecraftServer server){Runtime runtime=WORLDS.get(server);if(runtime==null||!runtime.ready)return;try{if(runtime.preparation!=null)prepareTick(server,runtime);else if(runtime.applying!=null)applyTick(server,runtime);}catch(Exception failure){if(runtime.preparation!=null)refusePreparation(server,runtime,failure);else stopApplying(server,runtime,failure);}}

    private static void prepareTick(MinecraftServer server,Runtime runtime)throws Exception{
        Preparation preparation=runtime.preparation;if(!preparation.eligible.getAsBoolean())throw new IllegalStateException("Event conditions changed during Region review.");WorldAreaOperations.closeContainers(server);
        PreparationTarget target=preparation.targets.get(preparation.target);validateTarget(server,target.area,target.change.snapshot());ServerWorld world=WorldAreaOperations.world(server,target.bounds);WorldAreaOperations.loaded(world,target.bounds);WorldAreaOperations.clearOfPlayers(world,target.bounds,Set.of());AreaWorldPolicies.validate(server,target.bounds);
        int end=Math.min(target.bounds.cells(),preparation.cursor+BUDGET);
        for(;preparation.cursor<end;preparation.cursor++){
            BlockPos pos=WorldAreaOperations.position(target.bounds,preparation.cursor);AreaCell live=AreaChanges.captureCell(server,world,pos,target.bounds,false);
            if(preparation.verifying){if(!target.before.cell(preparation.cursor).equals(live))throw new IllegalStateException("Event Region changed during review at "+pos.toShortString()+".");}
            else{AreaCell after=target.after.cell(preparation.cursor);WorldAreaOperations.validateSavedCell(server,world,pos,after);AreaWorldPolicies.validateCell(world,pos,live,after);target.capture(preparation.cursor,live);inspectCell(server,preparation,target,pos,live,after);}
        }
        if(preparation.cursor<target.bounds.cells())return;
        if(!preparation.verifying){target.before=target.snapshot();preparation.decodedBytes=Math.addExact(preparation.decodedBytes,target.decodedBytes);if(preparation.decodedBytes>MAX_DECODED)throw new IllegalArgumentException("Event Region before-plus-after evidence exceeds 64 MiB.");preparation.cursor=0;preparation.verifying=true;return;}
        preparation.target++;preparation.cursor=0;preparation.verifying=false;if(preparation.target<preparation.targets.size())return;
        if(ChestAuthoring.service(server).state().revision()!=preparation.contentRevision)throw new IllegalStateException("Chest or lore state changed during Event Region review.");
        List<EventBatchFiles.Target> targets=new ArrayList<>();for(PreparationTarget item:preparation.targets){validateTarget(server,item.area,item.change.snapshot());SnapshotRef before=AreaChanges.snapshots(server).put(item.before),after=AreaChanges.snapshots(server).put(item.after);if(!after.equals(item.change.snapshot()))throw new IllegalStateException("Saved Region evidence changed during review.");targets.add(new EventBatchFiles.Target(item.area.id(),item.area.revision(),item.change.state(),item.bounds,before,after));}
        AreaEffectFiles.Effects effects=effects(server,preparation);String effectsHash=AreaEffectFiles.save(server,effects);EventBatchFiles.Plan plan=new EventBatchFiles.Plan(preparation.id,preparation.actor,preparation.createdAt,effectsHash,preparation.contentRevision,preparation.decodedBytes,preparation.totalCells,preparation.uniqueChunks,targets);String hash=EventBatchFiles.savePlan(server,plan);
        runtime.preparation=null;runtime.problem="";RuntimeHolds.release(server,HOLD);preparation.ready.accept(new Prepared(preparation.id,hash));
    }

    private static void loadApplying(MinecraftServer server,Runtime runtime,EventBatchFiles.Plan plan,EventBatchFiles.Entry entry,BooleanSupplier eligible,boolean prepareManaged)throws IOException{
        exactPlan(server,plan);validateEntry(plan,entry);AreaEffectFiles.Effects effects=loadEffects(server,plan);
        if(effects.managedPlan()!=null){if(prepareManaged)ManagedBlocks.prepareAreaReplacement(server,effects.managedPlan());else ManagedBlocks.reinstateAreaReplacement(server,effects.managedPlan());}
        reserveContent(server,plan,effects);List<ApplyTarget> targets=new ArrayList<>();for(EventBatchFiles.Target target:plan.targets())targets.add(new ApplyTarget(target,AreaChanges.snapshots(server).load(target.before()),AreaChanges.snapshots(server).load(target.after())));
        runtime.applying=new Applying(plan,effects,targets,entry,eligible);runtime.recoveredPlan=plan;
    }

    private static void applyTick(MinecraftServer server,Runtime runtime)throws Exception{
        Applying applying=runtime.applying;if(!applying.eligible.getAsBoolean()){runtime.problem="Event conditions changed; the exact Region batch remains held.";return;}runtime.problem="";
        requireAllowedHolds(server);
        WorldAreaOperations.closeContainers(server);
        if(applying.entry.phase()==EventBatchFiles.Phase.TERRAIN_SAVED||applying.entry.step()==EventBatchFiles.Step.COMMIT){commitMetadata(server,runtime,applying);return;}
        ApplyTarget target=applying.targets.get(applying.entry.target());validateApplyTarget(server,target);int cursor=applying.entry.cursor(),end=Math.min(target.target.bounds().cells(),cursor+BUDGET);EventBatchFiles.Step step=applying.entry.step();
        for(;cursor<end;cursor++){
            BlockPos pos=WorldAreaOperations.position(target.target.bounds(),cursor);AreaCell live=AreaChanges.captureCell(server,target.world,pos,target.target.bounds(),false);AreaRecovery.CellStatus status=target.classify(cursor,live);
            if(step==EventBatchFiles.Step.WRITE){if(status==AreaRecovery.CellStatus.CONFLICT)throw new ConflictException("Conflicting Event Region cell at "+pos.toShortString()+"; exact evidence retained.");if(status==AreaRecovery.CellStatus.BEFORE){AreaWorldPolicies.validateCell(target.world,pos,live,target.after.cell(cursor));WorldAreaOperations.writeCell(server,target.world,pos,target.after.cell(cursor));if(!AreaChanges.captureCell(server,target.world,pos,target.target.bounds(),false).equals(target.after.cell(cursor)))throw new ConflictException("Event Region write did not match at "+pos.toShortString()+".");}}
            else if(status==AreaRecovery.CellStatus.CONFLICT||step!=EventBatchFiles.Step.PREVERIFY&&status!=AreaRecovery.CellStatus.AFTER)throw new ConflictException("Conflicting Event Region cell at "+pos.toShortString()+"; exact evidence retained.");
        }
        applying.entry=new EventBatchFiles.Entry(applying.entry.id(),applying.entry.planSha256(),applying.entry.phase(),step,applying.entry.target(),cursor,applying.entry.detail(),Instant.now().toString());
        if(cursor<target.target.bounds().cells()){if(cursor%CHECKPOINT_CELLS==0)publish(runtime,applying.entry);return;}
        advance(server,runtime,applying);
    }

    private static void advance(MinecraftServer server,Runtime runtime,Applying applying)throws Exception{
        EventBatchFiles.Entry entry=applying.entry;int target=entry.target();
        switch(entry.step()){
            case PREVERIFY->{if(target+1<applying.targets.size())entry=entry.at(EventBatchFiles.Phase.PREPARED,EventBatchFiles.Step.PREVERIFY,target+1,0,"");else entry=entry.at(EventBatchFiles.Phase.APPLYING,EventBatchFiles.Step.WRITE,0,0,"");}
            case WRITE->entry=entry.at(EventBatchFiles.Phase.APPLYING,EventBatchFiles.Step.VERIFY_TARGET,target,0,"");
            case VERIFY_TARGET->{AreaWorldPolicies.durable(server,applying.targets.get(target).target.bounds());if(!server.saveAll(true,true,true))throw new IOException("Minecraft did not confirm the Event Region child world save.");entry=target+1<applying.targets.size()?entry.at(EventBatchFiles.Phase.APPLYING,EventBatchFiles.Step.WRITE,target+1,0,""):entry.at(EventBatchFiles.Phase.APPLYING,EventBatchFiles.Step.VERIFY_ALL,0,0,"");}
            case VERIFY_ALL->{if(target+1<applying.targets.size())entry=entry.at(EventBatchFiles.Phase.APPLYING,EventBatchFiles.Step.VERIFY_ALL,target+1,0,"");else{requireAllowedHolds(server);if(!applying.eligible.getAsBoolean())throw new PausedException("Event conditions changed before the final Region save.");for(ApplyTarget item:applying.targets){validateApplyTarget(server,item);AreaWorldPolicies.durable(server,item.target.bounds());}if(!server.saveAll(true,true,true))throw new IOException("Minecraft did not confirm the final Event Region world save.");entry=entry.at(EventBatchFiles.Phase.TERRAIN_SAVED,EventBatchFiles.Step.COMMIT,applying.targets.size(),0,"");}}
            case COMMIT->throw new IllegalStateException("Event Region metadata commit did not run.");
        }
        applying.entry=entry;publish(runtime,entry);if(entry.phase()==EventBatchFiles.Phase.TERRAIN_SAVED)commitMetadata(server,runtime,applying);
    }

    private static void commitMetadata(MinecraftServer server,Runtime runtime,Applying applying)throws IOException{
        requireAllowedHolds(server);if(!applying.eligible.getAsBoolean())throw new PausedException("Event conditions changed before Region outcome commit.");exactPlan(server,applying.plan);for(ApplyTarget target:applying.targets)validateApplyTarget(server,target);
        if(applying.effects.managedPlan()!=null)ManagedBlocks.commitAreaReplacement(server,applying.effects.managedPlan());commitContent(server,applying.plan,applying.effects);
        if(applying.effects.managedPlan()!=null)ManagedBlocks.releaseAreaReplacement(server,applying.effects.managedPlan(),ManagedAreaReplacementPlan.Outcome.WORLD_DURABLY_SAVED);
        EventBatchFiles.Entry done=applying.entry.at(EventBatchFiles.Phase.WORLD_COMMITTED,EventBatchFiles.Step.COMMIT,applying.targets.size(),0,"");publish(runtime,done);runtime.applying=null;runtime.recoveredPlan=null;runtime.problem="";RuntimeHolds.release(server,HOLD);
    }

    private static void validateApplyTarget(MinecraftServer server,ApplyTarget target){
        validateTarget(server,WorldAreaOperations.requireArea(server,target.target.areaId(),target.target.areaRevision()),target.target.after());target.world=WorldAreaOperations.world(server,target.target.bounds());WorldAreaOperations.loaded(target.world,target.target.bounds());WorldAreaOperations.clearOfPlayers(target.world,target.target.bounds(),Set.of());AreaWorldPolicies.validate(server,target.target.bounds());
    }
    private static void requireAllowedHolds(MinecraftServer server)throws PausedException{if(RuntimeHolds.heldByOthers(server,Set.of(HOLD,PARENT_HOLD,CONTENT_HOLD)))throw new PausedException("Another Chronicle operation is holding world changes.");}
    private static void exactPlan(MinecraftServer server,EventBatchFiles.Plan plan){for(EventBatchFiles.Target target:plan.targets()){AreaChange area=WorldAreaOperations.requireArea(server,target.areaId(),target.areaRevision());if(!area.bounds().equals(target.bounds())||!area.requireSnapshot(target.state()).equals(target.after()))throw new IllegalStateException("Event Region target is stale: "+target.areaId());AreaChanges.currentRegion(server,area);}}
    private static void validateEntry(EventBatchFiles.Plan plan,EventBatchFiles.Entry entry){if(!plan.id().equals(entry.id()))throw new IllegalStateException("Event batch journal identity mismatch.");if(entry.step()==EventBatchFiles.Step.COMMIT){if(entry.target()!=plan.targets().size()||entry.cursor()!=0)throw new IllegalStateException("Event batch commit cursor is invalid.");return;}if(entry.target()>=plan.targets().size()||entry.cursor()>plan.targets().get(entry.target()).bounds().cells())throw new IllegalStateException("Event batch journal cursor is invalid.");}
    private static AreaChange exactArea(MinecraftServer server,EventRegionChange change){AreaChange area=WorldAreaOperations.requireArea(server,change.areaId(),change.areaRevision());if(!area.bounds().regionId().equals(change.region().id())||area.bounds().regionRevision()!=change.region().revision()||!area.requireSnapshot(change.state()).equals(change.snapshot()))throw new IllegalStateException("Event Region target is stale: "+change.areaId());AreaChanges.currentRegion(server,area);return area;}
    private static void validateTarget(MinecraftServer server,AreaChange area,SnapshotRef after){AreaChange current=WorldAreaOperations.requireArea(server,area.id(),area.revision());if(!current.equals(area)||!area.bounds().equals(after.bounds())||!area.start().filter(after::equals).isPresent()&&!area.end().filter(after::equals).isPresent())throw new IllegalStateException("Event Region target changed: "+area.id());AreaChanges.currentRegion(server,current);}

    private static AreaEffectFiles.Effects effects(MinecraftServer server,Preparation preparation)throws Exception{
        ManagedAreaReplacementPlan managed=preparation.managedPositions.isEmpty()?null:ManagedBlocks.planAreaReplacement(server,preparation.id,preparation.targets.getFirst().bounds.dimension(),preparation.managedPositions,preparation.placements);
        Set<String> affected=new HashSet<>(),retired=new HashSet<>();Map<String,AuthoringSite> sites=AuthoringRuntime.get(server).service().state().sites();
        for(ChestBinding binding:ChestAuthoring.service(server).state().bindings().values()){
            ChestTarget chest=binding.typed()?binding.directTarget():WorldAreaOperations.legacyTarget(sites,binding);if(chest==null)continue;PreparationTarget target=find(preparation.targets,chest.dimension(),new BlockPosition(chest.x(),chest.y(),chest.z()));if(target==null)continue;
            affected.add(binding.id());int index=WorldAreaOperations.index(target.bounds,new BlockPos(chest.x(),chest.y(),chest.z()));if(!WorldAreaOperations.id(WorldAreaOperations.decode(server,target.after.cell(index))).equals(chest.expectedBlock()))retired.add(binding.id());
        }
        for(AuthoringSite site:sites.values())if(site instanceof AuthoringSite.Anchor anchor){PreparationTarget target=find(preparation.targets,anchor.dimension(),anchor.position());if(target==null)continue;int index=WorldAreaOperations.index(target.bounds,new BlockPos(anchor.position().x(),anchor.position().y(),anchor.position().z()));if(!WorldAreaOperations.id(WorldAreaOperations.decode(server,target.after.cell(index))).equals(anchor.expectedBlock()))throw new IllegalStateException("Event Region replacement would invalidate Block site '"+anchor.id()+"'.");}
        return new AreaEffectFiles.Effects(preparation.id.toString(),managed==null?"":Base64.getEncoder().encodeToString(ManagedAreaReplacementCodec.encode(managed)),preparation.contentRevision,affected,retired,preparation.books,Set.of());
    }

    private static AreaEffectFiles.Effects loadEffects(MinecraftServer server,EventBatchFiles.Plan plan)throws IOException{
        EventBatchFiles.Target first=plan.targets().getFirst();AreaOperation shell=new AreaOperation(plan.id(),AreaOperation.Intent.APPLY,first.areaId(),first.areaId(),first.areaRevision(),first.bounds(),Optional.of(first.state()),Optional.of(first.before()),Optional.of(first.after()),plan.actor(),0,AreaOperation.Stage.PREPARED,false,plan.effectsHash(),plan.createdAt());return AreaEffectFiles.load(server,shell);
    }
    private static void reserveContent(MinecraftServer server,EventBatchFiles.Plan plan,AreaEffectFiles.Effects effects)throws IOException{if(!effects.hasContent())return;Set<ManagedBookRemoval> books=new HashSet<>();for(AreaEffectFiles.Book book:effects.books())books.add(new ManagedBookRemoval(book.bookId(),book.token()));ChestAuthoring.service(server).prepareAreaRemoval(plan.id(),effects.contentRevision(),effects.affectedBindings(),effects.retiredBindings(),books,plan.createdAt());ChestAuthoring.refreshHold(server);}
    private static void commitContent(MinecraftServer server,EventBatchFiles.Plan plan,AreaEffectFiles.Effects effects)throws IOException{if(effects.hasContent()){String hash=EventBatchFiles.savePlan(server,plan);ChestAuthoring.service(server).commitAreaRemoval(plan.id(),"event-world:"+plan.id()+":"+hash,plan.createdAt());ChestAuthoring.refreshHold(server);}}

    private static void inspectCell(MinecraftServer server,Preparation preparation,PreparationTarget target,BlockPos pos,AreaCell before,AreaCell after)throws Exception{
        String oldId=WorldAreaOperations.id(WorldAreaOperations.decode(server,before)),newId=WorldAreaOperations.id(WorldAreaOperations.decode(server,after));BlockPosition at=WorldAreaOperations.blockPosition(pos);
        if(ManagedBlocks.isManagedBlock(oldId)||ManagedBlocks.isManagedBlock(newId))preparation.managedPositions.add(at);ChronicleCore.managedBlocks().byBlock(newId).ifPresent(definition->preparation.placements.add(new ManagedAreaReplacementPlan.Placement(definition.typeId(),at)));
        if(before.blockEntityNbt()!=null)preparation.books.addAll(WorldAreaOperations.bookMarkers(server,StringNbtReader.readCompound(before.blockEntityNbt())));
    }

    private static void publish(Runtime runtime,EventBatchFiles.Entry entry)throws IOException{EventBatchFiles.Journal next=runtime.journal.put(entry);runtime.store.save(next);runtime.journal=next;}
    private static void refusePreparation(MinecraftServer server,Runtime runtime,Exception failure){Preparation preparation=runtime.preparation;runtime.preparation=null;runtime.problem="";RuntimeHolds.release(server,HOLD);preparation.refused.accept(message(failure));}
    private static void stopApplying(MinecraftServer server,Runtime runtime,Exception failure){Applying applying=runtime.applying;runtime.applying=null;runtime.problem=message(failure);try{if(failure instanceof ConflictException){EventBatchFiles.Entry conflict=applying.entry.at(EventBatchFiles.Phase.CONFLICT,applying.entry.step(),applying.entry.target(),applying.entry.cursor(),runtime.problem);publish(runtime,conflict);}}catch(Exception journalFailure){failure.addSuppressed(journalFailure);}RuntimeHolds.hold(server,HOLD,"Event Region batch stopped: "+runtime.problem);ChronicleCore.LOGGER.error("Event Region batch stopped; exact evidence retained",failure);}
    private static Runtime runtime(MinecraftServer server){if(!server.isOnThread())throw new IllegalStateException("Event Region batches require the server thread.");Runtime runtime=WORLDS.get(server);if(runtime==null)throw new IllegalStateException("Event Region batches are unavailable.");return runtime;}
    private static List<EventBatchFiles.Plan> plans(Runtime runtime){List<EventBatchFiles.Plan> result=new ArrayList<>();if(runtime.recoveredPlan!=null)result.add(runtime.recoveredPlan);if(runtime.applying!=null&&!result.contains(runtime.applying.plan))result.add(runtime.applying.plan);return result;}
    private static PreparationTarget find(List<PreparationTarget> targets,String dimension,BlockPosition pos){for(PreparationTarget target:targets)if(WorldAreaOperations.contains(target.bounds,dimension,pos))return target;return null;}
    private static boolean overlaps(AreaBounds a,AreaBounds b){return a.dimension().equals(b.dimension())&&a.min().x()<=b.max().x()&&a.max().x()>=b.min().x()&&a.min().y()<=b.max().y()&&a.max().y()>=b.min().y()&&a.min().z()<=b.max().z()&&a.max().z()>=b.min().z();}
    private static void addChunks(Set<Long> chunks,AreaBounds bounds){for(int x=Math.floorDiv(bounds.min().x(),16);x<=Math.floorDiv(bounds.max().x(),16);x++)for(int z=Math.floorDiv(bounds.min().z(),16);z<=Math.floorDiv(bounds.max().z(),16);z++)chunks.add(((long)x<<32)^(z&0xffffffffL));}
    private static long decodedBytes(AreaSnapshot snapshot){long value=(long)snapshot.cells()*Integer.BYTES;for(String state:snapshot.palette())value=Math.addExact(value,state.getBytes(StandardCharsets.UTF_8).length);for(String nbt:snapshot.blockEntities().values())value=Math.addExact(value,Integer.BYTES+nbt.getBytes(StandardCharsets.UTF_8).length);return value;}
    private static String message(Throwable failure){String value=Objects.requireNonNullElse(failure.getMessage(),"Event Region batch failed; inspect the server log.");return value.substring(0,Math.min(480,value.length()));}

    private static final class Runtime{final EventBatchFiles.Store store;EventBatchFiles.Journal journal=EventBatchFiles.Journal.empty();Preparation preparation;Applying applying;EventBatchFiles.Plan recoveredPlan;boolean ready;String problem="";Runtime(EventBatchFiles.Store store){this.store=store;}}
    private static final class Preparation{
        final UUID id,actor;final List<PreparationTarget> targets;final BooleanSupplier eligible;final Consumer<Prepared> ready;final Consumer<String> refused;final long contentRevision;final int totalCells,uniqueChunks;final String createdAt=Instant.now().toString();
        final Set<BlockPosition> managedPositions=new HashSet<>();final List<ManagedAreaReplacementPlan.Placement> placements=new ArrayList<>();final Set<AreaEffectFiles.Book> books=new HashSet<>();long decodedBytes;int target,cursor;boolean verifying;
        Preparation(UUID id,UUID actor,List<PreparationTarget> targets,BooleanSupplier eligible,Consumer<Prepared> ready,Consumer<String> refused,long contentRevision,int totalCells,int uniqueChunks,long afterBytes){this.id=id;this.actor=actor;this.targets=targets;this.eligible=eligible;this.ready=ready;this.refused=refused;this.contentRevision=contentRevision;this.totalCells=totalCells;this.uniqueChunks=uniqueChunks;decodedBytes=afterBytes;}
    }
    private static final class PreparationTarget{
        final EventRegionChange change;final AreaChange area;final AreaBounds bounds;final AreaSnapshot after;final List<String> palette=new ArrayList<>();final Map<String,Integer> lookup=new HashMap<>();final Map<Integer,String> entities=new HashMap<>();final int[] cells;long decodedBytes;AreaSnapshot before;
        PreparationTarget(EventRegionChange change,AreaChange area,AreaBounds bounds,AreaSnapshot after){this.change=change;this.area=area;this.bounds=bounds;this.after=after;cells=new int[bounds.cells()];decodedBytes=(long)cells.length*Integer.BYTES;}
        void capture(int cursor,AreaCell cell){Integer index=lookup.get(cell.blockStateJson());if(index==null){index=palette.size();lookup.put(cell.blockStateJson(),index);palette.add(cell.blockStateJson());decodedBytes=Math.addExact(decodedBytes,cell.blockStateJson().getBytes(StandardCharsets.UTF_8).length);}cells[cursor]=index;if(cell.blockEntityNbt()!=null){entities.put(cursor,cell.blockEntityNbt());decodedBytes=Math.addExact(decodedBytes,Integer.BYTES+cell.blockEntityNbt().getBytes(StandardCharsets.UTF_8).length);}if(decodedBytes>MAX_DECODED)throw new IllegalArgumentException("Event Region snapshot exceeds the retained evidence budget.");}
        AreaSnapshot snapshot(){return new AreaSnapshot(bounds,palette,cells,entities);}
    }
    private static final class ApplyTarget{final EventBatchFiles.Target target;final AreaSnapshot before,after;ServerWorld world;ApplyTarget(EventBatchFiles.Target target,AreaSnapshot before,AreaSnapshot after){this.target=target;this.before=before;this.after=after;}AreaRecovery.CellStatus classify(int cell,AreaCell live){if(after.cell(cell).equals(live))return AreaRecovery.CellStatus.AFTER;return before.cell(cell).equals(live)?AreaRecovery.CellStatus.BEFORE:AreaRecovery.CellStatus.CONFLICT;}}
    private static final class Applying{final EventBatchFiles.Plan plan;final AreaEffectFiles.Effects effects;final List<ApplyTarget> targets;final BooleanSupplier eligible;EventBatchFiles.Entry entry;Applying(EventBatchFiles.Plan plan,AreaEffectFiles.Effects effects,List<ApplyTarget> targets,EventBatchFiles.Entry entry,BooleanSupplier eligible){this.plan=plan;this.effects=effects;this.targets=targets;this.entry=entry;this.eligible=eligible;}}
    private static final class ConflictException extends IllegalStateException{ConflictException(String message){super(message);}}
    private static final class PausedException extends IOException{PausedException(String message){super(message);}}
}
