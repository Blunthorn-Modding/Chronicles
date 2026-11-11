package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.SessionGameplay;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemEntity.class, ExperienceOrbEntity.class})
abstract class PausedPickupMixin {
    @Inject(method="onPlayerCollision", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$pickup(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity actor && ((Object)this instanceof ExperienceOrbEntity
                ? SessionGameplay.frozen(actor) : !SessionGameplay.canBuild(actor))) ci.cancel();
    }
}
