package io.github.chronicle.core.server.testing;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.server.AreaChanges;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.Departures;
import io.github.chronicle.core.server.WorldAreaOperations;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import java.nio.file.Files;
import java.util.Set;
import static net.minecraft.server.command.CommandManager.literal;

/** Strictly disposable metadata and terrain fixture; all editor mutations are made by the client. */
public final class SubtypesTrial {
    private static final String REGION="subtype_boarding", OTHER="subtype_other", HELM="subtype_helm";
    private static io.github.chronicle.core.areas.AreaChange baseline;
    private static long baselineRegionRevision;
    private static AuthoringSite.Region menuOriginal;
    private static final BlockPos CHEST=new BlockPos(4,64,4), CONTROL=new BlockPos(8,64,8);
    private SubtypesTrial(){}
    public static void register(){if(!"true".equals(System.getenv("CHRONICLE_SUBTYPES_TRIAL")))return;
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{var root=literal("chronicle_subtypes_trial").requires(s->s.hasPermissionLevel(2));
            for(String action:new String[]{"prepare","end_scene","target","verify_apply","verify","verify_edit","verify_tool","respawn_tool","cancel_capture","verify_cancel","verify_ordinary","verify_stateful","baseline","verify_noop","change_bounds","verify_changed","verify_recapture","verify_both","verify_bounds_guard","verify_departure_removed","verify_states_removed","verify_capture_refused","prepare_menu_bounds","verify_menu_bounds_unpublished","verify_menu_bounds_saved"})root.then(literal(action).executes(c->run(c.getSource(),action)));dispatcher.register(root);});}
    private static int run(ServerCommandSource source,String action){try{fixture(source);switch(action){
        case "prepare" -> prepare(source); case "end_scene" -> setChest(source.getWorld(),Items.EMERALD,4); case "target" -> setChest(source.getWorld(),Items.NETHERITE_INGOT,1);
        case "verify_apply" -> {require(!AreaChanges.busy(source.getServer())&&!WorldAreaOperations.busy(source.getServer()),"operation still busy");var stack=chest(source.getWorld()).getStack(0);require(stack.isOf(Items.DIAMOND)&&stack.getCount()==2,"Start did not restore chest inventory");}
        case "verify", "verify_edit" -> verify(source,action.equals("verify_edit"));
        case "verify_tool" -> verifyTool(source);
        case "cancel_capture" -> cancelCapture(source);
        case "respawn_tool" -> {var player=respawnTool(source);player.sendMessage(Text.literal("Subtypes trial PASS respawn_tool"),false);return 1;}
        case "verify_cancel" -> {var player=source.getPlayerOrThrow();require(!AuthoringRuntime.get(source.getServer()).service().state().sites().containsKey("unified_cancelled"),"cancel published a Region");require(AuthoringRuntime.get(source.getServer()).regionDraft(player.getUuid()).isEmpty()&&!io.github.chronicle.core.server.AuthorSelectionTools.activeHand(player),"cancel retained the draft or shovel");}
        case "verify_ordinary" -> {var region=(AuthoringSite.Region)AuthoringRuntime.get(source.getServer()).service().state().sites().get("unified_ordinary");require(region!=null&&region.min().equals(new BlockPosition(2,63,2))&&region.max().equals(new BlockPosition(3,63,3)),"ordinary Region geometry wrong");require(AreaChanges.forRegion(source.getServer(),region.id()).isEmpty(),"ordinary Region has saved states");require(!io.github.chronicle.core.server.AuthorSelectionTools.activeHand(source.getPlayerOrThrow()),"tool retained after save");}
        case "verify_stateful" -> {var area=AreaChanges.catalog(source.getServer()).areas().values().stream().filter(a->a.name().equals("Unified stateful")).findFirst().orElseThrow();require(area.start().isEmpty()&&area.end().isPresent(),"End-first snapshot wrong");require(area.bounds().min().equals(new BlockPosition(2,63,2))&&area.bounds().max().equals(new BlockPosition(3,63,3)),"End-first bounds wrong");}
        case "baseline" -> {baseline=AreaChanges.forRegion(source.getServer(),REGION).orElseThrow();baselineRegionRevision=site(source).revision();require(baseline.start().isPresent()&&baseline.end().isPresent(),"baseline snapshots missing");}
        case "verify_noop" -> require(baseline!=null&&baseline.equals(AreaChanges.forRegion(source.getServer(),REGION).orElseThrow())&&site(source).revision()==baselineRegionRevision,"no-op edit changed Region revision or states");
        case "verify_bounds_guard" -> {var region=site(source);boolean refused=false;try{AuthoringRuntime.get(source.getServer()).service().put(new AuthoringSite.Region(region.id(),region.dimension(),region.revision(),region.min(),new BlockPosition(6,65,5)),true);}catch(IllegalStateException expected){refused=true;}require(refused&&region.equals(site(source)),"departure pin allowed bounds mutation");}
        case "verify_departure_removed" -> require(Departures.state(source.getServer()).definitions().values().stream().noneMatch(d->d.name().equals("Edited subtype departure")),"unused departure subtype was not removed");
        case "verify_states_removed" -> require(AreaChanges.forRegion(source.getServer(),REGION).isEmpty()&&site(source)!=null&&AreaChanges.operations(source.getServer()).state().operations().values().stream().anyMatch(o->o.areaId().equals(baseline.id())&&o.intent()==io.github.chronicle.core.areas.AreaOperation.Intent.DELETE_UNCHANGED),"Saved states removal lost geometry or deletion history");
        case "change_bounds" -> {var player=source.getPlayerOrThrow();var author=AuthoringRuntime.get(source.getServer());var original=site(source);author.resumeRegionDraft(player,REGION);author.setRegionDraftCorner(player,2,new BlockPosition(7,65,5));String review=io.github.chronicle.core.server.RegionAuthoringWorkflow.save(source);require(review.contains("both saved states stale")&&original.equals(site(source))&&baseline.equals(AreaChanges.forRegion(source.getServer(),REGION).orElseThrow()),"bounds review wrote metadata or omitted warning");author.setRegionDraftCorner(player,2,new BlockPosition(6,65,5));boolean refused=false;try{io.github.chronicle.core.server.RegionAuthoringWorkflow.save(source,true);}catch(IllegalStateException expected){refused=true;}require(refused&&original.equals(site(source)),"changed proposal reused old review");io.github.chronicle.core.server.RegionAuthoringWorkflow.save(source);io.github.chronicle.core.server.RegionAuthoringWorkflow.save(source,true);require(baseline.equals(AreaChanges.forRegion(source.getServer(),REGION).orElseThrow()),"confirmed bounds change altered snapshots");}
        case "verify_capture_refused" -> {var server=source.getServer();require(!AuthoringRuntime.isAuthor(source.getPlayerOrThrow()),"fixture expected Editing off");var before=AreaChanges.catalog(server);var area=AreaChanges.forRegion(server,REGION).orElseThrow();boolean refused=false;try{AreaChanges.captureCurrent(source,area.id(),io.github.chronicle.core.areas.AreaState.START,area.revision());}catch(IllegalStateException expected){refused=true;}require(refused&&before.equals(AreaChanges.catalog(server))&&!AreaChanges.busy(server)&&io.github.chronicle.core.server.RuntimeHolds.reason(server)==null,"capture without Editing mutated state or acquired a hold");}
        case "prepare_menu_bounds" -> {var author=AuthoringRuntime.get(source.getServer());var player=source.getPlayerOrThrow();menuOriginal=site(source);author.resumeRegionDraft(player,REGION);author.setRegionDraftCorner(player,2,new BlockPosition(8,65,5));}
        case "verify_menu_bounds_unpublished" -> require(menuOriginal!=null&&menuOriginal.equals(site(source)),"menu review published bounds before confirmation");
        case "verify_menu_bounds_saved" -> require(site(source).max().equals(new BlockPosition(8,65,5))&&site(source).revision()==menuOriginal.revision()+1&&AreaChanges.stale(source.getServer(),AreaChanges.forRegion(source.getServer(),REGION).orElseThrow()),"menu confirmation failed to publish exactly one revised Region with stale states");
        case "verify_changed" -> {var area=AreaChanges.forRegion(source.getServer(),REGION).orElseThrow();require(baseline.equals(area)&&AreaChanges.stale(source.getServer(),area),"changed bounds lost old states or are not stale");}
        case "verify_recapture" -> {var area=AreaChanges.forRegion(source.getServer(),REGION).orElseThrow();require(area.id().equals(baseline.id())&&area.start().isPresent()&&area.end().isEmpty()&&!AreaChanges.stale(source.getServer(),area),"recapture did not keep ID, save Start and invalidate End");}
        case "verify_both" -> {var area=AreaChanges.forRegion(source.getServer(),REGION).orElseThrow();require(area.id().equals(baseline.id())&&area.start().isPresent()&&area.end().isPresent(),"End recapture missing or identity changed");}
        default -> throw new IllegalArgumentException(action);
    }source.sendFeedback(()->Text.literal("Subtypes trial PASS "+action),false);return 1;}catch(Exception e){source.sendError(Text.literal("Subtypes trial FAIL "+action+": "+e.getMessage()));return 0;}}
    private static void prepare(ServerCommandSource source)throws Exception{var server=source.getServer();var p=source.getPlayerOrThrow();var world=source.getWorld();require(AreaChanges.ready(server)&&Departures.ready(server),"services unavailable");
        for(int x=0;x<=12;x++)for(int z=0;z<=12;z++)world.setBlockState(new BlockPos(x,63,z),Blocks.STONE.getDefaultState(),3);
        world.setBlockState(CHEST,Blocks.CHEST.getDefaultState(),3);setChest(world,Items.DIAMOND,2);world.setBlockState(CONTROL,Blocks.BARREL.getDefaultState(),3);
        var authoring=AuthoringRuntime.get(server);authoring.enable(p.getUuid());authoring.service().put(new AuthoringSite.Region(REGION,"minecraft:overworld",1,new BlockPosition(3,64,3),new BlockPosition(5,65,5)),false);
        authoring.service().put(new AuthoringSite.Region(OTHER,"minecraft:overworld",1,new BlockPosition(10,64,10),new BlockPosition(11,65,11)),false);
        authoring.service().put(new AuthoringSite.Anchor(HELM,"minecraft:overworld",1,new BlockPosition(8,64,8),"minecraft:barrel"),false);
        for(int i=0;i<16;i++)authoring.service().put(new AuthoringSite.Region(String.format(java.util.Locale.ROOT,"zzpage_%02d",i),"minecraft:overworld",1,new BlockPosition(10,64,10),new BlockPosition(11,65,11)),false);
        p.changeGameMode(GameMode.CREATIVE);p.teleport(world,1.5,64,1.5,Set.of(),0,0,false);p.setVelocity(0,0,0);p.onLanding();io.github.chronicle.core.server.SessionBuilding.set(source,true);
    }
    private static void verify(ServerCommandSource source,boolean edit){require(io.github.chronicle.core.server.SessionBuilding.enabled(source.getServer()),"departure Save/edit lost Building Mode");var area=AreaChanges.forRegion(source.getServer(),REGION).orElseThrow();require(area.start().isPresent()&&area.end().isPresent(),"Start/End were not captured");
        var departure=Departures.state(source.getServer()).definitions().values().stream().filter(d->d.name().equals(edit?"Edited subtype departure":"Subtype departure")).findFirst().orElseThrow();
        require(departure.revision()==(edit?2:1),"unexpected departure revision");require(departure.firstText().equals("First native warning")&&departure.finalText().equals("Final native warning"),"warning text was not preserved");require(departure.exactBlockAnchor().isPresent()&&departure.exactBlockAnchor().get().id().equals(HELM),"wrong anchor");require(departure.boardingRegion().isPresent()&&departure.boardingRegion().get().id().equals(REGION),"wrong boarding Region");
        require(departure.areaLink().isPresent()&&departure.areaLink().get().areaId().equals(area.id())&&departure.areaLink().get().requestedState().name().equals("END"),"wrong saved state");}
    private static net.minecraft.server.network.ServerPlayerEntity respawnTool(ServerCommandSource source)throws Exception {
        var server=source.getServer(); var old=source.getPlayerOrThrow(); var authoring=AuthoringRuntime.get(server);
        require(io.github.chronicle.core.server.RuntimeHolds.reason(server)==null,"fixture started with a hold");
        io.github.chronicle.core.server.RegionAuthoringWorkflow.begin(source,"Fixture respawn",null);
        require(io.github.chronicle.core.server.AuthorSelectionTools.active(old,old.getMainHandStack())&&taggedShovels(old)==1,"ordinary draft did not issue exactly one tool");
        // Same PlayerManager path and arguments used by the vanilla alive dimension-return path.
        var current=server.getPlayerManager().respawnPlayer(old,true,net.minecraft.entity.Entity.RemovalReason.CHANGED_DIMENSION);
        current.networkHandler.player=current; current.networkHandler.syncWithPlayerPosition();
        require(current!=old&&current.getUuid().equals(old.getUuid()),"alive respawn did not replace the player");
        require(authoring.regionDraft(current.getUuid()).isPresent(),"ordinary draft lost during alive respawn");
        require(taggedShovels(current)==1&&io.github.chronicle.core.server.AuthorSelectionTools.active(current,current.getMainHandStack()),"respawn retained a stale copy or failed to issue one active shovel");
        io.github.chronicle.core.server.RegionAuthoringWorkflow.cancel(current.getCommandSource());
        require(authoring.regionDraft(current.getUuid()).isEmpty()&&taggedShovels(current)==0&&!io.github.chronicle.core.server.AuthorSelectionTools.activeHand(current),"respawn draft cleanup retained the selection tool");
        current.teleport(source.getWorld(),1.5,64,1.5,Set.of(),0,0,false);current.setVelocity(0,0,0);current.onLanding();
        return current;
    }
    private static int taggedShovels(net.minecraft.server.network.ServerPlayerEntity player){int count=0;for(int i=0;i<player.getInventory().size();i++){var data=player.getInventory().getStack(i).get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);if(data!=null&&data.copyNbt().getString("chronicle_region_draft").isPresent())count++;}return count;}
    private static void cancelCapture(ServerCommandSource source)throws Exception {
        var server=source.getServer(); var player=source.getPlayerOrThrow(); var authoring=AuthoringRuntime.get(server);
        require(io.github.chronicle.core.server.RuntimeHolds.reason(server)==null&&!AreaChanges.busy(server),"fixture started with a hold or capture");
        var catalog=AreaChanges.catalog(server); var sites=authoring.service().state();
        io.github.chronicle.core.server.RegionAuthoringWorkflow.begin(source,"Fixture cancelled capture",io.github.chronicle.core.areas.AreaState.START);
        AreaChanges.setDraftCorner(player,1,new BlockPosition(2,63,2)); AreaChanges.setDraftCorner(player,2,new BlockPosition(3,63,3));
        io.github.chronicle.core.server.RegionAuthoringWorkflow.save(source);
        require(AreaChanges.busy(server)&&AreaChanges.hasDraft(server,player.getUuid())&&io.github.chronicle.core.server.RuntimeHolds.reason(server)!=null,"initial capture did not acquire its hold");
        io.github.chronicle.core.server.RegionAuthoringWorkflow.cancel(source);
        require(!AreaChanges.busy(server)&&!AreaChanges.hasDraft(server,player.getUuid())&&io.github.chronicle.core.server.RuntimeHolds.reason(server)==null,"cancellation left capture, draft or hold");
        require(catalog.equals(AreaChanges.catalog(server))&&sites.equals(authoring.service().state()),"cancelled capture published catalog or Region metadata");
        require(!io.github.chronicle.core.server.AuthorSelectionTools.activeHand(player),"cancelled capture retained its shovel");
    }
    private static AuthoringSite.Region site(ServerCommandSource source){return (AuthoringSite.Region)AuthoringRuntime.get(source.getServer()).service().state().sites().get(REGION);}
    private static void verifyTool(ServerCommandSource source)throws Exception{var player=source.getPlayerOrThrow();require(io.github.chronicle.core.server.SessionBuilding.enabled(source.getServer()),"Building Mode is not enabled");require(io.github.chronicle.core.server.AuthorSelectionTools.active(player,player.getMainHandStack()),"leased shovel left main hand");var nearby=source.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new net.minecraft.util.math.Box(-16,0,-16,32,128,32),e->io.github.chronicle.core.server.AuthorSelectionTools.active(player,e.getStack()));require(nearby.isEmpty(),"leased shovel dropped into the world");}
    private static ChestBlockEntity chest(ServerWorld world){if(!(world.getBlockEntity(CHEST) instanceof ChestBlockEntity c))throw new IllegalStateException("fixture chest missing");return c;}
    private static void setChest(ServerWorld world,net.minecraft.item.Item item,int count){var c=chest(world);c.clear();c.setStack(0,new ItemStack(item,count));c.markDirty();}
    private static void fixture(ServerCommandSource source){var server=source.getServer();var root=server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();require(root!=null&&root.getParent()!=null&&root.getParent().getFileName().toString().equals("test-runs")&&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")&&Files.isRegularFile(root.resolve(".subtypes-fixture"))&&server.isDedicated()&&!server.isOnlineMode()&&server.getServerPort()==25575&&"127.0.0.1".equals(server.getServerIp())&&source.getWorld().getRegistryKey()==net.minecraft.world.World.OVERWORLD,"unsafe fixture");}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
