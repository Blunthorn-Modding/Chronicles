package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.EventSources;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.WrittenBookItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

/** Only successful server-side use of an authentic managed written book establishes reading/opening. */
@Mixin(WrittenBookItem.class)
abstract class WrittenBookItemEventSourceMixin {
    @Unique private final ThreadLocal<Deque<String>> chronicle$bookUses=ThreadLocal.withInitial(LinkedList::new);
    @Inject(method="use",at=@At("HEAD"),cancellable=true,require=1)
    private void chronicle$beginRead(World world,PlayerEntity user,Hand hand,CallbackInfoReturnable<ActionResult> ci) {
        try{chronicle$bookUses.get().push(user instanceof ServerPlayerEntity player?EventSources.prepareBookOpen(player,player.getStackInHand(hand)):null);}
        catch(RuntimeException failure){if(user instanceof ServerPlayerEntity player)EventSources.fail(player.getEntityWorld().getServer(),failure);ci.setReturnValue(ActionResult.FAIL);}
    }
    @Inject(method="use",at=@At("RETURN"),require=1)
    private void chronicle$finishRead(World world,PlayerEntity user,Hand hand,CallbackInfoReturnable<ActionResult> ci) {
        var uses=chronicle$bookUses.get();if(uses.isEmpty())return;String id=uses.pop();if(uses.isEmpty())chronicle$bookUses.remove();
        if(user instanceof ServerPlayerEntity player)try{EventSources.complete(player.getEntityWorld().getServer(),id,ci.getReturnValue().isAccepted());}catch(EventSources.Unavailable held){/* Exact source preparation and hold already published. */}
    }
}
