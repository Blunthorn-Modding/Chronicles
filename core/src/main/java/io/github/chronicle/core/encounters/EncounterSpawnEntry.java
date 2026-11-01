package io.github.chronicle.core.encounters;

import java.util.*;

/** Predetermined spawn identity and destination. CLAIMED is durable before any external spawn call. */
public record EncounterSpawnEntry(UUID entityId, String entityType, String dimension, double x, double y, double z, Stage stage) {
    public enum Stage { PLANNED, CLAIMED, SPAWNED }
    public EncounterSpawnEntry { Objects.requireNonNull(entityId, "entity UUID is required"); entityType=EncounterIds.registry(entityType,"entity type"); dimension=EncounterIds.registry(dimension,"destination dimension"); if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("spawn destination coordinates must be finite"); Objects.requireNonNull(stage,"spawn stage is required"); }
    public EncounterSpawnEntry claim() { if(stage!=Stage.PLANNED)throw new IllegalStateException("only planned spawn entries may be claimed"); return new EncounterSpawnEntry(entityId,entityType,dimension,x,y,z,Stage.CLAIMED); }
    public EncounterSpawnEntry spawned() { if(stage!=Stage.CLAIMED)throw new IllegalStateException("only claimed spawn entries may be marked spawned"); return new EncounterSpawnEntry(entityId,entityType,dimension,x,y,z,Stage.SPAWNED); }
}
