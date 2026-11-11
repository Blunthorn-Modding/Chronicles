package io.github.chronicle.core.authoring;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Applies assignment mutations only after strict storage has durably accepted them. */
public final class SiteAssignmentService {
    private final SiteAssignmentRepository repository;
    private final AuthoringCatalog catalog;
    private SiteAssignments current;
    private MutationGuard mutationGuard = (requirement, existing, proposed) -> { };

    public SiteAssignmentService(SiteAssignmentRepository repository, AuthoringCatalog catalog) throws IOException {
        if (repository == null || catalog == null) throw new IllegalArgumentException("assignment repository and catalog are required");
        this.repository = repository;
        this.catalog = catalog;
        this.current = repository.load();
        if (current == null) throw new IOException("assignment repository returned no state");
    }

    public synchronized SiteAssignments state() { return current; }
    public synchronized void mutationGuard(MutationGuard guard) {
        mutationGuard = guard == null ? (requirement, existing, proposed) -> { } : guard;
    }

    /** Persists the explicit initial state, preventing absent-file adoption from repeating after a restart. */
    public synchronized void establishInitial(Map<String, SiteAssignments.Binding> adopted) throws IOException {
        if (current.revision() != 0 || !current.bindings().isEmpty()) throw new IllegalStateException("assignment state is already established");
        commit(new SiteAssignments(adopted.isEmpty() ? 0 : 1, adopted));
    }

    /** One-time migration for older maps where a registered requirement already used its own site ID. */
    public synchronized void establishFromExisting(AuthoringRegistry registry) throws IOException {
        if (registry == null) throw new IllegalArgumentException("authoring registry is required");
        Map<String, SiteAssignments.Binding> adopted = new LinkedHashMap<>();
        for (AuthoringCatalog.Role role : catalog.roles()) {
            AuthoringSite site = registry.sites().get(role.id());
            if (site != null && role.kind().accepts(site)) {
                adopted.put(role.id(), new SiteAssignments.Binding(role.id(), site.id(), site.revision()));
            }
        }
        establishInitial(adopted);
    }

    public synchronized SiteAssignments.Binding assign(String requirementId, String siteId, AuthoringRegistry registry) throws IOException {
        AuthoringCatalog.Role role = role(requirementId);
        AuthoringSite site = site(registry, siteId);
        if (!role.kind().accepts(site)) throw new IllegalArgumentException(role.label() + " requires " + role.kind().name().toLowerCase(java.util.Locale.ROOT) + " geometry.");
        SiteAssignments.Binding binding = new SiteAssignments.Binding(requirementId, site.id(), site.revision());
        mutationGuard.check(requirementId, current.bindings().get(requirementId), binding);
        LinkedHashMap<String, SiteAssignments.Binding> next = new LinkedHashMap<>(current.bindings());
        next.put(requirementId, binding);
        commit(new SiteAssignments(nextRevision(), next));
        return binding;
    }

    public synchronized void clear(String requirementId) throws IOException {
        role(requirementId);
        if (!current.bindings().containsKey(requirementId)) throw new IllegalStateException("No assignment exists for " + requirementId + ".");
        mutationGuard.check(requirementId, current.bindings().get(requirementId), null);
        LinkedHashMap<String, SiteAssignments.Binding> next = new LinkedHashMap<>(current.bindings());
        next.remove(requirementId);
        commit(new SiteAssignments(nextRevision(), next));
    }

    /** Resolves only an unchanged site of the required registered type. */
    public synchronized Optional<AuthoringSite> resolve(String requirementId, AuthoringRegistry registry) {
        role(requirementId);
        SiteAssignments.Binding binding = current.bindings().get(requirementId);
        if (binding == null) return Optional.empty();
        AuthoringSite site = registry.sites().get(binding.siteId());
        if (site == null || site.revision() != binding.siteRevision()) {
            throw new IllegalStateException("Assigned site changed or is missing: " + binding.siteId() + ". Reassign " + requirementId + ".");
        }
        if (!role(requirementId).kind().accepts(site)) {
            throw new IllegalStateException("Assigned site no longer has the required geometry type: " + binding.siteId() + ".");
        }
        return Optional.of(site);
    }

    private AuthoringCatalog.Role role(String requirementId) {
        return catalog.get(requirementId).orElseThrow(() -> new IllegalArgumentException("Unknown registered map requirement: " + requirementId));
    }
    private static AuthoringSite site(AuthoringRegistry registry, String siteId) {
        if (registry == null) throw new IllegalArgumentException("authoring registry is required");
        AuthoringSite site = registry.sites().get(siteId);
        if (site == null) throw new IllegalArgumentException("Unknown saved map site: " + siteId);
        return site;
    }
    private void commit(SiteAssignments candidate) throws IOException { repository.save(candidate); current = candidate; }
    private long nextRevision() {
        try { return Math.addExact(current.revision(), 1); }
        catch (ArithmeticException exception) { throw new IllegalStateException("assignment revision is exhausted", exception); }
    }
    @FunctionalInterface public interface MutationGuard {
        void check(String requirementId, SiteAssignments.Binding existing, SiteAssignments.Binding proposed);
    }
}
