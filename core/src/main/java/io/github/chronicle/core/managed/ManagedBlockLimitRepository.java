package io.github.chronicle.core.managed;

import java.io.IOException;

public interface ManagedBlockLimitRepository {
    ManagedBlockLimits load() throws IOException;
    void save(ManagedBlockLimits limits) throws IOException;
}
