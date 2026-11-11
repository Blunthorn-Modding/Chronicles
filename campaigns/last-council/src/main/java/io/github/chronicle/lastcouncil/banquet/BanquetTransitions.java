package io.github.chronicle.lastcouncil.banquet;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import static io.github.chronicle.lastcouncil.banquet.BanquetState.*;

/** Pure transitions. Callers own authorization, inventory, clock eligibility and effects. */
public final class BanquetTransitions {
    private BanquetTransitions() { }

    public static BanquetState issue(BanquetState state, long expectedRevision, UUID request, UUID actor,
                                     UUID token, SupplyKind kind) {
        Operation op = new Operation(actor, OperationType.ISSUE, token, null, kind, null, null);
        if (replay(state, request, op)) return state;
        checkFresh(state, expectedRevision);
        if (state.supplies().containsKey(token) || state.supplies().size() >= MAX_SUPPLIES)
            throw new IllegalStateException("Supply unavailable or capacity reached");
        Map<UUID, Supply> supplies = new HashMap<>(state.supplies());
        supplies.put(token, new Supply(kind, actor, null, null, null));
        return commit(state, request, op, supplies, state.illnesses());
    }

    public static BanquetState contaminate(BanquetState state, long expectedRevision, UUID request, UUID actor,
                                           UUID poisonToken, UUID drinkToken) {
        Operation op = new Operation(actor, OperationType.CONTAMINATE, poisonToken, drinkToken, null, null, null);
        if (replay(state, request, op)) return state;
        checkFresh(state, expectedRevision);
        Supply poison = available(state, poisonToken, SupplyKind.POISON);
        Supply drink = available(state, drinkToken, SupplyKind.DRINK);
        if (drink.poisoner() != null) throw new IllegalStateException("Drink already contaminated");
        Map<UUID, Supply> supplies = new HashMap<>(state.supplies());
        supplies.put(poisonToken, consumed(poison, actor, request));
        supplies.put(drinkToken, new Supply(drink.kind(), drink.issuer(), actor, null, null));
        return commit(state, request, op, supplies, state.illnesses());
    }

    public static BanquetState drink(BanquetState state, long expectedRevision, UUID request, UUID actor,
                                      UUID token, Timing timing, RepeatPolicy repeatPolicy) {
        Operation op = new Operation(actor, OperationType.DRINK, token, null, null, timing, repeatPolicy);
        if (replay(state, request, op)) return state;
        checkFresh(state, expectedRevision);
        Supply drink = available(state, token, SupplyKind.DRINK);
        Map<UUID, Illness> illnesses = new HashMap<>(state.illnesses());
        if (drink.poisoner() != null && (!illnesses.containsKey(actor) || repeatPolicy == RepeatPolicy.REFRESH)) {
            if (!illnesses.containsKey(actor) && illnesses.size() >= MAX_ILLNESSES)
                throw new IllegalStateException("Illness capacity reached");
            illnesses.put(actor, new Illness(timing.onsetTicks(), timing.durationTicks()));
        }
        Map<UUID, Supply> supplies = new HashMap<>(state.supplies());
        supplies.put(token, consumed(drink, actor, request));
        return commit(state, request, op, supplies, illnesses);
    }

    public static BanquetState antidote(BanquetState state, long expectedRevision, UUID request, UUID actor, UUID token) {
        Operation op = new Operation(actor, OperationType.ANTIDOTE, token, null, null, null, null);
        if (replay(state, request, op)) return state;
        checkFresh(state, expectedRevision);
        Supply antidote = available(state, token, SupplyKind.ANTIDOTE);
        Map<UUID, Supply> supplies = new HashMap<>(state.supplies());
        supplies.put(token, consumed(antidote, actor, request));
        Map<UUID, Illness> illnesses = new HashMap<>(state.illnesses());
        illnesses.remove(actor);
        return commit(state, request, op, supplies, illnesses);
    }

    public static BanquetState advance(BanquetState state, long deltaTicks, Set<UUID> eligiblePlayers) {
        Objects.requireNonNull(state); Objects.requireNonNull(eligiblePlayers);
        if (deltaTicks < 0) throw new IllegalArgumentException("Negative time");
        Set<UUID> eligible = Set.copyOf(eligiblePlayers);
        Map<UUID, Illness> illnesses = new HashMap<>(state.illnesses());
        for (UUID player : eligible) {
            Illness illness = illnesses.get(player);
            if (illness == null || deltaTicks == 0) continue;
            long onset = illness.onsetTicks();
            if (deltaTicks < onset) illnesses.put(player, new Illness(onset - deltaTicks, illness.durationTicks()));
            else {
                long activeTicks = deltaTicks - onset;
                if (activeTicks >= illness.durationTicks()) illnesses.remove(player);
                else illnesses.put(player, new Illness(0, illness.durationTicks() - activeTicks));
            }
        }
        if (illnesses.equals(state.illnesses())) return state;
        return new BanquetState(nextRevision(state), state.supplies(), illnesses, state.operations());
    }

    private static boolean replay(BanquetState state, UUID request, Operation op) {
        Objects.requireNonNull(state); Objects.requireNonNull(request);
        Operation previous = state.operations().get(request);
        if (previous == null) return false;
        if (!previous.equals(op)) throw new IllegalStateException("Request ID collision");
        return true;
    }
    private static void checkFresh(BanquetState state, long expectedRevision) {
        if (state.revision() != expectedRevision) throw new IllegalStateException("Stale revision");
        if (state.operations().size() >= MAX_OPERATIONS) throw new IllegalStateException("Receipt capacity reached");
        nextRevision(state);
    }
    private static long nextRevision(BanquetState state) {
        if (state.revision() == Long.MAX_VALUE) throw new IllegalStateException("Revision exhausted");
        return state.revision() + 1;
    }
    private static Supply available(BanquetState state, UUID token, SupplyKind kind) {
        Supply supply = state.supplies().get(token);
        if (supply == null || supply.kind() != kind || supply.usedBy() != null)
            throw new IllegalStateException("Supply unavailable");
        return supply;
    }
    private static Supply consumed(Supply supply, UUID actor, UUID request) {
        return new Supply(supply.kind(), supply.issuer(), supply.poisoner(), actor, request);
    }
    private static BanquetState commit(BanquetState state, UUID request, Operation op,
                                       Map<UUID, Supply> supplies, Map<UUID, Illness> illnesses) {
        Map<UUID, Operation> operations = new HashMap<>(state.operations());
        operations.put(request, op);
        return new BanquetState(nextRevision(state), supplies, illnesses, operations);
    }
}
