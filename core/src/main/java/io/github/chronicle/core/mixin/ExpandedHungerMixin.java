package io.github.chronicle.core.mixin;

import io.github.chronicle.core.stats.FoodCapacity;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only food limits/regen thresholds change. Exhaustion, timings and starvation remain native. */
@Mixin(HungerManager.class)
abstract class ExpandedHungerMixin implements FoodCapacity.Owner {
    @Unique private PlayerEntity chronicle$owner;
    @Override public void chronicle$bind(PlayerEntity player) { chronicle$owner = player; }

    @ModifyConstant(method="addInternal", constant=@Constant(intValue=20), require=1, allow=1)
    private int chronicle$eatingCapacity(int vanilla) { return FoodCapacity.capacity(chronicle$owner); }

    @ModifyConstant(method="isNotFull", constant=@Constant(intValue=20), require=1, allow=1)
    private int chronicle$fullCapacity(int vanilla) { return FoodCapacity.capacity(chronicle$owner); }

    @Inject(method="update", at=@At("HEAD"), require=1, allow=1)
    private void chronicle$actualOwner(ServerPlayerEntity player, CallbackInfo callback) {
        chronicle$owner = player;
        FoodCapacity.clamp((HungerManager)(Object)this, FoodCapacity.capacity(player));
    }

    @ModifyConstant(method="update", constant=@Constant(intValue=20), require=1, allow=1)
    private int chronicle$fastRegeneration(int vanilla) { return FoodCapacity.capacity(chronicle$owner); }

    @ModifyConstant(method="update", constant=@Constant(intValue=18), require=1, allow=1)
    private int chronicle$ordinaryRegeneration(int vanilla) { return Math.max(1, FoodCapacity.capacity(chronicle$owner) - 2); }
}
