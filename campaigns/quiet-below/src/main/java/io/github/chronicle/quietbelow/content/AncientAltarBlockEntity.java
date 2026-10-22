package io.github.chronicle.quietbelow.content;

import io.github.chronicle.quietbelow.QuietBelow;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** Only the placed station identity; no inventory, ticking work or per-sculk entity. */
public final class AncientAltarBlockEntity extends BlockEntity {
    public AncientAltarBlockEntity(BlockPos pos, BlockState state) { super(QuietBelow.ALTAR_BLOCK_ENTITY, pos, state); }
}
