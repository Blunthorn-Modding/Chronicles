package io.github.chronicle.core.authoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoringStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsEverySiteTypeAndKeepsThePreviousValidSnapshot() throws IOException {
        Path file = temporaryDirectory.resolve("authoring.json");
        AuthoringStore store = new AuthoringStore(file);
        AuthoringRegistry first = new AuthoringRegistry(1, Map.of("cave", new AuthoringSite.Region("cave", "minecraft:overworld", 1,
                new BlockPosition(5, 20, -1), new BlockPosition(-3, -10, 9))));
        AuthoringRegistry second = new AuthoringRegistry(2, Map.of(
                "cave", new AuthoringSite.Region("cave", "minecraft:overworld", 1, new BlockPosition(-3, -10, -1), new BlockPosition(5, 20, 9)),
                "arrival", new AuthoringSite.Location("arrival", "minecraft:overworld", 2, 1.5, 63.25, -2.75, 90.0f, -15.0f),
                "heart", new AuthoringSite.Anchor("heart", "minecraft:overworld", 2, new BlockPosition(3, 4, 5), "minecraft:sculk_catalyst")));

        store.save(first);
        store.save(second);

        assertEquals(second, new AuthoringStore(file).load());
        assertEquals(first, new AuthoringStore(file.resolveSibling("authoring.json.bak")).load());
    }

    @Test
    void rejectsUnknownMissingFutureBadAndTrailingDataWithoutReplacingIt() throws IOException {
        Path file = temporaryDirectory.resolve("authoring.json");
        AuthoringStore store = new AuthoringStore(file);
        String malformed = "{\"schemaVersion\":1,\"revision\":0,\"sites\":[],\"unknown\":true}";
        Files.writeString(file, malformed);
        assertThrows(IOException.class, store::load);
        assertThrows(IOException.class, () -> store.save(AuthoringRegistry.empty()));
        assertEquals(malformed, Files.readString(file));

        Files.writeString(file, "{\"schemaVersion\":2,\"revision\":0,\"sites\":[]}");
        assertThrows(IOException.class, store::load);
        Files.writeString(file, "{\"schemaVersion\":1,\"revision\":0,\"sites\":[]} null");
        assertThrows(IOException.class, store::load);
        Files.writeString(file, "{\"schemaVersion\":1,\"revision\":0,\"sites\":[{\"type\":\"location\",\"id\":\"spot\",\"dimension\":\"minecraft:overworld\",\"revision\":1,\"x\":\"bad\",\"y\":0,\"z\":0,\"yaw\":0,\"pitch\":0}]}");
        assertThrows(IOException.class, store::load);
    }

    @Test
    void corruptCurrentFileIsRefusedAndExistingEvidenceRemainsUntouched() throws IOException {
        Path file = temporaryDirectory.resolve("authoring.json");
        AuthoringStore store = new AuthoringStore(file);
        store.save(AuthoringRegistry.empty());
        Files.writeString(file, "{ definitely not authoring data }");
        String before = Files.readString(file);

        assertThrows(IOException.class, () -> store.save(AuthoringRegistry.empty()));
        assertEquals(before, Files.readString(file));
    }

    @Test
    void refusesAnExternalReplacementAfterLoad() throws IOException {
        Path file = temporaryDirectory.resolve("authoring.json");
        AuthoringStore store = new AuthoringStore(file);
        store.save(AuthoringRegistry.empty());
        store.load();
        Files.writeString(file, "{\"schemaVersion\":1,\"revision\":1,\"sites\":[]}");

        assertThrows(IOException.class, () -> store.save(AuthoringRegistry.empty()));
    }

    @Test
    void refusesExternalFileCreationOrDeletionAfterTheRegistryWasLoaded() throws IOException {
        Path created = temporaryDirectory.resolve("created.json");
        AuthoringStore absentStore = new AuthoringStore(created);
        absentStore.load();
        Files.writeString(created, "{\"schemaVersion\":1,\"revision\":0,\"sites\":[]}");
        assertThrows(IOException.class, () -> absentStore.save(AuthoringRegistry.empty()));

        Path removed = temporaryDirectory.resolve("removed.json");
        AuthoringStore presentStore = new AuthoringStore(removed);
        presentStore.save(AuthoringRegistry.empty());
        presentStore.load();
        Files.delete(removed);
        assertThrows(IOException.class, () -> presentStore.save(AuthoringRegistry.empty()));
    }

    @Test
    void rejectsDuplicateMembersAtEveryObjectLevelWithoutReplacingEvidence() throws IOException {
        Path file = temporaryDirectory.resolve("duplicates.json");
        AuthoringStore store = new AuthoringStore(file);
        String duplicateDimension = "{\"schemaVersion\":1,\"revision\":1,\"sites\":[{\"type\":\"anchor\",\"id\":\"heart\",\"dimension\":\"minecraft:overworld\",\"dimension\":\"minecraft:the_nether\",\"revision\":1,\"position\":{\"x\":0,\"y\":0,\"z\":0},\"expectedBlock\":\"minecraft:sculk\"}]}";
        Files.writeString(file, duplicateDimension);
        assertThrows(IOException.class, store::load);
        assertEquals(duplicateDimension, Files.readString(file));

        String duplicatePositionAndBlock = "{\"schemaVersion\":1,\"revision\":1,\"sites\":[{\"type\":\"anchor\",\"id\":\"heart\",\"dimension\":\"minecraft:overworld\",\"revision\":1,\"position\":{\"x\":0,\"x\":1,\"y\":0,\"z\":0},\"expectedBlock\":\"minecraft:sculk\",\"expectedBlock\":\"minecraft:stone\"}]}";
        Files.writeString(file, duplicatePositionAndBlock);
        assertThrows(IOException.class, store::load);
        assertEquals(duplicatePositionAndBlock, Files.readString(file));
    }
}
