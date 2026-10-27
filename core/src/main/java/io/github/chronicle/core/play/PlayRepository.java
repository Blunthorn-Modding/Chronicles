package io.github.chronicle.core.play;

import java.io.IOException;

/** Durable storage boundary for shared participation, active time and observation deliveries. */
public interface PlayRepository {
    PlayState load() throws IOException;

    void save(PlayState state) throws IOException;

    /** Publishes immutable terminal/history evidence before a service clears its current pointer. */
    default void archiveEvent(EventExecution execution) throws IOException {
        throw new IOException("event execution archives are unsupported by this play repository");
    }
}
