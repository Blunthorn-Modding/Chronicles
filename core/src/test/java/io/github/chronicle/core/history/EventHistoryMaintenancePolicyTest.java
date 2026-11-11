package io.github.chronicle.core.history;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventHistoryMaintenancePolicyTest {
    private static final EventHistoryMaintenanceConfig CONFIG = new EventHistoryMaintenanceConfig(true, 60, 300);

    @Test void startsQuietWindowWithoutStartupFiringAndSpacesAttempts() {
        var policy = new EventHistoryMaintenancePolicy();
        assertFalse(policy.observe(0, CONFIG, true, true).attempt());
        assertFalse(policy.observe(1_199, CONFIG, true, true).attempt());
        assertTrue(policy.observe(1_200, CONFIG, true, true).attempt());
        assertFalse(policy.observe(1_220, CONFIG, true, true).attempt());
        assertTrue(policy.observe(7_200, CONFIG, true, true).attempt());
    }

    @Test void joinDiscardsQuietCredit() { assertEligibilityReset(600); }
    @Test void activePlayDiscardsQuietCredit() { assertEligibilityReset(600); }
    @Test void runtimeHoldDiscardsQuietCredit() { assertEligibilityReset(600); }

    @Test void disabledAndNoWorkNeverAccumulateAStaleAttempt() {
        var policy = new EventHistoryMaintenancePolicy();
        policy.observe(0, CONFIG, true, false);
        assertFalse(policy.observe(10_000, CONFIG, true, true).attempt());
        var disabled = new EventHistoryMaintenanceConfig(false, 60, 300);
        assertFalse(policy.observe(20_000, disabled, true, true).attempt());
        assertFalse(policy.observe(21_200, CONFIG, true, true).attempt());
    }

    @Test void resetClearsBothQuietAndAttemptSpacing() {
        var policy = new EventHistoryMaintenancePolicy();
        policy.observe(0, CONFIG, true, true);
        assertTrue(policy.observe(1_200, CONFIG, true, true).attempt());
        policy.reset();
        assertFalse(policy.observe(1_201, CONFIG, true, true).attempt());
        assertTrue(policy.observe(2_401, CONFIG, true, true).attempt());
    }

    @Test void briefActivityBetweenCoarsePollsPreservesAttemptSpacing(){
        var policy=new EventHistoryMaintenancePolicy();policy.observe(0,CONFIG,true,true);assertTrue(policy.observe(1200,CONFIG,true,true).attempt());policy.interruptQuiet();assertFalse(policy.observe(1220,CONFIG,true,true).attempt());assertFalse(policy.observe(2420,CONFIG,true,true).attempt());assertTrue(policy.observe(7200,CONFIG,true,true).attempt());
    }

    private static void assertEligibilityReset(long interruptionTick) {
        var policy = new EventHistoryMaintenancePolicy();
        policy.observe(0, CONFIG, true, true);
        // Scheduler maps a join, ACTIVE phase, and any RuntimeHold to the same ineligible input.
        policy.observe(interruptionTick, CONFIG, false, true);
        assertFalse(policy.observe(1_200, CONFIG, true, true).attempt());
        assertTrue(policy.observe(2_400, CONFIG, true, true).attempt());
    }
}
