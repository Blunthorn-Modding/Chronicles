package io.github.chronicle.quietbelow.content;

import io.github.chronicle.quietbelow.ecology.EcologyAppearance;
import io.github.chronicle.quietbelow.ecology.EcologyHooks;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Cosmetic state and native item placement; no new block/entity registrations. */
public final class SculkAppearance {
    public static final IntProperty VITALITY=IntProperty.of(EcologyAppearance.PROPERTY,0,3);
    private SculkAppearance() { }

    /** Native item writes only. Ownership/vitality remain the ecology's responsibility. */
    public static BlockState placement(World world,BlockPos pos,BlockState proposed) {
        EcologyHooks.Handler handler=EcologyHooks.handler();
        if(!(world instanceof ServerWorld server)||!proposed.contains(VITALITY)||handler==null
                ||!handler.managesFeature(server,pos))return proposed;
        // Native placement already requires the target chunk. Never load terrain here.
        if(!server.getChunkManager().isChunkLoaded(pos.getX()>>4,pos.getZ()>>4))return proposed;
        BlockState current=world.getBlockState(pos);
        // Extending an owned vein retains its derived band. Unowned legacy blue
        // veins and item components cannot bypass dormant placement.
        int stage=handler.owns(server,pos)&&current.isOf(proposed.getBlock())&&current.contains(VITALITY)
                ?current.get(VITALITY):EcologyAppearance.EXHAUSTED;
        return proposed.with(VITALITY,stage);
    }
}
