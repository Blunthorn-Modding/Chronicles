package io.github.chronicle.lastcouncil.progression;

import java.util.Objects;

/** Public, actorless political evidence; never contains votes or treaty terms. */
public record PoliticalOccurrence(String authority, String type, String target, String activity) {
    public PoliticalOccurrence {
        Objects.requireNonNull(authority); Objects.requireNonNull(type);
        Objects.requireNonNull(target); Objects.requireNonNull(activity);
    }
}
