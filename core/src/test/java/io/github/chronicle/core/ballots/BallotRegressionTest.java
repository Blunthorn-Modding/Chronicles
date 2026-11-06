package io.github.chronicle.core.ballots;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BallotRegressionTest {
    private static final UUID OPERATOR = UUID.randomUUID();
    private static final List<BallotOption> OPTIONS = List.of(
            new BallotOption("yes", "Yes", true), new BallotOption("no", "No", false),
            new BallotOption("abstain", "Abstain", false));
    private static BallotDefinition definition(int count, BallotVisibility visibility) {
        Map<String, BallotElector> electorate = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) electorate.put("house" + i, new BallotElector(UUID.randomUUID(), "House " + i));
        return new BallotDefinition(UUID.randomUUID(), "test", "Question", OPTIONS, electorate,
                BallotDefinition.majority(count), visibility);
    }
    private static final class MemoryRepository implements BallotRepository {
        BallotState state = BallotState.empty(); boolean fail;
        public BallotState load() { return state; }
        public void save(BallotState next) throws IOException { if (fail) throw new IOException("injected failure"); state = next; }
    }
    @Test void failedWritesPreserveStateRevisionAndTerminalFields() throws Exception {
        MemoryRepository repository = new MemoryRepository();
        BallotService service = new BallotService(repository, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        BallotDefinition d = definition(5, BallotVisibility.PUBLIC);
        repository.fail = true;
        assertThrows(IOException.class, () -> service.open(d, OPERATOR));
        assertEquals(BallotState.empty(), service.state());
        repository.fail = false; service.open(d, OPERATOR);
        BallotState before = service.state(); repository.fail = true;
        assertThrows(IOException.class, () -> service.cast(d.id(), d.electorate().get("house0").actor(), "yes"));
        assertEquals(before, service.state());
        assertThrows(IOException.class, () -> service.close(d.id(), OPERATOR));
        assertEquals(before, service.state());
        assertEquals("", service.state().ballots().get(d.id()).finishedAt());
        assertNull(service.state().ballots().get(d.id()).finishedBy());
        repository.fail = false; Ballot closed = service.close(d.id(), OPERATOR);
        assertEquals("2026-01-01T00:00:00Z", closed.finishedAt());
        assertEquals(before.revision() + 1, service.state().revision());
        assertEquals(closed, service.close(d.id(), UUID.randomUUID()));
        assertEquals(before.revision() + 1, service.state().revision());
    }
    @Test void publicViewsAndFrozenElectorates() throws Exception {
        for (int count : List.of(5, 8)) {
            BallotService service = new BallotService(new MemoryRepository(), Clock.systemUTC());
            BallotDefinition d = definition(count, BallotVisibility.PUBLIC); UUID voter = d.electorate().get("house0").actor();
            service.open(d, OPERATOR); service.cast(d.id(), voter, "yes");
            assertThrows(IllegalStateException.class, () -> service.cast(d.id(), voter, "no"));
            service.cast(d.id(), voter, "yes");
            BallotView live = service.view(d.id(), UUID.randomUUID());
            assertEquals(count, live.electorateLabels().size()); assertEquals(BallotDefinition.majority(count), live.threshold());
            assertEquals(Map.of("house0", "yes"), live.namedVotes()); assertEquals(1, live.totals().get("yes"));
            assertTrue(live.passedOptions().isEmpty());
            service.cast(d.id(), d.electorate().get("house1").actor(), "abstain"); service.close(d.id(), OPERATOR);
            BallotView result = service.view(d.id(), voter);
            assertEquals(count, result.electorateLabels().size()); assertEquals(1, result.totals().get("abstain"));
            assertEquals(2, result.namedVotes().size()); assertTrue(result.passedOptions().isEmpty());
        }
    }
    @Test void secretVoidRetainsVotesButConcealsThemAndRequiresNewId() throws Exception {
        BallotService service = new BallotService(new MemoryRepository(), Clock.systemUTC());
        BallotDefinition d = definition(5, BallotVisibility.SECRET); UUID voter = d.electorate().get("house0").actor();
        service.open(d, OPERATOR); assertThrows(IllegalStateException.class, () -> service.open(d, UUID.randomUUID()));
        service.cast(d.id(), voter, "yes"); Ballot voided = service.voidBallot(d.id(), OPERATOR, "Cancelled");
        assertEquals(voided, service.voidBallot(d.id(), UUID.randomUUID(), "Cancelled"));
        assertThrows(IllegalStateException.class, () -> service.voidBallot(d.id(), OPERATOR, "Different"));
        assertThrows(IllegalStateException.class, () -> service.open(d, OPERATOR));
        assertThrows(IllegalStateException.class, () -> service.close(d.id(), OPERATOR));
        assertEquals("yes", voided.votes().get("house0"));
        for (UUID viewer : List.of(voter, OPERATOR, UUID.randomUUID())) {
            BallotView view = service.view(d.id(), viewer); assertTrue(view.totals().isEmpty());
            assertTrue(view.namedVotes().isEmpty()); assertTrue(view.passedOptions().isEmpty());
            assertEquals("Cancelled", view.reason());
        }
        assertEquals("yes", service.view(d.id(), voter).ownChoice());
        assertEquals("", service.view(d.id(), OPERATOR).ownChoice());
        BallotDefinition replacement = definition(5, BallotVisibility.SECRET); service.open(replacement, OPERATOR);
        assertEquals(BallotStatus.OPEN, service.state().ballots().get(replacement.id()).status());
    }
    @Test void definitionBoundsAndUniqueness() {
        UUID actor = UUID.randomUUID(); BallotElector elector = new BallotElector(actor, "House");
        assertThrows(IllegalArgumentException.class, () -> new BallotDefinition(UUID.randomUUID(), "test", "Title", OPTIONS, Map.of(), 1, BallotVisibility.PUBLIC));
        assertThrows(IllegalArgumentException.class, () -> new BallotDefinition(UUID.randomUUID(), "test", "Title", OPTIONS, Map.of("a", elector, "b", elector), 1, BallotVisibility.PUBLIC));
        assertThrows(IllegalArgumentException.class, () -> new BallotDefinition(UUID.randomUUID(), "test", "Title", List.of(OPTIONS.get(0)), Map.of("a", elector), 1, BallotVisibility.PUBLIC));
        assertThrows(IllegalArgumentException.class, () -> new BallotDefinition(UUID.randomUUID(), "test", "x".repeat(513), OPTIONS, Map.of("a", elector), 1, BallotVisibility.PUBLIC));
        assertThrows(IllegalArgumentException.class, () -> new BallotDefinition(UUID.randomUUID(), "test", "Title", OPTIONS, Map.of("a", elector), 2, BallotVisibility.PUBLIC));
        assertThrows(IllegalArgumentException.class, () -> new BallotOption("bad key", "Label", true));
        assertThrows(IllegalArgumentException.class, () -> new BallotElector(actor, "x".repeat(161)));
        assertThrows(IllegalArgumentException.class, () -> new BallotOption("x", "Bad\u00a7label", true));
    }
}
