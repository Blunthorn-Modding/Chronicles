package io.github.chronicle.core.entry;

import java.io.IOException;

public interface RoleEntryRepository {
    RoleEntryState load() throws IOException;
    void save(RoleEntryState state) throws IOException;
}
