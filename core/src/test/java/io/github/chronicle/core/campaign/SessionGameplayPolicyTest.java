package io.github.chronicle.core.campaign;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionGameplayPolicyTest {
    @Test void activeIsTheOnlyUnheldPlayableState() {
        for (SessionPhase phase : SessionPhase.values()) {
            assertEquals(phase != SessionPhase.ACTIVE, SessionGameplayPolicy.frozen(phase, false));
            assertTrue(SessionGameplayPolicy.frozen(phase, true));
        }
        assertTrue(SessionGameplayPolicy.frozen(null, false));
        assertTrue(SessionGameplayPolicy.frozen(null, true));
    }
    @Test void globalBuildingModeIsIndependentOfPlayerAuthorityAndNeverUnfreezesSurvival() {
        for (SessionPhase phase : SessionPhase.values()) {
            for (boolean building : new boolean[]{false, true}) {
                assertEquals(phase == SessionPhase.ACTIVE || building,
                        SessionGameplayPolicy.canBuild(phase, false, building));
                assertFalse(SessionGameplayPolicy.canBuild(phase, true, building));
                assertEquals(phase != SessionPhase.ACTIVE, SessionGameplayPolicy.frozen(phase, false));
            }
        }
        assertFalse(SessionGameplayPolicy.canBuild(null, false, true));
    }
}
