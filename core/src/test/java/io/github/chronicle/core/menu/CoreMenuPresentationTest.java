package io.github.chronicle.core.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreMenuPresentationTest {
    @Test
    void cleanViewHidesOnlyExplicitTechnicalLabels() {
        assertFalse(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Authority", "world/chronicle/example.json"), false));
        assertFalse(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Purchases", "3"), false));
        assertFalse(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Registered block", "quiet_below:ancient_altar"), false));
        assertTrue(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Capacity", "1 / 1 - Limit reached"), false));
        assertTrue(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Status", "Missing"), false));
        assertTrue(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Status", "Unloaded"), false));
        assertTrue(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Status", "Changed"), false));
    }

    @Test
    void unknownLabelsAndDebugFactsStayVisibleWithoutValueHeuristics() {
        assertTrue(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Organizer note", "authority purchases revision"), false));
        assertTrue(CoreMenuPresentation.showFact(new CoreMenuModel.Fact("Authority", "world/chronicle/example.json"), true));
        assertFalse(CoreMenuPresentation.technicalFactLabel("Capacity"));
        assertFalse(CoreMenuPresentation.technicalFactLabel("Manual reference"));
        assertTrue(CoreMenuPresentation.technicalFactLabel("Sound ID"));
        assertTrue(CoreMenuPresentation.technicalFactLabel("Work bounds"));
    }

    @Test
    void cleanFooterSuppressesOnlyRoutineIdleAndAcknowledgementText() {
        assertFalse(CoreMenuPresentation.showFooterMessage("", false, false));
        assertFalse(CoreMenuPresentation.showFooterMessage("Server state updated.", false, false));
        assertTrue(CoreMenuPresentation.showFooterMessage("Saved loot table.", false, false));
        assertTrue(CoreMenuPresentation.showFooterMessage("Waiting for server...", true, false));
        assertTrue(CoreMenuPresentation.showFooterMessage("", false, true));
    }
}
