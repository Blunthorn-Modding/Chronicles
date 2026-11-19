package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.RegionAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class RegionAccessPlayerMixin {
    @Inject(method = "teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/server/network/ServerPlayerEntity;",
            at = @At("HEAD"), cancellable = true)
    private void chronicle$guardPlayerTeleport(TeleportTarget target, CallbackInfoReturnable<ServerPlayerEntity> callback) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        var destination = EntityPosition.apply(EntityPosition.fromEntity(player), EntityPosition.fromTeleportTarget(target), target.relatives());
        if (!RegionAccess.canTeleport(player, target.world(), destination.position())) {
            RegionAccess.refused(player);
            callback.setReturnValue(null);
        } else RegionAccess.beginTeleport(player);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/server/network/ServerPlayerEntity;", at = @At("RETURN"))
    private void chronicle$observePlayerTeleport(TeleportTarget target, CallbackInfoReturnable<ServerPlayerEntity> callback) {
        RegionAccess.finishTeleport((ServerPlayerEntity) (Object) this, callback.getReturnValue() != null);
    }

    @Inject(method = "startRiding(Lnet/minecraft/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
    private void chronicle$guardMount(Entity vehicle, boolean force, boolean sendEvent, CallbackInfoReturnable<Boolean> callback) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (!RegionAccess.canMount(player, vehicle)) {
            RegionAccess.refused(player);
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "setCameraEntity", at = @At("HEAD"), cancellable = true)
    private void chronicle$guardSpectatorCamera(Entity target, CallbackInfo callback) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (target != null && target != player && target.getEntityWorld() instanceof ServerWorld world
                && !RegionAccess.canTeleport(player, world, target.getEntityPos())) {
            RegionAccess.refused(player);
            callback.cancel();
        }
    }
}
