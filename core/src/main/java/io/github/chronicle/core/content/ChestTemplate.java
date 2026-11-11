package io.github.chronicle.core.content;

import java.util.Objects;

public record ChestTemplate(String id, long revision, InventorySnapshot contents) {
    public ChestTemplate {
        ContentIds.requireId(id, "template");
        ContentIds.requireRevision(revision, "template");
        Objects.requireNonNull(contents, "template contents are required");
    }

    public ChestTemplate withRevision(long revision) {
        return new ChestTemplate(id, revision, contents);
    }
}
