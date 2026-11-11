package io.github.chronicle.lastcouncil.banquet;

import java.io.IOException;

/** Private world storage, separate from reusable scenario content. */
public interface BanquetRepository {
    BanquetState load() throws IOException;
    void verifyUnchanged() throws IOException;
    void save(BanquetState state) throws IOException;
}
