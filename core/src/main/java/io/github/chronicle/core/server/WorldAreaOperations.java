package io.github.chronicle.core.server;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.areas.*;
import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.content.*;
import io.github.chronicle.core.managed.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.nio.charset.StandardCharsets;

/** Loaded-only, journal-first native area replacement, shared by editor actions and departures. */
public final class WorldAreaOperations {
    public static final String HOLD="chronicle_core:area-apply";
    public static final String HOLD_OWNER=HOLD;
    private static final int BUDGET=256;
    private static final ThreadLocal<WriteCell> WRITING=new ThreadLocal<>();
    private record WriteCell(ServerWorld world,BlockPos pos){}
    public static boolean writing(ServerWorld world,BlockPos pos){WriteCell cell=WRITING.get();return cell!=null&&cell.world==world&&cell.pos.equals(pos);}
    private static final Map<MinecraftServer,Runtime> WORLDS=new IdentityHashMap<>();
    private WorldAreaOperations(){}
    public record Prepared(AreaOperation operation){}
    public static void register(){
        ServerLifecycleEvents.SERVER_STARTED.register(WorldAreaOperations::start);
        ServerTickEvents.END_SERVER_TICK.register(WorldAreaOperations::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
    }
    private static void start(MinecraftServer server){
        Runtime r=new Runtime();WORLDS.put(server,r);
        try{
            AuthoringRuntime.get(server).service().addMutationGuard((old,next,operation)->{
                if(r.retiringAnchor!=null&&old!=null&&r.retiringAnchor.equals(old.id()))return;
                AuthoringSite site=old==null?next:old;if(site==null)return;
                if(site instanceof AuthoringSite.Region&&(r.preparation!=null&&r.preparation.area.bounds().regionId().equals(site.id())||r.prepared!=null&&r.prepared.operation().bounds().regionId().equals(site.id())||EventWorldBatches.reservedRegion(server,site.id())))throw new IllegalStateException("This Region is reserved by an Area or Event operation.");
                if(site instanceof AuthoringSite.Anchor anchor&&(reserved(server,anchor.dimension(),anchor.position())||EventWorldBatches.reserved(server,anchor.dimension(),anchor.position())))throw new IllegalStateException("This block site is reserved by an Area or Event operation.");
            });
            for(AreaOperation operation:AreaChanges.operations(server).state().operations().values()){
                if(operation.unresolved()){
                    AreaEffectFiles.Effects effects=AreaEffectFiles.load(server,operation);
                    if(effects.managedPlan()!=null)ManagedBlocks.reinstateAreaReplacement(server,effects.managedPlan());
                    RuntimeHolds.hold(server,HOLD,"An Area change is awaiting loaded terrain or deliberate recovery.");
                    if(operation.intent()!=AreaOperation.Intent.DELETE_UNCHANGED&&operation.stage()!=AreaOperation.Stage.CONFLICT&&r.applying==null)loadApply(server,r,operation,effects);
                }else if(operation.deletionObligation()&&AreaChanges.catalog(server).areas().containsKey(operation.areaId()))AreaChanges.retire(server,operation);
            }
        }catch(Exception failure){r.problem=message(failure);RuntimeHolds.hold(server,HOLD,"Area operation evidence is unavailable; inspect the server log.");ChronicleCore.LOGGER.error("Area operation recovery unavailable; evidence preserved",failure);}
    }
    public static boolean busy(MinecraftServer server){Runtime r=runtime(server);return r.preparation!=null||r.prepared!=null||r.applying!=null;}
    /** Campaign projection jobs pause too; their observed-write hooks still journal coordinator writes. */
    public static boolean hasReservation(MinecraftServer server){
        Runtime r=WORLDS.get(server);
        if(r==null)return true;
        try{return r.preparation!=null||r.prepared!=null||r.applying!=null||!r.problem.isBlank()||AreaChanges.operations(server).state().operations().values().stream().anyMatch(AreaOperation::unresolved);}
        catch(IllegalStateException unavailable){return true;}
    }
    public static String problem(MinecraftServer server){Runtime r=WORLDS.get(server);return r==null?"Area operations are unavailable.":r.problem;}
    public static Object revision(MinecraftServer server){Runtime r=WORLDS.get(server);if(r==null)return "unavailable";return List.of(AreaChanges.catalog(server).revision(),AreaChanges.operations(server).state().revision(),r.preparation==null?"":r.preparation.id.toString(),r.prepared==null?"":r.prepared.operation().operationId().toString(),r.problem);}
    public static Optional<AreaOperation> pendingPrepared(MinecraftServer server,String areaId){Runtime r=runtime(server);return r.prepared!=null&&r.prepared.operation().areaId().equals(areaId)?Optional.of(r.prepared.operation()):Optional.empty();}
    public static boolean complete(MinecraftServer server,UUID id){AreaOperation o=AreaChanges.operations(server).state().operations().get(id);return o!=null&&o.stage()==AreaOperation.Stage.COMPLETE;}
    public static UUID apply(ServerCommandSource source,String id,AreaState state,long expectedRevision,boolean delete){
        requireOperator(source);AreaChange area=requireArea(source.getServer(),id,expectedRevision);
        idle(source.getServer());
        if(delete&&AreaChanges.catalog(source.getServer()).tombstones().size()>=AreaCatalog.MAX_TOMBSTONES)throw new IllegalStateException("Area deletion history is full.");
        UUID op=UUID.randomUUID();EventSources.prepareRegion(source.getServer(),op,source.getPlayer().getUuid(),area.bounds().regionId(),state.name(),EventSources.activeOperation(source.getServer())&&!AuthoringRuntime.isAuthor(source.getPlayer()),"area");
        try{prepare(source.getServer(),op,source.getPlayer().getUuid(),area,state,delete,Set.of(),Set.of(),p->{try{commitPrepared(source.getServer(),p.operation().operationId());}catch(Exception e){fail(source.getServer(),e);}},m->{try{EventSources.cancelAuthority(source.getServer(),"area:"+op);}catch(EventSources.Unavailable held){/* Exact refusal preparation remains held for source repair. */}source.sendError(Text.literal(m));});return op;}
        catch(RuntimeException failure){try{EventSources.cancelAuthority(source.getServer(),"area:"+op);}catch(EventSources.Unavailable cancellation){failure.addSuppressed(cancellation);}throw failure;}
    }
    /** Cancellation requires a valid unchanged full journal and no live or parent-owned preparation. */
    static boolean sourceDefinitelyAbsent(MinecraftServer server,UUID id) {
        Runtime r=WORLDS.get(server);if(r==null||!r.problem.isBlank()||!AreaChanges.ready(server)||!Departures.ready(server))return false;
        if(r.preparation!=null&&r.preparation.id.equals(id)||r.prepared!=null&&r.prepared.operation().operationId().equals(id)||r.applying!=null&&r.applying.operation.operationId().equals(id))return false;
        if(Departures.state(server).operations().values().stream().anyMatch(op->op.preparedAreaEffect().map(effect->effect.operationId().equals(id)).orElse(false)))return false;
        try{AreaChanges.operations(server).verifyUnchanged();return !AreaChanges.operations(server).state().operations().containsKey(id);}
        catch(IOException failure){throw new IllegalStateException("Area source refusal cannot be proved from its journal",failure);}
    }
    public static UUID deleteUnchanged(ServerCommandSource source,String id,long expectedRevision)throws IOException{
        requireOperator(source);MinecraftServer server=source.getServer();idle(server);if(AreaChanges.catalog(server).tombstones().size()>=AreaCatalog.MAX_TOMBSTONES)throw new IllegalStateException("Area deletion history is full.");AreaChange area=requireArea(server,id,expectedRevision);UUID op=UUID.randomUUID();
        AreaOperation o=new AreaOperation(op,AreaOperation.Intent.DELETE_UNCHANGED,id,area.name(),area.revision(),area.bounds(),Optional.empty(),Optional.empty(),Optional.empty(),source.getPlayer().getUuid(),0,AreaOperation.Stage.PREPARED,true,"",Instant.now().toString());
        AreaChanges.operations(server).prepare(o);AreaChanges.refreshOperationHold(server);AreaOperation done=AreaChanges.operations(server).complete(op);AreaChanges.retire(server,done);AreaChanges.refreshOperationHold(server);return op;
    }
    public static void prepareDeparture(MinecraftServer server,UUID departureOperation,UUID actor,String id,AreaState state,Set<UUID> passengers,Set<String> retiredAnchors,Consumer<Prepared> ready,Consumer<String> refused){
        UUID areaOperation=UUID.nameUUIDFromBytes((departureOperation+":area").getBytes(StandardCharsets.UTF_8));
        AreaChange area=AreaChanges.catalog(server).areas().get(id);if(area==null)throw new IllegalStateException("The linked Area change is missing.");
        prepare(server,areaOperation,actor,area,state,false,passengers,retiredAnchors,ready,refused);
    }
    private static void prepare(MinecraftServer server,UUID id,UUID actor,AreaChange area,AreaState state,boolean delete,Set<UUID> exempt,Set<String> retiredAnchors,Consumer<Prepared> ready,Consumer<String> refused){
        idle(server);AreaChanges.currentRegion(server,area);SnapshotRef afterRef=area.requireSnapshot(state);ServerWorld world=world(server,area.bounds());loaded(world,area.bounds());clearOfPlayers(world,area.bounds(),exempt);AreaWorldPolicies.validate(server,area.bounds());
        try{
            AreaSnapshot after=AreaChanges.snapshots(server).load(afterRef);Runtime r=runtime(server);
            r.preparation=new Preparation(id,actor,area,state,delete,Set.copyOf(exempt),Set.copyOf(retiredAnchors),after,ready,refused,ChestAuthoring.service(server).state().revision());
            RuntimeHolds.hold(server,HOLD,"Area change: reserving and verifying the selected terrain.");closeContainers(server);
        }catch(Exception e){throw new IllegalStateException("The selected Area snapshot could not be read: "+e.getMessage(),e);}
    }
    /** Reuses immutable prepared evidence after a departure commits, including restart recovery. */
    public static void commitPrepared(MinecraftServer server,UUID id)throws IOException{
        Runtime r=runtime(server);AreaOperation prepared=AreaEffectFiles.prepared(server,id);AreaEffectFiles.Effects effects=AreaEffectFiles.load(server,prepared);
        AreaOperation existing=AreaChanges.operations(server).state().operations().get(id);
        if(existing!=null){if(existing.stage()==AreaOperation.Stage.COMPLETE)return;if(r.applying==null)loadApply(server,r,existing,effects);return;}
        if(AreaChanges.operations(server).state().operations().values().stream().anyMatch(AreaOperation::unresolved))throw new IllegalStateException("Resolve the earlier Area operation first.");
        AreaChange area=requireArea(server,prepared.areaId(),prepared.areaRevision());AreaChanges.currentRegion(server,area);loaded(world(server,prepared.bounds()),prepared.bounds());
        RuntimeHolds.hold(server,HOLD,"Area change is applying saved terrain.");
        if(effects.managedPlan()!=null)ManagedBlocks.prepareAreaReplacement(server,effects.managedPlan());
        AreaChanges.operations(server).prepare(prepared);AreaChanges.refreshOperationHold(server);r.prepared=null;
        loadApply(server,r,prepared,effects);
    }
    public static void releasePreparation(MinecraftServer server,UUID id){Runtime r=runtime(server);if(AreaChanges.operations(server).state().operations().containsKey(id))throw new IllegalStateException("A committed Area operation cannot be cancelled.");if(r.prepared!=null&&r.prepared.operation().operationId().equals(id))r.prepared=null;if(r.preparation!=null&&r.preparation.id.equals(id))r.preparation=null;if(!busy(server))RuntimeHolds.release(server,HOLD);}
    public static void recover(ServerCommandSource source,UUID id)throws IOException{
        if(!source.hasPermissionLevel(2)||source.getPlayer()==null)throw new IllegalStateException("Operator player required.");MinecraftServer server=source.getServer();Runtime r=runtime(server);
        if(r.preparation!=null||r.applying!=null)throw new IllegalStateException("An Area operation is already running.");
        if(r.prepared!=null&&r.prepared.operation().operationId().equals(id)){commitPrepared(server,id);return;}
        AreaOperation op=AreaChanges.operations(server).state().operations().get(id);if(op==null)throw new IllegalStateException("Choose an Area operation.");
        if(op.stage()==AreaOperation.Stage.COMPLETE&&op.deletionObligation()){
            if(AreaChanges.catalog(server).areas().containsKey(op.areaId()))AreaChanges.retire(server,op);
            AreaEffectFiles.Effects effects=op.intent()==AreaOperation.Intent.DELETE_UNCHANGED?null:AreaEffectFiles.load(server,op);
            if(effects!=null&&effects.managedPlan()!=null){ManagedBlocks.reinstateAreaReplacement(server,effects.managedPlan());ManagedBlocks.releaseAreaReplacement(server,effects.managedPlan(),ManagedAreaReplacementPlan.Outcome.WORLD_DURABLY_SAVED);}
            r.problem="";AreaChanges.refreshOperationHold(server);RuntimeHolds.release(server,HOLD);return;
        }
        if(!op.unresolved())throw new IllegalStateException("This Area operation is complete.");
        if(op.intent()==AreaOperation.Intent.DELETE_UNCHANGED){AreaChanges.retire(server,AreaChanges.operations(server).complete(id));AreaChanges.refreshOperationHold(server);return;}
        loadApply(server,r,op,AreaEffectFiles.load(server,op));
    }
    private static void loadApply(MinecraftServer server,Runtime r,AreaOperation op,AreaEffectFiles.Effects effects)throws IOException{
        if(r.applying!=null&&!r.applying.operation.operationId().equals(op.operationId()))throw new IllegalStateException("Another Area operation is running.");
        AreaSnapshot before=AreaChanges.snapshots(server).load(op.before().orElseThrow()),after=AreaChanges.snapshots(server).load(op.after().orElseThrow());
        if(effects.managedPlan()!=null)ManagedBlocks.reinstateAreaReplacement(server,effects.managedPlan());
        reserveContent(server,op,effects);r.applying=new Applying(op,effects,before,after);RuntimeHolds.hold(server,HOLD,"Area change is verifying durable before/after evidence.");r.problem="";
    }
    private static void tick(MinecraftServer server){Runtime r=WORLDS.get(server);if(r==null)return;
        try{if(r.preparation!=null)prepareTick(server,r);else if(r.applying!=null)applyTick(server,r);}catch(Exception e){if(r.preparation!=null){Preparation p=r.preparation;r.preparation=null;r.problem="";RuntimeHolds.release(server,HOLD);p.refused.accept(message(e));}else fail(server,e);}
    }
    private static void prepareTick(MinecraftServer server,Runtime r)throws Exception{
        Preparation p=r.preparation;ServerWorld world=world(server,p.area.bounds());loaded(world,p.area.bounds());closeContainers(server);clearOfPlayers(world,p.area.bounds(),p.exempt);AreaChanges.currentRegion(server,requireArea(server,p.area.id(),p.area.revision()));
        if(p.exempt.isEmpty()){ServerPlayerEntity actor=server.getPlayerManager().getPlayer(p.actor);if(actor==null)throw new IllegalStateException("The Area operator disconnected.");requireOperator(actor.getCommandSource());}
        AreaWorldPolicies.validate(server,p.area.bounds());int end=Math.min(p.area.bounds().cells(),p.cursor+BUDGET);
        for(;p.cursor<end;p.cursor++){
            BlockPos pos=position(p.area.bounds(),p.cursor);AreaCell cell=AreaChanges.captureCell(server,world,pos,p.area.bounds(),false);
            if(p.verifying){if(!p.before.cell(p.cursor).equals(cell))throw new IllegalStateException("The target changed during preparation at "+pos.toShortString()+". Review again.");}
            else{validateSavedCell(server,world,pos,p.after.cell(p.cursor));AreaWorldPolicies.validateCell(world,pos,cell,p.after.cell(p.cursor));p.add(cell);inspectCell(server,p,pos,cell,p.after.cell(p.cursor));}
        }
        if(p.cursor<p.area.bounds().cells())return;
        if(!p.verifying){p.before=p.snapshot();p.cursor=0;p.verifying=true;return;}
        if(ChestAuthoring.service(server).state().revision()!=p.contentRevision)throw new IllegalStateException("Chest or lore state changed during preparation. Review again.");
        SnapshotRef beforeRef=AreaChanges.snapshots(server).put(p.before),afterRef=AreaChanges.snapshots(server).put(p.after);
        AreaEffectFiles.Effects effects=effects(server,p);String hash=AreaEffectFiles.save(server,effects);
        AreaOperation op=new AreaOperation(p.id,p.delete?AreaOperation.Intent.DELETE_APPLY:AreaOperation.Intent.APPLY,p.area.id(),p.area.name(),p.area.revision(),p.area.bounds(),Optional.of(p.state),Optional.of(beforeRef),Optional.of(afterRef),p.actor,0,AreaOperation.Stage.PREPARED,p.delete,hash,Instant.now().toString());
        AreaEffectFiles.savePrepared(server,op);r.preparation=null;r.prepared=new Prepared(op);p.ready.accept(r.prepared);
    }
    private static void applyTick(MinecraftServer server,Runtime r)throws Exception{
        if(RuntimeHolds.heldByOthers(server,Set.of(HOLD,"chronicle_core:area-operations","chronicle_core:chests",Departures.HOLD)))throw new IllegalStateException("Another Chronicle operation is holding world changes.");
        Applying a=r.applying;AreaOperation op=a.operation;ServerWorld world=world(server,op.bounds());loaded(world,op.bounds());closeContainers(server);clearOfPlayers(world,op.bounds(),Set.of());AreaChanges.currentRegion(server,requireArea(server,op.areaId(),op.areaRevision()));AreaWorldPolicies.validate(server,op.bounds());
        int end=Math.min(op.bounds().cells(),a.cursor+BUDGET);
        for(;a.cursor<end;a.cursor++){
            BlockPos pos=position(op.bounds(),a.cursor);AreaCell live=AreaChanges.captureCell(server,world,pos,op.bounds(),false);AreaRecovery.CellStatus status=a.plan.classify(a.cursor,live);
            if(a.verifying){if(status==AreaRecovery.CellStatus.CONFLICT)throw new IllegalStateException("Conflicting Area cell at "+pos.toShortString()+". Original evidence retained.");if(a.finalVerify&&status!=AreaRecovery.CellStatus.AFTER)throw new IllegalStateException("A previously applied cell changed at "+pos.toShortString());}
            else if(status==AreaRecovery.CellStatus.BEFORE){AreaWorldPolicies.validateCell(world,pos,live,a.after.cell(a.cursor));writeCell(server,world,pos,a.after.cell(a.cursor));AreaCell observed=AreaChanges.captureCell(server,world,pos,op.bounds(),false);if(!observed.equals(a.after.cell(a.cursor)))throw new IllegalStateException("Area write did not match its saved state at "+pos.toShortString());}
            else if(status==AreaRecovery.CellStatus.CONFLICT)throw new IllegalStateException("Conflicting Area cell at "+pos.toShortString()+". Original evidence retained.");
        }
        if(a.cursor<op.bounds().cells())return;
        if(a.verifying&&!a.finalVerify){
            if(op.stage()==AreaOperation.Stage.CONFLICT)op=AreaChanges.operations(server).resumeConflict(op.operationId());
            if(op.stage()==AreaOperation.Stage.PREPARED)op=AreaChanges.operations(server).begin(op.operationId());
            a.operation=op;a.verifying=false;a.cursor=0;return;
        }
        if(!a.verifying){a.verifying=true;a.finalVerify=true;a.cursor=0;return;}
        AreaWorldPolicies.durable(server,op.bounds());if(!server.saveAll(true,true,true))throw new IOException("Minecraft did not confirm the Area world save.");
        if(a.effects.managedPlan()!=null)ManagedBlocks.commitAreaReplacement(server,a.effects.managedPlan());
        commitContent(server,op,a.effects);retireAnchors(server,r,a.effects);
        AreaChanges.operations(server).checkpoint(op.operationId(),op.bounds().cells());AreaOperation done=AreaChanges.operations(server).complete(op.operationId());
        if(done.deletionObligation())AreaChanges.retire(server,done);
        if(a.effects.managedPlan()!=null)ManagedBlocks.releaseAreaReplacement(server,a.effects.managedPlan(),ManagedAreaReplacementPlan.Outcome.WORLD_DURABLY_SAVED);
        r.applying=null;r.problem="";AreaChanges.refreshOperationHold(server);RuntimeHolds.release(server,HOLD);
        ServerPlayerEntity actor=server.getPlayerManager().getPlayer(op.actor());if(actor!=null)actor.sendMessage(Text.literal("Area change saved: "+op.areaName()+" ("+op.requestedState().orElseThrow()+")."),false);
    }
    private static AreaEffectFiles.Effects effects(MinecraftServer server,Preparation p)throws Exception{
        ManagedAreaReplacementPlan managed=p.managedPositions.isEmpty()?null:ManagedBlocks.planAreaReplacement(server,p.id,p.area.bounds().dimension(),p.managedPositions,p.placements);
        Set<String> affected=new HashSet<>(),retired=new HashSet<>(),anchors=new HashSet<>();
        var authoring=AuthoringRuntime.get(server).service().state().sites();
        for(ChestBinding binding:ChestAuthoring.service(server).state().bindings().values()){
            ChestTarget t=binding.typed()?binding.directTarget():legacyTarget(authoring,binding);if(t==null||!contains(p.area.bounds(),t.dimension(),new BlockPosition(t.x(),t.y(),t.z())))continue;
            affected.add(binding.id());int index=index(p.area.bounds(),new BlockPos(t.x(),t.y(),t.z()));if(!id(decode(server,p.after.cell(index))).equals(t.expectedBlock()))retired.add(binding.id());
        }
        for(AuthoringSite site:authoring.values())if(site instanceof AuthoringSite.Anchor anchor&&contains(p.area.bounds(),anchor.dimension(),anchor.position())){
            int i=index(p.area.bounds(),new BlockPos(anchor.position().x(),anchor.position().y(),anchor.position().z()));if(!id(decode(server,p.after.cell(i))).equals(anchor.expectedBlock())){
                if(!p.retiredAnchors.contains(anchor.id()))throw new IllegalStateException("Area replacement would invalidate Block site '"+anchor.id()+"'. Remove its uses or explicitly authorize the departure helm.");
                if(MapSiteAssignments.bindings(server).values().stream().anyMatch(b->b.siteId().equals(anchor.id())))throw new IllegalStateException("The departing helm is also used by another map requirement: "+anchor.id());anchors.add(anchor.id());
            }
        }
        return new AreaEffectFiles.Effects(p.id.toString(),managed==null?"":Base64.getEncoder().encodeToString(ManagedAreaReplacementCodec.encode(managed)),p.contentRevision,affected,retired,p.books,anchors);
    }
    static ChestTarget legacyTarget(Map<String,AuthoringSite> sites,ChestBinding b){AuthoringSite site=sites.get(b.anchorId());if(!(site instanceof AuthoringSite.Anchor a)||a.revision()!=b.anchorRevision())return null;return new ChestTarget(a.id(),a.revision(),a.dimension(),a.position().x(),a.position().y(),a.position().z(),a.expectedBlock());}
    private static void retireAnchors(MinecraftServer server,Runtime r,AreaEffectFiles.Effects effects)throws IOException{for(String id:effects.retiredAnchors()){if(!AuthoringRuntime.get(server).service().state().sites().containsKey(id))continue;if(MapSiteAssignments.bindings(server).values().stream().anyMatch(b->b.siteId().equals(id)))throw new IllegalStateException("Block site gained another map consumer during the Area operation: "+id);r.retiringAnchor=id;try{AuthoringRuntime.get(server).service().remove(id);}finally{r.retiringAnchor=null;}}}
    static Set<AreaEffectFiles.Book> bookMarkers(MinecraftServer server,NbtElement root){
        Set<AreaEffectFiles.Book> result=new HashSet<>();Deque<NbtElement> todo=new ArrayDeque<>();todo.push(root);int count=0;
        var ledger=ChestAuthoring.service(server).state().bookDelivery();
        while(!todo.isEmpty()){
            if(++count>65536)throw new IllegalStateException("Area inventory NBT is too complex.");NbtElement value=todo.pop();
            if(value instanceof NbtCompound compound){
                NbtCompound marker=compound.getCompound("chronicle_book").orElse(null);
                if(marker!=null){String campaign=marker.getString("campaign").orElse(""),book=marker.getString("book").orElse(""),token=marker.getString("token").orElse("");var delivery=ledger.deliveries().get(book);if(!campaign.equals(ledger.campaignId())||delivery==null||!delivery.deliveryToken().equals(token)||!delivery.issued())throw new IllegalStateException("Unresolved managed lore provenance in Area target.");result.add(new AreaEffectFiles.Book(book,token));}
                for(String key:compound.getKeys()){NbtElement child=compound.get(key);if(child!=null)todo.push(child);}
            }else if(value instanceof NbtList list)for(NbtElement child:list)todo.push(child);
        }return result;
    }
    private static void reserveContent(MinecraftServer server,AreaOperation op,AreaEffectFiles.Effects effects)throws IOException{
        if(!effects.hasContent())return;
        Set<ManagedBookRemoval> books=new HashSet<>();for(AreaEffectFiles.Book b:effects.books())books.add(new ManagedBookRemoval(b.bookId(),b.token()));
        ChestAuthoring.service(server).prepareAreaRemoval(op.operationId(),effects.contentRevision(),effects.affectedBindings(),effects.retiredBindings(),books,op.createdAt());ChestAuthoring.refreshHold(server);
    }
    private static void commitContent(MinecraftServer server,AreaOperation op,AreaEffectFiles.Effects effects)throws IOException{if(effects.hasContent()){ChestAuthoring.service(server).commitAreaRemoval(op.operationId(),"area-world:"+op.operationId()+":"+op.after().orElseThrow().sha256(),op.createdAt());ChestAuthoring.refreshHold(server);}}
    private static void inspectCell(MinecraftServer server,Preparation p,BlockPos pos,AreaCell before,AreaCell after)throws Exception{
        String oldId=id(decode(server,before)),newId=id(decode(server,after));BlockPosition at=blockPosition(pos);
        if(ManagedBlocks.isManagedBlock(oldId)||ManagedBlocks.isManagedBlock(newId))p.managedPositions.add(at);
        ChronicleCore.managedBlocks().byBlock(newId).ifPresent(d->p.placements.add(new ManagedAreaReplacementPlan.Placement(d.typeId(),at)));
        if(before.blockEntityNbt()!=null)p.books.addAll(bookMarkers(server,StringNbtReader.readCompound(before.blockEntityNbt())));
    }
    static void validateSavedCell(MinecraftServer server,ServerWorld world,BlockPos pos,AreaCell cell)throws Exception{
        BlockState state=decode(server,cell);if(cell.blockEntityNbt()!=null){NbtCompound nbt=StringNbtReader.readCompound(cell.blockEntityNbt());if(BlockEntity.createFromNbt(pos,state,nbt,server.getRegistryManager())==null)throw new IllegalStateException("Saved block entity is incompatible at "+pos);}
        AreaWorldPolicies.validateCell(world,pos,cell,cell);
    }
    static void writeCell(MinecraftServer server,ServerWorld world,BlockPos pos,AreaCell cell)throws Exception{
        BlockState desired=decode(server,cell);BlockEntity replacement=null;
        if(cell.blockEntityNbt()!=null){NbtCompound nbt=StringNbtReader.readCompound(cell.blockEntityNbt());nbt.putInt("x",pos.getX());nbt.putInt("y",pos.getY());nbt.putInt("z",pos.getZ());replacement=BlockEntity.createFromNbt(pos,desired,nbt,server.getRegistryManager());if(replacement==null)throw new IllegalStateException("Cannot reconstruct saved block entity.");}
        world.removeBlockEntity(pos);WRITING.set(new WriteCell(world,pos.toImmutable()));try{world.setBlockState(pos,desired,Block.FORCE_STATE_AND_SKIP_CALLBACKS_AND_DROPS|Block.NOTIFY_LISTENERS);}finally{WRITING.remove();}
        if(!world.getBlockState(pos).equals(desired))throw new IllegalStateException("Campaign policy refused Area write at "+pos.toShortString());
        if(replacement!=null){world.addBlockEntity(replacement);replacement.markDirty();world.updateListeners(pos,desired,desired,Block.NOTIFY_LISTENERS);}
        world.getChunk(pos).markNeedsSaving();
    }
    static BlockState decode(MinecraftServer server,AreaCell cell){return BlockState.CODEC.parse(RegistryOps.of(JsonOps.INSTANCE,server.getRegistryManager()),JsonParser.parseString(cell.blockStateJson())).getOrThrow(m->new IllegalArgumentException("Invalid saved Area block: "+m));}
    static String id(BlockState state){return Registries.BLOCK.getId(state.getBlock()).toString();}
    private static void requireOperator(ServerCommandSource source){if(source.getPlayer()==null||!source.hasPermissionLevel(2))throw new IllegalStateException("An operator player is required.");if(ChronicleRuntime.service(source.getServer()).state().phase()!=SessionPhase.ACTIVE&&!AuthoringRuntime.isAuthor(source.getPlayer()))throw new IllegalStateException("Enable Editing, or apply during active play.");}
    private static void idle(MinecraftServer server){Runtime r=runtime(server);if(AreaChanges.operations(server).state().operations().size()>=AreaOperationJournal.MAX_OPERATIONS)throw new IllegalStateException("Area operation history is full; archive this campaign before further changes.");if(!r.problem.isBlank()||busy(server)||AreaChanges.busy(server)||RuntimeHolds.reason(server)!=null||AreaChanges.operations(server).state().operations().values().stream().anyMatch(AreaOperation::unresolved))throw new IllegalStateException("Resolve the current operation before starting another Area change.");}
    static AreaChange requireArea(MinecraftServer server,String id,long revision){AreaChange area=AreaChanges.catalog(server).areas().get(id);if(area==null||area.revision()!=revision)throw new IllegalStateException("Area change is missing or stale; review again.");return area;}
    static ServerWorld world(MinecraftServer server,AreaBounds b){ServerWorld world=server.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(b.dimension())));if(world==null)throw new IllegalStateException("Area dimension unavailable.");return world;}
    static void loaded(ServerWorld world,AreaBounds b){for(int x=Math.floorDiv(b.min().x(),16);x<=Math.floorDiv(b.max().x(),16);x++)for(int z=Math.floorDiv(b.min().z(),16);z<=Math.floorDiv(b.max().z(),16);z++)if(!world.getChunkManager().isChunkLoaded(x,z))throw new IllegalStateException("Area chunks are not all loaded; no chunks were forced.");}
    static void clearOfPlayers(ServerWorld world,AreaBounds b,Set<UUID> exempt){Box box=new Box(b.min().x(),b.min().y(),b.min().z(),b.max().x()+1d,b.max().y()+1d,b.max().z()+1d);for(ServerPlayerEntity player:world.getPlayers())if(!player.isSpectator()&&!exempt.contains(player.getUuid())&&player.getBoundingBox().intersects(box))throw new IllegalStateException("Move non-spectator player '"+player.getName().getString()+"' clear of the Area before applying.");}
    static void closeContainers(MinecraftServer server){for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList())if(player.currentScreenHandler!=player.playerScreenHandler)player.closeHandledScreen();}
    private static boolean reserved(MinecraftServer server,String dimension,BlockPosition pos){Runtime r=WORLDS.get(server);if(r==null)return false;if(r.preparation!=null&&contains(r.preparation.area.bounds(),dimension,pos))return true;if(r.prepared!=null&&contains(r.prepared.operation().bounds(),dimension,pos))return true;try{return AreaChanges.operations(server).state().operations().values().stream().anyMatch(o->o.unresolved()&&contains(o.bounds(),dimension,pos));}catch(IllegalStateException unavailable){return true;}}
    static BlockPos position(AreaBounds b,int i){int x=i%b.sizeX(),q=i/b.sizeX(),z=q%b.sizeZ(),y=q/b.sizeZ();return new BlockPos(b.min().x()+x,b.min().y()+y,b.min().z()+z);}
    static int index(AreaBounds b,BlockPos p){return p.getX()-b.min().x()+b.sizeX()*(p.getZ()-b.min().z()+b.sizeZ()*(p.getY()-b.min().y()));}
    static BlockPosition blockPosition(BlockPos p){return new BlockPosition(p.getX(),p.getY(),p.getZ());}
    static boolean contains(AreaBounds b,String dimension,BlockPosition p){return b.dimension().equals(dimension)&&p.x()>=b.min().x()&&p.x()<=b.max().x()&&p.y()>=b.min().y()&&p.y()<=b.max().y()&&p.z()>=b.min().z()&&p.z()<=b.max().z();}
    private static Runtime runtime(MinecraftServer server){if(!server.isOnThread())throw new IllegalStateException("Area operations require server thread.");Runtime r=WORLDS.get(server);if(r==null)throw new IllegalStateException("Area operations unavailable.");return r;}
    private static String message(Exception e){String m=Objects.requireNonNullElse(e.getMessage(),"Area operation failed; inspect server log.");return m.substring(0,Math.min(480,m.length()));}
    private static void fail(MinecraftServer server,Exception failure){Runtime r=runtime(server);r.problem=message(failure);if(r.applying!=null){AreaOperation op=r.applying.operation;try{if(op.stage()==AreaOperation.Stage.APPLYING)AreaChanges.operations(server).conflict(op.operationId(),Math.max(op.cursor(),Math.min(r.applying.cursor,op.bounds().cells()-1)),op.detail());}catch(Exception journalFailure){failure.addSuppressed(journalFailure);}}r.applying=null;RuntimeHolds.hold(server,HOLD,"Area operation stopped: "+r.problem);ChronicleCore.LOGGER.error("Area replacement stopped; original evidence preserved",failure);}
    private static final class Runtime{Preparation preparation;Prepared prepared;Applying applying;String problem="",retiringAnchor;}
    private static final class Preparation{
        final UUID id,actor;final AreaChange area;final AreaState state;final boolean delete;final Set<UUID> exempt;final Set<String> retiredAnchors;final AreaSnapshot after;final Consumer<Prepared> ready;final Consumer<String> refused;final long contentRevision;
        final Set<BlockPosition> managedPositions=new HashSet<>();final List<ManagedAreaReplacementPlan.Placement> placements=new ArrayList<>();final Set<AreaEffectFiles.Book> books=new HashSet<>();
        final List<String> palette=new ArrayList<>();final Map<String,Integer> paletteMap=new HashMap<>();final Map<Integer,String> entities=new HashMap<>();final int[] cells;long bytes;int cursor;boolean verifying;AreaSnapshot before;
        Preparation(UUID id,UUID actor,AreaChange area,AreaState state,boolean delete,Set<UUID> exempt,Set<String> retiredAnchors,AreaSnapshot after,Consumer<Prepared> ready,Consumer<String> refused,long contentRevision){this.id=id;this.actor=actor;this.area=area;this.state=state;this.delete=delete;this.exempt=exempt;this.retiredAnchors=retiredAnchors;this.after=after;this.ready=ready;this.refused=refused;this.contentRevision=contentRevision;cells=new int[area.bounds().cells()];bytes=(long)cells.length*4;}
        void add(AreaCell cell){Integer p=paletteMap.get(cell.blockStateJson());if(p==null){p=palette.size();palette.add(cell.blockStateJson());paletteMap.put(cell.blockStateJson(),p);bytes+=cell.blockStateJson().getBytes(StandardCharsets.UTF_8).length;}cells[cursor]=p;if(cell.blockEntityNbt()!=null){entities.put(cursor,cell.blockEntityNbt());bytes+=4L+cell.blockEntityNbt().getBytes(StandardCharsets.UTF_8).length;}if(bytes>AreaSnapshot.MAX_DECODED_BYTES)throw new IllegalStateException("Area target exceeds the 64 MiB snapshot budget.");}
        AreaSnapshot snapshot(){return new AreaSnapshot(area.bounds(),palette,cells,entities);}
    }
    private static final class Applying{AreaOperation operation;final AreaEffectFiles.Effects effects;final AreaSnapshot before,after;final AreaRecovery.Plan plan;int cursor;boolean verifying=true,finalVerify;Applying(AreaOperation op,AreaEffectFiles.Effects effects,AreaSnapshot before,AreaSnapshot after){operation=op;this.effects=effects;this.before=before;this.after=after;plan=AreaRecovery.plan(op,before,after);}}
}
