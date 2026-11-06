package io.github.chronicle.lastcouncil.office;

import java.io.IOException;

public interface CouncilOfficeRepository {
    CouncilOffice load() throws IOException;
    void save(CouncilOffice office) throws IOException;
    default void verifyUnchanged() throws IOException { }
}
