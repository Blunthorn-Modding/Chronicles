package io.github.chronicle.core.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.server.network.ServerPlayNetworkHandler.class)
abstract class BookClickDiscoveryMixin {
    @org.spongepowered.asm.mixin.Shadow public net.minecraft.server.network.ServerPlayerEntity player;
    @org.spongepowered.asm.mixin.injection.Inject(method="onClickSlot",at=@org.spongepowered.asm.mixin.injection.At("RETURN"),require=1)
    private void chronicle$bookReceipt(net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket packet,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){io.github.chronicle.core.server.BookDeliveryRuntime.observeInventory(player);}
}
