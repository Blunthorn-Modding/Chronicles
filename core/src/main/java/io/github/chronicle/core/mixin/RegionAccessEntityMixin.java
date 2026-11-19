package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.RegionAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.MovementType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class RegionAccessEntityMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void chronicle$guardPhysicalMovement(MovementType type, Vec3d delta, CallbackInfo callback) {
        if (!RegionAccess.canMove((Entity) (Object) this, delta)) callback.cancel();
    }

    @Inject(method = "setPos", at = @At("HEAD"), cancellable = true)
    private void chronicle$guardPositionCommit(double x, double y, double z, CallbackInfo callback) {
        if ((Object) this instanceof ServerPlayerEntity player && !RegionAccess.canSetPosition(player, new Vec3d(x, y, z)))
            callback.cancel();
    }

    @Inject(method = "setPosition(DDD)V", at = @At("RETURN"))
    private void chronicle$observePosition(double x, double y, double z, CallbackInfo callback) {
        if ((Object) this instanceof ServerPlayerEntity player) RegionAccess.moved(player);
    }

    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;setPosition(DDD)V"))
    private void chronicle$observeFastFlight(Entity entity, double x, double y, double z) {
        chronicle$observeMovement(entity, x, y, z);
    }

    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;setPosition(Lnet/minecraft/util/math/Vec3d;)V"))
    private void chronicle$observePhysicalMovement(Entity entity, Vec3d position) {
        chronicle$observeMovement(entity, position.x, position.y, position.z);
    }

    private static void chronicle$observeMovement(Entity entity, double x, double y, double z) {
        if (entity instanceof ServerPlayerEntity player) {
            var start = RegionAccess.captureMovement(player);
            entity.setPosition(x, y, z);
            RegionAccess.crossedBetweenPositions(player, start);
        } else {
            entity.setPosition(x, y, z);
        }
    }

    @Redirect(method = "updatePassengerPosition(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity$PositionUpdater;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity$PositionUpdater;accept(Lnet/minecraft/entity/Entity;DDD)V"))
    private void chronicle$guardPassengerSeat(Entity.PositionUpdater updater, Entity passenger, double x, double y, double z) {
        if (passenger instanceof ServerPlayerEntity player) {
            if (!RegionAccess.canSetPosition(player, new Vec3d(x, y, z))) {
                player.dismountVehicle();
                RegionAccess.refused(player);
                return;
            }
            var start = RegionAccess.captureMovement(player);
            updater.accept(player, x, y, z);
            RegionAccess.crossedBetweenPositions(player, start);
            return;
        }
        updater.accept(passenger, x, y, z);
    }

    @Inject(method = "teleportTo", at = @At("HEAD"), cancellable = true)
    private void chronicle$guardVehicleTransfer(TeleportTarget target, CallbackInfoReturnable<Entity> callback) {
        Entity entity = (Entity) (Object) this;
        if (!entity.hasPassengers()) return;
        var destination = EntityPosition.apply(EntityPosition.fromEntity(entity), EntityPosition.fromTeleportTarget(target), target.relatives());
        if (!RegionAccess.canTeleportPassengers(entity, target.world(), destination.position())) callback.setReturnValue(null);
    }
}
