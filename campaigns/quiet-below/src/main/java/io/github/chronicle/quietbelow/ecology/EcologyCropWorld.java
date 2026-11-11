package io.github.chronicle.quietbelow.ecology;

import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import static io.github.chronicle.quietbelow.ecology.QuietBelowEcology.blockPos;

/** Pinned native crop conditions and one-age writes, without changing random tick speed. */
final class EcologyCropWorld implements EcologyCrops.Terrain {
    private final ServerWorld world;
    EcologyCropWorld(ServerWorld world){this.world=world;}
    static boolean supported(BlockState state){return state.isOf(Blocks.WHEAT)||state.isOf(Blocks.CARROTS)||state.isOf(Blocks.POTATOES)||state.isOf(Blocks.BEETROOTS);}
    @Override public boolean loaded(EcologyTerrain.Pos p){
        return EcologyLoadedBlocks.chunk(world,blockPos(p))!=null;
    }
    @Override public boolean mayContainCrops(EcologyCrops.Section section){
        var pos=section.at(0);if(pos==null||world.isOutOfHeightLimit(blockPos(pos)))return false;
        // A section may straddle a custom border; its palette is safe metadata to inspect.
        var chunk=world.getChunkManager().getWorldChunk(pos.x()>>4,pos.z()>>4);
        return chunk!=null&&chunk.getSectionArray()[world.getSectionIndex(pos.y())].hasAny(EcologyCropWorld::supported);
    }
    @Override public EcologyCrops.Crop crop(EcologyTerrain.Pos p){
        if(!loaded(p))throw new IllegalStateException("Unloaded crop read");
        var pos=blockPos(p);BlockState state=EcologyLoadedBlocks.state(world,pos);if(!supported(state))return null;
        var crop=(CropBlock)state.getBlock();
        var soil=EcologyLoadedBlocks.chunk(world,pos.down());
        return new EcologyCrops.Crop(Registries.BLOCK.getId(crop).toString(),crop.getAge(state),crop.getMaxAge(),
                world.getBaseLightLevel(pos,0)>=9&&soil!=null&&soil.getBlockState(pos.down()).isOf(Blocks.FARMLAND));
    }
    @Override public boolean grow(EcologyTerrain.Pos p,EcologyCrops.Crop expected){
        if(!loaded(p)||!expected.equals(crop(p))||!expected.eligible()||expected.age()>=expected.maximum())return false;
        var pos=blockPos(p);BlockState actual=EcologyLoadedBlocks.state(world,pos);
        return world.setBlockState(pos,((CropBlock)actual.getBlock()).withAge(expected.age()+1),Block.NOTIFY_LISTENERS);
    }
}
