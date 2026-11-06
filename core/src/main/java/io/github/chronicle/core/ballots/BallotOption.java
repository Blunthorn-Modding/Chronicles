package io.github.chronicle.core.ballots;

import java.util.*;
import java.io.IOException;

public record BallotOption(String id, String label, boolean canPass) {
    public BallotOption { BallotValidation.id(id); BallotValidation.text(label, 160, false); }
}
