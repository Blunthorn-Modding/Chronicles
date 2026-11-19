package io.github.chronicle.lastcouncil.progression;

import java.io.IOException;
import java.util.List;

/** Runs before the first durable political mutation; observers must not modify the list. */
@FunctionalInterface
public interface PoliticalObserver {
    PoliticalObserver NONE = occurrences -> { };
    void before(List<PoliticalOccurrence> occurrences) throws IOException;
}
