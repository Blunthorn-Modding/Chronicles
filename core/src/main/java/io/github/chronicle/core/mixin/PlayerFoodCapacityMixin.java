package io.github.chronicle.core.mixin;

import com.mojang.authlib.GameProfile;
import io.github.chronicle.core.stats.FoodCapacity;
import io.github.chronicle.core.stats.OfferingAttributes;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
abstract class PlayerFoodCapacityMixin {
    @Shadow protected HungerManager hungerManager;

    @Inject(method="createPlayerAttributes", at=@At("RETURN"), require=1, allow=1)
    private static void chronicle$foodAttribute(CallbackInfoReturnable<DefaultAttributeContainer.Builder> callback) {
        callback.getReturnValue().add(OfferingAttributes.FOOD_CAPACITY).add(OfferingAttributes.EXHAUSTION_MULTIPLIER);
    }

    @Inject(method="<init>(Lnet/minecraft/world/World;Lcom/mojang/authlib/GameProfile;)V", at=@At("TAIL"), require=1, allow=1)
    private void chronicle$bindHunger(World world, GameProfile profile, CallbackInfo callback) {
        ((FoodCapacity.Owner) hungerManager).chronicle$bind((PlayerEntity)(Object)this);
    }

    @Inject(method="getHungerManager", at=@At("RETURN"), require=1, allow=1)
    private void chronicle$bindCurrentHunger(CallbackInfoReturnable<HungerManager> callback) {
        ((FoodCapacity.Owner) callback.getReturnValue()).chronicle$bind((PlayerEntity)(Object)this);
    }
}
