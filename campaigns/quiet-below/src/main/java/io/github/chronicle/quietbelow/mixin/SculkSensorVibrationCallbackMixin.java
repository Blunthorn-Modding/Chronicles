package io.github.chronicle.quietbelow.mixin;

import io.github.chronicle.quietbelow.ecology.EcologyHooks;
import net.minecraft.entity.Entity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.block.entity.SculkSensorBlockEntity$VibrationCallback")
abstract class SculkSensorVibrationCallbackMixin {
    @Shadow
    @Final
    protected BlockPos pos;

    @Inject(
            method = "accepts(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/world/event/GameEvent$Emitter;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void quietBelow$gateOwnedSensorIntake(
            ServerWorld world,
            BlockPos eventPos,
            RegistryEntry<GameEvent> event,
            GameEvent.Emitter emitter,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (quietBelow$ownedAndInactive(world)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = "accept(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;F)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void quietBelow$gateQueuedOwnedSensorVibration(
            ServerWorld world,
            BlockPos eventPos,
            RegistryEntry<GameEvent> event,
            Entity source,
            Entity projectileOwner,
            float distance,
            CallbackInfo callback
    ) {
        if (quietBelow$ownedAndInactive(world)) {
            callback.cancel();
        }
    }

    private boolean quietBelow$ownedAndInactive(ServerWorld world) {
        EcologyHooks.Handler handler = EcologyHooks.handler();
        return handler != null && handler.owns(world, pos) && !handler.active(world, pos);
    }
}
