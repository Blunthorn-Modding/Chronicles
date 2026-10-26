package io.github.chronicle.core.entry;

import io.github.chronicle.core.content.InventorySnapshot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleEntryServiceTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OPERATION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final PlayerPosition BEFORE_POSITION = new PlayerPosition("minecraft:overworld", 1, 64, 2, 0, 0);
    private static final PlayerPosition AFTER_POSITION = new PlayerPosition("minecraft:overworld", 10.5, 70, -3.5, 90, 0);
    private static final InventorySnapshot BEFORE = inventory("{}");
    private static final InventorySnapshot AFTER = inventory("{\"count\":1,\"id\":\"minecraft:stone\"}");

    @Test void failedJournalSavePublishesNoClaim() throws IOException {
        Memory repository = new Memory();
        RoleEntryService service = new RoleEntryService(repository);
        repository.fail = true;
        assertThrows(IOException.class, () -> service.prepare(operation()));
        assertEquals(RoleEntryState.empty(), service.state());
    }

    @Test void failureAfterPreparedAndAfterApplyingRetainsDistinctEvidence() throws IOException {
        Memory repository = new Memory();
        RoleEntryService service = new RoleEntryService(repository);
        service.prepare(operation());
        repository.fail = true;
        assertThrows(IOException.class, () -> service.begin(PLAYER, OPERATION));
        assertEquals(RoleEntryOperation.Stage.PREPARED, service.state().operations().get(PLAYER).stage());

        repository.fail = false;
        service.begin(PLAYER, OPERATION);
        repository.fail = true;
        assertThrows(IOException.class, () -> service.complete(PLAYER, OPERATION));
        assertEquals(RoleEntryOperation.Stage.APPLYING, service.state().operations().get(PLAYER).stage());
    }

    @Test void duplicateClaimIsRefusedButSafeAbortedPreparationCanBeReplanned() throws IOException {
        RoleEntryService service = new RoleEntryService(new Memory());
        service.prepare(operation());
        assertThrows(IllegalStateException.class, () -> service.prepare(operation(UUID.randomUUID())));
        service.abortPrepared(PLAYER, OPERATION);
        RoleEntryOperation replacement = operation(UUID.randomUUID());
        service.prepare(replacement);
        assertEquals(replacement.operationId(), service.state().operations().get(PLAYER).operationId());
        service.begin(PLAYER, replacement.operationId());
        service.complete(PLAYER, replacement.operationId());
        assertThrows(IllegalStateException.class, () -> service.prepare(operation(UUID.randomUUID())));
    }

    @Test void exactPostCanFinishButExactBeforeAfterClaimIsAmbiguous() {
        RoleEntryOperation prepared = operation();
        assertEquals(RoleEntryRecovery.Decision.ABORT_BEFORE_EFFECTS,
                RoleEntryRecovery.inspect(prepared, BEFORE, BEFORE_POSITION));
        RoleEntryOperation applying = prepared.withStage(RoleEntryOperation.Stage.APPLYING, "claimed");
        assertEquals(RoleEntryRecovery.Decision.FINISH_EXACT_POST_STATE,
                RoleEntryRecovery.inspect(applying, AFTER, AFTER_POSITION));
        assertEquals(RoleEntryRecovery.Decision.CONFLICT,
                RoleEntryRecovery.inspect(applying, BEFORE, BEFORE_POSITION));
        assertEquals(RoleEntryRecovery.Decision.CONFLICT,
                RoleEntryRecovery.inspect(applying, AFTER, BEFORE_POSITION));
    }

    private static RoleEntryOperation operation() { return operation(OPERATION); }
    private static RoleEntryOperation operation(UUID id) {
        String content = "{\"id\":\"quiet_below:scholar\"}";
        return new RoleEntryOperation(id, PLAYER, "quiet_below:scholar", fingerprint(content), content, BEFORE, AFTER,
                BEFORE_POSITION, AFTER_POSITION, "chronicle_core:arrival",
                List.of(new RoleBookGrant("quiet_below:journal", "33333333-3333-3333-3333-333333333333")),
                RoleEntryOperation.Stage.PREPARED, "prepared", "2026-10-26T12:00:00Z");
    }
    private static InventorySnapshot inventory(String first) {
        ArrayList<String> values = new ArrayList<>(java.util.Collections.nCopies(36, "{}")); values.set(0, first);
        return new InventorySnapshot(36, values);
    }
    private static String fingerprint(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new AssertionError(exception); }
    }

    private static final class Memory implements RoleEntryRepository {
        RoleEntryState state = RoleEntryState.empty(); boolean fail;
        @Override public RoleEntryState load() { return state; }
        @Override public void save(RoleEntryState value) throws IOException { if (fail) throw new IOException("deliberate"); state = value; }
    }
}
