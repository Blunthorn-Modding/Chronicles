package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.SessionGameplay;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops an in-progress server-side use from ticking or completing while paused. */
@Mixin(ServerPlayerEntity.class)
abstract class PausedItemUseMixin {
    @Inject(method="tickItemStackUsage(Lnet/minecraft/item/ItemStack;)V", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$freezeUseTick(ItemStack stack, CallbackInfo callback) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        if (SessionGameplay.frozen(player)) {
            player.clearActiveItem();
            player.playerScreenHandler.syncState();
            player.currentScreenHandler.syncState();
            callback.cancel();
        }
    }

    @Inject(method="consumeItem()V", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$freezeUseCompletion(CallbackInfo callback) {
        if (SessionGameplay.frozen((ServerPlayerEntity)(Object)this)) callback.cancel();
    }
}
