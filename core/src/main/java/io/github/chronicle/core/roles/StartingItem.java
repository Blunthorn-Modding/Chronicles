package io.github.chronicle.core.roles;

import io.github.chronicle.core.content.InventorySnapshot;
import java.util.List;

/** One canonical ItemStack payload; runtime registry/component validation is performed later. */
public record StartingItem(String stackJson) {
    public StartingItem {
        if(stackJson==null||stackJson.length()>InventorySnapshot.MAX_STACK_JSON_CHARS)throw new IllegalArgumentException("Starting item exceeds its JSON limit");
        // Escaped component JSON is a second parse boundary; bound it before recursive canonicalization.
        io.github.chronicle.core.books.BookConfig.strictObject(stackJson);
        // Reuse the bounded canonical JSON validation without accepting an empty slot.
        stackJson = new InventorySnapshot(1, List.of(stackJson)).stacks().getFirst();
    }
}
