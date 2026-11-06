package io.github.chronicle.core.ballots;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BallotStoreRegressionTest {
    @TempDir Path directory;
    private static final UUID ACTOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static BallotState closedState() {
        BallotDefinition definition = new BallotDefinition(UUID.randomUUID(), "test", "Question",
                List.of(new BallotOption("yes", "Yes", true), new BallotOption("no", "No", false)),
                Map.of("a", new BallotElector(ACTOR, "A")), 1, BallotVisibility.PUBLIC);
        Ballot ballot = new Ballot(definition, ACTOR, "2026-01-01T00:00:00Z", BallotStatus.CLOSED,
                Map.of("a", "yes"), ACTOR, "2026-01-01T00:00:01Z", "");
        return new BallotState(2, Map.of(definition.id(), ballot));
    }
    private static JsonObject ledger() { return JsonParser.parseString(BallotStore.encode(closedState())).getAsJsonObject(); }
    private static JsonObject ballot(JsonObject root) { return root.getAsJsonArray("ballots").get(0).getAsJsonObject(); }
    private static void invalid(JsonObject root) { assertThrows(RuntimeException.class, () -> BallotStore.decode(root.toString())); }
    @Test void rejectsUnknownDuplicateMalformedAndNullFields() {
        JsonObject unknown = ledger(); unknown.addProperty("unexpected", true); invalid(unknown);
        String duplicate = BallotStore.encode(closedState()).replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1");
        assertThrows(RuntimeException.class, () -> BallotStore.decode(duplicate));
        JsonObject extraNested = ledger(); ballot(extraNested).getAsJsonObject("definition").addProperty("unknown", 1); invalid(extraNested);
        JsonObject fractional = ledger(); fractional.addProperty("revision", 1.5); invalid(fractional);
        JsonObject stringNumber = ledger(); stringNumber.addProperty("revision", "2"); invalid(stringNumber);
        JsonObject nullRevision = ledger(); nullRevision.add("revision", JsonNull.INSTANCE); invalid(nullRevision);
        JsonObject nullVotes = ledger(); ballot(nullVotes).add("votes", JsonNull.INSTANCE); invalid(nullVotes);
        JsonObject badBoolean = ledger(); ballot(badBoolean).getAsJsonObject("definition").getAsJsonArray("options").get(0).getAsJsonObject().addProperty("canPass", "true"); invalid(badBoolean);
        JsonObject duplicateId = ledger(); duplicateId.getAsJsonArray("ballots").add(ballot(duplicateId).deepCopy()); invalid(duplicateId);
    }
    @Test void rejectsNoncanonicalUuidsIncludingFinishedBy() {
        JsonObject actor = ledger(); ballot(actor).addProperty("openedBy", ACTOR.toString().toUpperCase(Locale.ROOT)); invalid(actor);
        JsonObject finished = ledger(); ballot(finished).addProperty("finishedBy", ACTOR.toString().toUpperCase(Locale.ROOT)); invalid(finished);
    }
    @Test void failedDecodeAndOversizeNeverGrantWritePermission() throws Exception {
        Path corrupt = directory.resolve("corrupt.json"); Files.writeString(corrupt, "{}"); BallotStore store = new BallotStore(corrupt);
        assertThrows(IOException.class, store::load); assertThrows(IOException.class, () -> store.save(BallotState.empty()));
        assertEquals("{}", Files.readString(corrupt));
        Path oversize = directory.resolve("oversize.json"); Files.write(oversize, new byte[2 * 1024 * 1024 + 1]); BallotStore large = new BallotStore(oversize);
        assertThrows(IOException.class, large::load); assertThrows(IOException.class, () -> large.save(BallotState.empty()));
        assertEquals(2 * 1024 * 1024 + 1, Files.size(oversize));
        Path utf8 = directory.resolve("utf8.json"); Files.write(utf8, new byte[] {(byte) 0xc3, 0x28}); BallotStore malformed = new BallotStore(utf8);
        assertThrows(IOException.class, malformed::load); assertThrows(IOException.class, () -> malformed.save(BallotState.empty()));
    }
    @Test void externalMutationPreserved() throws Exception {
        Path path = directory.resolve("ledger.json"); BallotStore store = new BallotStore(path); store.load(); store.save(closedState());
        String external = "external edit"; Files.writeString(path, external);
        assertThrows(IOException.class, () -> store.save(BallotState.empty())); assertEquals(external, Files.readString(path));
    }
}
