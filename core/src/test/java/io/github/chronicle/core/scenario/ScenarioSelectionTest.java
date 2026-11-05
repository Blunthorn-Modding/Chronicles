package io.github.chronicle.core.scenario;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioSelectionTest {
    @Test void noScenarioAndSingleScenarioRemainAutomatic() {
        assertEquals(Optional.empty(),ScenarioSelection.resolve(Set.of(),Optional.empty()));
        assertEquals(Optional.of("quiet_below:scenario"),ScenarioSelection.resolve(Set.of("quiet_below:scenario"),Optional.empty()));
        assertEquals(Optional.of("last_council:scenario"),ScenarioSelection.resolve(Set.of("last_council:scenario"),Optional.empty()));
    }
    @Test void dualInstallRequiresExplicitChoiceAndRejectsMissingProvider() {
        var installed=Set.of("quiet_below:scenario","last_council:scenario");
        assertThrows(IllegalArgumentException.class,()->ScenarioSelection.resolve(installed,Optional.empty()));
        assertEquals(Optional.of("last_council:scenario"),ScenarioSelection.resolve(installed,Optional.of("last_council:scenario")));
        assertThrows(IllegalArgumentException.class,()->ScenarioSelection.resolve(installed,Optional.of("other:missing")));
    }
    @Test void strictRoundTripAndInvalidConfiguration() {
        assertEquals("last_council:scenario",ScenarioSelection.decode(ScenarioSelection.encode("last_council:scenario")));
        for(String bad:List.of("{}","{\"schema\":1,\"schema\":1,\"scenarioId\":\"a:b\"}","{\"schema\":\"1\",\"scenarioId\":\"a:b\"}","{\"schema\":1,\"scenarioId\":\"a:b\",\"extra\":true}","{\"schema\":1,\"scenarioId\":\"BAD\"}"))
            assertThrows(IllegalArgumentException.class,()->ScenarioSelection.decode(bad.getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,()->ScenarioSelection.decode(new byte[]{(byte)0xC0,(byte)0xAF}));
    }
}
