package io.github.chronicle.core.spawn;

import io.github.chronicle.core.authoring.AuthoringRegistry;
import io.github.chronicle.core.authoring.AuthoringSite;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpawnServiceTest {
    @Test void bindsOnlyLocationsPinsRevisionsAndNeverPublishesFailedSave() throws IOException {
        Memory repository = new Memory();
        SpawnService service = new SpawnService(repository);
        AuthoringRegistry authoring = registry(location("arrival", 4));
        SpawnBinding binding = service.bind(authoring, "arrival", "arrival", false);
        assertEquals(1, service.state().revision());
        assertEquals(4, binding.locationRevision());
        assertEquals(location("arrival", 4), service.resolve(authoring, "arrival"));
        assertThrows(IllegalStateException.class, () -> service.bind(authoring, "arrival", "arrival", false));
        assertThrows(IllegalArgumentException.class, () -> service.bind(registry(new AuthoringSite.Region("area", "minecraft:overworld", 5, new io.github.chronicle.core.authoring.BlockPosition(0, 0, 0), new io.github.chronicle.core.authoring.BlockPosition(1, 1, 1))), "other", "area", false));
        assertThrows(IllegalStateException.class, () -> service.resolve(registry(location("arrival", 5)), "arrival"));
        repository.fail = true;
        assertThrows(IOException.class, () -> service.remove("arrival"));
        assertEquals(Map.of("arrival", binding), service.state().bindings());
    }
    @Test void replaceAndRemoveAreExplicit() throws IOException {
        SpawnService service = new SpawnService(new Memory());
        AuthoringRegistry authoring = registry(location("first", 1), location("second", 2));
        assertThrows(IllegalStateException.class, () -> service.bind(authoring, "arrival", "first", true));
        service.bind(authoring, "arrival", "first", false);
        assertEquals("second", service.bind(authoring, "arrival", "second", true).locationId());
        service.remove("arrival");
        assertThrows(IllegalStateException.class, () -> service.remove("arrival"));
    }
    private static AuthoringRegistry registry(AuthoringSite... sites) { return new AuthoringRegistry(9, java.util.Arrays.stream(sites).collect(java.util.stream.Collectors.toMap(AuthoringSite::id, s -> s))); }
    private static AuthoringSite.Location location(String id, long revision) { return new AuthoringSite.Location(id, "minecraft:overworld", revision, 0.5, 64, 0.5, 20, 10); }
    private static final class Memory implements SpawnRepository { SpawnRegistry state = SpawnRegistry.empty(); boolean fail; public SpawnRegistry load() { return state; } public void save(SpawnRegistry value) throws IOException { if (fail) throw new IOException("deliberate"); state = value; } }
}
