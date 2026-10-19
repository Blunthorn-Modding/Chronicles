package io.github.chronicle.quietbelow.content;

import com.mojang.serialization.MapCodec;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.quietbelow.QuietBelow;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** A static campaign clue block; it has no block entity or autonomous behavior. */
public final class ResonanceMarkerBlock extends HorizontalFacingBlock {
    public static final MapCodec<ResonanceMarkerBlock> CODEC = createCodec(ResonanceMarkerBlock::new);

    private static final VoxelShape NORTH_SHAPE = createCuboidShape(0, 0, 12.5, 16, 16, 16);
    private static final VoxelShape SOUTH_SHAPE = createCuboidShape(0, 0, 0, 16, 16, 3.5);
    private static final VoxelShape WEST_SHAPE = createCuboidShape(12.5, 0, 0, 16, 16, 16);
    private static final VoxelShape EAST_SHAPE = createCuboidShape(0, 0, 0, 3.5, 16, 16);
    private static final Text DORMANT_NOTICE = Text.translatable("message.quiet_below.session_dormant");
    private static final Text OBSERVATION = Text.translatable("message.quiet_below.marker_observation");
    private static final Text LENS_CLUE = Text.translatable("message.quiet_below.marker_lens_clue");

    public ResonanceMarkerBlock(AbstractBlock.Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        Direction side = context.getSide();
        Direction facing = side.getAxis().isHorizontal()
                ? side
                : context.getHorizontalPlayerFacing().getOpposite();
        return getDefaultState().with(FACING, facing);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapeFor(state.get(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapeFor(state.get(FACING));
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                 BlockHitResult hit) {
        if (!world.isClient()) {
            if (isSessionActive(world)) {
                player.sendMessage(revealed(world) ? Text.translatable("message.quiet_below.marker_revealed") : OBSERVATION, false);
            } else {
                player.sendMessage(DORMANT_NOTICE, false);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                         PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!stack.isOf(QuietBelow.ECHO_LENS)) {
            return stack.isEmpty() ? ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION : ActionResult.PASS;
        }

        if (!world.isClient()) {
            if (isSessionActive(world)) {
                player.sendMessage(revealed(world) ? Text.translatable("message.quiet_below.marker_lens_revealed") : LENS_CLUE, false);
                world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
                        SoundCategory.BLOCKS, 0.45f, 1.15f);
                ((ServerWorld) world).spawnParticles(ParticleTypes.ENCHANT,
                        pos.getX() + 0.5, pos.getY() + 0.65, pos.getZ() + 0.5,
                        8, 0.22, 0.18, 0.22, 0.01);
            } else {
                player.sendMessage(DORMANT_NOTICE, false);
            }
        }
        return ActionResult.SUCCESS;
    }

    private static boolean isSessionActive(World world) {
        try {
            return ChronicleRuntime.service(world.getServer()).state().phase() == SessionPhase.ACTIVE;
        } catch (IllegalStateException unavailable) {
            return false;
        }
    }

    private static boolean revealed(World world) {
        return ChronicleRuntime.service(world.getServer()).state().flag("quiet_below:fragment_revealed");
    }

    private static VoxelShape shapeFor(Direction facing) {
        return switch (facing) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> throw new IllegalStateException("Resonance marker must face horizontally");
        };
    }
}
