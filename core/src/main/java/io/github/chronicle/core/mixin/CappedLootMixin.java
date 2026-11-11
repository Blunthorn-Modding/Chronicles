package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.EnchantmentCapRuntime;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.Consumer;

@Mixin(LootTable.class)
abstract class CappedLootMixin {
    @Inject(method="processStacks",at=@At("RETURN"),cancellable=true)
    private static void chronicle$capLoot(ServerWorld world,Consumer<ItemStack> consumer,CallbackInfoReturnable<Consumer<ItemStack>> ci) {
        Consumer<ItemStack> nativeConsumer=ci.getReturnValue();
        ci.setReturnValue(stack -> { EnchantmentCapRuntime.normalize(world.getServer(),stack);nativeConsumer.accept(stack); });
    }
}
