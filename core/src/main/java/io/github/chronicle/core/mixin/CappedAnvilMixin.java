package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.EnchantmentCapRuntime;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A capped merge may otherwise charge levels solely to raise the prior-work penalty. */
@Mixin(AnvilScreenHandler.class)
abstract class CappedAnvilMixin {
    @Shadow @Final private Property levelCost;
    @Inject(method="updateResult",at=@At("TAIL"))
    private void chronicle$rejectUselessMerge(CallbackInfo ci) {
        var handler=(AnvilScreenHandler)(Object)this;
        // getMaxLevel already caps the candidate; only reject a no-improvement result.
        if (!EnchantmentCapRuntime.active()) return;
        ItemStack input=handler.getSlot(0).getStack(), output=handler.getSlot(2).getStack();
        if (input.isEmpty() || output.isEmpty()) return;
        ItemStack comparison=output.copy();
        Integer prior=input.get(DataComponentTypes.REPAIR_COST);
        if (prior==null) comparison.remove(DataComponentTypes.REPAIR_COST);
        else comparison.set(DataComponentTypes.REPAIR_COST,prior);
        if (ItemStack.areItemsAndComponentsEqual(input,comparison)) {
            handler.getSlot(2).setStack(ItemStack.EMPTY); levelCost.set(0);
        }
    }
}
