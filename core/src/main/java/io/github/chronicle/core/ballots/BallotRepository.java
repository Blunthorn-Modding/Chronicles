package io.github.chronicle.core.ballots;

import java.util.*;
import java.io.IOException;

public interface BallotRepository {
    default void verifyUnchanged() throws IOException { }
    BallotState load() throws IOException;
    void save(BallotState state) throws IOException;
}
