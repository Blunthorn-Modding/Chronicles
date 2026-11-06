package io.github.chronicle.core.ballots;

import java.util.*;
import java.io.IOException;

public record BallotState(long revision, Map<UUID, Ballot> ballots) {
    public BallotState {
        if (revision < 0) throw new IllegalArgumentException("Invalid revision"); ballots = Map.copyOf(ballots);
        if (ballots.size() > 256 || ballots.values().stream().filter(b -> b.status() == BallotStatus.OPEN).count() > 1) throw new IllegalArgumentException("Invalid ballot history");
        ballots.forEach((id, b) -> { if (!id.equals(b.definition().id())) throw new IllegalArgumentException("Ballot identity mismatch"); });
    }
    public static BallotState empty() { return new BallotState(0, Map.of()); }
}
