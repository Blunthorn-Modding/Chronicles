package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.SessionGameplay;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reject ordinary gameplay before Fabric/campaign interaction callbacks, on the server thread. */
@Mixin(ServerPlayNetworkHandler.class)
abstract class PausedInteractionsMixin {
    @Shadow public ServerPlayerEntity player;
    @Shadow public abstract void updateSequence(int sequence);
    private static final String MAIN_THREAD = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V";
    private static final String ACK = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;updateSequence(I)V";

    @Inject(method="onPlayerInteractBlock", at=@At(value="INVOKE", target=ACK, shift=At.Shift.AFTER), cancellable=true, require=1, allow=1)
    private void chronicle$block(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        if (!SessionGameplay.canUseBlock(player, player.getStackInHand(packet.getHand()), packet.getBlockHitResult().getBlockPos())) {
            SessionGameplay.correctBlock(player, packet.getBlockHitResult().getBlockPos());
            SessionGameplay.correctBlock(player, packet.getBlockHitResult().getBlockPos().offset(packet.getBlockHitResult().getSide()));
            SessionGameplay.correctInventory(player);
            ci.cancel();
        }
    }

    @Inject(method="onPlayerInteractItem", at=@At(value="INVOKE", target=ACK, shift=At.Shift.AFTER), cancellable=true, require=1, allow=1)
    private void chronicle$item(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        if (!SessionGameplay.canUseItem(player, player.getStackInHand(packet.getHand()))) {
            player.clearActiveItem();
            SessionGameplay.correctInventory(player);
            ci.cancel();
        }
    }

    @Inject(method="onPlayerAction", at=@At(value="INVOKE", target=MAIN_THREAD, shift=At.Shift.AFTER), cancellable=true, require=1, allow=1)
    private void chronicle$action(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (!SessionGameplay.canBuild(player) && packet.getAction() != PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
            if (packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                    || packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
                updateSequence(packet.getSequence());
                SessionGameplay.correctBlock(player, packet.getPos());
            }
            player.clearActiveItem();
            SessionGameplay.correctInventory(player);
            ci.cancel();
        }
    }

    @Inject(method={"onPlayerInteractEntity", "onClickSlot", "onButtonClick", "onCreativeInventoryAction",
            "onCraftRequest", "onSelectMerchantTrade", "onUpdateBeacon", "onRenameItem", "onSlotChangedState", "onBundleItemSelected"},
            at=@At(value="INVOKE", target=MAIN_THREAD, shift=At.Shift.AFTER), cancellable=true, require=1)
    private void chronicle$mutation(CallbackInfo ci) {
        if (!SessionGameplay.canBuild(player)) {
            SessionGameplay.correctInventory(player);
            ci.cancel();
        }
    }

    // These two vanilla handlers filter text asynchronously instead of enforcing a main-thread entry.
    @Inject(method="onBookUpdate", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$book(BookUpdateC2SPacket packet, CallbackInfo ci) {
        NetworkThreadUtils.forceMainThread(packet, (ServerPlayNetworkHandler)(Object)this, player.getEntityWorld());
        if (!SessionGameplay.canBuild(player)) { SessionGameplay.correctInventory(player); ci.cancel(); }
    }
    @Inject(method={"updateBookContent", "addBook"}, at=@At("HEAD"), cancellable=true, require=1)
    private void chronicle$bookCompletion(CallbackInfo ci) {
        if (!SessionGameplay.canBuild(player)) { SessionGameplay.correctInventory(player); ci.cancel(); }
    }
    @Inject(method="onUpdateSign", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$sign(UpdateSignC2SPacket packet, CallbackInfo ci) {
        NetworkThreadUtils.forceMainThread(packet, (ServerPlayNetworkHandler)(Object)this, player.getEntityWorld());
        if (!SessionGameplay.canBuild(player)) { SessionGameplay.correctBlock(player, packet.getPos()); ci.cancel(); }
    }
    @Inject(method="onSignUpdate", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$signCompletion(UpdateSignC2SPacket packet, java.util.List<net.minecraft.server.filter.FilteredMessage> text, CallbackInfo ci) {
        if (!SessionGameplay.canBuild(player)) { SessionGameplay.correctBlock(player, packet.getPos()); ci.cancel(); }
    }
}
