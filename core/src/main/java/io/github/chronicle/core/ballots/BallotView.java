package io.github.chronicle.core.ballots;

import java.util.*;
import java.io.IOException;

public record BallotView(UUID id, String purpose, String title, List<BallotOption> options,
        Map<String, String> electorateLabels, int threshold, BallotVisibility visibility, BallotStatus status,
        String ownChoice, Map<String, String> namedVotes, Map<String, Integer> totals, List<String> passedOptions, String reason) {
    public BallotView { options = List.copyOf(options); electorateLabels = Map.copyOf(electorateLabels); namedVotes = Map.copyOf(namedVotes); totals = Map.copyOf(totals); passedOptions = List.copyOf(passedOptions); }
}
