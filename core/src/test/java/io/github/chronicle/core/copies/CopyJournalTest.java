package io.github.chronicle.core.copies;

import io.github.chronicle.core.content.InventorySnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CopyJournalTest {
    static final class Memory implements CopyJournal.Storage {
        byte[] data; boolean fail, publish;
        public byte[] load() { return data; }
        public void save(byte[] bytes) throws IOException { if (!fail || publish) data = bytes.clone(); if (fail) throw new IOException("injected"); }
    }
    static InventorySnapshot inventory(boolean book) { var stacks = new ArrayList<>(Collections.nCopies(36, "{}")); if(book) stacks.set(0,"{\"id\":\"minecraft:written_book\"}"); return new InventorySnapshot(36, stacks); }
    static CopyJournal.Operation op(UUID id, UUID actor) { return new CopyJournal.Operation(id, actor, "test:provider", "secret/one", "a".repeat(64), 1, CopyJournal.Stage.CLAIMED, inventory(false), inventory(true)); }
    @Test void receiptsCompactAndReplayIsBlockedAcrossReload() throws Exception {
        Memory storage = new Memory(); CopyJournal journal = new CopyJournal(storage); UUID id=UUID.randomUUID(), actor=UUID.randomUUID();
        journal.claim(op(id,actor)); journal.finish(id, CopyJournal.Stage.COMPLETE);
        String bytes = new String(storage.data, StandardCharsets.UTF_8); assertFalse(bytes.contains("before")); assertFalse(bytes.contains("minecraft:written_book"));
        CopyJournal restored = new CopyJournal(storage); assertEquals(CopyJournal.Stage.COMPLETE, restored.get(id).stage());
        assertThrows(IllegalArgumentException.class, () -> restored.claim(op(id,UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class, () -> restored.claim(op(id,actor)));
    }
    @Test void failedPublicationPoisonedUntilExactReload() throws Exception {
        for(boolean published : List.of(false,true)) {
            Memory storage=new Memory(); CopyJournal journal=new CopyJournal(storage); UUID id=UUID.randomUUID(); storage.fail=true; storage.publish=published;
            assertThrows(IOException.class, () -> journal.claim(op(id,UUID.randomUUID()))); assertTrue(journal.poisoned());
            storage.fail=false; journal.reload(); assertFalse(journal.poisoned()); assertEquals(published, journal.get(id)!=null);
        }
    }
    @Test void terminalPublicationFailureReloadsActualDurableEvidence() throws Exception {
        for(boolean published : List.of(false,true)) {
            Memory storage=new Memory(); CopyJournal journal=new CopyJournal(storage); UUID id=UUID.randomUUID(); journal.claim(op(id,UUID.randomUUID()));
            storage.fail=true; storage.publish=published;
            assertThrows(IOException.class,()->journal.finish(id,CopyJournal.Stage.COMPLETE)); assertTrue(journal.poisoned());
            storage.fail=false; journal.reload(); assertEquals(published ? CopyJournal.Stage.COMPLETE : CopyJournal.Stage.CLAIMED,journal.get(id).stage());
        }
    }
    @Test void classifiesExactEvidenceWithoutGuessing() {
        var operation=op(UUID.randomUUID(),UUID.randomUUID());
        assertEquals(CopyJournal.Evidence.BEFORE,CopyJournal.inspect(operation,inventory(false)));
        assertEquals(CopyJournal.Evidence.AFTER,CopyJournal.inspect(operation,inventory(true)));
        var mixed = new ArrayList<>(inventory(true).stacks()); mixed.set(1,"{\"id\":\"minecraft:stone\"}");
        assertEquals(CopyJournal.Evidence.MIXED,CopyJournal.inspect(operation,new InventorySnapshot(36,mixed)));
    }
    @Test void malformedTypedFieldsAreRejectedWithoutReplacingEvidence() throws Exception {
        Memory good = new Memory(); CopyJournal journal = new CopyJournal(good); UUID id = UUID.fromString("abcdefab-cdef-4abc-8abc-abcdefabcdef"); journal.claim(op(id,UUID.randomUUID()));
        String original = new String(good.data, StandardCharsets.UTF_8);
        for (String malformed : List.of(
                original.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
                original.replace("\"count\":1", "\"count\":1.0"),
                original.replace("\"count\":1", "\"count\":\"1\""),
                original.replace(id.toString(), id.toString().toUpperCase(Locale.ROOT)),
                original.replace("\"size\":36", "\"size\":36.0"),
                original.replace("\"size\":36", "\"size\":\"36\""),
                original.replace("\"provider\":\"test:provider\"", "\"provider\":7"),
                original.replace("\"stage\":\"CLAIMED\"", "\"stage\":\"COMPLETE\""))) {
            assertNotEquals(original, malformed);
            Memory corrupted = new Memory(); corrupted.data = malformed.getBytes(StandardCharsets.UTF_8); byte[] evidence=corrupted.data.clone();
            assertThrows(IOException.class, () -> new CopyJournal(corrupted)); assertArrayEquals(evidence, corrupted.data);
        }
    }
    @Test void pendingIndexIsBoundedAndUpdatedAfterFinishing() throws Exception {
        CopyJournal journal = new CopyJournal(new Memory()); UUID id=UUID.randomUUID(); journal.claim(op(id,UUID.randomUUID()));
        assertEquals(1,journal.pending().size()); journal.finish(id,CopyJournal.Stage.CANCELLED); assertTrue(journal.pending().isEmpty());
        assertEquals(CopyJournal.Stage.CANCELLED,journal.get(id).stage());
    }
    @Test void malformedNestedStackJsonPreservesJournalEvidence() throws Exception {
        Memory good = new Memory(); CopyJournal journal = new CopyJournal(good);
        journal.claim(op(UUID.randomUUID(), UUID.randomUUID()));
        String original = new String(good.data, StandardCharsets.UTF_8);
        for (String stack : List.of("{id:1}", "{\"id\":1,\"id\":2}", "{\"nested\":{\"x\":1,\"x\":2}}", "{} {}", "{", "[]")) {
            var root = com.google.gson.JsonParser.parseString(original).getAsJsonObject();
            root.getAsJsonArray("operations").get(0).getAsJsonObject().getAsJsonObject("before").getAsJsonArray("stacks").set(0, new com.google.gson.JsonPrimitive(stack));
            Memory corrupted = new Memory(); corrupted.data = root.toString().getBytes(StandardCharsets.UTF_8); byte[] evidence = corrupted.data.clone();
            assertThrows(IOException.class, () -> new CopyJournal(corrupted), stack);
            assertArrayEquals(evidence, corrupted.data);
        }
    }
    @Test void onePendingPerActorAndLimit() throws Exception {
        Memory storage=new Memory(); CopyJournal journal=new CopyJournal(storage); UUID actor=UUID.randomUUID(); journal.claim(op(UUID.randomUUID(),actor));
        assertThrows(IllegalStateException.class,()->journal.claim(op(UUID.randomUUID(),actor)));
        for(int i=1;i<CopyJournal.MAX_PENDING;i++) journal.claim(op(UUID.randomUUID(),UUID.randomUUID()));
        assertThrows(IllegalStateException.class,()->journal.claim(op(UUID.randomUUID(),UUID.randomUUID())));
    }
}
