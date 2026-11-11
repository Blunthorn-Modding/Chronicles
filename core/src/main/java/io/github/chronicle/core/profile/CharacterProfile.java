package io.github.chronicle.core.profile;

import java.util.UUID;

/** A revisioned, server-owned character card for one player UUID. */
public record CharacterProfile(UUID owner, String name, String pronouns, String description, long revision) {
    public static final int NAME_LIMIT = 48;
    public static final int PRONOUNS_LIMIT = 32;
    public static final int DESCRIPTION_LIMIT = 512;
    public static final long REVISION_LIMIT = Long.MAX_VALUE;

    public CharacterProfile {
        if (owner == null) {
            throw new IllegalArgumentException("profile owner is required");
        }
        name = requiredText(name, "name", NAME_LIMIT);
        pronouns = optionalText(pronouns, "pronouns", PRONOUNS_LIMIT);
        description = optionalText(description, "description", DESCRIPTION_LIMIT);
        if (revision < 0 || revision > REVISION_LIMIT) {
            throw new IllegalArgumentException("profile revision must be between 0 and " + REVISION_LIMIT);
        }
    }

    private static String requiredText(String value, String field, int limit) {
        String normalized = optionalText(value, field, limit);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String optionalText(String value, String field, int limit) {
        String supplied = value == null ? "" : value;
        if (supplied.indexOf('\u00A7') >= 0 || supplied.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must not contain formatting or control characters");
        }
        String normalized = supplied.strip();
        if (normalized.codePointCount(0, normalized.length()) > limit) {
            throw new IllegalArgumentException(field + " must contain at most " + limit + " Unicode code points");
        }
        return normalized;
    }
}
