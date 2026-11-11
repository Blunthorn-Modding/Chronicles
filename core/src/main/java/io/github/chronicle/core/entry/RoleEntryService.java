package io.github.chronicle.core.entry;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Save-before-publish state machine for role first-entry claims and recovery evidence. */
public final class RoleEntryService {
    private final RoleEntryRepository repository;
    private RoleEntryState current;

    public RoleEntryService(RoleEntryRepository repository) throws IOException {
        if (repository == null) throw new IllegalArgumentException("role-entry repository is required");
        this.repository = repository;
        this.current = repository.load();
        if (current == null) throw new IOException("role-entry repository returned no state");
    }

    public synchronized RoleEntryState state() { return current; }

    public synchronized void prepare(RoleEntryOperation operation) throws IOException {
        if (operation == null || operation.stage() != RoleEntryOperation.Stage.PREPARED) {
            throw new IllegalArgumentException("a prepared role-entry operation is required");
        }
        RoleEntryOperation prior = current.operations().get(operation.playerId());
        if (prior != null && prior.stage() != RoleEntryOperation.Stage.ABORTED) {
            throw new IllegalStateException("role first entry already has durable evidence for this player");
        }
        replace(operation);
    }

    public synchronized RoleEntryOperation begin(UUID player, UUID operation) throws IOException {
        RoleEntryOperation prior = require(player, operation);
        if (prior.stage() != RoleEntryOperation.Stage.PREPARED) throw new IllegalStateException("role first entry is not prepared");
        RoleEntryOperation next = prior.withStage(RoleEntryOperation.Stage.APPLYING, "Effects claimed; exact post-state is required for recovery.");
        replace(next); return next;
    }

    public synchronized void complete(UUID player, UUID operation) throws IOException {
        RoleEntryOperation prior = require(player, operation);
        if (prior.stage() != RoleEntryOperation.Stage.APPLYING) throw new IllegalStateException("only an applying role first entry can complete");
        replace(prior.withStage(RoleEntryOperation.Stage.COMPLETE, "First entry completed."));
    }

    public synchronized void abortPrepared(UUID player, UUID operation) throws IOException {
        RoleEntryOperation prior = require(player, operation);
        if (prior.stage() != RoleEntryOperation.Stage.PREPARED) throw new IllegalStateException("only a prepared role first entry can be safely aborted");
        replace(prior.withStage(RoleEntryOperation.Stage.ABORTED, "Recovered exact before-state; no effects were claimed."));
    }

    public synchronized void conflict(UUID player, UUID operation, String detail) throws IOException {
        RoleEntryOperation prior = require(player, operation);
        if (!prior.pending()) throw new IllegalStateException("terminal role-entry evidence cannot become conflicted");
        replace(prior.withStage(RoleEntryOperation.Stage.CONFLICT, detail));
    }

    private RoleEntryOperation require(UUID player, UUID operation) {
        RoleEntryOperation result = current.operations().get(player);
        if (result == null || !result.operationId().equals(operation)) throw new IllegalStateException("role-entry operation identity changed");
        return result;
    }

    private void replace(RoleEntryOperation operation) throws IOException {
        LinkedHashMap<UUID, RoleEntryOperation> operations = new LinkedHashMap<>(current.operations());
        operations.put(operation.playerId(), operation);
        RoleEntryState candidate = new RoleEntryState(Math.addExact(current.revision(), 1), operations);
        repository.save(candidate);
        current = candidate;
    }
}
