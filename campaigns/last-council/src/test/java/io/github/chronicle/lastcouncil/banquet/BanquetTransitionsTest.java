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

    @Test void refreshingCannotPostponePendingOnsetOrRestartActiveIncubation(){
        UUID actor=id();BanquetState s=infect(BanquetState.empty(),actor,TIMING);s=advance(s,3,Set.of(actor));
        for(int i=0;i<2;i++){
            if(i==1)s=advance(s,2,Set.of(actor));
            UUID poison=id(),drink=id();s=issue(s,s.revision(),id(),actor,poison,SupplyKind.POISON);s=issue(s,s.revision(),id(),actor,drink,SupplyKind.DRINK);
            s=contaminate(s,s.revision(),id(),actor,poison,drink);s=drink(s,s.revision(),id(),actor,drink,new Timing(20,30),RepeatPolicy.REFRESH);
            assertEquals(new Illness(i==0?2:0,30),s.illnesses().get(actor));
        }
    }
    @Test void anotherDoseIsConsumedWithoutRevealingPriorContamination(){
        UUID actor=id(),other=id(),drink=id(),first=id(),second=id();BanquetState s=BanquetState.empty();
        for(var entry:Map.of(drink,SupplyKind.DRINK,first,SupplyKind.POISON,second,SupplyKind.POISON).entrySet())s=issue(s,s.revision(),id(),actor,entry.getKey(),entry.getValue());
        s=contaminate(s,s.revision(),id(),actor,first,drink);s=contaminate(s,s.revision(),id(),other,second,drink);
        assertEquals(actor,s.supplies().get(drink).poisoner());assertEquals(other,s.supplies().get(second).usedBy());assertNull(s.supplies().get(drink).usedBy());
    }

    @Test void foodExposureMovesThroughEveryLethalStageAndEliminationIsPermanent() {
        UUID actor = id(), poison = id(), food = id();
        Timing lethal = new Timing(2, 4, 2, true);
        BanquetState s = issue(BanquetState.empty(), 0, id(), actor, poison, SupplyKind.POISON);
        s = issue(s, s.revision(), id(), actor, food, SupplyKind.FOOD);
        s = contaminate(s, s.revision(), id(), actor, poison, food);
        s = eat(s, s.revision(), id(), actor, food, lethal, RepeatPolicy.IGNORE);
        assertEquals(Stage.INCUBATION, s.illnesses().get(actor).stage());
        s = advance(s, 2, Set.of(actor));
        assertEquals(Stage.MILD, s.illnesses().get(actor).stage());
        s = advance(s, 2, Set.of(actor));
        assertEquals(Stage.POISONED, s.illnesses().get(actor).stage());
        s = advance(s, 2, Set.of(actor));
        assertEquals(Stage.CRITICAL, s.illnesses().get(actor).stage());
        assertSame(s, advance(s, 100, Set.of(actor)));
        s = eliminate(s, actor);
        assertTrue(s.eliminated().contains(actor));
        assertFalse(s.illnesses().containsKey(actor));
        UUID freshFood = id();
        BanquetState excluded = issue(s, s.revision(), id(), actor, freshFood, SupplyKind.FOOD);
        assertThrows(IllegalStateException.class, () -> eat(excluded, excluded.revision(), id(), actor, freshFood, lethal, RepeatPolicy.REFRESH));
    }

    @Test void refreshingLethalExposureNeverResetsCriticalStage() {
        UUID actor = id();
        Timing lethal = new Timing(5, 10, 4, true);
        BanquetState s = infect(BanquetState.empty(), actor, lethal);
        s = advance(s, 15, Set.of(actor));
        assertEquals(Stage.CRITICAL, s.illnesses().get(actor).stage());
        UUID poison = id(), food = id();
        s = issue(s, s.revision(), id(), actor, poison, SupplyKind.POISON);
        s = issue(s, s.revision(), id(), actor, food, SupplyKind.FOOD);
        s = contaminate(s, s.revision(), id(), actor, poison, food);
        s = eat(s, s.revision(), id(), actor, food, lethal, RepeatPolicy.REFRESH);
        assertEquals(Stage.CRITICAL, s.illnesses().get(actor).stage());
    }

    @Test void laterExposureAndChangedSettingsCannotDowngradePoison() {
        UUID actor = id();
        BanquetState state = infect(BanquetState.empty(), actor, new Timing(2, 10, 4, true));
        state = advance(state, 9, Set.of(actor));
        assertEquals(Stage.POISONED, state.illnesses().get(actor).stage());
        Illness before = state.illnesses().get(actor);
        state = infect(state, actor, new Timing(20, 40, 20, true));
        assertEquals(before, state.illnesses().get(actor));
        UUID poison = id(), food = id(), remedy = id();
        state = issue(state, state.revision(), id(), actor, poison, SupplyKind.POISON);
        state = issue(state, state.revision(), id(), actor, food, SupplyKind.FOOD);
        state = contaminate(state, state.revision(), id(), actor, poison, food);
        state = eat(state, state.revision(), id(), actor, food, new Timing(20, 40, 20, true), RepeatPolicy.REFRESH);
        assertEquals(before, state.illnesses().get(actor));
        state = advance(state, 100, Set.of(actor));
        state = issue(state, state.revision(), id(), actor, remedy, SupplyKind.ANTIDOTE);
        state = antidote(state, state.revision(), id(), actor, remedy);
        assertTrue(state.illnesses().isEmpty());
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
