package io.github.chronicle.core.managed;

import io.github.chronicle.core.authoring.BlockPosition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ManagedAreaReplacementTest {
    private static final String WORLD = "minecraft:overworld";
    private static final BlockPosition FIRST = new BlockPosition(1, 64, 1);
    private static final BlockPosition SECOND = new BlockPosition(2, 64, 2);

    @Test void netReplacementAtLimitRetainsQuotaWithoutReleasingOldClaimEarly() throws IOException {
        Fixture fixture = new Fixture();
        ManagedBlockInstance old = fixture.confirmed("test:altar", FIRST);
        long beforeRevision = fixture.service.ledger().revision();

        ManagedAreaReplacementPlan plan = fixture.plan(Set.of(FIRST, SECOND), placement("test:altar", SECOND));

        assertEquals(beforeRevision, fixture.service.ledger().revision());
        assertEquals(old.id(), fixture.service.at(WORLD, FIRST).orElseThrow().id());
        assertTrue(plan.after().instances().stream().noneMatch(value -> value.id().equals(old.id())));
        fixture.service.lockAreaReplacement(plan);
        fixture.service.commitAreaReplacement(plan);
        assertTrue(fixture.service.at(WORLD, FIRST).isEmpty());
        assertEquals(1, fixture.service.count("test:altar"));
        fixture.service.unlockAreaReplacement(plan, ManagedAreaReplacementPlan.Outcome.WORLD_DURABLY_SAVED);
    }

    @Test void overQuotaAndDuplicateAfterPositionsAreRefusedBeforeAnyMutation() throws IOException {
        Fixture fixture = new Fixture();
        assertThrows(IllegalStateException.class, () -> fixture.plan(Set.of(FIRST, SECOND),
                placement("test:altar", FIRST), placement("test:altar", SECOND)));
        assertThrows(IllegalArgumentException.class, () -> fixture.plan(Set.of(FIRST),
                placement("test:altar", FIRST), placement("test:heart", FIRST)));
        assertEquals(ManagedBlockLedger.empty(), fixture.service.ledger());
        assertEquals(1, fixture.nextId.get(), "rejected plans must not consume managed identities");
    }

    @Test void retainedIdentityIsExactAndOnlyAdditionsReceiveNewIds() throws IOException {
        Fixture fixture = new Fixture();
        ManagedBlockInstance existing = fixture.confirmed("test:altar", FIRST);
        ManagedAreaReplacementPlan plan = fixture.plan(Set.of(FIRST, SECOND),
                placement("test:altar", FIRST), placement("test:heart", SECOND));

        ManagedBlockInstance retained = plan.after().instances().stream()
                .filter(value -> value.position().equals(FIRST)).findFirst().orElseThrow();
        ManagedBlockInstance added = plan.after().instances().stream()
                .filter(value -> value.position().equals(SECOND)).findFirst().orElseThrow();
        assertSame(existing, retained);
        assertNotEquals(existing.id(), added.id());
        assertEquals(ManagedBlockInstance.Registration.CONFIRMED, added.registration());
        assertEquals(ManagedBlockInstance.Origin.DISCOVERED, added.origin());
        assertEquals(plan.after().revision(), added.revision());
    }

    @Test void unchangedPreexistingOverCapInstancesMayRemainButNoNewIdentityCanBeAdded() throws IOException {
        Fixture fixture = new Fixture();
        ManagedBlockInstance first = fixture.service.adopt("test:altar", WORLD, FIRST, ManagedBlockInstance.Origin.DISCOVERED);
        fixture.service.adopt("test:altar", WORLD, SECOND, ManagedBlockInstance.Origin.DISCOVERED);

        ManagedAreaReplacementPlan unchanged = fixture.plan(Set.of(FIRST), placement("test:altar", FIRST));
        assertEquals(fixture.service.ledger(), unchanged.after());
        assertEquals(first.id(), unchanged.after().instances().stream().filter(value -> value.position().equals(FIRST)).findFirst().orElseThrow().id());
        BlockPosition third = new BlockPosition(3, 64, 3);
        assertThrows(IllegalStateException.class, () -> fixture.plan(Set.of(third), placement("test:altar", third)));
    }

    @Test void lockRejectsOperationAndPlanIdentityMismatchesAndAllDirectMutators() throws IOException {
        Fixture fixture = new Fixture();
        ManagedBlockInstance existing = fixture.confirmed("test:altar", FIRST);
        ManagedAreaReplacementPlan plan = fixture.plan(Set.of(FIRST), List.of());
        fixture.service.lockAreaReplacement(plan);
        ManagedAreaReplacementPlan otherOperation = new ManagedAreaReplacementPlan(UUID.randomUUID(), plan.dimension(),
                plan.affected(), plan.before(), plan.after(), plan.expectedLimits());

        assertThrows(IllegalStateException.class, () -> fixture.service.commitAreaReplacement(otherOperation));
        assertThrows(IllegalStateException.class, () -> fixture.service.reserve("test:heart", WORLD, SECOND));
        assertThrows(IllegalStateException.class, () -> fixture.service.adopt("test:heart", WORLD, SECOND, ManagedBlockInstance.Origin.DISCOVERED));
        assertThrows(IllegalStateException.class, () -> fixture.service.confirm(existing.id()));
        assertThrows(IllegalStateException.class, () -> fixture.service.release(existing.id()));
        assertThrows(IllegalStateException.class, () -> fixture.service.setLimit("test:altar", 2, fixture.service.limits().revision()));
        assertThrows(IllegalStateException.class, fixture.service::completeMigration);
        assertEquals(plan.before(), fixture.service.ledger());
    }

    @Test void saveFailureKeepsOldLedgerAndLockForRetry() throws IOException {
        Fixture fixture = new Fixture();
        fixture.confirmed("test:altar", FIRST);
        ManagedAreaReplacementPlan plan = fixture.plan(Set.of(FIRST), List.of());
        fixture.service.lockAreaReplacement(plan); fixture.ledger.fail = true;

        assertThrows(IOException.class, () -> fixture.service.commitAreaReplacement(plan));
        assertEquals(plan.before(), fixture.service.ledger());
        assertEquals(plan, fixture.service.lockedAreaReplacement().orElseThrow());
        assertThrows(IllegalStateException.class, () -> fixture.service.reserve("test:heart", WORLD, SECOND));

        fixture.ledger.fail = false;
        fixture.service.commitAreaReplacement(plan);
        fixture.service.unlockAreaReplacement(plan, ManagedAreaReplacementPlan.Outcome.WORLD_DURABLY_SAVED);
        assertTrue(fixture.service.lockedAreaReplacement().isEmpty());
    }

    @Test void exactAfterPlanCanBeReinstatedAndCommittedIdempotentlyAfterRestart() throws IOException {
        Fixture fixture = new Fixture();
        fixture.confirmed("test:altar", FIRST);
        ManagedAreaReplacementPlan plan = fixture.plan(Set.of(FIRST, SECOND), placement("test:altar", SECOND));
        fixture.service.lockAreaReplacement(plan); fixture.service.commitAreaReplacement(plan);
        int saves = fixture.ledger.saves;

        ManagedBlockService restarted = fixture.restart();
        restarted.lockAreaReplacement(plan);
        restarted.commitAreaReplacement(plan);
        assertEquals(saves, fixture.ledger.saves);
        restarted.unlockAreaReplacement(plan, ManagedAreaReplacementPlan.Outcome.WORLD_DURABLY_SAVED);
    }

    @Test void codecIsDeterministicBoundedAndRoundTripsExactPlan() throws IOException {
        Fixture fixture = new Fixture(); fixture.confirmed("test:altar", FIRST);
        ManagedAreaReplacementPlan plan = fixture.plan(Set.of(SECOND, FIRST),
                placement("test:altar", FIRST), placement("test:heart", SECOND));
        byte[] first = ManagedAreaReplacementCodec.encode(plan);
        byte[] second = ManagedAreaReplacementCodec.encode(plan);
        assertArrayEquals(first, second);
        assertEquals(plan, ManagedAreaReplacementCodec.decode(first));
        assertThrows(IllegalArgumentException.class,
                () -> ManagedAreaReplacementCodec.decode(new byte[ManagedAreaReplacementCodec.MAX_ENCODED_BYTES + 1]));
        assertThrows(IllegalArgumentException.class,
                () -> ManagedAreaReplacementCodec.decode(Arrays.copyOf(first, first.length - 1)));
    }

    private static ManagedAreaReplacementPlan.Placement placement(String type, BlockPosition position) {
        return new ManagedAreaReplacementPlan.Placement(type, position);
    }

    private static ManagedBlockCatalog catalog() {
        ManagedBlockCatalog catalog = new ManagedBlockCatalog();
        catalog.register(new ManagedBlockDefinition("test:altar", "test:altar", "Altar", "Station", 1, ManagedBlockDefinition.LimitMode.EDITABLE));
        catalog.register(new ManagedBlockDefinition("test:heart", "test:heart", "Heart", "Root", 1, ManagedBlockDefinition.LimitMode.FIXED));
        return catalog;
    }

    private static final class Fixture {
        final MemoryLedger ledger = new MemoryLedger();
        final MemoryLimits limits = new MemoryLimits();
        final AtomicLong nextId = new AtomicLong(1);
        final ManagedBlockService service;

        Fixture() throws IOException { service = create(); }
        ManagedBlockService create() throws IOException {
            return new ManagedBlockService(catalog(), ledger, limits, () -> new UUID(0, nextId.getAndIncrement()));
        }
        ManagedBlockService restart() throws IOException { return create(); }
        ManagedBlockInstance confirmed(String type, BlockPosition position) throws IOException {
            ManagedBlockService.Reservation reservation = service.reserve(type, WORLD, position);
            return service.confirm(reservation.id());
        }
        ManagedAreaReplacementPlan plan(Set<BlockPosition> affected, ManagedAreaReplacementPlan.Placement... after) {
            return plan(affected, List.of(after));
        }
        ManagedAreaReplacementPlan plan(Set<BlockPosition> affected, List<ManagedAreaReplacementPlan.Placement> after) {
            return service.planAreaReplacement(UUID.randomUUID(), WORLD, affected, after);
        }
    }

    private static final class MemoryLedger implements ManagedBlockLedgerRepository {
        ManagedBlockLedger state = ManagedBlockLedger.empty(); boolean fail; int saves;
        public ManagedBlockLedger load() { return state; }
        public void save(ManagedBlockLedger ledger) throws IOException {
            if (fail) throw new IOException("deliberate");
            state = ledger; saves++;
        }
    }
    private static final class MemoryLimits implements ManagedBlockLimitRepository {
        ManagedBlockLimits state = ManagedBlockLimits.empty();
        public ManagedBlockLimits load() { return state; }
        public void save(ManagedBlockLimits limits) { state = limits; }
    }
}
