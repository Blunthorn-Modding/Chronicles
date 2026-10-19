package io.github.chronicle.core.play;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservationConfigurationTest {
    @Test
    void roundTripsAllFiniteFieldsAndNormalizesUnusedDelay() {
        ObservationConfiguration timer = new ObservationConfiguration(PlayState.Mode.TIMER, "ancient_city", "quiet_below:ready", 90);
        assertEquals(timer, ObservationConfiguration.decode(timer.encode()));
        ObservationConfiguration manual = new ObservationConfiguration(PlayState.Mode.MANUAL, "", "", 12);
        assertEquals(0, manual.delaySeconds());
        assertEquals(manual, ObservationConfiguration.decode(manual.encode()));
    }

    @Test
    void rejectsMalformedDuplicateUnknownAndModeSpecificPayloads() {
        assertThrows(IllegalArgumentException.class, () -> ObservationConfiguration.decode("{\"mode\":\"MANUAL\",\"regionId\":\"\",\"requiredFlag\":\"\",\"delaySeconds\":0,\"mode\":\"TIMER\"}"));
        assertThrows(IllegalArgumentException.class, () -> ObservationConfiguration.decode("{\"mode\":\"MANUAL\",\"regionId\":\"\",\"requiredFlag\":\"\",\"delaySeconds\":0,\"extra\":0}"));
        assertThrows(IllegalArgumentException.class, () -> ObservationConfiguration.decode("{\"mode\":\"UNKNOWN\",\"regionId\":\"\",\"requiredFlag\":\"\",\"delaySeconds\":0}"));
        assertThrows(IllegalArgumentException.class, () -> new ObservationConfiguration(PlayState.Mode.ENTRY, "", "", 0));
        assertThrows(IllegalArgumentException.class, () -> new ObservationConfiguration(PlayState.Mode.TIMER, "", "", 0));
        assertThrows(IllegalArgumentException.class, () -> new ObservationConfiguration(PlayState.Mode.CONDITION, "", "", 0));
        assertThrows(IllegalArgumentException.class, () -> new ObservationConfiguration(PlayState.Mode.MANUAL, "", "bad flag", 0));
    }
}
