package io.github.chronicle.core.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.JsonOps;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.areas.AreaBounds;
import io.github.chronicle.core.areas.AreaCatalog;
import io.github.chronicle.core.areas.AreaCatalogService;
import io.github.chronicle.core.areas.AreaCatalogStore;
import io.github.chronicle.core.areas.AreaCell;
import io.github.chronicle.core.areas.AreaChange;
import io.github.chronicle.core.areas.AreaOperation;
import io.github.chronicle.core.areas.AreaOperationService;
import io.github.chronicle.core.areas.AreaOperationStore;
import io.github.chronicle.core.areas.AreaSnapshot;
import io.github.chronicle.core.areas.AreaSnapshotStore;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.areas.SnapshotRef;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.books.BookConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BedBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Native CF21a selection and loaded-only capture. Applying saved terrain is deliberately separate. */
public final class AreaChanges {
    private static final int CELLS_PER_TICK=512;
    private static final String CAPTURE_HOLD="chronicle_core:area-capture";
    private static final String OPERATION_HOLD="chronicle_core:area-operations";
    private static final String TOOL_TAG="chronicle_area_draft";
    private static final int MAX_NBT_TRAVERSAL_DEPTH=64;
    private static final int MAX_NBT_TRAVERSAL_NODES=65_536;
    private static final Map<MinecraftServer,Runtime> WORLDS=new IdentityHashMap<>();
    private static final Map<String,CaptureAdapter> ADAPTERS=new LinkedHashMap<>();
    private static final Map<String,CaptureAdapter> BLOCK_ADAPTERS=new LinkedHashMap<>();
    private static boolean adaptersFrozen;
    private AreaChanges(){}

    public static void register(){
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->commands(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server->adaptersFrozen=true);
        ServerLifecycleEvents.SERVER_STARTED.register(AreaChanges::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->WORLDS.remove(server));
        ServerTickEvents.END_SERVER_TICK.register(AreaChanges::tick);
        ServerDisconnects.register((handler,server)->disconnected(handler.player));
        AttackBlockCallback.EVENT.register((player,world,hand,pos,direction)->selectWithTool(player,world,hand,pos,1));
        UseBlockCallback.EVENT.register((player,world,hand,hit)->selectWithTool(player,world,hand,hit.getBlockPos(),2));
    }

    /** Campaign adapters must validate and strip their own transient ownership/identity before returning data. */
    public static synchronized void registerCaptureAdapter(String namespace,CaptureAdapter adapter){
        if(adaptersFrozen)throw new IllegalStateException("Area capture adapters are frozen");
        if(namespace==null||!namespace.matches("[a-z0-9_.-]+")||namespace.equals("minecraft")||adapter==null)throw new IllegalArgumentException("A non-minecraft namespace and adapter are required");
        if(ADAPTERS.putIfAbsent(namespace,adapter)!=null)throw new IllegalArgumentException("Area capture adapter already registered: "+namespace);
    }
    /** Exact-block adapters let a campaign audit native special blocks without owning the whole namespace. */
    public static synchronized void registerBlockCaptureAdapter(String blockId,CaptureAdapter adapter){if(adaptersFrozen||blockId==null||!AuthoringSite.NAMESPACED_ID.matcher(blockId).matches()||adapter==null||BLOCK_ADAPTERS.putIfAbsent(blockId,adapter)!=null)throw new IllegalArgumentException("A unique unfrozen exact-block capture adapter is required");}

    public static AreaCatalog catalog(MinecraftServer server){return runtime(server).catalog.state();}
    public static AreaSnapshotStore snapshots(MinecraftServer server){return runtime(server).snapshots;}
    public static AreaOperationService operations(MinecraftServer server){return runtime(server).operations;}
    /** True while a terrain capture or its durable cross-store publication is unresolved. */
    public static boolean busy(MinecraftServer server){Runtime r=runtime(server);return r.job!=null||!r.intents.isEmpty();}
    /** A concise reason UI/actions are unavailable. Recovery code may still use the service getters. */
    public static Optional<String> problem(MinecraftServer server){
        if(!server.isOnThread())throw new IllegalStateException("Area changes require the server thread.");Runtime r=WORLDS.get(server);if(r==null)return Optional.of("Area changes are unavailable; inspect the server log and area-changes files.");
        if(r.job!=null)return Optional.of("An Area change capture is in progress.");if(!r.intents.isEmpty())return Optional.of("An Area change capture publication requires recovery.");if(r.operations.state().operations().values().stream().anyMatch(AreaOperation::unresolved))return Optional.of("An Area change operation requires recovery.");return Optional.empty();
    }
    public static boolean ready(MinecraftServer server){return problem(server).isEmpty();}
    /** Resolves the one authoritative Core Region and verifies the exact revision/geometry pin. */
    public static AuthoringSite.Region currentRegion(MinecraftServer server,AreaChange area){if(area==null)throw new IllegalArgumentException("Area change is required.");runtime(server);return resolveRegion(server,area.bounds());}
    public static boolean stale(MinecraftServer server,AreaChange area){if(area==null)throw new IllegalArgumentException("Area change is required.");runtime(server);AuthoringSite site=AuthoringRuntime.get(server).service().state().sites().get(area.bounds().regionId());return !(site instanceof AuthoringSite.Region region)||!area.bounds().matches(region);}
    /**
     * Reads one loaded cell through the same audited codecs as capture. A template source is
     * strict; a live target preserves unresolved loot IDs and managed lore without rolling it.
     */
    public static AreaCell captureCell(MinecraftServer server,ServerWorld world,BlockPos pos,AreaBounds bounds,boolean templateSource){
        runtime(server);if(world==null||world.getServer()!=server||pos==null||bounds==null)throw new IllegalArgumentException("Server world, position and Area bounds are required.");
        if(!world.getRegistryKey().getValue().toString().equals(bounds.dimension()))throw new IllegalArgumentException("Area cell world does not match its pinned dimension.");if(!contains(bounds,pos))throw new IllegalArgumentException("Area cell is outside its pinned bounds: "+pos);
        checkPosition(world,toPosition(pos));if(!world.getChunkManager().isChunkLoaded(Math.floorDiv(pos.getX(),16),Math.floorDiv(pos.getZ(),16)))throw new IllegalStateException("Area cell is in an unloaded chunk; no chunk was forced: "+pos);
        return readCell(server,world,bounds,cellIndex(bounds,pos),templateSource);
    }
    /** Retires only from durable completed deletion evidence; operation history is retained. */
    public static void retire(MinecraftServer server,AreaOperation completedDelete)throws IOException{
        Runtime r=runtime(server);if(busy(server))throw new IllegalStateException("An Area capture is still running or publishing.");if(completedDelete==null)throw new IllegalArgumentException("Completed Area deletion is required.");AreaOperation durable=r.operations.state().operations().get(completedDelete.operationId());if(!completedDelete.equals(durable))throw new IllegalStateException("Area deletion evidence is not the current durable operation.");
        if(completedDelete.intent()==AreaOperation.Intent.DELETE_UNCHANGED)r.catalog.retire(completedDelete.areaId(),completedDelete.areaRevision(),completedDelete);
        else if(completedDelete.intent()==AreaOperation.Intent.DELETE_APPLY){AreaChange area=r.catalog.state().areas().get(completedDelete.areaId());if(area==null)throw new IllegalStateException("Area change is missing: "+completedDelete.areaId());r.catalog.retire(area.id(),completedDelete.areaRevision(),currentRegion(server,area),completedDelete);}
        else throw new IllegalStateException("Only a completed Area deletion can retire a definition.");
    }
    public static Optional<DraftView> draft(MinecraftServer server,UUID player){Runtime r=WORLDS.get(server);Draft d=r==null?null:r.drafts.get(player);return d==null?Optional.empty():Optional.of(d.view(r.job!=null&&r.job.player.equals(player)));}
    public static boolean hasDraft(MinecraftServer server,UUID player){Runtime r=WORLDS.get(server);return r!=null&&r.drafts.containsKey(player);}

    public static DraftView begin(ServerCommandSource source,String name,AreaState state){return begin(source,null,name,state);}
    public static DraftView begin(ServerCommandSource source,String requestedId,String name,AreaState state){
        ServerPlayerEntity player=requireOperator(source);MinecraftServer server=source.getServer();Runtime runtime=runtime(server);
        if(state==null)throw new IllegalArgumentException("Initial Area state is required.");
        if(!AuthoringRuntime.editable(server))throw new IllegalStateException("Creating an Area change requires non-active Editing.");
        AuthoringRuntime authoring=AuthoringRuntime.get(server);authoring.selection(player.getUuid());
        if(authoring.regionDraft(player.getUuid()).isPresent())throw new IllegalStateException("An ordinary Region draft is active. Save it, or disable and re-enable author mode before creating an Area change.");
        if(runtime.drafts.containsKey(player.getUuid()))throw new IllegalStateException("An Area change draft is already active.");
        creationCapacity(runtime,authoring);
        String id=requestedId==null?nextId(runtime,authoring,name):requestedId;
        validateNewId(runtime,authoring,id);AreaChange probeName=new AreaChange(id,name,new AreaBounds(id,dimension(player),1,new BlockPosition(0,0,0),new BlockPosition(0,0,0)),1,Optional.empty(),Optional.empty());
        AuthoringRuntime.Presentation previous=authoring.presentation(player.getUuid());
        Draft draft=new Draft(UUID.randomUUID(),player.getUuid(),id,probeName.name(),state,dimension(player),previous);
        runtime.drafts.put(player.getUuid(),draft);
        authoring.selection(player.getUuid(),new AuthoringRuntime.Selection(draft.dimension,null,null));AuthoringRuntime.clearPreview(player);draft.presentationEpoch=authoring.presentationEpoch(player.getUuid());
        draft.tool=issueTool(player,draft.token);
        player.sendMessage(Text.literal("Area change '"+draft.name+"' started with "+state+". Left-click with the Debug shovel for position 1 and right-click for position 2, or use /chronicles author region pos1 and pos2. Then use /chronicle area change save."),false);
        if(draft.tool==null)player.sendMessage(Text.literal("No safe inventory slot was available for the Debug shovel. Position commands still work; free a slot and restart the draft if you want the tool."),false);
        return draft.view(false);
    }

    public static DraftView setDraftCorner(ServerPlayerEntity player,int which,BlockPosition position){
        Runtime runtime=runtime(player.getEntityWorld().getServer());Draft draft=runtime.drafts.get(player.getUuid());if(draft==null)throw new IllegalStateException("No Area change draft is active.");
        if(!AuthoringRuntime.isAuthor(player)||!player.getCommandSource().hasPermissionLevel(2))throw new IllegalStateException("Area change selection requires non-active operator Editing.");
        if(runtime.job!=null&&runtime.job.player.equals(player.getUuid()))throw new IllegalStateException("This Area change capture is already running.");
        if(which!=1&&which!=2)throw new IllegalArgumentException("Area corner must be 1 or 2");
        if(!draft.dimension.equals(dimension(player)))throw new IllegalStateException("Area draft is in another dimension.");
        ServerWorld world=(ServerWorld)player.getEntityWorld();checkPosition(world,position);
        BlockPosition first=which==1?position:draft.first,second=which==2?position:draft.second;
        AuthoringSite preview;
        if(first!=null&&second!=null){
            AuthoringSite.Region region=new AuthoringSite.Region(draft.id,draft.dimension,1,first,second);
            try{new AreaBounds(region.id(),region.dimension(),region.revision(),region.min(),region.max());}
            catch(IllegalArgumentException invalid){throw new IllegalArgumentException("Corner not changed: "+invalid.getMessage()+". The previous selection is retained.",invalid);}
            preview=region;
        }else{BlockPosition p=first==null?second:first;preview=new AuthoringSite.Location("area_draft_corner",draft.dimension,1,p.x()+.5,p.y()+.5,p.z()+.5,0,0);}
        draft.first=first;draft.second=second;
        AuthoringRuntime authoring=AuthoringRuntime.get(world.getServer());authoring.selection(player.getUuid(),new AuthoringRuntime.Selection(draft.dimension,first,second));authoring.preview(player,preview);
        draft.presentationEpoch=authoring.presentationEpoch(player.getUuid());return draft.view(false);
    }

    public static UUID saveDraft(ServerCommandSource source){
        ServerPlayerEntity player=requireOperator(source);Runtime runtime=runtime(source.getServer());Draft draft=runtime.drafts.get(player.getUuid());if(draft==null)throw new IllegalStateException("No Area change draft is active.");
        if(!AuthoringRuntime.editable(source.getServer()))throw new IllegalStateException("Creating an Area change requires non-active Editing.");
        if(!draft.dimension.equals(dimension(player)))throw new IllegalStateException("Return to the Area draft dimension before saving.");
        AuthoringRuntime.get(source.getServer()).selection(player.getUuid());
        if(draft.first==null||draft.second==null)throw new IllegalStateException("Select both Area change positions before saving.");
        if(runtime.job!=null)throw new IllegalStateException("Another Area change capture is already running.");
        if(runtime.intents.values().stream().anyMatch(intent->intent.areaId.equals(draft.id)))throw new IllegalStateException("This Area change has an unresolved publication intent; inspect the server log.");
        creationCapacity(runtime,AuthoringRuntime.get(source.getServer()));if(runtime.catalog.state().areas().containsKey(draft.id)||runtime.catalog.state().tombstones().contains(draft.id)||AuthoringRuntime.get(source.getServer()).service().state().sites().containsKey(draft.id))throw new IllegalStateException("This Area draft ID was claimed elsewhere; cancel it and create a new draft.");
        long predictedRevision=Math.addExact(AuthoringRuntime.get(source.getServer()).service().state().revision(),1);
        AuthoringSite.Region region=new AuthoringSite.Region(draft.id,draft.dimension,predictedRevision,draft.first,draft.second);
        AreaBounds bounds=AreaBounds.from(region);ServerWorld world=resolveWorld(source.getServer(),bounds);preflightLoaded(world,bounds);
        CaptureJob job=CaptureJob.initial(player.getUuid(),draft,bounds,runtime.catalog.state().revision(),AuthoringRuntime.get(source.getServer()).service().state().revision());
        beginJob(source.getServer(),runtime,job);player.sendMessage(Text.literal("Capturing "+bounds.cells()+" loaded cells for Area change '"+draft.name+"'. Existing definitions remain unchanged until the capture verifies and saves."),false);return job.operationId;
    }

    public static UUID captureCurrent(ServerCommandSource source,String areaId,AreaState state,long expectedRevision){
        ServerPlayerEntity player=requireOperator(source);Runtime runtime=runtime(source.getServer());if(runtime.job!=null)throw new IllegalStateException("Another Area change capture is already running.");
        if(state==null)throw new IllegalArgumentException("Area state is required.");
        AreaChange area=runtime.catalog.state().areas().get(areaId);if(area==null)throw new IllegalStateException("Area change is missing: "+areaId);if(area.revision()!=expectedRevision)throw new IllegalStateException("Area change revision is stale.");
        AuthoringSite site=AuthoringRuntime.get(source.getServer()).service().state().sites().get(area.bounds().regionId());if(!(site instanceof AuthoringSite.Region region)||!area.bounds().matches(region))throw new IllegalStateException("Area change bounds are stale or the Core Region is missing.");
        ServerWorld world=resolveWorld(source.getServer(),area.bounds());preflightLoaded(world,area.bounds());
        CaptureJob job=CaptureJob.recapture(player.getUuid(),area,state);beginJob(source.getServer(),runtime,job);player.sendMessage(Text.literal("Recapturing "+state+" for '"+area.name()+"'. The current saved state remains authoritative until verification and save complete."),false);return job.operationId;
    }

    public static void cancel(ServerCommandSource source){ServerPlayerEntity player=requireOperator(source);if(!cancel(runtime(source.getServer()),source.getServer(),player,true))throw new IllegalStateException("No Area change draft is active.");}
    static void cancelIfPresent(ServerCommandSource source){ServerPlayerEntity player=source.getPlayer();if(player!=null){Runtime runtime=WORLDS.get(source.getServer());if(runtime!=null)cancel(runtime,source.getServer(),player,false);}}

    public static void refreshOperationHold(MinecraftServer server){Runtime r=runtime(server);if(r.operations.state().operations().values().stream().anyMatch(operation->operation.unresolved()))RuntimeHolds.hold(server,OPERATION_HOLD,"An Area change operation requires recovery before play can resume.");else RuntimeHolds.release(server,OPERATION_HOLD);}

    private static void start(MinecraftServer server){
        Path root=server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/area-changes");
        try{
            Runtime runtime=new Runtime(new AreaCatalogService(new AreaCatalogStore(root.resolve("catalog.bin"))),new AreaSnapshotStore(root.resolve("snapshots")),new AreaOperationService(new AreaOperationStore(root.resolve("operations.bin"))),new IntentStore(root.resolve("capture-intents.json")));
            for(AreaChange area:runtime.catalog.state().areas().values()){if(area.start().isPresent())runtime.snapshots.load(area.start().get());if(area.end().isPresent())runtime.snapshots.load(area.end().get());}
            runtime.intents.putAll(runtime.intentStore.load());WORLDS.put(server,runtime);
            AuthoringRuntime.get(server).service().addMutationGuard((existing,proposed,operation)->guardRegionMutation(runtime,existing,proposed));
            refreshOperationHold(server);if(!runtime.intents.isEmpty()){RuntimeHolds.hold(server,CAPTURE_HOLD,"An Area change capture publication requires recovery.");recoverIntents(server,runtime);}
            ChronicleCore.LOGGER.info("Chronicle Area changes ready: {} areas, {} pending capture intents",runtime.catalog.state().areas().size(),runtime.intents.size());
        }catch(IOException|IllegalArgumentException exception){WORLDS.remove(server);RuntimeHolds.hold(server,CAPTURE_HOLD,"Area changes are unavailable; restore their files while stopped.");ChronicleCore.LOGGER.error("Area changes unavailable; existing evidence was preserved at {}",root,exception);}
    }

    private static void tick(MinecraftServer server){Runtime runtime=WORLDS.get(server);if(runtime==null)return;
        if(runtime.job!=null)processJob(server,runtime);
        if(server.getTicks()%20==0&&!runtime.intents.isEmpty()&&runtime.job==null)recoverIntents(server,runtime);
        for(Draft draft:List.copyOf(runtime.drafts.values())){ServerPlayerEntity player=server.getPlayerManager().getPlayer(draft.player);if(player!=null&&(!AuthoringRuntime.isAuthor(player)||!player.getCommandSource().hasPermissionLevel(2)))cancel(runtime,server,player,false);}
    }

    private static void processJob(MinecraftServer server,Runtime runtime){CaptureJob job=runtime.job;
        try{
            ServerWorld world=resolveWorld(server,job.bounds);preflightLoaded(world,job.bounds);validateJobPins(server,runtime,job);
            int limit=Math.min(job.bounds.cells(),job.cursor+CELLS_PER_TICK);
            if(job.stage==JobStage.CAPTURE){for(;job.cursor<limit;job.cursor++)job.capture(readCell(server,world,job.bounds,job.cursor,true));if(job.cursor==job.bounds.cells()){job.snapshot=job.snapshot();job.cursor=0;job.stage=JobStage.VERIFY;}}
            else{for(;job.cursor<limit;job.cursor++){AreaCell live=readCell(server,world,job.bounds,job.cursor,true);if(!live.equals(job.snapshot.cell(job.cursor)))throw new IllegalStateException("Area changed during capture at cell "+job.cursor+"; no snapshot was published.");}if(job.cursor==job.bounds.cells())finishJob(server,runtime,job);}
        }catch(Exception exception){runtime.job=null;if(!runtime.intents.containsKey(job.operationId))RuntimeHolds.release(server,CAPTURE_HOLD);notify(server,job.player,"Area change capture failed: "+safe(exception.getMessage())+" The prior saved state is unchanged.");ChronicleCore.LOGGER.error("Area capture {} failed",job.operationId,exception);}
    }

    private static void finishJob(MinecraftServer server,Runtime runtime,CaptureJob job)throws IOException{
        requirePublishingActor(server,job);
        SnapshotRef ref=runtime.snapshots.put(job.snapshot);
        if(!job.initial){AreaChange updated=runtime.catalog.replaceSnapshot(job.areaId,job.expectedAreaRevision,resolveRegion(server,job.bounds),job.state,ref);runtime.job=null;RuntimeHolds.release(server,CAPTURE_HOLD);notify(server,job.player,"Saved current area as "+job.state+" for '"+updated.name()+"'.");return;}
        CaptureIntent intent=new CaptureIntent(job.operationId,job.player,job.areaId,job.areaName,job.state,job.bounds,ref,job.expectedCatalogRevision,job.expectedAuthoringRevision,IntentStage.PREPARED,Instant.now().toString());
        Map<UUID,CaptureIntent> next=new LinkedHashMap<>(runtime.intents);next.put(intent.operationId,intent);runtime.intentStore.save(next);runtime.intents.clear();runtime.intents.putAll(next);runtime.job=null;publishIntent(server,runtime,intent);
    }

    private static void recoverIntents(MinecraftServer server,Runtime runtime){for(CaptureIntent intent:List.copyOf(runtime.intents.values()))try{publishIntent(server,runtime,intent);}catch(Exception exception){RuntimeHolds.hold(server,CAPTURE_HOLD,"An Area change capture publication requires recovery.");ChronicleCore.LOGGER.error("Area capture publication {} remains unresolved",intent.operationId,exception);return;}}

    private static void publishIntent(MinecraftServer server,Runtime runtime,CaptureIntent original)throws IOException{
        CaptureIntent intent=runtime.intents.get(original.operationId);if(intent==null)return;runtime.snapshots.load(intent.snapshot);AuthoringRuntime authoring=AuthoringRuntime.get(server);
        AuthoringSite current=authoring.service().state().sites().get(intent.bounds.regionId());AuthoringSite.Region region;
        if(current==null){if(authoring.service().state().revision()!=intent.expectedAuthoringRevision)throw new IllegalStateException("Authoring registry changed before Area Region publication");runtime.publishingRegion=intent.areaId;try{region=(AuthoringSite.Region)authoring.service().put(new AuthoringSite.Region(intent.bounds.regionId(),intent.bounds.dimension(),intent.bounds.regionRevision(),intent.bounds.min(),intent.bounds.max()),false);}finally{runtime.publishingRegion=null;}if(!intent.bounds.matches(region))throw new IllegalStateException("Published Core Region revision/geometry differs from capture intent");intent=intent.withStage(IntentStage.REGION_SAVED);replaceIntent(runtime,intent);}else if(current instanceof AuthoringSite.Region existing&&intent.bounds.matches(existing)){region=existing;if(intent.stage==IntentStage.PREPARED){intent=intent.withStage(IntentStage.REGION_SAVED);replaceIntent(runtime,intent);}}else throw new IllegalStateException("Area capture Region ID now identifies different geometry");
        AreaChange area=runtime.catalog.state().areas().get(intent.areaId);if(area==null){runtime.catalog.create(intent.areaId,intent.areaName,region,intent.state,intent.snapshot,intent.expectedCatalogRevision);}else if(area.revision()!=1||!area.bounds().equals(intent.bounds)||!area.snapshot(intent.state).equals(Optional.of(intent.snapshot)))throw new IllegalStateException("Area catalog contains different evidence for pending capture");
        Map<UUID,CaptureIntent> next=new LinkedHashMap<>(runtime.intents);next.remove(intent.operationId);runtime.intentStore.save(next);runtime.intents.clear();runtime.intents.putAll(next);if(runtime.intents.isEmpty())RuntimeHolds.release(server,CAPTURE_HOLD);
        Draft draft=runtime.drafts.remove(intent.player);ServerPlayerEntity player=server.getPlayerManager().getPlayer(intent.player);if(draft!=null&&player!=null){cleanupTool(player,draft);if(AuthoringRuntime.isAuthor(player))AuthoringRuntime.get(server).restorePresentation(player,draft.presentationEpoch,draft.previous);}notify(server,intent.player,"Saved Area change '"+intent.areaName+"' with its "+intent.state+" state.");
    }

    private static void replaceIntent(Runtime runtime,CaptureIntent intent)throws IOException{Map<UUID,CaptureIntent> next=new LinkedHashMap<>(runtime.intents);next.put(intent.operationId,intent);runtime.intentStore.save(next);runtime.intents.clear();runtime.intents.putAll(next);}

    private static void beginJob(MinecraftServer server,Runtime runtime,CaptureJob job){if(!runtime.intents.isEmpty())throw new IllegalStateException("A previous Area capture publication requires recovery first.");if(RuntimeHolds.heldByOther(server,CAPTURE_HOLD))throw new IllegalStateException("Another unresolved Chronicle operation currently holds play.");runtime.job=job;RuntimeHolds.hold(server,CAPTURE_HOLD,"Area change capture is verifying a loaded region.");}

    private static void validateJobPins(MinecraftServer server,Runtime runtime,CaptureJob job){if(job.initial){if(AuthoringRuntime.get(server).service().state().revision()!=job.expectedAuthoringRevision)throw new IllegalStateException("Authoring registry changed while the Area capture was running");Draft draft=runtime.drafts.get(job.player);if(draft==null||!draft.id.equals(job.areaId)||!draft.token.equals(job.draftToken))throw new IllegalStateException("Area draft is no longer current");}else{AreaChange area=runtime.catalog.state().areas().get(job.areaId);if(area==null||area.revision()!=job.expectedAreaRevision||!area.bounds().equals(job.bounds))throw new IllegalStateException("Area change changed while capture was running");resolveRegion(server,job.bounds);}}
    private static void requirePublishingActor(MinecraftServer server,CaptureJob job){ServerPlayerEntity actor=server.getPlayerManager().getPlayer(job.player);if(actor==null||!actor.getCommandSource().hasPermissionLevel(2))throw new IllegalStateException("The Area capture operator must remain online and an operator until publication.");if(job.initial&&!AuthoringRuntime.isAuthor(actor))throw new IllegalStateException("Initial Area publication still requires non-active Editing.");}

    private static AreaCell readCell(MinecraftServer server,ServerWorld world,AreaBounds bounds,int index,boolean templateSource){BlockPos pos=position(bounds,index);BlockState state=world.getBlockState(pos);Identifier blockId=Registries.BLOCK.getId(state.getBlock());String stateJson=BlockState.CODEC.encodeStart(RegistryOps.of(JsonOps.INSTANCE,server.getRegistryManager()),templateSource?AreaWorldPolicies.templateState(world,pos,state):state).getOrThrow(message->new IllegalArgumentException("Unable to encode block state at "+pos+": "+message)).toString();BlockEntity entity=world.getBlockEntity(pos);String nbt;
        CaptureAdapter adapter=BLOCK_ADAPTERS.getOrDefault(blockId.toString(),ADAPTERS.get(blockId.getNamespace()));if(adapter!=null){CaptureData data=adapter.capture(server,world,pos,state,entity);if(data==null)throw new IllegalStateException("Capture adapter refused "+blockId+" at "+pos);nbt=templateSource?data.blockEntityNbt():rawBlockEntity(server,entity);}
        else{if(!blockId.getNamespace().equals("minecraft"))throw new IllegalStateException("Custom block "+blockId+" requires an audited Area capture adapter.");validateStructure(bounds,pos,state);nbt=vanillaBlockEntity(server,pos,entity,templateSource);}
        return new AreaCell(stateJson,nbt);
    }

    private static String vanillaBlockEntity(MinecraftServer server,BlockPos pos,BlockEntity entity,boolean templateSource){if(entity==null)return null;
        if(!(entity instanceof ChestBlockEntity)&&!(entity instanceof BarrelBlockEntity)&&!(entity instanceof ShulkerBoxBlockEntity)&&!(entity instanceof SignBlockEntity))throw new IllegalStateException("Unsupported vanilla block entity "+Registries.BLOCK_ENTITY_TYPE.getId(entity.getType())+" at "+pos);
        if(templateSource&&entity instanceof LootableContainerBlockEntity lootable&&lootable.getLootTable()!=null)throw new IllegalStateException("Container at "+pos+" has unresolved loot table "+lootable.getLootTable().getValue()+"; capture will not roll it.");NbtCompound payload=entity.createNbtWithIdentifyingData(server.getRegistryManager());if(templateSource&&containsManagedLore(payload))throw new IllegalStateException("Managed Chronicle lore provenance at "+pos+" cannot be cloned; use Books & Lore definitions.");return payload.toString();
    }

    private static String rawBlockEntity(MinecraftServer server,BlockEntity entity){return entity==null?null:entity.createNbtWithIdentifyingData(server.getRegistryManager()).toString();}
    private static boolean containsManagedLore(NbtElement root){ArrayDeque<NbtNode> pending=new ArrayDeque<>();pending.push(new NbtNode(root,0));int visited=0;while(!pending.isEmpty()){NbtNode node=pending.pop();if(++visited>MAX_NBT_TRAVERSAL_NODES)throw new IllegalStateException("Block entity NBT traversal exceeds "+MAX_NBT_TRAVERSAL_NODES+" nodes");if(node.depth>MAX_NBT_TRAVERSAL_DEPTH)throw new IllegalStateException("Block entity NBT traversal exceeds depth "+MAX_NBT_TRAVERSAL_DEPTH);if(node.value instanceof NbtCompound compound){for(String key:compound.getKeys()){if(key.equals("chronicle_book"))return true;NbtElement child=compound.get(key);if(child!=null)pending.push(new NbtNode(child,node.depth+1));}}else if(node.value instanceof NbtList list){for(int i=0;i<list.size();i++){NbtElement child=list.get(i);if(child!=null)pending.push(new NbtNode(child,node.depth+1));}}}return false;}

    private static void validateStructure(AreaBounds bounds,BlockPos pos,BlockState state){if(state.contains(ChestBlock.CHEST_TYPE)&&state.get(ChestBlock.CHEST_TYPE)!=ChestType.SINGLE){BlockPos other=pos.offset(ChestBlock.getFacing(state));if(!contains(bounds,other))throw new IllegalStateException("Partial double chest crosses Area bounds at "+pos);}
        if(state.contains(Properties.DOUBLE_BLOCK_HALF)){DoubleBlockHalf half=state.get(Properties.DOUBLE_BLOCK_HALF);BlockPos other=half==DoubleBlockHalf.LOWER?pos.up():pos.down();if(!contains(bounds,other))throw new IllegalStateException("Partial two-block structure crosses Area bounds at "+pos);}
        if(state.contains(Properties.BED_PART)){BlockPos other=pos.offset(BedBlock.getOppositePartDirection(state));if(!contains(bounds,other))throw new IllegalStateException("Partial bed crosses Area bounds at "+pos);}}

    public static boolean isDebugTool(ItemStack stack){return toolToken(stack)!=null;}
    /** Metadata-only packet route, before the paused-gameplay gate; it never permits ordinary block use. */
    public static boolean selectPacket(ServerPlayerEntity player,Hand hand,BlockPos pos,int corner){
        if(!isDebugTool(player.getStackInHand(hand)))return false;
        selectWithTool(player,player.getEntityWorld(),hand,pos,corner);return true;
    }
    private static ActionResult selectWithTool(net.minecraft.entity.player.PlayerEntity player,net.minecraft.world.World world,Hand hand,BlockPos pos,int corner){String token=toolToken(player.getStackInHand(hand));if(token==null)return ActionResult.PASS;if(hand!=Hand.MAIN_HAND)return ActionResult.FAIL;if(!(world instanceof ServerWorld serverWorld))return ActionResult.SUCCESS;Runtime runtime=WORLDS.get(serverWorld.getServer());Draft draft=runtime==null?null:runtime.drafts.get(player.getUuid());if(draft==null||!draft.token.toString().equals(token))return ActionResult.FAIL;try{setDraftCorner((ServerPlayerEntity)player,corner,toPosition(pos));player.sendMessage(Text.literal("Area change position "+corner+" set at "+pos.toShortString()+"."),false);}catch(RuntimeException exception){player.sendMessage(Text.literal(safe(exception.getMessage())),false);}return ActionResult.SUCCESS;}

    private static ToolLease issueTool(ServerPlayerEntity player,UUID token){int previous=player.getInventory().getSelectedSlot();int toolSlot=-1;for(int i=0;i<9;i++)if(player.getInventory().getStack(i).isEmpty()){toolSlot=i;break;}int displaced=-1;ItemStack original=null;if(toolSlot<0){for(int i=9;i<36;i++)if(player.getInventory().getStack(i).isEmpty()){displaced=i;break;}if(displaced<0)return null;original=player.getInventory().getStack(previous).copy();player.getInventory().setStack(displaced,original.copy());player.getInventory().setStack(previous,ItemStack.EMPTY);toolSlot=previous;}ItemStack tool=new ItemStack(Items.WOODEN_SHOVEL);NbtCompound tag=new NbtCompound();tag.putString(TOOL_TAG,token.toString());tool.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(tag));tool.set(DataComponentTypes.CUSTOM_NAME,Text.literal("Area change Debug shovel"));player.getInventory().setStack(toolSlot,tool);player.getInventory().setSelectedSlot(toolSlot);player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();return new ToolLease(token,previous,toolSlot,displaced,original);}

    private static void cleanupTool(ServerPlayerEntity player,Draft draft){ToolLease lease=draft.tool;if(lease==null)return;ItemStack current=player.getInventory().getStack(lease.toolSlot);if(lease.token.toString().equals(toolToken(current))){player.getInventory().setStack(lease.toolSlot,ItemStack.EMPTY);if(lease.original!=null&&lease.displacedSlot>=0&&player.getInventory().getStack(lease.toolSlot).isEmpty()&&ItemStack.areEqual(player.getInventory().getStack(lease.displacedSlot),lease.original)){player.getInventory().setStack(lease.toolSlot,lease.original.copy());player.getInventory().setStack(lease.displacedSlot,ItemStack.EMPTY);}}if(player.getInventory().getSelectedSlot()==lease.toolSlot)player.getInventory().setSelectedSlot(lease.previousSelected);player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();}
    private static String toolToken(ItemStack stack){NbtComponent data=stack.get(DataComponentTypes.CUSTOM_DATA);return data==null?null:data.copyNbt().getString(TOOL_TAG).orElse(null);}

    private static boolean cancel(Runtime runtime,MinecraftServer server,ServerPlayerEntity player,boolean message){Draft draft=runtime.drafts.get(player.getUuid());if(draft==null)return false;if(runtime.intents.values().stream().anyMatch(intent->intent.player.equals(player.getUuid())))throw new IllegalStateException("This Area change is already durably publishing and cannot be cancelled.");if(runtime.job!=null&&runtime.job.player.equals(player.getUuid())){runtime.job=null;RuntimeHolds.release(server,CAPTURE_HOLD);}runtime.drafts.remove(player.getUuid());cleanupTool(player,draft);if(AuthoringRuntime.isAuthor(player))AuthoringRuntime.get(server).restorePresentation(player,draft.presentationEpoch,draft.previous);if(message)player.sendMessage(Text.literal("Area change draft cancelled; nothing was published."),false);return true;}
    private static void disconnected(ServerPlayerEntity player){Runtime runtime=WORLDS.get(player.getEntityWorld().getServer());if(runtime==null)return;Draft draft=runtime.drafts.get(player.getUuid());if(draft==null)return;cleanupTool(player,draft);if(runtime.intents.values().stream().anyMatch(intent->intent.player.equals(player.getUuid()))){runtime.drafts.remove(player.getUuid());return;}if(runtime.job!=null&&runtime.job.player.equals(player.getUuid())){runtime.job=null;RuntimeHolds.release(player.getEntityWorld().getServer(),CAPTURE_HOLD);}runtime.drafts.remove(player.getUuid());}

    private static void guardRegionMutation(Runtime runtime,AuthoringSite existing,AuthoringSite proposed){String id=existing!=null?existing.id():proposed==null?null:proposed.id();if(id==null)return;if(id.equals(runtime.publishingRegion))return;if(runtime.drafts.values().stream().anyMatch(draft->draft.id.equals(id))||(runtime.job!=null&&runtime.job.bounds.regionId().equals(id))||runtime.intents.values().stream().anyMatch(intent->intent.bounds.regionId().equals(id))||runtime.operations.state().operations().values().stream().anyMatch(operation->operation.unresolved()&&operation.bounds().regionId().equals(id)))throw new IllegalStateException("Core Region is pinned by an Area change draft, capture, publication or unresolved operation: "+id);}

    private static Runtime runtime(MinecraftServer server){if(!server.isOnThread())throw new IllegalStateException("Area changes require the server thread.");Runtime r=WORLDS.get(server);if(r==null)throw new IllegalStateException("Area changes are unavailable; inspect the server log and area-changes files.");return r;}
    private static ServerPlayerEntity requireOperator(ServerCommandSource source){if(!source.hasPermissionLevel(2)||source.getPlayer()==null)throw new IllegalStateException("Area changes require an operator player.");return source.getPlayer();}
    private static ServerWorld resolveWorld(MinecraftServer server,AreaBounds bounds){ServerWorld world=server.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(bounds.dimension())));if(world==null)throw new IllegalStateException("Area dimension is unavailable: "+bounds.dimension());return world;}
    private static AuthoringSite.Region resolveRegion(MinecraftServer server,AreaBounds bounds){AuthoringSite site=AuthoringRuntime.get(server).service().state().sites().get(bounds.regionId());if(!(site instanceof AuthoringSite.Region region)||!bounds.matches(region))throw new IllegalStateException("Core Region revision/geometry is stale: "+bounds.regionId());return region;}
    private static void preflightLoaded(ServerWorld world,AreaBounds b){checkPosition(world,b.min());checkPosition(world,b.max());for(int x=Math.floorDiv(b.min().x(),16);x<=Math.floorDiv(b.max().x(),16);x++)for(int z=Math.floorDiv(b.min().z(),16);z<=Math.floorDiv(b.max().z(),16);z++)if(!world.getChunkManager().isChunkLoaded(x,z))throw new IllegalStateException("Area contains unloaded chunk "+x+","+z+"; move near it and retry. No chunks were forced.");}
    private static void checkPosition(ServerWorld world,BlockPosition p){BlockPos pos=new BlockPos(p.x(),p.y(),p.z());if(!world.isInBuildLimit(pos))throw new IllegalArgumentException("Area position is outside dimension build limits");if(!world.getWorldBorder().contains(pos))throw new IllegalArgumentException("Area position is outside the world border");}
    private static BlockPos position(AreaBounds b,int i){int x=i%b.sizeX();int q=i/b.sizeX();int z=q%b.sizeZ();int y=q/b.sizeZ();return new BlockPos(b.min().x()+x,b.min().y()+y,b.min().z()+z);}
    private static int cellIndex(AreaBounds b,BlockPos p){int x=p.getX()-b.min().x(),y=p.getY()-b.min().y(),z=p.getZ()-b.min().z();return x+b.sizeX()*(z+b.sizeZ()*y);}
    private static boolean contains(AreaBounds b,BlockPos p){return p.getX()>=b.min().x()&&p.getX()<=b.max().x()&&p.getY()>=b.min().y()&&p.getY()<=b.max().y()&&p.getZ()>=b.min().z()&&p.getZ()<=b.max().z();}
    private static BlockPosition toPosition(BlockPos p){return new BlockPosition(p.getX(),p.getY(),p.getZ());}
    private static String dimension(ServerPlayerEntity p){return p.getEntityWorld().getRegistryKey().getValue().toString();}
    private static void notify(MinecraftServer server,UUID player,String message){ServerPlayerEntity target=server.getPlayerManager().getPlayer(player);if(target!=null)target.sendMessage(Text.literal(message),false);}
    private static String safe(String message){return message==null||message.isBlank()?"Unknown error":message;}
    private static String nextId(Runtime runtime,AuthoringRuntime authoring,String name){String slug=name==null?"area":name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+","_").replaceAll("^_+|_+$","");if(slug.isBlank()||!Character.isLetter(slug.charAt(0)))slug="area_"+slug;if(slug.length()>58)slug=slug.substring(0,58);String base="area_"+slug;String id=base;for(int suffix=2;runtime.catalog.state().areas().containsKey(id)||runtime.catalog.state().tombstones().contains(id)||authoring.service().state().sites().containsKey(id)||draftIdUsed(runtime,id);suffix++)id=base.substring(0,Math.min(base.length(),63-Integer.toString(suffix).length()))+"_"+suffix;return id;}
    private static boolean draftIdUsed(Runtime runtime,String id){return runtime.drafts.values().stream().anyMatch(d->d.id.equals(id));}
    private static void creationCapacity(Runtime r,AuthoringRuntime authoring){if(r.catalog.state().areas().size()>=AreaCatalog.MAX_AREAS)throw new IllegalStateException("The Area definition limit is reached.");if(authoring.service().state().sites().size()>=io.github.chronicle.core.authoring.AuthoringRegistry.MAX_SITES)throw new IllegalStateException("The Core Region/location registry is full.");}
    private static void validateNewId(Runtime r,AuthoringRuntime a,String id){if(id==null||!AuthoringSite.ID.matcher(id).matches())throw new IllegalArgumentException("Area ID must match "+AuthoringSite.ID.pattern());if(r.catalog.state().areas().containsKey(id)||r.catalog.state().tombstones().contains(id)||a.service().state().sites().containsKey(id)||draftIdUsed(r,id))throw new IllegalStateException("Area/Region ID is already used or retired: "+id);}

    private static void commands(CommandDispatcher<ServerCommandSource> dispatcher){
        for(String root:List.of("chronicle","chronicles")){
            var beginNode=literal("begin")
                    .then(literal("start").then(argument("name",StringArgumentType.greedyString()).executes(c->command(c.getSource(),()->"Area draft "+begin(c.getSource(),StringArgumentType.getString(c,"name"),AreaState.START).id()+" started."))))
                    .then(literal("end").then(argument("name",StringArgumentType.greedyString()).executes(c->command(c.getSource(),()->"Area draft "+begin(c.getSource(),StringArgumentType.getString(c,"name"),AreaState.END).id()+" started."))));
            var captureNode=literal("capture").then(argument("id",StringArgumentType.string())
                    .then(literal("start").then(argument("revision",LongArgumentType.longArg(1)).executes(c->command(c.getSource(),()->"Area recapture "+captureCurrent(c.getSource(),StringArgumentType.getString(c,"id"),AreaState.START,LongArgumentType.getLong(c,"revision"))+" started."))))
                    .then(literal("end").then(argument("revision",LongArgumentType.longArg(1)).executes(c->command(c.getSource(),()->"Area recapture "+captureCurrent(c.getSource(),StringArgumentType.getString(c,"id"),AreaState.END,LongArgumentType.getLong(c,"revision"))+" started.")))));
            var change=literal("change").then(beginNode)
                    .then(literal("save").executes(c->command(c.getSource(),()->"Area capture "+saveDraft(c.getSource())+" started.")))
                    .then(literal("cancel").executes(c->command(c.getSource(),()->{cancel(c.getSource());return"Area draft cancelled.";})))
                    .then(captureNode);
            dispatcher.register(literal(root).then(literal("area").then(change)));
        }
    }
    private static int command(ServerCommandSource source,Command action){try{String result=action.run();source.sendFeedback(()->Text.literal(result),false);return 1;}catch(IOException exception){ChronicleCore.LOGGER.error("Area change command persistence failed",exception);source.sendError(Text.literal("Area change could not be saved; previous definitions remain authoritative. Check the server log."));}catch(RuntimeException exception){source.sendError(Text.literal(safe(exception.getMessage())));}return 0;}

    public record DraftView(UUID token,String id,String name,AreaState firstState,String dimension,BlockPosition first,BlockPosition second,boolean toolIssued,boolean capturing){}
    public record CaptureData(String blockEntityNbt){}
    @FunctionalInterface public interface CaptureAdapter{CaptureData capture(MinecraftServer server,ServerWorld world,BlockPos pos,BlockState state,BlockEntity blockEntity);}
    @FunctionalInterface private interface Command{String run()throws IOException;}
    private enum JobStage{CAPTURE,VERIFY}private enum IntentStage{PREPARED,REGION_SAVED}

    private static final class Runtime{final AreaCatalogService catalog;final AreaSnapshotStore snapshots;final AreaOperationService operations;final IntentStore intentStore;final Map<UUID,Draft>drafts=new HashMap<>();final Map<UUID,CaptureIntent>intents=new LinkedHashMap<>();CaptureJob job;String publishingRegion;Runtime(AreaCatalogService c,AreaSnapshotStore s,AreaOperationService o,IntentStore i){catalog=c;snapshots=s;operations=o;intentStore=i;}}
    private static final class Draft{final UUID token,player;final String id,name,dimension;final AreaState state;final AuthoringRuntime.Presentation previous;BlockPosition first,second;long presentationEpoch;ToolLease tool;Draft(UUID t,UUID p,String i,String n,AreaState s,String d,AuthoringRuntime.Presentation previous){token=t;player=p;id=i;name=n;state=s;dimension=d;this.previous=previous;}DraftView view(boolean capturing){return new DraftView(token,id,name,state,dimension,first,second,tool!=null,capturing);}}
    private record ToolLease(UUID token,int previousSelected,int toolSlot,int displacedSlot,ItemStack original){}
    private record CaptureIntent(UUID operationId,UUID player,String areaId,String areaName,AreaState state,AreaBounds bounds,SnapshotRef snapshot,long expectedCatalogRevision,long expectedAuthoringRevision,IntentStage stage,String createdAt){
        CaptureIntent{if(operationId==null||player==null||state==null||bounds==null||snapshot==null||stage==null)throw new IllegalArgumentException("capture intent fields are required");new AreaChange(areaId,areaName,bounds,1,state==AreaState.START?Optional.of(snapshot):Optional.empty(),state==AreaState.END?Optional.of(snapshot):Optional.empty());if(expectedCatalogRevision<0||expectedAuthoringRevision<0||bounds.regionRevision()!=expectedAuthoringRevision+1)throw new IllegalArgumentException("capture intent revisions are invalid");Instant parsed=Instant.parse(createdAt);if(!parsed.toString().equals(createdAt))throw new IllegalArgumentException("capture intent time is not canonical");}
        CaptureIntent withStage(IntentStage value){return new CaptureIntent(operationId,player,areaId,areaName,state,bounds,snapshot,expectedCatalogRevision,expectedAuthoringRevision,value,createdAt);}}
    private record NbtNode(NbtElement value,int depth){}

    private static final class CaptureJob{final UUID operationId=UUID.randomUUID(),player;final String areaId,areaName;final AreaState state;final AreaBounds bounds;final boolean initial;final long expectedCatalogRevision,expectedAuthoringRevision,expectedAreaRevision;final UUID draftToken;final List<String>palette=new ArrayList<>();final Map<String,Integer>paletteLookup=new HashMap<>();final int[]indices;final Map<Integer,String>entities=new HashMap<>();long decodedBytes;int cursor;JobStage stage=JobStage.CAPTURE;AreaSnapshot snapshot;
        private CaptureJob(UUID player,String id,String name,AreaState state,AreaBounds bounds,boolean initial,long catalogRevision,long authoringRevision,long areaRevision,UUID draftToken){this.player=player;areaId=id;areaName=name;this.state=state;this.bounds=bounds;this.initial=initial;expectedCatalogRevision=catalogRevision;expectedAuthoringRevision=authoringRevision;expectedAreaRevision=areaRevision;this.draftToken=draftToken;indices=new int[bounds.cells()];decodedBytes=(long)bounds.cells()*Integer.BYTES;}
        static CaptureJob initial(UUID player,Draft draft,AreaBounds bounds,long catalogRevision,long authoringRevision){return new CaptureJob(player,draft.id,draft.name,draft.state,bounds,true,catalogRevision,authoringRevision,0,draft.token);}
        static CaptureJob recapture(UUID player,AreaChange area,AreaState state){return new CaptureJob(player,area.id(),area.name(),state,area.bounds(),false,0,0,area.revision(),null);}
        void capture(AreaCell cell){Integer index=paletteLookup.get(cell.blockStateJson());if(index==null){if(palette.size()>=AreaSnapshot.MAX_PALETTE)throw new IllegalStateException("Area snapshot palette limit exceeded");long next=Math.addExact(decodedBytes,cell.blockStateJson().getBytes(StandardCharsets.UTF_8).length);requireDecoded(next);decodedBytes=next;index=palette.size();paletteLookup.put(cell.blockStateJson(),index);palette.add(cell.blockStateJson());}indices[cursor]=index;if(cell.blockEntityNbt()!=null){if(entities.size()>=AreaSnapshot.MAX_BLOCK_ENTITIES)throw new IllegalStateException("Area snapshot block-entity limit exceeded");long next=Math.addExact(decodedBytes,Integer.BYTES+cell.blockEntityNbt().getBytes(StandardCharsets.UTF_8).length);requireDecoded(next);decodedBytes=next;entities.put(cursor,cell.blockEntityNbt());}}
        private static void requireDecoded(long bytes){if(bytes>AreaSnapshot.MAX_DECODED_BYTES)throw new IllegalStateException("Area snapshot decoded data exceeds 64 MiB");}
        AreaSnapshot snapshot(){return new AreaSnapshot(bounds,palette,indices,entities);}}

    /** Small strict atomic journal solely for bridging Core Region + Area catalog publication. */
    private static final class IntentStore{private static final int SCHEMA=1,MAX_BYTES=4*1024*1024;private final Path file;private byte[] observed;private Boolean present;IntentStore(Path file){this.file=file.toAbsolutePath().normalize();}
        Map<UUID,CaptureIntent> load()throws IOException{if(!Files.exists(file)){present=false;observed=null;return Map.of();}if(!Files.isRegularFile(file)||Files.size(file)>MAX_BYTES)throw new IOException("capture intent journal size/type is invalid");byte[] bytes=Files.readAllBytes(file);try{Map<UUID,CaptureIntent> decoded=decode(new String(bytes,StandardCharsets.UTF_8));present=true;observed=hash(bytes);return decoded;}catch(RuntimeException e){throw new IOException("capture intent journal is invalid and was preserved",e);}}
        void save(Map<UUID,CaptureIntent> intents)throws IOException{byte[] bytes=encode(intents).getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("capture intent journal exceeds 4 MiB");Path parent=file.getParent();Files.createDirectories(parent);boolean exists=Files.exists(file);if(present!=null&&present!=exists)throw new IOException("capture intent journal presence changed externally");if(exists&&Files.size(file)>MAX_BYTES)throw new IOException("capture intent journal changed to an oversized file");if(exists&&observed!=null&&!java.security.MessageDigest.isEqual(observed,hash(Files.readAllBytes(file))))throw new IOException("capture intent journal changed externally");Path tmp=Files.createTempFile(parent,"capture-intents-",".tmp");try{try(FileChannel c=FileChannel.open(tmp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())c.write(b);c.force(true);}try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){throw new IOException("atomic capture-intent replacement unsupported",e);}present=true;observed=hash(bytes);}finally{Files.deleteIfExists(tmp);}}
        private static String encode(Map<UUID,CaptureIntent> intents){JsonObject root=new JsonObject();root.addProperty("schemaVersion",SCHEMA);JsonArray values=new JsonArray();for(CaptureIntent i:intents.values()){JsonObject v=new JsonObject();v.addProperty("operationId",i.operationId.toString());v.addProperty("player",i.player.toString());v.addProperty("areaId",i.areaId);v.addProperty("areaName",i.areaName);v.addProperty("state",i.state.name());v.add("bounds",bounds(i.bounds));v.add("snapshot",ref(i.snapshot));v.addProperty("expectedCatalogRevision",i.expectedCatalogRevision);v.addProperty("expectedAuthoringRevision",i.expectedAuthoringRevision);v.addProperty("stage",i.stage.name());v.addProperty("createdAt",i.createdAt);values.add(v);}root.add("intents",values);return root.toString();}
        private static Map<UUID,CaptureIntent> decode(String source){JsonObject root=BookConfig.strictObject(source);exact(root,Set.of("schemaVersion","intents"));if(number(root,"schemaVersion")!=SCHEMA)throw new IllegalArgumentException("unsupported capture-intent schema");JsonArray values=root.getAsJsonArray("intents");if(values.size()>16)throw new IllegalArgumentException("too many capture intents");Map<UUID,CaptureIntent> result=new LinkedHashMap<>();for(JsonElement element:values){JsonObject v=element.getAsJsonObject();exact(v,Set.of("operationId","player","areaId","areaName","state","bounds","snapshot","expectedCatalogRevision","expectedAuthoringRevision","stage","createdAt"));CaptureIntent i=new CaptureIntent(uuid(v,"operationId"),uuid(v,"player"),string(v,"areaId"),string(v,"areaName"),AreaState.valueOf(string(v,"state")),bounds(v.getAsJsonObject("bounds")),ref(v.getAsJsonObject("snapshot")),number(v,"expectedCatalogRevision"),number(v,"expectedAuthoringRevision"),IntentStage.valueOf(string(v,"stage")),string(v,"createdAt"));if(result.putIfAbsent(i.operationId,i)!=null)throw new IllegalArgumentException("duplicate capture intent");}return result;}
        private static JsonObject bounds(AreaBounds b){JsonObject v=new JsonObject();v.addProperty("regionId",b.regionId());v.addProperty("dimension",b.dimension());v.addProperty("revision",b.regionRevision());v.addProperty("minX",b.min().x());v.addProperty("minY",b.min().y());v.addProperty("minZ",b.min().z());v.addProperty("maxX",b.max().x());v.addProperty("maxY",b.max().y());v.addProperty("maxZ",b.max().z());return v;}
        private static AreaBounds bounds(JsonObject v){exact(v,Set.of("regionId","dimension","revision","minX","minY","minZ","maxX","maxY","maxZ"));return new AreaBounds(string(v,"regionId"),string(v,"dimension"),number(v,"revision"),new BlockPosition(integer(v,"minX"),integer(v,"minY"),integer(v,"minZ")),new BlockPosition(integer(v,"maxX"),integer(v,"maxY"),integer(v,"maxZ")));}
        private static JsonObject ref(SnapshotRef r){JsonObject v=new JsonObject();v.addProperty("sha256",r.sha256());v.add("bounds",bounds(r.bounds()));v.addProperty("cells",r.cells());v.addProperty("encodedBytes",r.encodedBytes());return v;}
        private static SnapshotRef ref(JsonObject v){exact(v,Set.of("sha256","bounds","cells","encodedBytes"));return new SnapshotRef(string(v,"sha256"),bounds(v.getAsJsonObject("bounds")),integer(v,"cells"),number(v,"encodedBytes"));}
        private static String string(JsonObject v,String f){if(!v.has(f)||!v.get(f).isJsonPrimitive()||!v.getAsJsonPrimitive(f).isString())throw new IllegalArgumentException(f+" must be string");return v.get(f).getAsString();}
        private static long number(JsonObject v,String f){if(!v.has(f)||!v.get(f).isJsonPrimitive()||!v.get(f).getAsString().matches("-?[0-9]+"))throw new IllegalArgumentException(f+" must be integer");return v.get(f).getAsLong();}
        private static int integer(JsonObject v,String f){long n=number(v,f);if(n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw new IllegalArgumentException(f+" outside int range");return(int)n;}
        private static UUID uuid(JsonObject v,String f){String s=string(v,f);UUID id=UUID.fromString(s);if(!id.toString().equals(s))throw new IllegalArgumentException(f+" not canonical UUID");return id;}
        private static void exact(JsonObject v,Set<String> fields){if(!v.keySet().equals(fields))throw new IllegalArgumentException("capture intent fields do not match schema");}
        private static byte[] hash(byte[] bytes)throws IOException{try{return java.security.MessageDigest.getInstance("SHA-256").digest(bytes);}catch(java.security.NoSuchAlgorithmException e){throw new IOException("SHA-256 unavailable",e);}}
    }
}
