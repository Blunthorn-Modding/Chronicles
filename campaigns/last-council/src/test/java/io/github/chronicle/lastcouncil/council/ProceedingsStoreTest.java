package io.github.chronicle.lastcouncil.council;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ProceedingsStoreTest {
    @TempDir Path directory;

    @Test void emptyRoundTripAndExternalChanges() throws Exception {
        Path path = directory.resolve("proceedings.json"); ProceedingsStore store = new ProceedingsStore(path);
        assertThrows(IOException.class, () -> store.save(ProceedingsState.empty()));
        assertEquals(ProceedingsState.empty(), store.load()); store.save(ProceedingsState.empty()); store.verifyUnchanged();
        assertEquals(ProceedingsState.empty(), new ProceedingsStore(path).load());
        Files.writeString(path, "external");
        assertThrows(IOException.class, store::verifyUnchanged);
        assertThrows(IOException.class, () -> store.save(ProceedingsState.empty()));
        assertEquals("external", Files.readString(path));
    }

    @Test void rejectsMalformedLedgersAndPreventsOverwrite() throws Exception {
        Path path = directory.resolve("proceedings.json"); ProceedingsStore source = new ProceedingsStore(path);
        source.load(); source.save(ProceedingsState.empty()); String valid = Files.readString(path);
        for (String malformed : new String[] {
                valid.replace("\"revision\":0", "\"revision\":1.0"),
                valid.replace("\"revision\":0", "\"revision\":\"0\""),
                valid.replace("\"revision\":0", "\"revision\":null"),
                valid.replace("\"revision\":0", "\"revision\":0,\"revision\":0"),
                valid.replace("\"schemaVersion\":2", "\"schemaVersion\":2,\"extra\":true"),
                valid.replace("\"activeBallot\":null", "\"activeBallot\":\"AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA\"")
        }) {
            assertNotEquals(valid, malformed);
            Files.writeString(path, malformed); ProceedingsStore store = new ProceedingsStore(path);
            assertThrows(IOException.class, store::load); assertThrows(IOException.class, () -> store.save(ProceedingsState.empty()));
            assertEquals(malformed, Files.readString(path));
        }
        Files.write(path, new byte[] {(byte) 0xc3, 0x28}); ProceedingsStore malformedUtf8 = new ProceedingsStore(path);
        assertThrows(IOException.class, malformedUtf8::load); assertThrows(IOException.class, () -> malformedUtf8.save(ProceedingsState.empty()));
        Files.write(path, new byte[4 * 1024 * 1024 + 1]); ProceedingsStore oversized = new ProceedingsStore(path);
        assertThrows(IOException.class, oversized::load); assertThrows(IOException.class, () -> oversized.save(ProceedingsState.empty()));
    }
}
