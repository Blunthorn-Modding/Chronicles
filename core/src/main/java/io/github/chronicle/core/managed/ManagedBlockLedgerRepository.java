package io.github.chronicle.core.managed;

import java.io.IOException;

public interface ManagedBlockLedgerRepository {
    ManagedBlockLedger load() throws IOException;
    void save(ManagedBlockLedger ledger) throws IOException;
}
