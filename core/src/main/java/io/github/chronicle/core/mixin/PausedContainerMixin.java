package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.SessionGameplay;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

/** Also cover server-side container opens that do not originate from a block-use packet. */
@Mixin(ServerPlayerEntity.class)
abstract class PausedContainerMixin {
    @Inject(method="openHandledScreen", at=@At("HEAD"), cancellable=true, require=1, allow=1)
    private void chronicle$closedWhilePaused(NamedScreenHandlerFactory factory, CallbackInfoReturnable<OptionalInt> callback) {
        if (!SessionGameplay.canBuild((ServerPlayerEntity)(Object)this)) callback.setReturnValue(OptionalInt.empty());
    }
}
