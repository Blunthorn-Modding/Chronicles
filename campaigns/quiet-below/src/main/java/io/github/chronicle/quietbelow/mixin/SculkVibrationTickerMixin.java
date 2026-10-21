package io.github.chronicle.quietbelow.mixin;

import io.github.chronicle.quietbelow.ecology.EcologyFeatureActivity;
import net.minecraft.world.World;
import net.minecraft.world.event.Vibrations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Vibrations.Ticker.class)
interface SculkVibrationTickerMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private static void quietBelow$discardInactiveVibration(World world,Vibrations.ListenerData data,
                                                           Vibrations.Callback receiver,CallbackInfo ci) {
        if(EcologyFeatureActivity.suppressTick(world,data,receiver))ci.cancel();
    }
}
