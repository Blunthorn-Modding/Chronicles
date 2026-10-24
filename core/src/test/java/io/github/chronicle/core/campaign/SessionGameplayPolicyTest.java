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
    @Test void pausedMapWorkRequiresBothLiveAuthorityAndExplicitEditing() {
        for (SessionPhase phase : SessionPhase.values()) {
            for (boolean operator : new boolean[]{false, true}) {
                for (boolean editing : new boolean[]{false, true}) {
                    assertEquals(phase == SessionPhase.ACTIVE || (operator && editing),
                            SessionGameplayPolicy.canBuild(phase, false, operator, editing));
                    assertFalse(SessionGameplayPolicy.canBuild(phase, true, operator, editing));
                }
            }
        }
        assertFalse(SessionGameplayPolicy.canBuild(null, false, true, true));
    }
}
