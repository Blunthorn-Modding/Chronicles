package io.github.chronicle.quietbelow.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;

/** Intrinsic placed station; manual map-site registration is optional context only. */
public final class AncientAltarBlock extends HorizontalFacingBlock implements BlockEntityProvider {
    public static final MapCodec<AncientAltarBlock> CODEC = createCodec(AncientAltarBlock::new);
    private static final VoxelShape COLLISION = createCuboidShape(0, 0, 0, 16, 16, 16);
    private static final VoxelShape OUTLINE = createCuboidShape(-3, 0, -3, 19, 19, 19);
    public AncientAltarBlock(AbstractBlock.Settings settings) { super(settings); setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH)); }
    @Override protected MapCodec<? extends HorizontalFacingBlock> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) { return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite()); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new AncientAltarBlockEntity(pos, state); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return COLLISION; }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return OUTLINE; }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (player instanceof ServerPlayerEntity actor) AncientAltars.open(actor, pos,Hand.MAIN_HAND);
        return ActionResult.SUCCESS;
    }
    @Override protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayerEntity actor) AncientAltars.open(actor,pos,hand);
        return ActionResult.SUCCESS;
    }
    @Override protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (world instanceof ServerWorld serverWorld) io.github.chronicle.core.server.ManagedBlocks.observePlaced(serverWorld, pos, "quiet_below:ancient_altar");
    }
    @Override protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        super.onStateReplaced(state, world, pos, moved);
        io.github.chronicle.core.server.ManagedBlocks.verifyAfterReplacement(world, pos, "quiet_below:ancient_altar");
    }
}
