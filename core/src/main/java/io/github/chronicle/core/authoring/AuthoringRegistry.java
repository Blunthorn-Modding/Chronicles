package io.github.chronicle.core.authoring;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** A revisioned, immutable collection of all key locations for one world. */
public record AuthoringRegistry(long revision, Map<String, AuthoringSite> sites) {
    public static final int MAX_SITES = 1_024;

    public AuthoringRegistry {
        if (revision < 0) {
            throw new IllegalArgumentException("registry revision must not be negative");
        }
        if (sites == null || sites.size() > MAX_SITES) {
            throw new IllegalArgumentException("registry may contain at most " + MAX_SITES + " sites");
        }
        Map<String, AuthoringSite> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, AuthoringSite> entry : new TreeMap<>(sites).entrySet()) {
            AuthoringSite site = entry.getValue();
            if (site == null || !entry.getKey().equals(site.id())) {
                throw new IllegalArgumentException("registry key must match its site ID");
            }
            if (site.revision() > revision) {
                throw new IllegalArgumentException("site revision may not exceed registry revision");
            }
            ordered.put(entry.getKey(), site);
        }
        sites = Collections.unmodifiableMap(ordered);
    }

    public static AuthoringRegistry empty() {
        return new AuthoringRegistry(0, Map.of());
    }
}
