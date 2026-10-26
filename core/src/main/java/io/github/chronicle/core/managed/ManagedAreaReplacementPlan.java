package io.github.chronicle.core.managed;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;

import java.util.*;

/**
 * Durable, world-write-independent intent for replacing the managed blocks inside one area.
 * The coordinator persists this value before touching the world; this package deliberately owns no files.
 */
public record ManagedAreaReplacementPlan(UUID operationId, String dimension, List<BlockPosition> affected,
                                         ManagedBlockLedger before, ManagedBlockLedger after,
                                         ManagedBlockLimits expectedLimits) {
    public static final int MAX_AFFECTED_POSITIONS = 262_144;

    public ManagedAreaReplacementPlan {
        Objects.requireNonNull(operationId, "area replacement operation ID is required");
        if (dimension == null || !AuthoringSite.NAMESPACED_ID.matcher(dimension).matches())
            throw new IllegalArgumentException("area replacement dimension must be namespaced");
        Objects.requireNonNull(affected, "affected positions are required");
        if (affected.isEmpty() || affected.size() > MAX_AFFECTED_POSITIONS)
            throw new IllegalArgumentException("area replacement accepts 1-" + MAX_AFFECTED_POSITIONS + " affected positions");
        ArrayList<BlockPosition> sorted = new ArrayList<>(affected.size());
        HashSet<BlockPosition> unique = new HashSet<>();
        for (BlockPosition position : affected) {
            Objects.requireNonNull(position, "affected position is required");
            if (!unique.add(position)) throw new IllegalArgumentException("duplicate affected position");
            sorted.add(position);
        }
        sorted.sort(POSITION_ORDER);
        affected = List.copyOf(sorted);
        Objects.requireNonNull(before, "before ledger is required");
        Objects.requireNonNull(after, "after ledger is required");
        Objects.requireNonNull(expectedLimits, "expected managed limits are required");
        validateTransition(dimension, unique, before, after);
    }

    public boolean affects(String candidateDimension, BlockPosition position) {
        return dimension.equals(candidateDimension) && Collections.binarySearch(affected, position, POSITION_ORDER) >= 0;
    }

    public enum Outcome { WORLD_UNCHANGED, WORLD_DURABLY_SAVED }

    public record Placement(String typeId, BlockPosition position) {
        public Placement {
            if (typeId == null || !AuthoringSite.NAMESPACED_ID.matcher(typeId).matches())
                throw new IllegalArgumentException("managed placement type must be namespaced");
            Objects.requireNonNull(position, "managed placement position is required");
        }
    }

    private static void validateTransition(String dimension, Set<BlockPosition> affected,
                                           ManagedBlockLedger before, ManagedBlockLedger after) {
        if (before.migrationVersion() != after.migrationVersion())
            throw new IllegalArgumentException("area replacement cannot change the managed migration version");
        boolean changed = !before.instances().equals(after.instances());
        long expectedRevision = changed ? next(before.revision()) : before.revision();
        if (after.revision() != expectedRevision)
            throw new IllegalArgumentException("area replacement after-ledger revision is invalid");

        Map<PositionKey, ManagedBlockInstance> oldByPosition = positions(before);
        Map<PositionKey, ManagedBlockInstance> newByPosition = positions(after);
        Set<PositionKey> positions = new HashSet<>(oldByPosition.keySet());
        positions.addAll(newByPosition.keySet());
        Map<UUID, ManagedBlockInstance> oldById = new HashMap<>();
        for (ManagedBlockInstance instance : before.instances()) oldById.put(instance.id(), instance);

        for (PositionKey key : positions) {
            ManagedBlockInstance oldValue = oldByPosition.get(key);
            ManagedBlockInstance newValue = newByPosition.get(key);
            boolean inArea = key.dimension.equals(dimension) && affected.contains(key.position);
            if (!inArea && !Objects.equals(oldValue, newValue))
                throw new IllegalArgumentException("area replacement changes a managed block outside its affected positions");
            if (!inArea || oldValue == null || newValue == null) continue;
            if (oldValue.typeId().equals(newValue.typeId()) && !oldValue.equals(newValue))
                throw new IllegalArgumentException("unchanged managed placement must retain its exact instance identity");
        }
        for (ManagedBlockInstance instance : after.instances()) {
            ManagedBlockInstance oldIdentity = oldById.get(instance.id());
            if (oldIdentity != null && !oldIdentity.equals(instance))
                throw new IllegalArgumentException("area replacement cannot repurpose a managed instance identity");
            if (oldIdentity == null) {
                if (!instance.dimension().equals(dimension) || !affected.contains(instance.position()))
                    throw new IllegalArgumentException("new managed instance is outside the affected area");
                if (instance.registration() != ManagedBlockInstance.Registration.CONFIRMED
                        || instance.origin() != ManagedBlockInstance.Origin.DISCOVERED
                        || instance.revision() != after.revision())
                    throw new IllegalArgumentException("new area-managed instance metadata is invalid");
            }
        }
    }

    private static Map<PositionKey, ManagedBlockInstance> positions(ManagedBlockLedger ledger) {
        Map<PositionKey, ManagedBlockInstance> result = new HashMap<>();
        for (ManagedBlockInstance instance : ledger.instances())
            result.put(new PositionKey(instance.dimension(), instance.position()), instance);
        return result;
    }

    private static long next(long revision) {
        try { return Math.addExact(revision, 1); }
        catch (ArithmeticException exception) { throw new IllegalArgumentException("managed ledger revision exhausted", exception); }
    }

    private record PositionKey(String dimension, BlockPosition position) { }

    static final Comparator<BlockPosition> POSITION_ORDER = Comparator.comparingInt(BlockPosition::x)
            .thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z);
}
