package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.SessionGameplay;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Defense for direct interaction-manager calls in addition to network admission. */
@Mixin(ServerPlayerInteractionManager.class)
abstract class PausedBlockInteractionMixin {
    @Shadow @Final protected ServerPlayerEntity player;
    @Inject(method="tryBreakBlock", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$break(BlockPos pos, CallbackInfoReturnable<Boolean> ci) {
        if (!SessionGameplay.canBuild(player)) { SessionGameplay.correctBlock(player, pos); ci.setReturnValue(false); }
    }
    @Inject(method="interactBlock", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$block(ServerPlayerEntity actor, World world, ItemStack stack, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> ci) {
        if (!SessionGameplay.canUseBlock(actor, stack, hit.getBlockPos())) ci.setReturnValue(ActionResult.FAIL);
    }
    @Inject(method="interactItem", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$item(ServerPlayerEntity actor, World world, ItemStack stack, Hand hand, CallbackInfoReturnable<ActionResult> ci) {
        if (!SessionGameplay.canUseItem(actor, stack))
            ci.setReturnValue(ActionResult.FAIL);
    }
}
