package io.github.chronicle.quietbelow.mixin;

import io.github.chronicle.quietbelow.ecology.EcologyHooks;
import net.minecraft.block.entity.SculkShriekerBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SculkShriekerBlockEntity.class)
abstract class SculkShriekerBlockEntityMixin {
    @Inject(
            method = "shriek(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void quietBelow$gateOwnedShriek(
            ServerWorld world,
            ServerPlayerEntity player,
            CallbackInfo callback
    ) {
        if (quietBelow$ownedAndInactive(world)) {
            callback.cancel();
        }
    }

    @Inject(
            method = "warn(Lnet/minecraft/server/world/ServerWorld;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void quietBelow$gateOwnedDelayedWarning(ServerWorld world, CallbackInfo callback) {
        if (quietBelow$ownedAndInactive(world)) {
            callback.cancel();
        }
    }

    private boolean quietBelow$ownedAndInactive(ServerWorld world) {
        BlockPos pos = ((SculkShriekerBlockEntity) (Object) this).getPos();
        EcologyHooks.Handler handler = EcologyHooks.handler();
        return handler != null && handler.managesFeature(world, pos) && !handler.active(world, pos);
    }
}
