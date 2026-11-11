package io.github.chronicle.core.departures;

import io.github.chronicle.core.authoring.AuthoringSite;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

final class DepartureValidation {
    private DepartureValidation() {}

    static String id(String value, String field) {
        if (value == null || !AuthoringSite.ID.matcher(value).matches()) throw new IllegalArgumentException(field + " is invalid");
        return value;
    }

    static String text(String value, int minimumBytes, int maximumBytes, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < minimumBytes || bytes > maximumBytes) throw new IllegalArgumentException(field + " is outside its UTF-8 byte limit");
        for (int offset = 0; offset < value.length();) {
            int point = value.codePointAt(offset);
            if (Character.isISOControl(point) && point != '\n' && point != '\t') throw new IllegalArgumentException(field + " contains control characters");
            offset += Character.charCount(point);
        }
        return value;
    }

    static String instant(String value, String field) {
        text(value, 1, 128, field);
        try { Instant.parse(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException(field + " must be an ISO-8601 instant", exception); }
        return value;
    }
}
