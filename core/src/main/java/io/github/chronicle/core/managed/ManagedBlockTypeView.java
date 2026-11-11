package io.github.chronicle.core.managed;

public record ManagedBlockTypeView(String typeId, String blockId, String label, String description,
                                   int count, int limit, boolean fixed, boolean overLimit) { }
