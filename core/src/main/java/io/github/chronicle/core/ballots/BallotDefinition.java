package io.github.chronicle.core.ballots;

import java.util.*;
import java.io.IOException;

public record BallotDefinition(UUID id, String purpose, String title, List<BallotOption> options,
        Map<String, BallotElector> electorate, int threshold, BallotVisibility visibility) {
    public BallotDefinition {
        Objects.requireNonNull(id); BallotValidation.text(purpose, 128, false); BallotValidation.text(title, 512, true);
        options = List.copyOf(options); electorate = Map.copyOf(electorate); Objects.requireNonNull(visibility);
        if (options.size() < 2 || options.size() > 16 || electorate.isEmpty() || electorate.size() > 32 || threshold < 1 || threshold > electorate.size()) throw new IllegalArgumentException("Invalid ballot dimensions");
        if (options.stream().map(BallotOption::id).distinct().count() != options.size() || electorate.values().stream().map(BallotElector::actor).distinct().count() != electorate.size()) throw new IllegalArgumentException("Duplicate ballot option or actor");
        electorate.keySet().forEach(BallotValidation::id);
    }
    public static int majority(int n) { if (n < 1 || n > 32) throw new IllegalArgumentException("Invalid electorate size"); return n / 2 + 1; }
    public static int twoThirds(int n) { if (n < 1 || n > 32) throw new IllegalArgumentException("Invalid electorate size"); return (2 * n + 2) / 3; }
}
