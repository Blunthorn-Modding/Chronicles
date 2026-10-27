package io.github.chronicle.core.play;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventJsonTest {
    @Test
    void roundTripsDefinitionsConfigurationsAndEveryExecutionPhase() {
        EventDefinition definition = EventExecutionTest.definition("quiet_below:world", true);
        EventConfiguration configuration = EventExecutionTest.configuration(definition, PlayState.Mode.ENTRY,
                Optional.of(new EventRegionReference("entrance", 4)), "quiet_below:ready", true);
        assertEquals(definition, EventJson.decodeDefinition(EventJson.encodeDefinition(definition)));
        assertEquals(configuration, EventJson.decodeConfiguration(EventJson.encodeConfiguration(configuration)));

        for (EventExecution.Phase phase : EventExecution.Phase.values()) {
            EventExecution execution = EventExecutionTest.execution(configuration, phase,
                    PlayState.DeliverySource.ENTRY,
                    java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), phase.name());
            assertEquals(execution, EventJson.decodeExecution(EventJson.encodeExecution(execution)));
        }
    }

    @Test
    void rejectsUnknownMissingAndMalformedNestedFields() {
        EventDefinition definition = EventExecutionTest.definition("quiet_below:world", true);
        JsonObject unknown = EventJson.encodeDefinition(definition).deepCopy();
        unknown.addProperty("unknown", true);
        assertThrows(IllegalArgumentException.class, () -> EventJson.decodeDefinition(unknown));

        JsonObject missing = EventJson.encodeDefinition(definition).deepCopy();
        missing.remove("label");
        assertThrows(IllegalArgumentException.class, () -> EventJson.decodeDefinition(missing));

        JsonObject badState = EventJson.encodeDefinition(definition).deepCopy();
        ((JsonArray) badState.get("changes")).get(0).getAsJsonObject().addProperty("state", "UNKNOWN");
        assertThrows(IllegalArgumentException.class, () -> EventJson.decodeDefinition(badState));

        JsonObject mismatchedReference = EventJson.encodeDefinition(definition).deepCopy();
        JsonObject change = ((JsonArray) mismatchedReference.get("changes")).get(0).getAsJsonObject();
        change.getAsJsonObject("snapshot").getAsJsonObject("bounds").addProperty("regionRevision", 99);
        assertThrows(IllegalArgumentException.class, () -> EventJson.decodeDefinition(mismatchedReference));

        EventConfiguration configuration = EventExecutionTest.configuration(definition, PlayState.Mode.TIMER,
                Optional.empty(), "", true);
        JsonObject badActor = EventJson.encodeExecution(EventExecutionTest.execution(configuration,
                EventExecution.Phase.PREPARED, PlayState.DeliverySource.TIMER, null, ""));
        badActor.addProperty("operationId", "not-a-uuid");
        assertThrows(IllegalArgumentException.class, () -> EventJson.decodeExecution(badActor));
    }

    @Test
    void rejectsIndividualObjectsAboveTheEncodedLimitBeforeTraversal() {
        JsonObject oversized = new JsonObject();
        oversized.addProperty("padding", "x".repeat(EventJson.MAX_ENCODED_BYTES));
        assertThrows(IllegalArgumentException.class, () -> EventJson.decodeDefinition(oversized));
    }
}
