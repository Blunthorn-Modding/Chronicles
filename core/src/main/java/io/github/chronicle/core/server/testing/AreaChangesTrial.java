package io.github.chronicle.core.server.testing;

import io.github.chronicle.core.areas.AreaChange;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.content.ChestTarget;
import io.github.chronicle.core.server.AreaChanges;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.ChestAuthoring;
import io.github.chronicle.core.server.ManagedBlocks;
import io.github.chronicle.core.server.WorldAreaOperations;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.literal;

/** Explicitly opt-in CF21 native checks. Every command refuses non-disposable worlds. */
public final class AreaChangesTrial {
    private static final String AREA="area_smoke", MANAGED="managed_quota", LORE="managed_lore";
    private static final BlockPos CHEST_KEEP=new BlockPos(4,64,4), CHEST_REMOVE=new BlockPos(5,64,4), SIGN=new BlockPos(6,64,4), COLOR=new BlockPos(7,64,4), QUOTA=new BlockPos(10,64,4), LORE_CHEST=new BlockPos(12,64,4);
    private static ItemStack staleTool=ItemStack.EMPTY;private static UUID operation;
    private AreaChangesTrial(){}

    public static void register(){
        if(!"true".equals(System.getenv("CHRONICLE_AREAS_TRIAL")))return;
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            var root=literal("chronicle_areas_trial").requires(source->source.hasPermissionLevel(2));
            for(String verb:List.of("prepare","invalid_corner","remember_tool","make_end","target_start","verify_start","target_end","verify_end","make_start2","target_start2","verify_start2","player_refusal","unloaded","lore_begin","lore_verify","seed_cancel","restore_stale","verify_cancel","quota_start","quota_end","quota_refuse","quota_verify","restart","delete_main","verify_delete","delete_quota"))root.then(literal(verb).executes(context->run(context.getSource(),verb)));
            dispatcher.register(root);
        });
    }

    private static int run(ServerCommandSource source,String verb){
        try{
            fixture(source);ServerPlayerEntity player=source.getPlayerOrThrow();
            switch(verb){
                case "prepare"->prepare(source,player);
                case "invalid_corner"->{
                    var before=AreaChanges.draft(source.getServer(),player.getUuid()).orElseThrow();var presentation=AuthoringRuntime.get(source.getServer()).presentation(player.getUuid());
                    try{AreaChanges.setDraftCorner(player,2,new BlockPosition(700,64,4));throw new IllegalStateException("oversized Area corner accepted");}
                    catch(IllegalArgumentException expected){require(expected.getMessage().contains("Corner not changed"),"unexpected corner refusal: "+expected.getMessage());}
                    require(before.equals(AreaChanges.draft(source.getServer(),player.getUuid()).orElseThrow()),"invalid corner changed Area draft");require(presentation.equals(AuthoringRuntime.get(source.getServer()).presentation(player.getUuid())),"invalid corner changed private outline/selection");
                }
                case "remember_tool"->{require(AreaChanges.hasDraft(source.getServer(),player.getUuid()),"Area draft missing");staleTool=player.getMainHandStack().copy();require(AreaChanges.isDebugTool(staleTool),"debug shovel was not issued");}
                case "make_end"->setEnd(source.getWorld());
                case "target_start"->{setTarget(source.getWorld());outside(player,source.getWorld());operation=WorldAreaOperations.apply(source,AREA,AreaState.START,area(source,AREA).revision(),false);}
                case "verify_start"->{complete(source);verifyStart(source.getWorld(),false);require(binding(source,CHEST_KEEP)&&binding(source,CHEST_REMOVE),"Start apply retired a surviving chest binding");}
                case "target_end"->{setTarget(source.getWorld());outside(player,source.getWorld());operation=WorldAreaOperations.apply(source,AREA,AreaState.END,area(source,AREA).revision(),false);}
                case "verify_end"->{complete(source);verifyEnd(source);require(binding(source,CHEST_KEEP),"surviving chest binding was removed");require(!binding(source,CHEST_REMOVE),"replaced chest binding was not retired");}
                case "make_start2"->setStart(source.getServer(),source.getWorld(),true);
                case "target_start2"->{setTarget(source.getWorld());outside(player,source.getWorld());operation=WorldAreaOperations.apply(source,AREA,AreaState.START,area(source,AREA).revision(),false);}
                case "verify_start2"->{complete(source);verifyStart(source.getWorld(),true);}
                case "player_refusal"->playerRefusal(source,player);
                case "unloaded"->unloaded(source,player);
                case "lore_begin"->loreBegin(source,player);
                case "lore_verify"->{require(!AreaChanges.catalog(source.getServer()).areas().containsKey(LORE),"managed lore capture was published");require(chest(source.getWorld(),LORE_CHEST).getStack(0).get(DataComponentTypes.CUSTOM_DATA)!=null,"managed lore source was changed");AreaChanges.cancel(source);}
                case "seed_cancel"->{AuthoringRuntime.get(source.getServer()).selection(player.getUuid(),new AuthoringRuntime.Selection("minecraft:overworld",position(CHEST_KEEP),position(COLOR)));}
                case "restore_stale"->{require(!staleTool.isEmpty(),"stale tool was not remembered");player.setStackInHand(Hand.MAIN_HAND,staleTool.copy());player.currentScreenHandler.sendContentUpdates();}
                case "verify_cancel"->verifyCancel(source,player);
                case "quota_start"->quotaStart(source,player);
                case "quota_end"->quotaEnd(source);
                case "quota_refuse"->quotaRefuse(source,player);
                case "quota_verify"->quotaVerify(source);
                case "restart"->restart(source,player);
                case "delete_main"->{outside(player,source.getWorld());operation=WorldAreaOperations.apply(source,AREA,AreaState.END,area(source,AREA).revision(),true);}
                case "verify_delete"->verifyDelete(source);
                case "delete_quota"->{AreaChange area=area(source,MANAGED);WorldAreaOperations.deleteUnchanged(source,MANAGED,area.revision());require(!AreaChanges.catalog(source.getServer()).areas().containsKey(MANAGED)&&AreaChanges.catalog(source.getServer()).tombstones().contains(MANAGED),"unchanged deletion did not retire/tombstone quota Area");}
                default->throw new IllegalArgumentException("unknown Area trial action");
            }
            source.sendFeedback(()->Text.literal("Areas trial PASS "+verb),false);return 1;
        }catch(Exception error){source.sendError(Text.literal("Areas trial FAIL "+verb+": "+error.getMessage()));return 0;}
    }

    private static void prepare(ServerCommandSource source,ServerPlayerEntity player)throws Exception{
        require(AreaChanges.ready(source.getServer()),"Area changes are unavailable");require(AreaChanges.catalog(source.getServer()).areas().isEmpty(),"fixture Area catalog is not empty");ServerWorld world=source.getWorld();
        for(int x=0;x<=16;x++)for(int z=0;z<=8;z++)world.setBlockState(new BlockPos(x,63,z),Blocks.STONE.getDefaultState(),3);
        player.changeGameMode(GameMode.CREATIVE);player.teleport(world,COLOR.getX()+.5,64,COLOR.getZ()+.5,Set.of(),90,0,false);player.setVelocity(0,0,0);player.onLanding();AuthoringRuntime.get(source.getServer()).enable(player.getUuid());
        setStart(source.getServer(),world,false);putBinding(source,CHEST_KEEP,"test:keep");putBinding(source,CHEST_REMOVE,"test:remove");
        AuthoringRuntime.get(source.getServer()).selection(player.getUuid(),new AuthoringRuntime.Selection("minecraft:overworld",position(QUOTA),position(LORE_CHEST)));
    }
    private static void setStart(net.minecraft.server.MinecraftServer server,ServerWorld world,boolean second){
        put(world,CHEST_KEEP,Blocks.CHEST);ChestBlockEntity keep=chest(world,CHEST_KEEP);keep.clear();keep.setStack(0,second?new ItemStack(Items.GOLD_INGOT,3):ordinaryBook());keep.markDirty();
        if(second)put(world,CHEST_REMOVE,Blocks.STONE);else{put(world,CHEST_REMOVE,Blocks.CHEST);ChestBlockEntity remove=chest(world,CHEST_REMOVE);remove.clear();remove.setStack(0,new ItemStack(Items.DIAMOND,2));remove.markDirty();}
        sign(world,SIGN,second?"START TWO":"START");put(world,COLOR,second?Blocks.LIME_WOOL:Blocks.RED_WOOL);
    }
    private static void setEnd(ServerWorld world){put(world,CHEST_KEEP,Blocks.CHEST);ChestBlockEntity keep=chest(world,CHEST_KEEP);keep.clear();keep.setStack(0,new ItemStack(Items.EMERALD,4));keep.markDirty();put(world,CHEST_REMOVE,Blocks.STONE);sign(world,SIGN,"END");put(world,COLOR,Blocks.BLUE_WOOL);}
    private static void setTarget(ServerWorld world){put(world,CHEST_KEEP,Blocks.CHEST);ChestBlockEntity keep=chest(world,CHEST_KEEP);keep.clear();keep.setStack(0,new ItemStack(Items.NETHERITE_INGOT));keep.markDirty();put(world,CHEST_REMOVE,Blocks.CHEST);ChestBlockEntity remove=chest(world,CHEST_REMOVE);remove.clear();remove.setStack(0,new ItemStack(Items.DIAMOND,7));remove.markDirty();sign(world,SIGN,"TARGET");put(world,COLOR,Blocks.BLACK_WOOL);}
    private static void verifyStart(ServerWorld world,boolean second){require(world.getBlockState(CHEST_KEEP).isOf(Blocks.CHEST),"Start keep chest missing");ItemStack actual=chest(world,CHEST_KEEP).getStack(0);require(second?actual.isOf(Items.GOLD_INGOT)&&actual.getCount()==3:ItemStack.areEqual(actual,ordinaryBook()),"Start container NBT did not restore exactly");if(second)require(world.getBlockState(CHEST_REMOVE).isOf(Blocks.STONE),"recaptured Start block missing");else require(chest(world,CHEST_REMOVE).getStack(0).isOf(Items.DIAMOND)&&chest(world,CHEST_REMOVE).getStack(0).getCount()==2,"Start removed-chest contents did not restore");require(signLine(world).equals(second?"START TWO":"START"),"Start sign NBT did not restore");require(world.getBlockState(COLOR).isOf(second?Blocks.LIME_WOOL:Blocks.RED_WOOL),"Start ordinary block did not restore");}
    private static void verifyEnd(ServerCommandSource source){ServerWorld world=source.getWorld();require(chest(world,CHEST_KEEP).getStack(0).isOf(Items.EMERALD)&&chest(world,CHEST_KEEP).getStack(0).getCount()==4,"End container NBT did not restore");require(world.getBlockState(CHEST_REMOVE).isOf(Blocks.STONE),"End replacement block missing");require(signLine(world).equals("END")&&world.getBlockState(COLOR).isOf(Blocks.BLUE_WOOL),"End sign/block state did not restore");require(world.getEntitiesByClass(ItemEntity.class,new Box(3,63,3,9,66,6),entity->true).isEmpty(),"replacement dropped discarded contents");}

    private static void playerRefusal(ServerCommandSource source,ServerPlayerEntity player){verifyStart(source.getWorld(),true);player.teleport(source.getWorld(),CHEST_KEEP.getX()+.5,64,CHEST_KEEP.getZ()+.5,Set.of(),0,0,false);int operations=AreaChanges.operations(source.getServer()).state().operations().size();try{WorldAreaOperations.apply(source,AREA,AreaState.END,area(source,AREA).revision(),false);throw new IllegalStateException("apply accepted a non-spectator inside the Area");}catch(IllegalStateException expected){require(expected.getMessage().contains("Move non-spectator"),"unexpected player refusal: "+expected.getMessage());}require(AreaChanges.operations(source.getServer()).state().operations().size()==operations,"player refusal published an operation");verifyStart(source.getWorld(),true);outside(player,source.getWorld());}
    private static void unloaded(ServerCommandSource source,ServerPlayerEntity player){ServerWorld world=source.getWorld();int chunk=1000;require(!world.getChunkManager().isChunkLoaded(chunk,chunk),"chosen unloaded chunk is already loaded");AreaChanges.begin(source,"unloaded_area","Unloaded",AreaState.START);AreaChanges.setDraftCorner(player,1,new BlockPosition(chunk*16,64,chunk*16));AreaChanges.setDraftCorner(player,2,new BlockPosition(chunk*16+1,64,chunk*16+1));try{AreaChanges.saveDraft(source);throw new IllegalStateException("unloaded capture was accepted");}catch(IllegalStateException expected){require(expected.getMessage().contains("unloaded chunk"),"unexpected unloaded refusal: "+expected.getMessage());}AreaChanges.cancel(source);require(!world.getChunkManager().isChunkLoaded(chunk,chunk),"unloaded refusal forced the chunk");}
    private static void loreBegin(ServerCommandSource source,ServerPlayerEntity player)throws Exception{put(source.getWorld(),LORE_CHEST,Blocks.CHEST);ChestBlockEntity chest=chest(source.getWorld(),LORE_CHEST);chest.clear();chest.setStack(0,managedBook(source));chest.markDirty();AreaChanges.begin(source,LORE,"Managed lore",AreaState.START);AreaChanges.setDraftCorner(player,1,position(LORE_CHEST));AreaChanges.setDraftCorner(player,2,position(LORE_CHEST));AreaChanges.saveDraft(source);}
    private static ItemStack managedBook(ServerCommandSource source)throws Exception{var service=ChestAuthoring.service(source.getServer());String id="test:area_managed";service.claimOperatorIssuance(id);ItemStack book=ordinaryBook();NbtCompound marker=new NbtCompound();marker.putString("campaign",service.state().bookDelivery().campaignId());marker.putString("book",id);marker.putString("token",service.deliveryToken(id));NbtCompound custom=new NbtCompound();custom.put("chronicle_book",marker);book.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(custom));return book;}
    private static void verifyCancel(ServerCommandSource source,ServerPlayerEntity player){require(!AreaChanges.hasDraft(source.getServer(),player.getUuid()),"cancelled Area draft survived");var selection=AuthoringRuntime.get(source.getServer()).selection(player.getUuid());require(position(CHEST_KEEP).equals(selection.first())&&position(COLOR).equals(selection.second()),"cancel did not restore prior private selection");require(AreaChanges.catalog(source.getServer()).areas().size()==1,"cancel/stale shovel published an Area");player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);staleTool=ItemStack.EMPTY;}

    private static void quotaStart(ServerCommandSource source,ServerPlayerEntity player){put(source.getWorld(),QUOTA,Blocks.STONE);AreaChanges.begin(source,MANAGED,"Managed quota",AreaState.START);AreaChanges.setDraftCorner(player,1,position(QUOTA));AreaChanges.setDraftCorner(player,2,position(QUOTA));AreaChanges.saveDraft(source);}
    private static void quotaEnd(ServerCommandSource source){net.minecraft.block.Block altar=net.minecraft.registry.Registries.BLOCK.get(net.minecraft.util.Identifier.of("quiet_below:ancient_altar"));require(altar!=Blocks.AIR,"Quiet Below altar is not registered");put(source.getWorld(),QUOTA,altar);AreaChanges.captureCurrent(source,MANAGED,AreaState.END,area(source,MANAGED).revision());}
    private static void quotaRefuse(ServerCommandSource source,ServerPlayerEntity player){put(source.getWorld(),QUOTA,Blocks.STONE);outside(player,source.getWorld());var snapshot=ManagedBlocks.snapshot(source.getServer());ManagedBlocks.setLimit(source,"quiet_below:ancient_altar",0,snapshot.policyRevision());operation=WorldAreaOperations.apply(source,MANAGED,AreaState.END,area(source,MANAGED).revision(),false);}
    private static void quotaVerify(ServerCommandSource source){require(WorldAreaOperations.problem(source.getServer()).isBlank()&&!WorldAreaOperations.busy(source.getServer())&&io.github.chronicle.core.server.RuntimeHolds.reason(source.getServer())==null,"precommit quota refusal left a blocking problem or hold");require(source.getWorld().getBlockState(QUOTA).isOf(Blocks.STONE),"managed quota refusal changed terrain");require(ManagedBlocks.snapshot(source.getServer()).instances().stream().noneMatch(value->value.position().equals(position(QUOTA))),"managed quota refusal registered an instance");require(AreaChanges.operations(source.getServer()).state().operations().values().stream().noneMatch(value->value.areaId().equals(MANAGED)),"managed quota refusal published durable apply evidence");}
    private static void restart(ServerCommandSource source,ServerPlayerEntity player){require(AreaChanges.ready(source.getServer()),"Area services did not recover cleanly");require(area(source,AREA).revision()==3&&area(source,MANAGED).revision()==2,"Area revisions did not survive restart");verifyStart(source.getWorld(),true);require(source.getWorld().getBlockState(QUOTA).isOf(Blocks.STONE),"quota-refused terrain changed over restart");require(AreaChanges.operations(source.getServer()).state().operations().values().stream().noneMatch(value->value.unresolved()),"unexpected unfinished operation after pre-commit refusal");AuthoringRuntime.get(source.getServer()).enable(player.getUuid());outside(player,source.getWorld());}
    private static void verifyDelete(ServerCommandSource source){require(!AreaChanges.catalog(source.getServer()).areas().containsKey(AREA)&&AreaChanges.catalog(source.getServer()).tombstones().contains(AREA),"applied deletion did not retire/tombstone Area");verifyEnd(source);require(binding(source,CHEST_KEEP),"surviving binding was removed by Area retirement");require(!binding(source,CHEST_REMOVE),"replaced binding returned after Area retirement");}

    private static void complete(ServerCommandSource source){require(operation!=null&&WorldAreaOperations.complete(source.getServer(),operation),"Area operation has not completed");}
    private static AreaChange area(ServerCommandSource source,String id){AreaChange area=AreaChanges.catalog(source.getServer()).areas().get(id);if(area==null)throw new IllegalStateException("Area missing: "+id);return area;}
    private static void putBinding(ServerCommandSource source,BlockPos pos,String id)throws Exception{ChestTarget target=new ChestTarget(id,1,"minecraft:overworld",pos.getX(),pos.getY(),pos.getZ(),"minecraft:chest");ChestAuthoring.service(source.getServer()).putTypedBinding("test:empty",target,false);}
    private static boolean binding(ServerCommandSource source,BlockPos pos){return ChestAuthoring.service(source.getServer()).state().bindings().values().stream().anyMatch(binding->binding.typed()&&binding.directTarget().x()==pos.getX()&&binding.directTarget().y()==pos.getY()&&binding.directTarget().z()==pos.getZ());}
    private static ItemStack ordinaryBook(){ItemStack book=new ItemStack(Items.WRITTEN_BOOK);book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT,new WrittenBookContentComponent(RawFilteredPair.of("Ordinary notes"),"Fixture",0,List.of(RawFilteredPair.of(Text.literal("The words chronicle_book are ordinary prose here."))),true));return book;}
    private static void sign(ServerWorld world,BlockPos pos,String line){put(world,pos,Blocks.OAK_SIGN);SignBlockEntity sign=(SignBlockEntity)world.getBlockEntity(pos);require(sign!=null,"sign block entity missing");sign.setText(sign.getFrontText().withMessage(0,Text.literal(line)),true);sign.markDirty();}
    private static String signLine(ServerWorld world){SignBlockEntity sign=(SignBlockEntity)world.getBlockEntity(SIGN);require(sign!=null,"sign block entity missing");return sign.getFrontText().getMessage(0,false).getString();}
    private static void put(ServerWorld world,BlockPos pos,net.minecraft.block.Block block){world.setBlockState(pos,block.getDefaultState(),net.minecraft.block.Block.FORCE_STATE_AND_SKIP_CALLBACKS_AND_DROPS|net.minecraft.block.Block.NOTIFY_LISTENERS);}
    private static ChestBlockEntity chest(ServerWorld world,BlockPos pos){if(!(world.getBlockEntity(pos) instanceof ChestBlockEntity chest))throw new IllegalStateException("chest missing at "+pos.toShortString());return chest;}
    private static BlockPosition position(BlockPos pos){return new BlockPosition(pos.getX(),pos.getY(),pos.getZ());}
    private static void outside(ServerPlayerEntity player,ServerWorld world){player.teleport(world,1.5,64,1.5,Set.of(),0,0,false);player.setVelocity(0,0,0);player.onLanding();}
    private static void fixture(ServerCommandSource source){var server=source.getServer();var root=server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();require(root!=null&&root.getParent()!=null&&root.getParent().getFileName().toString().equals("test-runs")&&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")&&java.nio.file.Files.isRegularFile(root.resolve(".area-changes-fixture"))&&server.isDedicated()&&!server.isOnlineMode()&&server.getServerPort()==25575&&"127.0.0.1".equals(server.getServerIp())&&source.getWorld().getRegistryKey()==net.minecraft.world.World.OVERWORLD,"unsafe Area changes fixture");}
    private static void require(boolean condition,String text){if(!condition)throw new IllegalStateException(text);}
}
