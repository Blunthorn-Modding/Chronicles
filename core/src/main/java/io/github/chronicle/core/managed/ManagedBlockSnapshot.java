package io.github.chronicle.core.managed;

import java.util.List;

public record ManagedBlockSnapshot(long instanceRevision, long policyRevision, long statusRevision,
                                   boolean ready, String problem, List<ManagedBlockTypeView> types,
                                   List<ManagedBlockInstanceView> instances) {
    public ManagedBlockSnapshot {
        problem = problem == null ? "" : problem;
        types = List.copyOf(types);
        instances = List.copyOf(instances);
    }
}
