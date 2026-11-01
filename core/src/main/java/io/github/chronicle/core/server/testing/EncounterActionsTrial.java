package io.github.chronicle.core.server.testing;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.encounters.EncounterGroup;
import io.github.chronicle.core.encounters.EncounterMember;
import io.github.chronicle.core.encounters.EncounterSpawnEntry;
import io.github.chronicle.core.encounters.EncounterTarget;
import io.github.chronicle.core.menu.EventEditorForm;
import io.github.chronicle.core.play.EventExecution;
import io.github.chronicle.core.play.EventSource;
import io.github.chronicle.core.play.ObservationCatalog;
import io.github.chronicle.core.play.PlayState;
import io.github.chronicle.core.server.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.util.WorldSavePath;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static net.minecraft.server.command.CommandManager.literal;

/** Opt-in native proof for bounded at-most-once Event encounter spawning. */
public final class EncounterActionsTrial {
    private static final String GROUP="test:encounter_patrol",EVENT="test:encounter_event",POINT="encounter_trial_point",WIDE_GROUP="test:encounter_wide",WIDE_EVENT="test:encounter_wide_event",WIDE_POINT="encounter_wide_point",INVALID_GROUP="test:encounter_invalid",INVALID_EVENT="test:encounter_invalid_event";
    private static final EncounterGroup DEFAULT=new EncounterGroup(GROUP,"Trial patrol","Native encounter fixture",List.of(new EncounterMember("minecraft:zombie",2),new EncounterMember("minecraft:villager",1)));
    private static final EncounterGroup WIDE=new EncounterGroup(WIDE_GROUP,"Wide footprint","Chunk-footprint and placement fixture",List.of(new EncounterMember("minecraft:ravager",2)));
    private static final EncounterGroup INVALID=new EncounterGroup(INVALID_GROUP,"Crowded geometry","Deterministic overlap refusal fixture",List.of(new EncounterMember("minecraft:zombie",2)));
    private static Job job;
    private static FootprintJob footprint;
    private static WideJob wideJob;
    private static UUID reviewPlayer;
    private EncounterActionsTrial(){}

    public static void register(){
        if(!"true".equals(System.getenv("CHRONICLE_ENCOUNTERS_TRIAL")))return;
        Encounters.registerDefaults(List.of(DEFAULT,WIDE,INVALID));
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("chronicle_encounters_trial")
                .then(literal("ui_prepare").executes(context->prepareUi(context.getSource())))
                .then(literal("ui_verify").then(literal("ui_wide").executes(context->verifyUi(context.getSource(),"ui_wide"))).then(literal("ui_compact").executes(context->verifyUi(context.getSource(),"ui_compact"))))
                .then(literal("uncertain").executes(context->seedUncertain(context.getSource())))
                .then(literal("preflight").executes(context->runPreflight(context.getSource())))
                .then(literal("footprint").executes(context->runFootprint(context.getSource())))
                .then(literal("wide_runtime").executes(context->runWideRuntime(context.getSource())))
                .then(literal("invalid_geometry").executes(context->runInvalidGeometry(context.getSource())))
                .then(literal("basic").executes(context->run(context.getSource(),false)))
                .then(literal("restart").executes(context->run(context.getSource(),true)))));
        ServerTickEvents.END_SERVER_TICK.register(EncounterActionsTrial::tick);
    }

    private static int prepareUi(ServerCommandSource source){try{fixture(source);require(source.hasPermissionLevel(2),"operator permission required");ServerPlayerEntity player=source.getPlayerOrThrow();var campaign=ChronicleRuntime.service(source.getServer());if(campaign.state().phase()==SessionPhase.INACTIVE){campaign.start(actor(player),1);campaign.pause(actor(player));}require(campaign.state().phase()==SessionPhase.PAUSED,"UI fixture must be paused");AuthoringRuntime authoring=AuthoringRuntime.get(source.getServer());authoring.enable(player.getUuid());if(!authoring.service().state().sites().containsKey(POINT))authoring.service().put(new AuthoringSite.Location(POINT,"minecraft:overworld",1,12.5,64,12.5,0,0),false);source.sendFeedback(()->Text.literal("Encounter trial PASS ui_prepare"),false);return 1;}catch(Exception failure){source.sendError(Text.literal("Encounter trial FAIL ui: "+message(failure)));return 0;}}

    private static int verifyUi(ServerCommandSource source,String phase){try{fixture(source);require(source.hasPermissionLevel(2),"operator permission required");String eventId="test:event_"+phase,groupId="test:"+phase;var configuration=EventRuntime.stored(source.getServer(),eventId);require(configuration!=null,"saved UI Event is missing");var actions=configuration.definition().encounterActions();require(actions.size()==1&&actions.getFirst().groupId().equals(groupId)&&actions.getFirst().targetOverride().isEmpty(),"saved UI Event encounter action did not retain its group binding");require(!PlayRuntime.service(source.getServer()).state().executions().containsKey(eventId),"saving the UI Event executed it");require(Encounters.liveOperationCount(source.getServer())==0,"saving the UI Event created an encounter operation");source.sendFeedback(()->Text.literal("Encounter trial PASS ui_verify "+phase),false);return 1;}catch(Exception failure){source.sendError(Text.literal("Encounter trial FAIL ui_verify "+phase+": "+message(failure)));return 0;}}

    private static int run(ServerCommandSource source,boolean restart){
        try{
            fixture(source);require(source.hasPermissionLevel(2),"operator permission required");require(job==null,"encounter trial is already running");
            ServerPlayerEntity player=source.getPlayerOrThrow();
            if(restart)restart(source,player);else basic(source,player);
            return 1;
        }catch(Exception failure){source.sendError(Text.literal("Encounter trial FAIL "+(restart?"restart":"basic")+": "+message(failure)));return 0;}
    }

    private static void basic(ServerCommandSource source,ServerPlayerEntity player)throws Exception{
        startEvent(source,player);job=new Job(player.getUuid(),false,null);
    }

    private static int runPreflight(ServerCommandSource source){try{fixture(source);require(source.hasPermissionLevel(2),"operator permission required");require(job==null,"encounter trial is already running");ServerPlayerEntity player=source.getPlayerOrThrow();EventExecution execution=startEvent(source,player);source.getWorld().setBlockState(new BlockPos(13,64,12),Blocks.STONE.getDefaultState(),3);job=new Job(player.getUuid(),false,execution.operationId(),true);return 1;}catch(Exception failure){source.sendError(Text.literal("Encounter trial FAIL preflight: "+message(failure)));return 0;}}

    private static int runFootprint(ServerCommandSource source){try{fixture(source);require(source.hasPermissionLevel(2),"operator permission required");require(job==null&&footprint==null,"encounter trial is already running");ServerPlayerEntity player=source.getPlayerOrThrow();int[] edge=loadedEastEdge(source);EventExecution execution=startWideEvent(source,player,edge);footprint=new FootprintJob(player.getUuid(),execution.operationId(),edge[0],edge[1]);return 1;}catch(Exception failure){source.sendError(Text.literal("Encounter trial FAIL footprint: "+message(failure)));return 0;}}

    private static int runWideRuntime(ServerCommandSource source){try{fixture(source);require(source.hasPermissionLevel(2),"operator permission required");require(job==null&&footprint==null&&wideJob==null&&reviewPlayer==null,"encounter trial is already running");ServerPlayerEntity player=source.getPlayerOrThrow();MinecraftServer server=source.getServer();server.setDifficulty(Difficulty.NORMAL,true);source.getWorld().setTimeOfDay(18000);for(int x=24;x<=40;x++)for(int z=24;z<=40;z++){source.getWorld().setBlockState(new BlockPos(x,63,z),Blocks.STONE.getDefaultState(),3);for(int y=64;y<=68;y++)source.getWorld().setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState(),3);}EventExecution execution=startWidePointEvent(source,player);wideJob=new WideJob(player.getUuid(),execution.operationId());return 1;}catch(Exception failure){source.sendError(Text.literal("Encounter trial FAIL wide_runtime: "+message(failure)));return 0;}}

    private static int runInvalidGeometry(ServerCommandSource source){try{fixture(source);require(source.hasPermissionLevel(2),"operator permission required");require(job==null&&footprint==null&&reviewPlayer==null,"encounter trial is already running");MinecraftServer server=source.getServer();ServerPlayerEntity player=source.getPlayerOrThrow();var campaign=ChronicleRuntime.service(server);require(campaign.state().phase()==SessionPhase.INACTIVE,"geometry trial requires a fresh campaign");AuthoringRuntime authoring=AuthoringRuntime.get(server);authoring.enable(player.getUuid());AuthoringSite.Location point=(AuthoringSite.Location)authoring.service().put(new AuthoringSite.Location("encounter_invalid_point","minecraft:overworld",1,12.5,64,12.5,0,0),false);AuthoringSite.Region crowded=(AuthoringSite.Region)authoring.service().put(new AuthoringSite.Region("encounter_invalid_region","minecraft:overworld",1,new BlockPosition(20,64,20),new BlockPosition(20,64,20)),false);var snapshot=Encounters.snapshot(server);Encounters.save(player,INVALID,new EncounterTarget(EncounterTarget.Kind.POINT,point.id(),point.revision()),snapshot.groups().get(INVALID_GROUP),snapshot.bindings().get(INVALID_GROUP),snapshot.groupRevision(),snapshot.bindingRevision());EventEditorForm.EncounterAction action=new EventEditorForm.EncounterAction(INVALID_GROUP,INVALID.label(),"GROUP","","",0,true,"");EventEditorForm form=new EventEditorForm(INVALID_EVENT,0,"Invalid geometry trial","Pre-begin deterministic refusal","","",ObservationCatalog.Audience.PARTICIPANTS.name(),"",0,PlayState.Mode.MANUAL.name(),"",0,0,List.of(),List.of(),true,Map.of(),EventSource.empty(),List.of(action));EventRuntime.save(player,form,null,true);snapshot=Encounters.snapshot(server);Encounters.save(player,INVALID,new EncounterTarget(EncounterTarget.Kind.REGION,crowded.id(),crowded.revision()),snapshot.groups().get(INVALID_GROUP),snapshot.bindings().get(INVALID_GROUP),snapshot.groupRevision(),snapshot.bindingRevision());campaign.start(actor(player),1);campaign.pause(actor(player));PlayRuntime.setParticipant(source,player.getUuid(),true);authoring.disable(player.getUuid());campaign.resume(actor(player));EventRuntime.Review forged=new EventRuntime.Review(EventRuntime.configuration(server,INVALID_EVENT),campaign.state().sessionNumber(),List.of(player.getUuid()),System.nanoTime(),"fixture");Exception refusal=null;try{EventRuntime.run(player,INVALID_EVENT,forged);}catch(IllegalArgumentException|IllegalStateException expected){refusal=expected;}require(refusal!=null&&message(refusal).toLowerCase(Locale.ROOT).contains("overlap"),"invalid geometry was not refused before execution");require(!PlayRuntime.service(server).state().executions().containsKey(INVALID_EVENT),"invalid geometry published an Event execution");require(Encounters.liveOperationCount(server)==0,"invalid geometry published an encounter operation");require(RuntimeHolds.reason(server)==null,"invalid geometry acquired an operation hold");campaign.pause(actor(player));source.sendFeedback(()->Text.literal("Encounter trial PASS invalid_geometry"),false);return 1;}catch(Exception failure){source.sendError(Text.literal("Encounter trial FAIL invalid_geometry: "+message(failure)));return 0;}}

    private static int[] loadedEastEdge(ServerCommandSource source)throws Exception{int centerX=source.getPlayerOrThrow().getBlockX()>>4,centerZ=source.getPlayerOrThrow().getBlockZ()>>4;for(int radius=0;radius<=64;radius++)for(int x=centerX-radius;x<=centerX+radius;x++)for(int z=centerZ-radius;z<=centerZ+radius;z++)if(source.getWorld().getChunkManager().isChunkLoaded(x,z)&&!source.getWorld().getChunkManager().isChunkLoaded(x+1,z))return new int[]{x,z};throw new IllegalStateException("fixture could not find a loaded-to-unloaded chunk edge");}

    private static EventExecution startWideEvent(ServerCommandSource source,ServerPlayerEntity player,int[] edge)throws Exception{MinecraftServer server=source.getServer();var campaign=ChronicleRuntime.service(server);require(campaign.state().phase()==SessionPhase.INACTIVE,"footprint trial requires a fresh campaign");campaign.start(actor(player),1);campaign.pause(actor(player));AuthoringRuntime authoring=AuthoringRuntime.get(server);authoring.enable(player.getUuid());double x=(edge[0]+1)*16-.25,z=edge[1]*16+8.5;AuthoringSite.Location point=(AuthoringSite.Location)authoring.service().put(new AuthoringSite.Location(WIDE_POINT,"minecraft:overworld",1,x,64,z,0,0),false);EventEditorForm.EncounterAction action=new EventEditorForm.EncounterAction(WIDE_GROUP,WIDE.label(),"POINT",point.id(),point.id(),point.revision(),true,"");EventEditorForm form=new EventEditorForm(WIDE_EVENT,0,"Footprint trial","Wide-mob chunk footprint proof","","",ObservationCatalog.Audience.PARTICIPANTS.name(),"",0,PlayState.Mode.MANUAL.name(),"",0,0,List.of(),List.of(),true,Map.of(),EventSource.empty(),List.of(action));EventRuntime.save(player,form,null,true);PlayRuntime.setParticipant(source,player.getUuid(),true);authoring.disable(player.getUuid());campaign.resume(actor(player));EventRuntime.run(player,WIDE_EVENT,EventRuntime.review(server,WIDE_EVENT));return PlayRuntime.service(server).state().executions().get(WIDE_EVENT);}

    private static EventExecution startWidePointEvent(ServerCommandSource source,ServerPlayerEntity player)throws Exception{MinecraftServer server=source.getServer();var campaign=ChronicleRuntime.service(server);require(campaign.state().phase()==SessionPhase.INACTIVE,"wide runtime trial requires a fresh campaign");campaign.start(actor(player),1);campaign.pause(actor(player));AuthoringRuntime authoring=AuthoringRuntime.get(server);authoring.enable(player.getUuid());AuthoringSite.Location point=(AuthoringSite.Location)authoring.service().put(new AuthoringSite.Location("encounter_wide_runtime_point","minecraft:overworld",1,32.5,64,32.5,0,0),false);EventEditorForm.EncounterAction action=new EventEditorForm.EncounterAction(WIDE_GROUP,WIDE.label(),"POINT",point.id(),point.id(),point.revision(),true,"");EventEditorForm form=new EventEditorForm(WIDE_EVENT,0,"Wide runtime trial","Two-ravager placement proof","","",ObservationCatalog.Audience.PARTICIPANTS.name(),"",0,PlayState.Mode.MANUAL.name(),"",0,0,List.of(),List.of(),true,Map.of(),EventSource.empty(),List.of(action));EventRuntime.save(player,form,null,true);PlayRuntime.setParticipant(source,player.getUuid(),true);authoring.disable(player.getUuid());campaign.resume(actor(player));player.teleport(source.getWorld(),28.5,64,28.5,Set.of(),0,0,false);EventRuntime.run(player,WIDE_EVENT,EventRuntime.review(server,WIDE_EVENT));return PlayRuntime.service(server).state().executions().get(WIDE_EVENT);}

    private static EventExecution startEvent(ServerCommandSource source,ServerPlayerEntity player)throws Exception{
        MinecraftServer server=source.getServer();var campaign=ChronicleRuntime.service(server);
        require(campaign.state().phase()==SessionPhase.INACTIVE,"basic encounter trial requires a fresh campaign");
        server.setDifficulty(Difficulty.NORMAL,true);source.getWorld().setTimeOfDay(18000);
        for(int x=8;x<=16;x++)for(int z=8;z<=16;z++){source.getWorld().setBlockState(new BlockPos(x,63,z),Blocks.STONE.getDefaultState(),3);source.getWorld().setBlockState(new BlockPos(x,64,z),Blocks.AIR.getDefaultState(),3);source.getWorld().setBlockState(new BlockPos(x,65,z),Blocks.AIR.getDefaultState(),3);}
        campaign.start(actor(player),1);campaign.pause(actor(player));
        AuthoringRuntime authoring=AuthoringRuntime.get(server);authoring.enable(player.getUuid());
        AuthoringSite.Location point=(AuthoringSite.Location)authoring.service().put(new AuthoringSite.Location(POINT,"minecraft:overworld",1,12.5,64,12.5,0,0),false);
        EventEditorForm.EncounterAction action=new EventEditorForm.EncounterAction(GROUP,DEFAULT.label(),"POINT",point.id(),point.id(),point.revision(),true,"");
        EventEditorForm form=new EventEditorForm(EVENT,0,"Encounter trial","Native at-most-once proof","","",ObservationCatalog.Audience.PARTICIPANTS.name(),"",0,
                PlayState.Mode.MANUAL.name(),"",0,0,List.of(),List.of(),true,Map.of(),EventSource.empty(),List.of(action));
        EventRuntime.save(player,form,null,true);PlayRuntime.setParticipant(source,player.getUuid(),true);authoring.disable(player.getUuid());
        campaign.resume(actor(player));player.teleport(source.getWorld(),10.5,64,10.5,Set.of(),0,0,false);
        EventRuntime.run(player,EVENT,EventRuntime.review(server,EVENT));
        return PlayRuntime.service(server).state().executions().get(EVENT);
    }

    private static int seedUncertain(ServerCommandSource source){try{fixture(source);require(source.hasPermissionLevel(2),"operator permission required");require(job==null&&reviewPlayer==null,"encounter trial is already running");ServerPlayerEntity player=source.getPlayerOrThrow();EventExecution execution=startEvent(source,player);Encounters.testingSeedClaimedMissing(source.getServer(),execution);reviewPlayer=player.getUuid();source.sendFeedback(()->Text.literal("Encounter trial PASS uncertain_seed"),false);return 1;}catch(Exception failure){source.sendError(Text.literal("Encounter trial FAIL uncertain: "+message(failure)));return 0;}}

    private static void restart(ServerCommandSource source,ServerPlayerEntity player)throws Exception{
        MinecraftServer server=source.getServer();server.setDifficulty(Difficulty.NORMAL,true);source.getWorld().setTimeOfDay(18000);require(ChronicleRuntime.service(server).state().phase()==SessionPhase.PAUSED,"restart proof requires the maintained paused world");
        EventExecution first=PlayRuntime.service(server).state().executions().get(EVENT);require(first!=null&&first.phase()==EventExecution.Phase.COMPLETE,"first Event completion evidence is missing");
        UUID child=child(first.operationId(),0);for(int i=0;i<3;i++){var entity=source.getWorld().getEntity(entity(child,i));if(entity!=null)entity.discard();}
        server.saveAll(true,true,true);
        require(Encounters.progress(server,first,()->true)==Encounters.Progress.COMPLETE,"archived encounter retry was not terminal");
        for(int i=0;i<3;i++)require(source.getWorld().getEntity(entity(child,i))==null,"completed encounter retry respawned a vanished entity");
        AuthoringRuntime.get(server).enable(player.getUuid());PlayRuntime.reset(source,EVENT);AuthoringRuntime.get(server).disable(player.getUuid());
        ChronicleRuntime.service(server).resume(actor(player));EventRuntime.run(player,EVENT,EventRuntime.review(server,EVENT));job=new Job(player.getUuid(),true,first.operationId());
    }

    private static void tick(MinecraftServer server){
        if(reviewPlayer!=null){ServerPlayerEntity player=server.getPlayerManager().getPlayer(reviewPlayer);if(player==null)return;try{EventExecution execution=PlayRuntime.service(server).state().executions().get(EVENT);if(execution==null||execution.phase()!=EventExecution.Phase.COMPLETE)return;UUID child=child(execution.operationId(),0);for(int i=0;i<3;i++)require(player.getEntityWorld().getEntity(entity(child,i))==null,"accepted partial encounter unexpectedly spawned an entity");ChronicleRuntime.service(server).pause(actor(player));var result=EventHistoryMaintenance.maintain(server);require(result.encounterOperationsRetired()==1,"reviewed encounter operation was not archived");reviewPlayer=null;player.sendMessage(Text.literal("Encounter trial PASS uncertain_review"),false);}catch(Exception failure){reviewPlayer=null;player.sendMessage(Text.literal("Encounter trial FAIL uncertain_review: "+message(failure)),false);}return;}
        if(footprint!=null){ServerPlayerEntity player=server.getPlayerManager().getPlayer(footprint.player);if(player==null)return;try{var operation=Encounters.testingOperation(server,footprint.eventOperation,0).orElse(null);if(operation==null)return;require(operation.entries().stream().allMatch(entry->entry.stage()==EncounterSpawnEntry.Stage.PLANNED),"wide-footprint refusal persisted a claim");for(var entry:operation.entries())require(player.getEntityWorld().getEntity(entry.entityId())==null,"wide-footprint refusal spawned an entity");require(player.getEntityWorld().getChunkManager().isChunkLoaded(footprint.loadedChunkX,footprint.chunkZ),"fixture origin chunk unexpectedly unloaded");require(!player.getEntityWorld().getChunkManager().isChunkLoaded(footprint.loadedChunkX+1,footprint.chunkZ),"footprint preflight forced the adjacent chunk");footprint=null;player.sendMessage(Text.literal("Encounter trial PASS footprint"),false);}catch(Exception failure){footprint=null;player.sendMessage(Text.literal("Encounter trial FAIL footprint: "+message(failure)),false);}return;}
        if(wideJob!=null){ServerPlayerEntity player=server.getPlayerManager().getPlayer(wideJob.player);if(player==null)return;try{EventExecution execution=PlayRuntime.service(server).state().executions().get(WIDE_EVENT);if(execution==null||execution.phase()!=EventExecution.Phase.COMPLETE)return;UUID child=child(wideJob.eventOperation,0);for(int i=0;i<2;i++){var entity=player.getEntityWorld().getEntity(entity(child,i));require(entity!=null,"wide planned entity is missing");require(net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType()).toString().equals("minecraft:ravager"),"wide planned UUID has the wrong entity type");}ChronicleRuntime.service(server).pause(actor(player));var result=EventHistoryMaintenance.maintain(server);require(result.encounterOperationsRetired()==1&&Encounters.liveOperationCount(server)==0,"wide completed operation was not archived");wideJob=null;player.sendMessage(Text.literal("Encounter trial PASS wide_runtime"),false);}catch(Exception failure){wideJob=null;player.sendMessage(Text.literal("Encounter trial FAIL wide_runtime: "+message(failure)),false);}return;}
        if(job==null)return;ServerPlayerEntity player=server.getPlayerManager().getPlayer(job.player);if(player==null)return;
        try{
            if(job.preflight){var operation=Encounters.testingOperation(server,job.firstOperation,0).orElse(null);if(operation==null)return;require(operation.entries().stream().allMatch(entry->entry.stage()==EncounterSpawnEntry.Stage.PLANNED),"preflight refusal persisted a claim");for(var entry:operation.entries())require(player.getEntityWorld().getEntity(entry.entityId())==null,"preflight refusal spawned an entity");job=null;player.sendMessage(Text.literal("Encounter trial PASS preflight"),false);return;}
            EventExecution execution=PlayRuntime.service(server).state().executions().get(EVENT);if(execution==null||execution.phase()!=EventExecution.Phase.COMPLETE)return;
            UUID child=child(execution.operationId(),0);boolean villager=false;for(int i=0;i<3;i++){var entity=player.getEntityWorld().getEntity(entity(child,i));require(entity!=null,"planned encounter entity is missing before completion proof");villager|=net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType()).toString().equals("minecraft:villager");}require(villager,"curated friendly MobEntity was not created through the encounter adapter");
            if(job.restart)require(!execution.operationId().equals(job.firstOperation),"rearmed Event reused its original operation UUID");
            ChronicleRuntime.service(server).pause(actor(player));var result=EventHistoryMaintenance.maintain(server);
            require(result.encounterOperationsRetired()==1,"maintenance did not retire exactly one completed encounter operation");
            require(Encounters.liveOperationCount(server)==0,"completed encounter operation still consumes live capacity");
            Path proof=fixtureRoot(server).resolve(".encounter-actions-trial.json");
            Files.writeString(proof,"{\"phase\":\""+(job.restart?"restart":"basic")+"\",\"operation\":\""+execution.operationId()+"\",\"retired\":"+result.encounterOperationsRetired()+"}\n",StandardCharsets.UTF_8);
            String phase=job.restart?"restart":"basic";job=null;player.sendMessage(Text.literal("Encounter trial PASS "+phase),false);
        }catch(Exception failure){String phase=job.restart?"restart":"basic";job=null;player.sendMessage(Text.literal("Encounter trial FAIL "+phase+": "+message(failure)),false);}
    }

    private static UUID child(UUID operation,int action){return UUID.nameUUIDFromBytes(("chronicle:encounter:"+operation+":"+action).getBytes(StandardCharsets.UTF_8));}
    private static UUID entity(UUID child,int index){return UUID.nameUUIDFromBytes(("chronicle:encounter-entity:"+child+":"+index).getBytes(StandardCharsets.UTF_8));}
    private static Actor actor(ServerPlayerEntity player){return new Actor("encounter-trial",player.getUuidAsString());}
    private static Path fixtureRoot(MinecraftServer server){Path world=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();return Objects.requireNonNull(world.getParent());}
    private static void fixture(ServerCommandSource source){MinecraftServer server=source.getServer();Path root=fixtureRoot(server);require(root.getParent()!=null&&root.getParent().getFileName().toString().equals("test-runs")&&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")&&Files.isRegularFile(root.resolve(".encounter-actions-fixture"))&&server.isDedicated()&&!server.isOnlineMode()&&server.getServerPort()==25575&&"127.0.0.1".equals(server.getServerIp()),"unsafe encounter fixture");}
    private static String message(Throwable failure){return failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private record Job(UUID player,boolean restart,UUID firstOperation,boolean preflight){Job(UUID player,boolean restart,UUID firstOperation){this(player,restart,firstOperation,false);}}
    private record FootprintJob(UUID player,UUID eventOperation,int loadedChunkX,int chunkZ){}
    private record WideJob(UUID player,UUID eventOperation){}
}
