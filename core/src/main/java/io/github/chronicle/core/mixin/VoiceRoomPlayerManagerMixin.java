package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.VoiceRooms;
import net.minecraft.entity.Entity;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerManager.class)
public abstract class VoiceRoomPlayerManagerMixin {
    @Inject(method = "onPlayerConnect", at = @At("HEAD"))
    private void chronicle$beginVoiceAdmission(ClientConnection c, ServerPlayerEntity p, ConnectedClientData data, CallbackInfo ci) {
        VoiceRooms.beforeTransfer(p);
    }
    @Inject(method = "onPlayerConnect", at = @At("RETURN"))
    private void chronicle$finishVoiceAdmission(ClientConnection c, ServerPlayerEntity p, ConnectedClientData data, CallbackInfo ci) {
        VoiceRooms.afterTransfer(p);
    }
    @Inject(method = "respawnPlayer", at = @At("HEAD"))
    private void chronicle$beginVoiceRespawn(ServerPlayerEntity p, boolean alive, Entity.RemovalReason reason, CallbackInfoReturnable<ServerPlayerEntity> ci) {
        VoiceRooms.beforeTransfer(p);
    }
    @Inject(method = "respawnPlayer", at = @At("RETURN"))
    private void chronicle$finishVoiceRespawn(ServerPlayerEntity p, boolean alive, Entity.RemovalReason reason, CallbackInfoReturnable<ServerPlayerEntity> ci) {
        VoiceRooms.afterTransfer(ci.getReturnValue() == null ? p : ci.getReturnValue());
    }
    @Inject(method = "remove", at = @At("HEAD"))
    private void chronicle$beginVoiceDeparture(ServerPlayerEntity p, CallbackInfo ci) { VoiceRooms.beforeTransfer(p); }
    @Inject(method = "remove", at = @At("RETURN"))
    private void chronicle$finishVoiceDeparture(ServerPlayerEntity p, CallbackInfo ci) { VoiceRooms.afterTransfer(p); }
}
