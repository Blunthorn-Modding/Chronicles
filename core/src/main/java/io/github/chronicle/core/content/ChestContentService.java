package io.github.chronicle.core.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;

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
        commit(new ChestContentState(revision, templates, current.bindings(), current.receipts(), current.pending(), current.schedule()));
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
        commit(new ChestContentState(revision, current.templates(), bindings, current.receipts(), current.pending(), current.schedule()));
        return binding;
    }

    /** Binds a loot type directly to a physical container using a stable location-derived identity. */
    public synchronized ChestBinding putTypedBinding(String lootType, ChestTarget physicalTarget,
                                                      boolean replace) throws IOException {
        requireNoPendingEdits();
        if (physicalTarget == null) throw new IllegalArgumentException("typed chest physical target is required");
        ContentIds.requireNamespacedId(lootType, "loot type");
        String id = typedBindingId(physicalTarget);
        ChestBinding existing = current.bindings().get(id);
        requireReplacement(existing != null, replace, "binding", id);
        if (existing != null && (!existing.typed() || !samePhysicalTarget(existing.directTarget(), physicalTarget))) {
            throw new IllegalStateException("Stable typed chest ID collides with another binding: " + id);
        }
        for (ChestBinding binding : current.bindings().values()) {
            if (!binding.id().equals(id) && binding.typed() && samePhysicalTarget(binding.directTarget(), physicalTarget)) {
                throw new IllegalStateException("A typed chest binding already owns this physical target: " + binding.id());
            }
        }
        long revision = nextRevision();
        ChestBinding binding = ChestBinding.typed(id, revision, lootType, physicalTarget);
        var bindings = new LinkedHashMap<>(current.bindings());
        bindings.put(id, binding);
        var receipts = translatedReceiptForRetype(existing, binding);
        commit(new ChestContentState(revision, current.templates(), bindings, receipts, current.pending(), current.schedule()));
        return binding;
    }

    public synchronized void removeTypedBinding(String id) throws IOException {
        ChestBinding binding = current.bindings().get(id);
        if (binding == null) throw new IllegalArgumentException("Unknown chest binding: " + id);
        if (!binding.typed()) throw new IllegalArgumentException("Chest binding is not typed: " + id);
        if (current.pending().values().stream().anyMatch(operation -> operation.bindingId().equals(id))) {
            throw new IllegalStateException("Resolve the pending chest operation before removing binding: " + id);
        }
        var bindings = new LinkedHashMap<>(current.bindings());
        bindings.remove(id);
        long revision = nextRevision();
        commit(new ChestContentState(revision, current.templates(), bindings, current.receipts(), current.pending(), current.schedule()));
    }

    public synchronized PendingChestOperation begin(PendingChestOperation proposed, boolean targetWasEmpty) throws IOException {
        if (proposed == null) throw new IllegalArgumentException("pending chest operation is required");
        ChestBinding binding = requireCurrentBinding(proposed.bindingId(), proposed.bindingRevision());
        if (!targetWasEmpty) throw new IllegalStateException(binding.typed()
                ? "Replacing typed chest contents was not explicitly authorized."
                : "Target inventory is not completely empty.");
        if (current.pending().containsKey(proposed.operationId())) throw new IllegalStateException("Chest operation already exists: " + proposed.operationId());
        if (current.pending().values().stream().anyMatch(operation -> operation.bindingId().equals(binding.id())
                || samePhysicalTarget(operation.target(), proposed.target()))) {
            throw new IllegalStateException("Another unresolved chest operation already owns this target.");
        }
        if (binding.typed()) {
            if (proposed.cycle() < 0) throw new IllegalStateException("Typed chest operation requires a nonnegative cycle.");
            if (!proposed.target().equals(binding.directTarget())) throw new IllegalStateException("Operation target does not match the typed binding's physical target.");
            requireTypedEligible(binding.id(), proposed.sessionNumber(), proposed.cycle());
            boolean replacesReceipt = current.receipts().stream().anyMatch(receipt -> receipt.bindingId().equals(binding.id()) && receipt.cycle() >= 0);
            if (!replacesReceipt && current.receipts().size() >= ChestContentState.MAX_RECEIPTS) {
                throw new IllegalStateException("Chest receipt limit reached; archive legacy campaign receipts before adding typed chests.");
            }
        } else {
            if (proposed.cycle() != -1) throw new IllegalStateException("Legacy chest operation cycle must be -1.");
            if (proposed.before().equals(proposed.after())) throw new IllegalStateException("Chest template would not change the empty target.");
            if (current.receipts().size() >= ChestContentState.MAX_RECEIPTS) {
                throw new IllegalStateException("Chest receipt limit reached; archive this campaign before further fills.");
            }
            if (!proposed.target().anchorId().equals(binding.anchorId()) || proposed.target().anchorRevision() != binding.anchorRevision()) {
                throw new IllegalStateException("Operation target does not match the binding's pinned anchor.");
            }
            ChestTemplate template = requireCurrentTemplate(binding);
            if (!template.contents().equals(proposed.after())) throw new IllegalStateException("Operation after snapshot does not match its pinned template.");
            if (proposed.before().size() != template.contents().size()) throw new IllegalStateException("Target inventory size does not match the template.");
            requireEligible(binding, proposed.sessionNumber());
        }
        long revision = nextRevision();
        var pending = new LinkedHashMap<>(current.pending());
        pending.put(proposed.operationId(), proposed);
        commit(new ChestContentState(revision, current.templates(), current.bindings(), current.receipts(), pending, current.schedule()));
        return proposed;
    }

    public synchronized ChestReceipt complete(String operationId, String completedAt) throws IOException {
        PendingChestOperation operation = requirePending(operationId);
        ChestBinding binding = requireCurrentBinding(operation.bindingId(), operation.bindingRevision());
        if (!binding.typed() && current.receipts().size() >= ChestContentState.MAX_RECEIPTS) {
            throw new IllegalStateException("Chest receipt limit reached; archive this campaign before further fills.");
        }
        ChestReceipt receipt = new ChestReceipt(operationId, binding.id(), operation.sessionNumber(), operation.cycle(), completedAt);
        var receipts = new ArrayList<>(current.receipts());
        if (binding.typed()) receipts.removeIf(existing -> existing.bindingId().equals(binding.id()) && existing.cycle() >= 0);
        receipts.add(receipt);
        var pending = new LinkedHashMap<>(current.pending());
        pending.remove(operationId);
        long revision = nextRevision();
        commit(new ChestContentState(revision, current.templates(), current.bindings(), receipts, pending, current.schedule()));
        return receipt;
    }

    /** Resolve proof that no mutation occurred. This deliberately creates no execution receipt. */
    public synchronized void abortUnchanged(String operationId) throws IOException {
        requirePending(operationId);
        var pending = new LinkedHashMap<>(current.pending());
        pending.remove(operationId);
        long revision = nextRevision();
        commit(new ChestContentState(revision, current.templates(), current.bindings(), current.receipts(), pending, current.schedule()));
    }

    public synchronized boolean hasCompletedCycle(String bindingId, int sessionNumber, long cycle) {
        ContentIds.requireId(bindingId, "binding");
        if (sessionNumber < 0) throw new IllegalArgumentException("session number cannot be negative");
        if (cycle < 0) throw new IllegalArgumentException("typed chest cycle cannot be negative");
        return current.receipts().stream().anyMatch(receipt -> receipt.bindingId().equals(bindingId)
                && receipt.sessionNumber() == sessionNumber && receipt.cycle() >= cycle);
    }

    /** Persists schedule changes before making them visible. Identical clocks do not consume revisions. */
    public synchronized void saveSchedule(LootSchedule schedule) throws IOException {
        if (schedule == null) throw new IllegalArgumentException("loot schedule is required");
        if (schedule.equals(current.schedule())) return;
        long revision = nextRevision();
        commit(new ChestContentState(revision, current.templates(), current.bindings(), current.receipts(), current.pending(), schedule));
    }

    public synchronized void requireEligible(ChestBinding binding, int sessionNumber) {
        if (sessionNumber < 0) throw new IllegalArgumentException("session number cannot be negative");
        if (binding.typed()) throw new IllegalArgumentException("typed chest bindings use generation eligibility");
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
        if (binding.typed()) throw new IllegalArgumentException("typed chest binding does not reference a template: " + binding.id());
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

    private void requireTypedEligible(String bindingId, int sessionNumber, long cycle) {
        ChestReceipt last = current.receipts().stream()
                .filter(receipt -> receipt.bindingId().equals(bindingId) && receipt.cycle() >= 0)
                .findFirst().orElse(null);
        if (last == null) return;
        if (sessionNumber < last.sessionNumber()) {
            throw new IllegalStateException("Typed chest operation belongs to an older session than its last receipt: " + bindingId);
        }
        if (sessionNumber == last.sessionNumber() && cycle <= last.cycle()) {
            throw new IllegalStateException("Typed chest generation has already completed: " + bindingId + " cycle " + cycle);
        }
    }

    private static String typedBindingId(ChestTarget target) {
        String identity = target.dimension() + "|" + target.x() + "|" + target.y() + "|" + target.z();
        return "chest_" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private static boolean samePhysicalTarget(ChestTarget left, ChestTarget right) {
        return left.dimension().equals(right.dimension()) && left.x() == right.x() && left.y() == right.y() && left.z() == right.z();
    }

    private java.util.List<ChestReceipt> translatedReceiptForRetype(ChestBinding previous, ChestBinding replacement) {
        if (previous != null && previous.typed() && previous.lootType().equals(replacement.lootType())) return current.receipts();
        if (current.schedule().sessionNumber() < 0) return current.receipts();
        LootCycle scheduled = current.schedule().types().get(replacement.lootType());
        long generation = scheduled == null ? 0 : scheduled.generation();
        ArrayList<ChestReceipt> translated = new ArrayList<>(current.receipts().size());
        boolean changed = false;
        for (ChestReceipt receipt : current.receipts()) {
            if (receipt.bindingId().equals(replacement.id()) && receipt.cycle() >= 0
                    && receipt.sessionNumber() == current.schedule().sessionNumber()) {
                translated.add(new ChestReceipt(receipt.operationId(), receipt.bindingId(), receipt.sessionNumber(), generation, receipt.completedAt()));
                changed = true;
            } else {
                translated.add(receipt);
            }
        }
        return changed ? translated : current.receipts();
    }
}
