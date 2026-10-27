package io.github.chronicle.core.server.testing;

import io.github.chronicle.core.areas.*;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.campaign.*;
import io.github.chronicle.core.content.ChestTarget;
import io.github.chronicle.core.menu.EventEditorForm;
import io.github.chronicle.core.play.*;
import io.github.chronicle.core.server.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.nio.file.Files;
import java.util.*;

import static net.minecraft.server.command.CommandManager.literal;

/** Explicitly opt-in CF22 native fixture. Every mutation is refused outside a disposable loopback world. */
public final class EventActionsTrial {
    public static final String REGION_A="event_region_a",REGION_B="event_region_b",REGION_UNLOADED="event_region_unloaded";
    public static final String WORLD_EVENT="chronicle_core:trial_world",CRASH_EVENT="chronicle_core:trial_crash",MEDIA_EVENT="chronicle_core:trial_media",AUTO_EVENT="chronicle_core:trial_auto",UNCERTAIN_EVENT="chronicle_core:trial_uncertain";
    public static final String FLAG="quiet_below:m8_gate",WORLD_MESSAGE="The paired regions answer together.",CRASH_MESSAGE="The recovered regions answer once.",MEDIA_MESSAGE="A private bell crosses the island.",AUTO_MESSAGE="The delayed event answers through the same gate.",UNCERTAIN_MESSAGE="This message must never be sent automatically.";
    private static final BlockPos HOME=new BlockPos(1,64,1),A_CHEST=new BlockPos(4,64,4),A_SIGN=new BlockPos(5,64,4),A_COLOR=new BlockPos(6,64,4);
    private static final BlockPos B_CHEST=new BlockPos(33,64,4),B_COLOR=new BlockPos(34,64,4),FAR=new BlockPos(16000,64,16000);
    private static final BlockPosition A_MIN=position(A_CHEST),A_MAX=new BlockPosition(7,64,5),B_MIN=new BlockPosition(32,64,0),B_MAX=new BlockPosition(63,71,31);
    private static final String BOOK="test:event_managed";
    private static FixtureJob job;
    private EventActionsTrial(){}

    public static void register(){
        if(!"true".equals(System.getenv("CHRONICLE_EVENTS_TRIAL")))return;
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("chronicle_events_trial").then(literal("prepare_ui").executes(c->run(c.getSource(),"prepare_ui"))).then(literal("configure").executes(c->run(c.getSource(),"configure"))).then(literal("phase_guard").executes(c->run(c.getSource(),"phase_guard"))).then(literal("activate").executes(c->run(c.getSource(),"activate"))).then(literal("flag_guard").executes(c->run(c.getSource(),"flag_guard"))).then(literal("enable_flag").executes(c->run(c.getSource(),"enable_flag"))).then(literal("run_media").executes(c->run(c.getSource(),"run_media"))).then(literal("target_world").executes(c->run(c.getSource(),"target_world"))).then(literal("invalid_second").executes(c->run(c.getSource(),"invalid_second"))).then(literal("missing_target").executes(c->run(c.getSource(),"missing_target"))).then(literal("player_refusal").executes(c->run(c.getSource(),"player_refusal"))).then(literal("hold_refusal").executes(c->run(c.getSource(),"hold_refusal"))).then(literal("unloaded_refusal").executes(c->run(c.getSource(),"unloaded_refusal"))).then(literal("run_world").executes(c->run(c.getSource(),"run_world"))).then(literal("verify_world").executes(c->run(c.getSource(),"verify_world"))).then(literal("verify_auto").executes(c->run(c.getSource(),"verify_auto"))).then(literal("run_crash").executes(c->run(c.getSource(),"run_crash"))).then(literal("verify_recovery").executes(c->run(c.getSource(),"verify_recovery"))).then(literal("verify_final").executes(c->run(c.getSource(),"verify_final"))).then(literal("seed_media_claimed_and_halt").executes(c->run(c.getSource(),"seed_media_claimed_and_halt"))).then(literal("verify_uncertain").executes(c->run(c.getSource(),"verify_uncertain"))).then(literal("nonop_guard").executes(c->run(c.getSource(),"nonop_guard"))).then(literal("prepare_max").executes(c->run(c.getSource(),"prepare_max"))).then(literal("max_preflight").executes(c->run(c.getSource(),"max_preflight")))));
        ServerTickEvents.END_SERVER_TICK.register(EventActionsTrial::tick);
    }

    private static int run(ServerCommandSource source,String verb){
        try{
            fixture(source);ServerPlayerEntity player=source.getPlayerOrThrow();
            switch(verb){
                case "prepare_ui"->{requireOp(source);prepareUi(source,player);return 1;}
                case "prepare_max"->{requireOp(source);prepareMax(source,player);return 1;}
                case "configure"->{requireOp(source);configure(source,player);}
                case "phase_guard"->{requireOp(source);expectRefusal(()->EventRuntime.review(source.getServer(),WORLD_EVENT),"active");}
                case "activate"->{requireOp(source);activate(source,player);}
                case "flag_guard"->{requireOp(source);expectRefusal(()->EventRuntime.review(source.getServer(),WORLD_EVENT),"flag");}
                case "enable_flag"->{requireOp(source);ChronicleRuntime.service(source.getServer()).setFlag(actor(player),FLAG,true);}
                case "run_media"->{requireOp(source);runEvent(player,MEDIA_EVENT);}
                case "target_world"->{requireOp(source);targetWorld(source);}
                case "invalid_second"->{requireOp(source);invalidSecond(source,false);}
                case "missing_target"->{requireOp(source);invalidSecond(source,true);}
                case "player_refusal"->{requireOp(source);playerRefusal(source,player);}
                case "hold_refusal"->{requireOp(source);holdRefusal(source);}
                case "unloaded_refusal"->{requireOp(source);unloadedRefusal(source);}
                case "run_world"->{requireOp(source);runEvent(player,WORLD_EVENT);}
                case "verify_world"->{requireOp(source);verifyWorld(source);}
                case "verify_auto"->{requireOp(source);complete(source,AUTO_EVENT);}
                case "run_crash"->{requireOp(source);runEvent(player,CRASH_EVENT);}
                case "verify_recovery"->{requireOp(source);verifyRecovery(source);}
                case "verify_final"->{requireOp(source);verifyRecovery(source);}
                case "seed_media_claimed_and_halt"->{requireOp(source);seedClaimedAndHalt(source,player);return 1;}
                case "verify_uncertain"->{requireOp(source);verifyUncertain(source,player);}
                case "nonop_guard"->{require(!source.hasPermissionLevel(2),"non-operator guard must use an ordinary player");expectRefusal(()->EventRuntime.previewMedia(player,MEDIA_EVENT),"operator");}
                case "max_preflight"->{requireOp(source);maxPreflight(source);return 1;}
                default->throw new IllegalArgumentException("unknown Events trial action");
            }
            pass(source,verb);return 1;
        }catch(Exception failure){source.sendError(Text.literal("Events trial FAIL "+verb+": "+failure.getMessage()));return 0;}
    }

    private static void prepareUi(ServerCommandSource source,ServerPlayerEntity player)throws Exception{
        require(job==null,"another Events fixture job is running");ensurePausedFixture(source,player);
        if(completeArea(source.getServer(),REGION_A,true)&&completeArea(source.getServer(),REGION_B,true)&&completeArea(source.getServer(),REGION_UNLOADED,false)){pass(source,"prepare_ui");return;}
        require(AreaChanges.catalog(source.getServer()).areas().isEmpty(),"prepare_ui requires a fresh Area catalog");player.changeGameMode(GameMode.CREATIVE);home(player,source.getWorld());startState(source.getWorld());beginArea(source,player,REGION_A,"Event Region A",A_MIN,A_MAX);job=new FixtureJob(player.getUuid(),JobMode.UI,Stage.WAIT_A_START);
    }

    private static void prepareMax(ServerCommandSource source,ServerPlayerEntity player)throws Exception{
        require(job==null,"another Events fixture job is running");ensurePausedFixture(source,player);require(AreaChanges.catalog(source.getServer()).areas().isEmpty(),"prepare_max requires a fresh Area catalog");player.changeGameMode(GameMode.SPECTATOR);player.teleport(source.getWorld(),128.5,200,32.5,Set.of(),0,0,false);job=new FixtureJob(player.getUuid(),JobMode.MAX,Stage.WAIT_MAX_LOAD);
    }

    private static void tick(MinecraftServer server){
        if(job==null)return;ServerPlayerEntity player=server.getPlayerManager().getPlayer(job.player);if(player==null){job=null;return;}ServerCommandSource source=player.getCommandSource();
        try{
            if(AreaChanges.busy(server))return;
            switch(job.stage){
                case WAIT_A_START->{if(!completeArea(server,REGION_A,false))return;endA(source.getWorld());AreaChange area=area(server,REGION_A);AreaChanges.captureCurrent(source,REGION_A,AreaState.END,area.revision());job.stage=Stage.WAIT_A_END;}
                case WAIT_A_END->{if(!completeArea(server,REGION_A,true))return;startB(source.getWorld());beginArea(source,player,REGION_B,"Event Region B",B_MIN,B_MAX);job.stage=Stage.WAIT_B_START;}
                case WAIT_B_START->{if(!completeArea(server,REGION_B,false))return;endB(source.getWorld());AreaChange area=area(server,REGION_B);AreaChanges.captureCurrent(source,REGION_B,AreaState.END,area.revision());job.stage=Stage.WAIT_B_END;}
                case WAIT_B_END->{if(!completeArea(server,REGION_B,true))return;player.teleport(source.getWorld(),FAR.getX()+.5,96,FAR.getZ()+.5,Set.of(),0,0,false);job.stage=Stage.WAIT_FAR_LOAD;}
                case WAIT_FAR_LOAD->{if(!source.getWorld().getChunkManager().isChunkLoaded(FAR.getX()>>4,FAR.getZ()>>4))return;put(source.getWorld(),FAR,Blocks.STONE);beginArea(source,player,REGION_UNLOADED,"Event Unloaded Region",position(FAR),position(FAR));job.stage=Stage.WAIT_FAR_START;}
                case WAIT_FAR_START->{if(!completeArea(server,REGION_UNLOADED,false))return;home(player,source.getWorld());pass(source,"prepare_ui");job=null;}
                case WAIT_MAX_LOAD->{if(!loaded(source.getWorld(),0,255,0,63))return;beginArea(source,player,"event_max_a","Event Max A",new BlockPosition(0,64,0),new BlockPosition(127,79,63));job.stage=Stage.WAIT_MAX_A;}
                case WAIT_MAX_A->{if(!completeArea(server,"event_max_a",false))return;beginArea(source,player,"event_max_b","Event Max B",new BlockPosition(128,64,0),new BlockPosition(255,79,63));job.stage=Stage.WAIT_MAX_B;}
                case WAIT_MAX_B->{if(!completeArea(server,"event_max_b",false))return;pass(source,"prepare_max");job=null;}
            }
        }catch(Exception failure){source.sendError(Text.literal("Events trial FAIL "+(job.mode==JobMode.MAX?"prepare_max":"prepare_ui")+": "+failure.getMessage()));job=null;}
    }

    private static void configure(ServerCommandSource source,ServerPlayerEntity player)throws Exception{
        require(completeArea(source.getServer(),REGION_A,true)&&completeArea(source.getServer(),REGION_B,true),"prepare_ui has not completed");
        putBinding(source,A_CHEST,"test:event_a");putBinding(source,B_CHEST,"test:event_b");
        saveEvent(player,WORLD_EVENT,"Paired Region change",WORLD_MESSAGE,"minecraft:block.note_block.bell",PlayState.Mode.MANUAL,0,FLAG,List.of(change(source,REGION_A,AreaState.END),change(source,REGION_B,AreaState.END)));
        saveEvent(player,CRASH_EVENT,"Crash recovery",CRASH_MESSAGE,"minecraft:block.note_block.bell",PlayState.Mode.MANUAL,0,FLAG,List.of(change(source,REGION_A,AreaState.START),change(source,REGION_B,AreaState.START)));
        saveEvent(player,MEDIA_EVENT,"Media only",MEDIA_MESSAGE,"minecraft:block.note_block.bell",PlayState.Mode.MANUAL,0,FLAG,List.of());
        saveEvent(player,AUTO_EVENT,"Automatic gate",AUTO_MESSAGE,"minecraft:block.note_block.bell",PlayState.Mode.TIMER,10,FLAG,List.of());
        saveEvent(player,UNCERTAIN_EVENT,"Uncertain media",UNCERTAIN_MESSAGE,"minecraft:block.note_block.bell",PlayState.Mode.MANUAL,0,FLAG,List.of());
    }

    private static void activate(ServerCommandSource source,ServerPlayerEntity player)throws Exception{
        var campaign=ChronicleRuntime.service(source.getServer());require(campaign.state().phase()==SessionPhase.PAUSED,"fixture session is not paused");PlayRuntime.setParticipant(source,player.getUuid(),true);AuthoringRuntime.get(source.getServer()).disable(player.getUuid());campaign.resume(actor(player));home(player,source.getWorld());
    }

    private static void targetWorld(ServerCommandSource source)throws Exception{
        ServerWorld world=source.getWorld();put(world,A_CHEST,Blocks.CHEST);chest(world,A_CHEST).setStack(0,new ItemStack(Items.NETHERITE_INGOT));sign(world,A_SIGN,"A TARGET");put(world,A_COLOR,Blocks.BLACK_WOOL);
        put(world,B_CHEST,Blocks.CHEST);ChestBlockEntity lore=chest(world,B_CHEST);lore.clear();lore.setStack(0,managedBook(source));lore.markDirty();put(world,B_COLOR,Blocks.STONE);
    }

    private static void invalidSecond(ServerCommandSource source,boolean missing)throws Exception{
        var changes=EventRuntime.configuration(source.getServer(),WORLD_EVENT).definition().changes();EventRegionChange second=changes.get(1);second=new EventRegionChange(second.region(),missing?"missing_event_area":second.areaId(),missing?second.areaRevision():second.areaRevision()+1,second.state(),second.snapshot());
        UUID id=UUID.randomUUID();boolean[] callback={false};IllegalStateException refusal=null;
        try{EventWorldBatches.preflight(source.getServer(),id,source.getPlayerOrThrow().getUuid(),List.of(changes.getFirst(),second),()->true,p->callback[0]=true,m->callback[0]=true);}catch(IllegalStateException expected){refusal=expected;}
        if(refusal==null){EventWorldBatches.cancelUncommitted(source.getServer(),id);throw new IllegalStateException("invalid second Event target was accepted");}
        require(!callback[0],"invalid target entered asynchronous preparation");require(EventWorldBatches.status(source.getServer(),id).isEmpty(),"invalid target published batch authority");require(source.getWorld().getBlockState(A_COLOR).isOf(Blocks.BLACK_WOOL),"invalid second target changed the first Region");
    }

    private static void playerRefusal(ServerCommandSource source,ServerPlayerEntity player)throws Exception{player.teleport(source.getWorld(),A_COLOR.getX()+.5,64,A_COLOR.getZ()+.5,Set.of(),0,0,false);expectPreflightRefusal(source,EventRuntime.configuration(source.getServer(),WORLD_EVENT).definition().changes(),"Move non-spectator");home(player,source.getWorld());}
    private static void holdRefusal(ServerCommandSource source)throws Exception{RuntimeHolds.hold(source.getServer(),"event-trial-foreign","Foreign fixture hold");try{expectPreflightRefusal(source,EventRuntime.configuration(source.getServer(),WORLD_EVENT).definition().changes(),"holds world");}finally{RuntimeHolds.release(source.getServer(),"event-trial-foreign");}}
    private static void unloadedRefusal(ServerCommandSource source)throws Exception{AreaChange area=area(source.getServer(),REGION_UNLOADED);int x=FAR.getX()>>4,z=FAR.getZ()>>4;require(!source.getWorld().getChunkManager().isChunkLoaded(x,z),"far Event fixture chunk has not unloaded yet");EventRegionChange change=new EventRegionChange(new EventRegionReference(REGION_UNLOADED,area.bounds().regionRevision()),area.id(),area.revision(),AreaState.START,area.requireSnapshot(AreaState.START));expectPreflightRefusal(source,List.of(change),"not all loaded");require(!source.getWorld().getChunkManager().isChunkLoaded(x,z),"unloaded Event refusal forced its chunk");}

    private static void expectPreflightRefusal(ServerCommandSource source,List<EventRegionChange> changes,String fragment)throws Exception{UUID id=UUID.randomUUID();IllegalStateException refusal=null;try{EventWorldBatches.preflight(source.getServer(),id,source.getPlayerOrThrow().getUuid(),changes,()->true,p->{throw new IllegalStateException("refused preflight completed");},m->{throw new IllegalStateException(m);});}catch(IllegalStateException expected){refusal=expected;}if(refusal==null){EventWorldBatches.cancelUncommitted(source.getServer(),id);throw new IllegalStateException("Event preflight unexpectedly started");}require(refusal.getMessage().toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT)),"unexpected Event preflight refusal: "+refusal.getMessage());require(EventWorldBatches.status(source.getServer(),id).isEmpty(),"refused Event preflight published authority");}

    private static void verifyWorld(ServerCommandSource source){
        complete(source,WORLD_EVENT);ServerWorld world=source.getWorld();require(chest(world,A_CHEST).getStack(0).isOf(Items.EMERALD)&&chest(world,A_CHEST).getStack(0).getCount()==4,"Event did not restore exact chest NBT");require(signLine(world,A_SIGN).equals("A END")&&world.getBlockState(A_COLOR).isOf(Blocks.BLUE_WOOL),"Event did not restore sign/block state");require(world.getBlockState(B_CHEST).isOf(Blocks.STONE),"Event did not discard the replaced chest");var altar=Registries.BLOCK.get(Identifier.of("quiet_below:ancient_altar"));require(world.getBlockState(B_COLOR).isOf(altar),"Event did not place the managed block");require(binding(source,A_CHEST)&&!binding(source,B_CHEST),"aggregate binding retirement was not exact");require(ManagedBlocks.snapshot(source.getServer()).instances().stream().anyMatch(value->value.position().equals(position(B_COLOR))),"aggregate managed ledger did not commit");var delivery=ChestAuthoring.service(source.getServer()).state().bookDelivery().deliveries().get(BOOK);require(delivery!=null&&delivery.pendingOperationIds().isEmpty(),"aggregate lore removal claim remains pending");}
    private static void verifyRecovery(ServerCommandSource source){complete(source,CRASH_EVENT);ServerWorld world=source.getWorld();require(chest(world,A_CHEST).getStack(0).isOf(Items.IRON_INGOT)&&signLine(world,A_SIGN).equals("A START")&&world.getBlockState(A_COLOR).isOf(Blocks.RED_WOOL),"first Region did not recover exactly to START");require(chest(world,B_CHEST).getStack(0).isOf(Items.DIAMOND)&&world.getBlockState(B_COLOR).isOf(Blocks.STONE),"second Region did not recover exactly to START");}
    private static void complete(ServerCommandSource source,String id){EventExecution execution=PlayRuntime.service(source.getServer()).state().executions().get(id);require(execution!=null&&execution.phase()==EventExecution.Phase.COMPLETE,"Event is not complete: "+id);require(PlayRuntime.service(source.getServer()).state().deliveries().containsKey(id),"Event delivery receipt is missing: "+id);}

    private static void seedClaimedAndHalt(ServerCommandSource source,ServerPlayerEntity player)throws Exception{
        EventConfiguration c=EventRuntime.configuration(source.getServer(),UNCERTAIN_EVENT);UUID operation=UUID.randomUUID();EventExecution execution=new EventExecution(UNCERTAIN_EVENT,operation,c,EventExecution.Phase.EVENT_OUTCOME,PlayState.DeliverySource.MANUAL,player.getUuid(),ChronicleRuntime.service(source.getServer()).state().sessionNumber(),PlayRuntime.elapsedTicks(source.getServer()),List.of(player.getUuid()),"","");PlayRuntime.service(source.getServer()).beginExecution(execution);PlayRuntime.service(source.getServer()).advanceExecution(UNCERTAIN_EVENT,operation,EventExecution.Phase.MEDIA_CLAIMED,"Native fixture durable media claim before abrupt termination.");Runtime.getRuntime().halt(97);
    }
    private static void verifyUncertain(ServerCommandSource source,ServerPlayerEntity player)throws Exception{EventExecution execution=PlayRuntime.service(source.getServer()).state().executions().get(UNCERTAIN_EVENT);require(execution!=null&&execution.phase()==EventExecution.Phase.MEDIA_UNCERTAIN,"restart did not preserve explicit media uncertainty");EventRuntime.acknowledge(player,UNCERTAIN_EVENT);complete(source,UNCERTAIN_EVENT);}

    private static void maxPreflight(ServerCommandSource source)throws Exception{
        AreaChange a=area(source.getServer(),"event_max_a"),b=area(source.getServer(),"event_max_b");List<EventRegionChange> changes=List.of(change(a,AreaState.START),change(b,AreaState.START));require(a.bounds().cells()+b.bounds().cells()==262_144,"maximum fixture cell count is not exact");require(uniqueChunks(a.bounds(),b.bounds())==64,"maximum fixture chunk count is not exact");UUID id=UUID.randomUUID();EventWorldBatches.preflight(source.getServer(),id,source.getPlayerOrThrow().getUuid(),changes,()->true,prepared->{EventWorldBatches.cancelUncommitted(source.getServer(),prepared.id());pass(source,"max_preflight");},reason->source.sendError(Text.literal("Events trial FAIL max_preflight: "+reason)));
    }

    private static void saveEvent(ServerPlayerEntity player,String id,String name,String message,String sound,PlayState.Mode mode,long seconds,String flag,List<EventEditorForm.Change> changes)throws Exception{
        if(EventRuntime.stored(player.getEntityWorld().getServer(),id)!=null)return;EventEditorForm form=new EventEditorForm(id,0,name,"Native Events fixture",message,sound,ObservationCatalog.Audience.PARTICIPANTS.name(),"",0,mode.name(),"",0,seconds,flag,changes,true);EventRuntime.save(player,form,null,true);
    }
    private static EventEditorForm.Change change(ServerCommandSource source,String id,AreaState state){AreaChange area=area(source.getServer(),id);SnapshotRef ref=area.requireSnapshot(state);return new EventEditorForm.Change(area.bounds().regionId(),area.name(),state.name(),area.revision(),ref.sha256(),area.bounds().dimension(),true,"");}
    private static EventRegionChange change(AreaChange area,AreaState state){return new EventRegionChange(new EventRegionReference(area.bounds().regionId(),area.bounds().regionRevision()),area.id(),area.revision(),state,area.requireSnapshot(state));}
    private static void runEvent(ServerPlayerEntity player,String id)throws Exception{EventRuntime.Review review=EventRuntime.review(player.getEntityWorld().getServer(),id);EventRuntime.run(player,id,review);}

    private static void ensurePausedFixture(ServerCommandSource source,ServerPlayerEntity player)throws Exception{
        var campaign=ChronicleRuntime.service(source.getServer());if(campaign.state().phase()==SessionPhase.INACTIVE){campaign.start(actor(player),1);campaign.setFlag(actor(player),FLAG,false);campaign.pause(actor(player));}require(campaign.state().phase()==SessionPhase.PAUSED,"fixture campaign must be paused");if(!campaign.state().flags().containsKey(FLAG))throw new IllegalStateException("fixture condition flag is missing");AuthoringRuntime.get(source.getServer()).enable(player.getUuid());
    }
    private static void beginArea(ServerCommandSource source,ServerPlayerEntity player,String id,String name,BlockPosition min,BlockPosition max){AreaChanges.begin(source,id,name,AreaState.START);AreaChanges.setDraftCorner(player,1,min);AreaChanges.setDraftCorner(player,2,max);AreaChanges.saveDraft(source);}
    private static void startState(ServerWorld world){put(world,A_CHEST,Blocks.CHEST);ChestBlockEntity chest=chest(world,A_CHEST);chest.clear();chest.setStack(0,new ItemStack(Items.IRON_INGOT,2));chest.markDirty();sign(world,A_SIGN,"A START");put(world,A_COLOR,Blocks.RED_WOOL);}
    private static void endA(ServerWorld world){put(world,A_CHEST,Blocks.CHEST);ChestBlockEntity chest=chest(world,A_CHEST);chest.clear();chest.setStack(0,new ItemStack(Items.EMERALD,4));chest.markDirty();sign(world,A_SIGN,"A END");put(world,A_COLOR,Blocks.BLUE_WOOL);}
    private static void startB(ServerWorld world){put(world,B_CHEST,Blocks.CHEST);ChestBlockEntity chest=chest(world,B_CHEST);chest.clear();chest.setStack(0,new ItemStack(Items.DIAMOND,3));chest.markDirty();put(world,B_COLOR,Blocks.STONE);}
    private static void endB(ServerWorld world){put(world,B_CHEST,Blocks.STONE);var altar=Registries.BLOCK.get(Identifier.of("quiet_below:ancient_altar"));require(altar!=Blocks.AIR,"Quiet Below altar is unavailable");put(world,B_COLOR,altar);}
    private static boolean completeArea(MinecraftServer server,String id,boolean both){AreaChange area=AreaChanges.catalog(server).areas().get(id);return area!=null&&area.start().isPresent()&&(!both||area.end().isPresent());}
    private static AreaChange area(MinecraftServer server,String id){AreaChange area=AreaChanges.catalog(server).areas().get(id);if(area==null)throw new IllegalStateException("Area missing: "+id);return area;}
    private static void putBinding(ServerCommandSource source,BlockPos pos,String id)throws Exception{if(binding(source,pos))return;ChestAuthoring.service(source.getServer()).putTypedBinding("test:empty",new ChestTarget(id,1,"minecraft:overworld",pos.getX(),pos.getY(),pos.getZ(),"minecraft:chest"),false);}
    private static boolean binding(ServerCommandSource source,BlockPos pos){return ChestAuthoring.service(source.getServer()).state().bindings().values().stream().anyMatch(binding->binding.typed()&&binding.directTarget().x()==pos.getX()&&binding.directTarget().y()==pos.getY()&&binding.directTarget().z()==pos.getZ());}
    private static ItemStack managedBook(ServerCommandSource source)throws Exception{var service=ChestAuthoring.service(source.getServer());var old=service.state().bookDelivery().deliveries().get(BOOK);if(old==null)service.claimOperatorIssuance(BOOK);ItemStack book=new ItemStack(Items.WRITTEN_BOOK);book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT,new WrittenBookContentComponent(RawFilteredPair.of("Event evidence"),"Fixture",0,List.of(RawFilteredPair.of(Text.literal("Managed event evidence."))),true));NbtCompound marker=new NbtCompound();marker.putString("campaign",service.state().bookDelivery().campaignId());marker.putString("book",BOOK);marker.putString("token",service.deliveryToken(BOOK));NbtCompound custom=new NbtCompound();custom.put("chronicle_book",marker);book.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(custom));return book;}
    private static ChestBlockEntity chest(ServerWorld world,BlockPos pos){if(!(world.getBlockEntity(pos) instanceof ChestBlockEntity chest))throw new IllegalStateException("chest missing at "+pos.toShortString());return chest;}
    private static void sign(ServerWorld world,BlockPos pos,String value){put(world,pos,Blocks.OAK_SIGN);SignBlockEntity sign=(SignBlockEntity)world.getBlockEntity(pos);require(sign!=null,"sign missing");sign.setText(sign.getFrontText().withMessage(0,Text.literal(value)),true);sign.markDirty();}
    private static String signLine(ServerWorld world,BlockPos pos){SignBlockEntity sign=(SignBlockEntity)world.getBlockEntity(pos);require(sign!=null,"sign missing");return sign.getFrontText().getMessage(0,false).getString();}
    private static void put(ServerWorld world,BlockPos pos,net.minecraft.block.Block block){world.setBlockState(pos,block.getDefaultState(),net.minecraft.block.Block.FORCE_STATE_AND_SKIP_CALLBACKS_AND_DROPS|net.minecraft.block.Block.NOTIFY_LISTENERS);}
    private static void home(ServerPlayerEntity player,ServerWorld world){player.teleport(world,HOME.getX()+.5,64,HOME.getZ()+.5,Set.of(),0,0,false);player.setVelocity(0,0,0);player.onLanding();}
    private static boolean loaded(ServerWorld world,int minX,int maxX,int minZ,int maxZ){for(int x=minX>>4;x<=maxX>>4;x++)for(int z=minZ>>4;z<=maxZ>>4;z++)if(!world.getChunkManager().isChunkLoaded(x,z))return false;return true;}
    private static int uniqueChunks(AreaBounds...bounds){Set<Long> result=new HashSet<>();for(AreaBounds b:bounds)for(int x=Math.floorDiv(b.min().x(),16);x<=Math.floorDiv(b.max().x(),16);x++)for(int z=Math.floorDiv(b.min().z(),16);z<=Math.floorDiv(b.max().z(),16);z++)result.add(((long)x<<32)^(z&0xffffffffL));return result.size();}
    private static BlockPosition position(BlockPos pos){return new BlockPosition(pos.getX(),pos.getY(),pos.getZ());}
    private static Actor actor(ServerPlayerEntity player){return new Actor("events-trial",player.getUuidAsString());}
    private static void requireOp(ServerCommandSource source){require(source.hasPermissionLevel(2),"operator permission required");}
    private static void expectRefusal(Throwing action,String fragment){try{action.run();throw new IllegalStateException("operation was accepted");}catch(Exception expected){require(expected.getMessage()!=null&&expected.getMessage().toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT)),"unexpected refusal: "+expected.getMessage());}}
    private static void pass(ServerCommandSource source,String verb){source.sendFeedback(()->Text.literal("Events trial PASS "+verb),false);}
    private static void fixture(ServerCommandSource source){var server=source.getServer();var root=server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();require(root!=null&&root.getParent()!=null&&root.getParent().getFileName().toString().equals("test-runs")&&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")&&Files.isRegularFile(root.resolve(".event-actions-fixture"))&&server.isDedicated()&&!server.isOnlineMode()&&server.getServerPort()==25575&&"127.0.0.1".equals(server.getServerIp())&&source.getWorld().getRegistryKey()==net.minecraft.world.World.OVERWORLD,"unsafe Events fixture");}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    @FunctionalInterface private interface Throwing{void run()throws Exception;}
    private enum JobMode{UI,MAX}private enum Stage{WAIT_A_START,WAIT_A_END,WAIT_B_START,WAIT_B_END,WAIT_FAR_LOAD,WAIT_FAR_START,WAIT_MAX_LOAD,WAIT_MAX_A,WAIT_MAX_B}
    private static final class FixtureJob{final UUID player;final JobMode mode;Stage stage;FixtureJob(UUID player,JobMode mode,Stage stage){this.player=player;this.mode=mode;this.stage=stage;}}
}
