package io.github.chronicle.core.authoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SiteAssignmentServiceTest {
    @Test
    void establishesLegacyBindingsOnceAndAnExplicitClearDoesNotReadopt(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("map-assignments.json");
        AuthoringCatalog catalog = catalog();
        SiteAssignmentStore store = new SiteAssignmentStore(file);
        SiteAssignmentService service = new SiteAssignmentService(store, catalog);
        assertTrue(store.wasAbsent());
        service.establishFromExisting(registry(new AuthoringSite.Region("quiet:city", "minecraft:overworld", 4,
                new BlockPosition(0, 0, 0), new BlockPosition(1, 1, 1))));
        service.clear("quiet:city");

        SiteAssignmentStore restartedStore = new SiteAssignmentStore(file);
        SiteAssignmentService restarted = new SiteAssignmentService(restartedStore, catalog);
        assertFalse(restartedStore.wasAbsent());
        assertTrue(restarted.state().bindings().isEmpty());
    }

    @Test
    void rejectsKindMismatchAndFailsClosedWhenThePinnedSiteChanges() throws IOException {
        AuthoringCatalog catalog = catalog();
        MemoryRepository repository = new MemoryRepository();
        SiteAssignmentService service = new SiteAssignmentService(repository, catalog);
        AuthoringRegistry locations = registry(new AuthoringSite.Location("quiet:city", "minecraft:overworld", 4, 0, 64, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.assign("quiet:city", "quiet:city", locations));

        AuthoringRegistry region = registry(new AuthoringSite.Region("quiet:city", "minecraft:overworld", 4,
                new BlockPosition(0, 0, 0), new BlockPosition(1, 1, 1)));
        service.assign("quiet:city", "quiet:city", region);
        AuthoringRegistry changed = registry(new AuthoringSite.Region("quiet:city", "minecraft:overworld", 5,
                new BlockPosition(0, 0, 0), new BlockPosition(1, 1, 1)));
        assertThrows(IllegalStateException.class, () -> service.resolve("quiet:city", changed));
    }

    @Test
    void externalStoreEditsAndFailedDurabilityNeverPublishCandidate(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("map-assignments.json");
        SiteAssignmentStore store = new SiteAssignmentStore(file);
        SiteAssignments first = new SiteAssignments(1, Map.of("quiet:city", new SiteAssignments.Binding("quiet:city", "quiet:city", 1)));
        store.load(); store.save(first);
        Files.writeString(file, "{\"schemaVersion\":1,\"revision\":99,\"bindings\":[]}");
        assertThrows(IOException.class, () -> store.save(new SiteAssignments(2, Map.of())));

        MemoryRepository repository = new MemoryRepository();
        SiteAssignmentService service = new SiteAssignmentService(repository, catalog());
        repository.fail = true;
        assertThrows(IOException.class, () -> service.assign("quiet:city", "quiet:city", registry(new AuthoringSite.Region("quiet:city", "minecraft:overworld", 1,
                new BlockPosition(0, 0, 0), new BlockPosition(1, 1, 1)))));
        assertTrue(service.state().bindings().isEmpty());
    }

    private static AuthoringCatalog catalog() {
        AuthoringCatalog catalog = new AuthoringCatalog();
        catalog.register(new AuthoringCatalog.Role("quiet:city", "City", "A source region.", AuthoringCatalog.Kind.REGION));
        return catalog;
    }
    private static AuthoringRegistry registry(AuthoringSite site) { return new AuthoringRegistry(site.revision(), Map.of(site.id(), site)); }

    private static final class MemoryRepository implements SiteAssignmentRepository {
        private SiteAssignments state = SiteAssignments.empty();
        private boolean fail;
        @Override public SiteAssignments load() { return state; }
        @Override public void save(SiteAssignments value) throws IOException {
            if (fail) throw new IOException("deliberate failure");
            state = value;
        }
    }
}
