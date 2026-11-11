package io.github.chronicle.lastcouncil.banquet;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.chronicle.lastcouncil.banquet.BanquetState.*;
import static io.github.chronicle.lastcouncil.banquet.BanquetTransitions.*;

class BanquetTransitionsTest {
    private static UUID id() { return UUID.randomUUID(); }
    private static final Timing TIMING = new Timing(5, 10);

    private static BanquetState infect(BanquetState s, UUID actor, Timing timing) {
        UUID poison = id(), drink = id();
        s = issue(s, s.revision(), id(), actor, poison, SupplyKind.POISON);
        s = issue(s, s.revision(), id(), actor, drink, SupplyKind.DRINK);
        s = contaminate(s, s.revision(), id(), actor, poison, drink);
        return drink(s, s.revision(), id(), actor, drink, timing, RepeatPolicy.IGNORE);
    }

    @Test void consumptionReplayAndCollision() {
        UUID actor = id(), poison = id(), drink = id(), issue1 = id(), issue2 = id(), mix = id(), use = id();
        BanquetState s = issue(BanquetState.empty(), 0, issue1, actor, poison, SupplyKind.POISON);
        s = issue(s, 1, issue2, actor, drink, SupplyKind.DRINK);
        s = contaminate(s, 2, mix, actor, poison, drink);
        assertNull(s.supplies().get(drink).usedBy());
        assertEquals(actor, s.supplies().get(drink).poisoner());
        assertEquals(mix, s.supplies().get(poison).useRequest());
        BanquetState mixed = s;
        assertSame(s, contaminate(s, 0, mix, actor, poison, drink));
        assertThrows(IllegalStateException.class, () -> contaminate(mixed, 3, id(), actor, poison, drink));
        assertThrows(IllegalStateException.class, () -> contaminate(mixed, 3, mix, id(), poison, drink));
        s = drink(s, 3, use, actor, drink, TIMING, RepeatPolicy.IGNORE);
        assertEquals(new Illness(5, 10), s.illnesses().get(actor));
        assertEquals(use, s.supplies().get(drink).useRequest());
        BanquetState used = s;
        assertSame(s, drink(s, 0, use, actor, drink, TIMING, RepeatPolicy.IGNORE));
        assertThrows(IllegalStateException.class, () -> drink(used, 4, use, actor, drink, new Timing(1, 10), RepeatPolicy.IGNORE));
        assertThrows(IllegalStateException.class, () -> drink(used, 4, id(), actor, drink, TIMING, RepeatPolicy.IGNORE));
        assertThrows(IllegalStateException.class, () -> issue(used, 0, id(), actor, id(), SupplyKind.DRINK));
    }

    @Test void clockEligibilityExpiryAndCure() {
        UUID a = id(), b = id(), antidote = id();
        BanquetState s = infect(infect(BanquetState.empty(), a, TIMING), b, new Timing(0, 3));
        assertSame(s, advance(s, 99, Set.of()));
        s = advance(s, 6, Set.of(a));
        assertEquals(new Illness(0, 9), s.illnesses().get(a));
        assertEquals(new Illness(0, 3), s.illnesses().get(b));
        s = advance(s, Long.MAX_VALUE, Set.of(a));
        assertFalse(s.illnesses().containsKey(a));
        s = issue(s, s.revision(), id(), a, antidote, SupplyKind.ANTIDOTE);
        s = antidote(s, s.revision(), id(), b, antidote);
        assertTrue(s.illnesses().isEmpty());
        BanquetState result = s;
        assertThrows(IllegalArgumentException.class, () -> advance(result, -1, Set.of(a)));
    }

    @Test void repeatPoliciesAndPendingCure() {
        UUID actor = id();
        BanquetState s = infect(BanquetState.empty(), actor, TIMING);
        s = advance(s, 2, Set.of(actor));
        s = infect(s, actor, new Timing(1, 1));
        assertEquals(new Illness(3, 10), s.illnesses().get(actor));
        UUID poison = id(), cup = id(), remedy = id();
        s = issue(s, s.revision(), id(), actor, poison, SupplyKind.POISON);
        s = issue(s, s.revision(), id(), actor, cup, SupplyKind.DRINK);
        s = contaminate(s, s.revision(), id(), actor, poison, cup);
        s = drink(s, s.revision(), id(), actor, cup, new Timing(1, 2), RepeatPolicy.REFRESH);
        assertEquals(new Illness(1, 2), s.illnesses().get(actor));
        s = issue(s, s.revision(), id(), actor, remedy, SupplyKind.ANTIDOTE);
        s = antidote(s, s.revision(), id(), actor, remedy);
        assertTrue(s.illnesses().isEmpty());
    }

    @Test void malformedHistoryIsRejected() {
        UUID actor = id(), token = id(), request = id();
        Supply supply = new Supply(SupplyKind.DRINK, actor, null, null, null);
        Operation issued = new Operation(actor, OperationType.ISSUE, token, null, SupplyKind.DRINK, null, null);
        assertThrows(IllegalArgumentException.class, () -> new BanquetState(1, Map.of(token, supply), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new BanquetState(1, Map.of(), Map.of(), Map.of(request, issued)));
        assertThrows(IllegalArgumentException.class, () -> new BanquetState(2, Map.of(token, supply), Map.of(), Map.of(request, issued, id(), issued)));
        assertThrows(IllegalArgumentException.class, () -> new BanquetState(1, Map.of(token, supply), Map.of(actor, new Illness(1, 1)), Map.of(request, issued)));
        assertThrows(IllegalArgumentException.class, () -> new BanquetState(0, Map.of(token, supply), Map.of(), Map.of(request, issued)));
    }

    @Test void immutableAndBoundedState() {
        UUID token = id(), actor = id();
        Map<UUID, Supply> source = new HashMap<>();
        source.put(token, new Supply(SupplyKind.DRINK, actor, null, null, null));
        UUID issueRequest = id();
        BanquetState s = new BanquetState(1, source, Map.of(), Map.of(issueRequest,
                new Operation(actor, OperationType.ISSUE, token, null, SupplyKind.DRINK, null, null)));
        source.clear();
        assertEquals(1, s.supplies().size());
        assertThrows(UnsupportedOperationException.class, () -> s.supplies().clear());
        assertThrows(IllegalArgumentException.class, () -> new Timing(0, MAX_TICKS + 1));
        assertThrows(IllegalArgumentException.class, () -> new BanquetState(-1, Map.of(), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new BanquetState(0, Map.of(token,
                new Supply(SupplyKind.DRINK, actor, null, actor, id())), Map.of(), Map.of()));
        Map<UUID, Supply> full = new HashMap<>();
        Map<UUID, Operation> receipts = new HashMap<>();
        for (int i = 0; i < MAX_SUPPLIES; i++) {
            UUID supply = id();
            full.put(supply, new Supply(SupplyKind.DRINK, actor, null, null, null));
            receipts.put(id(), new Operation(actor, OperationType.ISSUE, supply, null, SupplyKind.DRINK, null, null));
        }
        BanquetState atCapacity = new BanquetState(MAX_SUPPLIES, full, Map.of(), receipts);
        assertThrows(IllegalStateException.class, () -> issue(atCapacity, MAX_SUPPLIES, id(), actor, id(), SupplyKind.POISON));
    }
}
