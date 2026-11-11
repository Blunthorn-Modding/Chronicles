package io.github.chronicle.quietbelow.mixin;

import io.github.chronicle.quietbelow.ecology.EcologyCostEffect;
import io.github.chronicle.quietbelow.content.AltarSightEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StatusEffectInstance.class)
abstract class ContactCostInstanceMixin implements EcologyCostEffect, AltarSightEffect {
    @Shadow private int duration;
    @Shadow private StatusEffectInstance hiddenEffect;
    @Unique private boolean quietBelow$contactCost;
    @Unique private boolean quietBelow$altarSight;

    @Inject(method="<init>(Lnet/minecraft/entity/effect/StatusEffectInstance;)V",at=@At("RETURN"))
    private void quietBelow$copyProvenance(StatusEffectInstance source,CallbackInfo ci) {
        if(((EcologyCostEffect)(Object)source).quietBelow$isContactCost())quietBelow$markContactCost();
        if(((AltarSightEffect)(Object)source).quietBelow$isAltarSight())quietBelow$markAltarSight();
    }

    @Override public boolean quietBelow$isContactCost() {
        if(quietBelow$contactCost&&hiddenEffect!=null)quietBelow$yieldContactCost();
        return quietBelow$contactCost;
    }
    @Override public void quietBelow$markContactCost() {
        if(hiddenEffect!=null)throw new IllegalStateException("Contact cost cannot own a hidden effect stack");
        quietBelow$contactCost=true;
    }
    @Override public void quietBelow$yieldContactCost() {
        if(!quietBelow$contactCost)return;
        quietBelow$contactCost=false;
        // One, rather than zero, lets native hidden-effect promotion run.
        duration=1;
    }
    @Override public boolean quietBelow$isAltarSight(){if(quietBelow$altarSight&&hiddenEffect!=null)quietBelow$yieldAltarSight();return quietBelow$altarSight;}
    @Override public void quietBelow$markAltarSight(){if(hiddenEffect!=null)throw new IllegalStateException("Altar sight cannot own a hidden effect stack");quietBelow$altarSight=true;}
    @Override public void quietBelow$yieldAltarSight(){if(!quietBelow$altarSight)return;quietBelow$altarSight=false;duration=1;}
    @Inject(method="upgrade",at=@At("HEAD"))
    private void quietBelow$foreignUpgrade(StatusEffectInstance incoming,CallbackInfoReturnable<Boolean> ci) {
        if(quietBelow$contactCost&&!((EcologyCostEffect)(Object)incoming).quietBelow$isContactCost())quietBelow$yieldContactCost();
        if(quietBelow$altarSight&&!((AltarSightEffect)(Object)incoming).quietBelow$isAltarSight())quietBelow$yieldAltarSight();
    }
    @Inject(method="copyFrom",at=@At("HEAD"))
    private void quietBelow$foreignCopy(StatusEffectInstance incoming,CallbackInfo ci) {
        if(quietBelow$contactCost&&!((EcologyCostEffect)(Object)incoming).quietBelow$isContactCost())quietBelow$yieldContactCost();
        if(quietBelow$altarSight&&!((AltarSightEffect)(Object)incoming).quietBelow$isAltarSight())quietBelow$yieldAltarSight();
    }
}
