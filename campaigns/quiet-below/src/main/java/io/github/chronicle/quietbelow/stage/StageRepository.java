package io.github.chronicle.quietbelow.stage;

import java.io.IOException;

/** Persistence seam kept package-private for deterministic failure-path tests. */
interface StageRepository {
    StageState load() throws IOException;
    void save(StageState state) throws IOException;
}
