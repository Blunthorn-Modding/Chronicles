package io.github.chronicle.quietbelow.mixin;

import io.github.chronicle.quietbelow.ecology.EcologyHooks;
import java.util.Optional;
import net.minecraft.block.entity.SculkCatalystBlockEntity;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SculkCatalystBlockEntity.Listener.class)
abstract class SculkCatalystListenerMixin {
    @Shadow
    public abstract PositionSource getPositionSource();

    @Inject(
            method = "listen(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/world/event/GameEvent$Emitter;Lnet/minecraft/util/math/Vec3d;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void quietBelow$gateOwnedCatalystIntake(
            ServerWorld world,
            RegistryEntry<GameEvent> event,
            GameEvent.Emitter emitter,
            Vec3d eventPos,
            CallbackInfoReturnable<Boolean> callback
    ) {
        EcologyHooks.Handler handler = EcologyHooks.handler();
        if (handler == null) {
            return;
        }
        Optional<Vec3d> source = getPositionSource().getPos(world);
        if (source.isEmpty()) {
            return;
        }
        BlockPos pos = BlockPos.ofFloored(source.get());
        if (handler.owns(world, pos) && !handler.active(world, pos)) {
            callback.setReturnValue(false);
        }
    }
}
