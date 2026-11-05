package io.github.chronicle.lastcouncil.house;

import java.util.Objects;

/** Reusable, public house content; contains no character or private lore. */
public record HouseDefinition(String id, String title, String reputation, String description, String interests, boolean defaultCandidate) {
    public HouseDefinition {
        id = validId(id);
        Objects.requireNonNull(title, "title");
        if (title.codePoints().anyMatch(c -> c == '\u00a7' || Character.isISOControl(c)))
            throw new IllegalArgumentException("title contains control or formatting characters");
        title = bounded(title.strip(), 80, "title", false);
        if (title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        reputation = bounded(reputation, 160, "reputation", false);
        description = bounded(description, 2048, "description", true);
        interests = bounded(interests, 1024, "interests", true);
    }
    static String validId(String id) {
        if (id == null || id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("invalid namespaced house ID");
        return id;
    }
    private static String bounded(String text, int max, String field, boolean multiline) {
        Objects.requireNonNull(text, field);
        if (text.codePointCount(0, text.length()) > max) throw new IllegalArgumentException(field + " exceeds " + max);
        if (text.codePoints().anyMatch(c -> c == '\u00a7' || (Character.isISOControl(c) && !(multiline && c == '\n'))))
            throw new IllegalArgumentException(field + " contains control or formatting characters");
        return text;
    }
}
