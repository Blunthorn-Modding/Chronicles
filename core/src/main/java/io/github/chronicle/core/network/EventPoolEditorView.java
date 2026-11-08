package io.github.chronicle.core.network;

import io.github.chronicle.core.eventpool.EventPoolDefinition;
import java.util.List;

/** Only reusable definitions and safe authoring diagnostics, never live draws. */
public record EventPoolEditorView(List<EventPoolDefinition> pools, List<Option> events) {
    public record Option(String id, String label, String problem) {}
}
