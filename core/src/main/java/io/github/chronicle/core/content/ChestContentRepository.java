package io.github.chronicle.core.content;

import java.io.IOException;

public interface ChestContentRepository {
    ChestContentState load() throws IOException;
    void save(ChestContentState state) throws IOException;
}
