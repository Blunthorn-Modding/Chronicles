package io.github.chronicle.lastcouncil.mixin;

import io.github.chronicle.lastcouncil.server.BanquetRuntime;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A foreign merge relinquishes our ownership before vanilla mutates the existing instance. */
@Mixin(LivingEntity.class)
public abstract class BanquetEffectOwnershipMixin {
    @Inject(method="addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",at=@At("HEAD"))
    private void chronicle$foreignEffect(StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> callback) {
        if ((Object)this instanceof ServerPlayerEntity player) BanquetRuntime.effectUpdated(player, effect);
    }
}
