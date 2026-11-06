package io.github.chronicle.lastcouncil.content;

import io.github.chronicle.core.play.*;
import io.github.chronicle.core.scenario.EventTemplate;
import java.util.*;

/** Editable reusable defaults. Every meeting call uses Core's existing source-occurrence path. */
public final class CouncilDefaults {
    private CouncilDefaults() { }
    public static EventTemplate meetingCall() {
        return new EventTemplate("last_council:council_call","Call Council",
                "The Chancellor calls the houses to gather. Each accepted call plays the configured message and sound.",
                "The Chancellor calls the houses to Council.","minecraft:block.bell.use",
                ObservationCatalog.Audience.PARTICIPANTS,"",PlayState.Mode.SYSTEM,"",0,List.of(),Map.of(),List.of(),
                new EventSource("last_council:council_called","council",""),List.of(),EventOccurrencePolicy.PER_SOURCE_OCCURRENCE);
    }
}
