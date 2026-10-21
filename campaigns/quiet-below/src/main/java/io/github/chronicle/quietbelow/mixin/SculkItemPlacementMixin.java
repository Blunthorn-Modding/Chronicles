package io.github.chronicle.quietbelow.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.chronicle.quietbelow.content.SculkAppearance;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Normalize both initial placement and the subsequent native block-state component write. */
@Mixin(BlockItem.class)
abstract class SculkItemPlacementMixin {
    @WrapOperation(method={"place(Lnet/minecraft/item/ItemPlacementContext;Lnet/minecraft/block/BlockState;)Z","placeFromNbt"},
            at=@At(value="INVOKE",target="Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    private boolean quietBelow$dormantPlacement(World world,BlockPos pos,BlockState state,int flags,Operation<Boolean> original) {
        return original.call(world,pos,SculkAppearance.placement(world,pos,state),flags);
    }
}
