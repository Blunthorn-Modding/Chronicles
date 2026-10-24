package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.SessionGameplay;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
abstract class PausedPlayerAttackMixin {
    @Inject(method="attack", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$attack(Entity target, CallbackInfo ci) {
        if (SessionGameplay.frozen((ServerPlayerEntity)(Object)this)) ci.cancel();
    }
}
