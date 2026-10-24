package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.SessionGameplay;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pause hazard clocks without suppressing the player tick/movement or saving ability overrides. */
@Mixin(Entity.class)
abstract class PausedHazardsMixin {
    @Inject(method={"setAir", "setFireTicks", "setFrozenTicks"}, at=@At("HEAD"), cancellable=true, require=1)
    private void chronicle$hazardClock(int value, CallbackInfo ci) {
        if ((Object)this instanceof PlayerEntity player && SessionGameplay.frozen(player)) ci.cancel();
    }
    @Inject(method="fall", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$pausedFall(double height, boolean onGround, BlockState state, BlockPos pos, CallbackInfo ci) {
        if ((Object)this instanceof PlayerEntity player && SessionGameplay.frozen(player)) {
            player.fallDistance = 0;
            ci.cancel();
        }
    }
}
