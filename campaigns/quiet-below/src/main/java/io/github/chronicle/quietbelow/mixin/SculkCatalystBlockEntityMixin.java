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
    @WrapMethod(
            method = "tick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/entity/SculkCatalystBlockEntity;)V",
            require = 1
    )
    private static void quietBelow$ignoreOwnedCatalystCharge(
            World world, BlockPos pos, BlockState state, SculkCatalystBlockEntity catalyst,
            Operation<Void> original
    ) {
        EcologyHooks.Handler handler = EcologyHooks.handler();
        // Preserve old queued charge without running it. It must not reappear as bonus growth.
        if (world instanceof ServerWorld serverWorld && handler != null && handler.managesCatalyst(serverWorld, pos)) return;
        original.call(world, pos, state, catalyst);
    }
}
