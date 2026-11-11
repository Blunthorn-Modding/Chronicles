package io.github.chronicle.core.spawn;

import java.io.IOException;

/** Durable boundary for spawn bindings, intentionally separate from map metadata. */
public interface SpawnRepository {
    SpawnRegistry load() throws IOException;
    void save(SpawnRegistry registry) throws IOException;
}
