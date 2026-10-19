package io.github.chronicle.core.authoring;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthoringServiceTest {
    @Test
    void createsReplacesAndDeletesWithDurableMonotonicRevisions() throws IOException {
        MemoryRepository repository = new MemoryRepository();
        AuthoringService service = new AuthoringService(repository);
        AuthoringSite.Region initial = region("city", 1);

        AuthoringSite saved = service.put(initial, false);
        assertEquals(1, saved.revision());
        assertEquals(1, service.state().revision());
        assertThrows(IllegalStateException.class, () -> service.put(initial, false));
        assertThrows(IllegalStateException.class, () -> service.put(region("missing", 1), true));
        AuthoringSite replacement = service.put(new AuthoringSite.Region("city", "minecraft:overworld", 1,
                new BlockPosition(4, 4, 4), new BlockPosition(5, 5, 5)), true);
        assertEquals(2, replacement.revision());
        service.remove("city");
        assertEquals(3, service.state().revision());
        assertEquals(AuthoringRegistry.empty().sites(), service.state().sites());
        assertThrows(IllegalStateException.class, () -> service.remove("city"));
    }

    @Test
    void failingSaveNeverPublishesTheCandidateState() throws IOException {
        MemoryRepository repository = new MemoryRepository();
        AuthoringService service = new AuthoringService(repository);
        AuthoringRegistry before = service.state();
        repository.fail = true;

        assertThrows(IOException.class, () -> service.put(region("city", 1), false));
        assertEquals(before, service.state());
    }

    @Test
    void exhaustedRevisionDoesNotAttemptOrPublishMutation() throws IOException {
        MemoryRepository emptyRepository = new MemoryRepository(new AuthoringRegistry(Long.MAX_VALUE, java.util.Map.of()));
        AuthoringService emptyService = new AuthoringService(emptyRepository);
        assertThrows(IllegalStateException.class, () -> emptyService.put(region("city", 1), false));
        assertEquals(0, emptyRepository.saves);
        assertEquals(AuthoringRegistry.empty().sites(), emptyService.state().sites());

        AuthoringSite.Region site = region("city", Long.MAX_VALUE);
        MemoryRepository populatedRepository = new MemoryRepository(new AuthoringRegistry(Long.MAX_VALUE, java.util.Map.of("city", site)));
        AuthoringService populatedService = new AuthoringService(populatedRepository);
        assertThrows(IllegalStateException.class, () -> populatedService.remove("city"));
        assertEquals(0, populatedRepository.saves);
        assertEquals(java.util.Map.of("city", site), populatedService.state().sites());
    }

    private static AuthoringSite.Region region(String id, long revision) {
        return new AuthoringSite.Region(id, "minecraft:overworld", revision, new BlockPosition(0, 0, 0), new BlockPosition(1, 1, 1));
    }

    private static final class MemoryRepository implements AuthoringRepository {
        private AuthoringRegistry state;
        private boolean fail;
        private int saves;

        private MemoryRepository() {
            this(AuthoringRegistry.empty());
        }

        private MemoryRepository(AuthoringRegistry state) {
            this.state = state;
        }

        @Override
        public AuthoringRegistry load() {
            return state;
        }

        @Override
        public void save(AuthoringRegistry registry) throws IOException {
            if (fail) {
                throw new IOException("deliberate failure");
            }
            saves++;
            state = registry;
        }
    }
}
