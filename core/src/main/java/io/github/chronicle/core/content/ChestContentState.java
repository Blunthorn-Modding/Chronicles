package io.github.chronicle.core.content;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Complete Core chest state. Every map is immutable and insertion ordered for stable persistence. */
public record ChestContentState(long revision, Map<String, ChestTemplate> templates,
                                Map<String, ChestBinding> bindings, List<ChestReceipt> receipts,
                                Map<String, PendingChestOperation> pending, LootSchedule schedule) {
    public static final int MAX_TEMPLATES = 256;
    public static final int MAX_BINDINGS = 512;
    public static final int MAX_RECEIPTS = 8_192;
    public static final int MAX_PENDING = 64;

    public ChestContentState {
        if (revision < 0) throw new IllegalArgumentException("content revision cannot be negative");
        templates = immutableMap(templates, "templates", MAX_TEMPLATES);
        bindings = immutableMap(bindings, "bindings", MAX_BINDINGS);
        receipts = List.copyOf(receipts);
        pending = immutableMap(pending, "pending operations", MAX_PENDING);
        if (schedule == null) throw new IllegalArgumentException("loot schedule is required");
        if (receipts.size() > MAX_RECEIPTS) throw new IllegalArgumentException("receipts may contain at most " + MAX_RECEIPTS + " entries");
        for (var entry : templates.entrySet()) {
            String id = entry.getKey(); ChestTemplate value = entry.getValue();
            if (!id.equals(value.id())) throw new IllegalArgumentException("template map key must match template ID");
            if (value.revision() > revision) throw new IllegalArgumentException("template revision cannot exceed state revision");
        }
        for (var entry : bindings.entrySet()) {
            String id = entry.getKey(); ChestBinding value = entry.getValue();
            if (!id.equals(value.id())) throw new IllegalArgumentException("binding map key must match binding ID");
            if (value.revision() > revision || (!value.typed() && value.templateRevision() > revision)) {
                throw new IllegalArgumentException("binding or template revision cannot exceed state revision");
            }
            if (!value.typed() && !templates.containsKey(value.templateId())) throw new IllegalArgumentException("binding references a missing template: " + value.templateId());
        }
        var typedTargets = new HashSet<String>();
        for (ChestBinding binding : bindings.values()) {
            if (binding.typed() && !typedTargets.add(physicalKey(binding.directTarget()))) {
                throw new IllegalArgumentException("typed bindings cannot share a physical target");
            }
        }
        HashSet<String> completedOperations = new HashSet<>();
        HashSet<String> latestTypedBindings = new HashSet<>();
        for (ChestReceipt receipt : receipts) {
            if (!completedOperations.add(receipt.operationId())) throw new IllegalArgumentException("receipt operation IDs must be unique");
            ChestBinding binding = bindings.get(receipt.bindingId());
            if (binding != null) {
                if (binding.typed() != (receipt.cycle() >= 0)) throw new IllegalArgumentException("receipt cycle kind does not match its binding");
                if (binding.typed() && !latestTypedBindings.add(binding.id())) throw new IllegalArgumentException("typed binding may retain only its newest receipt");
            }
        }
        for (var entry : pending.entrySet()) {
            String id = entry.getKey(); PendingChestOperation value = entry.getValue();
            if (!id.equals(value.operationId())) throw new IllegalArgumentException("pending map key must match operation ID");
            if (value.bindingRevision() > revision) throw new IllegalArgumentException("pending binding revision cannot exceed state revision");
            if (completedOperations.contains(id)) throw new IllegalArgumentException("an operation cannot be both pending and completed");
            ChestBinding binding = bindings.get(value.bindingId());
            if (binding == null || binding.revision() != value.bindingRevision()) throw new IllegalArgumentException("pending operation references a missing or stale binding: " + value.bindingId());
            if (binding.typed()) {
                if (value.cycle() < 0) throw new IllegalArgumentException("typed pending operation requires a nonnegative cycle");
                if (!value.target().equals(binding.directTarget())) throw new IllegalArgumentException("typed pending target does not match its binding target");
            } else {
                if (value.cycle() != -1) throw new IllegalArgumentException("legacy pending operation cycle must be -1");
                if (!value.target().anchorId().equals(binding.anchorId()) || value.target().anchorRevision() != binding.anchorRevision()) {
                    throw new IllegalArgumentException("pending target does not match its binding anchor");
                }
            }
        }
    }

    /** Source-compatible constructor for callers that do not manage typed refill schedules. */
    public ChestContentState(long revision, Map<String, ChestTemplate> templates,
                             Map<String, ChestBinding> bindings, List<ChestReceipt> receipts,
                             Map<String, PendingChestOperation> pending) {
        this(revision, templates, bindings, receipts, pending, LootSchedule.empty());
    }

    public static ChestContentState empty() {
        return new ChestContentState(0, Map.of(), Map.of(), List.of(), Map.of(), LootSchedule.empty());
    }

    private static String physicalKey(ChestTarget target) {
        return target.dimension() + '\u0000' + target.x() + '\u0000' + target.y() + '\u0000' + target.z();
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> values, String field, int max) {
        if (values == null) throw new IllegalArgumentException(field + " are required");
        if (values.size() > max) throw new IllegalArgumentException(field + " may contain at most " + max + " entries");
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
