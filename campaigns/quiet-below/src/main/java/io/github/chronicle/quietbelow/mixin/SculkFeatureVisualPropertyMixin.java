package io.github.chronicle.quietbelow.mixin;

import io.github.chronicle.quietbelow.content.SculkAppearance;
import net.minecraft.block.*;
import net.minecraft.state.StateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SculkCatalystBlock.class,SculkSensorBlock.class,SculkShriekerBlock.class})
abstract class SculkFeatureVisualPropertyMixin {
    @Inject(method="appendProperties",at=@At("TAIL"))
    private void quietBelow$visualProperty(StateManager.Builder<Block,BlockState> builder,CallbackInfo ci) {
        Class<?> type=getClass();
        if(type==SculkCatalystBlock.class||type==SculkSensorBlock.class||type==SculkShriekerBlock.class)
            builder.add(SculkAppearance.VITALITY);
    }
}
