package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.server.PlayRuntime;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Short native pulses only; never replaces or removes an existing effect. */
final class EcologyCostsWorld implements EcologyCosts.World {
    private final MinecraftServer server;
    private final ServerWorld dimension;
    private final EcologyEngine engine;
    private final EcologyCosts costs;
    private final BooleanSupplier permitted;
    EcologyCostsWorld(MinecraftServer server,ServerWorld dimension,EcologyEngine engine,EcologyCosts costs,BooleanSupplier permitted){
        this.server=server;this.dimension=dimension;this.engine=engine;this.costs=costs;this.permitted=permitted;
    }
    private List<ServerPlayerEntity> players(){
        return PlayRuntime.eligiblePlayers(server).stream().filter(p->p.getEntityWorld()==dimension&&!p.isCreative()).toList();
    }
    @Override public List<EcologyCosts.Player> eligiblePlayers(){
        return players().stream().map(p->new EcologyCosts.Player(p.getUuid(),position(p))).toList();
    }
    @Override public boolean apply(UUID id,EcologyCosts.Kind kind){
        var player=server.getPlayerManager().getPlayer(id);
        var rule=kind==EcologyCosts.Kind.HUNGER?costs.rules().hunger():costs.rules().darkness();
        if(player==null||!permitted.getAsBoolean()||!rule.enabled()||!costs.rules().equals(engine.costRules())
                ||!engine.costsAvailable()||kind==EcologyCosts.Kind.DARKNESS&&!engine.extensive()
                ||players().stream().noneMatch(p->p==player)||!EcologyCosts.dense(engine,position(player)))return false;
        var effect=kind==EcologyCosts.Kind.HUNGER?StatusEffects.HUNGER:StatusEffects.DARKNESS;
        if(player.hasStatusEffect(effect))return false;
        return player.addStatusEffect(new StatusEffectInstance(effect,kind==EcologyCosts.Kind.HUNGER?100:80,0,false,false,true));
    }
    private static EcologyTerrain.Pos position(ServerPlayerEntity p){
        var at=p.getBlockPos();return new EcologyTerrain.Pos(at.getX(),at.getY(),at.getZ());
    }
}
