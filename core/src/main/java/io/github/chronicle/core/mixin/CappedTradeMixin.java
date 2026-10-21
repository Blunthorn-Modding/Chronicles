package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.EnchantmentCapRuntime;
import net.minecraft.item.ItemStack;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TradeOffer.class)
abstract class CappedTradeMixin {
    @Inject(method={"getSellItem","copySellItem"},at=@At("RETURN"))
    private void chronicle$capMerchantOutput(CallbackInfoReturnable<ItemStack> ci) {
        EnchantmentCapRuntime.access(ci.getReturnValue());
    }
}
