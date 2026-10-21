package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.EnchantmentCapRuntime;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PersistentProjectileEntity.class)
abstract class CappedProjectileMixin {
    @Inject(method={"getItemStack","getWeaponStack"},at=@At("RETURN"))
    private void chronicle$capProjectileData(CallbackInfoReturnable<ItemStack> ci) {
        if(ci.getReturnValue()!=null)EnchantmentCapRuntime.access(ci.getReturnValue());
    }
}
