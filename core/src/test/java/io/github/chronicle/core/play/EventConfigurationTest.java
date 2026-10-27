package io.github.chronicle.core.play;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventConfigurationTest {
    @Test
    void normalizesUnusedDelayAndAcceptsIncompleteDisabledDefaults() {
        EventDefinition participants = definition(ObservationCatalog.Audience.PARTICIPANTS, Optional.empty());
        EventConfiguration manual = new EventConfiguration(participants, PlayState.Mode.MANUAL, Optional.empty(),
                12, 99, "", true);
        assertEquals(0, manual.delayTicks());

        EventDefinition unconfiguredRegionAudience = definition(ObservationCatalog.Audience.REGION, Optional.empty());
        new EventConfiguration(unconfiguredRegionAudience, PlayState.Mode.ENTRY, Optional.empty(), 0, 0, "", false);
        new EventConfiguration(participants, PlayState.Mode.CONDITION, Optional.empty(), 0, 0, "", false);
    }

    @Test
    void enforcesTimeBoundsAndTimerDelay() {
        EventDefinition definition = definition(ObservationCatalog.Audience.PARTICIPANTS, Optional.empty());
        EventConfiguration timer = new EventConfiguration(definition, PlayState.Mode.TIMER, Optional.empty(),
                5, EventConfiguration.MAX_DELAY_TICKS, "", true);
        assertEquals(EventConfiguration.MAX_DELAY_TICKS, timer.delayTicks());
        assertThrows(IllegalArgumentException.class, () -> new EventConfiguration(definition, PlayState.Mode.MANUAL,
                Optional.empty(), -1, 0, "", false));
        assertThrows(IllegalArgumentException.class, () -> new EventConfiguration(definition, PlayState.Mode.MANUAL,
                Optional.empty(), 0, -1, "", false));
        assertThrows(IllegalArgumentException.class, () -> new EventConfiguration(definition, PlayState.Mode.TIMER,
                Optional.empty(), 0, 0, "", false));
        assertThrows(IllegalArgumentException.class, () -> new EventConfiguration(definition, PlayState.Mode.TIMER,
                Optional.empty(), 0, EventConfiguration.MAX_DELAY_TICKS + 1, "", false));
    }

    @Test
    void enabledConfigurationsRequireModeAndAudienceReferences() {
        EventDefinition participants = definition(ObservationCatalog.Audience.PARTICIPANTS, Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> new EventConfiguration(participants, PlayState.Mode.ENTRY,
                Optional.empty(), 0, 0, "", true));
        assertThrows(IllegalArgumentException.class, () -> new EventConfiguration(participants, PlayState.Mode.CONDITION,
                Optional.empty(), 0, 0, "", true));
        assertThrows(IllegalArgumentException.class, () -> new EventConfiguration(participants, PlayState.Mode.MANUAL,
                Optional.empty(), 0, 0, "bad flag", true));

        EventRegionReference trigger = new EventRegionReference("entrance", 3);
        EventConfiguration entry = new EventConfiguration(participants, PlayState.Mode.ENTRY, Optional.of(trigger),
                0, 0, "", true);
        assertEquals(trigger, entry.triggerRegion().orElseThrow());

        EventConfiguration condition = new EventConfiguration(participants, PlayState.Mode.CONDITION, Optional.empty(),
                0, 0, "quiet_below:ready", true);
        assertEquals("quiet_below:ready", condition.requiredFlag());

        EventDefinition missingAudienceRegion = definition(ObservationCatalog.Audience.REGION, Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> new EventConfiguration(missingAudienceRegion, PlayState.Mode.MANUAL,
                Optional.empty(), 0, 0, "", true));
        EventDefinition pinnedAudience = definition(ObservationCatalog.Audience.REGION,
                Optional.of(new EventRegionReference("audience", 7)));
        new EventConfiguration(pinnedAudience, PlayState.Mode.MANUAL, Optional.empty(), 0, 0, "", true);
    }

    private static EventDefinition definition(ObservationCatalog.Audience audience,
                                              Optional<EventRegionReference> audienceRegion) {
        return new EventDefinition("quiet_below:test", 1, "Test", "", "Message", "", audience, audienceRegion, List.of());
    }
}
