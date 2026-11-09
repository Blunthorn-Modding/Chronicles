package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.lastcouncil.treaties.TreatyService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TreatyProjectionTest {
    @Test void historicalSnapshotRetainsMaximumSupplementaryUnicodeTerms() {
        String symbol="\uD83D\uDE00", title=symbol.repeat(80), terms=symbol.repeat(2048);
        UUID owner=UUID.randomUUID();
        var doc=new TreatyService.Document(UUID.randomUUID(),Long.MAX_VALUE,title,terms,TreatyService.Visibility.PRIVATE,List.of(
                new TreatyService.Party("house:first",owner,title),
                new TreatyService.Party("house:second",UUID.randomUUID(),"Second")));
        String history=TreatyRuntime.history(new TreatyService.Event(TreatyService.Action.EDITED,owner,doc));
        assertTrue(history.endsWith("\n"+title+"\n"+terms));
        assertTrue(history.length()<=4608);
    }
}
