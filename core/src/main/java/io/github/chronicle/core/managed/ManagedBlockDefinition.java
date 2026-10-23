package io.github.chronicle.core.managed;

import io.github.chronicle.core.authoring.AuthoringSite;

/** A campaign-supplied physical block type whose world instances Core accounts. */
public record ManagedBlockDefinition(String typeId, String blockId, String label, String description,
                                     int defaultLimit, LimitMode limitMode) {
    public static final int MAX_LIMIT = 1_024;

    public ManagedBlockDefinition {
        if (typeId == null || !AuthoringSite.NAMESPACED_ID.matcher(typeId).matches())
            throw new IllegalArgumentException("Managed block type ID must be namespaced.");
        if (blockId == null || !AuthoringSite.NAMESPACED_ID.matcher(blockId).matches())
            throw new IllegalArgumentException("Managed block registry ID must be namespaced.");
        plain(label, 80, "label");
        plain(description, 256, "description");
        if (defaultLimit < 0 || defaultLimit > MAX_LIMIT)
            throw new IllegalArgumentException("Managed block default limit must be 0-" + MAX_LIMIT + ".");
        if (limitMode == null) throw new IllegalArgumentException("Managed block limit mode is required.");
    }

    private static void plain(String value, int maximum, String field) {
        if (value == null || value.isBlank() || value.length() > maximum || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Managed block " + field + " must be plain text, 1-" + maximum + " characters.");
    }

    public enum LimitMode { EDITABLE, FIXED }
}
