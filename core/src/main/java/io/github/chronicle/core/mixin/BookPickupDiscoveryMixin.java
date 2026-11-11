package io.github.chronicle.core.mixin;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.entity.ItemEntity.class)
abstract class BookPickupDiscoveryMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method="onPlayerCollision",at=@org.spongepowered.asm.mixin.injection.At("RETURN"),require=1)
    private void chronicle$bookReceipt(net.minecraft.entity.player.PlayerEntity player,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){if(player instanceof net.minecraft.server.network.ServerPlayerEntity actor)io.github.chronicle.core.server.BookDeliveryRuntime.observeInventory(actor);}
}
