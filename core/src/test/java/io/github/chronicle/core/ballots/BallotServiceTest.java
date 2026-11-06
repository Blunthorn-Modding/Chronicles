package io.github.chronicle.core.ballots;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BallotServiceTest {
    @TempDir Path directory;
    private final UUID actor = UUID.randomUUID(), operator = UUID.randomUUID();
    private BallotDefinition definition(BallotVisibility visibility) {
        return new BallotDefinition(UUID.randomUUID(), "test", "A question?", List.of(new BallotOption("yes", "Yes", true), new BallotOption("no", "No", false)), Map.of("house", new BallotElector(actor, "House")), 1, visibility);
    }
    @Test void transitionsAndSecretViewsSurviveRestart() throws Exception {
        Path path = directory.resolve("ballots.json"); BallotService service = new BallotService(new BallotStore(path), Clock.systemUTC()); BallotDefinition d = definition(BallotVisibility.SECRET);
        assertEquals(5, BallotDefinition.majority(8)); assertEquals(6, BallotDefinition.twoThirds(8)); assertEquals(3, BallotDefinition.majority(5)); assertEquals(4, BallotDefinition.twoThirds(5));
        service.open(d, operator); service.open(d, operator);
        assertThrows(IllegalStateException.class, () -> service.open(definition(BallotVisibility.PUBLIC), operator));
        service.cast(d.id(), actor, "yes"); service.cast(d.id(), actor, "yes");
        assertThrows(IllegalStateException.class, () -> service.cast(d.id(), actor, "no"));
        assertThrows(IllegalArgumentException.class, () -> service.cast(d.id(), UUID.randomUUID(), "yes"));
        assertTrue(service.view(d.id(), operator).totals().isEmpty()); assertEquals("yes", service.view(d.id(), actor).ownChoice());
        BallotService restarted = new BallotService(new BallotStore(path), Clock.systemUTC());
        assertTrue(restarted.view(d.id(), operator).namedVotes().isEmpty()); restarted.close(d.id(), operator); Ballot closed = restarted.close(d.id(), operator);
        assertEquals(1, restarted.view(d.id(), operator).totals().get("yes")); assertEquals(List.of("yes"), restarted.view(d.id(), operator).passedOptions()); assertEquals(BallotStatus.CLOSED, closed.status());
    }
    @Test void voidAndCorruptExternalFile() throws Exception {
        Path path = directory.resolve("ballots.json"); BallotService service = new BallotService(new BallotStore(path), Clock.systemUTC()); BallotDefinition d = definition(BallotVisibility.SECRET); service.open(d, operator); service.cast(d.id(), actor, "no");
        assertThrows(IllegalArgumentException.class, () -> service.voidBallot(d.id(), operator, "")); Ballot result = service.voidBallot(d.id(), operator, "Cancelled"); assertEquals(result, service.voidBallot(d.id(), operator, "Cancelled"));
        assertEquals("no", result.votes().get("house")); assertTrue(service.view(d.id(), operator).totals().isEmpty());
        Files.writeString(path, "{}"); assertThrows(java.io.IOException.class, () -> service.open(definition(BallotVisibility.PUBLIC), operator)); assertThrows(java.io.IOException.class, () -> new BallotService(new BallotStore(path), Clock.systemUTC()));
    }
}
