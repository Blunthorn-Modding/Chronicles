package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.SessionGameplay;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps player survival damage from advancing while the shared session clock is frozen. */
@Mixin(PlayerEntity.class)
abstract class PausedPlayerPhysiologyMixin {
    @Inject(method="damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$freezeDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if (SessionGameplay.frozen((PlayerEntity)(Object)this)) callback.setReturnValue(false);
    }
}
