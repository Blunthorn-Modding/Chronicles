package io.github.chronicle.quietbelow.ecology;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/** Cached-only world reads. World.getBlockState/getChunk may refresh FULL-load tickets. */
final class EcologyLoadedBlocks {
    private EcologyLoadedBlocks() { }
    static WorldChunk chunk(ServerWorld world,BlockPos position){
        if(!world.isInBuildLimit(position)||!world.getWorldBorder().contains(position))return null;
        return world.getChunkManager().getWorldChunk(position.getX()>>4,position.getZ()>>4);
    }
    static BlockState state(ServerWorld world,BlockPos position){
        WorldChunk chunk=chunk(world,position);
        if(chunk==null)throw new IllegalStateException("Unloaded ecology read");
        return chunk.getBlockState(position);
    }
}
