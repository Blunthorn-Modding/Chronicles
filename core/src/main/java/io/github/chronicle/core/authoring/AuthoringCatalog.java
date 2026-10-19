package io.github.chronicle.core.authoring;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Campaigns register typed authoring choices once, before server startup; Core supplies their UI. */
public final class AuthoringCatalog {
    public enum Kind {
        REGION, LOCATION, BLOCK;
        public boolean accepts(AuthoringSite site) {
            return switch (this) {
                case REGION -> site instanceof AuthoringSite.Region;
                case LOCATION -> site instanceof AuthoringSite.Location;
                case BLOCK -> site instanceof AuthoringSite.Anchor;
            };
        }
    }
    public record Role(String id, String label, String description, Kind kind) {
        public Role {
            if (id == null || !AuthoringSite.ID.matcher(id).matches() || !id.contains(":")) throw new IllegalArgumentException("Registered roles require a namespaced site ID.");
            if (label == null || label.isBlank() || label.length() > 80 || label.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Role label must be plain text, 1-80 characters.");
            if (description == null || description.isBlank() || description.length() > 256 || description.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Role description must be plain text, 1-256 characters.");
            if (kind == null) throw new IllegalArgumentException("Role kind required.");
        }
    }
    private final Map<String, Role> roles = new TreeMap<>();
    private boolean frozen;
    public synchronized void register(Role role) {
        if (frozen) throw new IllegalStateException("Authoring catalog is frozen.");
        if (role == null || roles.size() >= 256) throw new IllegalArgumentException("Authoring catalog accepts at most 256 roles.");
        if (roles.putIfAbsent(role.id(), role) != null) throw new IllegalArgumentException("Duplicate authoring role: " + role.id());
    }
    public synchronized Optional<Role> get(String id) { return Optional.ofNullable(roles.get(id)); }
    public synchronized List<Role> roles() { return List.copyOf(roles.values()); }
    public synchronized void freeze() { frozen = true; }
    public synchronized void validate(AuthoringSite site) {
        Role role = roles.get(site.id());
        if (role != null && !role.kind().accepts(site)) throw new IllegalArgumentException(role.label() + " requires " + role.kind().name().toLowerCase(java.util.Locale.ROOT) + " geometry.");
    }
}
