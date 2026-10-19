package io.github.chronicle.core.spawn;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Join/phase edges only: releasing a hold must never fabricate an arrival event. */
public final class AutomaticArrivalEvents {
    private final Set<UUID> joins = new HashSet<>();
    private boolean wasActive;
    public void joined(UUID player) { joins.add(player); }
    public void disconnected(UUID player) { joins.remove(player); }
    public Set<UUID> next(boolean phaseActive, boolean held, Collection<UUID> online) {
        if (!phaseActive) { wasActive = false; joins.clear(); return Set.of(); }
        if (!wasActive) joins.addAll(online);
        wasActive = true;
        if (held) { joins.clear(); return Set.of(); }
        Set<UUID> result = Set.copyOf(joins);
        joins.clear();
        return result;
    }
}
