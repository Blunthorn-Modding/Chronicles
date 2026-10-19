package io.github.chronicle.core.campaign;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

final class CampaignValidation {
    static final int MAX_SESSION_NUMBER = 1_000_000;
    static final int MAX_FLAGS = 256;
    static final int MAX_COMPLETED_EVENTS = 1_024;
    static final int MAX_HISTORY = 1_000;
    static final int MAX_IDENTIFIER_LENGTH = 128;
    private static final int MAX_TEXT_LENGTH = 1_024;
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private CampaignValidation() {
    }

    static String identifier(String value, String field) {
        if (value == null || value.length() > MAX_IDENTIFIER_LENGTH || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a namespaced Minecraft identifier up to 128 characters");
        }
        return value;
    }

    static String text(String value, String field) {
        if (value == null || value.isBlank() || value.length() > MAX_TEXT_LENGTH || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must be non-blank, control-free text up to " + MAX_TEXT_LENGTH + " characters");
        }
        return value;
    }

    static void sessionNumber(int value) {
        if (value < 0 || value > MAX_SESSION_NUMBER) {
            throw new IllegalArgumentException("sessionNumber must be between 0 and " + MAX_SESSION_NUMBER);
        }
    }

    static void flagMap(Map<String, Boolean> flags, String field) {
        if (flags == null || flags.size() > MAX_FLAGS) {
            throw new IllegalArgumentException(field + " may contain at most " + MAX_FLAGS + " flags");
        }
        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            identifier(entry.getKey(), field + " key");
            if (entry.getValue() == null) {
                throw new IllegalArgumentException(field + " values must be boolean");
            }
        }
    }

    static void utcTimestamp(String timestamp) {
        text(timestamp, "timestamp");
        if (!timestamp.endsWith("Z")) {
            throw new IllegalArgumentException("timestamp must be an ISO-8601 UTC instant");
        }
        try {
            Instant.parse(timestamp);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("timestamp must be an ISO-8601 UTC instant", exception);
        }
    }
}
