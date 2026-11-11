package io.github.chronicle.quietbelow.ecology;

import java.util.EnumMap;
import java.util.Map;

/** Diagnostic-only counts; never consulted by gameplay. */
final class EcologySoakAccounting {
    private final EnumMap<EcologyTickWork,Long> work=new EnumMap<>(EcologyTickWork.class);
    private final EnumMap<EcologyTickWork,Long> unexpected=new EnumMap<>(EcologyTickWork.class);
    private long activeTicks,unexpectedTicks;
    EcologySoakAccounting(){for(var kind:EcologyTickWork.values()){work.put(kind,0L);unexpected.put(kind,0L);}}
    void record(EcologyTickWork kind,boolean expectedUnavailable){
        work.merge(kind,1L,Long::sum);
        if(kind.active())activeTicks++;
        else if(!expectedUnavailable){unexpectedTicks++;unexpected.merge(kind,1L,Long::sum);}
    }
    long activeTicks(){return activeTicks;}
    long unexpectedTicks(){return unexpectedTicks;}
    Map<EcologyTickWork,Long> workCounts(){return Map.copyOf(work);}
    Map<EcologyTickWork,Long> unexpectedCounts(){return Map.copyOf(unexpected);}
}
