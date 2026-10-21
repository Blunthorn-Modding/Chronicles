package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.EnchantmentCapRuntime;
import net.minecraft.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
abstract class EnchantmentMaximumMixin {
    @Inject(method="getMaxLevel", at=@At("RETURN"), cancellable=true)
    private void chronicle$serverMaximum(CallbackInfoReturnable<Integer> ci) {
        ci.setReturnValue(EnchantmentCapRuntime.maximum((Enchantment)(Object)this, ci.getReturnValue()));
    }
}
