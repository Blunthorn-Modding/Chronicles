package io.github.chronicle.core.managed;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ManagedBlockLedger(long revision, int migrationVersion, List<ManagedBlockInstance> instances) {
    public static final int CURRENT_MIGRATION = 1;
    public static final int MAX_INSTANCES = 8_192;

    public ManagedBlockLedger {
        if (revision < 0) throw new IllegalArgumentException("managed ledger revision cannot be negative");
        if (migrationVersion < 0 || migrationVersion > CURRENT_MIGRATION)
            throw new IllegalArgumentException("unsupported managed migration version: " + migrationVersion);
        if (instances == null || instances.size() > MAX_INSTANCES)
            throw new IllegalArgumentException("managed ledger accepts at most " + MAX_INSTANCES + " instances");
        Set<java.util.UUID> ids = new HashSet<>();
        Set<String> positions = new HashSet<>();
        for (ManagedBlockInstance instance : instances) {
            if (instance == null || !ids.add(instance.id())) throw new IllegalArgumentException("duplicate managed instance ID");
            String position = instance.dimension() + "\0" + instance.position().x() + "," + instance.position().y() + "," + instance.position().z();
            if (!positions.add(position)) throw new IllegalArgumentException("duplicate managed instance position");
            if (instance.revision() > revision) throw new IllegalArgumentException("instance revision exceeds ledger revision");
        }
        instances = List.copyOf(instances);
    }

    public static ManagedBlockLedger empty() { return new ManagedBlockLedger(0, CURRENT_MIGRATION, List.of()); }
    public static ManagedBlockLedger unmigrated() { return new ManagedBlockLedger(0, 0, List.of()); }
}
