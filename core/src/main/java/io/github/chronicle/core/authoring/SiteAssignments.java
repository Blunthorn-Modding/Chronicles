package io.github.chronicle.core.authoring;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable, revisioned links from registered setup requirements to saved map sites. */
public record SiteAssignments(long revision, Map<String, Binding> bindings) {
    public static final int MAX_BINDINGS = 256;

    public SiteAssignments {
        if (revision < 0) throw new IllegalArgumentException("assignment revision cannot be negative");
        Objects.requireNonNull(bindings, "assignment bindings are required");
        if (bindings.size() > MAX_BINDINGS) throw new IllegalArgumentException("at most " + MAX_BINDINGS + " site assignments are allowed");
        LinkedHashMap<String, Binding> copied = new LinkedHashMap<>();
        for (var entry : bindings.entrySet()) {
            String requirementId = entry.getKey();
            Binding binding = entry.getValue();
            if (requirementId == null || !AuthoringSite.ID.matcher(requirementId).matches()) {
                throw new IllegalArgumentException("assignment requirement ID is invalid");
            }
            if (binding == null || !requirementId.equals(binding.requirementId())) {
                throw new IllegalArgumentException("assignment map key must match its requirement ID");
            }
            if (copied.putIfAbsent(requirementId, binding) != null) throw new IllegalArgumentException("duplicate assignment requirement: " + requirementId);
        }
        bindings = Map.copyOf(copied);
    }

    public static SiteAssignments empty() { return new SiteAssignments(0, Map.of()); }

    /** A site ID and its exact saved revision; geometry is intentionally never copied here. */
    public record Binding(String requirementId, String siteId, long siteRevision) {
        public Binding {
            if (requirementId == null || !AuthoringSite.ID.matcher(requirementId).matches()) {
                throw new IllegalArgumentException("assignment requirement ID is invalid");
            }
            if (siteId == null || !AuthoringSite.ID.matcher(siteId).matches()) {
                throw new IllegalArgumentException("assignment site ID is invalid");
            }
            if (siteRevision < 1) throw new IllegalArgumentException("assignment site revision must be positive");
        }
    }
}
