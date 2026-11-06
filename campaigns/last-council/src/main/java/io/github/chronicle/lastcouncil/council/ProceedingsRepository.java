package io.github.chronicle.lastcouncil.council;

import java.io.IOException;

public interface ProceedingsRepository {
    ProceedingsState load() throws IOException;
    void save(ProceedingsState state) throws IOException;
    default void verifyUnchanged() throws IOException { }
}
