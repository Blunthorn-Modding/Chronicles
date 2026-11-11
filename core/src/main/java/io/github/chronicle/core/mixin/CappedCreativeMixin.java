package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.EnchantmentCapRuntime;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
abstract class CappedCreativeMixin {
    @Shadow public ServerPlayerEntity player;
    @Unique private boolean chronicle$creativeChanged;
    @Inject(method="onCreativeInventoryAction",at=@At(value="INVOKE",target="Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",shift=At.Shift.AFTER))
    private void chronicle$capIncoming(CreativeInventoryActionC2SPacket packet,CallbackInfo ci) {
        chronicle$creativeChanged=EnchantmentCapRuntime.normalize(player.getEntityWorld().getServer(),packet.stack());
    }
    @Inject(method="onCreativeInventoryAction",at=@At("RETURN"))
    private void chronicle$correctPrediction(CreativeInventoryActionC2SPacket packet,CallbackInfo ci) {
        if (chronicle$creativeChanged) { chronicle$creativeChanged=false;player.playerScreenHandler.syncState(); }
    }
}
