package io.github.chronicle.lastcouncil.mixin;

import io.github.chronicle.lastcouncil.banquet.BanquetItems;
import io.github.chronicle.lastcouncil.server.BanquetRuntime;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The outer stack method includes vanilla nutrition, effects and use remainders. */
@Mixin(ItemStack.class)
public abstract class BanquetFoodMixin {
    @Inject(method = "finishUsing", at = @At("HEAD"), cancellable = true)
    private void chronicle$finishTrackedFood(World world, LivingEntity entity,
                                            CallbackInfoReturnable<ItemStack> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (entity instanceof ServerPlayerEntity player && BanquetItems.trackedFood(stack)
                && !BanquetRuntime.finishingFood()) {
            callback.setReturnValue(BanquetRuntime.finishFood(player, stack));
        }
    }
}
