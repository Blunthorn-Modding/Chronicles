package io.github.chronicle.quietbelow.content;

import io.github.chronicle.quietbelow.QuietBelow;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** No stored items or work queue. This identity permits bounded loaded-station appearance updates. */
public final class AncientForgeBlockEntity extends BlockEntity {
    long workingUntil;
    public AncientForgeBlockEntity(BlockPos position,BlockState state){super(QuietBelow.FORGE_BLOCK_ENTITY,position,state);}
}
