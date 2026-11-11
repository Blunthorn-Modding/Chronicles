package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.EventSources;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

/** Observe the authoritative manager result, after all vanilla permission and block/item work. */
@Mixin(ServerPlayerInteractionManager.class)
abstract class ServerPlayerInteractionManagerEventSourceMixin {
    @Shadow @Final protected ServerPlayerEntity player;
    @Unique private final Deque<EventSources.BlockAttempt> chronicle$blocks=new LinkedList<>();
    @Unique private final Deque<List<String>> chronicle$breaks=new LinkedList<>();
    @Inject(method="interactBlock",at=@At("HEAD"),cancellable=true,require=1)
    private void chronicle$beginBlock(ServerPlayerEntity actor,World world,ItemStack stack,Hand hand,BlockHitResult hit,CallbackInfoReturnable<ActionResult> ci) {
        try{chronicle$blocks.push(world instanceof ServerWorld serverWorld?EventSources.beginBlock(actor,serverWorld,stack,hit):null);}
        catch(RuntimeException failure){EventSources.fail(actor.getEntityWorld().getServer(),failure);ci.setReturnValue(ActionResult.FAIL);}
    }
    @Inject(method="interactBlock",at=@At("RETURN"),require=1)
    private void chronicle$endBlock(ServerPlayerEntity actor,World world,ItemStack stack,Hand hand,BlockHitResult hit,CallbackInfoReturnable<ActionResult> ci) {
        if(!chronicle$blocks.isEmpty()&&world instanceof ServerWorld serverWorld)try{EventSources.finishBlock(actor,serverWorld,chronicle$blocks.pop(),ci.getReturnValue());}catch(EventSources.Unavailable held){/* Exact source preparation and hold already published. */}
    }
    @Inject(method="tryBreakBlock",at=@At("HEAD"),cancellable=true,require=1)
    private void chronicle$beginBreak(BlockPos position,CallbackInfoReturnable<Boolean> ci) {
        try{chronicle$breaks.push(EventSources.active(player)?EventSources.prepareTargets(player,"block_broken",EventSources.blockTargets(player.getEntityWorld(),position)):List.of());}
        catch(RuntimeException failure){EventSources.fail(player.getEntityWorld().getServer(),failure);ci.setReturnValue(false);}
    }
    @Inject(method="tryBreakBlock",at=@At("RETURN"),require=1)
    private void chronicle$endBreak(BlockPos position,CallbackInfoReturnable<Boolean> ci) {
        if(!chronicle$breaks.isEmpty())try{for(String id:chronicle$breaks.pop())EventSources.complete(player.getEntityWorld().getServer(),id,ci.getReturnValue());}catch(EventSources.Unavailable held){/* Exact source preparation and hold already published. */}
    }
}
