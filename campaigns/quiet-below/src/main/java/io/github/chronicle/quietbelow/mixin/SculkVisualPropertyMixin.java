package io.github.chronicle.quietbelow.mixin;

import io.github.chronicle.quietbelow.content.SculkAppearance;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SculkBlock;
import net.minecraft.state.StateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
abstract class SculkVisualPropertyMixin {
    @Inject(method="appendProperties",at=@At("TAIL"))
    private void quietBelow$visualProperty(StateManager.Builder<Block,BlockState> builder,CallbackInfo ci) {
        if(((Object)this).getClass()==SculkBlock.class)builder.add(SculkAppearance.VITALITY);
    }
}
