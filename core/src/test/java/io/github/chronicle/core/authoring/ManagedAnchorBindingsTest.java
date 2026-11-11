package io.github.chronicle.core.authoring;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ManagedAnchorBindingsTest {
    @Test void unboundRootMovesWithAliasWhileImmutableRootOnlyRepairsExactPosition() throws IOException {
        Fixture fixture = new Fixture();
        AuthoringSite.Anchor first = ManagedAnchorBindings.ensure(fixture.authoring, fixture.assignments, "test:root", "test:root",
                "minecraft:overworld", new BlockPosition(1, 2, 3), "test:heart", false);
        assertEquals("test:root", first.id()); assertEquals(first.revision(), fixture.binding().siteRevision());

        AuthoringSite.Anchor moved = ManagedAnchorBindings.ensure(fixture.authoring, fixture.assignments, "test:root", "test:root",
                "minecraft:overworld", new BlockPosition(8, 9, 10), "test:heart", false);
        assertEquals(first.id(), moved.id()); assertTrue(moved.revision() > first.revision()); assertEquals(moved.revision(), fixture.binding().siteRevision());

        long authoringRevision = fixture.authoring.state().revision(), assignmentRevision = fixture.assignments.state().revision();
        assertEquals(moved, ManagedAnchorBindings.ensure(fixture.authoring, fixture.assignments, "test:root", "test:root",
                "minecraft:overworld", moved.position(), "test:heart", true));
        assertEquals(authoringRevision, fixture.authoring.state().revision()); assertEquals(assignmentRevision, fixture.assignments.state().revision());
        assertThrows(IllegalStateException.class, () -> ManagedAnchorBindings.ensure(fixture.authoring, fixture.assignments, "test:root", "test:root",
                "minecraft:overworld", new BlockPosition(11, 12, 13), "test:heart", true));
    }

    @Test void staleBindingAndConflictingAliasesFailWithoutSilentRepair() throws IOException {
        Fixture stale = new Fixture();
        ManagedAnchorBindings.ensure(stale.authoring, stale.assignments, "test:root", "test:root", "minecraft:overworld", new BlockPosition(1, 2, 3), "test:heart", false);
        stale.authoring.put(new AuthoringSite.Anchor("test:root", "minecraft:overworld", 1, new BlockPosition(4, 5, 6), "test:heart"), true);
        long assignmentRevision = stale.assignments.state().revision();
        assertThrows(IllegalStateException.class, () -> ManagedAnchorBindings.ensure(stale.authoring, stale.assignments, "test:root", "test:root",
                "minecraft:overworld", new BlockPosition(7, 8, 9), "test:heart", false));
        assertEquals(assignmentRevision, stale.assignments.state().revision());

        Fixture conflict = new Fixture();
        conflict.authoring.put(new AuthoringSite.Anchor("one", "minecraft:overworld", 1, new BlockPosition(1, 2, 3), "test:heart"), false);
        conflict.authoring.put(new AuthoringSite.Anchor("two", "minecraft:overworld", 1, new BlockPosition(4, 5, 6), "test:heart"), false);
        assertThrows(IllegalStateException.class, () -> ManagedAnchorBindings.ensure(conflict.authoring, conflict.assignments, "test:root", "test:root",
                "minecraft:overworld", new BlockPosition(7, 8, 9), "test:heart", false));
        assertTrue(conflict.assignments.state().bindings().isEmpty());
    }

    private static final class Fixture {
        final MemoryAuthoring authoringRepository = new MemoryAuthoring();
        final MemoryAssignments assignmentRepository = new MemoryAssignments();
        final AuthoringService authoring;
        final SiteAssignmentService assignments;
        Fixture() throws IOException {
            AuthoringCatalog catalog = new AuthoringCatalog();
            catalog.register(new AuthoringCatalog.Role("test:root", "Root", "Managed root", AuthoringCatalog.Kind.BLOCK));
            authoring = new AuthoringService(authoringRepository); assignments = new SiteAssignmentService(assignmentRepository, catalog);
        }
        SiteAssignments.Binding binding() { return assignments.state().bindings().get("test:root"); }
    }
    private static final class MemoryAuthoring implements AuthoringRepository {
        AuthoringRegistry state = AuthoringRegistry.empty(); public AuthoringRegistry load() { return state; } public void save(AuthoringRegistry state) { this.state = state; }
    }
    private static final class MemoryAssignments implements SiteAssignmentRepository {
        SiteAssignments state = SiteAssignments.empty(); public SiteAssignments load() { return state; } public void save(SiteAssignments state) { this.state = state; }
    }
}
