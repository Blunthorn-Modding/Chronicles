package io.github.chronicle.core.play;

import io.github.chronicle.core.scenario.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EventRecurrenceConfigurationTest {
    private EventDefinition definition(int version,ObservationCatalog.Audience audience,Map<String,Boolean> flags) {
        return new EventDefinition("test:cue",1,"Cue","","A cue","",audience,Optional.empty(),List.of(),flags,List.of(),version);
    }
    private EventConfiguration current(EventOccurrencePolicy policy,long delay,Map<String,Boolean> flags) {
        return new EventConfiguration(definition(4,ObservationCatalog.Audience.SOURCE_ACTOR,flags),PlayState.Mode.SYSTEM,Optional.empty(),0,delay,List.of(),true,new EventSource("test:prayer","*",""),policy);
    }
    @Test void legacyFormatsRetainTheirEncodingAndRoundTripWithoutInventingRecurrence() {
        for(int version=1;version<=3;version++) {
            var c=new EventConfiguration(definition(version,ObservationCatalog.Audience.PARTICIPANTS,Map.of()),PlayState.Mode.MANUAL,Optional.empty(),0,0,List.of(),true,EventSource.empty(),version);
            var json=EventJson.encodeConfiguration(c);
            assertFalse(json.has("occurrencePolicy"));assertEquals(c,EventJson.decodeConfiguration(json));
            assertEquals(c.fingerprint(),EventJson.decodeConfiguration(json).fingerprint());
            assertEquals(EventOccurrencePolicy.ONCE,c.occurrencePolicy());
        }
    }
    @Test void currentPolicyIsPinnedInWireAndPortableContent() {
        var c=current(EventOccurrencePolicy.ONCE_PER_ACTOR,0,Map.of());
        assertEquals(c,EventJson.decodeConfiguration(EventJson.encodeConfiguration(c)));
        assertNotEquals(c.fingerprint(),current(EventOccurrencePolicy.PER_SOURCE_OCCURRENCE,0,Map.of()).fingerprint());
        var template=EventTemplate.from(c);assertFalse(template.enabled());assertEquals(c.occurrencePolicy(),template.occurrencePolicy());
        var content=new ScenarioContent("test:scenario",Map.of(),Map.of(),Map.of(),Map.of(template.id(),template));
        assertEquals(content,ScenarioContentJson.decode(ScenarioContentJson.encode(content)));
    }
    @Test void repeatsCannotBecomeDelayedWorldActionsOrCompletionLoops() {
        assertThrows(IllegalArgumentException.class,()->current(EventOccurrencePolicy.ONCE_PER_ACTOR,20,Map.of()));
        assertThrows(IllegalArgumentException.class,()->current(EventOccurrencePolicy.ONCE_PER_ACTOR,0,Map.of("test:changed",true)));
        var d=definition(4,ObservationCatalog.Audience.PARTICIPANTS,Map.of());
        assertThrows(IllegalArgumentException.class,()->new EventConfiguration(d,PlayState.Mode.MANUAL,Optional.empty(),0,0,List.of(),true,EventSource.empty(),EventOccurrencePolicy.PER_SOURCE_OCCURRENCE));
        assertThrows(IllegalArgumentException.class,()->new EventConfiguration(d,PlayState.Mode.SYSTEM,Optional.empty(),0,0,List.of(),true,new EventSource("chronicle_core:event_completed","test:cue",""),EventOccurrencePolicy.PER_SOURCE_OCCURRENCE));
        assertThrows(IllegalArgumentException.class,()->new EventConfiguration(d,PlayState.Mode.SYSTEM,Optional.empty(),0,0,List.of(),true,new EventSource("test:prayer","*",""),EventOccurrencePolicy.ONCE_PER_ACTOR));
    }
}
