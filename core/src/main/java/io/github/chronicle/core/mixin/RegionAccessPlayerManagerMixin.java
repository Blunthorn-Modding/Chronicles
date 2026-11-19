package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.RegionAccess;
import net.minecraft.entity.Entity;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.TeleportTarget;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(PlayerManager.class)
public abstract class RegionAccessPlayerManagerMixin {
    @Inject(method = "onPlayerConnect", at = @At("HEAD"), cancellable = true)
    private void chronicle$guardLogin(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData data, CallbackInfo callback) {
        try {
            var exit = RegionAccess.admissionExit(player, player.getEntityWorld(), player.getEntityPos());
            if (exit != null) player.refreshPositionAndAngles(exit.x(), exit.y(), exit.z(), exit.yaw(), exit.pitch());
        } catch (IllegalStateException refusal) {
            connection.disconnect(chronicle$unsafeArrival());
            callback.cancel();
        }
    }

    @Redirect(method = "respawnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;getRespawnTarget(ZLnet/minecraft/world/TeleportTarget$PostDimensionTransition;)Lnet/minecraft/world/TeleportTarget;"))
    private TeleportTarget chronicle$guardRespawnTarget(ServerPlayerEntity player, boolean consumeAnchor, TeleportTarget.PostDimensionTransition transition) {
        var vanilla = player.getRespawnTarget(consumeAnchor, transition);
        try { return RegionAccess.guardRespawn(player, vanilla); }
        catch (IllegalStateException refusal) { return null; }
    }

    @Inject(method = "respawnPlayer", at = @At(value = "FIELD", target = "Lnet/minecraft/server/PlayerManager;players:Ljava/util/List;", opcode = Opcodes.GETFIELD, ordinal = 0),
            locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private void chronicle$refuseRespawn(ServerPlayerEntity player, boolean alive, Entity.RemovalReason reason,
                                         CallbackInfoReturnable<ServerPlayerEntity> callback, TeleportTarget target) {
        if (target == null) {
            player.networkHandler.disconnect(chronicle$unsafeArrival());
            callback.setReturnValue(player);
        }
    }

    private static Text chronicle$unsafeArrival() {
        return Text.literal("The guarded Region has no safe, loaded outside Point. Ask an operator to check Region access.");
    }
}
