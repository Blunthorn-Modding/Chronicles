package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.managed.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.util.*;

/** Server-authoritative managed block runtime and the stable Core UI facade. */
public final class ManagedBlocks {
    private static final int VERIFY_PER_TICK = 32;
    private static final int SCAN_CELLS_PER_TICK = 8_192;
    private static final Map<MinecraftServer, Runtime> WORLDS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Set<PendingChunk>> EARLY_CHUNKS = new IdentityHashMap<>();
    private static final Map<String, PlacementPolicy> POLICIES = new TreeMap<>();
    private static boolean registered;
    private static boolean policiesFrozen;

    private ManagedBlocks() { }

    public static synchronized void registerPolicy(String typeId, PlacementPolicy policy) {
        if (policiesFrozen) throw new IllegalStateException("Managed block policies are frozen.");
        Objects.requireNonNull(policy, "managed placement policy is required");
        if (POLICIES.putIfAbsent(typeId, policy) != null) throw new IllegalArgumentException("Duplicate managed block policy: " + typeId);
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ServerLifecycleEvents.SERVER_STARTING.register(server -> policiesFrozen = true);
        ServerLifecycleEvents.SERVER_STARTED.register(ManagedBlocks::start);
        ServerTickEvents.END_SERVER_TICK.register(ManagedBlocks::tick);
        ServerChunkEvents.CHUNK_LOAD.register(ManagedBlocks::chunkLoaded);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { WORLDS.remove(server); EARLY_CHUNKS.remove(server); });
    }

    public static ManagedBlockSnapshot snapshot(MinecraftServer server) {
        requireThread(server);
        Runtime runtime = WORLDS.get(server);
        if (runtime == null) return unavailableSnapshot("Managed blocks are unavailable.");
        return runtime.snapshot();
    }

    public static ManagedBlockRevision revision(MinecraftServer server) {
        requireThread(server);
        Runtime runtime = WORLDS.get(server);
        return runtime == null ? new ManagedBlockRevision(0, 0, 0, false) : runtime.revision();
    }

    public static boolean isManagedBlock(String blockId) {
        return blockId != null && ChronicleCore.managedBlocks().byBlock(blockId).isPresent();
    }

    public static void preview(ServerPlayerEntity player, UUID instanceId) {
        MinecraftServer server = player.getEntityWorld().getServer(); requireThread(server);
        Runtime runtime = required(server);
        ManagedBlockInstance instance = runtime.service.get(instanceId).orElseThrow(() -> new IllegalArgumentException("Unknown managed block instance."));
        AuthoringRuntime.get(server).preview(player, new AuthoringSite.Anchor("managed_preview", instance.dimension(), 1, instance.position(), instance.blockId()));
    }

    public static ManagedBlockSnapshot setLimit(ServerCommandSource source, String typeId, int value, long expectedPolicyRevision) {
        MinecraftServer server = source.getServer(); requireThread(server);
        if (!source.hasPermissionLevel(2)) throw new IllegalStateException("Managed block limits require operator permission level 2.");
        if (!AuthoringRuntime.editable(server)) throw new IllegalStateException("Managed block limits require a non-active Chronicle session.");
        ServerPlayerEntity player = source.getPlayer();
        if (player == null || !AuthoringRuntime.isAuthor(player)) throw new IllegalStateException("Enable author mode before changing managed block limits.");
        Runtime runtime = required(server);
        try { runtime.service.setLimit(typeId, value, expectedPolicyRevision); }
        catch (IOException exception) { runtime.fail("Unable to save managed block limits; inspect managed-block-limits.json while stopped.", exception); throw new IllegalStateException(runtime.problem, exception); }
        return runtime.snapshot();
    }

    public static void observePlaced(ServerWorld world, BlockPos position, String typeId) {
        Runtime runtime = WORLDS.get(world.getServer()); if (runtime == null || runtime.service == null) return;
        runtime.observe(world, position, typeId, ManagedBlockInstance.Origin.DISCOVERED);
    }

    public static void verifyAfterReplacement(ServerWorld world, BlockPos position, String typeId) {
        Runtime runtime = WORLDS.get(world.getServer()); if (runtime == null || runtime.service == null) return;
        String dimension = dimension(world); BlockPosition pure = pure(position);
        if (runtime.areaReplacementLocked()) {
            runtime.queueScan(world, new ChunkPos(position));
            return;
        }
        runtime.service.at(dimension, pure).filter(instance -> instance.typeId().equals(typeId)).ifPresent(instance -> runtime.verifications.addLast(instance.id()));
    }

    public static ManagedAreaReplacementPlan planAreaReplacement(MinecraftServer server, UUID operationId,
            String dimension, Set<BlockPosition> affected, List<ManagedAreaReplacementPlan.Placement> after) {
        requireThread(server);
        return required(server).service.planAreaReplacement(operationId, dimension, affected, after);
    }

    /** Applies scenario policies and locks the exact persisted plan before any world mutation. */
    public static void prepareAreaReplacement(MinecraftServer server, ManagedAreaReplacementPlan plan) {
        requireThread(server); Runtime runtime = required(server);
        AreaReplacementRequest request = new AreaReplacementRequest(server, plan);
        for (PlacementPolicy policy : POLICIES.values()) {
            String denied = policy.denyAreaReplacement(request);
            if (denied != null) throw new IllegalStateException(denied);
        }
        runtime.service.lockAreaReplacement(plan); runtime.bumpStatus();
    }

    /** Restores a previously approved durable lock before gameplay/ticks resume. */
    public static void reinstateAreaReplacement(MinecraftServer server, ManagedAreaReplacementPlan plan) {
        requireThread(server); Runtime runtime = required(server);
        runtime.service.lockAreaReplacement(plan); runtime.bumpStatus();
    }

    /** Caller must invoke this only after the exact planned world cells are durably saved. */
    public static void commitAreaReplacement(MinecraftServer server, ManagedAreaReplacementPlan plan) throws IOException {
        requireThread(server); Runtime runtime = required(server);
        runtime.service.commitAreaReplacement(plan); runtime.bumpStatus();
    }

    /** Unlocks a proved outcome and schedules buffered world reconciliation without inventing identities mid-operation. */
    public static void releaseAreaReplacement(MinecraftServer server, ManagedAreaReplacementPlan plan,
                                              ManagedAreaReplacementPlan.Outcome outcome) {
        requireThread(server); Runtime runtime = required(server);
        runtime.service.unlockAreaReplacement(plan, outcome);
        runtime.resumeAfterReplacement(server, plan); runtime.bumpStatus();
    }

    public static ReservationTicket reservePlacement(ServerWorld world, BlockPos position, String typeId) {
        MinecraftServer server = world.getServer(); requireThread(server);
        Runtime runtime = required(server);
        if (!runtime.ready()) throw new IllegalStateException(runtime.problem());
        PlacementRequest request = new PlacementRequest(server, typeId, dimension(world), pure(position));
        PlacementPolicy policy = POLICIES.get(typeId);
        if (policy != null) {
            String denied = policy.deny(request);
            if (denied != null) throw new IllegalStateException(denied);
        }
        try {
            ManagedBlockService.Reservation reservation = runtime.service.reserve(typeId, request.dimension(), request.position());
            try { if (policy != null) policy.afterReservation(request); }
            catch (Exception exception) {
                try { runtime.service.release(reservation.id()); } catch (IOException release) { exception.addSuppressed(release); runtime.verifications.addLast(reservation.id()); }
                throw new IllegalStateException("Managed block preparation failed: " + message(exception), exception);
            }
            runtime.observeStatus(reservation.id(), ManagedBlockStatus.MISSING, "minecraft:air");
            return new ReservationTicket(server, reservation.id());
        } catch (IOException exception) {
            runtime.fail("Unable to reserve managed block placement; inspect managed-blocks.json while stopped.", exception);
            throw new IllegalStateException(runtime.problem, exception);
        }
    }

    public static void placementSucceeded(ReservationTicket ticket) {
        Runtime runtime = WORLDS.get(ticket.server()); if (runtime == null || runtime.service == null) return;
        if (runtime.areaReplacementLocked()) { runtime.verifications.addLast(ticket.id()); return; }
        try {
            ManagedBlockInstance instance = runtime.service.confirm(ticket.id());
            runtime.observeStatus(instance.id(), ManagedBlockStatus.PRESENT, instance.blockId());
        } catch (Exception exception) {
            ChronicleCore.LOGGER.error("Managed block was placed but confirmation failed; its durable reservation remains quota-bearing.", exception);
            runtime.verifications.addLast(ticket.id());
        }
    }

    public static void placementFailed(ReservationTicket ticket) {
        Runtime runtime = WORLDS.get(ticket.server()); if (runtime == null || runtime.service == null) return;
        if (runtime.areaReplacementLocked()) { runtime.verifications.addLast(ticket.id()); return; }
        try {
            if (runtime.service.release(ticket.id())) {
                runtime.observations.remove(ticket.id());
                runtime.bumpStatus();
            }
        } catch (IOException exception) {
            runtime.fail("Unable to release a failed managed block placement; inspect managed-blocks.json while stopped.", exception);
        }
        runtime.verifications.addLast(ticket.id());
    }

    /** A throwing world write is ambiguous, so retain its durable claim until loaded verification. */
    public static void placementUncertain(ReservationTicket ticket) {
        Runtime runtime = WORLDS.get(ticket.server()); if (runtime == null || runtime.service == null) return;
        runtime.verifications.addLast(ticket.id());
    }

    static void checkAuthoringMutation(MinecraftServer server, AuthoringSite existing, AuthoringSite proposed) {
        String id = existing != null ? existing.id() : proposed == null ? null : proposed.id();
        if (id == null) return;
        for (PlacementPolicy policy : POLICIES.values()) {
            String reason = policy.protectsAuthoringSite(server, id);
            if (reason != null) throw new IllegalStateException(reason);
        }
    }

    static void checkAssignmentMutation(MinecraftServer server, String requirementId, SiteAssignments.Binding existing, SiteAssignments.Binding proposed) {
        if (Objects.equals(existing, proposed)) return;
        for (PlacementPolicy policy : POLICIES.values()) {
            String reason = policy.protectsAssignment(server, requirementId);
            if (reason != null) throw new IllegalStateException(reason);
        }
    }

    private static void start(MinecraftServer server) {
        var ledgerPath = server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/managed-blocks.json");
        var limitsPath = FabricLoader.getInstance().getConfigDir().resolve("chronicle/managed-block-limits.json");
        try {
            validateRegistry();
            ManagedBlockStore store = new ManagedBlockStore(ledgerPath);
            ManagedBlockService service = new ManagedBlockService(ChronicleCore.managedBlocks(), store, new ManagedBlockLimitStore(limitsPath));
            Runtime runtime = new Runtime(service); WORLDS.put(server, runtime);
            if (service.migrationRequired()) {
                runtime.migrateAnchors(server);
                service.completeMigration();
            }
            runtime.queueKnownLoaded(server, true);
            Set<PendingChunk> early = EARLY_CHUNKS.remove(server);
            if (early != null) for (PendingChunk pending : early) {
                ServerWorld world = world(server, pending.dimension());
                if (world != null && world.getChunkManager().getWorldChunk(pending.x(), pending.z()) != null)
                    runtime.queueScan(world, new ChunkPos(pending.x(), pending.z()));
            }
            ChronicleCore.LOGGER.info("Chronicle managed blocks ready: {} with policy {}", ledgerPath, limitsPath);
        } catch (Exception exception) {
            Runtime runtime = new Runtime(null); runtime.fail("Managed blocks unavailable; restore managed-blocks.json and managed-block-limits.json while stopped.", exception);
            WORLDS.put(server, runtime);
        }
    }

    private static void tick(MinecraftServer server) {
        Runtime runtime = WORLDS.get(server); if (runtime == null || runtime.service == null) return;
        runtime.tick(server);
    }

    private static void chunkLoaded(ServerWorld world, WorldChunk chunk) {
        boolean candidate = Arrays.stream(chunk.getSectionArray()).anyMatch(section -> section.hasAny(state -> isManagedBlock(blockId(state))));
        Runtime runtime = WORLDS.get(world.getServer());
        if (runtime == null) {
            if (candidate) EARLY_CHUNKS.computeIfAbsent(world.getServer(), ignored -> new HashSet<>())
                    .add(new PendingChunk(dimension(world), chunk.getPos().x, chunk.getPos().z));
            return;
        }
        if (runtime.service == null) return;
        boolean known = runtime.service.ledger().instances().stream().anyMatch(instance -> instance.dimension().equals(dimension(world))
                && (instance.position().x() >> 4) == chunk.getPos().x && (instance.position().z() >> 4) == chunk.getPos().z);
        if (known) runtime.service.ledger().instances().stream().filter(instance -> instance.dimension().equals(dimension(world))
                && (instance.position().x() >> 4) == chunk.getPos().x && (instance.position().z() >> 4) == chunk.getPos().z)
                .forEach(instance -> runtime.verifications.addLast(instance.id()));
        boolean conflict = runtime.conflicts.stream().anyMatch(value -> value.dimension().equals(dimension(world))
                && (value.position().x() >> 4) == chunk.getPos().x && (value.position().z() >> 4) == chunk.getPos().z);
        if (candidate || conflict) runtime.queueScan(world, chunk.getPos());
    }

    private static void validateRegistry() {
        for (ManagedBlockDefinition definition : ChronicleCore.managedBlocks().definitions()) {
            Identifier id = Identifier.of(definition.blockId());
            if (!Registries.BLOCK.containsId(id)) throw new IllegalStateException("Managed block is not registered: " + definition.blockId());
        }
    }

    private static Runtime required(MinecraftServer server) {
        Runtime runtime = WORLDS.get(server);
        if (runtime == null || runtime.service == null) throw new IllegalStateException(runtime == null ? "Managed blocks are unavailable." : runtime.problem);
        return runtime;
    }
    private static void requireThread(MinecraftServer server) { if (!server.isOnThread()) throw new IllegalStateException("Managed blocks require the server thread."); }
    private static String dimension(ServerWorld world) { return world.getRegistryKey().getValue().toString(); }
    private static BlockPosition pure(BlockPos position) { return new BlockPosition(position.getX(), position.getY(), position.getZ()); }
    private static String blockId(BlockState state) { return Registries.BLOCK.getId(state.getBlock()).toString(); }
    private static String message(Exception exception) { return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(); }

    private static ManagedBlockSnapshot unavailableSnapshot(String problem) {
        return unavailableSnapshot(problem, 0);
    }

    private static ManagedBlockSnapshot unavailableSnapshot(String problem, long statusRevision) {
        List<ManagedBlockTypeView> types = ChronicleCore.managedBlocks().definitions().stream().map(definition -> new ManagedBlockTypeView(
                definition.typeId(), definition.blockId(), definition.label(), definition.description(), 0, definition.defaultLimit(),
                definition.limitMode() == ManagedBlockDefinition.LimitMode.FIXED, false)).toList();
        return new ManagedBlockSnapshot(0, 0, statusRevision, false, problem, types, List.of());
    }

    public record ReservationTicket(MinecraftServer server, UUID id) { }
    public record PlacementRequest(MinecraftServer server, String typeId, String dimension, BlockPosition position) { }
    public record AreaReplacementRequest(MinecraftServer server, ManagedAreaReplacementPlan plan) {
        public AreaReplacementRequest {
            Objects.requireNonNull(server, "server is required");
            Objects.requireNonNull(plan, "managed area replacement plan is required");
        }
    }
    public interface PlacementPolicy {
        default String deny(PlacementRequest request) { return null; }
        default String denyAreaReplacement(AreaReplacementRequest request) { return null; }
        default void afterReservation(PlacementRequest request) throws Exception { }
        default String protectsAuthoringSite(MinecraftServer server, String siteId) { return null; }
        default String protectsAssignment(MinecraftServer server, String requirementId) { return null; }
    }

    private static final class Runtime {
        private final ManagedBlockService service;
        private final Map<UUID, Observed> observations = new HashMap<>();
        private final Deque<UUID> verifications = new ArrayDeque<>();
        private final Deque<Scan> scans = new ArrayDeque<>();
        private final Set<String> scanKeys = new HashSet<>();
        private final Set<PendingChunk> deferredScans = new HashSet<>();
        private final Set<Conflict> conflicts = new HashSet<>();
        private long statusRevision;
        private int verifyCursor;
        private boolean checking;
        private String problem = "";

        private Runtime(ManagedBlockService service) { this.service = service; }
        private boolean ready() { return service != null && problem.isEmpty() && !checking && !areaReplacementLocked(); }
        private String problem() { return problem.isEmpty() ? areaReplacementLocked()
                ? "Managed blocks are locked while an area change is being saved."
                : checking ? "Checking loaded managed blocks; placement will be available shortly." : "" : problem; }
        private boolean areaReplacementLocked() { return service != null && service.lockedAreaReplacement().isPresent(); }
        private ManagedBlockRevision revision() {
            return new ManagedBlockRevision(service == null ? 0 : service.ledger().revision(), service == null ? 0 : service.limits().revision(), statusRevision, ready());
        }
        private void fail(String problem, Exception exception) {
            if (!Objects.equals(this.problem, problem)) { this.problem = problem; bumpStatus(); }
            ChronicleCore.LOGGER.error(problem, exception);
        }
        private void setChecking(boolean value) { if (checking != value) { checking = value; bumpStatus(); } }
        private void bumpStatus() { statusRevision = statusRevision == Long.MAX_VALUE ? Long.MAX_VALUE : statusRevision + 1; }

        private void migrateAnchors(MinecraftServer server) throws IOException {
            for (AuthoringSite site : AuthoringRuntime.get(server).service().state().sites().values()) {
                if (!(site instanceof AuthoringSite.Anchor anchor)) continue;
                Optional<ManagedBlockDefinition> definition = ChronicleCore.managedBlocks().byBlock(anchor.expectedBlock());
                if (definition.isEmpty()) continue;
                Optional<ManagedBlockInstance> claimed = service.at(anchor.dimension(), anchor.position());
                if (claimed.isPresent() && (!claimed.get().typeId().equals(definition.get().typeId())
                        || !claimed.get().blockId().equals(definition.get().blockId()))) {
                    conflicts.add(new Conflict(anchor.dimension(), anchor.position()));
                    setChecking(true);
                    continue;
                }
                service.adopt(definition.get().typeId(), anchor.dimension(), anchor.position(), ManagedBlockInstance.Origin.LEGACY);
            }
        }

        private void queueKnownLoaded(MinecraftServer server, boolean startup) {
            for (ManagedBlockInstance instance : List.copyOf(service.ledger().instances())) {
                ServerWorld world = world(server, instance.dimension());
                if (world != null && world.getChunkManager().getWorldChunk(instance.position().x() >> 4, instance.position().z() >> 4) != null)
                    verifications.addLast(instance.id());
                else observeStatus(instance.id(), ManagedBlockStatus.UNLOADED, "");
            }
            for (Conflict conflict : conflicts) {
                ServerWorld world = world(server, conflict.dimension());
                if (world != null && world.getChunkManager().getWorldChunk(conflict.position().x() >> 4, conflict.position().z() >> 4) != null)
                    queueScan(world, new ChunkPos(conflict.position().x() >> 4, conflict.position().z() >> 4));
            }
            if (startup && !verifications.isEmpty()) setChecking(true);
        }

        private void tick(MinecraftServer server) {
            if (areaReplacementLocked()) return;
            int verified = 0;
            while (verified++ < VERIFY_PER_TICK && !verifications.isEmpty()) verify(server, verifications.removeFirst());
            scan(server);
            periodicVerify(server);
            if (checking && verifications.isEmpty() && scans.isEmpty() && conflicts.isEmpty() && problem.isEmpty()) setChecking(false);
        }

        private void periodicVerify(MinecraftServer server) {
            List<ManagedBlockInstance> entries = service.ledger().instances();
            if (entries.isEmpty()) { verifyCursor = 0; return; }
            int count = Math.min(4, entries.size());
            for (int i = 0; i < count && !service.ledger().instances().isEmpty(); i++) {
                List<ManagedBlockInstance> current = service.ledger().instances();
                if (verifyCursor >= current.size()) verifyCursor = 0;
                UUID id = current.get(verifyCursor++).id(); verify(server, id);
            }
        }

        private void verify(MinecraftServer server, UUID id) {
            ManagedBlockInstance instance = service.get(id).orElse(null); if (instance == null) return;
            if (ChronicleCore.managedBlocks().get(instance.typeId()).isEmpty()) {
                observeStatus(id, ManagedBlockStatus.UNAVAILABLE, "");
                return;
            }
            ServerWorld world = world(server, instance.dimension());
            if (world == null) { observeStatus(id, ManagedBlockStatus.UNAVAILABLE, ""); return; }
            WorldChunk chunk = world.getChunkManager().getWorldChunk(instance.position().x() >> 4, instance.position().z() >> 4);
            if (chunk == null) { observeStatus(id, ManagedBlockStatus.UNLOADED, ""); return; }
            BlockPos position = new BlockPos(instance.position().x(), instance.position().y(), instance.position().z());
            BlockState actualState = chunk.getBlockState(position); String actual = blockId(actualState);
            if (actual.equals(instance.blockId())) {
                try { service.confirm(id); observeStatus(id, ManagedBlockStatus.PRESENT, actual); }
                catch (IOException exception) { fail("Unable to confirm a managed block; inspect managed-blocks.json while stopped.", exception); }
                return;
            }
            observeStatus(id, actualState.isAir() ? ManagedBlockStatus.MISSING : ManagedBlockStatus.CHANGED, actual);
            try {
                service.release(id); observations.remove(id); bumpStatus();
                ChronicleCore.managedBlocks().byBlock(actual).ifPresent(definition -> observe(world, position, definition.typeId(), ManagedBlockInstance.Origin.DISCOVERED));
            } catch (IOException exception) { fail("Unable to release a removed managed block; inspect managed-blocks.json while stopped.", exception); }
        }

        private void observe(ServerWorld world, BlockPos position, String typeId, ManagedBlockInstance.Origin origin) {
            if (areaReplacementLocked()) {
                queueScan(world, new ChunkPos(position));
                return;
            }
            try {
                Optional<ManagedBlockInstance> claimed = service.at(dimension(world), pure(position));
                if (claimed.isPresent() && !claimed.get().typeId().equals(typeId)) {
                    verifications.addLast(claimed.get().id());
                    queueScan(world, new ChunkPos(position));
                    return;
                }
                ManagedBlockInstance instance = service.adopt(typeId, dimension(world), pure(position), origin);
                observeStatus(instance.id(), ManagedBlockStatus.PRESENT, instance.blockId());
            } catch (Exception exception) { fail("A managed block exists but could not be recorded; inspect managed-blocks.json while stopped.", exception); }
        }

        private void observeStatus(UUID id, ManagedBlockStatus status, String actual) {
            Observed next = new Observed(status, actual == null ? "" : actual);
            if (!next.equals(observations.put(id, next))) bumpStatus();
        }

        private void queueScan(ServerWorld world, ChunkPos position) {
            if (areaReplacementLocked()) {
                deferredScans.add(new PendingChunk(dimension(world), position.x, position.z));
                return;
            }
            String key = dimension(world) + ":" + position.x + ":" + position.z;
            if (!scanKeys.add(key)) return;
            scans.addLast(new Scan(dimension(world), position.x, position.z)); setChecking(true);
        }

        private void resumeAfterReplacement(MinecraftServer server, ManagedAreaReplacementPlan plan) {
            Set<PendingChunk> restart = new HashSet<>(deferredScans);
            for (Scan scan : scans) restart.add(new PendingChunk(scan.dimension, scan.chunkX, scan.chunkZ));
            deferredScans.clear(); scans.clear(); scanKeys.clear();
            for (PendingChunk pending : restart) {
                ServerWorld world = world(server, pending.dimension());
                if (world != null && world.getChunkManager().getWorldChunk(pending.x(), pending.z()) != null)
                    queueScan(world, new ChunkPos(pending.x(), pending.z()));
            }
            queueAffected(server, plan);
        }

        private void queueAffected(MinecraftServer server, ManagedAreaReplacementPlan plan) {
            ServerWorld world = world(server, plan.dimension());
            if (world == null) return;
            Set<Long> chunks = new HashSet<>();
            for (BlockPosition position : plan.affected()) {
                int x = position.x() >> 4, z = position.z() >> 4;
                long key = ((long) x << 32) ^ (z & 0xffffffffL);
                if (chunks.add(key) && world.getChunkManager().getWorldChunk(x, z) != null)
                    queueScan(world, new ChunkPos(x, z));
            }
            for (ManagedBlockInstance instance : service.ledger().instances())
                if (instance.dimension().equals(plan.dimension()) && plan.affects(instance.dimension(), instance.position()))
                    verifications.addLast(instance.id());
        }

        private void scan(MinecraftServer server) {
            int budget = SCAN_CELLS_PER_TICK;
            while (budget > 0 && !scans.isEmpty()) {
                Scan scan = scans.peekFirst(); ServerWorld world = world(server, scan.dimension);
                WorldChunk chunk = world == null ? null : world.getChunkManager().getWorldChunk(scan.chunkX, scan.chunkZ);
                if (chunk == null) { finishScan(); continue; }
                var sections = chunk.getSectionArray();
                if (scan.section >= sections.length) { finishScan(); continue; }
                var section = sections[scan.section];
                if (scan.cell == 0 && !section.hasAny(state -> isManagedBlock(blockId(state)))) { scan.section++; continue; }
                while (budget-- > 0 && scan.cell < 4096) {
                    int cell = scan.cell++, x = cell & 15, z = (cell >> 4) & 15, y = (cell >> 8) & 15;
                    BlockState state = section.getBlockState(x, y, z); String block = blockId(state);
                    Optional<ManagedBlockDefinition> definition = ChronicleCore.managedBlocks().byBlock(block);
                    if (definition.isPresent()) observe(world, new BlockPos((scan.chunkX << 4) + x, world.getBottomY() + (scan.section << 4) + y, (scan.chunkZ << 4) + z), definition.get().typeId(), ManagedBlockInstance.Origin.DISCOVERED);
                }
                if (scan.cell >= 4096) { scan.section++; scan.cell = 0; }
            }
        }

        private void finishScan() {
            Scan done = scans.removeFirst(); scanKeys.remove(done.dimension + ":" + done.chunkX + ":" + done.chunkZ);
            if (conflicts.removeIf(conflict -> conflict.dimension().equals(done.dimension)
                    && (conflict.position().x() >> 4) == done.chunkX && (conflict.position().z() >> 4) == done.chunkZ)) bumpStatus();
        }

        private ManagedBlockSnapshot snapshot() {
            if (service == null) return unavailableSnapshot(problem(), statusRevision);
            Map<String, Integer> counts = new HashMap<>();
            for (ManagedBlockInstance instance : service.ledger().instances()) counts.merge(instance.typeId(), 1, Integer::sum);
            List<ManagedBlockTypeView> types = new ArrayList<>(); Set<String> known = new HashSet<>();
            for (ManagedBlockDefinition definition : ChronicleCore.managedBlocks().definitions()) {
                known.add(definition.typeId()); int count = counts.getOrDefault(definition.typeId(), 0), limit = service.effectiveLimit(definition);
                types.add(new ManagedBlockTypeView(definition.typeId(), definition.blockId(), definition.label(), definition.description(), count, limit,
                        definition.limitMode() == ManagedBlockDefinition.LimitMode.FIXED, count > limit));
            }
            for (var entry : new TreeMap<>(counts).entrySet()) if (!known.contains(entry.getKey()))
                types.add(new ManagedBlockTypeView(entry.getKey(), opaqueBlock(entry.getKey()), entry.getKey(), "Unavailable managed block type", entry.getValue(), entry.getValue(), true, false));
            List<ManagedBlockInstanceView> instances = service.ledger().instances().stream().sorted(Comparator.comparing(ManagedBlockInstance::dimension)
                    .thenComparingInt(i -> i.position().x()).thenComparingInt(i -> i.position().y()).thenComparingInt(i -> i.position().z())).map(instance -> {
                ManagedBlockDefinition definition = ChronicleCore.managedBlocks().get(instance.typeId()).orElse(null);
                Observed observed = observations.get(instance.id());
                ManagedBlockStatus status = definition == null ? ManagedBlockStatus.UNAVAILABLE : observed == null ? ManagedBlockStatus.UNLOADED : observed.status;
                String actual = observed == null ? "" : observed.actual;
                return new ManagedBlockInstanceView(instance.id(), instance.typeId(), definition == null ? instance.typeId() : definition.label(), instance.dimension(),
                        instance.position(), instance.blockId(), actual, status, instance.revision());
            }).toList();
            return new ManagedBlockSnapshot(service.ledger().revision(), service.limits().revision(), statusRevision, ready(), problem(), types, instances);
        }

        private String opaqueBlock(String typeId) { return service.ledger().instances().stream().filter(instance -> instance.typeId().equals(typeId)).map(ManagedBlockInstance::blockId).findFirst().orElse(typeId); }
    }

    private static ServerWorld world(MinecraftServer server, String dimension) {
        try { return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dimension))); }
        catch (RuntimeException exception) { return null; }
    }
    private record Observed(ManagedBlockStatus status, String actual) { }
    private record Conflict(String dimension, BlockPosition position) { }
    private record PendingChunk(String dimension, int x, int z) { }
    private static final class Scan {
        private final String dimension; private final int chunkX, chunkZ; private int section, cell;
        private Scan(String dimension, int chunkX, int chunkZ) { this.dimension = dimension; this.chunkX = chunkX; this.chunkZ = chunkZ; }
    }
}
