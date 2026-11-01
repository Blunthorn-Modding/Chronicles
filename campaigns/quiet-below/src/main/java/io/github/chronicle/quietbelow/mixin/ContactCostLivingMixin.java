package io.github.chronicle.quietbelow.mixin;

import io.github.chronicle.quietbelow.ecology.EcologyCostEffects;
import io.github.chronicle.quietbelow.content.TemporaryAltarOfferings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class ContactCostLivingMixin {
    @Inject(method="addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",at=@At("HEAD"))
    private void quietBelow$preserveIncoming(StatusEffectInstance incoming,Entity source,CallbackInfoReturnable<Boolean> ci) {
        if((Object)this instanceof ServerPlayerEntity player){EcologyCostEffects.beforeForeignAddition(player,incoming);TemporaryAltarOfferings.beforeForeignAddition(player,incoming);}
    }
    @ModifyArg(method="writeCustomData",index=2,
            at=@At(value="INVOKE",target="Lnet/minecraft/storage/WriteView;put(Ljava/lang/String;Lcom/mojang/serialization/Codec;Ljava/lang/Object;)V"),
            slice=@Slice(from=@At(value="CONSTANT",args="stringValue=active_effects"),to=@At(value="CONSTANT",args="stringValue=FallFlying")),require=1,allow=1)
    private Object quietBelow$omitTransientCosts(Object value) {
        return (Object)this instanceof ServerPlayerEntity?TemporaryAltarOfferings.persistentEffectData(EcologyCostEffects.persistentEffectData(value)):value;
    }
}
