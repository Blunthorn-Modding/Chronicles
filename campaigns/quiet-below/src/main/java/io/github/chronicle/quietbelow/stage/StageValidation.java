package io.github.chronicle.quietbelow.stage;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.List;

final class StageValidation {
    static final int MAX_POSITIONS = 64;
    static final int MAX_ZONES = 32;
    static final int MAX_HISTORY = 256;
    static final Set<String> NATURAL_BLOCKS = Set.of(
            "minecraft:stone", "minecraft:deepslate", "minecraft:cobblestone",
            "minecraft:cobbled_deepslate", "minecraft:tuff", "minecraft:calcite");
    static final StageBlockState SCULK = new StageBlockState("minecraft:sculk", java.util.Map.of());

    private StageValidation() { }

    static void zoneId(String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("zone ID must use 1-64 lowercase letters, digits, dot, underscore or dash");
        }
    }

    static void dimension(String value) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || value.length() > 128) {
            throw new IllegalArgumentException("dimension must be a namespaced identifier");
        }
    }

    static void timestamp(String value) {
        try {
            Instant.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("timestamp must be an ISO-8601 instant", exception);
        }
    }

    static void stageEntries(List<StageEntry> entries) {
        for (StageEntry entry : entries) {
            if (!NATURAL_BLOCKS.contains(entry.before().id()) || !SCULK.equals(entry.after())) {
                throw new IllegalArgumentException("stage entries may only replace the natural allowlist with passive minecraft:sculk");
            }
        }
    }
}
