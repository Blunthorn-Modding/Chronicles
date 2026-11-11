package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EcologySoakAccountingTest {
    @Test void completingMaintenanceCannotInheritThePriorActiveWork(){
        var counts=new EcologySoakAccounting();
        var work=EcologyTickWork.observed(true,false,false,EcologyTickWork.GROWTH_ATTEMPT);
        assertEquals(EcologyTickWork.MAINTENANCE,work);
        counts.record(work,false);
        assertEquals(0,counts.activeTicks());assertEquals(1,counts.unexpectedTicks());
        assertEquals(EcologyTickWork.GROWTH_ATTEMPT,
                EcologyTickWork.observed(false,false,false,EcologyTickWork.GROWTH_ATTEMPT));
        assertEquals(EcologyTickWork.FAILURE,
                EcologyTickWork.observed(false,false,true,EcologyTickWork.COUNTDOWN));
    }
    @Test void countsActualWorkAndKeepsExpectedUnloadsSeparate(){
        var counts=new EcologySoakAccounting();
        counts.record(EcologyTickWork.GROWTH_ATTEMPT,false);
        counts.record(EcologyTickWork.COUNTDOWN,false);
        counts.record(EcologyTickWork.TOPOLOGY_UNKNOWN,true);
        counts.record(EcologyTickWork.TOPOLOGY_REBUILD,true);
        counts.record(EcologyTickWork.TOPOLOGY_UNKNOWN,false);
        counts.record(EcologyTickWork.EXTENSION_WAIT,false);
        assertEquals(2,counts.activeTicks());assertEquals(2,counts.unexpectedTicks());
        assertEquals(6,counts.workCounts().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(2,counts.unexpectedCounts().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(1L,counts.unexpectedCounts().get(EcologyTickWork.TOPOLOGY_UNKNOWN));
        assertEquals(0L,counts.unexpectedCounts().get(EcologyTickWork.TOPOLOGY_REBUILD));
        assertEquals(0L,counts.unexpectedCounts().get(EcologyTickWork.GROWTH_ATTEMPT));
    }
}
