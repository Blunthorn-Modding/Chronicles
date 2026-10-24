package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.SessionGameplay;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps ordinary healing from changing a paused player's live survival state. */
@Mixin(LivingEntity.class)
abstract class PausedLivingEntityPhysiologyMixin {
    @Inject(method="heal(F)V", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$freezeHealing(float amount, CallbackInfo callback) {
        if ((Object)this instanceof PlayerEntity player && SessionGameplay.frozen(player)) callback.cancel();
    }

    @Inject(method="tickStatusEffects()V", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$freezeStatusEffects(CallbackInfo callback) {
        // Freeze the whole effect loop: retaining an instance at duration % 600 == 0
        // would otherwise trigger vanilla's periodic upgrade/sync every player tick.
        if ((Object)this instanceof PlayerEntity player && SessionGameplay.frozen(player)) callback.cancel();
    }
}
