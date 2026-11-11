package io.github.chronicle.core.roles;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.spawn.SpawnBinding;
import java.util.List;
import java.util.Set;

/** A reusable character or background definition; it has no direct world side effect. */
public record PlayerRole(String id, String name, String description, boolean selectable, int capacity,
                         SpawnBinding starting, SpawnBinding respawn, List<StartingItem> items, List<String> books) {
    public static final int MAX_ROLES = 128;
    public static final int MAX_NAME = 80, MAX_DESCRIPTION = 512, MAX_ITEMS = 36, MAX_BOOKS = 32;
    public PlayerRole {
        if (id == null || id.length() > 160 || !AuthoringSite.ID.matcher(id).matches()) throw new IllegalArgumentException("role id must match " + AuthoringSite.ID.pattern());
        name = text(name, "role name", MAX_NAME, true);
        description = text(description, "role description", MAX_DESCRIPTION, false);
        if (capacity < 1 || capacity > 512) throw new IllegalArgumentException("role capacity must be between 1 and 512");
        items = List.copyOf(items == null ? List.of() : items);
        if (items.size() > MAX_ITEMS || items.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("role items may contain at most " + MAX_ITEMS + " stacks");
        books = List.copyOf(books == null ? List.of() : books);
        if (books.size() > MAX_BOOKS || books.stream().anyMatch(book -> book == null || book.length() > 160 || !book.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) || Set.copyOf(books).size() != books.size())
            throw new IllegalArgumentException("role books must be distinct namespaced IDs");
    }
    public static String generatedId(java.util.UUID uuid) { return "chronicle:role_"+java.util.Objects.requireNonNull(uuid).toString().replace("-",""); }
    private static String text(String value, String field, int max, boolean required) {
        if (value == null || value.codePoints().anyMatch(cp -> Character.isISOControl(cp) || cp == '\u00a7') || value.codePointCount(0, value.length()) > max || (required && value.isBlank()))
            throw new IllegalArgumentException(field + " must be literal text within its limit");
        return value;
    }
}
