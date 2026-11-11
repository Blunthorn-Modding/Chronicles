package io.github.chronicle.core.managed;

import io.github.chronicle.core.authoring.BlockPosition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ManagedBlockServiceTest {
    private static final BlockPosition FIRST = new BlockPosition(1, 64, 1);

    @Test void reservationCountsImmediatelyConfirmationIsIdempotentAndRemovalReleases() throws IOException {
        Fixture fixture = new Fixture();
        var reservation = fixture.service.reserve("test:altar", "minecraft:overworld", FIRST);
        assertEquals(1, fixture.service.count("test:altar"));
        assertThrows(IllegalStateException.class, () -> fixture.service.reserve("test:altar", "minecraft:overworld", new BlockPosition(2, 64, 2)));
        long reservedRevision = fixture.service.ledger().revision();
        fixture.service.confirm(reservation.id()); long confirmedRevision = fixture.service.ledger().revision();
        fixture.service.confirm(reservation.id());
        assertEquals(confirmedRevision, fixture.service.ledger().revision()); assertTrue(confirmedRevision > reservedRevision);
        assertTrue(fixture.service.release(reservation.id())); assertEquals(0, fixture.service.count("test:altar"));
        fixture.service.reserve("test:altar", "minecraft:overworld", new BlockPosition(2, 64, 2));
    }

    @Test void discoveryPreservesExistingOverLimitButNewPolicyCannotGoBelowCount() throws IOException {
        Fixture fixture = new Fixture();
        fixture.service.adopt("test:altar", "minecraft:overworld", FIRST, ManagedBlockInstance.Origin.DISCOVERED);
        fixture.service.adopt("test:altar", "minecraft:overworld", new BlockPosition(2,64,2), ManagedBlockInstance.Origin.DISCOVERED);
        var original = fixture.service.limits();
        assertThrows(IllegalArgumentException.class, () -> fixture.service.setLimit("test:altar",0,0));
        assertSame(original,fixture.service.limits());
        assertEquals(2,fixture.service.count("test:altar"));
        fixture.service.setLimit("test:altar",3,0);
        fixture.service.setLimit("test:altar",2,1);
        assertEquals(2,fixture.service.ledger().instances().size());
        assertThrows(IllegalStateException.class, () -> fixture.service.reserve("test:altar","minecraft:overworld",new BlockPosition(3,64,3)));
    }

    @Test void reservationAfterDialogOpenRaisesTheMinimumEvenWithoutAPolicyRevisionChange() throws IOException {
        Fixture fixture = new Fixture();
        fixture.service.setLimit("test:altar",2,0);
        fixture.service.reserve("test:altar","minecraft:overworld",FIRST);
        long viewedRevision = fixture.service.limits().revision();
        var second = fixture.service.reserve("test:altar","minecraft:overworld",new BlockPosition(2,64,2));
        assertThrows(IllegalArgumentException.class,()->fixture.service.setLimit("test:altar",1,viewedRevision));
        assertEquals(viewedRevision,fixture.service.limits().revision());
        fixture.service.release(second.id());
        fixture.service.setLimit("test:altar",1,viewedRevision);
        assertEquals(1,fixture.service.effectiveLimit(catalog().get("test:altar").orElseThrow()));
    }

    @Test void failingSaveNeverPublishesReservationOrRelease() throws IOException {
        Fixture fixture = new Fixture(); fixture.ledger.fail = true;
        assertThrows(IOException.class, () -> fixture.service.reserve("test:altar", "minecraft:overworld", FIRST));
        assertTrue(fixture.service.ledger().instances().isEmpty());
        fixture.ledger.fail = false; var reservation = fixture.service.reserve("test:altar", "minecraft:overworld", FIRST);
        fixture.ledger.fail = true; assertThrows(IOException.class, () -> fixture.service.release(reservation.id()));
        assertEquals(1, fixture.service.count("test:altar"));
    }

    @Test void unknownOverridesSurviveKnownPolicyChangesAndFixedCannotChange() throws IOException {
        ManagedBlockCatalog catalog = catalog(); MemoryLedger ledger = new MemoryLedger();
        MemoryLimits limits = new MemoryLimits(new ManagedBlockLimits(4, Map.of("missing:future", 9)));
        ManagedBlockService service = new ManagedBlockService(catalog, ledger, limits, () -> UUID.randomUUID());
        service.setLimit("test:altar", 2, 4);
        assertEquals(9, service.limits().overrides().get("missing:future"));
        assertThrows(IllegalStateException.class, () -> service.setLimit("test:heart", 2, 5));
    }

    private static ManagedBlockCatalog catalog() {
        ManagedBlockCatalog catalog = new ManagedBlockCatalog();
        catalog.register(new ManagedBlockDefinition("test:altar", "test:altar", "Altar", "Station", 1, ManagedBlockDefinition.LimitMode.EDITABLE));
        catalog.register(new ManagedBlockDefinition("test:heart", "test:heart", "Heart", "Root", 1, ManagedBlockDefinition.LimitMode.FIXED));
        return catalog;
    }
    private static final class Fixture {
        final MemoryLedger ledger = new MemoryLedger(); final ManagedBlockService service;
        Fixture() throws IOException {
            AtomicLong nextId = new AtomicLong(1);
            service = new ManagedBlockService(catalog(), ledger, new MemoryLimits(ManagedBlockLimits.empty()),
                    () -> new UUID(0, nextId.getAndIncrement()));
        }
    }
    private static final class MemoryLedger implements ManagedBlockLedgerRepository {
        ManagedBlockLedger state = ManagedBlockLedger.empty(); boolean fail;
        public ManagedBlockLedger load() { return state; }
        public void save(ManagedBlockLedger ledger) throws IOException { if (fail) throw new IOException("deliberate"); state = ledger; }
    }
    private static final class MemoryLimits implements ManagedBlockLimitRepository {
        ManagedBlockLimits state;
        MemoryLimits(ManagedBlockLimits state) { this.state = state; }
        public ManagedBlockLimits load() { return state; }
        public void save(ManagedBlockLimits limits) { state = limits; }
    }
}
