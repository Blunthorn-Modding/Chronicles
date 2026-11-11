package io.github.chronicle.core.spawn;

import java.io.IOException;

/** Durable storage boundary for automatic player spawn policies and arrival receipts. */
public interface AutomaticSpawnRepository {
    AutomaticSpawnState load() throws IOException;

    void save(AutomaticSpawnState state) throws IOException;
}
