package io.github.chronicle.quietbelow.stage;

import io.github.chronicle.core.campaign.Actor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

/** Server-thread stage state machine. It never discovers or loads chunks. */
public final class StageService {
    public static final int WRITES_PER_TICK = 8;
    private static final Actor SYSTEM = new Actor("server", null);

    private final StageRepository store;
    private final Clock clock;
    private StageState current;

    public StageService(StageStore store, Clock clock) throws IOException {
        this((StageRepository) store, clock);
    }

    StageService(StageRepository store, Clock clock) throws IOException {
        if (store == null || clock == null) throw new IllegalArgumentException("stage store and clock are required");
        this.store = store;
        this.clock = clock;
        this.current = store.load();
        StageOperation operation = current.operation();
        if (operation != null && (operation.status() == StageOperationStatus.APPLYING
                || operation.status() == StageOperationStatus.UNDOING)) {
            StageOperation interrupted = operation.checkpoint(StageOperationStatus.INTERRUPTED, operation.progress(),
                    "Server restarted; inspect the zone and use stage resume explicitly.");
            commit(current.zones(), interrupted, SYSTEM, "stage.interrupted", operation.zoneId(), operation.operationId(),
                    "progress=" + operation.progress());
        }
    }

    public synchronized StageState state() {
        return current;
    }

    public synchronized StageZone defineZone(Actor actor, String id, StageWorld world,
                                               StagePosition first, StagePosition second) throws IOException {
        requireActor(actor);
        StageValidation.zoneId(id);
        if (world == null || first == null || second == null) throw new IllegalArgumentException("world and both positions are required");
        rejectUnfinishedOperation();
        int minX = Math.min(first.x(), second.x()), maxX = Math.max(first.x(), second.x());
        int minY = Math.min(first.y(), second.y()), maxY = Math.max(first.y(), second.y());
        int minZ = Math.min(first.z(), second.z()), maxZ = Math.max(first.z(), second.z());
        long width = (long) maxX - minX + 1;
        long height = (long) maxY - minY + 1;
        long depth = (long) maxZ - minZ + 1;
        long volume = width > StageValidation.MAX_POSITIONS || height > StageValidation.MAX_POSITIONS
                || depth > StageValidation.MAX_POSITIONS ? StageValidation.MAX_POSITIONS + 1L : width * height * depth;
        if (volume < 1 || volume > StageValidation.MAX_POSITIONS) {
            throw new IllegalArgumentException("zone volume must contain between 1 and " + StageValidation.MAX_POSITIONS + " positions; requested " + volume);
        }
        List<StageEntry> entries = new ArrayList<>((int) volume);
        for (long y = minY; y <= maxY; y++) {
            for (long z = minZ; z <= maxZ; z++) {
                for (long x = minX; x <= maxX; x++) {
                    StagePosition position = new StagePosition((int) x, (int) y, (int) z);
                    requireAccessible(world, position);
                    StageBlockState state = world.getBlockState(position);
                    if (!StageValidation.NATURAL_BLOCKS.contains(state.id())) {
                        throw new IllegalArgumentException("unsupported natural block at " + position.display() + ": " + state.display());
                    }
                    entries.add(new StageEntry(position, state, StageValidation.SCULK));
                }
            }
        }
        Map<String, StageZone> zones = new LinkedHashMap<>(current.zones());
        if (!zones.containsKey(id) && zones.size() >= StageValidation.MAX_ZONES) {
            throw new IllegalStateException("zone limit of " + StageValidation.MAX_ZONES + " reached");
        }
        StageZone zone = new StageZone(id, world.dimension(), entries, actor, now());
        zones.put(id, zone);
        // Once an operation was fully undone its intent no longer needs to occupy the latest-operation slot.
        StageOperation retained = current.operation() != null && current.operation().status() == StageOperationStatus.UNDONE
                ? null : current.operation();
        commit(zones, retained, actor, "zone.define", id, "", "positions=" + entries.size());
        return zone;
    }

    public synchronized StagePreview preview(String zoneId, StageWorldResolver worlds) {
        StageZone zone = zone(zoneId);
        StageWorld world = resolve(worlds, zone.dimension());
        preflightExact(zone.entries(), world, StageDirection.APPLY, false);
        return new StagePreview(zone.id(), zone.dimension(), token(zone), zone.entries());
    }

    public synchronized StageOperation apply(Actor actor, String zoneId, String token,
                                              boolean sessionActive, StageWorldResolver worlds) throws IOException {
        requireActor(actor);
        requireActive(sessionActive);
        rejectUnfinishedOperation();
        StageZone zone = zone(zoneId);
        if (!token(zone).equals(token)) throw new IllegalArgumentException("confirmation token does not match the current zone capture");
        StageWorld world = resolve(worlds, zone.dimension());
        preflightExact(zone.entries(), world, StageDirection.APPLY, false);
        StageOperation operation = new StageOperation(UUID.randomUUID().toString(), zone.id(), zone.dimension(),
                zone.entries(), actor, now(), StageDirection.APPLY, StageOperationStatus.APPLYING, 0,
                "Intent persisted; waiting for server ticks.");
        commit(current.zones(), operation, actor, "stage.apply", zone.id(), operation.operationId(),
                "positions=" + operation.entries().size());
        return operation;
    }

    public synchronized StageOperation undo(Actor actor, boolean sessionActive, StageWorldResolver worlds) throws IOException {
        requireActor(actor);
        requireActive(sessionActive);
        StageOperation latest = current.operation();
        if (latest == null || latest.direction() != StageDirection.APPLY
                || (latest.status() != StageOperationStatus.APPLYING
                && latest.status() != StageOperationStatus.APPLIED
                && latest.status() != StageOperationStatus.INTERRUPTED
                && latest.status() != StageOperationStatus.CONFLICT
                && latest.status() != StageOperationStatus.RECOVERY_REQUIRED)) {
            throw new IllegalStateException("the latest apply operation cannot be undone in its current state");
        }
        StageWorld world = resolve(worlds, latest.dimension());
        preflightExact(latest.entries(), world, StageDirection.UNDO, true);
        StageOperation undo = new StageOperation(latest.operationId(), latest.zoneId(), latest.dimension(), latest.entries(),
                actor, now(), StageDirection.UNDO, StageOperationStatus.UNDOING, 0,
                "Undo intent persisted; waiting for server ticks.");
        commit(current.zones(), undo, actor, "stage.undo", latest.zoneId(), latest.operationId(),
                "positions=" + undo.entries().size() + "; previousStatus=" + latest.status());
        return undo;
    }

    public synchronized StageOperation resume(Actor actor, boolean sessionActive, StageWorldResolver worlds) throws IOException {
        requireActor(actor);
        requireActive(sessionActive);
        StageOperation operation = current.operation();
        if (operation == null || (operation.status() != StageOperationStatus.INTERRUPTED
                && operation.status() != StageOperationStatus.CONFLICT
                && operation.status() != StageOperationStatus.RECOVERY_REQUIRED)) {
            throw new IllegalStateException("the latest operation is not interrupted or halted");
        }
        StageWorld world = resolve(worlds, operation.dimension());
        preflightExact(operation.entries(), world, operation.direction(), true);
        StageOperationStatus running = operation.direction() == StageDirection.APPLY
                ? StageOperationStatus.APPLYING : StageOperationStatus.UNDOING;
        // Revisit all entries after a restart: a world save may lag a journal checkpoint, while each exact
        // before/after state remains safe and idempotent. This never accepts a third state.
        StageOperation resumed = operation.checkpoint(running, 0, "Explicitly resumed after a full preflight; progress was reconciled from the start.");
        commit(current.zones(), resumed, actor, "stage.resume", operation.zoneId(), operation.operationId(),
                "previousProgress=" + operation.progress());
        return resumed;
    }

    /** Executes at most eight ordered entries. Inactive sessions suspend without changing progress. */
    public synchronized int tick(boolean sessionActive, StageWorldResolver worlds) throws IOException {
        StageOperation operation = current.operation();
        if (!sessionActive || operation == null || (operation.status() != StageOperationStatus.APPLYING
                && operation.status() != StageOperationStatus.UNDOING)) {
            return 0;
        }
        try {
            StageWorld world = resolve(worlds, operation.dimension());
            preflightRemaining(operation, world);
            return tickPreflighted(operation, world);
        } catch (RuntimeException exception) {
            String detail = exception.getMessage() == null ? "Stage preflight failed without a diagnostic." : exception.getMessage();
            haltConflict(operation, detail);
            return 0;
        }
    }

    private int tickPreflighted(StageOperation operation, StageWorld world) throws IOException {
        int progress = operation.progress();
        int processed = 0;
        while (progress < operation.entries().size() && processed < WRITES_PER_TICK) {
            StageEntry entry = operation.entryAtProgress(progress);
            StageBlockState desired = desired(operation.direction(), entry);
            StageBlockState actual = world.getBlockState(entry.position());
            if (!actual.equals(desired)) {
                boolean changed;
                try {
                    changed = world.setBlockState(entry.position(), desired);
                } catch (RuntimeException exception) {
                    current = new StageState(StageState.SCHEMA_VERSION, current.zones(),
                            operation.checkpoint(StageOperationStatus.RECOVERY_REQUIRED, operation.progress(),
                                    "World write failed with unknown outcome at " + entry.position().display() + "; explicit recovery is required."),
                            current.history(), current.nextSequence());
                    throw new IOException("Stage world write failed with an unknown outcome; recovery is required", exception);
                }
                if (!changed) {
                    return checkpointAfterWriteFailure(operation, progress, processed,
                            "World rejected change at " + entry.position().display());
                }
            }
            progress++;
            processed++;
        }

        StageOperationStatus complete = operation.direction() == StageDirection.APPLY
                ? StageOperationStatus.APPLIED : StageOperationStatus.UNDONE;
        StageOperationStatus status = progress == operation.entries().size() ? complete : operation.status();
        String detail = status == complete ? "Completed " + progress + " positions." : "Checkpointed " + progress + " positions.";
        StageOperation checkpoint = operation.checkpoint(status, progress, detail);
        try {
            persist(current.zones(), checkpoint, current.history(), current.nextSequence());
        } catch (IOException exception) {
            current = new StageState(StageState.SCHEMA_VERSION, current.zones(),
                    operation.checkpoint(StageOperationStatus.RECOVERY_REQUIRED, operation.progress(),
                            "World changed after progress " + operation.progress() + " but checkpoint failed; explicit recovery is required."),
                    current.history(), current.nextSequence());
            throw new IOException("Stage world changed but its progress checkpoint could not be saved; recovery is required", exception);
        }
        return processed;
    }

    public synchronized String status() {
        StageOperation operation = current.operation();
        if (operation == null) return "No environmental stage has been started; zones=" + current.zones().size() + ".";
        return "Stage " + operation.operationId() + " zone=" + operation.zoneId() + " direction="
                + operation.direction().name().toLowerCase(Locale.ROOT) + " status=" + operation.status().name().toLowerCase(Locale.ROOT)
                + " progress=" + operation.progress() + "/" + operation.entries().size() + "; " + operation.detail();
    }

    public static String token(StageZone zone) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, zone.id());
            update(digest, zone.dimension());
            for (StageEntry entry : zone.entries()) {
                update(digest, entry.position().display());
                update(digest, entry.before().display());
                update(digest, entry.after().display());
            }
            return HexFormat.of().formatHex(digest.digest(), 0, 6);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private int checkpointAfterWriteFailure(StageOperation operation, int progress, int processed, String detail) throws IOException {
        StageOperation conflict = operation.checkpoint(StageOperationStatus.CONFLICT, progress, detail);
        try {
            persist(current.zones(), conflict, current.history(), current.nextSequence());
        } catch (IOException exception) {
            current = new StageState(StageState.SCHEMA_VERSION, current.zones(),
                    operation.checkpoint(StageOperationStatus.RECOVERY_REQUIRED, operation.progress(),
                            "A partial tick could not be checkpointed; explicit recovery is required."), current.history(), current.nextSequence());
            throw new IOException("Stage may have partially changed the world and could not checkpoint; recovery is required", exception);
        }
        return processed;
    }

    private void haltConflict(StageOperation operation, String detail) throws IOException {
        StageOperation conflict = operation.checkpoint(StageOperationStatus.CONFLICT, operation.progress(), detail);
        try {
            persist(current.zones(), conflict, current.history(), current.nextSequence());
        } catch (IOException exception) {
            current = new StageState(StageState.SCHEMA_VERSION, current.zones(), conflict,
                    current.history(), current.nextSequence());
            throw exception;
        }
    }

    private void preflightRemaining(StageOperation operation, StageWorld world) {
        for (int offset = operation.progress(); offset < operation.entries().size(); offset++) {
            StageEntry entry = operation.entryAtProgress(offset);
            requireAccessible(world, entry.position());
            StageBlockState actual = world.getBlockState(entry.position());
            StageBlockState source = source(operation.direction(), entry);
            StageBlockState desired = desired(operation.direction(), entry);
            if (!actual.equals(source) && !actual.equals(desired)) {
                throw new IllegalStateException("conflict at " + entry.position().display() + ": found " + actual.display()
                        + ", expected " + source.display() + " or " + desired.display());
            }
        }
    }

    private void preflightExact(List<StageEntry> entries, StageWorld world, StageDirection direction, boolean acceptDesired) {
        for (StageEntry entry : entries) {
            requireAccessible(world, entry.position());
            StageBlockState actual = world.getBlockState(entry.position());
            StageBlockState source = source(direction, entry);
            StageBlockState desired = desired(direction, entry);
            if (!actual.equals(source) && (!acceptDesired || !actual.equals(desired))) {
                throw new IllegalStateException("conflict at " + entry.position().display() + ": found " + actual.display()
                        + ", expected " + source.display() + (acceptDesired ? " or " + desired.display() : ""));
            }
        }
    }

    private static StageBlockState source(StageDirection direction, StageEntry entry) {
        return direction == StageDirection.APPLY ? entry.before() : entry.after();
    }

    private static StageBlockState desired(StageDirection direction, StageEntry entry) {
        return direction == StageDirection.APPLY ? entry.after() : entry.before();
    }

    private static void requireAccessible(StageWorld world, StagePosition position) {
        if (!world.isLoaded(position)) throw new IllegalStateException("chunk is not loaded at " + position.display());
        if (!world.isInBuildLimit(position)) throw new IllegalArgumentException("position is outside world height at " + position.display());
        if (!world.isInsideWorldBorder(position)) throw new IllegalArgumentException("position is outside world border at " + position.display());
        if (world.hasBlockEntity(position)) throw new IllegalArgumentException("block entity is protected at " + position.display());
    }

    private void rejectUnfinishedOperation() {
        StageOperation operation = current.operation();
        if (operation != null && operation.status() != StageOperationStatus.UNDONE) {
            throw new IllegalStateException("latest operation must be safely undone before defining or applying another stage");
        }
    }

    private StageZone zone(String id) {
        StageValidation.zoneId(id);
        StageZone zone = current.zones().get(id);
        if (zone == null) throw new IllegalArgumentException("unknown stage zone: " + id);
        return zone;
    }

    private static StageWorld resolve(StageWorldResolver worlds, String dimension) {
        if (worlds == null) throw new IllegalArgumentException("world resolver is required");
        StageWorld world = worlds.resolve(dimension);
        if (world == null || !dimension.equals(world.dimension())) throw new IllegalStateException("stage dimension is unavailable: " + dimension);
        return world;
    }

    private static void requireActor(Actor actor) {
        if (actor == null) throw new IllegalArgumentException("actor is required");
    }

    private static void requireActive(boolean active) {
        if (!active) throw new IllegalStateException("campaign session must be active for stage mutation");
    }

    private void commit(Map<String, StageZone> zones, StageOperation operation, Actor actor, String action,
                        String target, String operationId, String detail) throws IOException {
        if (current.nextSequence() == Long.MAX_VALUE) throw new IllegalStateException("stage audit sequence is exhausted");
        List<StageAuditEntry> history = new ArrayList<>(current.history());
        history.add(new StageAuditEntry(current.nextSequence(), now(), actor, action, target, operationId, detail));
        if (history.size() > StageValidation.MAX_HISTORY) history.removeFirst();
        persist(zones, operation, history, current.nextSequence() + 1);
    }

    private void persist(Map<String, StageZone> zones, StageOperation operation,
                         List<StageAuditEntry> history, long nextSequence) throws IOException {
        StageState candidate = new StageState(StageState.SCHEMA_VERSION, zones, operation, history, nextSequence);
        store.save(candidate);
        current = candidate;
    }

    private String now() {
        return Instant.now(clock).toString();
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
