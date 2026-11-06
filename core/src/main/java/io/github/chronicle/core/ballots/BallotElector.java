package io.github.chronicle.core.ballots;

import java.util.*;
import java.io.IOException;

public record BallotElector(UUID actor, String label) {
    public BallotElector { Objects.requireNonNull(actor); BallotValidation.text(label, 160, false); }
}
