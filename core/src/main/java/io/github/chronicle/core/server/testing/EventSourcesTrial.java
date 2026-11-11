package io.github.chronicle.core.server.testing;

import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.campaign.*;
import io.github.chronicle.core.menu.EventEditorForm;
import io.github.chronicle.core.play.*;
import io.github.chronicle.core.progression.SourceLedger;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.departures.*;
import io.github.chronicle.core.server.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.item.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

/** Real native player/provider fixture. Never registered or permitted in an ordinary server. */
public final class EventSourcesTrial {
    private static final String BOOK="test:source_book",WRONG="test:source_wrong",RECOVERY_BOOK="test:source_recovery_book",GATE="chronicle_core:source_gate";
    private static final String CORE="chronicle_core:";
    private static Job job;
    private EventSourcesTrial() { }
    public static void register() {
        if(!"true".equals(System.getenv("CHRONICLE_EVENT_SOURCES_TRIAL")))return;
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            var command=literal("chronicle_event_sources_trial");
            for(String action:List.of("basic","recovery","departure","regions"))command.then(literal(action).executes(c->run(c.getSource(),action)));
            command.then(literal("departure_cancelled").executes(c->{try{fixture(c.getSource());var server=c.getSource().getServer();require(job!=null&&job.phase.equals("departure")&&job.stage==2,"wrong cancellation stage");require(Departures.state(server).operations().isEmpty()&&!flagged(server,departure()),"review/cancellation claimed departure completion");check("real departure cancellation and first confirmation produced no completion source");pass(c.getSource(),"departure_cancelled");return 1;}catch(Exception failure){c.getSource().sendError(Text.literal("Event sources trial FAIL departure_cancelled: "+failure.getMessage()));return 0;}}));
            for(String action:List.of("arm","verify"))command.then(literal(action).then(argument("type",string()).then(argument("target",string()).then(argument("activity",string()).executes(c->generic(c.getSource(),action,new EventSource(getString(c,"type"),getString(c,"target"),getString(c,"activity"))))))));
            dispatcher.register(command);
        });
        ServerTickEvents.END_SERVER_TICK.register(EventSourcesTrial::tick);
    }
    private static int generic(ServerCommandSource source,String action,EventSource eventSource) {
        try{fixture(source);if(action.equals("arm"))arm(source.getPlayerOrThrow(),eventSource,List.of());else verify(source.getServer(),eventSource);pass(source,action+" "+eventSource.type()+" "+eventSource.target()+" "+eventSource.activity());return 1;}
        catch(Exception error){source.sendError(Text.literal("Event sources trial FAIL "+action+": "+error.getMessage()));return 0;}
    }
    private static int run(ServerCommandSource source,String action) {
        try{fixture(source);require(source.getName().equals("ChronSources"),"wrong native fixture account");require(job==null,"another source fixture is running");
            switch(action){case "basic"->prepare(source);case "recovery"->recover(source);case "departure"->prepareDeparture(source);case "regions"->prepareRegions(source);default->throw new IllegalArgumentException();}return 1;
        }catch(Exception error){source.sendError(Text.literal("Event sources trial FAIL "+action+": "+error.getMessage()));return 0;}
    }
    private static void prepare(ServerCommandSource source)throws Exception {
        var server=source.getServer();var campaign=ChronicleRuntime.service(server);var player=source.getPlayerOrThrow();
        require(campaign.state().phase()==SessionPhase.INACTIVE,"fresh source fixture required");campaign.defineFlag(actor(player),GATE);
        campaign.start(actor(player),1);campaign.pause(actor(player));AuthoringRuntime.get(server).enable(player.getUuid());PlayRuntime.setParticipant(source,player.getUuid(),true);
        player.changeGameMode(GameMode.SURVIVAL);player.teleport(source.getWorld(),1.5,65,1.5,Set.of(),-90,0,false);
        for(int x=0;x<16;x++){source.getWorld().setBlockState(new BlockPos(x,63,1),Blocks.STONE.getDefaultState());source.getWorld().setBlockState(new BlockPos(x,64,1),Blocks.AIR.getDefaultState());}
        for(int x:List.of(3,9))source.getWorld().setBlockState(new BlockPos(x,64,1),Blocks.CHEST.getDefaultState());
        for(int x:List.of(5,7,11,13))source.getWorld().setBlockState(new BlockPos(x,64,1),Blocks.STONE.getDefaultState());
        for(int x:List.of(3,5,7,11,13))AuthoringRuntime.get(server).service().put(new AuthoringSite.Anchor("source_"+x,"minecraft:overworld",1,new BlockPosition(x,64,1),x==3?"minecraft:chest":"minecraft:stone"),false);
        for(var sourceKind:List.of(block("block_interacted",3),block("block_opened",3),block("block_broken",5),block("block_placed",7),book("book_found"),book("book_opened"),block("block_broken",11),block("block_broken",13)))
            arm(player,sourceKind,sourceKind.equals(book("book_opened"))||sourceKind.equals(block("block_broken",13))?List.of(GATE):List.of());
        arm(player,recoveryBook(),List.of());
        campaign.defineFlag(actor(player),"chronicle_core:source_recovery_observer");
        EventRuntime.save(player,new EventEditorForm("chronicle_core:source_recovery_watch",0,"Recovery source observer","Keeps source subscribed while another Event prerequisite is false","","","PARTICIPANTS","",0,"SYSTEM","",0,0,List.of(),List.of(),true,Map.of("chronicle_core:source_recovery_observer",true),block("block_broken",13)),null,true);
        AuthoringRuntime.get(server).disable(player.getUuid());campaign.resume(actor(player));job=new Job(player.getUuid(),"basic");
    }
    /** Existing QB fixture suites may arm this silent probe before performing their own real station/world work. */
    public static void arm(ServerPlayerEntity player,EventSource source,List<String> required)throws Exception {
        MinecraftServer server=player.getEntityWorld().getServer();String flag=flag(source);var campaign=ChronicleRuntime.service(server);
        require(campaign.state().phase()!=SessionPhase.ACTIVE&&AuthoringRuntime.isAuthor(player),"arm requires non-active operator Editing");
        if(!campaign.state().flags().containsKey(flag))campaign.defineFlag(actor(player),flag);
        if(EventRuntime.stored(server,event(source))!=null)return;
        EventRuntime.save(player,new EventEditorForm(event(source),0,"Native source proof","Disposable provider integration probe","","","PARTICIPANTS","",0,"SYSTEM","",0,0,required,List.of(),true,Map.of(flag,true),source),null,true);
    }
    public static void verify(MinecraftServer server,EventSource source) {
        var execution=PlayRuntime.service(server).state().executions().get(event(source));require(execution!=null&&execution.phase()==EventExecution.Phase.COMPLETE,"source Event has not completed: "+source);
        require(execution.source()==PlayState.DeliverySource.SYSTEM,"source probe bypassed SYSTEM dispatch");require(ChronicleRuntime.service(server).state().flag(flag(source)),"silent source outcome missing");
    }
    private static void tick(MinecraftServer server) {
        if(job==null)return;var player=server.getPlayerManager().getPlayer(job.player);if(player==null)return;
        try{fixture(player.getCommandSource());require(++job.ticks<1600,"source fixture deadline at stage "+job.stage);
            if(job.wait-->0)return;
            if(job.phase.equals("recovery")){recoveryTick(server,player);return;}
            if(job.phase.equals("departure")){departureTick(server,player);return;}
            if(job.phase.equals("regions")){regionsTick(server,player);return;}
            switch(job.stage) {
                case 0->{interact(player,9,ItemStack.EMPTY);player.closeHandledScreen();job.wait=15;job.stage++;}
                case 1->{require(!flagged(server,block("block_interacted",3))&&!flagged(server,block("block_opened",3)),"foreign target fired selected-site source");check("foreign block target ignored");interact(player,3,ItemStack.EMPTY);player.closeHandledScreen();job.stage++;}
                case 2->{if(!flagged(server,block("block_interacted",3))||!flagged(server,block("block_opened",3)))return;verify(server,block("block_interacted",3));verify(server,block("block_opened",3));check("real interaction manager opened selected chest and dispatched silent SYSTEM outcomes");require(player.interactionManager.tryBreakBlock(new BlockPos(5,64,1)),"real selected block break refused");job.stage++;}
                case 3->{if(!flagged(server,block("block_broken",5)))return;verify(server,block("block_broken",5));check("real successful block break matched its removed target");require(player.interactionManager.tryBreakBlock(new BlockPos(7,64,1)),"placement site preparation break refused");interact(player,7,new ItemStack(Items.STONE),true);job.stage++;}
                case 4->{if(!flagged(server,block("block_placed",7)))return;verify(server,block("block_placed",7));check("real BlockItem placement matched the exact registered site");
                    ItemStack spoof=new ItemStack(Items.WRITTEN_BOOK);spoof.set(DataComponentTypes.WRITTEN_BOOK_CONTENT,new WrittenBookContentComponent(RawFilteredPair.of("Source book"),"Fixture",0,List.of(RawFilteredPair.of(Text.literal("Untrusted lookalike"))),true));use(player,spoof);job.wait=15;job.stage++;}
                case 5->{require(!flagged(server,book("book_found"))&&!flagged(server,book("book_opened")),"unmarked title spoof produced a managed source");BookGifts.give(player.getCommandSource(),BookRuntime.books(server).get(WRONG),player.getUuidAsString());use(player,find(player,WRONG));job.wait=15;job.stage++;}
                case 6->{require(!flagged(server,book("book_found"))&&!flagged(server,book("book_opened")),"wrong authentic book with same title fired selected source");check("unmarked title spoof and wrong authentic same-title book ignored");BookGifts.give(player.getCommandSource(),BookRuntime.books(server).get(BOOK),player.getUuidAsString());job.stage++;}
                case 7->{if(!flagged(server,book("book_found")))return;verify(server,book("book_found"));require(!flagged(server,book("book_opened")),"inventory discovery counted as opening");check("real durable GM gift discovery fired Book found independently of opening");use(player,find(player,BOOK));job.wait=15;job.stage++;}
                case 8->{require(!flagged(server,book("book_opened")),"book opening ignored false prerequisite snapshot");ChronicleRuntime.service(server).setFlag(actor(player),GATE,true);job.wait=15;job.stage++;}
                case 9->{require(!flagged(server,book("book_opened")),"old opening resurrected when gate later turned On");check("discrete occurrence with false gate was not resurrected by later override");use(player,find(player,BOOK));job.stage++;}
                case 10->{if(!flagged(server,book("book_opened")))return;verify(server,book("book_opened"));check("authentic real WrittenBookItem.use fired future gated Book opened");seedRecovery(server,player);finish(player.getCommandSource(),"basic");}
                default->throw new IllegalStateException("invalid source stage");
            }
        }catch(Exception failure){var source=player.getCommandSource();source.sendError(Text.literal("Event sources trial FAIL "+job.phase+": "+failure.getMessage()));job=null;}
    }
    private static void seedRecovery(MinecraftServer server,ServerPlayerEntity player)throws Exception {
        var campaign=ChronicleRuntime.service(server);campaign.setFlag(actor(player),GATE,false);
        for(int x:List.of(11,13)){EventSource source=block("block_broken",x);String authority="transient:"+UUID.randomUUID();String id=EventSources.prepare(player,EventSources.id(authority,source.type(),source.target(),""),source,authority);require(id!=null,"uncertainty seam did not save active origin");}
        var content=ChestAuthoring.service(server);content.claimOperatorIssuance(RECOVERY_BOOK);
        String prepared=BookDeliveryRuntime.prepareDiscovery(server,RECOVERY_BOOK,content.deliveryToken(RECOVERY_BOOK),player.getUuid(),EventSources.active(player));require(prepared!=null,"book interruption preparation missing");
        require(content.state().bookDelivery().deliveries().get(RECOVERY_BOOK).firstDiscovery()==null&&countBook(player,RECOVERY_BOOK)==0,"book interruption seam already delivered or discovered its book");
        campaign.pause(actor(player));check("test-only interruption seam retained PREPARED block and book receipts before book inventory/discovery, with original false flag snapshot");
    }
    private static void recover(ServerCommandSource source)throws Exception {
        var server=source.getServer();var player=source.getPlayerOrThrow();var pending=EventSources.pending(server);
        require(pending.size()==3&&RuntimeHolds.reason(server)!=null,"restart did not retain uncertain block/book source hold");
        var cancelled=pending.stream().filter(r->r.target().equals("site:source_11")).findFirst().orElseThrow();
        var committed=pending.stream().filter(r->r.target().equals("site:source_13")).findFirst().orElseThrow();
        var book=pending.stream().filter(r->r.target().equals(RECOVERY_BOOK)).findFirst().orElseThrow();
        require(Boolean.FALSE.equals(committed.flags().get(GATE))&&committed.activeOrigin(),"restart lost accepted-origin flag snapshot");
        var campaign=ChronicleRuntime.service(server);campaign.resume(actor(player));expectRefusal(()->EventSources.recover(source,committed,true));campaign.pause(actor(player));
        var stale=new SourceLedger.Receipt(committed.id(),committed.type(),committed.target(),committed.activity(),committed.actor(),true,Map.of(GATE,true),committed.authority(),committed.stage());expectRefusal(()->EventSources.recover(source,stale,true));
        expectRefusal(()->EventSources.recover(source,book,true));require(ChestAuthoring.service(server).state().bookDelivery().deliveries().get(RECOVERY_BOOK).firstDiscovery()==null,"missing-inventory acknowledgement manufactured discovery");
        EventSources.recover(source,book,false);require(countBook(player,RECOVERY_BOOK)==0&&ChestAuthoring.service(server).state().bookDelivery().deliveries().get(RECOVERY_BOOK).firstDiscovery()==null,"source cancellation replayed issuance or discovery");
        EventSources.recover(source,cancelled,false);EventSources.recover(source,committed,true);require(RuntimeHolds.reason(server)==null,"exact source recovery did not release its hold");
        campaign.resume(actor(player));campaign.setFlag(actor(player),GATE,true);
        String fresh=BookDeliveryRuntime.prepareDiscovery(server,RECOVERY_BOOK,ChestAuthoring.service(server).deliveryToken(RECOVERY_BOOK),player.getUuid(),EventSources.active(player));
        require(fresh!=null&&!fresh.equals(book.id()),"cancelled book attempt reused its old identity");var freshBook=EventSources.pending(server).stream().filter(receipt->receipt.id().equals(fresh)).findFirst().orElseThrow();
        require(Boolean.TRUE.equals(freshBook.flags().get(GATE))&&Boolean.FALSE.equals(book.flags().get(GATE)),"fresh book attempt overwrote old flags or reused stale flags");campaign.pause(actor(player));
        expectRefusal(()->EventSources.recover(source,freshBook,true));int slot=emptySlot(player);ItemStack spoof=new ItemStack(Items.WRITTEN_BOOK);spoof.set(DataComponentTypes.WRITTEN_BOOK_CONTENT,new WrittenBookContentComponent(RawFilteredPair.of("Source book"),"Fixture",0,List.of(RawFilteredPair.of(Text.literal("Unmarked recovery lookalike"))),true));player.getInventory().setStack(slot,spoof);
        expectRefusal(()->EventSources.recover(source,freshBook,true));player.getInventory().setStack(slot,authenticRecoveryBook(server));player.getInventory().markDirty();EventSources.recover(source,freshBook,true);
        var found=ChestAuthoring.service(server).state().bookDelivery().deliveries().get(RECOVERY_BOOK).firstDiscovery();require(found!=null&&found.playerUuid().equals(player.getUuidAsString())&&countBook(player,RECOVERY_BOOK)==1,"real book recovery did not conserve one authentic book and original discovery actor");
        campaign.resume(actor(player));job=new Job(player.getUuid(),"recovery");job.wait=25;check("actual restart kept source evidence; active-phase and stale review refused; exact cancellation/acknowledgement audited");check("uncertain book could not complete without authentic inventory; cancellation allowed a fresh ID with changed flags, and real inventory proof durably recorded discovery before progress");
    }
    private static void recoveryTick(MinecraftServer server,ServerPlayerEntity player)throws Exception {
        switch(job.stage){case 0->{require(!flagged(server,block("block_broken",11))&&!flagged(server,block("block_broken",13)),"cancelled/false-origin-gate evidence incorrectly fired");require(player.interactionManager.tryBreakBlock(new BlockPos(13,64,1)),"future recovery block break refused");job.stage++;}
            case 1->{if(!flagged(server,block("block_broken",13))||!flagged(server,recoveryBook()))return;verify(server,block("block_broken",13));verify(server,recoveryBook());require(!flagged(server,block("block_broken",11)),"cancelled source was replayed");require(EventSources.facts(server,recoveryBook()).size()==1,"cancelled book source was replayed or recovery duplicated its occurrence");check("false-gate acknowledged occurrence stayed ignored; future real break completed once");finish(player.getCommandSource(),"recovery");}
            default->throw new IllegalStateException();}
    }
    private static void prepareDeparture(ServerCommandSource source)throws Exception {
        var server=source.getServer();var player=source.getPlayerOrThrow();var campaign=ChronicleRuntime.service(server);require(campaign.state().phase()==SessionPhase.INACTIVE,"fresh departure source fixture required");
        campaign.start(actor(player),1);campaign.pause(actor(player));AuthoringRuntime.get(server).enable(player.getUuid());PlayRuntime.setParticipant(source,player.getUuid(),true);player.changeGameMode(GameMode.SURVIVAL);
        for(int x=1;x<=7;x++)for(int z=1;z<=7;z++)source.getWorld().setBlockState(new BlockPos(x,63,z),Blocks.STONE.getDefaultState());
        source.getWorld().setBlockState(new BlockPos(4,64,4),Blocks.BARREL.getDefaultState());player.teleport(source.getWorld(),4.5,64,3.5,Set.of(),0,0,false);
        AreaChanges.begin(source,"source_ship","Source vessel",AreaState.START);AreaChanges.setDraftCorner(player,1,new BlockPosition(3,64,3));AreaChanges.setDraftCorner(player,2,new BlockPosition(5,66,5));AreaChanges.saveDraft(source);job=new Job(player.getUuid(),"departure");
    }
    private static void departureTick(MinecraftServer server,ServerPlayerEntity player)throws Exception {
        var source=player.getCommandSource();var area=AreaChanges.catalog(server).areas().get("source_ship");if(area==null||AreaChanges.busy(server))return;
        switch(job.stage){
            case 0->{if(area.start().isEmpty())return;source.getWorld().setBlockState(new BlockPos(4,64,4),Blocks.AIR.getDefaultState());AreaChanges.captureCurrent(source,area.id(),AreaState.END,area.revision());job.stage++;}
            case 1->{if(area.end().isEmpty())return;source.getWorld().setBlockState(new BlockPos(4,64,4),Blocks.BARREL.getDefaultState());var authoring=AuthoringRuntime.get(server).service();
                var anchor=(AuthoringSite.Anchor)authoring.put(new AuthoringSite.Anchor("source_helm","minecraft:overworld",1,new BlockPosition(4,64,4),"minecraft:barrel"),false);
                var boarding=AreaChanges.currentRegion(server,area);
                Departures.create(source,new DepartureDefinition("source_departure","Source vessel",1,Optional.of(new SiteRef(anchor.id(),anchor.revision())),Optional.of(new SiteRef(boarding.id(),boarding.revision())),"Review this fixture passenger.","This last departure ends play.",Optional.of(new AreaLink(area.id(),AreaState.END)),true),Departures.state(server).revision());
                arm(player,departure(),List.of());arm(player,region(area.bounds().regionId()),List.of());var campaign=ChronicleRuntime.service(server);campaign.defineFlag(actor(player),"chronicle_core:source_mixed_forbidden");
                EventRuntime.save(player,new EventEditorForm("chronicle_core:source_mixed_departure",0,"Ended mixed source","Must skip after final departure","This ended-play media must never be sent.","","PARTICIPANTS","",0,"SYSTEM","",0,0,List.of(),List.of(),true,Map.of("chronicle_core:source_mixed_forbidden",true),departure()),null,true);
                AuthoringRuntime.get(server).disable(player.getUuid());campaign.resume(actor(player));job.stage++;source.sendFeedback(()->Text.literal("Event sources trial NEXT departure_review"),false);}
            case 2->{if(!flagged(server,departure())||!flagged(server,region(area.bounds().regionId())))return;verify(server,departure());verify(server,region(area.bounds().regionId()));
                require(ChronicleRuntime.service(server).state().phase()==SessionPhase.ENDED&&player.isSpectator(),"final departure did not end play with committed spectator effects");require(source.getWorld().getBlockState(new BlockPos(4,64,4)).isAir(),"linked saved END state did not remove helm");
                var mixed=EventProgressRuntime.entry(server,EventRuntime.configuration(server,"chronicle_core:source_mixed_departure"));if(mixed==null||!mixed.skipped())return;
                require(!ChronicleRuntime.service(server).state().flag("chronicle_core:source_mixed_forbidden")&&!PlayRuntime.service(server).state().executions().containsKey("chronicle_core:source_mixed_departure"),"mixed departure source performed effects after ENDED");require(RuntimeHolds.reason(server)==null,"final departure source left a permanent hold");
                check("real two-confirmation linked departure ended play; silent departure and Region completion flags finalized after ENDED");check("matched mixed departure Event was skipped after play ended without flags, media or permanent hold");finish(source,"departure");}
            default->throw new IllegalStateException();
        }
    }
    private static EventSource departure(){return new EventSource(CORE+"departure_completed","source_departure","");}
    private static EventSource region(String id){return new EventSource(CORE+"region_applied",id,"END");}
    private static void prepareRegions(ServerCommandSource source)throws Exception {
        var server=source.getServer();var player=source.getPlayerOrThrow();var campaign=ChronicleRuntime.service(server);require(campaign.state().phase()==SessionPhase.INACTIVE,"fresh Region source fixture required");
        campaign.start(actor(player),1);campaign.pause(actor(player));AuthoringRuntime.get(server).enable(player.getUuid());PlayRuntime.setParticipant(source,player.getUuid(),true);player.changeGameMode(GameMode.SURVIVAL);
        source.getWorld().setBlockState(new BlockPos(8,63,8),Blocks.STONE.getDefaultState());source.getWorld().setBlockState(new BlockPos(8,64,8),Blocks.STONE.getDefaultState());player.teleport(source.getWorld(),5.5,65,5.5,Set.of(),0,0,false);
        AreaChanges.begin(source,"source_region","Source Region",AreaState.START);AreaChanges.setDraftCorner(player,1,new BlockPosition(8,64,8));AreaChanges.setDraftCorner(player,2,new BlockPosition(8,64,8));AreaChanges.saveDraft(source);job=new Job(player.getUuid(),"regions");
    }
    private static void regionsTick(MinecraftServer server,ServerPlayerEntity player)throws Exception {
        var source=player.getCommandSource();var area=AreaChanges.catalog(server).areas().get("source_region");if(area==null||AreaChanges.busy(server))return;
        EventSource start=new EventSource(CORE+"region_applied",area.bounds().regionId(),"START"),end=region(area.bounds().regionId());
        switch(job.stage) {
            case 0->{if(area.start().isEmpty())return;source.getWorld().setBlockState(new BlockPos(8,64,8),Blocks.GOLD_BLOCK.getDefaultState());AreaChanges.captureCurrent(source,area.id(),AreaState.END,area.revision());job.stage++;}
            case 1->{if(area.end().isEmpty())return;arm(player,start,List.of());arm(player,end,List.of());
                var snapshot=area.requireSnapshot(AreaState.END);var change=new EventEditorForm.Change(area.bounds().regionId(),area.name(),"END",area.revision(),snapshot.sha256(),area.bounds().dimension(),true,"");
                EventRuntime.save(player,new EventEditorForm("chronicle_core:source_world_batch",0,"Real aggregate source","Disposable Region source batch","","","PARTICIPANTS","",0,"MANUAL","",0,0,List.of(),List.of(change),true,Map.of(),EventSource.empty()),null,true);
                require(!flagged(server,start)&&!flagged(server,end),"capturing a Region template claimed application");
                AuthoringRuntime.get(server).disable(player.getUuid());ChronicleRuntime.service(server).resume(actor(player));
                player.teleport(source.getWorld(),8.5,64,8.5,Set.of(),0,0,false);
                for(int refused=0;refused<20;refused++)expectRefusal(()->WorldAreaOperations.apply(source,area.id(),AreaState.START,area.revision(),false));
                require(EventSources.pending(server).stream().noneMatch(receipt->receipt.authority().startsWith("area:")),"synchronous Area refusal leaked source preparations");
                player.teleport(source.getWorld(),5.5,65,5.5,Set.of(),0,0,false);WorldAreaOperations.apply(source,area.id(),AreaState.START,area.revision(),false);
                player.teleport(source.getWorld(),8.5,64,8.5,Set.of(),0,0,false);job.stage=10;job.wait=4;}
            case 10->{if(WorldAreaOperations.busy(server))return;require(!flagged(server,start)&&EventSources.pending(server).stream().noneMatch(receipt->receipt.authority().startsWith("area:")),"asynchronous Area refusal leaked source preparations or completion");
                require(RuntimeHolds.reason(server)==null,"refused Area source left a global hold");job.checks.add("20 synchronous and one asynchronous real Area refusals cancelled source evidence without consuming global pending capacity");
                player.teleport(source.getWorld(),5.5,65,5.5,Set.of(),0,0,false);WorldAreaOperations.apply(source,area.id(),AreaState.START,area.revision(),false);job.stage=2;}
            case 2->{if(!flagged(server,start))return;verify(server,start);require(source.getWorld().getBlockState(new BlockPos(8,64,8)).isOf(Blocks.STONE),"direct START source preceded actual terrain commit");require(!flagged(server,end),"direct START claimed unrelated END application");
                job.checks.add("real direct Area operation COMPLETE dispatched exact saved START Region source; capture and foreign state did not count");
                EventRuntime.run(player,"chronicle_core:source_world_batch",EventRuntime.review(server,"chronicle_core:source_world_batch"));job.stage++;}
            case 3->{if(!flagged(server,end))return;verify(server,end);var execution=PlayRuntime.service(server).state().executions().get("chronicle_core:source_world_batch");
                require(execution!=null&&execution.phase()==EventExecution.Phase.COMPLETE&&EventWorldBatches.status(server,execution.operationId()).orElse(null)==EventWorldBatches.Status.WORLD_COMMITTED,"aggregate source lacked whole Event world commit");
                require(source.getWorld().getBlockState(new BlockPos(8,64,8)).isOf(Blocks.GOLD_BLOCK),"aggregate END source preceded actual terrain commit");require(RuntimeHolds.reason(server)==null,"Region source completion left a hold");
                job.checks.add("real EventRuntime saved Region batch WORLD_COMMITTED dispatched END source independently of direct Area receipts");finish(source,"regions");}
            default->throw new IllegalStateException();
        }
    }
    private static void interact(ServerPlayerEntity player,int x,ItemStack stack){interact(player,x,stack,false);}
    private static void interact(ServerPlayerEntity player,int x,ItemStack stack,boolean ground) {
        player.setStackInHand(Hand.MAIN_HAND,stack);BlockPos pos=new BlockPos(x,ground?63:64,1);
        var result=player.interactionManager.interactBlock(player,player.getEntityWorld(),stack,Hand.MAIN_HAND,new BlockHitResult(pos.toCenterPos().add(0,.5,0),Direction.UP,pos,false));
        require(result.isAccepted(),"vanilla interaction refused at "+pos);
    }
    private static void use(ServerPlayerEntity player,ItemStack stack){player.setStackInHand(Hand.MAIN_HAND,stack.copy());require(stack.getItem().use(player.getEntityWorld(),player,Hand.MAIN_HAND).isAccepted(),"vanilla written book use refused");}
    private static ItemStack find(ServerPlayerEntity player,String id){for(int i=0;i<player.getInventory().size();i++){var stack=player.getInventory().getStack(i);if(id.equals(BookDeliveryRuntime.authenticBook(player.getEntityWorld().getServer(),stack)))return stack;}throw new IllegalStateException("authentic book missing: "+id);}
    private static EventSource block(String kind,int x){return new EventSource(CORE+kind,"site:source_"+x,"");}
    private static EventSource book(String kind){return new EventSource(CORE+kind,BOOK,"");}
    private static EventSource recoveryBook(){return new EventSource(CORE+"book_found",RECOVERY_BOOK,"");}
    private static int countBook(ServerPlayerEntity player,String book){int count=0;for(int slot=0;slot<player.getInventory().size();slot++)if(book.equals(BookDeliveryRuntime.authenticBook(player.getEntityWorld().getServer(),player.getInventory().getStack(slot))))count+=player.getInventory().getStack(slot).getCount();return count;}
    private static int emptySlot(ServerPlayerEntity player){for(int slot=0;slot<36;slot++)if(player.getInventory().getStack(slot).isEmpty())return slot;throw new IllegalStateException("no native inventory slot for book proof");}
    private static ItemStack authenticRecoveryBook(MinecraftServer server){var definition=BookRuntime.books(server).get(RECOVERY_BOOK);ItemStack book=new ItemStack(Items.WRITTEN_BOOK);book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT,new WrittenBookContentComponent(RawFilteredPair.of(definition.title()),definition.author(),0,definition.pages().stream().map(page->RawFilteredPair.<Text>of(Text.literal(page))).toList(),true));var content=ChestAuthoring.service(server);NbtCompound marker=new NbtCompound();marker.putString("campaign",content.state().bookDelivery().campaignId());marker.putString("book",RECOVERY_BOOK);marker.putString("token",content.deliveryToken(RECOVERY_BOOK));NbtCompound custom=new NbtCompound();custom.put("chronicle_book",marker);book.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(custom));return book;}
    private static String key(EventSource source){return UUID.nameUUIDFromBytes((source.type()+"|"+source.target()+"|"+source.activity()).getBytes(StandardCharsets.UTF_8)).toString();}
    public static String event(EventSource source){return "chronicle_core:source_probe/"+key(source);}
    public static String flag(EventSource source){return "chronicle_core:source_result/"+key(source);}
    private static boolean flagged(MinecraftServer server,EventSource source){return ChronicleRuntime.service(server).state().flag(flag(source));}
    private static Actor actor(ServerPlayerEntity player){return new Actor("event-sources-trial",player.getUuidAsString());}
    private static void check(String text){job.checks.add(text);}
    private static void finish(ServerCommandSource source,String phase)throws Exception {var json=new com.google.gson.JsonObject();json.addProperty("phase",phase);var checks=new com.google.gson.JsonArray();job.checks.forEach(checks::add);json.add("checks",checks);Files.writeString(root(source.getServer()).resolve(".event-sources-trial.json"),json+"\n");job=null;pass(source,phase);}
    private static void pass(ServerCommandSource source,String text){source.sendFeedback(()->Text.literal("Event sources trial PASS "+text),false);}
    private static Path root(MinecraftServer server){return server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();}
    private static void fixture(ServerCommandSource source){var server=source.getServer();Path root=root(server);require("true".equals(System.getenv("CHRONICLE_EVENT_SOURCES_TRIAL"))&&source.hasPermissionLevel(2)&&source.getPlayer()!=null&&root!=null&&root.getParent()!=null&&root.getParent().getFileName().toString().equals("test-runs")&&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")&&Files.isRegularFile(root.resolve(".event-sources-fixture"))&&server.isDedicated()&&!server.isOnlineMode()&&server.getServerPort()==25575&&"127.0.0.1".equals(server.getServerIp()),"unsafe source fixture");}
    private static void expectRefusal(Attempt attempt)throws Exception{try{attempt.run();}catch(IllegalStateException expected){return;}throw new IllegalStateException("unsafe recovery accepted");}
    private static void require(boolean value,String text){if(!value)throw new IllegalStateException(text);}
    @FunctionalInterface private interface Attempt{void run()throws Exception;}
    private static final class Job{final UUID player;final String phase;final List<String> checks=new ArrayList<>();int stage,ticks,wait;Job(UUID player,String phase){this.player=player;this.phase=phase;}}
}
