package io.github.chronicle.core.content;

import io.github.chronicle.core.books.BookChance;

import java.util.Objects;

/** A revision-pinned legacy template binding or a typed loot binding at a physical container. */
public record ChestBinding(String id, long revision, String templateId, long templateRevision,
                           String anchorId, long anchorRevision, ChestPolicy policy,
                           String lootType, ChestTarget directTarget, BookChance directBook) {
    public ChestBinding {
        ContentIds.requireId(id, "binding");
        ContentIds.requireRevision(revision, "binding");
        ContentIds.requireId(anchorId, "anchor");
        ContentIds.requireRevision(anchorRevision, "anchor");
        Objects.requireNonNull(policy, "chest policy is required");
        if (lootType == null) {
            ContentIds.requireId(templateId, "template");
            ContentIds.requireRevision(templateRevision, "template");
            if (directTarget != null) throw new IllegalArgumentException("legacy binding cannot have a direct target");
            if (directBook != null) throw new IllegalArgumentException("legacy binding cannot have a direct book");
        } else {
            ContentIds.requireNamespacedId(lootType, "loot type");
            if (templateId != null || templateRevision != 0) throw new IllegalArgumentException("typed binding cannot reference a template");
            if (!anchorId.equals(id) || anchorRevision != revision) throw new IllegalArgumentException("typed binding anchor must pin its own ID and revision");
            if (policy != ChestPolicy.MANUAL) throw new IllegalArgumentException("typed binding policy must be MANUAL");
            Objects.requireNonNull(directTarget, "typed binding direct target is required");
            if (!directTarget.anchorId().equals(id) || directTarget.anchorRevision() != revision) {
                throw new IllegalArgumentException("typed binding direct target must pin its binding ID and revision");
            }
        }
    }

    /** Source-compatible constructor for schema-1/template bindings. */
    public ChestBinding(String id, long revision, String templateId, long templateRevision,
                        String anchorId, long anchorRevision, ChestPolicy policy) {
        this(id, revision, templateId, templateRevision, anchorId, anchorRevision, policy, null, null, null);
    }

    /** Source-compatible constructor for schema-2 typed bindings. */
    public ChestBinding(String id, long revision, String templateId, long templateRevision,
                        String anchorId, long anchorRevision, ChestPolicy policy,
                        String lootType, ChestTarget directTarget) {
        this(id, revision, templateId, templateRevision, anchorId, anchorRevision, policy, lootType, directTarget, null);
    }

    public static ChestBinding typed(String id, long revision, String lootType, ChestTarget physicalTarget) {
        Objects.requireNonNull(physicalTarget, "typed binding physical target is required");
        ChestTarget pinned = new ChestTarget(id, revision, physicalTarget.dimension(), physicalTarget.x(), physicalTarget.y(),
                physicalTarget.z(), physicalTarget.expectedBlock());
        return new ChestBinding(id, revision, null, 0, id, revision, ChestPolicy.MANUAL, lootType, pinned, null);
    }

    public static ChestBinding typed(String id, long revision, String lootType, ChestTarget physicalTarget, BookChance directBook) {
        ChestBinding binding = typed(id, revision, lootType, physicalTarget);
        return new ChestBinding(binding.id, binding.revision, null, 0, binding.anchorId, binding.anchorRevision,
                binding.policy, binding.lootType, binding.directTarget, directBook);
    }

    public boolean typed() { return lootType != null; }

    public ChestBinding withRevision(long revision) {
        return typed() ? typed(id, revision, lootType, directTarget, directBook)
                : new ChestBinding(id, revision, templateId, templateRevision, anchorId, anchorRevision, policy);
    }
}
