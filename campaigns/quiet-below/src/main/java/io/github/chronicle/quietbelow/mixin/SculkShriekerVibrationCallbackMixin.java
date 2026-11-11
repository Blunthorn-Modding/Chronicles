package io.github.chronicle.quietbelow.mixin;

import io.github.chronicle.quietbelow.ecology.EcologyHooks;
import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.PositionSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.block.entity.SculkShriekerBlockEntity$VibrationCallback")
abstract class SculkShriekerVibrationCallbackMixin {
    @Shadow
    public abstract PositionSource getPositionSource();

    @Inject(
            method = "accepts(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/world/event/GameEvent$Emitter;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void quietBelow$gateOwnedShriekerIntake(
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
    private void quietBelow$gateQueuedOwnedShriekerVibration(
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
        if (handler == null) {
            return false;
        }
        Optional<Vec3d> source = getPositionSource().getPos(world);
        if (source.isEmpty()) {
            return false;
        }
        BlockPos pos = BlockPos.ofFloored(source.get());
        return handler.managesFeature(world, pos) && !handler.active(world, pos);
    }
}
