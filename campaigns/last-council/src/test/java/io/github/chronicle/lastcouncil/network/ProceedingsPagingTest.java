package io.github.chronicle.lastcouncil.network;

import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProceedingsPagingTest {
    @Test void newestFourFirstAndEachPageStaysChronological() {
        var ids=IntStream.range(0,10).mapToObj(i->new UUID(0,i)).toList();
        assertEquals(ids.subList(6,10),ProceedingsPayloads.attemptPage(ids,0));
        assertEquals(ids.subList(2,6),ProceedingsPayloads.attemptPage(ids,1));
        assertEquals(ids.subList(0,2),ProceedingsPayloads.attemptPage(ids,2));
        assertTrue(ProceedingsPayloads.attemptPage(ids,3).isEmpty());
        assertThrows(IllegalArgumentException.class,()->ProceedingsPayloads.attemptPage(ids,64));
        var old=new ProceedingsPayloads.Request(UUID.randomUUID(),ProceedingsPayloads.Action.STATE,0,new UUID(0,0),"","","",false,false);
        assertEquals(0,old.historyPage());
    }
}
