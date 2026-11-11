package io.github.chronicle.core.managed;

import io.github.chronicle.core.authoring.BlockPosition;
import java.util.UUID;

public record ManagedBlockInstanceView(UUID id, String typeId, String label, String dimension,
                                       BlockPosition position, String expectedBlock, String actualBlock,
                                       ManagedBlockStatus status, long revision) { }
