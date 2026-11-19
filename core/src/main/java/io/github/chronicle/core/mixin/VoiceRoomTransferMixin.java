package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.VoiceRooms;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class VoiceRoomTransferMixin {
    @Inject(method = "teleportTo", at = @At("HEAD"))
    private void chronicle$invalidateVoiceBeforeTransfer(TeleportTarget target, CallbackInfoReturnable<ServerPlayerEntity> ci) {
        VoiceRooms.beforeTransfer((ServerPlayerEntity) (Object) this);
    }
    @Inject(method = "teleportTo", at = @At("RETURN"))
    private void chronicle$publishVoiceAfterTransfer(TeleportTarget target, CallbackInfoReturnable<ServerPlayerEntity> ci) {
        VoiceRooms.afterTransfer((ServerPlayerEntity) (Object) this);
    }
}
