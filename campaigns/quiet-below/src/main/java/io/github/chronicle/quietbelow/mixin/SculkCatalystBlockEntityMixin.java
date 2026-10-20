package io.github.chronicle.quietbelow.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.chronicle.quietbelow.ecology.EcologyHooks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.SculkCatalystBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SculkCatalystBlockEntity.class)
abstract class SculkCatalystBlockEntityMixin {
    /**
     * Vanilla's largest direct cursor-relative read is the SculkBlock sensor search at
     * x/z +/-4. A full-block 16-block margin also contains its local spread and neighbor
     * update reads without relying on a chunk access to decide whether the tick is safe.
     */
    private static final int QUIET_BELOW_READ_MARGIN = 16;

    @WrapMethod(
            method = "tick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/entity/SculkCatalystBlockEntity;)V",
            require = 1
    )
    private static void quietBelow$gateOwnedCatalystTick(
            World world,
            BlockPos pos,
            BlockState state,
            SculkCatalystBlockEntity catalyst,
            Operation<Void> original
    ) {
        EcologyHooks.Handler handler = EcologyHooks.handler();
        if (!(world instanceof ServerWorld serverWorld) || handler == null || !handler.owns(serverWorld, pos)) {
            original.call(world, pos, state, catalyst);
            return;
        }
        if (!handler.active(serverWorld, pos)) {
            return;
        }
        if (!quietBelow$readNeighborhoodLoaded(serverWorld, catalyst)) {
            return;
        }
        try (EcologyHooks.Scope ignored = EcologyHooks.catalystScope(serverWorld, pos)) {
            original.call(world, pos, state, catalyst);
        }
    }

    private static boolean quietBelow$readNeighborhoodLoaded(
            ServerWorld world,
            SculkCatalystBlockEntity catalyst
    ) {
        var cursors = catalyst.getEventListener().getSpreadManager().getCursors();
        if (cursors.isEmpty()) {
            return true;
        }
        for (var cursor : cursors) {
            BlockPos cursorPos = cursor.getPos();
            int minChunkX = (cursorPos.getX() - QUIET_BELOW_READ_MARGIN) >> 4;
            int maxChunkX = (cursorPos.getX() + QUIET_BELOW_READ_MARGIN) >> 4;
            int minChunkZ = (cursorPos.getZ() - QUIET_BELOW_READ_MARGIN) >> 4;
            int maxChunkZ = (cursorPos.getZ() + QUIET_BELOW_READ_MARGIN) >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
