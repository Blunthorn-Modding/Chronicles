package io.github.chronicle.core.play;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import io.github.chronicle.core.encounters.EncounterTarget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @Test
    void formatThreePinsEncounterActionsWithoutChangingLegacyShapes() {
        EventEncounterAction group=EventEncounterAction.groupTarget("test:patrol");
        EventEncounterAction point=new EventEncounterAction("test:guards",Optional.of(new EncounterTarget(EncounterTarget.Kind.POINT,"gate",7)));
        EventDefinition definition=new EventDefinition("test:arrival",3,"Arrival","","","",ObservationCatalog.Audience.PARTICIPANTS,
                Optional.empty(),List.of(),Map.of(),List.of(group,point));
        EventConfiguration configuration=new EventConfiguration(definition,PlayState.Mode.MANUAL,Optional.empty(),0,0,List.of(),true,EventSource.empty());
        JsonObject encoded=EventJson.encodeDefinition(definition);
        assertEquals(3,encoded.get("formatVersion").getAsInt());
        assertEquals(2,encoded.getAsJsonArray("encounterActions").size());
        assertEquals(definition,EventJson.decodeDefinition(encoded));
        assertEquals(configuration,EventJson.decodeConfiguration(EventJson.encodeConfiguration(configuration)));
        EventDefinition changed=new EventDefinition("test:arrival",3,"Arrival","","","",ObservationCatalog.Audience.PARTICIPANTS,
                Optional.empty(),List.of(),Map.of(),List.of(point,group));
        assertNotEquals(definition.fingerprint(),changed.fingerprint());
        JsonObject mixed=encoded.deepCopy();mixed.addProperty("formatVersion",2);
        assertThrows(IllegalArgumentException.class,()->EventJson.decodeDefinition(mixed));
    }
}
