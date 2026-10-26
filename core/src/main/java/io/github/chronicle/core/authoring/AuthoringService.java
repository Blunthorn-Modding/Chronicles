package io.github.chronicle.core.authoring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Applies revisioned authoring changes only after their durable save succeeds. */
public final class AuthoringService {
    private final AuthoringRepository repository;
    private AuthoringRegistry current;
    private final List<MutationGuard> mutationGuards = new ArrayList<>();

    public AuthoringService(AuthoringRepository repository) throws IOException {
        if (repository == null) {
            throw new IllegalArgumentException("authoring repository is required");
        }
        this.repository = repository;
        this.current = repository.load();
        if (current == null) {
            throw new IOException("authoring repository returned no registry");
        }
    }

    public synchronized AuthoringRegistry state() {
        return current;
    }

    /** Installs a server-owned dependency guard; ordinary standalone services remain allow-all. */
    public synchronized void mutationGuard(MutationGuard guard) {
        mutationGuards.clear();
        if (guard != null) mutationGuards.add(guard);
    }

    /** Adds an independent dependency guard without replacing guards installed by other Core services. */
    public synchronized void addMutationGuard(MutationGuard guard) {
        if (guard == null) throw new IllegalArgumentException("mutation guard is required");
        mutationGuards.add(guard);
    }

    public synchronized AuthoringSite put(AuthoringSite site, boolean replace) throws IOException {
        if (site == null) {
            throw new IllegalArgumentException("authoring site is required");
        }
        boolean exists = current.sites().containsKey(site.id());
        if (exists && !replace) {
            throw new IllegalStateException("authoring site already exists: " + site.id());
        }
        if (!exists && replace) {
            throw new IllegalStateException("authoring site does not exist for replacement: " + site.id());
        }
        checkGuards(current.sites().get(site.id()), site, replace ? Operation.REPLACE : Operation.CREATE);
        long revision = nextRevision();
        AuthoringSite saved = site.withRevision(revision);
        LinkedHashMap<String, AuthoringSite> sites = new LinkedHashMap<>(current.sites());
        sites.put(saved.id(), saved);
        commit(new AuthoringRegistry(revision, sites));
        return saved;
    }

    public synchronized void remove(String id) throws IOException {
        if (id == null || !AuthoringSite.ID.matcher(id).matches()) {
            throw new IllegalArgumentException("site ID must match " + AuthoringSite.ID.pattern());
        }
        if (!current.sites().containsKey(id)) {
            throw new IllegalStateException("authoring site does not exist: " + id);
        }
        checkGuards(current.sites().get(id), null, Operation.REMOVE);
        long revision = nextRevision();
        LinkedHashMap<String, AuthoringSite> sites = new LinkedHashMap<>(current.sites());
        sites.remove(id);
        commit(new AuthoringRegistry(revision, sites));
    }

    private void commit(AuthoringRegistry candidate) throws IOException {
        repository.save(candidate);
        current = candidate;
    }

    private void checkGuards(AuthoringSite existing, AuthoringSite proposed, Operation operation) {
        for (MutationGuard guard : List.copyOf(mutationGuards)) guard.check(existing, proposed, operation);
    }

    private long nextRevision() {
        try {
            return Math.addExact(current.revision(), 1);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("authoring registry revision is exhausted", exception);
        }
    }

    public enum Operation { CREATE, REPLACE, REMOVE }
    @FunctionalInterface public interface MutationGuard {
        void check(AuthoringSite existing, AuthoringSite proposed, Operation operation);
    }
}
