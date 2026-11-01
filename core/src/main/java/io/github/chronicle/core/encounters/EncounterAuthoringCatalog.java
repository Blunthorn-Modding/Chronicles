package io.github.chronicle.core.encounters;

import java.util.Objects;

/** One durable world-owned publication containing portable groups and their separate map bindings. */
public record EncounterAuthoringCatalog(EncounterGroupRegistry groups, EncounterBindingRegistry bindings) {
    public EncounterAuthoringCatalog {
        Objects.requireNonNull(groups, "encounter groups are required");
        Objects.requireNonNull(bindings, "encounter bindings are required");
        for (String groupId : bindings.bindings().keySet())
            if (!groups.groups().containsKey(groupId))
                throw new IllegalArgumentException("encounter binding references a missing group: " + groupId);
    }
}
