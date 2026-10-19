package io.github.chronicle.core.content;

import java.util.Objects;

/** A revision-pinned link from a reusable template to a Core block anchor. */
public record ChestBinding(String id, long revision, String templateId, long templateRevision,
                           String anchorId, long anchorRevision, ChestPolicy policy) {
    public ChestBinding {
        ContentIds.requireId(id, "binding");
        ContentIds.requireRevision(revision, "binding");
        ContentIds.requireId(templateId, "template");
        ContentIds.requireRevision(templateRevision, "template");
        ContentIds.requireId(anchorId, "anchor");
        ContentIds.requireRevision(anchorRevision, "anchor");
        Objects.requireNonNull(policy, "chest policy is required");
    }

    public ChestBinding withRevision(long revision) {
        return new ChestBinding(id, revision, templateId, templateRevision, anchorId, anchorRevision, policy);
    }
}
