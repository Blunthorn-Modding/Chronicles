package io.github.chronicle.quietbelow.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** One station block entity, no inventory/escrow and no per-sculk entities. */
public final class AncientForgeBlock extends HorizontalFacingBlock implements BlockEntityProvider {
    public static final MapCodec<AncientForgeBlock> CODEC=createCodec(AncientForgeBlock::new);
    public static final IntProperty HEAT=IntProperty.of("heat",0,2);
    // The solid masonry occupies its own cell. Decorative rims/chimney overhang
    // are selectable but do not unexpectedly block players in neighboring cells.
    private static final VoxelShape COLLISION=createCuboidShape(0,0,0,16,16,16);
    private static final VoxelShape OUTLINE=createCuboidShape(-3,0,-3,19,19,19);
    public AncientForgeBlock(AbstractBlock.Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.NORTH).with(HEAT,0));}
    @Override protected MapCodec<? extends HorizontalFacingBlock> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(FACING,HEAT);}
    @Override public BlockState getPlacementState(ItemPlacementContext context){return getDefaultState().with(FACING,context.getHorizontalPlayerFacing().getOpposite());}
    @Override public BlockEntity createBlockEntity(BlockPos position,BlockState state){return new AncientForgeBlockEntity(position,state);}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return COLLISION;}
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return OUTLINE;}
    @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit){
        if(player instanceof ServerPlayerEntity serverPlayer)AncientForges.open(serverPlayer,pos);
        return ActionResult.SUCCESS;
    }
    @Override protected ActionResult onUseWithItem(ItemStack stack,BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){
        // Main-hand tools are workshop targets, not vanilla block-use actions.
        return onUse(state,world,pos,player,hit);
    }
}
