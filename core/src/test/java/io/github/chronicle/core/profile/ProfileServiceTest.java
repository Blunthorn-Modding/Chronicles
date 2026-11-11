package io.github.chronicle.core.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileServiceTest {
    private static final UUID OWNER = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultReadIsReadOnlyAndUpdatesUseOptimisticRevisions() throws IOException {
        Path profiles = temporaryDirectory.resolve("profiles");
        ProfileService service = new ProfileService(new ProfileStore(profiles));

        CharacterProfile defaultProfile = service.get(OWNER, "  Nova  ");
        assertEquals(new CharacterProfile(OWNER, "Nova", "", "", 0), defaultProfile);
        assertFalse(Files.exists(profiles));

        CharacterProfile updated = service.update(OWNER, 0, "Nova", "she/her", "A scout");
        assertEquals(1, updated.revision());
        assertThrows(IllegalStateException.class, () -> service.update(OWNER, 0, "Nova", "", ""));
        assertEquals(updated, new ProfileService(new ProfileStore(profiles)).get(OWNER, "Ignored"));
    }

    @Test
    void saveFailureDoesNotPublishAnUpdate() throws IOException {
        Path blockedDirectory = temporaryDirectory.resolve("blocked");
        ProfileService service = new ProfileService(new ProfileStore(blockedDirectory));
        Files.writeString(blockedDirectory, "not a directory");

        assertThrows(IOException.class, () -> service.update(OWNER, 0, "Nova", "", ""));
        assertFalse(Files.exists(blockedDirectory.resolve(OWNER + ".json")));
        assertEquals(0, service.get(OWNER, "Nova").revision());
    }
}
