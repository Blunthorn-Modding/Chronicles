package io.github.chronicle.core.play;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EventProgressStoreTest {
    @TempDir Path directory;

    @Test void absentLoadDoesNotCreateFileAndSaveRequiresLoad() throws Exception {
        Path file = directory.resolve("nested/progress.json");
        EventProgressStore store = new EventProgressStore(file);
        assertThrows(IOException.class, () -> store.save(state(1)));
        assertEquals(EventProgressState.empty(), store.load());
        assertFalse(Files.exists(file));
        assertFalse(Files.exists(file.getParent()));
        store.save(state(1));
        assertEquals(state(1), new EventProgressStore(file).load());
    }

    @Test void roundTripsEveryPinnedFieldIncludingActorNullAndNegativeUnstartedSentinel() throws Exception {
        EventProgressState.Entry matched = entry("test:matched", 400, true, true);
        EventProgressState.Entry unstarted = new EventProgressState.Entry("test:waiting", "b".repeat(64), -1, false,
                null, PlayState.DeliverySource.TIMER, "", false, Set.of(), Map.of("test:ready", 0L), false);
        EventProgressState state = new EventProgressState(12, Map.of(matched.eventId(), matched, unstarted.eventId(), unstarted));
        String encoded = EventProgressStore.encode(state);
        assertEquals(state, EventProgressStore.decode(encoded));
        assertEquals(encoded, EventProgressStore.encode(EventProgressStore.decode(encoded)));
        Path file = directory.resolve("progress.json");
        EventProgressStore store = new EventProgressStore(file); store.load(); store.save(state);
        assertEquals(state, new EventProgressStore(file).load());
    }

    @Test void stateDefensivelyCopiesCollectionsAndValidatesCrossFieldPins() {
        Set<String> facts = new HashSet<>(Set.of("occurrence:1"));
        Map<String, Long> epochs = new HashMap<>(Map.of("test:ready", 2L));
        EventProgressState.Entry entry = new EventProgressState.Entry("test:event", "a".repeat(64), 20, true,
                null, PlayState.DeliverySource.SYSTEM, "occurrence:1", true, facts, epochs, false);
        Map<String, EventProgressState.Entry> entries = new HashMap<>(Map.of(entry.eventId(), entry));
        EventProgressState state = new EventProgressState(1, entries);
        facts.clear(); epochs.clear(); entries.clear();
        assertEquals(Set.of("occurrence:1"), entry.seenFacts());
        assertEquals(Map.of("test:ready", 2L), entry.offVersions());
        assertEquals(Map.of(entry.eventId(), entry), state.entries());
        assertThrows(UnsupportedOperationException.class, () -> entry.seenFacts().clear());
        assertThrows(UnsupportedOperationException.class, () -> entry.offVersions().clear());
        assertThrows(UnsupportedOperationException.class, () -> state.entries().clear());
        assertThrows(IllegalArgumentException.class, () -> new EventProgressState(1, Map.of("test:other", entry)));
        assertThrows(IllegalArgumentException.class, () -> new EventProgressState(-1, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> entry("test:event", 0, false, false));
        assertThrows(IllegalArgumentException.class, () -> entry("test:event", -2, true, false));
        assertThrows(IllegalArgumentException.class, () -> new EventProgressState.Entry("test:event", "a".repeat(64), -1, true,
                null, PlayState.DeliverySource.TIMER, "occurrence", true, Set.of(), Map.of(), false));
        assertThrows(IllegalArgumentException.class, () -> new EventProgressState.Entry("test:event", "a".repeat(64), -1, true,
                null, PlayState.DeliverySource.SYSTEM, "", true, Set.of(), Map.of(), false));
    }

    @Test void retiredFactsTrimWithoutLosingLatchedOccurrenceCountdownOrRetrySafety() {
        Set<String> oldFacts=new HashSet<>();IntStream.range(0,EventProgressState.Entry.MAX_FACTS).forEach(i->oldFacts.add("source:old_"+i));
        EventProgressState.Entry latched=new EventProgressState.Entry("test:event","a".repeat(64),40,true,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),PlayState.DeliverySource.SYSTEM,"source:old_0",true,
                oldFacts,Map.of("test:ready",2L),false);
        EventProgressState state=new EventProgressState(9,Map.of(latched.eventId(),latched));
        EventProgressState trimmed=state.retainLiveFacts(Set.of("source:old_999"));EventProgressState.Entry kept=trimmed.entries().get(latched.eventId());
        assertEquals(Set.of("source:old_999"),kept.seenFacts());assertEquals("source:old_0",kept.occurrence());
        assertEquals(40,kept.startedAt());assertTrue(kept.triggered());assertEquals(latched.offVersions(),kept.offVersions());
        assertSame(trimmed,trimmed.retainLiveFacts(Set.of("source:old_999")));
        Set<String> newFacts=new HashSet<>();IntStream.range(0,EventProgressState.Entry.MAX_FACTS).forEach(i->newFacts.add("source:new_"+i));
        assertDoesNotThrow(()->new EventProgressState.Entry("test:new","b".repeat(64),-1,false,null,
                PlayState.DeliverySource.SYSTEM,"",false,newFacts,Map.of(),false));
    }

    @Test void rejectsUnknownMissingDuplicateAndWrongTypedSchemaFields() {
        JsonObject root = json(); root.addProperty("unknown", true);
        assertThrows(IllegalArgumentException.class, () -> EventProgressStore.decode(root.toString()));
        JsonObject missing = json(); missing.remove("revision");
        assertThrows(IllegalArgumentException.class, () -> EventProgressStore.decode(missing.toString()));
        JsonObject future = json(); future.addProperty("schemaVersion", 2);
        assertThrows(IllegalArgumentException.class, () -> EventProgressStore.decode(future.toString()));
        JsonObject wrongRevision = json(); wrongRevision.addProperty("revision", "1");
        assertThrows(IllegalArgumentException.class, () -> EventProgressStore.decode(wrongRevision.toString()));
        String duplicateRoot = EventProgressStore.encode(state(1)).stripTrailing();
        duplicateRoot = duplicateRoot.substring(0, duplicateRoot.length() - 1) + ",\"revision\":2}";
        String duplicate = duplicateRoot;
        assertThrows(IllegalArgumentException.class, () -> EventProgressStore.decode(duplicate));
        JsonObject duplicateEntry = json(); duplicateEntry.getAsJsonArray("entries").add(duplicateEntry.getAsJsonArray("entries").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> EventProgressStore.decode(duplicateEntry.toString()));
        JsonObject duplicateFact = json(); activation(duplicateFact).getAsJsonArray("seenFacts").add("occurrence:1");
        assertThrows(IllegalArgumentException.class, () -> EventProgressStore.decode(duplicateFact.toString()));
        String duplicateEpoch = EventProgressStore.encode(state(1)).replace("\"test:ready\":2", "\"test:ready\":2,\"test:ready\":3");
        assertThrows(IllegalArgumentException.class, () -> EventProgressStore.decode(duplicateEpoch));
    }

    @Test void rejectsMalformedActivationTypesPinsAndBounds() {
        List<Consumer<JsonObject>> corruptions = new ArrayList<>();
        corruptions.add(o -> o.addProperty("unknown", 1));
        corruptions.add(o -> o.remove("actor"));
        corruptions.add(o -> o.addProperty("actor", "invalid-uuid"));
        corruptions.add(o -> o.addProperty("source", "UNKNOWN"));
        corruptions.add(o -> o.addProperty("fingerprint", "A".repeat(64)));
        corruptions.add(o -> o.addProperty("triggered", "true"));
        corruptions.add(o -> o.addProperty("activeOrigin", "true"));
        corruptions.add(o -> o.addProperty("skipped", "false"));
        corruptions.add(o -> o.addProperty("startedAt", "40"));
        corruptions.add(o -> o.addProperty("startedAt", 1.5));
        corruptions.add(o -> o.addProperty("startedAt", -2));
        corruptions.add(o -> o.getAsJsonObject("offVersions").addProperty("test:ready", -1));
        corruptions.add(o -> o.getAsJsonObject("offVersions").addProperty("test:ready", "2"));
        corruptions.add(o -> o.getAsJsonObject("offVersions").addProperty("test:ready", 2.5));
        corruptions.add(o -> o.addProperty("occurrence", "x".repeat(257)));
        corruptions.add(o -> { JsonArray facts = new JsonArray(); IntStream.range(0, EventProgressState.Entry.MAX_FACTS + 1).forEach(i -> facts.add("fact:" + i)); o.add("seenFacts", facts); });
        corruptions.add(o -> { JsonObject epochs = new JsonObject(); IntStream.range(0, 33).forEach(i -> epochs.addProperty("test:f" + i, 0)); o.add("offVersions", epochs); });
        for (int index = 0; index < corruptions.size(); index++) {
            JsonObject root = json(); corruptions.get(index).accept(activation(root));
            assertThrows(IllegalArgumentException.class, () -> EventProgressStore.decode(root.toString()), "corruption " + index);
        }
    }

    @Test void countdownTimeUsesStrictIntegerLexicalFormAndLongBounds() {
        String encoded = EventProgressStore.encode(state(1));
        for (String invalid : List.of("40.0", "4e1", "9223372036854775808", "-9223372036854775809")) {
            String malformed = encoded.replace("\"startedAt\":40", "\"startedAt\":" + invalid);
            assertThrows(IllegalArgumentException.class, () -> EventProgressStore.decode(malformed), invalid);
        }
    }

    @Test void malformedLoadAndCompareBeforeReplaceNeverAlterForeignEvidence() throws Exception {
        Path file = directory.resolve("progress.json");
        EventProgressStore store = new EventProgressStore(file); store.load(); store.save(state(1));
        byte[] foreign = ("  " + EventProgressStore.encode(state(8))).getBytes(StandardCharsets.UTF_8);
        Files.write(file, foreign);
        assertThrows(IOException.class, () -> store.save(state(2)));
        assertArrayEquals(foreign, Files.readAllBytes(file));
        assertThrows(IOException.class, () -> store.save(state(3)));
        assertArrayEquals(foreign, Files.readAllBytes(file));
        assertEquals(state(8), store.load()); store.save(state(9));
        assertArrayEquals(foreign, Files.readAllBytes(file.resolveSibling("progress.json.bak")));
        byte[] invalid = "{\"schemaVersion\":99}".getBytes(StandardCharsets.UTF_8);
        Files.write(file, invalid);
        assertThrows(IOException.class, () -> store.load());
        assertThrows(IOException.class, () -> store.save(state(10)));
        assertArrayEquals(invalid, Files.readAllBytes(file));
    }

    @Test void presenceChangesAreRefusedAndBackupRetainsActualPreviousBytes() throws Exception {
        Path file = directory.resolve("progress.json");
        EventProgressStore store = new EventProgressStore(file); store.load();
        byte[] original = (" \n" + EventProgressStore.encode(state(1)) + "  ").getBytes(StandardCharsets.UTF_8);
        Files.write(file, original);
        assertThrows(IOException.class, () -> store.save(state(2)));
        assertArrayEquals(original, Files.readAllBytes(file));
        store.load(); store.save(state(2));
        assertArrayEquals(original, Files.readAllBytes(file.resolveSibling("progress.json.bak")));
        Files.delete(file);
        assertThrows(IOException.class, () -> store.save(state(3)));
        assertFalse(Files.exists(file));
    }

    @Test void failedBackupPreparationDoesNotPublishReplacementOrAdvanceObservedFingerprint() throws Exception {
        Path file = directory.resolve("progress.json");
        EventProgressStore store = new EventProgressStore(file); store.load(); store.save(state(1));
        byte[] original = Files.readAllBytes(file);
        Path blockedBackup = file.resolveSibling("progress.json.bak"); Files.createDirectory(blockedBackup);
        assertThrows(IOException.class, () -> store.save(state(2)));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(state(1), new EventProgressStore(file).load());
        try (var paths = Files.list(directory)) {
            assertTrue(paths.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
        Files.delete(blockedBackup);
        store.save(state(2));
        assertEquals(state(2), new EventProgressStore(file).load());
        assertArrayEquals(original, Files.readAllBytes(blockedBackup));
    }

    @Test void journalAndBackupSymlinksAreRefusedWithoutChangingTargets() throws Exception {
        Path target = directory.resolve("foreign.json"); byte[] original = EventProgressStore.encode(state(7)).getBytes(StandardCharsets.UTF_8);
        Files.write(target, original);
        Path link = directory.resolve("progress.json"); symlink(link, target);
        assertThrows(IOException.class, () -> new EventProgressStore(link).load());
        assertArrayEquals(original, Files.readAllBytes(target));
        Files.delete(link);
        EventProgressStore store = new EventProgressStore(link); store.load(); store.save(state(1));
        Path backup = directory.resolve("progress.json.bak"); symlink(backup, target);
        byte[] journal = Files.readAllBytes(link);
        assertThrows(IOException.class, () -> store.save(state(2)));
        assertArrayEquals(original, Files.readAllBytes(target));
        assertArrayEquals(journal, Files.readAllBytes(link));
    }

    @Test void savingThroughSymlinkDirectoryIsRefused() throws Exception {
        Path actual = directory.resolve("actual"); Files.createDirectory(actual);
        Path linked = directory.resolve("linked");
        EventProgressStore store = new EventProgressStore(linked.resolve("progress.json")); store.load();
        symlink(linked, actual);
        assertThrows(IOException.class, () -> store.save(state(1)));
        assertFalse(Files.exists(actual.resolve("progress.json")));
    }

    private static void symlink(Path link, Path target) throws IOException {
        try { Files.createSymbolicLink(link, target); }
        catch (UnsupportedOperationException | IOException failure) {
            assumeTrue(false, "Symbolic link creation unavailable: " + failure.getClass().getSimpleName());
        }
    }
    private static EventProgressState state(long revision) {
        EventProgressState.Entry entry = entry("test:event", 40, true, false);
        return new EventProgressState(revision, Map.of(entry.eventId(), entry));
    }
    private static EventProgressState.Entry entry(String id, long startedAt, boolean triggered, boolean skipped) {
        return new EventProgressState.Entry(id, "a".repeat(64), startedAt, triggered,
                UUID.fromString("11111111-1111-1111-1111-111111111111"), PlayState.DeliverySource.SYSTEM,
                "occurrence:1", true, Set.of("occurrence:1", "occurrence:2"), Map.of("test:ready", 2L), skipped);
    }
    private static JsonObject json() { return BookConfig.strictObject(EventProgressStore.encode(state(1))); }
    private static JsonObject activation(JsonObject root) { return root.getAsJsonArray("entries").get(0).getAsJsonObject(); }
}
