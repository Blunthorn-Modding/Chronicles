package io.github.chronicle.core.mixin;

import io.github.chronicle.core.server.*;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

@Mixin(ServerWorld.class)
public abstract class EntitySpawnEventSourceMixin {
    @Unique private final Deque<Optional<String>> chronicle$spawnReceipts = new ArrayDeque<>();
    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void chronicle$prepareSpawn(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        try { chronicle$spawnReceipts.push(Optional.ofNullable(EntitySpawnEventSources.prepare((ServerWorld)(Object)this, entity))); }
        catch (EventSources.Unavailable failure) { callback.setReturnValue(false); }
    }
    @Inject(method = "spawnEntity", at = @At("RETURN"))
    private void chronicle$completeSpawn(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (!chronicle$spawnReceipts.isEmpty()) try {
            EventSources.complete(((ServerWorld)(Object)this).getServer(), chronicle$spawnReceipts.pop().orElse(null), callback.getReturnValue());
        } catch (EventSources.Unavailable failure) { /* Accepted spawn is retained; exact source recovery now holds Chronicle. */ }
    }
}
