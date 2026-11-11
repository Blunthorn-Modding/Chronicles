package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.EnchantmentCapRuntime;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clamp the actual inventory object before vanilla copies it for preview/network sync. */
@Mixin(Slot.class)
abstract class CappedSlotMixin {
    @Inject(method="getStack",at=@At("RETURN"))
    private void chronicle$normalizeSlot(CallbackInfoReturnable<ItemStack> ci) {
        EnchantmentCapRuntime.access(ci.getReturnValue());
    }
}
