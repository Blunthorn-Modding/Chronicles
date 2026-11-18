package io.github.chronicle.lastcouncil.conflicts;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import io.github.chronicle.lastcouncil.server.ConflictOperations;
import io.github.chronicle.lastcouncil.treaties.TreatyService;
import org.junit.jupiter.api.io.TempDir;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.chronicle.lastcouncil.conflicts.ConflictService.*;

class ConflictBudgetTest {
    private static final int LIMIT = 8 * 1024 * 1024;
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();

    private static final class Store implements Storage {
        byte[] bytes;
        int writes;
        public byte[] load() { return bytes; }
        public void save(byte[] value) { bytes = value.clone(); writes++; }
    }

    @TempDir Path dir;

    @Test void agreementPreflightRejectsARealOversizedTransitionBeforeIntent() throws Exception {
        Store templateStore = new Store();
        ConflictService template = new ConflictService(templateStore);
        List<House> houses = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            houses.add(new House("house:h" + i, new UUID(0, i + 1), "😀".repeat(80)));
        }
        UUID templateId = new UUID(1, 1);
        template.declare(templateId, new UUID(2, 1), houses.get(0), houses.get(1), "Road");
        for (int i = 2; i < houses.size(); i++) {
            template.join(templateId, template.revision(), houses.get(i), houses.get(0).houseId());
        }
        while (template.revision() < 127) {
            long revision = template.revision();
            template.change(templateId, revision, houses.get(2),
                    houses.get((revision - 15) % 2 == 0 ? 1 : 0).houseId());
        }
        Set<String> signers = new HashSet<>();
        houses.forEach(h -> signers.add(h.houseId()));
        template.recordAgreement(templateId, 127, new UUID(3, 1), new UUID(4, 1),
                127, AgreementKind.PEACE, signers);
        JsonObject ended = JsonParser.parseString(new String(templateStore.bytes, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("conflicts").get(0).getAsJsonObject();
        int endedLength = size(ended);
        UUID activeId = new UUID(1, 999), activeRequest = new UUID(2, 999);
        UUID chancellor = new UUID(3, 1), treatyId = new UUID(4, 1);
        JsonObject active = null;
        int endedCount = 0, prefix = 0;
        // Find an actual agreement transition crossing the real UTF-8 byte limit.
        // Generate each plausible successor through the service, including its
        // event and snapshot, rather than approximating their JSON structure.
        for (int n = 15; n < 127 && active == null; n++) {
            JsonObject before = prefix(ended, n, activeId, activeRequest);
            int beforeSize = size(before);
            for (int count = 1; count < 63; count++) {
                long priorSize = ledgerSize(count, endedLength, beforeSize, n);
                if (priorSize > LIMIT || LIMIT - priorSize > 20_000) continue;
                JsonObject after = agreementSuccessor(before, n, activeId, chancellor, treatyId, signers);
                if (ledgerSize(count, endedLength, size(after), n + 1) > LIMIT) {
                    active = before; endedCount = count; prefix = n; break;
                }
            }
        }
        assertNotNull(active, "fixture must straddle the real 8 MiB boundary");
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("revision", endedCount * 128L + prefix);
        JsonArray conflicts = new JsonArray();
        for (int i = 0; i < endedCount; i++) {
            JsonObject copy = ended.deepCopy();
            setIds(copy, new UUID(1, i + 2), new UUID(2, i + 2));
            conflicts.add(copy);
        }
        conflicts.add(active);
        root.add("conflicts", conflicts);
        Store store = new Store();
        store.bytes = (root + "\n").getBytes(StandardCharsets.UTF_8);
        assertTrue(store.bytes.length <= LIMIT);
        ConflictService service = new ConflictService(store); // strict replay validates the entire fixture
        assertEquals(endedCount + 1, service.records().size());
        byte[] original = store.bytes.clone();
        long revision = service.revision();
        List<View> records = service.records();
        List<TreatyService.Party> parties = houses.stream()
                .map(h -> new TreatyService.Party(h.houseId(), h.owner(), h.publicName())).toList();
        TreatyService treaties = new TreatyService(dir.resolve("treaties.json"));
        var document = new TreatyService.Document(treatyId, 1, "Peace", "Promises",
                TreatyService.Visibility.PRIVATE, parties, TreatyService.Purpose.PEACE,
                new TreatyService.ConflictLink(activeId, prefix));
        treaties.create(treatyId, houses.get(0).owner(), document);
        Set<UUID> present = new HashSet<>();
        for (House house : houses) {
            treaties.sign(treatyId, 1, house.owner());
            present.add(house.owner());
        }
        present.add(chancellor);
        var treatyBefore = treaties.get(treatyId);
        assertEquals(TreatyService.Status.DRAFT, treatyBefore.status());
        assertEquals(16, treatyBefore.signatures().size());
        Path operationPath = dir.resolve("operations.json");
        var operations = new ConflictOperations(operationPath);
        byte[] operationBefore = Files.readAllBytes(operationPath);
        var operation = new ConflictOperations.Operation(ConflictOperations.Kind.AGREEMENT,
                new UUID(5, 1), chancellor, null, null, "", List.of(), document, present, prefix);
        IOException failure = assertThrows(IOException.class,
                () -> operations.begin(operation, service, treaties));
        assertEquals("Conflict data too large", failure.getMessage());
        assertNull(operations.pending());
        assertArrayEquals(operationBefore, Files.readAllBytes(operationPath));
        assertEquals(treatyBefore, treaties.get(treatyId));
        assertEquals(TreatyService.Status.DRAFT, treaties.get(treatyId).status());
        assertEquals(0, store.writes);
        assertArrayEquals(original, store.bytes);
        assertEquals(revision, service.revision());
        assertEquals(records, service.records());
    }

    private static JsonObject agreementSuccessor(JsonObject before, int revision, UUID id,
            UUID chancellor, UUID treatyId, Set<String> signers) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("revision", revision);
        JsonArray entries = new JsonArray();
        entries.add(before.deepCopy());
        root.add("conflicts", entries);
        Store store = new Store();
        store.bytes = (root + "\n").getBytes(StandardCharsets.UTF_8);
        ConflictService service = new ConflictService(store);
        service.recordAgreement(id, revision, chancellor, treatyId, revision, AgreementKind.PEACE, signers);
        return JsonParser.parseString(new String(store.bytes, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("conflicts").get(0).getAsJsonObject();
    }

    private static JsonObject prefix(JsonObject ended, int n, UUID id, UUID request) {
        JsonObject result = new JsonObject();
        JsonArray history = new JsonArray(), snapshots = new JsonArray();
        JsonArray allHistory = ended.getAsJsonArray("history"), allSnapshots = ended.getAsJsonArray("snapshots");
        for (int i = 0; i < n; i++) {
            history.add(allHistory.get(i).deepCopy());
            snapshots.add(allSnapshots.get(i).deepCopy());
        }
        result.add("record", snapshots.get(n - 1).deepCopy());
        result.add("history", history);
        result.add("snapshots", snapshots);
        setIds(result, id, request);
        return result;
    }

    private static void setIds(JsonObject entry, UUID id, UUID request) {
        updateIds(entry.getAsJsonObject("record"), id, request);
        for (JsonElement snapshot : entry.getAsJsonArray("snapshots")) {
            updateIds(snapshot.getAsJsonObject(), id, request);
        }
    }

    private static void updateIds(JsonObject record, UUID id, UUID request) {
        record.addProperty("id", id.toString());
        record.addProperty("requestId", request.toString());
    }

    private static int size(JsonElement value) {
        return JSON.toJson(value).getBytes(StandardCharsets.UTF_8).length;
    }

    private static long ledgerSize(int count, int endedSize, int activeSize, int prefix) {
        String header = "{\"schema\":1,\"revision\":" + (count * 128L + prefix) + ",\"conflicts\":[";
        return header.length() + (long) count * endedSize + count + activeSize + 3; // ]} and newline
    }
}
