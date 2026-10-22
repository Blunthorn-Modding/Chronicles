package io.github.chronicle.quietbelow.mixin;

import io.github.chronicle.quietbelow.ecology.QuietBelowEcology;
import net.minecraft.block.BlockState;
import net.minecraft.block.PistonBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Legacy vanilla anchors also cannot be copied/moved by a piston before ending. */
@Mixin(PistonBlock.class)
abstract class RelicHeartPistonMixin {
    @Inject(method="isMovable",at=@At("HEAD"),cancellable=true,require=1)
    private static void quietBelow$boundHeart(BlockState state,World world,BlockPos pos,Direction direction,
                                              boolean canBreak,Direction pistonDirection,CallbackInfoReturnable<Boolean> cir){
        if(world instanceof ServerWorld server&&QuietBelowEcology.protectsRoot(server,pos))cir.setReturnValue(false);
    }
}
