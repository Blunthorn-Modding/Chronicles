package io.github.chronicle.core.content;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Transactional domain service: candidates become visible only after durable repository writes. */
public final class ChestContentService {
    private final ChestContentRepository repository;
    private ChestContentState current;

    public ChestContentService(ChestContentRepository repository) throws IOException {
        if (repository == null) throw new IllegalArgumentException("chest content repository is required");
        this.repository = repository;
        this.current = repository.load();
        if (current == null) throw new IOException("chest content repository returned no state");
    }

    public synchronized ChestContentState state() { return current; }

    public synchronized ChestTemplate putTemplate(String id, InventorySnapshot contents, boolean replace) throws IOException {
        requireNoPendingEdits();
        boolean exists = current.templates().containsKey(id);
        requireReplacement(exists, replace, "template", id);
        long revision = nextRevision();
        ChestTemplate template = new ChestTemplate(id, revision, contents);
        var templates = new LinkedHashMap<>(current.templates());
        templates.put(id, template);
        commit(new ChestContentState(revision, templates, current.bindings(), current.receipts(), current.pending()));
        return template;
    }

    public synchronized ChestBinding putBinding(String id, String templateId, long templateRevision,
                                                 String anchorId, long anchorRevision, ChestPolicy policy,
                                                 boolean replace) throws IOException {
        requireNoPendingEdits();
        ChestTemplate template = current.templates().get(templateId);
        if (template == null) throw new IllegalArgumentException("Unknown chest template: " + templateId);
        if (template.revision() != templateRevision) throw new IllegalStateException("Chest template revision is stale: " + templateId);
        boolean exists = current.bindings().containsKey(id);
        requireReplacement(exists, replace, "binding", id);
        long revision = nextRevision();
        ChestBinding binding = new ChestBinding(id, revision, templateId, templateRevision, anchorId, anchorRevision, policy);
        var bindings = new LinkedHashMap<>(current.bindings());
        bindings.put(id, binding);
        commit(new ChestContentState(revision, current.templates(), bindings, current.receipts(), current.pending()));
        return binding;
    }

    public synchronized PendingChestOperation begin(PendingChestOperation proposed, boolean targetWasEmpty) throws IOException {
        if (proposed == null) throw new IllegalArgumentException("pending chest operation is required");
        if (!targetWasEmpty) throw new IllegalStateException("Target inventory is not completely empty.");
        if (proposed.before().equals(proposed.after())) throw new IllegalStateException("Chest template would not change the empty target.");
        if (current.receipts().size() >= ChestContentState.MAX_RECEIPTS) {
            throw new IllegalStateException("Chest receipt limit reached; archive this campaign before further fills.");
        }
        if (current.pending().containsKey(proposed.operationId())) throw new IllegalStateException("Chest operation already exists: " + proposed.operationId());
        if (current.pending().values().stream().anyMatch(operation -> operation.target().equals(proposed.target()))) {
            throw new IllegalStateException("Another unresolved chest operation already owns this target.");
        }
        ChestBinding binding = requireCurrentBinding(proposed.bindingId(), proposed.bindingRevision());
        if (!proposed.target().anchorId().equals(binding.anchorId()) || proposed.target().anchorRevision() != binding.anchorRevision()) {
            throw new IllegalStateException("Operation target does not match the binding's pinned anchor.");
        }
        ChestTemplate template = requireCurrentTemplate(binding);
        if (!template.contents().equals(proposed.after())) throw new IllegalStateException("Operation after snapshot does not match its pinned template.");
        if (proposed.before().size() != template.contents().size()) throw new IllegalStateException("Target inventory size does not match the template.");
        requireEligible(binding, proposed.sessionNumber());
        long revision = nextRevision();
        var pending = new LinkedHashMap<>(current.pending());
        pending.put(proposed.operationId(), proposed);
        commit(new ChestContentState(revision, current.templates(), current.bindings(), current.receipts(), pending));
        return proposed;
    }

    public synchronized ChestReceipt complete(String operationId, String completedAt) throws IOException {
        PendingChestOperation operation = requirePending(operationId);
        ChestBinding binding = requireCurrentBinding(operation.bindingId(), operation.bindingRevision());
        if (current.receipts().size() >= ChestContentState.MAX_RECEIPTS) {
            throw new IllegalStateException("Chest receipt limit reached; archive this campaign before further fills.");
        }
        ChestReceipt receipt = new ChestReceipt(operationId, binding.id(), operation.sessionNumber(), completedAt);
        var receipts = new ArrayList<>(current.receipts());
        receipts.add(receipt);
        var pending = new LinkedHashMap<>(current.pending());
        pending.remove(operationId);
        long revision = nextRevision();
        commit(new ChestContentState(revision, current.templates(), current.bindings(), receipts, pending));
        return receipt;
    }

    /** Resolve proof that no mutation occurred. This deliberately creates no execution receipt. */
    public synchronized void abortUnchanged(String operationId) throws IOException {
        requirePending(operationId);
        var pending = new LinkedHashMap<>(current.pending());
        pending.remove(operationId);
        long revision = nextRevision();
        commit(new ChestContentState(revision, current.templates(), current.bindings(), current.receipts(), pending));
    }

    public synchronized void requireEligible(ChestBinding binding, int sessionNumber) {
        if (sessionNumber < 0) throw new IllegalArgumentException("session number cannot be negative");
        boolean used = switch (binding.policy()) {
            case MANUAL -> false;
            case ONCE_CAMPAIGN -> current.receipts().stream().anyMatch(receipt -> receipt.bindingId().equals(binding.id()));
            case ONCE_SESSION -> current.receipts().stream().anyMatch(receipt -> receipt.bindingId().equals(binding.id())
                    && receipt.sessionNumber() == sessionNumber);
        };
        if (used) throw new IllegalStateException(binding.policy() == ChestPolicy.ONCE_CAMPAIGN
                ? "Chest binding has already run in this campaign: " + binding.id()
                : "Chest binding has already run in session " + sessionNumber + ": " + binding.id());
    }

    public synchronized ChestBinding requireCurrentBinding(String id, long revision) {
        ChestBinding binding = current.bindings().get(id);
        if (binding == null) throw new IllegalArgumentException("Unknown chest binding: " + id);
        if (binding.revision() != revision) throw new IllegalStateException("Chest binding revision is stale: " + id);
        return binding;
    }

    public synchronized ChestTemplate requireCurrentTemplate(ChestBinding binding) {
        ChestTemplate template = current.templates().get(binding.templateId());
        if (template == null || template.revision() != binding.templateRevision()) {
            throw new IllegalStateException("Chest binding " + binding.id() + " references a missing or replaced template.");
        }
        return template;
    }

    public synchronized PendingChestOperation requirePending(String id) {
        PendingChestOperation pending = current.pending().get(id);
        if (pending == null) throw new IllegalArgumentException("Unknown pending chest operation: " + id);
        return pending;
    }

    private void commit(ChestContentState candidate) throws IOException {
        repository.save(candidate);
        current = candidate;
    }

    private long nextRevision() {
        try { return Math.addExact(current.revision(), 1); }
        catch (ArithmeticException exception) { throw new IllegalStateException("chest content revision is exhausted", exception); }
    }

    private static void requireReplacement(boolean exists, boolean replace, String type, String id) {
        if (exists && !replace) throw new IllegalStateException("Chest " + type + " already exists: " + id);
        if (!exists && replace) throw new IllegalStateException("Chest " + type + " does not exist for replacement: " + id);
    }

    private void requireNoPendingEdits() {
        if (!current.pending().isEmpty()) throw new IllegalStateException("Resolve pending chest operations before editing templates or bindings.");
    }
}
