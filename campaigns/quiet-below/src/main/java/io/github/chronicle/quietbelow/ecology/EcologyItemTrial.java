package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.quietbelow.content.SculkAppearance;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import static net.minecraft.server.command.CommandManager.literal;

/** Real survival loot/BlockItem paths; enabled only in disposable loopback fixtures. */
public final class EcologyItemTrial {
    private static final List<Block> BLOCKS=List.of(Blocks.SCULK,Blocks.SCULK_VEIN,Blocks.SCULK_CATALYST,Blocks.SCULK_SENSOR,Blocks.SCULK_SHRIEKER);
    private EcologyItemTrial() { }
    public static void register(){
        if(System.getenv("CHRONICLE_ECOLOGY_NATIVE_TRIAL")==null)return;
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(literal("quiet_below_item_trial")
                .requires(s->s.hasPermissionLevel(2))
                .then(literal("vanilla").executes(c->run(c.getSource(),"vanilla")))
                .then(literal("prepare").executes(c->run(c.getSource(),"prepare")))
                .then(literal("connected").executes(c->run(c.getSource(),"connected")))));
    }
    private static int run(ServerCommandSource s,String phase){
        try{
            Path root=s.getServer().getRunDirectory().toRealPath();
            require(s.getServer().isDedicated()&&!s.getServer().isOnlineMode()&&s.getServer().getServerPort()==25575
                    &&s.getServer().getServerIp().equals("127.0.0.1")&&root.getParent().getFileName().toString().equals("test-runs")
                    &&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z"),"wrong item fixture");
            var player=s.getPlayerOrThrow();var world=s.getWorld();
            player.interactionManager.changeGameMode(GameMode.SURVIVAL);
            if(phase.equals("vanilla")){
                require(QuietBelowEcology.engineForTest(s.getServer()).setup()==null,"vanilla probe already configured");
                for(int i=0;i<BLOCKS.size();i++){
                    BlockPos p=new BlockPos(i+1,65,8);support(world,p);place(player,new ItemStack(BLOCKS.get(i)),p);
                    require(world.getBlockState(p).get(SculkAppearance.VITALITY)==0,"unconfigured placement recolored");
                    world.removeBlock(p,false);
                }
            }else if(phase.equals("prepare"))prepare(player,world);
            else connected(player,world);
            s.sendFeedback(()->Text.literal("PASS sculk items "+phase+": native loot, placement and network appearance checked."),false);return 1;
        }catch(Exception e){s.sendError(Text.literal("FAIL sculk items "+phase+": "+e.getMessage()));return 0;}
    }
    private static void prepare(ServerPlayerEntity player,ServerWorld world){
        var engine=QuietBelowEcology.engineForTest(world.getServer());
        require(engine.setup()!=null&&engine.setup().city().equals("items_source")&&engine.enabled(),"item source not indexed/enabled");
        player.interactionManager.changeGameMode(GameMode.SURVIVAL);
        ItemStack hoe=new ItemStack(Items.NETHERITE_HOE);
        hoe.addEnchantment(world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),1);
        for(int i=0;i<BLOCKS.size();i++){
            Block block=BLOCKS.get(i);BlockPos harvest=new BlockPos(i+1,65,-8);support(world,harvest);
            BlockState state=block.getDefaultState();if(block==Blocks.SCULK_VEIN)state=state.with(MultifaceBlock.getProperty(Direction.DOWN),true);
            world.setBlockState(harvest,state,Block.NOTIFY_ALL);player.setStackInHand(Hand.MAIN_HAND,hoe);
            require(player.interactionManager.tryBreakBlock(harvest),"survival harvest failed: "+block);
            var drops=world.getEntitiesByClass(ItemEntity.class,new Box(harvest),entity->entity.getStack().isOf(block.asItem()));
            require(drops.size()==1&&drops.getFirst().getStack().getCount()==1,"Silk Touch loot count changed: "+block);
            ItemStack stack=drops.getFirst().getStack().copy();drops.getFirst().discard();
            require(ItemStack.areItemsAndComponentsEqual(stack,new ItemStack(block)),"harvest changed vanilla stacking/components: "+block);
            // All four world appearances must produce the same ordinary item.
            for(int stage=0;stage<4;stage++){
                var loot=Block.getDroppedStacks(state.with(SculkAppearance.VITALITY,stage),world,harvest,null,player,hoe);
                require(loot.size()==1&&loot.getFirst().getCount()==1&&ItemStack.areItemsAndComponentsEqual(loot.getFirst(),stack),"appearance leaked into loot: "+block);
            }
            require(Block.getDroppedStacks(state,world,harvest,null,player,new ItemStack(Items.NETHERITE_HOE)).isEmpty(),"non-Silk loot changed: "+block);
            world.setBlockState(harvest,state,Block.NOTIFY_ALL);player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.NETHERITE_HOE));
            require(player.interactionManager.tryBreakBlock(harvest),"non-Silk survival break failed");
            require(world.getEntitiesByClass(ItemEntity.class,new Box(harvest),entity->entity.getStack().isOf(block.asItem())).isEmpty(),"non-Silk survival yielded sculk");
            BlockPos detached=new BlockPos(i+1,65,8);support(world,detached);
            if(state.contains(Properties.WATERLOGGED))world.setBlockState(detached,Blocks.WATER.getDefaultState(),Block.NOTIFY_ALL);
            place(player,stack.copy(),detached);
            require(world.getBlockState(detached).get(SculkAppearance.VITALITY)==3,"unconnected placed item looks healthy: "+block);
            if(state.contains(Properties.WATERLOGGED))require(world.getBlockState(detached).get(Properties.WATERLOGGED),"placement lost waterlogging");
            BlockPos connected=new BlockPos(i+1,65,0);place(player,stack.copy(),connected);
            require(world.getBlockState(connected).get(SculkAppearance.VITALITY)==3,"paused connected placement skipped dormant stage");
            if(block==Blocks.SCULK_SHRIEKER)require(!world.getBlockState(connected).get(SculkShriekerBlock.CAN_SUMMON),"harvested shrieker gained summon capability");
            // An explicit item component cannot override the server-derived appearance.
            BlockPos component=new BlockPos(i+1,65,12);support(world,component);
            ItemStack custom=stack.copy();custom.set(DataComponentTypes.BLOCK_STATE,BlockStateComponent.DEFAULT.with(SculkAppearance.VITALITY,0));
            place(player,custom,component);
            require(world.getBlockState(component).get(SculkAppearance.VITALITY)==3,"item component bypassed dormant placement");
            if(block==Blocks.SCULK_VEIN){
                // Even an old unowned blue vein is normalized when another face is placed.
                world.setBlockState(detached,world.getBlockState(detached).with(SculkAppearance.VITALITY,2),Block.NOTIFY_ALL);
                BlockPos wall=detached.north();world.setBlockState(wall,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                ItemStack face=stack.copy();player.setStackInHand(Hand.MAIN_HAND,face);
                var hit=new BlockHitResult(Vec3d.ofCenter(wall).add(0,0,0.5),Direction.SOUTH,wall,false);
                require(((BlockItem)face.getItem()).place(new ItemPlacementContext(player,Hand.MAIN_HAND,face,hit)).isAccepted(),"vein face extension rejected");
                var extended=world.getBlockState(detached);
                require(extended.get(SculkAppearance.VITALITY)==3&&extended.get(Properties.WATERLOGGED)
                        &&extended.get(MultifaceBlock.getProperty(Direction.DOWN))&&extended.get(MultifaceBlock.getProperty(Direction.NORTH)),"vein extension changed appearance/fluid/faces");
                world.setBlockState(detached,extended.with(SculkAppearance.VITALITY,3),Block.NOTIFY_ALL);
            }
        }
        BlockPos unrelated=new BlockPos(7,65,8);support(world,unrelated);place(player,new ItemStack(Blocks.CALIBRATED_SCULK_SENSOR),unrelated);
        require(!world.getBlockState(unrelated).contains(SculkAppearance.VITALITY),"unsupported calibrated sensor acquired appearance");
        world.removeBlock(unrelated,false);place(player,new ItemStack(Blocks.STONE),unrelated);
        require(world.getBlockState(unrelated).isOf(Blocks.STONE),"ordinary block item placement changed");
        for(int i=0;i<BLOCKS.size();i++)player.getInventory().setStack(i,new ItemStack(BLOCKS.get(i)));
        player.getInventory().setSelectedSlot(0);
    }
    private static void connected(ServerPlayerEntity player,ServerWorld world){
        var e=QuietBelowEcology.engineForTest(world.getServer());
        require(QuietBelowEcology.playing(world.getServer()),"item probe requires occupied play");
        for(int i=0;i<BLOCKS.size();i++){
            BlockPos p=new BlockPos(i+1,65,0),detached=new BlockPos(i+1,65,8);var at=QuietBelowEcology.pos(p);
            require(e.owns(at)&&e.living(at),"connected item not adopted/living: "+BLOCKS.get(i));
            require(world.getBlockState(p).get(SculkAppearance.VITALITY)==EcologyAppearance.stage(e.vitality().value(at),e.rooted(at),e.vitality().mode(at)),"connected item color not reconciled");
            require(!e.owns(QuietBelowEcology.pos(detached))&&world.getBlockState(detached).get(SculkAppearance.VITALITY)==3,"detached item activated/recolored");
        }
        var sensor=(net.minecraft.block.SculkSensorBlock)Blocks.SCULK_SENSOR;
        sensor.setActive(player,world,new BlockPos(4,65,8),world.getBlockState(new BlockPos(4,65,8)),15,15);
        require(world.getBlockState(new BlockPos(4,65,8)).get(SculkSensorBlock.POWER)==0,"detached placed sensor responded");
        sensor.setActive(player,world,new BlockPos(4,65,0),world.getBlockState(new BlockPos(4,65,0)),15,15);
        require(world.getBlockState(new BlockPos(4,65,0)).get(SculkSensorBlock.POWER)>0,"connected placed sensor stayed inactive");
        var detachedShrieker=world.getBlockEntity(new BlockPos(5,65,8),net.minecraft.block.entity.BlockEntityType.SCULK_SHRIEKER).orElseThrow();
        detachedShrieker.shriek(world,player);
        require(!world.getBlockState(new BlockPos(5,65,8)).get(SculkShriekerBlock.SHRIEKING),"detached placed shrieker responded");
        var liveShrieker=world.getBlockEntity(new BlockPos(5,65,0),net.minecraft.block.entity.BlockEntityType.SCULK_SHRIEKER).orElseThrow();
        liveShrieker.shriek(world,player);
        require(world.getBlockState(new BlockPos(5,65,0)).get(SculkShriekerBlock.SHRIEKING),"connected placed shrieker stayed inactive");
        BlockPos vein=new BlockPos(2,65,0),wall=vein.north();var before=world.getBlockState(vein);
        world.setBlockState(wall,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        ItemStack face=new ItemStack(Blocks.SCULK_VEIN);player.setStackInHand(Hand.MAIN_HAND,face);
        var hit=new BlockHitResult(Vec3d.ofCenter(wall).add(0,0,0.5),Direction.SOUTH,wall,false);
        require(((BlockItem)face.getItem()).place(new ItemPlacementContext(player,Hand.MAIN_HAND,face,hit)).isAccepted(),"owned vein extension failed");
        var after=world.getBlockState(vein);
        require(after.get(SculkAppearance.VITALITY).equals(before.get(SculkAppearance.VITALITY))
                &&after.get(MultifaceBlock.getProperty(Direction.DOWN))&&after.get(MultifaceBlock.getProperty(Direction.NORTH)),"owned vein extension reset its appearance/shape");
    }
    private static void support(ServerWorld w,BlockPos p){
        require(w.getChunkManager().isChunkLoaded(p.getX()>>4,p.getZ()>>4),"item fixture not loaded");
        w.setBlockState(p.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
    }
    private static void place(ServerPlayerEntity player,ItemStack stack,BlockPos p){
        player.setStackInHand(Hand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3d.ofCenter(p.down()).add(0,0.5,0),Direction.UP,p.down(),false);
        require(((BlockItem)stack.getItem()).place(new ItemPlacementContext(player,Hand.MAIN_HAND,stack,hit)).isAccepted(),"native item placement rejected: "+stack);
        require(stack.isEmpty(),"survival placement did not consume one item");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
