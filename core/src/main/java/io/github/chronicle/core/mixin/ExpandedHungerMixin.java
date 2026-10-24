package io.github.chronicle.core.mixin;

import io.github.chronicle.core.stats.FoodCapacity;
import io.github.chronicle.core.stats.OfferingAttributes;
import io.github.chronicle.core.stats.OfferingExhaustion;
import io.github.chronicle.core.server.SessionGameplay;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native hunger mechanics; source-owned capacity/regen and positive exhaustion price only. */
@Mixin(HungerManager.class)
abstract class ExpandedHungerMixin implements FoodCapacity.Owner {
    @Unique private PlayerEntity chronicle$owner;
    @Override public void chronicle$bind(PlayerEntity player) { chronicle$owner = player; }

    @ModifyVariable(method="addExhaustion",at=@At("HEAD"),argsOnly=true,ordinal=0,require=1,allow=1)
    private float chronicle$exhaustionPrice(float amount) {
        if (chronicle$owner != null && SessionGameplay.frozen(chronicle$owner)) return 0.0F;
        if(!(chronicle$owner instanceof ServerPlayerEntity player)||player.isCreative()||player.isSpectator())return amount;
        return OfferingExhaustion.scale(amount,player.getAttributeValue(OfferingAttributes.EXHAUSTION_MULTIPLIER));
    }

    @ModifyConstant(method="addInternal", constant=@Constant(intValue=20), require=1, allow=1)
    private int chronicle$eatingCapacity(int vanilla) { return FoodCapacity.capacity(chronicle$owner); }

    @ModifyConstant(method="isNotFull", constant=@Constant(intValue=20), require=1, allow=1)
    private int chronicle$fullCapacity(int vanilla) { return FoodCapacity.capacity(chronicle$owner); }

    @Inject(method="update", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$actualOwner(ServerPlayerEntity player, CallbackInfo callback) {
        chronicle$owner = player;
        if (SessionGameplay.frozen(player)) {
            callback.cancel();
            return;
        }
        FoodCapacity.clamp((HungerManager)(Object)this, FoodCapacity.capacity(player));
    }

    @ModifyConstant(method="update", constant=@Constant(intValue=20), require=1, allow=1)
    private int chronicle$fastRegeneration(int vanilla) { return FoodCapacity.capacity(chronicle$owner); }

    @ModifyConstant(method="update", constant=@Constant(intValue=18), require=1, allow=1)
    private int chronicle$ordinaryRegeneration(int vanilla) { return Math.max(1, FoodCapacity.capacity(chronicle$owner) - 2); }
}
