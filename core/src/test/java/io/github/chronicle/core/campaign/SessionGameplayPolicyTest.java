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
    @Test void buildingRunsTheWorldWithoutReleasingSurvivalAndHoldsTakePriority() {
        for (SessionPhase phase : SessionPhase.values()) {
            assertEquals(phase != SessionPhase.ACTIVE, SessionGameplayPolicy.simulationFrozen(phase, false, false));
            assertFalse(SessionGameplayPolicy.simulationFrozen(phase, false, true));
            for (boolean building : new boolean[]{false, true})
                assertTrue(SessionGameplayPolicy.simulationFrozen(phase, true, building));
            assertEquals(phase != SessionPhase.ACTIVE, SessionGameplayPolicy.frozen(phase, false));
        }
        assertTrue(SessionGameplayPolicy.simulationFrozen(null, false, true));
        assertTrue(SessionGameplayPolicy.simulationFrozen(null, true, true));
    }
}
