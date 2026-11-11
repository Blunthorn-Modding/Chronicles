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
import java.util.UUID;
import java.util.LinkedHashMap;

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
        Files.writeString(file, "{\"schemaVersion\":3,\"sessionNumber\":0,\"phase\":\"INACTIVE\",\"flags\":{},\"completedEvents\":[],\"history\":[],\"nextSequence\":0}");
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

    @Test
    void schemaOneReadPreservesOriginalBytesAndNextMutationUpgradesWithExactBackup() throws IOException {
        Path file = temporaryDirectory.resolve("campaign.json");
        String original = "{ \"schemaVersion\":1,\"sessionNumber\":1,\"phase\":\"ACTIVE\",\"flags\":{\"quiet:ready\":true},\"completedEvents\":[\"quiet:old\"],\"history\":[],\"nextSequence\":4 }\n";
        Files.writeString(file, original);
        CampaignStore store = new CampaignStore(file);
        CampaignState loaded = store.load();
        assertEquals(1, loaded.schemaVersion());
        assertEquals(0, loaded.flagOffVersion("quiet:ready"));
        assertTrue(loaded.eventFlagOutcomes().isEmpty());
        assertEquals(original, Files.readString(file));
        CampaignService service = new CampaignService(store, new CampaignEvents(), java.time.Clock.systemUTC());
        service.setFlag(new Actor("GM", null), "quiet:ready", false);
        assertEquals(2, store.load().schemaVersion());
        assertEquals(4, store.load().flagOffVersion("quiet:ready"));
        assertEquals(loaded.completedEvents(), store.load().completedEvents());
        assertEquals(original, Files.readString(file.resolveSibling("campaign.json.bak")));
    }

    @Test
    void progressionMetadataDefensivelyCopiesAndRoundTripsIndependentlyOfAudit() throws IOException {
        UUID id = UUID.randomUUID();
        Map<String, Boolean> changes = new LinkedHashMap<>(Map.of("quiet:ready", false));
        FlagOutcomeReceipt receipt = new FlagOutcomeReceipt(id, "quiet:event", "a".repeat(64), changes);
        Map<UUID, FlagOutcomeReceipt> receipts = new LinkedHashMap<>(Map.of(id, receipt));
        Map<String, Long> versions = new LinkedHashMap<>(Map.of("quiet:ready", 7L));
        CampaignState state = new CampaignState(2, 1, SessionPhase.ACTIVE, Map.of("quiet:ready", true), Set.of(), List.of(), 8, versions, receipts);
        changes.clear();
        versions.clear();
        receipts.clear();
        assertEquals(Map.of("quiet:ready", false), receipt.changes());
        assertEquals(7, state.flagOffVersion("quiet:ready"));
        assertEquals(receipt, state.eventFlagOutcomes().get(id));
        assertThrows(UnsupportedOperationException.class, () -> receipt.changes().clear());
        assertThrows(UnsupportedOperationException.class, () -> state.flagOffVersions().clear());
        assertThrows(UnsupportedOperationException.class, () -> state.eventFlagOutcomes().clear());
        CampaignStore store = new CampaignStore(temporaryDirectory.resolve("campaign.json"));
        store.save(state);
        assertEquals(state, store.load());
        assertTrue(store.load().history().isEmpty());
    }

    @Test
    void rejectsInvalidProgressionVersionsAndReceiptIdentity() {
        UUID id = UUID.randomUUID();
        FlagOutcomeReceipt receipt = new FlagOutcomeReceipt(id, "quiet:event", "a".repeat(64), Map.of("quiet:ready", true));
        assertThrows(IllegalArgumentException.class, () -> new CampaignState(2, 1, SessionPhase.ACTIVE,
                Map.of("quiet:ready", true), Set.of(), List.of(), 8, Map.of("quiet:ready", 8L), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new CampaignState(2, 1, SessionPhase.ACTIVE,
                Map.of("quiet:ready", true), Set.of(), List.of(), 8, Map.of("quiet:unknown", 7L), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new CampaignState(2, 1, SessionPhase.ACTIVE,
                Map.of(), Set.of(), List.of(), 8, Map.of(), Map.of(UUID.randomUUID(), receipt)));
        assertThrows(IllegalArgumentException.class, () -> new CampaignState(1, 1, SessionPhase.ACTIVE,
                Map.of("quiet:ready", true), Set.of(), List.of(), 8, Map.of("quiet:ready", 7L), Map.of()));
    }

    @Test
    void strictSchemaRejectsUnexpectedFieldsAndDuplicateOutcomeOperations() throws IOException {
        Path file = temporaryDirectory.resolve("campaign.json");
        CampaignStore store = new CampaignStore(file);
        store.save(CampaignState.newWorld());
        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        json.addProperty("unknown", true);
        Files.writeString(file, json.toString());
        assertThrows(IOException.class, store::load);
        json.remove("unknown");
        UUID id = UUID.randomUUID();
        com.google.gson.JsonObject receipt = new com.google.gson.JsonObject();
        receipt.addProperty("operationId", id.toString());
        receipt.addProperty("eventId", "quiet:event");
        receipt.addProperty("definitionFingerprint", "a".repeat(64));
        com.google.gson.JsonObject changes = new com.google.gson.JsonObject();
        changes.addProperty("quiet:ready", true);
        receipt.add("changes", changes);
        json.getAsJsonArray("eventFlagOutcomes").add(receipt);
        json.getAsJsonArray("eventFlagOutcomes").add(receipt.deepCopy());
        Files.writeString(file, json.toString());
        assertThrows(IOException.class, store::load);
        json.getAsJsonArray("eventFlagOutcomes").remove(1);
        receipt.addProperty("unexpected", true);
        Files.writeString(file, json.toString());
        assertThrows(IOException.class, store::load);
    }

    @Test
    void fullLedgerWithMaximumPatchesFitsTheExistingFileBound() throws IOException {
        Map<String, Boolean> patch = new LinkedHashMap<>();
        for (int i = 0; i < FlagOutcomeReceipt.MAX_FLAG_CHANGES; i++) {
            String suffix = "flag_" + i;
            patch.put("quiet:" + "x".repeat(128 - 6 - suffix.length()) + suffix, true);
        }
        Map<UUID, FlagOutcomeReceipt> receipts = new LinkedHashMap<>();
        for (int i = 0; i < CampaignState.MAX_EVENT_FLAG_OUTCOMES; i++) {
            UUID id = new UUID(0, i);
            receipts.put(id, new FlagOutcomeReceipt(id, "quiet:" + "e".repeat(122), "a".repeat(64), patch));
        }
        CampaignState state = new CampaignState(2, 1, SessionPhase.ACTIVE, patch, Set.of(), List.of(), 0, Map.of(), receipts);
        Path file = temporaryDirectory.resolve("campaign.json");
        CampaignStore store = new CampaignStore(file);
        store.save(state);
        assertTrue(Files.size(file) < CampaignStore.MAX_FILE_BYTES);
        assertEquals(state, store.load());
        UUID overflow = UUID.randomUUID();
        receipts.put(overflow, new FlagOutcomeReceipt(overflow, "quiet:event", "a".repeat(64), patch));
        assertThrows(IllegalArgumentException.class, () -> new CampaignState(2, 1, SessionPhase.ACTIVE, patch, Set.of(), List.of(), 0, Map.of(), receipts));
    }

    private static CampaignState stateWithAudit() {
        AuditEntry entry = new AuditEntry(0, Instant.parse("2026-10-18T12:00:00Z").toString(),
                new Actor("Game Master", null), "session-start", 1, "session", Map.of("number", "1"));
        return new CampaignState(1, 1, SessionPhase.ACTIVE, Map.of("quiet:ready", true), Set.of("quiet:intro"), List.of(entry), 1);
    }
}
