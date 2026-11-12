package io.github.chronicle.lastcouncil.banquet;

import io.github.chronicle.core.content.InventorySnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BanquetJournalTest {
    @TempDir Path directory;
    private static final String HASH = "a".repeat(64);
    private InventorySnapshot inventory(boolean changed) {
        List<String> slots = new ArrayList<>(Collections.nCopies(43, "{}"));
        if (changed) slots.set(0, "{\"id\":\"minecraft:stone\"}");
        return new InventorySnapshot(43, slots);
    }
    private BanquetJournal.Intent intent() {
        return new BanquetJournal.Intent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), HASH, "private", HASH,
                BanquetState.empty(), inventory(false), inventory(true));
    }
    @Test void pendingRestartFinishAndTerminalEvidence() throws Exception {
        Path path = directory.resolve("journal.json"); var initial = new BanquetJournal(path); var intent = intent();
        initial.claim(intent);
        var restarted = new BanquetJournal(path); assertEquals(intent, restarted.pending());
        assertThrows(IllegalStateException.class, () -> restarted.claim(intent()));
        restarted.finish(intent.request(), BanquetJournal.Stage.COMPLETE);
        restarted.finish(intent.request(), BanquetJournal.Stage.COMPLETE);
        assertThrows(IllegalArgumentException.class, () -> restarted.finish(intent.request(), BanquetJournal.Stage.CANCELLED));
        var terminal = new BanquetJournal(path); assertNull(terminal.pending());
        assertEquals(BanquetJournal.Stage.COMPLETE, terminal.get(intent.request()).stage());
        assertFalse(Files.readString(path).contains("afterLedger"));
        assertThrows(UnsupportedOperationException.class, () -> terminal.receipts().clear());
        assertThrows(IllegalArgumentException.class, () -> terminal.claim(intent));
    }
    @Test void staleWritePoisonsAndPreservesPending() throws Exception {
        Path path = directory.resolve("journal.json"); var journal = new BanquetJournal(path); var intent = intent(); journal.claim(intent);
        byte[] external = "{}".getBytes(StandardCharsets.UTF_8); Files.write(path, external);
        assertThrows(IOException.class, () -> journal.finish(intent.request(), BanquetJournal.Stage.COMPLETE));
        assertTrue(journal.poisoned()); assertEquals(intent, journal.pending()); assertNull(journal.get(intent.request()));
        assertThrows(IOException.class, () -> journal.finish(intent.request(), BanquetJournal.Stage.COMPLETE));
        assertArrayEquals(external, Files.readAllBytes(path));
    }
    @Test void invalidInputsDoNotPoison() throws Exception {
        var journal = new BanquetJournal(directory.resolve("journal.json"));
        assertThrows(IllegalArgumentException.class, () -> new BanquetJournal.Intent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "bad", "", HASH, BanquetState.empty(), inventory(false), inventory(true)));
        assertThrows(IllegalArgumentException.class, () -> new BanquetJournal.Intent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), HASH, "x".repeat(513), HASH, BanquetState.empty(), inventory(false), inventory(true)));
        assertThrows(IllegalArgumentException.class, () -> new BanquetJournal.Intent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), HASH, "", HASH, BanquetState.empty(), inventory(false), inventory(false)));
        assertFalse(journal.poisoned()); journal.claim(intent());
    }
    @Test void malformedFilesRemainUntouched() throws Exception {
        Path path = directory.resolve("journal.json");
        for (String value : List.of("{}", "{\"schema\":1,\"pending\":null,\"receipts\":[],\"receipts\":[]}",
                "{\"schema\":1.0,\"pending\":null,\"receipts\":[]}", "{\"schema\":1,\"pending\":null,\"receipts\":[{}]}")) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8); Files.write(path, bytes);
            assertThrows(IOException.class, () -> new BanquetJournal(path)); assertArrayEquals(bytes, Files.readAllBytes(path));
        }
        byte[] bytes = {(byte) 0xc3, 0x28}; Files.write(path, bytes);
        assertThrows(IOException.class, () -> new BanquetJournal(path)); assertArrayEquals(bytes, Files.readAllBytes(path));
    }
    @Test void receiptCapacityAndByteLimitPreserveFile() throws Exception {
        Path path = directory.resolve("journal.json");
        StringBuilder json = new StringBuilder("{\"schema\":1,\"pending\":null,\"receipts\":[");
        for (int i = 0; i < BanquetJournal.MAX_RECEIPTS; i++) {
            if (i > 0) json.append(',');
            String id = new UUID(0, i + 1).toString();
            json.append("{\"request\":\"").append(id).append("\",\"actor\":\"").append(id)
                .append("\",\"owner\":\"").append(id).append("\",\"fingerprint\":\"").append(HASH)
                .append("\",\"description\":\"\",\"stage\":\"COMPLETE\"}");
        }
        json.append("]}"); Files.writeString(path, json.toString());
        var journal = new BanquetJournal(path); byte[] original = Files.readAllBytes(path);
        assertEquals(BanquetJournal.MAX_RECEIPTS, journal.receipts().size());
        assertThrows(IllegalStateException.class, () -> journal.claim(intent()));
        assertFalse(journal.poisoned()); assertArrayEquals(original, Files.readAllBytes(path));
        byte[] oversized = new byte[BanquetJournal.MAX_BYTES + 1]; Arrays.fill(oversized, (byte) ' ');
        Files.write(path, oversized);
        assertThrows(IOException.class, () -> new BanquetJournal(path)); assertArrayEquals(oversized, Files.readAllBytes(path));
    }
    @Test void largeEscapedSnapshotsRoundTrip() throws Exception {
        String stack = "{\"x\":\"" + "\\\\".repeat(32760) + "\"}";
        assertTrue(stack.length() <= InventorySnapshot.MAX_STACK_JSON_CHARS);
        var before = new InventorySnapshot(43, Collections.nCopies(43, stack));
        var afterSlots = new ArrayList<>(before.stacks()); afterSlots.set(0, "{}");
        var after = new InventorySnapshot(43, afterSlots);
        var intent = new BanquetJournal.Intent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), HASH, "", HASH,
                BanquetState.empty(), before, after);
        Path path = directory.resolve("large.json"); var journal = new BanquetJournal(path);
        journal.claim(intent); assertFalse(journal.poisoned());
        assertTrue(Files.size(path) < BanquetJournal.MAX_BYTES);
        assertEquals(intent, new BanquetJournal(path).pending());
    }
}
