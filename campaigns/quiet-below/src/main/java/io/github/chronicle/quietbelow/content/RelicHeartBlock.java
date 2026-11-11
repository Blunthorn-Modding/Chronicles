package io.github.chronicle.quietbelow.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Physical mapmaking anchor only; campaign binding and eradication authority are external. */
public final class RelicHeartBlock extends Block {
    public static final MapCodec<RelicHeartBlock> CODEC = createCodec(RelicHeartBlock::new);
    public static final BooleanProperty VULNERABLE=BooleanProperty.of("vulnerable");
    public static final EnumProperty<Lens> LENS=EnumProperty.of("lens",Lens.class);
    public RelicHeartBlock(AbstractBlock.Settings settings) { super(settings);setDefaultState(getStateManager().getDefaultState().with(VULNERABLE,false).with(LENS,Lens.RESTING)); }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(VULNERABLE,LENS);}
    @Override protected MapCodec<? extends Block> getCodec() { return CODEC; }
    @Override protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (world instanceof ServerWorld serverWorld) io.github.chronicle.core.server.ManagedBlocks.observePlaced(serverWorld, pos, "quiet_below:relic_heart");
    }
    @Override protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        super.onStateReplaced(state, world, pos, moved);
        io.github.chronicle.core.server.ManagedBlocks.verifyAfterReplacement(world, pos, "quiet_below:relic_heart");
    }

    public enum Lens implements StringIdentifiable {
        RESTING("resting"),PROTECTED("protected"),WEAKENING("weakening"),VULNERABLE("vulnerable");
        private final String id;
        Lens(String id){this.id=id;}
        @Override public String asString(){return id;}
    }
}
