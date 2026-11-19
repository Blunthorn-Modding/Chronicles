package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.VoiceRooms;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class VoiceRoomPositionMixin {
    @Inject(method = "setPos", at = @At("HEAD"))
    private void chronicle$invalidateVoiceBeforePosition(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player) VoiceRooms.beforePosition(player, x, y, z);
    }
    @Inject(method = "setPos", at = @At("RETURN"))
    private void chronicle$publishVoiceAfterPosition(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player) VoiceRooms.afterPosition(player);
    }
}
