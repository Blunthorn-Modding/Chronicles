package io.github.chronicle.core.play;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EventOccurrenceStoreTest {
    @TempDir Path directory;
    private final UUID actor = UUID.randomUUID(), operation = UUID.randomUUID();
    private EventOccurrenceState.SourceSnapshot source() { return new EventOccurrenceState.SourceSnapshot("source:first", "test:prayer", "test:altar", "", actor, true, Map.of("test:ready", true), "prayer:exact"); }
    private EventOccurrenceState.Claim claim() { return new EventOccurrenceState.Claim("test:first_prayer", operation, "a".repeat(64), source(), 1, 20, List.of(actor), EventOccurrenceState.Stage.CLAIMED, "Claimed"); }
    private EventOccurrenceState.ActorConsumption consumption() { return new EventOccurrenceState.ActorConsumption("test:first_prayer", actor, operation, source().id(), "a".repeat(64)); }
    @Test void pendingClaimAndActorSurviveRestartWithoutFingerprintDeduplication() throws Exception {
        Path file = directory.resolve("event-occurrences.json"); var store = new EventOccurrenceStore(file); assertEquals(EventOccurrenceState.empty(), store.load());
        var consumed = consumption(); var state = new EventOccurrenceState(1, Map.of(operation, claim()), Map.of(consumed.key(), consumed)); store.save(state);
        assertEquals(state, new EventOccurrenceStore(file).load());
        store.publishActor(consumed); store.save(new EventOccurrenceState(2, Map.of(), Map.of()));
        var restarted = new EventOccurrenceStore(file); restarted.load();
        assertEquals(consumed, restarted.actor("test:first_prayer", actor).orElseThrow());
        assertTrue(restarted.actor("test:different_event", actor).isEmpty());
        assertTrue(restarted.actor("test:first_prayer", UUID.randomUUID()).isEmpty());
        assertThrows(IOException.class, () -> restarted.publishActor(new EventOccurrenceState.ActorConsumption(consumed.eventId(), actor, UUID.randomUUID(), "another-source", "b".repeat(64))));
    }
    @Test void exactTerminalPublicationIsIdempotentAndConflictsPreserveOriginal() throws Exception {
        var store = new EventOccurrenceStore(directory.resolve("event-occurrences.json")); store.load();
        var decision = new EventOccurrenceState.Decision(EventOccurrenceState.Decision.Kind.RETIRED, claim().eventId(), "a".repeat(64), source(), operation, List.of(actor));
        store.publishDecision(decision); store.publishDecision(decision);
        assertEquals(decision, store.decision(claim().eventId(), source().id()).orElseThrow());
        assertThrows(IOException.class, () -> store.publishDecision(new EventOccurrenceState.Decision(EventOccurrenceState.Decision.Kind.BASELINE, claim().eventId(), "a".repeat(64), source(), null, List.of())));
        assertEquals(decision, store.decision(claim().eventId(), source().id()).orElseThrow());
    }
    @Test void missingInitializedJournalAndMissingArchiveAnchorNeverBecomeFreshWorld() throws Exception {
        Path file = directory.resolve("event-occurrences.json"); new EventOccurrenceStore(file).load(); byte[] saved = Files.readAllBytes(file);
        Files.delete(file); assertThrows(IOException.class, () -> new EventOccurrenceStore(file).load());
        Files.write(file, saved);
        try (var files = Files.list(directory.resolve("event-actor-history"))) { Files.delete(files.findFirst().orElseThrow()); }
        assertThrows(IOException.class, () -> new EventOccurrenceStore(file).load());
    }
    @Test void malformedUtf8DuplicateFieldsAndExternalReplacementArePreserved() throws Exception {
        Path file = directory.resolve("event-occurrences.json"); var store = new EventOccurrenceStore(file); store.load();
        byte[] bad = {(byte)0xc3, (byte)0x28}; Files.write(file, bad);
        assertThrows(IOException.class, () -> new EventOccurrenceStore(file).load()); assertArrayEquals(bad, Files.readAllBytes(file));
        assertThrows(IOException.class, () -> store.save(EventOccurrenceState.empty()));
        assertThrows(IllegalArgumentException.class, () -> EventOccurrenceStore.decodeState("{\"schemaVersion\":1,\"schemaVersion\":1,\"revision\":0,\"claims\":[],\"actors\":[]}"));
    }
    @Test void roundTripRetainsFullSourceAndMonotonicStages() {
        var state = new EventOccurrenceState(1, Map.of(operation, claim()), Map.of());
        assertEquals(state, EventOccurrenceStore.decodeState(EventOccurrenceStore.encodeState(state)));
        var complete = claim().with(EventOccurrenceState.Stage.COMPLETE, "Done");
        assertThrows(IllegalArgumentException.class, () -> complete.with(EventOccurrenceState.Stage.CLAIMED, "Retry"));
        assertThrows(IllegalArgumentException.class, () -> new EventOccurrenceState(1, Map.of(operation, claim(), UUID.randomUUID(), claim()), Map.of()));
    }
}
