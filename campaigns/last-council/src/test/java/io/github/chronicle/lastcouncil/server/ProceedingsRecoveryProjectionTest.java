package io.github.chronicle.lastcouncil.server;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static io.github.chronicle.lastcouncil.council.ProceedingsState.*;
import static org.junit.jupiter.api.Assertions.*;

class ProceedingsRecoveryProjectionTest {
    @Test void onlyMissingPendingOpenIsOmitted() {
        UUID id=UUID.randomUUID(),other=UUID.randomUUID();
        var opening=new Pending(Operation.OPEN,id,other,"");
        assertNull(ProceedingsRuntime.historyProjection(id,opening,()->{throw new IllegalArgumentException("Unknown ballot");}));
        assertThrows(IllegalArgumentException.class,()->ProceedingsRuntime.historyProjection(other,opening,()->{throw new IllegalArgumentException("Unknown ballot");}));
        assertThrows(IllegalArgumentException.class,()->ProceedingsRuntime.historyProjection(id,new Pending(Operation.CLOSE,id,other,""),()->{throw new IllegalArgumentException("Unknown ballot");}));
        assertThrows(IllegalArgumentException.class,()->ProceedingsRuntime.historyProjection(id,opening,()->{throw new IllegalArgumentException("Corrupt ballot");}));
        assertThrows(IllegalStateException.class,()->ProceedingsRuntime.historyProjection(id,opening,()->{throw new IllegalStateException("Corrupt ballot");}));
    }
    @Test void emptyElectorateExplainsCountsAndEligibility() {
        String message=ProceedingsRuntime.emptyElectorateMessage(3,2,1);
        assertTrue(message.contains("Online inside: 3"));assertTrue(message.contains("Council House: 2"));
        assertTrue(message.contains("representatives outside: 1"));assertTrue(message.contains("participation"));
        assertTrue(message.contains("spectator"));assertTrue(message.contains("author"));assertTrue(message.contains("Chancellor"));
        assertTrue(message.length()<=512);
    }
}
