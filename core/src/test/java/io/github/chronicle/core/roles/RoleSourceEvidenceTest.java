package io.github.chronicle.core.roles;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RoleSourceEvidenceTest {
    private final UUID player = UUID.randomUUID();
    private final String role = "quiet_below:researcher";
    @Test void exactAssignmentIsProvedButLaterWritesRemainUncertain() {
        String authority = RoleSourceEvidence.authority(player, 12);
        assertEquals(RoleSourceEvidence.Decision.COMMITTED, RoleSourceEvidence.inspect(authority, player, role, state(12, role)));
        assertEquals(RoleSourceEvidence.Decision.NOT_COMMITTED, RoleSourceEvidence.inspect(authority, player, role, state(11, "quiet_below:sailor")));
        assertEquals(RoleSourceEvidence.Decision.UNCERTAIN, RoleSourceEvidence.inspect(authority, player, role, state(12, "quiet_below:sailor")));
        assertEquals(RoleSourceEvidence.Decision.UNCERTAIN, RoleSourceEvidence.inspect(authority, player, role, state(13, role)));
    }
    @Test void identityAndRevisionCannotBeForged() {
        String authority = RoleSourceEvidence.authority(player, 12);
        assertThrows(IllegalArgumentException.class, () -> RoleSourceEvidence.inspect(authority, UUID.randomUUID(), role, state(12, role)));
        assertThrows(IllegalArgumentException.class, () -> RoleSourceEvidence.inspect("roleassign:" + player + ":012", player, role, state(12, role)));
        assertThrows(IllegalArgumentException.class, () -> RoleSourceEvidence.authority(player, 0));
    }
    private PlayerRoleAssignments state(long revision, String assigned) {
        return new PlayerRoleAssignments(revision, Map.of(player, assigned), Set.of(), true);
    }
}
