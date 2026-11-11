package io.github.chronicle.core.roles;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Startup registry for campaign defaults; config materialization is deliberately separate. */
public final class PlayerRoleCatalog {
    private final TreeMap<String, PlayerRole> roles = new TreeMap<>();
    private boolean frozen;
    public synchronized void register(PlayerRole role) {
        if (frozen) throw new IllegalStateException("Player Role catalog is frozen");
        if (role == null) throw new IllegalArgumentException("Player Role is required");
        if (roles.size() >= PlayerRole.MAX_ROLES) throw new IllegalArgumentException("Player Role catalog accepts at most " + PlayerRole.MAX_ROLES + " roles");
        if (roles.putIfAbsent(role.id(), role) != null) throw new IllegalArgumentException("duplicate Player Role: " + role.id());
    }
    public synchronized void registerYaml(String sourceName, String yaml) {
        if (sourceName == null || sourceName.isBlank()) throw new IllegalArgumentException("role YAML source name is required");
        for (PlayerRole role : PlayerRoleConfig.decode(yaml).values()) register(role);
    }
    public synchronized void freeze() { frozen = true; }
    public synchronized Map<String, PlayerRole> roles() { return Collections.unmodifiableMap(new TreeMap<>(roles)); }
}
