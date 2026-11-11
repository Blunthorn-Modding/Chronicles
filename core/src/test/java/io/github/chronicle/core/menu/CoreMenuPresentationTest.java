package io.github.chronicle.core.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreMenuPresentationTest {
    @Test
    void cleanViewHidesEveryFact() {
        assertFalse(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Authority", "world/chronicle/example.json"), false));
        assertFalse(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Capacity", "1 / 1 - Limit reached"), false));
        assertFalse(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Status", "Missing"), false));
        assertFalse(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Organizer note", "Keep this visible elsewhere"), false));
    }

    @Test
    void debugViewShowsEveryFactWithoutLabelClassification() {
        assertTrue(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Organizer note", "authority purchases revision"), true));
        assertTrue(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Authority", "world/chronicle/example.json"), true));
        assertTrue(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Capacity", "1 / 1 - Limit reached"), true));
    }

    @Test
    void cleanFooterSuppressesOnlyRoutineIdleAndAcknowledgementText() {
        assertFalse(CoreMenuPresentation.showFooterMessage("", false, false));
        assertFalse(CoreMenuPresentation.showFooterMessage("Server state updated.", false, false));
        assertTrue(CoreMenuPresentation.showFooterMessage("Saved loot table.", false, false));
        assertTrue(CoreMenuPresentation.showFooterMessage("Waiting for server...", true, false));
        assertTrue(CoreMenuPresentation.showFooterMessage("", false, true));
    }
    @Test
    void ordinaryRoundTripsDoNotFlashWaitingFeedbackButSlowRequestsAndDebugDo() {
        assertFalse(CoreMenuPresentation.showPendingWait(true, 0, false));
        assertFalse(CoreMenuPresentation.showPendingWait(true, 3, false));
        assertTrue(CoreMenuPresentation.showPendingWait(true, 4, false));
        assertTrue(CoreMenuPresentation.showPendingWait(true, 200, false));
        assertTrue(CoreMenuPresentation.showPendingWait(true, 0, true));
        assertFalse(CoreMenuPresentation.showPendingWait(false, 200, true));
    }

}
