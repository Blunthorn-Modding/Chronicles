package io.github.chronicle.core.authoring;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class AuthoringMutationGuardTest {
    @Test void protectedMutationIsRejectedBeforeRepositorySave() throws IOException {
        Memory repository = new Memory(); AuthoringService service = new AuthoringService(repository);
        AuthoringSite.Anchor anchor = new AuthoringSite.Anchor("test:heart", "minecraft:overworld", 1, new BlockPosition(1, 2, 3), "test:heart");
        service.put(anchor, false); int before = repository.saves;
        service.mutationGuard((existing, proposed, operation) -> { if (existing != null && existing.id().equals("test:heart")) throw new IllegalStateException("bound"); });
        assertThrows(IllegalStateException.class, () -> service.remove("test:heart"));
        assertThrows(IllegalStateException.class, () -> service.put(new AuthoringSite.Anchor("test:heart", "minecraft:overworld", 1, new BlockPosition(4, 5, 6), "test:heart"), true));
        assertEquals(before, repository.saves); assertEquals(anchor.position(), ((AuthoringSite.Anchor) service.state().sites().get("test:heart")).position());
    }

    @Test void protectedAssignmentMutationIsRejectedBeforeRepositorySave() throws IOException {
        Memory authoringRepository = new Memory(); AuthoringService authoring = new AuthoringService(authoringRepository);
        AuthoringSite.Anchor anchor = (AuthoringSite.Anchor) authoring.put(new AuthoringSite.Anchor(
                "test:heart", "minecraft:overworld", 1, new BlockPosition(1, 2, 3), "test:heart"), false);
        AuthoringCatalog catalog = new AuthoringCatalog();
        catalog.register(new AuthoringCatalog.Role("test:root", "Root", "Campaign root", AuthoringCatalog.Kind.BLOCK));
        AssignmentMemory repository = new AssignmentMemory(); SiteAssignmentService assignments = new SiteAssignmentService(repository, catalog);
        assignments.assign("test:root", anchor.id(), authoring.state()); int before = repository.saves;
        assignments.mutationGuard((requirement, existing, proposed) -> {
            if (requirement.equals("test:root") && !java.util.Objects.equals(existing, proposed)) throw new IllegalStateException("bound");
        });
        assertThrows(IllegalStateException.class, () -> assignments.clear("test:root"));
        assertEquals(before, repository.saves); assertEquals(anchor.id(), assignments.state().bindings().get("test:root").siteId());
    }
    @Test void additiveGuardsComposeWithoutReplacingEarlierProtection() throws IOException {
        Memory repository=new Memory();AuthoringService service=new AuthoringService(repository);
        java.util.List<String> calls=new java.util.ArrayList<>();
        service.mutationGuard((existing,proposed,operation)->calls.add("managed"));
        service.addMutationGuard((existing,proposed,operation)->calls.add("area"));
        service.put(new AuthoringSite.Region("test:area","minecraft:overworld",1,new BlockPosition(0,0,0),new BlockPosition(1,1,1)),false);
        assertEquals(java.util.List.of("managed","area"),calls);
    }
    private static final class Memory implements AuthoringRepository {
        AuthoringRegistry state = AuthoringRegistry.empty(); int saves;
        public AuthoringRegistry load() { return state; }
        public void save(AuthoringRegistry state) { this.state = state; saves++; }
    }
    private static final class AssignmentMemory implements SiteAssignmentRepository {
        SiteAssignments state = SiteAssignments.empty(); int saves;
        public SiteAssignments load() { return state; }
        public void save(SiteAssignments state) { this.state = state; saves++; }
    }
}
