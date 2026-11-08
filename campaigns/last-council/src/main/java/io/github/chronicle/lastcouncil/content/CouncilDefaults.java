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
    public static List<EventTemplate> complications() {
        String[][] entries={
            {"broken_seal","A broken royal seal","An attendant brings a broken royal seal into the chamber. Two conflicting orders bear its impression. Both are said to have come from the royal household."},
            {"missing_ledger","The missing ledger","A clerk interrupts the Council: the treasury's final ledger is missing. A torn cover has been left in its place. No one has yet agreed who last held the key."},
            {"unsigned_codicil","An unsigned codicil","An unsigned addition to the late ruler's papers has arrived. Its seal is old, its ink less so. It names no heir. Its bearer asks the Council to decide whether it deserves a hearing."},
            {"late_caravan","The overdue caravan","A messenger reports that the expected supply caravan has not reached the crossing. Its escort returned without the wagons. Their accounts of the journey do not agree."},
            {"empty_chalice","An empty chalice","A servant places an empty chalice before the Chancellor. Something bitter clings to its rim. The servant will not say whose place it was taken from."}
        };
        List<EventTemplate> result=new ArrayList<>();
        for(String[] e:entries)result.add(new EventTemplate("last_council:"+e[0],e[1],"An interruption for the Council to interpret and respond to.",e[2],"minecraft:block.bell.use",ObservationCatalog.Audience.PARTICIPANTS,"",PlayState.Mode.MANUAL,"",0,List.of(),Map.of(),List.of(),EventSource.empty(),List.of(),EventOccurrencePolicy.ONCE));
        return List.copyOf(result);
    }
    public static io.github.chronicle.core.eventpool.EventPoolDefinition complicationPool() {
        return new io.github.chronicle.core.eventpool.EventPoolDefinition("last_council:complications","Council complications",complications().stream().map(e->new io.github.chronicle.core.eventpool.EventPoolDefinition.Entry(e.id(),1)).toList());
    }

}
