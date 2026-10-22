package io.github.chronicle.quietbelow.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;

/** Physical mapmaking anchor only; campaign binding and eradication authority are external. */
public final class RelicHeartBlock extends Block {
    public static final MapCodec<RelicHeartBlock> CODEC = createCodec(RelicHeartBlock::new);
    public static final BooleanProperty VULNERABLE=BooleanProperty.of("vulnerable");
    public RelicHeartBlock(AbstractBlock.Settings settings) { super(settings);setDefaultState(getStateManager().getDefaultState().with(VULNERABLE,false)); }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(VULNERABLE);}
    @Override protected MapCodec<? extends Block> getCodec() { return CODEC; }
}
