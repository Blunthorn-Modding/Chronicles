package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.SessionGameplay;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Peaceful replenishment bypasses HungerManager.update and must share the same boundary. */
@Mixin(ServerPlayerEntity.class)
abstract class PausedPeacefulHungerMixin {
    @Inject(method="tickHunger", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$peaceful(CallbackInfo ci) {
        if (SessionGameplay.frozen((ServerPlayerEntity)(Object)this)) ci.cancel();
    }
}
