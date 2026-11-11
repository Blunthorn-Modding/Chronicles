package io.github.chronicle.quietbelow.stage;

import java.util.List;

public record StagePreview(String zoneId, String dimension, String token, List<StageEntry> entries) {
    public StagePreview {
        entries = List.copyOf(entries);
    }
}
