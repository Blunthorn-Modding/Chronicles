package io.github.chronicle.core.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepartureEntryGateTest {
    @Test void participationFailsClosedForUnavailableConcludedOrDepartedAuthority(){
        assertFalse(PlayRuntime.departureAllows(false,false,false));
        assertFalse(PlayRuntime.departureAllows(true,true,false));
        assertFalse(PlayRuntime.departureAllows(true,false,true));
        assertTrue(PlayRuntime.departureAllows(true,false,false));
    }

    @Test void onlyARealRoleAssignmentRequiresContinuingParticipation(){
        assertFalse(PlayerRoles.assignmentRequiresParticipation(""));
        assertFalse(PlayerRoles.assignmentRequiresParticipation("-"));
        assertTrue(PlayerRoles.assignmentRequiresParticipation("quiet_below:captain"));
    }
}
