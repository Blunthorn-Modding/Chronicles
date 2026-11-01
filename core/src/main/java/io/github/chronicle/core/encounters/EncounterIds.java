package io.github.chronicle.core.encounters;

import io.github.chronicle.core.authoring.AuthoringSite;
import java.nio.charset.StandardCharsets;

final class EncounterIds {
    private EncounterIds() { }
    static String registry(String value, String label) {
        if (value == null || !AuthoringSite.NAMESPACED_ID.matcher(value).matches()) throw new IllegalArgumentException(label + " must be a namespaced registry ID");
        return value;
    }
    static String text(String value, int maximumBytes, String label) {
        if (value == null || value.indexOf('\0') >= 0 || !validUnicode(value) || value.getBytes(StandardCharsets.UTF_8).length > maximumBytes) throw new IllegalArgumentException(label + " is outside UTF-8 limits");
        return value;
    }
    private static boolean validUnicode(String value) { for (int i = 0; i < value.length(); i++) { char current = value.charAt(i); if (Character.isHighSurrogate(current)) { if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i))) return false; } else if (Character.isLowSurrogate(current)) return false; } return true; }
}
