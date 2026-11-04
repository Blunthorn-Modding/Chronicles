package io.github.chronicle.core.play;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioDefaultsTest {
    @TempDir Path directory;
    private static EventConfiguration event(String id, long revision, long ticks) {
        var definition = new EventDefinition(id, revision, "Event", "", "Text", "",
                ObservationCatalog.Audience.PARTICIPANTS, Optional.empty(), List.of());
        return new EventConfiguration(definition, PlayState.Mode.MANUAL, Optional.empty(), ticks, 0, "", false);
    }
    private static final class Memory implements PlayRepository {
        PlayState state = PlayState.empty(); int saves; boolean fail;
        public PlayState load() { return state; }
        public void save(PlayState next) throws IOException { if (fail) throw new IOException("failed"); state = next; saves++; }
    }
    @Test void bootIsAtomicIdempotentAndDeletionIsTombstoned() throws Exception {
        var repo = new Memory(); var service = new PlayService(repo);
        var ready = Map.of("test:a", event("test:a", 1, 0));
        repo.fail = true;
        assertThrows(IOException.class, () -> service.bootstrapScenarioEvents(ready, Set.of("test:a", "test:b"), Set.of()));
        assertEquals(PlayState.empty(), service.state());
        repo.fail = false;
        service.advance(true);
        assertEquals(Set.of("test:a"), service.bootstrapScenarioEvents(ready, Set.of("test:a", "test:b"), Set.of()));
        assertEquals(1, service.state().events().get("test:a").armedAtTicks());
        assertEquals(Set.of("test:b"), service.state().scenarioDrafts());
        var configured = service.state().events().get("test:a");
        service.advance(true);
        assertTrue(service.bootstrapScenarioEvents(ready, Set.of("test:a", "test:b"), Set.of()).isEmpty());
        assertEquals(1, repo.saves); assertEquals(configured, service.state().events().get("test:a"));
        service.deleteEvent("test:a", configured);
        service.discardScenarioDraft("test:b");
        int saves = repo.saves;
        assertTrue(service.bootstrapScenarioEvents(ready, Set.of("test:a", "test:b"), Set.of()).isEmpty());
        assertEquals(saves, repo.saves); assertTrue(service.state().events().isEmpty());
    }
    @Test void draftCanBecomeReadyOrBeEditedWithoutLosingMarker() throws Exception {
        var service = new PlayService(new Memory());
        service.bootstrapScenarioEvents(Map.of(), Set.of("test:a", "test:b", "test:reserved"), Set.of("test:reserved"));
        assertEquals(Set.of("test:a", "test:b"), service.state().scenarioDrafts());
        assertEquals(Set.of("test:a"), service.bootstrapScenarioEvents(Map.of("test:a", event("test:a", 1, 0)), Set.of("test:a", "test:b", "test:reserved"), Set.of("test:reserved")));
        service.putEvent(event("test:b", service.nextEventRevision(), service.elapsedTicks()), null, true);
        assertTrue(service.state().scenarioDrafts().isEmpty());
        assertEquals(Set.of("test:a", "test:b", "test:reserved"), service.state().scenarioDefaults());
    }
    @Test void existingConfigurationWinsAndOrdinaryCommitsKeepMarkers() throws Exception {
        var repo = new Memory(); var service = new PlayService(repo);
        var original = event("test:a", service.nextEventRevision(), service.elapsedTicks());
        service.putEvent(original, null, true);
        service.bootstrapScenarioEvents(Map.of("test:a", event("test:a", 1, 0)), Set.of("test:a", "test:b"), Set.of());
        assertEquals(original, service.state().events().get("test:a"));
        service.setParticipant(java.util.UUID.randomUUID(), true);
        assertEquals(Set.of("test:a", "test:b"), service.state().scenarioDefaults());
        assertEquals(Set.of("test:b"), service.state().scenarioDrafts());
        var restarted = new PlayService(repo);
        assertEquals(original, restarted.state().events().get("test:a"));
    }
    @Test void orphanedProgressAndOccurrenceIdsAreNeverAdoptedOrArmed() throws Exception {
        var service=new PlayService(new Memory());
        var ids=Set.of("test:progress_only","test:claim_only");
        var candidates=Map.of("test:progress_only",event("test:progress_only",1,0),"test:claim_only",event("test:claim_only",1,0));
        assertTrue(service.bootstrapScenarioEvents(candidates,ids,ids).isEmpty());
        assertEquals(ids,service.state().scenarioDefaults());
        assertTrue(service.state().events().isEmpty());
        assertTrue(service.state().scenarioDrafts().isEmpty());
        assertTrue(service.bootstrapScenarioEvents(candidates,ids,Set.of()).isEmpty());
        assertTrue(service.state().events().isEmpty());
    }
    @Test void schemasAndStrictDraftValidation() throws Exception {
        Path file = directory.resolve("play.json");
        String fields = "\"revision\":0,\"elapsedTicks\":0,\"participants\":[],\"bindings\":[],\"deliveries\":[]";
        for (int schema = 1; schema <= 3; schema++) {
            Files.writeString(file, "{\"schemaVersion\":" + schema + "," + fields + (schema >= 2 ? ",\"events\":[],\"executions\":[]" : "") + "}");
            assertEquals(Set.of(), new PlayStore(file).load().scenarioDefaults());
        }
        var state = new PlayState(1, 0, Set.of(), Map.of(), Map.of(), Map.of(), Map.of(), Set.of("test:a"), Set.of("test:a"));
        new PlayStore(file).save(state); assertEquals(state, new PlayStore(file).load());
        Files.writeString(file, "{\"schemaVersion\":4," + fields + ",\"events\":[],\"executions\":[],\"scenarioDefaults\":[],\"scenarioDrafts\":[\"test:a\"]}");
        assertThrows(IOException.class, () -> new PlayStore(file).load());
    }
}
