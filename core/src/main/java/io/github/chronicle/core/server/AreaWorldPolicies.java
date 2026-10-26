package io.github.chronicle.core.server;

import io.github.chronicle.core.areas.AreaBounds;
import io.github.chronicle.core.areas.AreaCell;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import java.util.LinkedHashMap;
import java.util.Map;

/** Campaign extensions for Core world operations. Implementations must not mutate during validation. */
public final class AreaWorldPolicies {
    private static final Map<String,Policy> POLICIES=new LinkedHashMap<>();
    private AreaWorldPolicies(){}
    public static synchronized void register(String id,Policy policy){
        if(id==null||id.isBlank()||policy==null||POLICIES.putIfAbsent(id,policy)!=null)
            throw new IllegalArgumentException("A unique area policy ID is required");
    }
    public static void validate(MinecraftServer server,AreaBounds bounds){for(Policy policy:POLICIES.values())policy.validate(server,bounds);}
    public static void validateCell(ServerWorld world,BlockPos pos,AreaCell before,AreaCell after){for(Policy policy:POLICIES.values())policy.validateCell(world,pos,before,after);}
    public static void durable(MinecraftServer server,AreaBounds bounds){for(Policy policy:POLICIES.values())policy.durable(server,bounds);}
    public static BlockState templateState(ServerWorld world,BlockPos pos,BlockState actual){BlockState saved=actual;for(Policy policy:POLICIES.values())saved=policy.templateState(world,pos,saved);if(saved==null||saved.getBlock()!=actual.getBlock())throw new IllegalStateException("An Area template policy changed the block type.");return saved;}
    public interface Policy {
        default void validate(MinecraftServer server,AreaBounds bounds){}
        default void validateCell(ServerWorld world,BlockPos pos,AreaCell before,AreaCell after){}
        default BlockState templateState(ServerWorld world,BlockPos pos,BlockState state){return state;}
        /** Flush campaign ownership observations before Core publishes its world receipt. Must be idempotent. */
        default void durable(MinecraftServer server,AreaBounds bounds){}
    }
}
