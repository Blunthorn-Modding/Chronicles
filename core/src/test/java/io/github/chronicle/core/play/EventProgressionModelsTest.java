package io.github.chronicle.core.play;

import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class EventProgressionModelsTest {
    @TempDir Path directory;
    private static final String LEGACY_DEFINITION = "{\"id\":\"test:event\",\"revision\":1,\"label\":\"Event\",\"description\":\"\",\"text\":\"Message\",\"sound\":\"\",\"audience\":\"PARTICIPANTS\",\"audienceRegion\":null,\"changes\":[]}";
    private static final String LEGACY_CONFIGURATION = "{\"definition\":" + LEGACY_DEFINITION
            + ",\"mode\":\"MANUAL\",\"triggerRegion\":null,\"armedAtTicks\":5,\"delayTicks\":0,\"requiredFlag\":\"test:ready\",\"enabled\":true}";

    @Test void legacyBytesAndFingerprintsRemainExactIncludingImmutableArchiveReuse() throws Exception {
        EventDefinition definition = EventJson.decodeDefinition(BookConfig.strictObject(LEGACY_DEFINITION));
        EventConfiguration configuration = EventJson.decodeConfiguration(BookConfig.strictObject(LEGACY_CONFIGURATION));
        assertEquals(1, definition.formatVersion());
        assertEquals(Map.of(), definition.flagChanges());
        assertEquals(LEGACY_DEFINITION, EventJson.encodeDefinition(definition).toString());
        assertEquals("62390884d64691a7f8f905781aa3e657e4a0f2409610c4fffcc5ad7e45bdc798", definition.fingerprint());
        assertEquals(LEGACY_CONFIGURATION, EventJson.encodeConfiguration(configuration).toString());
        assertEquals("263160a920c6338e4f844cb0f5f779d1effea62c95e07b23602a13cee186b45a", configuration.fingerprint());
        String executionJson = "{\"eventId\":\"test:event\",\"operationId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"configuration\":"
                + LEGACY_CONFIGURATION + ",\"phase\":\"COMPLETE\",\"source\":\"MANUAL\",\"actor\":\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\",\"session\":1,\"elapsedTicks\":10,\"recipients\":[\"11111111-1111-1111-1111-111111111111\"],\"worldPlanHash\":\"\",\"detail\":\"done\"}";
        EventExecution execution = EventJson.decodeExecution(BookConfig.strictObject(executionJson));
        assertEquals(executionJson, EventJson.encodeExecution(execution).toString());
        assertEquals(configuration.fingerprint(), execution.configurationFingerprint());
        PlayStore store = new PlayStore(directory.resolve("play.json"));
        store.archiveEvent(execution);
        List<Path> archived;
        try (var paths = Files.walk(directory)) { archived = paths.filter(Files::isRegularFile).toList(); }
        assertEquals(1, archived.size());
        byte[] original = Files.readAllBytes(archived.getFirst());
        store.archiveEvent(EventJson.decodeExecution(BookConfig.strictObject(executionJson)));
        assertArrayEquals(original, Files.readAllBytes(archived.getFirst()));
    }

    @Test void currentDefinitionPinsAtomicFlagPatchAndAcceptsSilentEffects() {
        Map<String, Boolean> flags = new HashMap<>(Map.of("test:b", false, "test:a", true));
        EventDefinition definition = silent(flags);
        flags.put("test:a", false);
        assertEquals(2, definition.formatVersion());
        assertEquals(List.of("test:a", "test:b"), new ArrayList<>(definition.flagChanges().keySet()));
        assertTrue(definition.flagChanges().get("test:a"));
        assertTrue(definition.progressOnly());
        assertFalse(definition.hasMedia());
        assertThrows(UnsupportedOperationException.class, () -> definition.flagChanges().clear());
        assertEquals(definition, EventJson.decodeDefinition(EventJson.encodeDefinition(definition)));
        assertNotEquals(definition.fingerprint(), silent(Map.of("test:a", false, "test:b", false)).fingerprint());
        assertThrows(IllegalArgumentException.class, () -> silent(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> silent(Map.of("bad flag", true)));
        Map<String, Boolean> tooMany = new HashMap<>();
        IntStream.range(0, 33).forEach(index -> tooMany.put("test:f" + index, true));
        assertThrows(IllegalArgumentException.class, () -> silent(tooMany));
        assertThrows(IllegalArgumentException.class, () -> new EventDefinition("test:event", 1, "Event", "", "Message", "",
                ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of(), Map.of("test:a", true), 1));
    }

    @Test void currentRequirementsSourcesAndDelayAreBoundedAndFingerprintPinned() {
        EventDefinition definition = silent(Map.of("test:done", true));
        EventSource source = new EventSource("chronicle:book_opened", "test:book", "read");
        EventConfiguration first = current(definition, PlayState.Mode.SYSTEM, 40, List.of("test:b", "test:a"), source);
        assertEquals(List.of("test:a", "test:b"), first.requiredFlags());
        assertEquals("test:a", first.requiredFlag());
        assertEquals(40, first.delayTicks());
        assertEquals(2, first.formatVersion());
        assertEquals(first, EventJson.decodeConfiguration(EventJson.encodeConfiguration(first)));
        assertEquals(first.fingerprint(), current(definition, PlayState.Mode.SYSTEM, 40, List.of("test:a", "test:b"), source).fingerprint());
        assertNotEquals(first.fingerprint(), current(definition, PlayState.Mode.SYSTEM, 40, List.of("test:a"), source).fingerprint());
        assertNotEquals(first.fingerprint(), current(definition, PlayState.Mode.SYSTEM, 40, first.requiredFlags(), new EventSource(source.type(), "test:other", "read")).fingerprint());
        assertNotEquals(first.fingerprint(), current(definition, PlayState.Mode.SYSTEM, 40, first.requiredFlags(), new EventSource(source.type(), source.target(), "found")).fingerprint());
        assertThrows(IllegalArgumentException.class, () -> current(definition, PlayState.Mode.SYSTEM, 0, List.of(), EventSource.empty()));
        assertThrows(IllegalArgumentException.class, () -> current(definition, PlayState.Mode.TIMER, 0, List.of(), EventSource.empty()));
        assertThrows(IllegalArgumentException.class, () -> current(definition, PlayState.Mode.MANUAL, -1, List.of(), EventSource.empty()));
        assertThrows(IllegalArgumentException.class, () -> current(definition, PlayState.Mode.MANUAL, EventConfiguration.MAX_DELAY_TICKS + 1, List.of(), EventSource.empty()));
        assertThrows(IllegalArgumentException.class, () -> current(definition, PlayState.Mode.CONDITION, 1, List.of("test:a", "test:a"), EventSource.empty()));
        assertThrows(IllegalArgumentException.class, () -> current(definition, PlayState.Mode.CONDITION, 1,
                IntStream.range(0, 33).mapToObj(i -> "test:f" + i).toList(), EventSource.empty()));
        for (PlayState.Mode mode : List.of(PlayState.Mode.MANUAL, PlayState.Mode.ENTRY, PlayState.Mode.CONDITION, PlayState.Mode.TIMER)) {
            assertEquals(40, current(definition, mode, 40, List.of("test:a"), EventSource.empty()).delayTicks());
        }
        assertThrows(IllegalArgumentException.class, () -> new EventSource("not namespaced", "", ""));
        assertThrows(IllegalArgumentException.class, () -> new EventSource("test:source", "x".repeat(161), ""));
        assertThrows(IllegalArgumentException.class, () -> new EventSource("test:source", "", "x".repeat(129)));
        assertThrows(IllegalArgumentException.class, () -> new EventSource("test:source", "\n", ""));
    }

    @Test void strictCurrentCodecsRejectMixedShapesUnknownVersionsAndMalformedFlagValues() {
        JsonObject definition = EventJson.encodeDefinition(silent(Map.of("test:done", true)));
        definition.getAsJsonObject("flagChanges").addProperty("test:done", "true");
        assertThrows(IllegalArgumentException.class, () -> EventJson.decodeDefinition(definition));
        JsonObject legacy = BookConfig.strictObject(LEGACY_DEFINITION);
        legacy.addProperty("formatVersion", 1);
        assertThrows(IllegalArgumentException.class, () -> EventJson.decodeDefinition(legacy));
        JsonObject current = EventJson.encodeConfiguration(current(silent(Map.of("test:done", true)), PlayState.Mode.MANUAL, 0, List.of(), EventSource.empty()));
        current.addProperty("requiredFlag", "test:unexpected");
        assertThrows(IllegalArgumentException.class, () -> EventJson.decodeConfiguration(current));
        current.remove("requiredFlag"); current.addProperty("formatVersion", 99);
        assertThrows(IllegalArgumentException.class, () -> EventJson.decodeConfiguration(current));
    }

    @Test void emptyRecipientsAreAllowedOnlyForFlagOnlyExecution() {
        EventConfiguration silent = current(silent(Map.of("test:done", true)), PlayState.Mode.TIMER, 20, List.of(), EventSource.empty());
        EventExecution execution = new EventExecution("test:event", UUID.randomUUID(), silent, EventExecution.Phase.EVENT_OUTCOME,
                PlayState.DeliverySource.TIMER, null, 1, 0, List.of(), "", "");
        assertEquals(execution, EventJson.decodeExecution(EventJson.encodeExecution(execution)));
        EventConfiguration media = EventJson.decodeConfiguration(BookConfig.strictObject(LEGACY_CONFIGURATION));
        assertThrows(IllegalArgumentException.class, () -> new EventExecution("test:event", UUID.randomUUID(), media,
                EventExecution.Phase.EVENT_OUTCOME, PlayState.DeliverySource.TIMER, null, 1, 10, List.of(), "", ""));
    }

    @Test void schemaTwoLoadsAndFourWritesPreserveLegacyPins() throws Exception {
        Path file = directory.resolve("play.json");
        Files.writeString(file, "{\"schemaVersion\":2,\"revision\":1,\"elapsedTicks\":10,\"participants\":[],\"bindings\":[],\"deliveries\":[],\"events\":["
                + LEGACY_CONFIGURATION + "],\"executions\":[]}");
        PlayStore store = new PlayStore(file);
        PlayState state = store.load();
        assertEquals(1, state.events().get("test:event").formatVersion());
        store.save(state);
        assertTrue(Files.readString(file).contains("\"schemaVersion\":4"));
        assertEquals(state, new PlayStore(file).load());
        assertEquals(LEGACY_CONFIGURATION, EventJson.encodeConfiguration(state.events().get("test:event")).toString());
    }

    private static EventDefinition silent(Map<String, Boolean> flags) {
        return new EventDefinition("test:event", 1, "Event", "", "", "", ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of(), flags);
    }
    private static EventConfiguration current(EventDefinition definition, PlayState.Mode mode, long delay, List<String> flags, EventSource source) {
        return new EventConfiguration(definition, mode, mode == PlayState.Mode.ENTRY ? Optional.of(new EventRegionReference("region", 1)) : Optional.empty(), 0, delay, flags, true, source);
    }
}
