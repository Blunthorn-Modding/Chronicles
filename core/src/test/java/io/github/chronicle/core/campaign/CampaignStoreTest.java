package io.github.chronicle.core.campaign;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsStateAndRetainsThePriorValidFileAsBackup() throws IOException {
        Path file = temporaryDirectory.resolve("campaign.json");
        CampaignStore store = new CampaignStore(file);
        CampaignState first = new CampaignState(1, 1, SessionPhase.ACTIVE, Map.of("quiet:ready", true), Set.of(), List.of(), 0);
        CampaignState second = stateWithAudit();

        store.save(first);
        store.save(second);

        assertEquals(second, store.load());
        assertEquals(first, new CampaignStore(file.resolveSibling("campaign.json.bak")).load());
    }

    @Test
    void absentFileStartsANewWorldButCorruptAndFutureFilesAreRejected() throws IOException {
        Path file = temporaryDirectory.resolve("campaign.json");
        CampaignStore store = new CampaignStore(file);
        assertEquals(CampaignState.newWorld(), store.load());

        Files.writeString(file, "{not json");
        assertThrows(IOException.class, store::load);
        Files.writeString(file, "{\"schemaVersion\":1,\"sessionNumber\":0,\"phase\":\"INACTIVE\",\"flags\":{},\"completedEvents\":[],\"history\":[],\"nextSequence\":0} {} ");
        assertThrows(IOException.class, store::load);
        Files.writeString(file, "{\"schemaVersion\":2,\"sessionNumber\":0,\"phase\":\"INACTIVE\",\"flags\":{},\"completedEvents\":[],\"history\":[],\"nextSequence\":0}");
        assertThrows(IOException.class, store::load);
    }

    @Test
    void rejectsOversizeAndInvalidPersistedLimitsWithoutReplacingTheFile() throws IOException {
        Path file = temporaryDirectory.resolve("campaign.json");
        CampaignStore store = new CampaignStore(file);
        Files.writeString(file, "x".repeat((int) CampaignStore.MAX_FILE_BYTES + 1));
        assertThrows(IOException.class, store::load);

        Files.writeString(file, "{\"schemaVersion\":1,\"sessionNumber\":0,\"phase\":\"INACTIVE\",\"flags\":{\"invalid\":true},\"completedEvents\":[],\"history\":[],\"nextSequence\":0}");
        assertThrows(IOException.class, () -> store.save(CampaignState.newWorld()));
        assertTrue(Files.readString(file).contains("invalid"));
    }

    @Test
    void modelDefensivelyCopiesAndRejectsInvalidBounds() {
        Map<String, Boolean> flags = new java.util.LinkedHashMap<>(Map.of("quiet:ready", true));
        CampaignState state = new CampaignState(1, 0, SessionPhase.INACTIVE, flags, Set.of(), List.of(), 0);
        flags.put("quiet:later", false);
        assertFalse(state.flags().containsKey("quiet:later"));
        assertFalse(state.flag("quiet:unknown"));
        assertThrows(IllegalArgumentException.class, () -> new CampaignState(1, 0, SessionPhase.INACTIVE,
                Map.of("bad", true), Set.of(), List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> new CampaignState(1, 1_000_001, SessionPhase.ACTIVE,
                Map.of(), Set.of(), List.of(), 0));
    }

    private static CampaignState stateWithAudit() {
        AuditEntry entry = new AuditEntry(0, Instant.parse("2026-10-18T12:00:00Z").toString(),
                new Actor("Game Master", null), "session-start", 1, "session", Map.of("number", "1"));
        return new CampaignState(1, 1, SessionPhase.ACTIVE, Map.of("quiet:ready", true), Set.of("quiet:intro"), List.of(entry), 1);
    }
}
