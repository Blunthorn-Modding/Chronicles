package io.github.chronicle.core.authoring;

import java.io.IOException;

/** Durable storage boundary for the Core authoring registry. */
public interface AuthoringRepository {
    AuthoringRegistry load() throws IOException;

    void save(AuthoringRegistry registry) throws IOException;
}
