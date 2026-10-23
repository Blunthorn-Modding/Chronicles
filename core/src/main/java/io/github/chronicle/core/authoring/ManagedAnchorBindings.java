package io.github.chronicle.core.authoring;

import java.io.IOException;
import java.util.List;

/** Pure coordinator for trusted automatic block anchors; the two stores remain independently atomic. */
public final class ManagedAnchorBindings {
    private ManagedAnchorBindings() { }

    public static AuthoringSite.Anchor ensure(AuthoringService authoring, SiteAssignmentService assignments,
                                               String requirementId, String preferredSiteId, String dimension,
                                               BlockPosition position, String expectedBlock, boolean immutable) throws IOException {
        SiteAssignments.Binding binding = assignments.state().bindings().get(requirementId);
        String siteId = binding == null ? null : binding.siteId();
        AuthoringSite existing = siteId == null ? null : authoring.state().sites().get(siteId);
        if (binding != null && (existing == null || existing.revision() != binding.siteRevision()))
            throw new IllegalStateException("Managed root assignment is stale: " + binding.siteId() + ". Resolve it explicitly before placement.");
        if (binding == null) {
            List<AuthoringSite.Anchor> candidates = authoring.state().sites().values().stream()
                    .filter(AuthoringSite.Anchor.class::isInstance).map(AuthoringSite.Anchor.class::cast)
                    .filter(anchor -> anchor.expectedBlock().equals(expectedBlock)).toList();
            if (candidates.size() > 1)
                throw new IllegalStateException("Several saved anchors reference " + expectedBlock + "; resolve the intended root explicitly.");
            if (candidates.size() == 1) { existing = candidates.getFirst(); siteId = existing.id(); }
            else { siteId = preferredSiteId; existing = authoring.state().sites().get(siteId); }
        }
        if (existing != null && (!(existing instanceof AuthoringSite.Anchor anchor) || !anchor.expectedBlock().equals(expectedBlock)))
            throw new IllegalStateException("Managed root site ID conflicts with existing authoring data: " + siteId + ".");
        AuthoringSite.Anchor anchor = existing == null ? null : (AuthoringSite.Anchor) existing;
        boolean exact = anchor != null && anchor.dimension().equals(dimension) && anchor.position().equals(position);
        if (!exact) {
            if (immutable) throw new IllegalStateException("The bound campaign root can only be repaired at its original position.");
            AuthoringSite.Anchor candidate = new AuthoringSite.Anchor(siteId, dimension, 1, position, expectedBlock);
            anchor = (AuthoringSite.Anchor) authoring.put(candidate, existing != null);
        }
        SiteAssignments.Binding current = assignments.state().bindings().get(requirementId);
        if (current == null || !current.siteId().equals(anchor.id()) || current.siteRevision() != anchor.revision())
            assignments.assign(requirementId, anchor.id(), authoring.state());
        return anchor;
    }
}
