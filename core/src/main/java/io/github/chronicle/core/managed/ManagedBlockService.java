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
    private ManagedAreaReplacementPlan areaReplacement;

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
    public synchronized Optional<ManagedAreaReplacementPlan> lockedAreaReplacement() { return Optional.ofNullable(areaReplacement); }

    public synchronized boolean migrationRequired() {
        return ledger.migrationVersion() < ManagedBlockLedger.CURRENT_MIGRATION;
    }

    /** Commits the migration marker only after every legacy candidate has been considered. */
    public synchronized void completeMigration() throws IOException {
        requireAreaReplacementUnlocked();
        if (!migrationRequired()) return;
        long revision = next(ledger.revision(), "managed ledger");
        commitLedger(new ManagedBlockLedger(revision, ManagedBlockLedger.CURRENT_MIGRATION, ledger.instances()));
    }

    public synchronized Reservation reserve(String typeId, String dimension, BlockPosition position) throws IOException {
        requireAreaReplacementUnlocked();
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
        requireAreaReplacementUnlocked();
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
        requireAreaReplacementUnlocked();
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
        requireAreaReplacementUnlocked();
        int index = indexOrMissing(id);
        if (index < 0) return false;
        long revision = next(ledger.revision(), "managed ledger");
        List<ManagedBlockInstance> entries = new ArrayList<>(ledger.instances()); entries.remove(index);
        commitLedger(new ManagedBlockLedger(revision, ledger.migrationVersion(), entries));
        return true;
    }

    public synchronized boolean releaseAt(String dimension, BlockPosition position) throws IOException {
        requireAreaReplacementUnlocked();
        Optional<ManagedBlockInstance> existing = at(dimension, position);
        return existing.isPresent() && release(existing.get().id());
    }

    public synchronized ManagedBlockLimits setLimit(String typeId, int value, long expectedRevision) throws IOException {
        requireAreaReplacementUnlocked();
        ManagedBlockDefinition definition = definition(typeId);
        if (definition.limitMode() == ManagedBlockDefinition.LimitMode.FIXED) throw new IllegalStateException(definition.label() + " has a fixed limit.");
        if (value < 0 || value > ManagedBlockDefinition.MAX_LIMIT) throw new IllegalArgumentException("Managed block limit must be 0-" + ManagedBlockDefinition.MAX_LIMIT + ".");
        if (limits.revision() != expectedRevision) throw new IllegalStateException("Managed block limits changed; reopen this page.");
        int placed = count(typeId);
        if (value < placed) throw new IllegalArgumentException("Limit cannot be below " + placed + " placed blocks.");
        long revision = next(limits.revision(), "managed limit policy");
        Map<String, Integer> overrides = new TreeMap<>(limits.overrides());
        if (value == definition.defaultLimit()) overrides.remove(typeId); else overrides.put(typeId, value);
        ManagedBlockLimits candidate = new ManagedBlockLimits(revision, overrides);
        limitRepository.save(candidate); limits = candidate; return candidate;
    }

    /**
     * Builds a side-effect-free intent. {@code affected} should be only the union of managed positions before and
     * after replacement, rather than every cell in the world volume.
     */
    public synchronized ManagedAreaReplacementPlan planAreaReplacement(UUID operationId, String dimension,
            Set<BlockPosition> affected, List<ManagedAreaReplacementPlan.Placement> after) {
        requireAreaReplacementUnlocked();
        Objects.requireNonNull(operationId, "area replacement operation ID is required");
        Objects.requireNonNull(affected, "affected managed positions are required");
        Objects.requireNonNull(after, "after managed placements are required");
        if (affected.isEmpty() || affected.size() > ManagedAreaReplacementPlan.MAX_AFFECTED_POSITIONS)
            throw new IllegalArgumentException("affected managed position count is outside limits");
        if (after.size() > ManagedBlockLedger.MAX_INSTANCES)
            throw new IllegalArgumentException("after managed placement count is outside limits");
        // Construct once up-front for validation and deterministic ordering without changing caller collections.
        List<BlockPosition> sortedAffected = new ArrayList<>(affected);
        if (sortedAffected.size() != affected.size()) throw new IllegalArgumentException("duplicate affected managed position");
        sortedAffected.sort(ManagedAreaReplacementPlan.POSITION_ORDER);
        Set<BlockPosition> affectedCopy = Set.copyOf(sortedAffected);

        Map<BlockPosition, ManagedAreaReplacementPlan.Placement> desired = new HashMap<>();
        for (ManagedAreaReplacementPlan.Placement placement : after) {
            Objects.requireNonNull(placement, "after managed placement is required");
            if (!affectedCopy.contains(placement.position()))
                throw new IllegalArgumentException("after managed placement is outside the affected positions");
            if (desired.putIfAbsent(placement.position(), placement) != null)
                throw new IllegalArgumentException("duplicate after managed placement position");
            definition(placement.typeId());
        }

        long candidateRevision = ledger.revision() == Long.MAX_VALUE ? Long.MAX_VALUE : ledger.revision() + 1;
        List<ManagedBlockInstance> candidate = new ArrayList<>(ledger.instances().size() + desired.size());
        Set<BlockPosition> satisfied = new HashSet<>();
        Set<UUID> usedIds = new HashSet<>();
        for (ManagedBlockInstance instance : ledger.instances()) {
            usedIds.add(instance.id());
            if (!instance.dimension().equals(dimension) || !affectedCopy.contains(instance.position())) {
                candidate.add(instance);
                continue;
            }
            ManagedAreaReplacementPlan.Placement placement = desired.get(instance.position());
            if (placement != null && placement.typeId().equals(instance.typeId())) {
                candidate.add(instance);
                satisfied.add(instance.position());
            }
        }
        List<ManagedAreaReplacementPlan.Placement> additions = desired.values().stream()
                .filter(placement -> !satisfied.contains(placement.position()))
                .sorted(Comparator.comparing(ManagedAreaReplacementPlan.Placement::position,
                        ManagedAreaReplacementPlan.POSITION_ORDER).thenComparing(ManagedAreaReplacementPlan.Placement::typeId))
                .toList();
        if (candidate.size() + additions.size() > ManagedBlockLedger.MAX_INSTANCES)
            throw new IllegalStateException("Managed instance limit reached.");
        Map<String, Integer> retainedCounts = new HashMap<>(), additionCounts = new HashMap<>();
        for (ManagedBlockInstance instance : candidate) retainedCounts.merge(instance.typeId(), 1, Integer::sum);
        for (ManagedAreaReplacementPlan.Placement placement : additions) additionCounts.merge(placement.typeId(), 1, Integer::sum);
        validateReplacementQuotaCounts(retainedCounts, additionCounts, limits);
        for (ManagedAreaReplacementPlan.Placement placement : additions) {
            ManagedBlockDefinition definition = definition(placement.typeId());
            UUID id = Objects.requireNonNull(ids.get(), "managed UUID source returned null");
            if (!usedIds.add(id)) throw new IllegalStateException("managed UUID source returned a duplicate identity");
            candidate.add(new ManagedBlockInstance(id, placement.typeId(), definition.blockId(), dimension,
                    placement.position(), ManagedBlockInstance.Registration.CONFIRMED,
                    ManagedBlockInstance.Origin.DISCOVERED, candidateRevision));
        }
        boolean changed = !candidate.equals(ledger.instances());
        ManagedBlockLedger afterLedger = changed
                ? new ManagedBlockLedger(candidateRevision, ledger.migrationVersion(), candidate)
                : ledger;
        return new ManagedAreaReplacementPlan(operationId, dimension, sortedAffected, ledger, afterLedger, limits);
    }

    /** Reinstalls the durable operation lock before gameplay resumes after a restart. */
    public synchronized void lockAreaReplacement(ManagedAreaReplacementPlan plan) {
        Objects.requireNonNull(plan, "managed area replacement plan is required");
        if (areaReplacement != null) {
            if (areaReplacement.equals(plan)) return;
            throw new IllegalStateException("another managed area replacement is already locked");
        }
        validateReplacementPlan(plan);
        if (!limits.equals(plan.expectedLimits()))
            throw new IllegalStateException("managed block limits no longer match the replacement plan");
        if (!ledger.equals(plan.before()) && !ledger.equals(plan.after()))
            throw new IllegalStateException("managed block ledger no longer matches either side of the replacement plan");
        areaReplacement = plan;
    }

    /** Saves the complete after-ledger before publishing it. Exact after-state recovery is idempotent. */
    public synchronized void commitAreaReplacement(ManagedAreaReplacementPlan plan) throws IOException {
        requireLockedPlan(plan);
        if (ledger.equals(plan.after())) return;
        if (!ledger.equals(plan.before()))
            throw new IllegalStateException("managed block ledger conflicts with the replacement plan");
        commitLedger(plan.after());
    }

    /**
     * Releases the lock only when the ledger proves the caller's declared outcome. WORLD_DURABLY_SAVED is a caller
     * attestation that the exact planned cells have already been durably saved; this service cannot inspect that receipt.
     */
    public synchronized void unlockAreaReplacement(ManagedAreaReplacementPlan plan,
                                                   ManagedAreaReplacementPlan.Outcome outcome) {
        requireLockedPlan(plan);
        Objects.requireNonNull(outcome, "managed area replacement outcome is required");
        ManagedBlockLedger expected = outcome == ManagedAreaReplacementPlan.Outcome.WORLD_UNCHANGED ? plan.before() : plan.after();
        if (!ledger.equals(expected))
            throw new IllegalStateException("managed block ledger does not prove the declared area replacement outcome");
        areaReplacement = null;
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
    private void validateReplacementPlan(ManagedAreaReplacementPlan plan) {
        Set<UUID> beforeIds = new HashSet<>();
        for (ManagedBlockInstance instance : plan.before().instances()) beforeIds.add(instance.id());
        for (ManagedBlockInstance instance : plan.after().instances()) {
            Optional<ManagedBlockDefinition> known = catalog.get(instance.typeId());
            if (known.isEmpty()) {
                if (!beforeIds.contains(instance.id()))
                    throw new IllegalStateException("replacement introduces unavailable managed type " + instance.typeId());
            } else if (!known.get().blockId().equals(instance.blockId()))
                throw new IllegalStateException("managed replacement block mapping changed for " + instance.typeId());
        }
        validateReplacementQuotas(plan.before().instances(), plan.after().instances(), plan.expectedLimits());
    }
    private void validateReplacementQuotas(List<ManagedBlockInstance> before, List<ManagedBlockInstance> after,
                                           ManagedBlockLimits policy) {
        Set<UUID> previous = new HashSet<>();
        for (ManagedBlockInstance instance : before) previous.add(instance.id());
        Map<String, Integer> retained = new HashMap<>(), additions = new HashMap<>();
        for (ManagedBlockInstance instance : after) {
            (previous.contains(instance.id()) ? retained : additions).merge(instance.typeId(), 1, Integer::sum);
        }
        validateReplacementQuotaCounts(retained, additions, policy);
    }
    private void validateReplacementQuotaCounts(Map<String, Integer> retained, Map<String, Integer> additions,
                                                ManagedBlockLimits policy) {
        Set<String> types = new HashSet<>(retained.keySet()); types.addAll(additions.keySet());
        for (String typeId : types) {
            Optional<ManagedBlockDefinition> known = catalog.get(typeId);
            if (known.isEmpty()) {
                if (additions.getOrDefault(typeId, 0) > 0)
                    throw new IllegalStateException("replacement introduces unavailable managed type " + typeId);
                continue;
            }
            ManagedBlockDefinition definition = known.get();
            int retainedCount = retained.getOrDefault(typeId, 0);
            int additionCount = additions.getOrDefault(typeId, 0);
            int available = Math.max(0, effectiveLimit(definition, policy) - retainedCount);
            if (additionCount > available)
                throw new IllegalStateException(definition.label() + " replacement exceeds its limit ("
                        + (retainedCount + additionCount) + "/" + effectiveLimit(definition, policy) + ").");
        }
    }
    private int effectiveLimit(ManagedBlockDefinition definition, ManagedBlockLimits policy) {
        return definition.limitMode() == ManagedBlockDefinition.LimitMode.FIXED ? definition.defaultLimit()
                : policy.overrides().getOrDefault(definition.typeId(), definition.defaultLimit());
    }
    private void requireAreaReplacementUnlocked() {
        if (areaReplacement != null)
            throw new IllegalStateException("managed blocks are locked by area replacement " + areaReplacement.operationId());
    }
    private void requireLockedPlan(ManagedAreaReplacementPlan plan) {
        Objects.requireNonNull(plan, "managed area replacement plan is required");
        if (areaReplacement == null) throw new IllegalStateException("no managed area replacement is locked");
        if (!areaReplacement.operationId().equals(plan.operationId()))
            throw new IllegalStateException("managed area replacement operation ID does not match the lock");
        if (!areaReplacement.equals(plan))
            throw new IllegalStateException("managed area replacement plan does not match the locked operation");
    }
    private void requireFreePosition(String dimension, BlockPosition position) { at(dimension, position).ifPresent(existing -> { throw new IllegalStateException("Managed position already belongs to " + existing.typeId() + "."); }); }
    private int index(UUID id) { int index = indexOrMissing(id); if (index < 0) throw new IllegalArgumentException("Unknown managed instance: " + id); return index; }
    private int indexOrMissing(UUID id) { for (int i = 0; i < ledger.instances().size(); i++) if (ledger.instances().get(i).id().equals(id)) return i; return -1; }
    private void commitLedger(ManagedBlockLedger candidate) throws IOException { ledgerRepository.save(candidate); ledger = candidate; }
    private static long next(long revision, String name) { try { return Math.addExact(revision, 1); } catch (ArithmeticException exception) { throw new IllegalStateException(name + " revision exhausted", exception); } }

    public record Reservation(UUID id, String typeId, String dimension, BlockPosition position) { }
}
