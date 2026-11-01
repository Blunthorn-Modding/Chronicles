package io.github.chronicle.core.encounters;

import java.util.*;

/** Server runtime supplies this adapter from its MobEntity registry; the model intentionally has no Minecraft dependency. */
@FunctionalInterface public interface EncounterTypeAllowlist {
    boolean allows(String entityType);
    static EncounterTypeAllowlist only(Collection<String> entityTypes) {
        if (entityTypes == null) throw new IllegalArgumentException("allowed encounter entity types are required");
        Set<String> allowed = new HashSet<>(); for (String type : entityTypes) allowed.add(EncounterIds.registry(type, "allowed entity type"));
        return allowed::contains;
    }
}
