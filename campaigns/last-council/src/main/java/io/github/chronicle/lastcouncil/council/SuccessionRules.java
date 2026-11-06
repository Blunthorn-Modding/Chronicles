package io.github.chronicle.lastcouncil.council;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/** Pure constitutional outcome calculation. The caller persists a drawn result before applying it. */
public final class SuccessionRules {
    private SuccessionRules() { }

    public enum Decision { CROWNED, ELIMINATED, VACANT }

    public record Result(Decision decision, String house, List<String> tiedLast) {
        public Result {
            if (decision == null || house == null || tiedLast == null) throw new IllegalArgumentException("Missing result field");
            tiedLast = List.copyOf(tiedLast);
            if (decision == Decision.VACANT) {
                if (!house.isEmpty() || !tiedLast.isEmpty()) throw new IllegalArgumentException("Invalid vacancy");
            } else {
                validateHouse(house);
                if (decision == Decision.CROWNED && !tiedLast.isEmpty()) throw new IllegalArgumentException("Invalid crown tie");
                if (!tiedLast.isEmpty()) {
                    if (decision != Decision.ELIMINATED || tiedLast.size() < 2 || !tiedLast.contains(house)) throw new IllegalArgumentException("Invalid elimination tie");
                    String previous = null;
                    for (String candidate : tiedLast) {
                        validateHouse(candidate);
                        if (previous != null && previous.compareTo(candidate) >= 0) throw new IllegalArgumentException("Tie must be sorted and unique");
                        previous = candidate;
                    }
                }
            }
        }
    }

    /**
     * Multi-candidate totals require each slate ID and "abstain". A one-candidate
     * confirmation requires "yes", "no", and "abstain"; an empty slate requires
     * only "abstain". Counts may omit uncast votes but not option keys.
     * The draw receives the size of a sorted tied-last list and returns an index.
     */
    public static Result decide(Set<String> slate, Map<String, Integer> totals, int electorate, IntUnaryOperator draw) {
        if (slate == null || totals == null || draw == null || electorate < 0) throw new IllegalArgumentException("Invalid ballot inputs");
        slate.forEach(SuccessionRules::validateHouse);
        Set<String> options = slate.size() == 1 ? Set.of("yes", "no", "abstain") : new java.util.HashSet<>(slate);
        if (slate.size() != 1) options.add("abstain");
        if (!totals.keySet().equals(options)) throw new IllegalArgumentException("Unexpected or missing ballot option");
        long sum = 0;
        for (Integer count : totals.values()) {
            if (count == null || count < 0) throw new IllegalArgumentException("Invalid vote count");
            sum += count;
        }
        if (sum > electorate) throw new IllegalArgumentException("Votes exceed electorate");
        if (slate.isEmpty() || electorate == 0) return new Result(Decision.VACANT, "", List.of());
        int majority = electorate / 2 + 1;
        if (slate.size() == 1) {
            return totals.get("yes") >= majority
                ? new Result(Decision.CROWNED, slate.iterator().next(), List.of())
                : new Result(Decision.VACANT, "", List.of());
        }
        List<String> candidates = slate.stream().sorted().toList();
        for (String candidate : candidates) {
            if (totals.get(candidate) >= majority) return new Result(Decision.CROWNED, candidate, List.of());
        }
        if (slate.size() == 2) return new Result(Decision.VACANT, "", List.of());
        int minimum = candidates.stream().mapToInt(totals::get).min().orElseThrow();
        List<String> lowest = new ArrayList<>();
        for (String candidate : candidates) if (totals.get(candidate) == minimum) lowest.add(candidate);
        if (lowest.size() == 1) return new Result(Decision.ELIMINATED, lowest.get(0), List.of());
        int index = draw.applyAsInt(lowest.size());
        if (index < 0 || index >= lowest.size()) throw new IllegalArgumentException("Draw index out of range");
        return new Result(Decision.ELIMINATED, lowest.get(index), lowest);
    }

    private static void validateHouse(String id) {
        if (id == null || id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid house ID");
    }
}
