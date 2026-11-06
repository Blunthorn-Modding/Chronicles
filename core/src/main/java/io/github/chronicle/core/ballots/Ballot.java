package io.github.chronicle.core.ballots;

import java.util.*;
import java.io.IOException;

public record Ballot(BallotDefinition definition, UUID openedBy, String openedAt, BallotStatus status,
        Map<String, String> votes, UUID finishedBy, String finishedAt, String reason) {
    public Ballot {
        Objects.requireNonNull(definition); Objects.requireNonNull(openedBy); Objects.requireNonNull(status);
        BallotValidation.text(openedAt, 64, false); Objects.requireNonNull(finishedAt); Objects.requireNonNull(reason); votes = Map.copyOf(votes);
        if (status == BallotStatus.OPEN ? finishedBy != null || !finishedAt.isEmpty() || !reason.isEmpty() : finishedBy == null || finishedAt.isEmpty()) throw new IllegalArgumentException("Invalid terminal fields");
        if (status == BallotStatus.VOID) BallotValidation.text(reason, 512, true); else if (!reason.isEmpty()) throw new IllegalArgumentException("Unexpected reason");
        if (!finishedAt.isEmpty()) BallotValidation.text(finishedAt, 64, false);
        Set<String> options = new HashSet<>(); definition.options().forEach(o -> options.add(o.id()));
        votes.forEach((key, value) -> { if (!definition.electorate().containsKey(key) || !options.contains(value)) throw new IllegalArgumentException("Invalid vote"); });
    }
}
