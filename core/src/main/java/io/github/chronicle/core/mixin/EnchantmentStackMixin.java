package io.github.chronicle.core.mixin;

import io.github.chronicle.core.enchanting.EnchantmentStackAccess;
import io.github.chronicle.core.enchanting.EnchantmentStacks;
import io.github.chronicle.core.server.EnchantmentCapRuntime;
import net.minecraft.component.MergedComponentMap;
import net.minecraft.component.ComponentChanges;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
abstract class EnchantmentStackMixin implements EnchantmentStackAccess {
    @Shadow @Final private MergedComponentMap components;
    @Unique private EnchantmentStacks.Stamp chronicle$capStamp;
    @Override public MergedComponentMap chronicle$capComponents() { return components; }
    @Override public EnchantmentStacks.Stamp chronicle$capStamp() { return chronicle$capStamp; }
    @Override public void chronicle$capStamp(EnchantmentStacks.Stamp stamp) { chronicle$capStamp = stamp; }

    @Inject(method={"getComponents", "getComponentChanges", "getImmutableComponents"}, at=@At("HEAD"))
    private void chronicle$normalizeStored(CallbackInfoReturnable<?> ci) {
        EnchantmentCapRuntime.access((ItemStack)(Object)this);
    }

    @Inject(method={"set(Lnet/minecraft/component/ComponentType;Ljava/lang/Object;)Ljava/lang/Object;",
            "set(Lnet/minecraft/component/Component;)Ljava/lang/Object;"},at=@At("RETURN"))
    private void chronicle$afterSet(CallbackInfoReturnable<?> ci) { EnchantmentCapRuntime.access((ItemStack)(Object)this); }

    @Inject(method="<init>(Lnet/minecraft/item/ItemConvertible;ILnet/minecraft/component/MergedComponentMap;)V",at=@At("RETURN"),require=1,allow=1)
    private void chronicle$afterConstruction(CallbackInfo ci) { EnchantmentCapRuntime.access((ItemStack)(Object)this); }

    @Inject(method={"applyChanges","applyUnvalidatedChanges","applyComponentsFrom"},at=@At("RETURN"))
    private void chronicle$afterPatch(CallbackInfo ci) { EnchantmentCapRuntime.access((ItemStack)(Object)this); }

    // Pinned MAP_CODEC and UNCOUNTED_CODEC lambdas read components directly, not through getters.
    @Inject(method="method_57385(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/component/ComponentChanges;",at=@At("HEAD"),require=1,allow=1)
    private static void chronicle$beforeCountedEncode(ItemStack stack,CallbackInfoReturnable<ComponentChanges> ci) {
        EnchantmentCapRuntime.access(stack);
    }
    @Inject(method="method_58094(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/component/ComponentChanges;",at=@At("HEAD"),require=1,allow=1)
    private static void chronicle$beforeUncountedEncode(ItemStack stack,CallbackInfoReturnable<ComponentChanges> ci) {
        EnchantmentCapRuntime.access(stack);
    }
}
