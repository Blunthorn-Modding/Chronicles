package io.github.chronicle.core.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldAreaOperationsTest {
    @Test void unavailableCoordinatorPausesCampaignProjection() {
        assertTrue(WorldAreaOperations.hasReservation(null));
    }
}
