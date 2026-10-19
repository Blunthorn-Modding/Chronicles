package io.github.chronicle.core.play;

import java.io.IOException;

/** Durable storage boundary for shared participation, active time and observation deliveries. */
public interface PlayRepository {
    PlayState load() throws IOException;

    void save(PlayState state) throws IOException;
}
