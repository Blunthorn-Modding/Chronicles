package io.github.chronicle.core.offering;

import java.io.IOException;
import java.util.UUID;

/** Serializes replay-safe purchases and irreversible campaign-source closure. */
public final class OfferingService implements AutoCloseable {
    private final OfferingStore store;
    private OfferingLedger ledger;

    public OfferingService(OfferingStore store) throws IOException {
        if (store == null) throw new IllegalArgumentException("offering store is required");
        this.store = store;
        this.ledger = store.loadOrCreate();
    }

    public synchronized OfferingLedger ledger() { return ledger; }

    public synchronized OfferingLedger purchase(long expectedRevision, UUID transaction, UUID player, String source,
            OfferingDefinition definition) throws IOException {
        return purchase(expectedRevision,transaction,player,source,definition,0,0);
    }
    public synchronized OfferingLedger purchase(long expectedRevision, UUID transaction, UUID player, String source,
            OfferingDefinition definition,long clock,long cooldownTicks) throws IOException {
        OfferingLedger replacement = ledger.purchase(expectedRevision, transaction, player, source, definition,clock,cooldownTicks);
        if (replacement == ledger) {
            store.verifyUnchanged();
            return ledger;
        }
        OfferingLedger published = store.save(replacement, ledger);
        ledger = published;
        return published;
    }

    public synchronized OfferingLedger closeSource(String source) throws IOException {
        OfferingLedger replacement = ledger.closeSource(source);
        if (replacement == ledger) {
            store.verifyUnchanged();
            return ledger;
        }
        OfferingLedger published = store.save(replacement, ledger);
        ledger = published;
        return published;
    }

    @Override public synchronized void close() throws IOException { store.close(); }
}
