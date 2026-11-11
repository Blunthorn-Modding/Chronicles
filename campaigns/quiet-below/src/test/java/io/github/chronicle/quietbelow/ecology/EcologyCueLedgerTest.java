package io.github.chronicle.quietbelow.ecology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class EcologyCueLedgerTest {
    @TempDir Path directory;
    @Test void growthHighWaterAndFirstStationSurviveRestart() throws Exception {
        var ledger = new EcologyCueLedger(directory.resolve("cues")); ledger.load(); assertFalse(ledger.initialized());
        ledger.initialize(7342); assertEquals(12342, ledger.nextThreshold()); ledger.publishGrowth(); ledger.publishStation("quiet_below:ancient_forge");
        var reopened = new EcologyCueLedger(directory.resolve("cues")); reopened.load();
        assertEquals(17342, reopened.nextThreshold()); assertTrue(reopened.stationSeen("quiet_below:ancient_forge")); assertFalse(reopened.stationSeen("quiet_below:ancient_altar"));
        assertTrue(reopened.proves("growth:1")); assertFalse(reopened.proves("growth:2")); assertTrue(reopened.proves("station:quiet_below:ancient_forge"));
        reopened.publishStation("quiet_below:ancient_forge"); assertThrows(IllegalArgumentException.class, () -> reopened.publishStation("test:any"));
    }
    @Test void missingInitializedArchiveIsAnError() throws Exception {
        Path path = directory.resolve("cues"); var ledger = new EcologyCueLedger(path); ledger.load(); ledger.initialize(0);
        try (var files = Files.list(path)) { Files.delete(files.findFirst().orElseThrow()); }
        assertThrows(IOException.class, () -> new EcologyCueLedger(path).load());
    }
}
