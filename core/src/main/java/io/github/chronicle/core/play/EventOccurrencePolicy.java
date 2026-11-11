package io.github.chronicle.core.play;

/** Finite execution identity policy for configured Events. */
public enum EventOccurrencePolicy {
    /** The legacy campaign-wide one-shot behavior. */
    ONCE,
    /** The first matching successful source occurrence for each source actor. */
    ONCE_PER_ACTOR,
    /** Every distinct matching durable source occurrence. */
    PER_SOURCE_OCCURRENCE
}
