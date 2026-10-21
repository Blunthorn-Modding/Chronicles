package io.github.chronicle.quietbelow.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.chronicle.quietbelow.ecology.EcologyHooks;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(World.class)
abstract class WorldMixin {
    @WrapMethod(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
            require = 1
    )
    private boolean quietBelow$observeServerWrite(
            BlockPos pos,
            BlockState proposed,
            int flags,
            int maxUpdateDepth,
            Operation<Boolean> original
    ) {
        World self = (World) (Object) this;
        EcologyHooks.Handler handler = EcologyHooks.handler();
        if (!(self instanceof ServerWorld world)
                || handler == null
                || world.isOutOfHeightLimit(pos)
                || world.isDebugWorld()) {
            return original.call(pos, proposed, flags, maxUpdateDepth);
        }
        if (EcologyHooks.claimOwnWrite()) {
            return original.call(pos, proposed, flags, maxUpdateDepth);
        }

        boolean ownedTarget = handler.owns(world, pos);
        EcologyHooks.CatalystOrigin catalystOrigin = EcologyHooks.catalystOrigin();
        boolean ownedCatalystDescendant = catalystOrigin != null
                && catalystOrigin.world() == world
                && handler.owns(world, catalystOrigin.pos());
        if (ownedCatalystDescendant
                && !world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }
        if (!ownedTarget && !ownedCatalystDescendant) {
            boolean success=original.call(pos, proposed, flags, maxUpdateDepth);
            if(success&&proposed.getBlock() instanceof net.minecraft.block.CropBlock)handler.cropWrite(world,pos,proposed);
            if(success&&(proposed.isOf(net.minecraft.block.Blocks.SCULK)||proposed.isOf(net.minecraft.block.Blocks.SCULK_VEIN)
                    ||proposed.isOf(net.minecraft.block.Blocks.SCULK_CATALYST)||proposed.isOf(net.minecraft.block.Blocks.SCULK_SENSOR)
                    ||proposed.isOf(net.minecraft.block.Blocks.SCULK_SHRIEKER)))handler.unownedNetworkWrite(world,pos);
            return success;
        }

        BlockState before = world.getBlockState(pos);
        BlockState effective = handler.beforeWrite(world, pos, before, proposed, flags);
        if (effective == null) {
            handler.afterWrite(world, pos, before, proposed, false);
            return false;
        }

        boolean success = false;
        RuntimeException runtimeFailure = null;
        Error errorFailure = null;
        try {
            success = original.call(pos, effective, flags, maxUpdateDepth);
            return success;
        } catch (RuntimeException failure) {
            runtimeFailure = failure;
            throw failure;
        } catch (Error failure) {
            errorFailure = failure;
            throw failure;
        } finally {
            try {
                handler.afterWrite(world, pos, before, effective, success);
                if(success&&effective.getBlock() instanceof net.minecraft.block.CropBlock)handler.cropWrite(world,pos,effective);
            } catch (RuntimeException | Error afterFailure) {
                if (runtimeFailure != null) {
                    runtimeFailure.addSuppressed(afterFailure);
                } else if (errorFailure != null) {
                    errorFailure.addSuppressed(afterFailure);
                } else {
                    throw afterFailure;
                }
            }
        }
    }
}
