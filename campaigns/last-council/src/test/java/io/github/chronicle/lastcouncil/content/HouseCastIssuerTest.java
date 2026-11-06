package io.github.chronicle.lastcouncil.content;

import io.github.chronicle.lastcouncil.house.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HouseCastIssuerTest {
    private static final LongSupplier NO_DRAW = () -> { throw new AssertionError("unexpected draw"); };
    static class Memory implements HouseCastIssuer.Storage {
        HouseCast value; int saves; boolean failBefore, failAfter;
        public HouseCast load() { return value; }
        public void save(HouseCast next) throws IOException {
            saves++;
            if (failBefore) throw new IOException("before write");
            value = next;
            if (failAfter) throw new IOException("after write");
        }
    }
    @Test void manualPublishedCastIsRespected() throws Exception {
        Memory pending = new Memory(), published = new Memory();
        published.value = HouseCastGenerator.generate(HouseCastTest.content(0), HouseCastTest.roster(), 2);
        HouseCastIssuer issuer = new HouseCastIssuer(pending, published);
        assertEquals(published.value, issuer.ensure(HouseCastTest.content(0), HouseCastTest.roster(), NO_DRAW));
        assertFalse(issuer.hasPending()); assertEquals(0, pending.saves); assertEquals(0, published.saves);
    }
    @Test void failedPublishReusesPendingAcrossRestartAndTemplateChanges() throws Exception {
        Memory pending = new Memory(), published = new Memory(); published.failBefore = true;
        HouseCastIssuer issuer = new HouseCastIssuer(pending, published);
        assertThrows(IOException.class, () -> issuer.ensure(HouseCastTest.content(100), HouseCastTest.roster(), () -> 42));
        HouseCast original = pending.value; assertNotNull(original); assertTrue(issuer.hasPending());
        published.failBefore = false;
        HouseCastIssuer restarted = new HouseCastIssuer(pending, published);
        assertTrue(restarted.hasPending());
        assertEquals(original, restarted.ensure(HouseCastTest.content(0), HouseCastTest.roster(), NO_DRAW));
        assertEquals(original, published.value); assertEquals(1, pending.saves);
    }
    @Test void failedAfterPublishDoesNotDuplicateOrDraw() throws Exception {
        Memory pending = new Memory(), published = new Memory(); published.failAfter = true;
        HouseCastIssuer issuer = new HouseCastIssuer(pending, published);
        assertThrows(IOException.class, () -> issuer.ensure(HouseCastTest.content(0), HouseCastTest.roster(), () -> 21));
        HouseCast committed = published.value; assertNotNull(committed);
        HouseCastIssuer restarted = new HouseCastIssuer(pending, published);
        assertEquals(committed, restarted.ensure(HouseCastTest.content(0), HouseCastTest.roster(), NO_DRAW));
        assertEquals(1, published.saves); assertEquals(1, pending.saves);
    }
    @Test void failedPendingBeforeWriteAcceptsNoDrawAndRetryCanUseNewSeed() throws Exception {
        Memory pending = new Memory(), published = new Memory(); pending.failBefore = true;
        HouseCastIssuer issuer = new HouseCastIssuer(pending, published);
        assertThrows(IOException.class, () -> issuer.ensure(HouseCastTest.content(0), HouseCastTest.roster(), () -> 1));
        assertNull(pending.value); assertNull(published.value); assertTrue(issuer.hasPending());
        pending.failBefore = false;
        HouseCast result = issuer.ensure(HouseCastTest.content(0), HouseCastTest.roster(), () -> 2);
        assertEquals(2, result.seed()); assertEquals(result, published.value);
    }
    @Test void rejectsMismatchedRostersAndConflictingReceipt() throws Exception {
        Memory pending = new Memory(), published = new Memory();
        HouseCast cast = HouseCastGenerator.generate(HouseCastTest.content(0), HouseCastTest.roster(), 1);
        published.value = cast;
        HouseCastIssuer issuer = new HouseCastIssuer(pending, published);
        assertThrows(IOException.class, () -> issuer.ensure(HouseCastTest.content(0), HouseRoster.empty(), NO_DRAW));
        var roster = HouseCastTest.roster();
        var missing = new HouseRoster(roster.revision(), Map.of(HouseCastTest.A, roster.assignments().get(HouseCastTest.A)));
        assertThrows(IOException.class, () -> issuer.ensure(HouseCastTest.content(0), missing, NO_DRAW));
        var changed = new HashMap<>(roster.assignments());
        changed.put(HouseCastTest.A, new HouseAssignment(HouseCastTest.A, "c:unknown", true, false));
        assertThrows(IOException.class, () -> issuer.ensure(HouseCastTest.content(0), new HouseRoster(roster.revision(), changed), NO_DRAW));
        pending.value = HouseCastGenerator.generate(HouseCastTest.content(0), roster, 2);
        assertThrows(IOException.class, () -> issuer.ensure(HouseCastTest.content(0), roster, NO_DRAW));
        published.value = null; pending.value = null;
        assertThrows(IllegalArgumentException.class, () -> issuer.ensure(HouseCastTest.content(0), HouseRoster.empty(), NO_DRAW));
    }
    @Test void actualFilesRoundTripAndCorruptionIsPreserved(@TempDir Path dir) throws Exception {
        HouseCastIssuer issuer = new HouseCastIssuer(dir);
        HouseCast result = issuer.ensure(HouseCastTest.content(100), HouseCastTest.roster(), () -> 55);
        assertEquals(result, new HouseCastIssuer(dir).ensure(HouseCastTest.content(0), HouseCastTest.roster(), NO_DRAW));
        Path pending = dir.resolve("automatic-cast.json");
        Files.writeString(pending, "invalid", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> new HouseCastIssuer(dir));
        assertEquals("invalid", Files.readString(pending, StandardCharsets.UTF_8));
        assertEquals(result, new HouseCastStore(dir.resolve("cast.json")).load());
    }
}
