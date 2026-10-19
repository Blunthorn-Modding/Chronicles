package io.github.chronicle.core.play;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayStoreTest {
    @TempDir Path directory;
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GM = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final ObservationCatalog.Definition DEFINITION = new ObservationCatalog.Definition(
            "quiet_below:cue", "Cue", "The stone answers.", "", ObservationCatalog.Audience.PARTICIPANTS);

    @Test
    void restartsRoundTripClaimsAndPreservePreviousBackup() throws IOException {
        Path file = directory.resolve("play-runtime.json");
        PlayStore store = new PlayStore(file);
        PlayState first = state(1, 40, PlayState.Status.CLAIMED);
        PlayState second = state(2, 80, PlayState.Status.DELIVERED);
        store.save(first);
        store.save(second);
        assertEquals(second, new PlayStore(file).load());
        assertEquals(first, new PlayStore(file.resolveSibling("play-runtime.json.bak")).load());
    }

    @Test
    void strictSchemaRejectsDuplicatesUnknownFieldsAndFutureVersionsWithoutReplacingEvidence() throws IOException {
        Path file = directory.resolve("play-runtime.json");
        PlayStore store = new PlayStore(file);
        String duplicate = "{\"schemaVersion\":1,\"revision\":0,\"elapsedTicks\":0,\"participants\":[],\"bindings\":[],\"deliveries\":[],\"revision\":1}";
        Files.writeString(file, duplicate);
        assertThrows(IOException.class, store::load);
        assertThrows(IOException.class, () -> store.save(PlayState.empty()));
        assertEquals(duplicate, Files.readString(file));

        Files.writeString(file, "{\"schemaVersion\":2,\"revision\":0,\"elapsedTicks\":0,\"participants\":[],\"bindings\":[],\"deliveries\":[]}");
        assertThrows(IOException.class, () -> new PlayStore(file).load());
        Files.writeString(file, "{\"schemaVersion\":1,\"revision\":0,\"elapsedTicks\":0,\"participants\":[],\"bindings\":[],\"deliveries\":[],\"unknown\":true}");
        assertThrows(IOException.class, () -> new PlayStore(file).load());
        Files.writeString(file, "{\"schemaVersion\":1,\"revision\":0,\"elapsedTicks\":0,\"participants\":[\"11111111-1111-1111-1111-111111111111\",\"11111111-1111-1111-1111-111111111111\"],\"bindings\":[],\"deliveries\":[]}");
        assertThrows(IOException.class, () -> new PlayStore(file).load());
    }

    @Test
    void rejectsMalformedReceiptAndPinnedStateRelationships() throws IOException {
        Path file = directory.resolve("play-runtime.json");
        String duplicateRecipients = "{\"schemaVersion\":1,\"revision\":1,\"elapsedTicks\":10,\"participants\":[\"11111111-1111-1111-1111-111111111111\"],\"bindings\":[],\"deliveries\":[{\"id\":\"quiet_below:cue\",\"status\":\"CLAIMED\",\"source\":\"MANUAL\",\"actor\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"session\":1,\"elapsedTicks\":10,\"recipients\":[\"11111111-1111-1111-1111-111111111111\",\"11111111-1111-1111-1111-111111111111\"]}]}";
        Files.writeString(file, duplicateRecipients);
        assertThrows(IOException.class, () -> new PlayStore(file).load());

        String futureBinding = "{\"schemaVersion\":1,\"revision\":1,\"elapsedTicks\":10,\"participants\":[],\"bindings\":[{\"id\":\"quiet_below:cue\",\"revision\":2,\"definitionFingerprint\":\"" + DEFINITION.fingerprint() + "\",\"mode\":\"MANUAL\",\"regionId\":\"\",\"regionRevision\":0,\"armedAtTicks\":0,\"delayTicks\":0,\"requiredFlag\":\"\"}],\"deliveries\":[]}";
        Files.writeString(file, futureBinding);
        assertThrows(IOException.class, () -> new PlayStore(file).load());
    }

    @Test
    void refusesExternalEditsAndPresenceChangesAfterObservation() throws IOException {
        Path file = directory.resolve("play-runtime.json");
        PlayStore store = new PlayStore(file);
        store.save(PlayState.empty());
        store.load();
        Files.writeString(file, "{\"schemaVersion\":1,\"revision\":1,\"elapsedTicks\":0,\"participants\":[],\"bindings\":[],\"deliveries\":[]}");
        assertThrows(IOException.class, () -> store.save(PlayState.empty()));

        Path absent = directory.resolve("created.json");
        PlayStore absentStore = new PlayStore(absent);
        absentStore.load();
        Files.writeString(absent, "{\"schemaVersion\":1,\"revision\":0,\"elapsedTicks\":0,\"participants\":[],\"bindings\":[],\"deliveries\":[]}");
        assertThrows(IOException.class, () -> absentStore.save(PlayState.empty()));
    }

    @Test
    void rejectsOversizedAndExcessivelyNestedFiles() throws IOException {
        Path oversized = directory.resolve("oversized.json");
        try (var writer = Files.newBufferedWriter(oversized)) {
            char[] chunk = new char[8192];
            java.util.Arrays.fill(chunk, ' ');
            long remaining = PlayStore.MAX_FILE_BYTES + 1;
            while (remaining > 0) {
                int count = (int) Math.min(remaining, chunk.length);
                writer.write(chunk, 0, count);
                remaining -= count;
            }
        }
        assertThrows(IOException.class, () -> new PlayStore(oversized).load());

        Path nested = directory.resolve("nested.json");
        Files.writeString(nested, "[".repeat(18) + "0" + "]".repeat(18));
        assertThrows(IOException.class, () -> new PlayStore(nested).load());
    }

    private static PlayState state(long revision, long elapsedTicks, PlayState.Status status) {
        PlayState.Binding binding = new PlayState.Binding(DEFINITION.id(), 1, DEFINITION.fingerprint(), PlayState.Mode.MANUAL,
                "", 0, 0, 0, "");
        PlayState.Delivery delivery = new PlayState.Delivery(DEFINITION.id(), status, PlayState.DeliverySource.MANUAL,
                GM, 1, 40, List.of(PLAYER));
        return new PlayState(revision, elapsedTicks, Set.of(PLAYER), Map.of(binding.id(), binding), Map.of(delivery.id(), delivery));
    }
}
