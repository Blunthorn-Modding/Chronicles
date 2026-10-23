package io.github.chronicle.core.managed;

import io.github.chronicle.core.authoring.BlockPosition;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

/** Server-thread-owned transactional state; all candidates publish only after durable saves. */
public final class ManagedBlockService {
    private final ManagedBlockCatalog catalog;
    private final ManagedBlockLedgerRepository ledgerRepository;
    private final ManagedBlockLimitRepository limitRepository;
    private final Supplier<UUID> ids;
    private ManagedBlockLedger ledger;
    private ManagedBlockLimits limits;

    public ManagedBlockService(ManagedBlockCatalog catalog, ManagedBlockLedgerRepository ledgerRepository,
                               ManagedBlockLimitRepository limitRepository) throws IOException {
        this(catalog, ledgerRepository, limitRepository, UUID::randomUUID);
    }

    ManagedBlockService(ManagedBlockCatalog catalog, ManagedBlockLedgerRepository ledgerRepository,
                        ManagedBlockLimitRepository limitRepository, Supplier<UUID> ids) throws IOException {
        this.catalog = Objects.requireNonNull(catalog, "managed block catalog is required");
        this.ledgerRepository = Objects.requireNonNull(ledgerRepository, "managed ledger repository is required");
        this.limitRepository = Objects.requireNonNull(limitRepository, "managed limit repository is required");
        this.ids = Objects.requireNonNull(ids, "managed UUID source is required");
        ledger = Objects.requireNonNull(ledgerRepository.load(), "managed ledger repository returned null");
        limits = Objects.requireNonNull(limitRepository.load(), "managed limit repository returned null");
        validateCatalogMappings();
        validateLimitOverrides();
    }

    public synchronized ManagedBlockLedger ledger() { return ledger; }
    public synchronized ManagedBlockLimits limits() { return limits; }

    public synchronized boolean migrationRequired() {
        return ledger.migrationVersion() < ManagedBlockLedger.CURRENT_MIGRATION;
    }

    /** Commits the migration marker only after every legacy candidate has been considered. */
    public synchronized void completeMigration() throws IOException {
        if (!migrationRequired()) return;
        long revision = next(ledger.revision(), "managed ledger");
        commitLedger(new ManagedBlockLedger(revision, ManagedBlockLedger.CURRENT_MIGRATION, ledger.instances()));
    }

    public synchronized Reservation reserve(String typeId, String dimension, BlockPosition position) throws IOException {
        ManagedBlockDefinition definition = definition(typeId);
        requireFreePosition(dimension, position);
        int count = count(typeId);
        int limit = effectiveLimit(definition);
        if (count >= limit) throw new IllegalStateException(definition.label() + " limit reached (" + count + "/" + limit + ").");
        long revision = next(ledger.revision(), "managed ledger");
        ManagedBlockInstance instance = new ManagedBlockInstance(ids.get(), typeId, definition.blockId(), dimension, position,
                ManagedBlockInstance.Registration.RESERVED, ManagedBlockInstance.Origin.ITEM, revision);
        List<ManagedBlockInstance> next = new ArrayList<>(ledger.instances()); next.add(instance);
        commitLedger(new ManagedBlockLedger(revision, ledger.migrationVersion(), next));
        return new Reservation(instance.id(), typeId, dimension, position);
    }

    public synchronized ManagedBlockInstance confirm(UUID id) throws IOException {
        int index = index(id);
        ManagedBlockInstance current = ledger.instances().get(index);
        if (current.registration() == ManagedBlockInstance.Registration.CONFIRMED) return current;
        long revision = next(ledger.revision(), "managed ledger");
        ManagedBlockInstance confirmed = current.confirmed(revision);
        List<ManagedBlockInstance> entries = new ArrayList<>(ledger.instances()); entries.set(index, confirmed);
        commitLedger(new ManagedBlockLedger(revision, ledger.migrationVersion(), entries));
        return confirmed;
    }

    /** Discovery bypasses the placement cap but never a physical-position conflict. */
    public synchronized ManagedBlockInstance adopt(String typeId, String dimension, BlockPosition position,
                                                   ManagedBlockInstance.Origin origin) throws IOException {
        ManagedBlockDefinition definition = definition(typeId);
        Optional<ManagedBlockInstance> existing = at(dimension, position);
        if (existing.isPresent()) {
            ManagedBlockInstance value = existing.get();
            if (!value.typeId().equals(typeId) || !value.blockId().equals(definition.blockId()))
                throw new IllegalStateException("Managed position is already claimed by " + value.typeId() + ".");
            return value.registration() == ManagedBlockInstance.Registration.CONFIRMED ? value : confirm(value.id());
        }
        if (ledger.instances().size() >= ManagedBlockLedger.MAX_INSTANCES) throw new IllegalStateException("Managed instance limit reached.");
        long revision = next(ledger.revision(), "managed ledger");
        ManagedBlockInstance instance = new ManagedBlockInstance(ids.get(), typeId, definition.blockId(), dimension, position,
                ManagedBlockInstance.Registration.CONFIRMED, origin, revision);
        List<ManagedBlockInstance> entries = new ArrayList<>(ledger.instances()); entries.add(instance);
        commitLedger(new ManagedBlockLedger(revision, ledger.migrationVersion(), entries));
        return instance;
    }

    public synchronized boolean release(UUID id) throws IOException {
        int index = indexOrMissing(id);
        if (index < 0) return false;
        long revision = next(ledger.revision(), "managed ledger");
        List<ManagedBlockInstance> entries = new ArrayList<>(ledger.instances()); entries.remove(index);
        commitLedger(new ManagedBlockLedger(revision, ledger.migrationVersion(), entries));
        return true;
    }

    public synchronized boolean releaseAt(String dimension, BlockPosition position) throws IOException {
        Optional<ManagedBlockInstance> existing = at(dimension, position);
        return existing.isPresent() && release(existing.get().id());
    }

    public synchronized ManagedBlockLimits setLimit(String typeId, int value, long expectedRevision) throws IOException {
        ManagedBlockDefinition definition = definition(typeId);
        if (definition.limitMode() == ManagedBlockDefinition.LimitMode.FIXED) throw new IllegalStateException(definition.label() + " has a fixed limit.");
        if (value < 0 || value > ManagedBlockDefinition.MAX_LIMIT) throw new IllegalArgumentException("Managed block limit must be 0-" + ManagedBlockDefinition.MAX_LIMIT + ".");
        if (limits.revision() != expectedRevision) throw new IllegalStateException("Managed block limits changed; reopen this page.");
        long revision = next(limits.revision(), "managed limit policy");
        Map<String, Integer> overrides = new TreeMap<>(limits.overrides());
        if (value == definition.defaultLimit()) overrides.remove(typeId); else overrides.put(typeId, value);
        ManagedBlockLimits candidate = new ManagedBlockLimits(revision, overrides);
        limitRepository.save(candidate); limits = candidate; return candidate;
    }

    public synchronized int effectiveLimit(ManagedBlockDefinition definition) {
        return definition.limitMode() == ManagedBlockDefinition.LimitMode.FIXED ? definition.defaultLimit()
                : limits.overrides().getOrDefault(definition.typeId(), definition.defaultLimit());
    }
    public synchronized int count(String typeId) { return (int) ledger.instances().stream().filter(i -> i.typeId().equals(typeId)).count(); }
    public synchronized Optional<ManagedBlockInstance> at(String dimension, BlockPosition position) {
        return ledger.instances().stream().filter(i -> i.dimension().equals(dimension) && i.position().equals(position)).findFirst();
    }
    public synchronized Optional<ManagedBlockInstance> get(UUID id) { return ledger.instances().stream().filter(i -> i.id().equals(id)).findFirst(); }

    private void validateCatalogMappings() {
        for (ManagedBlockInstance instance : ledger.instances()) {
            catalog.get(instance.typeId()).ifPresent(definition -> {
                if (!definition.blockId().equals(instance.blockId()))
                    throw new IllegalStateException("Managed type " + instance.typeId() + " changed block ID from " + instance.blockId() + " to " + definition.blockId() + ".");
            });
        }
    }
    private void validateLimitOverrides() {
        for (var entry : limits.overrides().entrySet()) {
            catalog.get(entry.getKey()).ifPresent(definition -> {
                if (definition.limitMode() == ManagedBlockDefinition.LimitMode.FIXED)
                    throw new IllegalStateException("Fixed managed block type has a persisted limit override: " + definition.typeId() + ".");
            });
        }
    }
    private ManagedBlockDefinition definition(String typeId) { return catalog.get(typeId).orElseThrow(() -> new IllegalArgumentException("Unknown managed block type: " + typeId)); }
    private void requireFreePosition(String dimension, BlockPosition position) { at(dimension, position).ifPresent(existing -> { throw new IllegalStateException("Managed position already belongs to " + existing.typeId() + "."); }); }
    private int index(UUID id) { int index = indexOrMissing(id); if (index < 0) throw new IllegalArgumentException("Unknown managed instance: " + id); return index; }
    private int indexOrMissing(UUID id) { for (int i = 0; i < ledger.instances().size(); i++) if (ledger.instances().get(i).id().equals(id)) return i; return -1; }
    private void commitLedger(ManagedBlockLedger candidate) throws IOException { ledgerRepository.save(candidate); ledger = candidate; }
    private static long next(long revision, String name) { try { return Math.addExact(revision, 1); } catch (ArithmeticException exception) { throw new IllegalStateException(name + " revision exhausted", exception); } }

    public record Reservation(UUID id, String typeId, String dimension, BlockPosition position) { }
}
