package io.github.chronicle.lastcouncil.banquet;

import java.util.HashMap;
import java.util.HashSet;
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
                                           UUID poisonToken, UUID foodToken) {
        Operation op = new Operation(actor, OperationType.CONTAMINATE, poisonToken, foodToken, null, null, null);
        if (replay(state, request, op)) return state;
        checkFresh(state, expectedRevision);
        requirePresent(state, actor);
        Supply poison = available(state, poisonToken, SupplyKind.POISON);
        Supply food = availableFood(state, foodToken);
        Map<UUID, Supply> supplies = new HashMap<>(state.supplies());
        supplies.put(poisonToken, consumed(poison, actor, request));
        supplies.put(foodToken, new Supply(food.kind(), food.issuer(), food.poisoner() == null ? actor : food.poisoner(), null, null));
        return commit(state, request, op, supplies, state.illnesses());
    }

    public static BanquetState drink(BanquetState state, long expectedRevision, UUID request, UUID actor,
                                     UUID token, Timing timing, RepeatPolicy repeatPolicy) {
        return consumeFood(state, expectedRevision, request, actor, token, timing, repeatPolicy);
    }

    public static BanquetState eat(BanquetState state, long expectedRevision, UUID request, UUID actor,
                                   UUID token, Timing timing, RepeatPolicy repeatPolicy) {
        return consumeFood(state, expectedRevision, request, actor, token, timing, repeatPolicy);
    }

    private static BanquetState consumeFood(BanquetState state, long expectedRevision, UUID request, UUID actor,
                                            UUID token, Timing timing, RepeatPolicy repeatPolicy) {
        Operation op = new Operation(actor, OperationType.DRINK, token, null, null, timing, repeatPolicy);
        if (replay(state, request, op)) return state;
        checkFresh(state, expectedRevision);
        requirePresent(state, actor);
        Supply food = availableFood(state, token);
        Map<UUID, Illness> illnesses = new HashMap<>(state.illnesses());
        if (food.poisoner() != null && (!illnesses.containsKey(actor) || repeatPolicy == RepeatPolicy.REFRESH)) {
            if (!illnesses.containsKey(actor) && illnesses.size() >= MAX_ILLNESSES)
                throw new IllegalStateException("Illness capacity reached");
            illnesses.put(actor, refresh(illnesses.get(actor), timing));
        }
        Map<UUID, Supply> supplies = new HashMap<>(state.supplies());
        supplies.put(token, consumed(food, actor, request));
        return commit(state, request, op, supplies, illnesses);
    }

    private static Illness refresh(Illness existing, Timing timing) {
        if (existing == null) return new Illness(timing.onsetTicks(), timing.durationTicks(), timing.poisonTicks(), timing.lethal());
        // Escalating illness persists until cured. Further exposures cannot grant an earlier-stage reprieve.
        if (existing.lethal()) return existing;
        long onset = Math.min(existing.onsetTicks(), timing.onsetTicks());
        if (timing.lethal()) return new Illness(onset, timing.durationTicks(), timing.poisonTicks(), true);
        return new Illness(onset, timing.durationTicks(), 0, false);
    }

    public static BanquetState antidote(BanquetState state, long expectedRevision, UUID request, UUID actor, UUID token) {
        Operation op = new Operation(actor, OperationType.ANTIDOTE, token, null, null, null, null);
        if (replay(state, request, op)) return state;
        checkFresh(state, expectedRevision);
        requirePresent(state, actor);
        Supply antidote = available(state, token, SupplyKind.ANTIDOTE);
        Map<UUID, Supply> supplies = new HashMap<>(state.supplies());
        supplies.put(token, consumed(antidote, actor, request));
        Map<UUID, Illness> illnesses = new HashMap<>(state.illnesses());
        illnesses.remove(actor);
        return commit(state, request, op, supplies, illnesses);
    }

    public static BanquetState eliminate(BanquetState state, UUID actor) {
        Objects.requireNonNull(state); Objects.requireNonNull(actor);
        if (state.eliminated().contains(actor)) return state;
        if (state.eliminated().size() >= MAX_ILLNESSES) throw new IllegalStateException("Elimination capacity reached");
        Map<UUID, Illness> illnesses = new HashMap<>(state.illnesses());
        illnesses.remove(actor);
        Set<UUID> eliminated = new HashSet<>(state.eliminated());
        eliminated.add(actor);
        return new BanquetState(nextRevision(state), state.supplies(), illnesses, state.operations(), eliminated);
    }

    public static BanquetState advance(BanquetState state, long deltaTicks, Set<UUID> eligiblePlayers) {
        Objects.requireNonNull(state); Objects.requireNonNull(eligiblePlayers);
        if (deltaTicks < 0) throw new IllegalArgumentException("Negative time");
        Set<UUID> eligible = Set.copyOf(eligiblePlayers);
        Map<UUID, Illness> illnesses = new HashMap<>(state.illnesses());
        for (UUID player : eligible) {
            Illness illness = illnesses.get(player);
            if (illness == null || deltaTicks == 0 || state.eliminated().contains(player)) continue;
            illnesses.computeIfPresent(player, (ignored, value) -> advance(value, deltaTicks));
        }
        if (illnesses.equals(state.illnesses())) return state;
        return new BanquetState(nextRevision(state), state.supplies(), illnesses, state.operations(), state.eliminated());
    }

    private static Illness advance(Illness illness, long ticks) {
        if (ticks < illness.onsetTicks()) return new Illness(illness.onsetTicks() - ticks, illness.durationTicks(), illness.poisonTicks(), illness.lethal());
        long remaining = ticks - illness.onsetTicks();
        if (illness.lethal()) return new Illness(0, Math.max(0, illness.durationTicks() - remaining), illness.poisonTicks(), true);
        if (remaining >= illness.durationTicks()) return null;
        return new Illness(0, illness.durationTicks() - remaining, 0, false);
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
    private static void requirePresent(BanquetState state, UUID actor) {
        if (state.eliminated().contains(actor)) throw new IllegalStateException("Eliminated characters cannot use banquet supplies");
    }
    private static Supply available(BanquetState state, UUID token, SupplyKind kind) {
        Supply supply = state.supplies().get(token);
        if (supply == null || supply.kind() != kind || supply.usedBy() != null)
            throw new IllegalStateException("Supply unavailable");
        return supply;
    }
    private static Supply availableFood(BanquetState state, UUID token) {
        Supply supply = state.supplies().get(token);
        if (supply == null || (supply.kind() != SupplyKind.DRINK && supply.kind() != SupplyKind.FOOD) || supply.usedBy() != null)
            throw new IllegalStateException("Food unavailable");
        return supply;
    }
    private static Supply consumed(Supply supply, UUID actor, UUID request) {
        return new Supply(supply.kind(), supply.issuer(), supply.poisoner(), actor, request);
    }
    private static BanquetState commit(BanquetState state, UUID request, Operation op,
                                       Map<UUID, Supply> supplies, Map<UUID, Illness> illnesses) {
        Map<UUID, Operation> operations = new HashMap<>(state.operations());
        operations.put(request, op);
        return new BanquetState(nextRevision(state), supplies, illnesses, operations, state.eliminated());
    }
}
