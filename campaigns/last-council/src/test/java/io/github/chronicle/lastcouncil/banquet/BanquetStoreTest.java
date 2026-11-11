package io.github.chronicle.lastcouncil.banquet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static io.github.chronicle.lastcouncil.banquet.BanquetState.*;
import static org.junit.jupiter.api.Assertions.*;

class BanquetStoreTest {
    private static final UUID HOST = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final UUID PLAYER = UUID.fromString("abcdefab-1234-1234-1234-123456789abc");
    private static final UUID DRINK = new UUID(0, 1), POISON = new UUID(0, 2), ANTIDOTE = new UUID(0, 3);
    private static final UUID ISSUE = new UUID(1, 1), ISSUE_POISON = new UUID(1, 2), ISSUE_ANTIDOTE = new UUID(1, 3);
    private static final UUID MIX = new UUID(2, 1), USE = new UUID(2, 2), CURE = new UUID(2, 3);
    private static final Timing TIMING = new Timing(5, 10);
    @TempDir Path directory;

    private static BanquetService prepare(BanquetRepository repo) throws IOException {
        BanquetService service = new BanquetService(repo);
        service.issue(0, ISSUE, HOST, DRINK, SupplyKind.DRINK);
        service.issue(1, ISSUE_POISON, HOST, POISON, SupplyKind.POISON);
        service.issue(2, ISSUE_ANTIDOTE, HOST, ANTIDOTE, SupplyKind.ANTIDOTE);
        service.contaminate(3, MIX, HOST, POISON, DRINK);
        service.drink(4, USE, PLAYER, DRINK, TIMING, RepeatPolicy.IGNORE);
        return service;
    }

    @Test void freshFileRestartReplayAndCure() throws Exception {
        Path path = directory.resolve("banquet.json");
        BanquetStore first = new BanquetStore(path);
        assertThrows(IOException.class, () -> first.save(BanquetState.empty()));
        assertEquals(BanquetState.empty(), first.load());
        assertFalse(Files.exists(path));
        BanquetService service = prepare(first);
        service.advance(6, Set.of(PLAYER));
        BanquetState before = service.snapshot();
        byte[] bytes = Files.readAllBytes(path);
        assertEquals(new Illness(0, 9), before.illnesses().get(PLAYER));
        BanquetService restarted = new BanquetService(new BanquetStore(path));
        assertEquals(before, restarted.snapshot());
        assertSame(restarted.snapshot(), restarted.drink(0, USE, PLAYER, DRINK, TIMING, RepeatPolicy.IGNORE));
        assertArrayEquals(bytes, Files.readAllBytes(path));
        BanquetState cured = restarted.antidote(before.revision(), CURE, PLAYER, ANTIDOTE);
        assertTrue(cured.illnesses().isEmpty());
        assertEquals(cured, new BanquetStore(path).load());
        assertSame(cured, restarted.antidote(0, CURE, PLAYER, ANTIDOTE));
    }

    @Test void serializationIsCanonicalAndFitsMaximumRetainedHistory() throws Exception {
        Map<UUID, Supply> supplies = new HashMap<>();
        Map<UUID, Operation> operations = new HashMap<>();
        for (int i = 0; i < MAX_SUPPLIES; i++) {
            UUID token = new UUID(0, i), issue = new UUID(1, i), use = new UUID(2, i);
            supplies.put(token, new Supply(SupplyKind.DRINK, HOST, null, PLAYER, use));
            operations.put(issue, new Operation(HOST, OperationType.ISSUE, token, null, SupplyKind.DRINK, null, null));
            operations.put(use, new Operation(PLAYER, OperationType.DRINK, token, null, null, TIMING, RepeatPolicy.IGNORE));
        }
        BanquetState full = new BanquetState(MAX_OPERATIONS, supplies, Map.of(), operations);
        byte[] encoded = BanquetStore.encode(full);
        assertTrue(encoded.length < BanquetStore.MAX_BYTES);
        assertEquals(full, BanquetStore.decode(encoded));
        assertArrayEquals(encoded, BanquetStore.encode(BanquetStore.decode(encoded)));
        Path path = directory.resolve("maximum.json");
        BanquetStore store = new BanquetStore(path);
        store.load(); store.save(full);
        assertEquals(full, new BanquetStore(path).load());
    }

    @Test void malformedShapeTypesAndHistoryCannotBeOverwritten() throws Exception {
        BanquetService service = prepare(new BanquetStore(directory.resolve("valid.json")));
        String good = new String(BanquetStore.encode(service.snapshot()), StandardCharsets.UTF_8);
        List<Consumer<JsonObject>> changes = List.of(
            r -> r.addProperty("schema", 2), r -> r.addProperty("schema", "1"),
            r -> r.addProperty("revision", 0), r -> r.addProperty("revision", -1), r -> r.addProperty("revision", 1.0),
            r -> r.addProperty("extra", true), r -> r.remove("operations"), r -> r.addProperty("supplies", "wrong"),
            r -> r.getAsJsonArray("supplies").get(0).getAsJsonObject().addProperty("issuer", HOST.toString().toUpperCase()),
            r -> r.getAsJsonArray("supplies").get(0).getAsJsonObject().addProperty("kind", "BOGUS"),
            r -> r.getAsJsonArray("supplies").add(r.getAsJsonArray("supplies").get(0).deepCopy()),
            r -> r.getAsJsonArray("operations").add(r.getAsJsonArray("operations").get(0).deepCopy()),
            r -> r.getAsJsonArray("illnesses").get(0).getAsJsonObject().addProperty("durationTicks", 0),
            r -> r.getAsJsonArray("illnesses").get(0).getAsJsonObject().addProperty("durationTicks", 100),
            r -> r.getAsJsonArray("illnesses").get(0).getAsJsonObject().addProperty("durationTicks", 9),
            r -> r.getAsJsonArray("illnesses").get(0).getAsJsonObject().addProperty("onsetTicks", 6),
            r -> r.getAsJsonArray("illnesses").get(0).getAsJsonObject().addProperty("onsetTicks", MAX_TICKS + 1),
            r -> r.getAsJsonArray("operations").remove(0),
            r -> r.getAsJsonArray("supplies").get(0).getAsJsonObject().addProperty("poisoner", PLAYER.toString())
        );
        Path path = directory.resolve("invalid.json");
        for (Consumer<JsonObject> change : changes) {
            JsonObject root = JsonParser.parseString(good).getAsJsonObject();
            change.accept(root);
            assertPreserved(path, root.toString().getBytes(StandardCharsets.UTF_8));
        }
        assertPreserved(path, good.replace("\"schema\":1", "\"schema\":1,\"schema\":1").getBytes(StandardCharsets.UTF_8));
        assertPreserved(path, "{} trailing".getBytes(StandardCharsets.UTF_8));
        assertPreserved(path, new byte[]{(byte) 0xc3, (byte) 0x28});
        assertPreserved(path, new byte[0]);
        assertThrows(IOException.class, () -> BanquetStore.decode(new byte[BanquetStore.MAX_BYTES + 1]));
    }

    private static void assertPreserved(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes);
        BanquetStore store = new BanquetStore(path);
        assertThrows(IOException.class, store::load);
        assertThrows(IOException.class, () -> store.save(BanquetState.empty()));
        assertArrayEquals(bytes, Files.readAllBytes(path));
    }

    @Test void externalEditsAndDeletionRefuseEvenReplayAndNoOpClock() throws Exception {
        Path path = directory.resolve("banquet.json");
        BanquetService service = prepare(new BanquetStore(path));
        BanquetState before = service.snapshot();
        Files.writeString(path, "external", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> service.drink(0, USE, PLAYER, DRINK, TIMING, RepeatPolicy.IGNORE));
        assertThrows(IOException.class, () -> service.advance(0, Set.of()));
        assertSame(before, service.snapshot());
        assertEquals("external", Files.readString(path));
        Files.delete(path);
        assertThrows(IOException.class, () -> service.antidote(before.revision(), CURE, PLAYER, ANTIDOTE));
        assertFalse(Files.exists(path));
        assertSame(before, service.snapshot());
    }

    @Test void competingStoresAndCreateRaceRetainFirstWriter() throws Exception {
        Path path = directory.resolve("banquet.json");
        BanquetService first = new BanquetService(new BanquetStore(path));
        BanquetService stale = new BanquetService(new BanquetStore(path));
        first.issue(0, ISSUE, HOST, DRINK, SupplyKind.DRINK);
        byte[] firstBytes = Files.readAllBytes(path);
        assertThrows(IOException.class, () -> stale.issue(0, ISSUE_POISON, HOST, POISON, SupplyKind.POISON));
        assertEquals(BanquetState.empty(), stale.snapshot());
        assertArrayEquals(firstBytes, Files.readAllBytes(path));
        BanquetService observed = new BanquetService(new BanquetStore(path));
        first.issue(1, ISSUE_POISON, HOST, POISON, SupplyKind.POISON);
        assertThrows(IOException.class, () -> observed.issue(1, ISSUE_ANTIDOTE, HOST, ANTIDOTE, SupplyKind.ANTIDOTE));
    }

    @Test void failedPersistenceDoesNotPublishMemoryAndRetryUsesSameOperation() throws Exception {
        class Repository implements BanquetRepository {
            BanquetState durable = BanquetState.empty(); boolean fail; int saves;
            public BanquetState load() { return durable; }
            public void verifyUnchanged() { }
            public void save(BanquetState next) throws IOException {
                if (fail) throw new IOException("simulated failed publication");
                durable = next; saves++;
            }
        }
        Repository repo = new Repository();
        BanquetService service = prepare(repo);
        BanquetState before = service.snapshot();
        repo.fail = true;
        assertThrows(IOException.class, () -> service.advance(6, Set.of(PLAYER)));
        assertThrows(IOException.class, () -> service.antidote(before.revision(), CURE, PLAYER, ANTIDOTE));
        assertSame(before, service.snapshot());
        assertSame(before, repo.durable);
        repo.fail = false;
        service.antidote(before.revision(), CURE, PLAYER, ANTIDOTE);
        int saves = repo.saves;
        service.antidote(0, CURE, PLAYER, ANTIDOTE);
        service.advance(100, Set.of(PLAYER));
        assertEquals(saves, repo.saves);
        assertSame(repo.durable, service.snapshot());
    }
}
