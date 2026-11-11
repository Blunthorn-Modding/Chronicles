package io.github.chronicle.core.encounters;

import java.util.*;

/** Portable reusable composition, independent of Minecraft's server registry and map coordinates. */
public record EncounterGroup(String id, String label, String description, List<EncounterMember> members) {
    public EncounterGroup {
        id = EncounterIds.registry(id, "encounter group ID");
        label = EncounterIds.text(label, 80, "encounter group label");
        description = EncounterIds.text(description, 512, "encounter group description");
        if (label.isEmpty()) throw new IllegalArgumentException("encounter group label is required");
        if (members == null || members.isEmpty() || members.size() > 16) throw new IllegalArgumentException("encounter group must contain 1 to 16 member types");
        List<EncounterMember> copy = List.copyOf(members);
        Set<String> types = new HashSet<>(); int total = 0;
        for (EncounterMember member : copy) { if (!types.add(member.entityType())) throw new IllegalArgumentException("encounter group contains duplicate entity type: " + member.entityType()); total = Math.addExact(total, member.count()); }
        if (total > 64) throw new IllegalArgumentException("encounter group may contain at most 64 entities");
        members = copy;
    }
    public int entityCount() { return members.stream().mapToInt(EncounterMember::count).sum(); }
    public void requireAllowed(EncounterTypeAllowlist allowlist) { Objects.requireNonNull(allowlist, "encounter type allowlist is required"); for (EncounterMember member : members) if (!allowlist.allows(member.entityType())) throw new IllegalArgumentException("encounter entity type is not allowed: " + member.entityType()); }
}
