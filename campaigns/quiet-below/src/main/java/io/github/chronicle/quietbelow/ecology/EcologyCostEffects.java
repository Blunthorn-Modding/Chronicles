package io.github.chronicle.quietbelow.ecology;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;
import java.util.function.BiPredicate;

/** Own effects never merge with independent effects and never enter player saves. Server-thread only. */
public final class EcologyCostEffects {
    private static final Map<MinecraftServer,Map<UUID,EnumMap<EcologyCosts.Kind,StatusEffectInstance>>> OWNED=new IdentityHashMap<>();
    private EcologyCostEffects() { }
    private static RegistryEntry<StatusEffect> type(EcologyCosts.Kind kind){return kind==EcologyCosts.Kind.HUNGER?StatusEffects.HUNGER:StatusEffects.DARKNESS;}
    private static EcologyCosts.Kind kind(RegistryEntry<StatusEffect> effect){return effect.equals(StatusEffects.HUNGER)?EcologyCosts.Kind.HUNGER:effect.equals(StatusEffects.DARKNESS)?EcologyCosts.Kind.DARKNESS:null;}
    public static boolean owned(StatusEffectInstance effect){return effect!=null&&((EcologyCostEffect)(Object)effect).quietBelow$isContactCost();}

    static void synchronize(MinecraftServer server,Map<UUID,Set<EcologyCosts.Kind>> active,BiPredicate<UUID,EcologyCosts.Kind> permitted){
        var tracked=OWNED.computeIfAbsent(server,ignored->new LinkedHashMap<>());
        for(var playerEntry:new ArrayList<>(tracked.entrySet())){
            var player=server.getPlayerManager().getPlayer(playerEntry.getKey());
            for(var entry:new ArrayList<>(playerEntry.getValue().entrySet())){
                boolean wanted=active.getOrDefault(playerEntry.getKey(),Set.of()).contains(entry.getKey())&&permitted.test(playerEntry.getKey(),entry.getKey());
                if(player==null||player.getStatusEffect(type(entry.getKey()))!=entry.getValue()||!owned(entry.getValue()))playerEntry.getValue().remove(entry.getKey());
                else if(!wanted){player.removeStatusEffect(type(entry.getKey()));playerEntry.getValue().remove(entry.getKey());}
            }
            if(playerEntry.getValue().isEmpty())tracked.remove(playerEntry.getKey());
        }
        for(var entry:active.entrySet())for(var effectKind:entry.getValue()){
            if(!permitted.test(entry.getKey(),effectKind))continue;
            var player=server.getPlayerManager().getPlayer(entry.getKey());if(player==null)continue;
            if(player.hasStatusEffect(type(effectKind)))continue;
            if(!tracked.containsKey(entry.getKey())&&tracked.size()>=EcologyCosts.MAX_PLAYERS)throw new IllegalStateException("Contact-cost ownership capacity reached");
            var effect=new StatusEffectInstance(type(effectKind),StatusEffectInstance.INFINITE,0,false,false,true);
            ((EcologyCostEffect)(Object)effect).quietBelow$markContactCost();
            if(player.addStatusEffect(effect)&&player.getStatusEffect(type(effectKind))==effect)
                tracked.computeIfAbsent(entry.getKey(),ignored->new EnumMap<>(EcologyCosts.Kind.class)).put(effectKind,effect);
        }
    }
    /** Runs before vanilla can merge an independent effect into our own active instance. */
    public static void beforeForeignAddition(ServerPlayerEntity player,StatusEffectInstance incoming){
        if(owned(incoming))return;
        var effectKind=kind(incoming.getEffectType());if(effectKind==null)return;
        var tracked=OWNED.get(player.getEntityWorld().getServer());if(tracked==null)return;
        var effects=tracked.get(player.getUuid());if(effects==null)return;
        var ours=effects.get(effectKind);
        if(ours!=null&&player.getStatusEffect(type(effectKind))==ours&&owned(ours))player.removeStatusEffect(type(effectKind));
        effects.remove(effectKind);if(effects.isEmpty())tracked.remove(player.getUuid());
    }
    /** Filters the serialized snapshot, never the live active-effect map. */
    public static Object persistentEffectData(Object value){
        if(!(value instanceof List<?> list))throw new IllegalStateException("Native active-effects serialization changed");
        return list.stream().filter(item->!(item instanceof StatusEffectInstance effect)||!owned(effect)).toList();
    }
    public static void clear(MinecraftServer server){synchronize(server,Map.of(),(id,kind)->false);}
    public static void forget(MinecraftServer server){clear(server);OWNED.remove(server);}
    public static void disconnect(ServerPlayerEntity player){
        var tracked=OWNED.get(player.getEntityWorld().getServer());if(tracked==null)return;
        var effects=tracked.remove(player.getUuid());if(effects==null)return;
        for(var entry:effects.entrySet())if(player.getStatusEffect(type(entry.getKey()))==entry.getValue()&&owned(entry.getValue()))player.removeStatusEffect(type(entry.getKey()));
    }
    /** Alive native respawns copy effect instances; copied contact costs must remain transient. */
    public static void withdrawMarked(ServerPlayerEntity player){
        for(var effectKind:EcologyCosts.Kind.values())if(owned(player.getStatusEffect(type(effectKind))))player.removeStatusEffect(type(effectKind));
    }
}
