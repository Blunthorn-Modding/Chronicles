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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileStoreTest {
    private static final UUID OWNER = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID OTHER_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsAcrossStoreRestartAndRetainsPreviousSnapshotAsBackup() throws IOException {
        ProfileStore store = new ProfileStore(temporaryDirectory.resolve("profiles"));
        CharacterProfile first = new CharacterProfile(OWNER, "Nova", "she/her", "First card", 1);
        CharacterProfile second = new CharacterProfile(OWNER, "Nova", "she/they", "Second card", 2);

        store.save(first);
        store.save(second);

        assertEquals(second, new ProfileStore(temporaryDirectory.resolve("profiles")).load(OWNER).orElseThrow());
        Path profiles = temporaryDirectory.resolve("profiles");
        Path backupDirectory = temporaryDirectory.resolve("backup-copy");
        Files.createDirectories(backupDirectory);
        Files.copy(profiles.resolve(OWNER + ".json.bak"), backupDirectory.resolve(OWNER + ".json"));
        assertEquals(first, new ProfileStore(backupDirectory).load(OWNER).orElseThrow());
    }

    @Test
    void rejectsOwnerMismatchAndStrictCorruptOrFutureDataWithoutChangingIt() throws IOException {
        Path profiles = temporaryDirectory.resolve("profiles");
        ProfileStore store = new ProfileStore(profiles);
        new ProfileStore(profiles).save(new CharacterProfile(OTHER_OWNER, "Other", "", "", 1));
        Files.copy(profiles.resolve(OTHER_OWNER + ".json"), profiles.resolve(OWNER + ".json"));
        assertThrows(IOException.class, () -> store.load(OWNER));

        String future = "{\"schemaVersion\":2,\"owner\":\"" + OWNER + "\",\"name\":\"Nova\",\"pronouns\":\"\",\"description\":\"\",\"revision\":0}";
        Files.writeString(profiles.resolve(OWNER + ".json"), future);
        assertThrows(IOException.class, () -> store.load(OWNER));
        assertThrows(IOException.class, () -> store.save(new CharacterProfile(OWNER, "Replacement", "", "", 1)));
        assertEquals(future, Files.readString(profiles.resolve(OWNER + ".json")));
        String trailing = "{\"schemaVersion\":1,\"owner\":\"" + OWNER + "\",\"name\":\"Nova\",\"pronouns\":\"\",\"description\":\"\",\"revision\":0} {} ";
        Files.writeString(profiles.resolve(OWNER + ".json"), trailing);
        assertThrows(IOException.class, () -> store.load(OWNER));
        assertEquals(trailing, Files.readString(profiles.resolve(OWNER + ".json")));
    }

    @Test
    void absentProfileDoesNotCreateAFileAndOversizeProfileIsRejected() throws IOException {
        Path profiles = temporaryDirectory.resolve("profiles");
        ProfileStore store = new ProfileStore(profiles);
        assertTrue(store.load(OWNER).isEmpty());
        assertFalse(Files.exists(profiles));

        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve(OWNER + ".json"), "x".repeat((int) ProfileStore.MAX_FILE_BYTES + 1));
        assertThrows(IOException.class, () -> store.load(OWNER));
    }
}
