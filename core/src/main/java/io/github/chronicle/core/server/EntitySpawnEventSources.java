package io.github.chronicle.core.server;

import io.github.chronicle.core.play.EventSource;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import java.util.*;

/** Fresh spawn acceptance only. Loading an entity from a saved chunk never calls this source. */
public final class EntitySpawnEventSources {
    public static final String TYPE = "chronicle_core:entity_spawned";
    private static final ThreadLocal<UUID> ENCOUNTER_SPAWN = new ThreadLocal<>();
    private static final Set<String> ENCOUNTER_HOLDS = Set.of(EventRuntime.HOLD,Encounters.HOLD,EventWorldBatches.HOLD,"chronicle_core:chests");
    private EntitySpawnEventSources() { }
    public static void register() {
        EventSources.registerProvider(TYPE, "Entity spawned", "An entity was newly spawned during occupied active play; saved entity loading does not count.",
                server -> Registries.ENTITY_TYPE.getIds().stream().sorted().map(id -> new EventSources.Choice(id.toString(), id.getPath().replace('_', ' '), "Fresh spawn")).toList(), List.of(), false);
        // Spawn success has no durable vanilla transaction receipt. Interrupted preparations require an explicit GM decision.
        EventSources.registerAuthority("entityspawn:", (server, receipt) -> false, true);
    }
    public static String prepare(ServerWorld world, Entity entity) {
        var server = world.getServer();
        if (!EventSources.activeOperation(server)) return null;
        if (RuntimeHolds.reason(server) != null && (!entity.getUuid().equals(ENCOUNTER_SPAWN.get())
                || RuntimeHolds.heldByOthers(server,ENCOUNTER_HOLDS))) return null;
        String target = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
        String authority = "entityspawn:" + UUID.randomUUID();
        return EventSources.prepare(server, EventSources.id(authority, TYPE, target, ""), new EventSource(TYPE, target, ""), null, true, authority);
    }
    /** Exact trusted spawn only; no hold is lifted, and the context cannot leak to later vanilla spawns. */
    static boolean spawnEncounter(ServerWorld world,Entity entity) {
        UUID previous=ENCOUNTER_SPAWN.get();ENCOUNTER_SPAWN.set(entity.getUuid());
        try { return world.spawnEntity(entity); }
        finally { if(previous==null)ENCOUNTER_SPAWN.remove();else ENCOUNTER_SPAWN.set(previous); }
    }
}
