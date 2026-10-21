package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.server.PlayRuntime;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Native health only: never changes status effects, food, maximum health, terrain or chunk tickets. */
final class EcologyRecoveryWorld implements EcologyRecovery.World {
    private final MinecraftServer server;
    private final ServerWorld dimension;
    private final EcologyEngine engine;
    private final EcologyRecovery recovery;
    private final BooleanSupplier permitted;
    EcologyRecoveryWorld(MinecraftServer server,ServerWorld dimension,EcologyEngine engine,EcologyRecovery recovery,BooleanSupplier permitted){
        this.server=server;this.dimension=dimension;this.engine=engine;this.recovery=recovery;this.permitted=permitted;
    }
    private List<ServerPlayerEntity> players(){
        return PlayRuntime.eligiblePlayers(server).stream().filter(p->p.getEntityWorld()==dimension&&!p.isCreative()).toList();
    }
    @Override public List<EcologyRecovery.Player> eligiblePlayers(){
        return players().stream().map(p->new EcologyRecovery.Player(p.getUuid(),position(p))).toList();
    }
    @Override public boolean needsHealing(UUID id){
        var p=server.getPlayerManager().getPlayer(id);return p!=null&&p.isAlive()&&p.getHealth()>0&&p.getHealth()<p.getMaxHealth();
    }
    @Override public boolean heal(UUID id,float points){
        var p=server.getPlayerManager().getPlayer(id);
        if(points!=1F||p==null||!permitted.getAsBoolean()||!recovery.rules().enabled()||!engine.recoveryAvailable()
                ||!needsHealing(id)||players().stream().noneMatch(player->player==p)||!recovery.nearLiving(engine,position(p)))return false;
        float before=p.getHealth();p.heal(points);return p.getHealth()>before;
    }
    private static EcologyTerrain.Pos position(ServerPlayerEntity p){
        var at=p.getBlockPos();return new EcologyTerrain.Pos(at.getX(),at.getY(),at.getZ());
    }
}
