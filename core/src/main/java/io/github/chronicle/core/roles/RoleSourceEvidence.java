package io.github.chronicle.core.roles;

import java.util.Objects;
import java.util.UUID;

/** Exact revision proof for one accepted assignment; later unrelated writes never imply success. */
public final class RoleSourceEvidence {
    private RoleSourceEvidence() { }
    public enum Decision { COMMITTED, NOT_COMMITTED, UNCERTAIN }

    public static String authority(UUID player, long expectedRevision) {
        Objects.requireNonNull(player, "assigned player is required");
        if (expectedRevision < 1) throw new IllegalArgumentException("assignment revision must be positive");
        return "roleassign:" + player + ":" + expectedRevision;
    }

    public static Decision inspect(String authority, UUID actor, String roleId, PlayerRoleAssignments assignments) {
        if (authority == null || actor == null || roleId == null || assignments == null)
            throw new IllegalArgumentException("complete assignment evidence is required");
        String[] parts = authority.split(":", -1);
        if (parts.length != 3 || !parts[0].equals("roleassign")) throw new IllegalArgumentException("invalid assignment authority");
        UUID player = UUID.fromString(parts[1]);
        long revision = Long.parseLong(parts[2]);
        if (!authority.equals(authority(player, revision)) || !player.equals(actor))
            throw new IllegalArgumentException("assignment authority identity mismatch");
        if (assignments.revision() == revision && roleId.equals(assignments.assigned().get(player)))
            return Decision.COMMITTED;
        if (assignments.revision() == revision - 1) return Decision.NOT_COMMITTED;
        return Decision.UNCERTAIN;
    }
}
