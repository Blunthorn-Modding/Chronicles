package io.github.chronicle.lastcouncil.network;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.chronicle.lastcouncil.network.ElectionPayloads.*;

class ElectionPayloadsTest {
    private View view(boolean chancellor,boolean withdraw,boolean end,String warning,List<String> history) {
        return new View(UUID.randomUUID(),false,1,0,"Contested",List.of("Alice — House Ash"),history,
                0,1,"Alice — House Ash",chancellor,withdraw,"",end,"",warning,"",false);
    }
    @Test void roleSpecificControlsCannotLeakThroughTheProjection() {
        assertThrows(IllegalArgumentException.class,()->view(false,false,true,"",List.of()));
        assertThrows(IllegalArgumentException.class,()->view(false,false,false,"Private planning",List.of()));
        assertThrows(IllegalArgumentException.class,()->view(true,true,false,"",List.of()));
        assertTrue(fits(view(true,false,true,"Two more meetings may be needed.",List.of())));
        assertTrue(fits(view(false,true,false,"",List.of())));
    }
    @Test void untrustedRequestsAndHistoryRemainBounded() {
        UUID request=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->new Request(request,Action.STATE,1,0,"",32));
        assertThrows(IllegalArgumentException.class,()->new Request(request,Action.END,1,-1,"Reason",0));
        assertThrows(IllegalArgumentException.class,()->new Request(request,Action.END,1,0,"x".repeat(1601),0));
        assertThrows(IllegalArgumentException.class,()->view(false,false,false,"",Collections.nCopies(17,"History")));
        assertThrows(IllegalArgumentException.class,()->view(false,false,false,"",List.of("x".repeat(2049))));
        assertTrue(fits(view(false,true,false,"",Collections.nCopies(16,"Public reason"))));
    }
    @Test void snapshotsDefensivelyCopyPublicHistory() {
        var history=new ArrayList<>(List.of("Meeting 1: ballot voided."));
        var projected=view(false,true,false,"",history);
        history.clear();
        assertEquals(1,projected.history().size());
        assertThrows(UnsupportedOperationException.class,()->projected.history().add("Forged"));
    }
}
