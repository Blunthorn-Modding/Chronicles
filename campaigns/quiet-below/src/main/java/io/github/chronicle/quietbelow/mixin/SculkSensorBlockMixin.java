package io.github.chronicle.quietbelow.mixin;

import io.github.chronicle.quietbelow.ecology.EcologyFeatureActivity;
import net.minecraft.block.BlockState;
import net.minecraft.block.SculkSensorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SculkSensorBlock.class)
abstract class SculkSensorBlockMixin {
    @Inject(method="scheduledTick",at=@At("HEAD"),cancellable=true)
    private void quietBelow$gateDelayedClick(BlockState state,ServerWorld world,BlockPos pos,
                                             net.minecraft.util.math.random.Random random,CallbackInfo ci) {
        if(EcologyFeatureActivity.blocked(world,pos)){EcologyFeatureActivity.settle(world,pos,state);ci.cancel();}
    }
    @Inject(method="setActive",at=@At("HEAD"),cancellable=true)
    private void quietBelow$gateDirectActivation(Entity entity,World world,BlockPos pos,BlockState state,
                                                int power,int frequency,CallbackInfo ci) {
        if(world instanceof ServerWorld server&&EcologyFeatureActivity.blocked(server,pos))ci.cancel();
    }
    @Inject(method="getWeakRedstonePower",at=@At("HEAD"),cancellable=true)
    private void quietBelow$gatePower(BlockState state,BlockView world,BlockPos pos,Direction direction,
                                      CallbackInfoReturnable<Integer> ci) {
        if(world instanceof ServerWorld server&&EcologyFeatureActivity.blocked(server,pos))ci.setReturnValue(0);
    }
    @Inject(method="getComparatorOutput",at=@At("HEAD"),cancellable=true)
    private void quietBelow$gateComparator(BlockState state,World world,BlockPos pos,Direction direction,
                                           CallbackInfoReturnable<Integer> ci) {
        if(world instanceof ServerWorld server&&EcologyFeatureActivity.blocked(server,pos))ci.setReturnValue(0);
    }
}
