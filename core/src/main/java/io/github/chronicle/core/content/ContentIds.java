package io.github.chronicle.core.content;

import java.util.UUID;
import java.util.regex.Pattern;

final class ContentIds {
    // Matches Core authoring: legacy plain names and registered namespace:name roles.
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]{0,63}(?::[a-z][a-z0-9_]{0,63})?");
    private static final Pattern NAMESPACED_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private ContentIds() { }

    static void requireId(String value, String field) {
        if (value == null || !ID.matcher(value).matches()) throw new IllegalArgumentException(field + " ID must match " + ID.pattern());
    }
    static void requireNamespacedId(String value, String field) {
        if (value == null || !NAMESPACED_ID.matcher(value).matches()) throw new IllegalArgumentException(field + " must be a namespaced registry ID");
    }
    static void requireRevision(long revision, String field) {
        if (revision < 1) throw new IllegalArgumentException(field + " revision must be positive");
    }
    static void requireOperationId(String value) {
        try { UUID.fromString(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("operation ID must be a UUID", exception); }
    }
    static void requireUuid(String value) {
        try { UUID.fromString(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("actor UUID must be a UUID", exception); }
    }
    static void requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(field + " must be non-blank and at most " + max + " characters");
    }
}
