package io.github.chronicle.lastcouncil.banquet;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;

/** Private, immutable domain state; never use this as a public projection. */
public record BanquetState(long revision, Map<UUID, Supply> supplies,
                           Map<UUID, Illness> illnesses, Map<UUID, Operation> operations) {
    public static final int MAX_SUPPLIES = 4096;
    public static final int MAX_ILLNESSES = 256;
    public static final int MAX_OPERATIONS = 8192;
    public static final long MAX_TICKS = 1_728_000L;

    public enum SupplyKind { DRINK, POISON, ANTIDOTE }
    public enum OperationType { ISSUE, CONTAMINATE, DRINK, ANTIDOTE }
    public enum RepeatPolicy { IGNORE, REFRESH }

    public record Timing(long onsetTicks, long durationTicks) {
        public Timing {
            if (onsetTicks < 0 || onsetTicks > MAX_TICKS || durationTicks <= 0 || durationTicks > MAX_TICKS)
                throw new IllegalArgumentException("Invalid illness timing");
        }
    }

    public record Illness(long onsetTicks, long durationTicks) {
        public Illness { new Timing(onsetTicks, durationTicks); }
        public boolean active() { return onsetTicks == 0; }
    }

    public record Supply(SupplyKind kind, UUID issuer, UUID poisoner, UUID usedBy, UUID useRequest) {
        public Supply {
            Objects.requireNonNull(kind); Objects.requireNonNull(issuer);
            if (poisoner != null && kind != SupplyKind.DRINK)
                throw new IllegalArgumentException("Only drinks can be contaminated");
            if ((usedBy == null) != (useRequest == null))
                throw new IllegalArgumentException("Incomplete consumption receipt");
        }
    }

    /** Full request payload, including policy arguments, to detect request ID collisions. */
    public record Operation(UUID actor, OperationType type, UUID inputToken, UUID secondToken,
                            SupplyKind issueKind, Timing timing, RepeatPolicy repeatPolicy) {
        public Operation {
            Objects.requireNonNull(actor); Objects.requireNonNull(type); Objects.requireNonNull(inputToken);
            boolean valid = switch (type) {
                case ISSUE -> issueKind != null && secondToken == null && timing == null && repeatPolicy == null;
                case CONTAMINATE -> secondToken != null && !inputToken.equals(secondToken)
                        && issueKind == null && timing == null && repeatPolicy == null;
                case DRINK -> secondToken == null && issueKind == null && timing != null && repeatPolicy != null;
                case ANTIDOTE -> secondToken == null && issueKind == null && timing == null && repeatPolicy == null;
            };
            if (!valid) throw new IllegalArgumentException("Invalid operation payload");
        }
    }

    public BanquetState {
        if (revision < 0) throw new IllegalArgumentException("Negative revision");
        supplies = Map.copyOf(supplies); illnesses = Map.copyOf(illnesses); operations = Map.copyOf(operations);
        if (supplies.size() > MAX_SUPPLIES || illnesses.size() > MAX_ILLNESSES || operations.size() > MAX_OPERATIONS)
            throw new IllegalArgumentException("Banquet capacity exceeded");
        if (revision < operations.size()) throw new IllegalArgumentException("Revision precedes receipts");
        Map<UUID, Operation> issued = new HashMap<>();
        Set<UUID> contaminated = new HashSet<>(), attributed = new HashSet<>();
        Set<UUID> illActors = new HashSet<>();
        for (var entry : operations.entrySet()) {
            UUID request = entry.getKey();
            Operation op = entry.getValue();
            Supply input = supplies.get(op.inputToken());
            if (input == null) throw new IllegalArgumentException("Dangling operation");
            switch (op.type()) {
                case ISSUE -> {
                    if (issued.put(op.inputToken(), op) != null || input.kind() != op.issueKind()
                            || !input.issuer().equals(op.actor()))
                        throw new IllegalArgumentException("Invalid issue receipt");
                }
                case CONTAMINATE -> {
                    Supply target = supplies.get(op.secondToken());
                    if (input.kind() != SupplyKind.POISON || target == null || target.kind() != SupplyKind.DRINK || target.poisoner() == null)
                        throw new IllegalArgumentException("Invalid contamination receipt");
                    contaminated.add(op.secondToken());
                    if(op.actor().equals(target.poisoner()))attributed.add(op.secondToken());
                    requireConsumed(input, request, op.actor());
                }
                case DRINK -> {
                    if (input.kind() != SupplyKind.DRINK) throw new IllegalArgumentException("Invalid drink receipt");
                    requireConsumed(input, request, op.actor());
                    Illness illness = illnesses.get(op.actor());
                    if (input.poisoner() != null && illness != null
                            && illness.onsetTicks() <= op.timing().onsetTicks()
                            && illness.durationTicks() <= op.timing().durationTicks()
                            && (illness.onsetTicks() == 0 || illness.durationTicks() == op.timing().durationTicks()))
                        illActors.add(op.actor());
                }
                case ANTIDOTE -> {
                    if (input.kind() != SupplyKind.ANTIDOTE) throw new IllegalArgumentException("Invalid antidote receipt");
                    requireConsumed(input, request, op.actor());
                }
            }
        }
        for (var entry : supplies.entrySet()) {
            Supply supply = entry.getValue();
            if (!issued.containsKey(entry.getKey()) || (supply.poisoner() != null) != contaminated.contains(entry.getKey())
                    || supply.poisoner()!=null&&!attributed.contains(entry.getKey()))
                throw new IllegalArgumentException("Supply history incomplete");
            if (supply.useRequest() != null) {
                Operation op = operations.get(supply.useRequest());
                OperationType expected = switch (supply.kind()) {
                    case DRINK -> OperationType.DRINK;
                    case POISON -> OperationType.CONTAMINATE;
                    case ANTIDOTE -> OperationType.ANTIDOTE;
                };
                if (op == null || op.type() != expected || !op.actor().equals(supply.usedBy())
                        || !op.inputToken().equals(entry.getKey()))
                    throw new IllegalArgumentException("Invalid consumption receipt");
            }
        }
        if (!illActors.containsAll(illnesses.keySet())) throw new IllegalArgumentException("Illness lacks matching exposure and timing");
    }

    private static void requireConsumed(Supply supply, UUID request, UUID actor) {
        if (!request.equals(supply.useRequest()) || !actor.equals(supply.usedBy()))
            throw new IllegalArgumentException("Operation lacks matching consumption");
    }

    public static BanquetState empty() { return new BanquetState(0, Map.of(), Map.of(), Map.of()); }
}
