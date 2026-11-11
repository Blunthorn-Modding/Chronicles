package io.github.chronicle.core.spawn;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticSpawnServiceTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final SpawnBinding ARRIVAL = new SpawnBinding("arrival", "city", 4);
    private static final SpawnBinding RESPAWN = new SpawnBinding("respawn", "mine", 8);

    @Test
    void policiesAreIndependentAndLeaveRetainsArrivalHistory() throws IOException {
        Memory repository = new Memory();
        AutomaticSpawnService service = new AutomaticSpawnService(repository);
        service.set(PLAYER, ARRIVAL, true);
        service.set(PLAYER, RESPAWN, false);
        assertEquals(new AutomaticSpawnState.Policy(ARRIVAL, RESPAWN), service.state().policies().get(PLAYER));

        service.disable(PLAYER, true);
        assertEquals(new AutomaticSpawnState.Policy(null, RESPAWN), service.state().policies().get(PLAYER));
        assertTrue(service.claim(PLAYER, "arrival"));
        service.leave(PLAYER);

        assertFalse(service.state().policies().containsKey(PLAYER));
        assertEquals(AutomaticSpawnState.Status.CLAIMED, service.state().arrivals().get(PLAYER).status());
    }

    @Test
    void claimIsOnceOnlyAcrossRestartUntilExplicitReset() throws IOException {
        Memory repository = new Memory();
        AutomaticSpawnService first = new AutomaticSpawnService(repository);
        assertTrue(first.claim(PLAYER, "arrival"));
        assertFalse(first.claim(PLAYER, "arrival"));

        AutomaticSpawnService restarted = new AutomaticSpawnService(repository);
        assertFalse(restarted.claim(PLAYER, "arrival"));
        restarted.reset(PLAYER);
        assertTrue(restarted.claim(PLAYER, "arrival"));
    }

    @Test
    void partialClaimsAndTerminalReceiptsCannotBeFinishedAgain() throws IOException {
        AutomaticSpawnService service = new AutomaticSpawnService(new Memory());
        assertThrows(IllegalStateException.class, () -> service.finish(PLAYER, AutomaticSpawnState.Status.MOVED, "no claim"));
        assertTrue(service.claim(PLAYER, "arrival"));
        service.finish(PLAYER, AutomaticSpawnState.Status.FAILED, "unsafe destination");
        assertEquals(AutomaticSpawnState.Status.FAILED, service.state().arrivals().get(PLAYER).status());
        assertThrows(IllegalStateException.class, () -> service.finish(PLAYER, AutomaticSpawnState.Status.MOVED, "later"));
        assertThrows(IllegalArgumentException.class, () -> service.finish(PLAYER, AutomaticSpawnState.Status.CLAIMED, ""));
    }

    @Test
    void failedSavesNeverPublishCandidatesAndPinnedBindingsAreRetainedVerbatim() throws IOException {
        Memory repository = new Memory();
        AutomaticSpawnService service = new AutomaticSpawnService(repository);
        service.set(PLAYER, ARRIVAL, true);
        assertEquals(ARRIVAL, service.state().policies().get(PLAYER).arrival());
        repository.fail = true;
        assertThrows(IOException.class, () -> service.set(PLAYER, RESPAWN, false));
        assertNull(service.state().policies().get(PLAYER).respawn());
        assertEquals(1, service.state().revision());
    }

    @Test
    void disabledPolicyDoesNotWriteAndStateBoundsAndDetailRulesAreEnforced() throws IOException {
        Memory repository = new Memory();
        AutomaticSpawnService service = new AutomaticSpawnService(repository);
        service.disable(PLAYER, true);
        assertEquals(0, repository.saves);
        assertThrows(IllegalArgumentException.class, () -> new AutomaticSpawnState.Arrival("arrival", AutomaticSpawnState.Status.FAILED, "line\nbreak"));
        Map<UUID, AutomaticSpawnState.Policy> tooMany = new java.util.HashMap<>();
        for (int index = 0; index <= AutomaticSpawnState.MAX_POLICIES; index++) {
            tooMany.put(new UUID(0, index), new AutomaticSpawnState.Policy(ARRIVAL, null));
        }
        assertThrows(IllegalArgumentException.class, () -> new AutomaticSpawnState(0, tooMany, Map.of()));
        Map<UUID, AutomaticSpawnState.Arrival> tooManyArrivals = new java.util.HashMap<>();
        for (int index = 0; index <= AutomaticSpawnState.MAX_ARRIVALS; index++) {
            tooManyArrivals.put(new UUID(1, index), new AutomaticSpawnState.Arrival("arrival", AutomaticSpawnState.Status.CLAIMED, ""));
        }
        assertThrows(IllegalArgumentException.class, () -> new AutomaticSpawnState(0, Map.of(), tooManyArrivals));
    }

    @Test void fullArrivalHistoryRefusesClaimWithoutPublishingOrOverwritingReceipts() throws IOException {
        Memory repository = new Memory();
        Map<UUID, AutomaticSpawnState.Arrival> receipts = new java.util.HashMap<>();
        for (int index = 0; index < AutomaticSpawnState.MAX_ARRIVALS; index++)
            receipts.put(new UUID(1, index), new AutomaticSpawnState.Arrival("arrival", AutomaticSpawnState.Status.MOVED, "existing"));
        repository.state = new AutomaticSpawnState(5, Map.of(), receipts);
        AutomaticSpawnService service = new AutomaticSpawnService(repository);
        assertThrows(IllegalArgumentException.class, () -> service.claim(PLAYER, "arrival"));
        assertSame(repository.state, service.state());
        assertEquals(0, repository.saves);
        assertEquals(AutomaticSpawnState.MAX_ARRIVALS, service.state().arrivals().size());
    }

    private static final class Memory implements AutomaticSpawnRepository {
        AutomaticSpawnState state = AutomaticSpawnState.empty();
        boolean fail;
        int saves;
        @Override public AutomaticSpawnState load() { return state; }
        @Override public void save(AutomaticSpawnState value) throws IOException {
            if (fail) throw new IOException("deliberate");
            state = value;
            saves++;
        }
    }
}
