package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static io.github.chronicle.quietbelow.ecology.EcologyTerrain.AIR;

/**
 * Bounded terminal cleanup whose progress lives beside the changed blocks in each chunk.
 * No chunk is loaded or ticketed by this class.
 */
public final class EcologyEradication {
    public static final int MAX_READS = 512;
    public static final int MAX_WRITES = 4;
    public static final int MAX_CHUNKS_PER_TICK = 8;
    private static final int MARKER_SCHEMA = 1;
    private static final Set<String> MARKER_FIELDS = Set.of("schema", "job", "plan", "count", "done", "conflicts", "checksum");
    private static final EcologyTerrain.State WATER = new EcologyTerrain.State("minecraft:water", Map.of("level", "0"));

    static final Codec<Marker> MARKER_CODEC = Codec.PASSTHROUGH.xmap(EcologyEradication::decodeMarker, Marker::dynamic);
    private static AttachmentType<Marker> markerType;

    private final UUID job;
    private final List<ChunkPlan> plans;
    private final Map<Long, Integer> planIndexes;
    private final Map<EcologyTerrain.Pos, TargetRef> targetsByPosition;
    private final BitSet observedDone;
    private final BitSet observedConflicts;
    private final BitSet completedPlans;
    private final Marker[] observedMarkers;
    private final int[] cursors;
    private int pendingPlanCount;
    private int completedCount;
    private int conflictCount;
    private final PlanSchedule schedule;
    private String failure;
    private boolean writing;

    public EcologyEradication(UUID job, EcologyTerrain.Pos root, EcologyTerrain.State rootState,
            List<EcologyEngine.Cell> frozenCells) {
        this.job = Objects.requireNonNull(job, "eradication job");
        Objects.requireNonNull(root, "eradication root");
        Objects.requireNonNull(rootState, "eradication root state");
        Objects.requireNonNull(frozenCells, "frozen ecology cells");
        if (frozenCells.size() > EcologyEngine.MAX_CELLS) throw new IllegalArgumentException("Eradication plan exceeds ecology capacity");

        Map<Long, List<Target>> grouped = new TreeMap<>();
        Set<EcologyTerrain.Pos> positions = new HashSet<>();
        positions.add(root);
        Target rootTarget = new Target(root, rootState, AIR, true);
        grouped.computeIfAbsent(chunk(root), ignored -> new ArrayList<>()).add(rootTarget);
        for (EcologyEngine.Cell cell : List.copyOf(frozenCells)) {
            if (cell == null) throw new IllegalArgumentException("Eradication cells must be complete");
            if (!positions.add(cell.position())) throw new IllegalArgumentException("Duplicate eradication target: " + cell.position());
            EcologyTerrain.State restore = cell.adopted() ? AIR : cell.before();
            grouped.computeIfAbsent(chunk(cell.position()), ignored -> new ArrayList<>())
                    .add(new Target(cell.position(), cell.after(), restore, false));
        }

        long rootChunk = chunk(root);
        List<Long> order = new ArrayList<>(grouped.keySet());
        order.remove(rootChunk);
        order.sort(Long::compare);
        order.addFirst(rootChunk);
        List<ChunkPlan> built = new ArrayList<>(order.size());
        Map<Long, ChunkPlan> byChunk = new LinkedHashMap<>();
        Map<Long, Integer> indexes = new HashMap<>();
        Map<EcologyTerrain.Pos, TargetRef> byPosition = new HashMap<>();
        int global = 0;
        for (long key : order) {
            List<Target> targets = grouped.get(key);
            targets.sort(Comparator.comparing(Target::root).reversed()
                    .thenComparingInt(target -> target.position().x())
                    .thenComparingInt(target -> target.position().y())
                    .thenComparingInt(target -> target.position().z()));
            int cx = chunkX(key), cz = chunkZ(key);
            ChunkPlan plan = new ChunkPlan(cx, cz, targets, global);
            built.add(plan);
            byChunk.put(key, plan);
            indexes.put(key, built.size() - 1);
            for (int local = 0; local < targets.size(); local++) {
                byPosition.put(targets.get(local).position(), new TargetRef(plan, local));
            }
            global += targets.size();
        }
        if (global != frozenCells.size() + 1 || global > EcologyEngine.MAX_CELLS + 1) {
            throw new IllegalArgumentException("Invalid eradication plan size");
        }
        plans = List.copyOf(built);
        planIndexes = Map.copyOf(indexes);
        targetsByPosition = Map.copyOf(byPosition);
        observedDone = new BitSet(global);
        observedConflicts = new BitSet(global);
        completedPlans = new BitSet(plans.size());
        observedMarkers = new Marker[plans.size()];
        cursors = new int[plans.size()];
        pendingPlanCount = plans.size();
        schedule = new PlanSchedule(plans.size());
    }

    /** Registers the single persistent per-chunk marker. Safe to call repeatedly in one runtime. */
    public static synchronized void register() {
        if (markerType == null) {
            markerType = AttachmentRegistry.createPersistent(Identifier.of("quiet_below", "eradication_chunk_v1"), MARKER_CODEC);
        }
    }

    /** Runs only against naturally loaded chunks and never exceeds the documented read/write budgets. */
    public void tick(ServerWorld world, boolean advancing) {
        Objects.requireNonNull(world, "eradication world");
        if (failure != null) return;
        if (!world.getServer().isOnThread()) {
            fail("Eradication must run on the server thread");
            return;
        }
        if (markerType == null) {
            fail("Eradication attachment is not registered");
            return;
        }
        Budget budget = new Budget();
        try {
            ChunkPlan rootPlan = plans.getFirst();
            if (!observedDone.get(0)) {
                inspect(world, rootPlan, advancing, budget, 1, true);
                if (!observedDone.get(0) || failure != null || budget.writes >= MAX_WRITES) return;
            }

            BitSet visited = new BitSet(plans.size());
            while (budget.chunks < MAX_CHUNKS_PER_TICK
                    && budget.reads < MAX_READS && budget.writes < MAX_WRITES) {
                int planIndex = schedule.next(completedPlans, visited);
                if (planIndex < 0) break;
                visited.set(planIndex);
                ChunkPlan plan = plans.get(planIndex);
                inspect(world, plan, advancing, budget, MAX_WRITES, false);
                if (failure != null) return;
            }
        } catch (RuntimeException exception) {
            fail("Eradication held: " + message(exception));
        }
    }

    /** Called after a successful non-eradication edit; player changes always win. */
    public void changed(ServerWorld world, BlockPos position) {
        Objects.requireNonNull(world, "eradication world");
        Objects.requireNonNull(position, "eradication position");
        if (failure != null || writing) return;
        TargetRef reference;
        try { reference = targetsByPosition.get(QuietBelowEcology.pos(position)); }
        catch (RuntimeException ignored) { return; }
        if (reference == null) return;
        WorldChunk chunk = EcologyLoadedBlocks.chunk(world, position);
        if (chunk == null) return;
        try {
            Marker marker = marker(chunk, reference.plan());
            Decision decision = reference.target().decide(QuietBelowEcology.encode(chunk.getBlockState(position)));
            if (decision.status() == Status.PENDING) {
                observe(reference.plan(), marker);
                return;
            }
            Marker replacement = marker.complete(reference.local(), decision.status() == Status.CONFLICT);
            if (replacement != marker) setMarker(chunk, replacement);
            observeCompletion(reference.plan(), marker, replacement, reference.local());
        } catch (RuntimeException exception) {
            fail("Eradication edit reconciliation held: " + message(exception));
        }
    }

    public int total() { return targetsByPosition.size(); }
    public int completed() { return completedCount; }
    public int conflicts() { return conflictCount; }
    public int pendingChunks() { return pendingPlanCount; }
    public String failure() { return failure; }

    List<ChunkPlan> plansForTest() { return plans; }
    int cursorForTest(ChunkPlan plan) { return cursors[index(plan)]; }
    void observeForTest(ChunkPlan plan, Marker marker) { observe(plan, marker); }

    private void inspect(ServerWorld world, ChunkPlan plan, boolean advancing, Budget budget, int writeLimit, boolean rootOnly) {
        if (budget.chunks >= MAX_CHUNKS_PER_TICK || completedPlans.get(index(plan))) return;
        budget.chunks++;
        WorldChunk chunk = world.getChunkManager().getWorldChunk(plan.x(), plan.z());
        if (chunk == null) return;
        Marker existing = attached(chunk);
        if (existing != null) {
            validate(existing, plan);
            observe(plan, existing);
            if (existing.complete()) return;
        }
        if (!advancing || budget.writes >= writeLimit) return;
        Marker marker = existing == null ? Marker.empty(job, plan.identity(), plan.targets().size()) : existing;
        int planIndex = index(plan);
        int local = cursors[planIndex];
        while (local < plan.targets().size() && (!rootOnly || local == 0)
                && budget.reads < MAX_READS && budget.writes < writeLimit) {
            Target target = plan.targets().get(local);
            BlockPos position = QuietBelowEcology.blockPos(target.position());
            if (!world.isInBuildLimit(position) || !world.getWorldBorder().contains(position)) {
                throw new IllegalStateException("Eradication target is outside the active world bounds: " + target.position());
            }
            EcologyTerrain.State actual = QuietBelowEcology.encode(chunk.getBlockState(position));
            budget.reads++;
            Decision decision = target.decide(actual);
            boolean wrote = false;
            if (decision.status() == Status.PENDING) {
                // A pending marker is harmless if a crash occurs before the block mutation.
                if (existing == null) {
                    setMarker(chunk, marker);
                    existing = marker;
                    observe(plan, marker);
                }
                write(world, position, decision.replacement());
                budget.writes++;
                wrote = true;
                if (budget.reads >= MAX_READS) return;
                decision = postWriteDecision(target, decision, QuietBelowEcology.encode(chunk.getBlockState(position)));
                budget.reads++;
                if (decision.status() == Status.PENDING) return;
            }
            Marker previous = marker;
            marker = previous.complete(local, decision.status() == Status.CONFLICT);
            setMarker(chunk, marker);
            existing = marker;
            if (!wrote) budget.writes++;
            observeCompletion(plan, previous, marker, local);
            local = cursors[planIndex];
        }
    }

    private void write(ServerWorld world, BlockPos position, EcologyTerrain.State replacement) {
        BlockState state = QuietBelowEcology.decode(replacement);
        writing = true;
        try (EcologyHooks.Scope ignored = EcologyHooks.ownWrite()) {
            if (!world.setBlockState(position, state, Block.NOTIFY_ALL)) {
                throw new IllegalStateException("Eradication block write did not change its target: " + position.toShortString());
            }
        } finally {
            writing = false;
        }
    }

    private Marker marker(WorldChunk chunk, ChunkPlan plan) {
        Marker marker = attached(chunk);
        if (marker == null) return Marker.empty(job, plan.identity(), plan.targets().size());
        validate(marker, plan);
        return marker;
    }

    private Marker attached(WorldChunk chunk) { return ((AttachmentTarget) chunk).getAttached(requireType()); }
    private void setMarker(WorldChunk chunk, Marker marker) { ((AttachmentTarget) chunk).setAttached(requireType(), marker); }

    private void validate(Marker marker, ChunkPlan plan) {
        if (marker.corrupt()) throw new IllegalStateException("Corrupt eradication marker retained in chunk " + plan.x() + "," + plan.z());
        if (!job.equals(marker.job()) || !plan.identity().equals(marker.plan()) || marker.count() != plan.targets().size()) {
            throw new IllegalStateException("Eradication marker belongs to a different job or plan in chunk " + plan.x() + "," + plan.z());
        }
    }

    private void observe(ChunkPlan plan, Marker marker) {
        validate(marker, plan);
        int planIndex = index(plan);
        Marker previous = observedMarkers[planIndex];
        if (previous == marker) return;
        if (previous != null && !marker.covers(previous)) {
            throw new IllegalStateException("Eradication marker progress regressed in chunk " + plan.x() + "," + plan.z());
        }
        addBits(observedDone, plan.globalOffset(), marker.doneBits(), previous == null ? null : previous.doneBits());
        addBits(observedConflicts, plan.globalOffset(), marker.conflictBits(), previous == null ? null : previous.conflictBits());
        completedCount += marker.doneCount() - (previous == null ? 0 : previous.doneCount());
        conflictCount += marker.conflictCount() - (previous == null ? 0 : previous.conflictCount());
        observedMarkers[planIndex] = marker;
        cursors[planIndex] = marker.nextPending(cursors[planIndex]);
        markPlanComplete(planIndex, marker.complete());
    }

    private void observeCompletion(ChunkPlan plan, Marker previous, Marker replacement, int local) {
        int planIndex = index(plan);
        if (observedMarkers[planIndex] != previous) observe(plan, previous);
        if (replacement == previous) return;
        validate(replacement, plan);
        int global = plan.globalOffset() + local;
        if (!previous.done(local) && replacement.done(local)) {
            observedDone.set(global);
            completedCount++;
        }
        if (!previous.conflict(local) && replacement.conflict(local)) {
            observedConflicts.set(global);
            conflictCount++;
        }
        observedMarkers[planIndex] = replacement;
        cursors[planIndex] = replacement.nextPending(cursors[planIndex]);
        markPlanComplete(planIndex, replacement.complete());
    }

    private void markPlanComplete(int planIndex, boolean complete) {
        if (complete && !completedPlans.get(planIndex)) {
            completedPlans.set(planIndex);
            pendingPlanCount--;
        }
    }

    private int index(ChunkPlan plan) {
        Integer result = planIndexes.get(plan.key());
        if (result == null || plans.get(result) != plan) throw new IllegalArgumentException("Unknown eradication chunk plan");
        return result;
    }

    private static void addBits(BitSet destination, int offset, BitSet current, BitSet previous) {
        if (previous != null) current.andNot(previous);
        for (int bit = current.nextSetBit(0); bit >= 0; bit = current.nextSetBit(bit + 1)) {
            destination.set(offset + bit);
            if (bit == Integer.MAX_VALUE) break;
        }
    }

    static Decision postWriteDecision(Target target, Decision attempted, EcologyTerrain.State actual) {
        Objects.requireNonNull(target, "eradication target");
        Objects.requireNonNull(attempted, "eradication write decision");
        if (attempted.status() != Status.PENDING) throw new IllegalArgumentException("Only pending decisions write terrain");
        return target.decide(actual);
    }

    static final class PlanSchedule {
        private final int count;
        private int turn;

        PlanSchedule(int count) {
            if (count < 1) throw new IllegalArgumentException("Eradication requires at least one chunk plan");
            this.count = count;
            turn = count == 1 ? 0 : 1;
        }

        int next(BitSet completed, BitSet visited) {
            Objects.requireNonNull(completed, "completed eradication plans");
            Objects.requireNonNull(visited, "visited eradication plans");
            int normalized = turn >= 0 && turn < count ? turn : 0;
            int result = completed.nextClearBit(normalized);
            if (result >= count) result = completed.nextClearBit(0);
            if (result >= count || visited.get(result)) return -1;
            turn = result + 1;
            return result;
        }
    }

    private void fail(String reason) { if (failure == null) failure = reason; }
    private static String message(Throwable throwable) { return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage(); }
    private static AttachmentType<Marker> requireType() { if (markerType == null) throw new IllegalStateException("Eradication attachment is not registered"); return markerType; }

    enum Status { PENDING, DONE, CONFLICT }
    record Decision(Status status, EcologyTerrain.State replacement) {
        Decision { Objects.requireNonNull(status); if (status == Status.PENDING) Objects.requireNonNull(replacement); }
    }

    record Target(EcologyTerrain.Pos position, EcologyTerrain.State expected, EcologyTerrain.State restore, boolean root) {
        Target { Objects.requireNonNull(position); Objects.requireNonNull(expected); Objects.requireNonNull(restore); }

        Decision decide(EcologyTerrain.State actual) {
            Objects.requireNonNull(actual, "actual eradication state");
            if (!expected.vein()) {
                if (restore.sameOwnedShape(actual) || restore.air() && waterlogged(expected) && actual.id().equals(WATER.id())) return new Decision(Status.DONE, null);
                if (expected.sameOwnedShape(actual)) return new Decision(Status.PENDING, restore.air() && waterlogged(actual) ? WATER : restore);
                return new Decision(Status.CONFLICT, null);
            }
            if (!actual.vein()) {
                if (restore.sameOwnedShape(actual) || waterlogged(expected) && actual.id().equals(WATER.id())) {
                    return new Decision(Status.DONE, null);
                }
                return new Decision(Status.CONFLICT, null);
            }
            Map<String, String> remaining = new TreeMap<>(actual.properties());
            boolean campaignFace = false;
            for (int face = 0; face < EcologyTerrain.FACES.length; face++) {
                if (expected.face(face) && actual.face(face)) {
                    campaignFace = true;
                    remaining.put(EcologyTerrain.FACES[face], "false");
                }
            }
            if (!campaignFace) return new Decision(Status.DONE, null);
            for (String face : EcologyTerrain.FACES) {
                if ("true".equals(remaining.get(face))) {
                    return new Decision(Status.PENDING, new EcologyTerrain.State(actual.id(), remaining));
                }
            }
            return new Decision(Status.PENDING, waterlogged(actual) ? WATER : restore);
        }

        private static boolean waterlogged(EcologyTerrain.State state) { return "true".equals(state.properties().get("waterlogged")); }
    }

    record ChunkPlan(int x, int z, List<Target> targets, int globalOffset, String identity) {
        ChunkPlan(int x, int z, List<Target> targets, int globalOffset) {
            this(x, z, List.copyOf(targets), globalOffset, planIdentity(x, z, targets));
        }
        ChunkPlan { targets = List.copyOf(targets); }
        long key() { return chunkKey(x, z); }
    }

    private record TargetRef(ChunkPlan plan, int local) {
        Target target() { return plan.targets().get(local); }
    }
    private static final class Budget { int reads, writes, chunks; }

    /** Immutable persistent marker. Corrupt values retain their raw dynamic payload. */
    static final class Marker {
        private final UUID job;
        private final String plan;
        private final int count;
        private final BitSet done;
        private final BitSet conflicts;
        private final int doneCount;
        private final int conflictCount;
        private final String checksum;
        private final Dynamic<?> raw;
        private final String problem;

        private Marker(UUID job, String plan, int count, BitSet done, BitSet conflicts, String checksum,
                Dynamic<?> raw, String problem) {
            this.job = job;
            this.plan = plan;
            this.count = count;
            this.done = done == null ? new BitSet() : (BitSet) done.clone();
            this.conflicts = conflicts == null ? new BitSet() : (BitSet) conflicts.clone();
            doneCount = this.done.cardinality();
            conflictCount = this.conflicts.cardinality();
            this.checksum = checksum;
            this.raw = raw;
            this.problem = problem;
        }

        static Marker empty(UUID job, String plan, int count) { return valid(job, plan, count, new BitSet(), new BitSet()); }

        static Marker valid(UUID job, String plan, int count, BitSet done, BitSet conflicts) {
            Objects.requireNonNull(job, "marker job");
            requireHash(plan, "marker plan");
            if (count < 1 || count > EcologyEngine.MAX_CELLS + 1) throw new IllegalArgumentException("Invalid eradication marker count");
            BitSet safeDone = (BitSet) Objects.requireNonNull(done, "marker done bits").clone();
            BitSet safeConflicts = (BitSet) Objects.requireNonNull(conflicts, "marker conflict bits").clone();
            if (safeDone.length() > count || safeConflicts.length() > count) throw new IllegalArgumentException("Eradication marker bit outside plan");
            BitSet outside = (BitSet) safeConflicts.clone(); outside.andNot(safeDone);
            if (!outside.isEmpty()) throw new IllegalArgumentException("Eradication conflicts must also be completed");
            String checksum = markerChecksum(job, plan, count, safeDone, safeConflicts);
            return new Marker(job, plan, count, safeDone, safeConflicts, checksum, null, null);
        }

        static Marker corrupt(Dynamic<?> raw, String problem) {
            return new Marker(null, null, 0, null, null, null, Objects.requireNonNull(raw), problem == null ? "invalid marker" : problem);
        }

        UUID job() { return job; }
        String plan() { return plan; }
        int count() { return count; }
        String checksum() { return checksum; }
        boolean corrupt() { return raw != null; }
        String problem() { return problem; }
        boolean done(int index) { check(index); return done.get(index); }
        boolean conflict(int index) { check(index); return conflicts.get(index); }
        boolean complete() { return !corrupt() && doneCount == count; }
        int doneCount() { return doneCount; }
        int conflictCount() { return conflictCount; }
        int nextPending(int from) {
            if (corrupt() || from < 0 || from > count) throw new IllegalArgumentException("Invalid eradication marker cursor");
            return Math.min(count, done.nextClearBit(from));
        }
        boolean covers(Marker previous) {
            if (corrupt() || previous.corrupt() || !Objects.equals(job, previous.job) || !Objects.equals(plan, previous.plan)
                    || count != previous.count) return false;
            BitSet missingDone = previous.doneBits(); missingDone.andNot(done);
            BitSet missingConflicts = previous.conflictBits(); missingConflicts.andNot(conflicts);
            return missingDone.isEmpty() && missingConflicts.isEmpty();
        }
        BitSet doneBits() { return (BitSet) done.clone(); }
        BitSet conflictBits() { return (BitSet) conflicts.clone(); }

        Marker complete(int index, boolean conflict) {
            check(index);
            if (done.get(index) && (!conflict || conflicts.get(index))) return this;
            BitSet nextDone = (BitSet) done.clone(); nextDone.set(index);
            BitSet nextConflicts = (BitSet) conflicts.clone(); if (conflict) nextConflicts.set(index);
            return valid(job, plan, count, nextDone, nextConflicts);
        }

        Dynamic<?> dynamic() {
            if (corrupt()) return raw;
            JsonObject object = new JsonObject();
            object.addProperty("schema", MARKER_SCHEMA);
            object.addProperty("job", job.toString());
            object.addProperty("plan", plan);
            object.addProperty("count", count);
            object.addProperty("done", bits(done));
            object.addProperty("conflicts", bits(conflicts));
            object.addProperty("checksum", checksum);
            return new Dynamic<>(JsonOps.INSTANCE, object);
        }

        private void check(int index) {
            if (corrupt() || index < 0 || index >= count) throw new IllegalArgumentException("Eradication marker index outside plan");
        }
    }

    private static Marker decodeMarker(Dynamic<?> raw) {
        try {
            JsonElement converted = raw.convert(JsonOps.INSTANCE).getValue();
            if (!converted.isJsonObject()) throw new IllegalArgumentException("marker must be an object");
            JsonObject object = converted.getAsJsonObject();
            if (!object.keySet().equals(MARKER_FIELDS)) throw new IllegalArgumentException("marker fields do not match schema");
            if (integer(object, "schema") != MARKER_SCHEMA) throw new IllegalArgumentException("unsupported marker schema");
            UUID job = canonicalUuid(string(object, "job"));
            String plan = string(object, "plan"); requireHash(plan, "marker plan");
            int count = integer(object, "count");
            BitSet done = parseBits(string(object, "done"), count);
            BitSet conflicts = parseBits(string(object, "conflicts"), count);
            String checksum = string(object, "checksum"); requireHash(checksum, "marker checksum");
            Marker marker = Marker.valid(job, plan, count, done, conflicts);
            if (!MessageDigest.isEqual(checksum.getBytes(StandardCharsets.US_ASCII), marker.checksum().getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("marker checksum mismatch");
            }
            return marker;
        } catch (RuntimeException exception) {
            return Marker.corrupt(raw, message(exception));
        }
    }

    private static String planIdentity(int x, int z, List<Target> targets) {
        StringBuilder value = new StringBuilder().append("plan1|").append(x).append('|').append(z).append('|').append(targets.size());
        for (Target target : targets) {
            value.append('|').append(target.root()).append('|').append(target.position().x()).append(',')
                    .append(target.position().y()).append(',').append(target.position().z()).append('|');
            state(value, target.expected()); value.append('|'); state(value, target.restore());
        }
        return hash(value.toString());
    }

    private static String markerChecksum(UUID job, String plan, int count, BitSet done, BitSet conflicts) {
        return hash("marker1|" + job + '|' + plan + '|' + count + '|' + bits(done) + '|' + bits(conflicts));
    }

    private static void state(StringBuilder value, EcologyTerrain.State state) {
        append(value, state.id());
        for (Map.Entry<String, String> entry : new TreeMap<>(state.properties()).entrySet()) {
            append(value, entry.getKey()); append(value, entry.getValue());
        }
    }
    private static void append(StringBuilder value, String part) { value.append(part.length()).append(':').append(part); }
    private static String bits(BitSet bits) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bits.toByteArray()); }
    private static BitSet parseBits(String encoded, int count) {
        if (count < 1 || count > EcologyEngine.MAX_CELLS + 1) throw new IllegalArgumentException("Invalid eradication marker count");
        int maximumBytes = (count + 7) / 8;
        int maximumCharacters = (maximumBytes * 4 + 2) / 3;
        if (encoded.length() > maximumCharacters) throw new IllegalArgumentException("Eradication marker bits exceed plan");
        byte[] bytes;
        try { bytes = Base64.getUrlDecoder().decode(encoded); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid eradication marker bits", exception); }
        if (bytes.length > maximumBytes) throw new IllegalArgumentException("Eradication marker bits exceed plan");
        BitSet result = BitSet.valueOf(bytes);
        if (result.length() > count) throw new IllegalArgumentException("Eradication marker bit outside plan");
        if (!bits(result).equals(encoded)) throw new IllegalArgumentException("Eradication marker bits are not canonical");
        return result;
    }
    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
    private static void requireHash(String value, String field) { if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(field + " must be SHA-256"); }
    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(field + " must be a string");
        return value.getAsString();
    }
    private static int integer(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("0|[1-9][0-9]*")) throw new IllegalArgumentException(field + " must be a canonical integer");
        try { return Integer.parseInt(value.getAsString()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(field + " is outside integer range", exception); }
    }
    private static UUID canonicalUuid(String value) {
        try { UUID uuid = UUID.fromString(value); if (!uuid.toString().equals(value)) throw new IllegalArgumentException(); return uuid; }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("marker job must be a canonical UUID", exception); }
    }
    private static long chunk(EcologyTerrain.Pos position) { return chunkKey(position.x() >> 4, position.z() >> 4); }
    private static long chunkKey(int x, int z) { return Integer.toUnsignedLong(x) | (Integer.toUnsignedLong(z) << 32); }
    private static int chunkX(long key) { return (int) key; }
    private static int chunkZ(long key) { return (int) (key >>> 32); }
}
