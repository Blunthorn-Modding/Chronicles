package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.RegionAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class RegionAccessNetworkMixin {
    @Shadow public ServerPlayerEntity player;
    @Shadow private Vec3d requestedTeleportPos;
    @Shadow private Entity topmostRiddenEntity;
    @Shadow public abstract void requestTeleport(double x, double y, double z, float yaw, float pitch);

    @Inject(method = "onPlayerMove", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), cancellable = true)
    private void chronicle$guardPlayerPacket(PlayerMoveC2SPacket packet, CallbackInfo callback) {
        if (!player.isLoaded() || requestedTeleportPos != null || player.hasVehicle() || !packet.changesPosition()) return;
        Vec3d to = chronicle$clamp(new Vec3d(packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ())));
        if (!RegionAccess.finite(to) || !Float.isFinite(packet.getYaw(player.getYaw())) || !Float.isFinite(packet.getPitch(player.getPitch()))) return;
        if (!RegionAccess.canMove(player, to.subtract(player.getEntityPos()))) {
            requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
            RegionAccess.refused(player);
            callback.cancel();
        } else {
            // Vanilla may tentatively apply a position and roll it back after its collision checks.
            RegionAccess.beginPacketMove(player);
        }
    }

    @Inject(method = "onPlayerMove", at = @At("RETURN"))
    private void chronicle$observePlayerPacket(PlayerMoveC2SPacket packet, CallbackInfo callback) {
        RegionAccess.finishPacketMove(player);
    }

    @Inject(method = "onVehicleMove", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), cancellable = true)
    private void chronicle$guardVehiclePacket(VehicleMoveC2SPacket packet, CallbackInfo callback) {
        Vec3d to = chronicle$clamp(packet.position());
        if (!player.isLoaded() || requestedTeleportPos != null || !RegionAccess.finite(to)
                || !Float.isFinite(packet.yaw()) || !Float.isFinite(packet.pitch())) return;
        Entity vehicle = player.getRootVehicle();
        if (vehicle == player || vehicle != topmostRiddenEntity || vehicle.getControllingPassenger() != player) return;
        if (!RegionAccess.canMove(vehicle, to.subtract(vehicle.getEntityPos()))) {
            player.networkHandler.sendPacket(VehicleMoveS2CPacket.fromVehicle(vehicle));
            RegionAccess.refused(player);
            callback.cancel();
        }
    }

    @Inject(method = "requestTeleport(Lnet/minecraft/entity/EntityPosition;Ljava/util/Set;)V", at = @At("HEAD"), cancellable = true)
    private void chronicle$guardDirectTeleport(EntityPosition target, Set<PositionFlag> relatives, CallbackInfo callback) {
        var destination = EntityPosition.apply(EntityPosition.fromEntity(player), target, relatives);
        if (!RegionAccess.canTeleport(player, player.getEntityWorld(), destination.position())) {
            RegionAccess.refused(player);
            callback.cancel();
        }
    }

    private static Vec3d chronicle$clamp(Vec3d vector) {
        return new Vec3d(MathHelper.clamp(vector.x, -3.0E7, 3.0E7), MathHelper.clamp(vector.y, -2.0E7, 2.0E7),
                MathHelper.clamp(vector.z, -3.0E7, 3.0E7));
    }
}
