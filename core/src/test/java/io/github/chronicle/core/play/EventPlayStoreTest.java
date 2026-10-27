package io.github.chronicle.core.play;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPlayStoreTest {
    @TempDir Path directory;

    @Test
    void schemaOneLoadsAndUpgradesWithoutLosingExistingRuntimeState() throws IOException {
        Path file = directory.resolve("play-runtime.json");
        String fingerprint = "0".repeat(64);
        String legacy = "{\"schemaVersion\":1,\"revision\":2,\"elapsedTicks\":40,"
                + "\"participants\":[\"11111111-1111-1111-1111-111111111111\"],"
                + "\"bindings\":[{\"id\":\"quiet_below:cue\",\"revision\":1,\"definitionFingerprint\":\"" + fingerprint
                + "\",\"mode\":\"MANUAL\",\"regionId\":\"\",\"regionRevision\":0,\"armedAtTicks\":0,\"delayTicks\":0,\"requiredFlag\":\"\"}],"
                + "\"deliveries\":[{\"id\":\"quiet_below:cue\",\"status\":\"DELIVERED\",\"source\":\"MANUAL\","
                + "\"actor\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"session\":1,\"elapsedTicks\":40,"
                + "\"recipients\":[\"11111111-1111-1111-1111-111111111111\"]}]}";
        Files.writeString(file, legacy);
        PlayStore store = new PlayStore(file);
        PlayState loaded = store.load();
        assertEquals(2, loaded.revision());
        assertEquals(40, loaded.elapsedTicks());
        assertEquals(1, loaded.participants().size());
        assertEquals(1, loaded.bindings().size());
        assertEquals(1, loaded.deliveries().size());
        assertTrue(loaded.events().isEmpty());
        assertTrue(loaded.executions().isEmpty());

        store.save(loaded);
        assertTrue(Files.readString(file).contains("\"schemaVersion\":2"));
        assertEquals(loaded, new PlayStore(file).load());
        assertEquals(loaded, new PlayStore(file.resolveSibling("play-runtime.json.bak")).load());
    }

    @Test
    void schemaTwoRoundTripsEveryExecutionPhaseAndPinnedConfiguration() throws IOException {
        Map<String, EventConfiguration> events = new LinkedHashMap<>();
        Map<String, EventExecution> executions = new LinkedHashMap<>();
        int index = 0;
        for (EventExecution.Phase phase : EventExecution.Phase.values()) {
            String id = "quiet_below:phase_" + phase.name().toLowerCase(java.util.Locale.ROOT);
            EventConfiguration configuration = EventExecutionTest.configuration(
                    EventExecutionTest.definition(id, true), PlayState.Mode.ENTRY,
                    Optional.of(new EventRegionReference("entrance", 7)), "quiet_below:ready", true);
            UUID operation = UUID.nameUUIDFromBytes(("operation-" + index++).getBytes(StandardCharsets.UTF_8));
            EventExecution execution = new EventExecution(id, operation, configuration, phase,
                    PlayState.DeliverySource.ENTRY,
                    UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), 2, 10,
                    List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")), "f".repeat(64), phase.name());
            events.put(id, configuration);
            executions.put(id, execution);
        }
        PlayState state = new PlayState(2, 20, Set.of(), Map.of(), Map.of(), events, executions);
        Path file = directory.resolve("play-runtime.json");
        new PlayStore(file).save(state);
        assertEquals(state, new PlayStore(file).load());
    }

    @Test
    void eventArchiveIsImmutableIdempotentAndValidatesExistingEvidence() throws IOException {
        Path file = directory.resolve("play-runtime.json");
        PlayStore store = new PlayStore(file);
        EventConfiguration configuration = EventExecutionTest.configuration(
                EventExecutionTest.definition("quiet_below:archive", true), PlayState.Mode.TIMER,
                Optional.empty(), "", true);
        EventExecution execution = EventExecutionTest.execution(configuration, EventExecution.Phase.COMPLETE,
                PlayState.DeliverySource.TIMER, null, "done");
        store.archiveEvent(execution);
        Path archive = directory.resolve("event-history").resolve(execution.operationId() + ".json");
        byte[] original = Files.readAllBytes(archive);
        store.archiveEvent(execution);
        assertArrayEquals(original, Files.readAllBytes(archive));

        EventExecution different = execution.with(EventExecution.Phase.COMPLETE, "different");
        assertThrows(IOException.class, () -> store.archiveEvent(different));
        assertArrayEquals(original, Files.readAllBytes(archive));

        UUID corruptOperation = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        EventExecution corruptTarget = new EventExecution(execution.eventId(), corruptOperation, configuration,
                EventExecution.Phase.COMPLETE, execution.source(), execution.actor(), execution.session(),
                execution.elapsedTicks(), execution.recipients(), execution.worldPlanHash(), execution.detail());
        Path corrupt = directory.resolve("event-history").resolve(corruptOperation + ".json");
        Files.writeString(corrupt, "not json");
        assertThrows(IOException.class, () -> store.archiveEvent(corruptTarget));
        assertEquals("not json", Files.readString(corrupt));
    }
}
