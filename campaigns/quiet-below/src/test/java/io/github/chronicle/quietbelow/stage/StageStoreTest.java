package io.github.chronicle.quietbelow.stage;

import io.github.chronicle.core.campaign.Actor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageStoreTest {
    private static final Actor GM = new Actor("GM", "123e4567-e89b-12d3-a456-426614174000");
    @TempDir Path temporaryDirectory;

    @Test
    void roundTripsAndMaintainsPreviousBackup() throws IOException {
        Path file = temporaryDirectory.resolve("stages.json");
        StageStore store = new StageStore(file);
        StageState first = state("first");
        StageState second = state("second");

        store.save(first);
        store.save(second);

        assertEquals(second, store.load());
        assertEquals(first, StageStore.decode(Files.readString(file.resolveSibling("stages.json.bak"))));
    }

    @Test
    void rejectsFutureCorruptTrailingAndUnknownDataWithoutReplacingEvidence() throws IOException {
        Path file = temporaryDirectory.resolve("stages.json");
        StageStore store = new StageStore(file);
        for (String invalid : List.of(
                "{\"schemaVersion\":2,\"zones\":{},\"operation\":null,\"history\":[],\"nextSequence\":0}",
                "{\"schemaVersion\":1,\"zones\":{},\"operation\":null,\"history\":[],\"nextSequence\":0} trailing",
                "{\"schemaVersion\":1,\"zones\":{},\"operation\":null,\"history\":[],\"nextSequence\":0,\"extra\":true}")) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, store::load);
            assertThrows(IOException.class, () -> store.save(StageState.newWorld()));
            assertEquals(invalid, Files.readString(file));
        }
    }

    @Test
    void boundedModelRejectsOversizedZoneAndAuditCollections() {
        Map<String, StageZone> zones = new java.util.LinkedHashMap<>();
        for (int index = 0; index <= StageValidation.MAX_ZONES; index++) zones.put("z" + index, zone("z" + index));
        assertThrows(IllegalArgumentException.class,
                () -> new StageState(1, zones, null, List.of(), 0));

        List<StageAuditEntry> history = new java.util.ArrayList<>();
        for (int index = 0; index <= StageValidation.MAX_HISTORY; index++) {
            history.add(new StageAuditEntry(index, "2026-10-19T12:00:00Z", GM, "stage.apply", "z", "", ""));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new StageState(1, Map.of(), null, history, history.size()));
    }

    @Test
    void serializedIntentContainsRegistryNamesPropertiesActorAndProgress() {
        StageState state = state("proof");
        String json = StageStore.encode(state);
        assertTrue(json.contains("minecraft:stone"));
        assertTrue(json.contains("axis"));
        assertTrue(json.contains(GM.uuid()));
        assertTrue(json.contains("progress"));
        assertTrue(json.contains("operationId"));
    }

    @Test
    void rejectsOperationThatDoesNotExactlyMatchApprovedZoneCapture() {
        JsonObject changedDimension = JsonParser.parseString(StageStore.encode(state("proof"))).getAsJsonObject();
        changedDimension.getAsJsonObject("operation").addProperty("dimension", "minecraft:the_nether");
        assertThrows(IllegalArgumentException.class, () -> StageStore.decode(changedDimension.toString()));

        JsonObject changedPosition = JsonParser.parseString(StageStore.encode(state("proof"))).getAsJsonObject();
        changedPosition.getAsJsonObject("operation").getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("x", 99);
        assertThrows(IllegalArgumentException.class, () -> StageStore.decode(changedPosition.toString()));
    }

    private static StageState state(String zoneId) {
        StageZone zone = zone(zoneId);
        StageOperation operation = new StageOperation("123e4567-e89b-12d3-a456-426614174099", zoneId,
                "minecraft:overworld", zone.entries(), GM, "2026-10-19T12:00:00Z", StageDirection.APPLY,
                StageOperationStatus.APPLYING, 0, "ready");
        StageAuditEntry audit = new StageAuditEntry(0, "2026-10-19T12:00:00Z", GM,
                "stage.apply", zoneId, operation.operationId(), "positions=1");
        return new StageState(1, Map.of(zoneId, zone), operation, List.of(audit), 1);
    }

    private static StageZone zone(String id) {
        StageEntry entry = new StageEntry(new StagePosition(1, 64, 2),
                new StageBlockState("minecraft:stone", Map.of("axis", "y")),
                new StageBlockState("minecraft:sculk", Map.of()));
        return new StageZone(id, "minecraft:overworld", List.of(entry), GM, "2026-10-19T12:00:00Z");
    }
}
