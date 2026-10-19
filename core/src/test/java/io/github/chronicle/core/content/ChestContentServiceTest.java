package io.github.chronicle.core.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestContentServiceTest {
    @Test
    void snapshotsCanonicalizeJsonObjectOrderForRestartSafeComparison() {
        InventorySnapshot left = new InventorySnapshot(1, List.of("{\"components\":{\"minecraft:z\":2,\"minecraft:a\":1},\"id\":\"minecraft:stone\",\"count\":1}"));
        InventorySnapshot right = new InventorySnapshot(1, List.of("{\"count\":1,\"id\":\"minecraft:stone\",\"components\":{\"minecraft:a\":1,\"minecraft:z\":2}}"));
        assertEquals(left, right);
    }

    @Test
    void revisionsPinBindingsAndFailedWritesNeverPublishCandidates() throws IOException {
        MemoryRepository repository = new MemoryRepository();
        ChestContentService service = new ChestContentService(repository);
        ChestTemplate template = service.putTemplate("supplies", snapshot("empty", "diamond"), false);
        ChestBinding binding = service.putBinding("mine_cache", template.id(), template.revision(), "quiet_below:forge", 12, ChestPolicy.MANUAL, false);
        assertEquals(2, binding.revision());

        ChestTemplate replaced = service.putTemplate("supplies", snapshot("empty", "emerald"), true);
        assertEquals(3, replaced.revision());
        assertThrows(IllegalStateException.class, () -> service.requireCurrentTemplate(binding));

        ChestContentState before = service.state();
        repository.fail = true;
        assertThrows(IOException.class, () -> service.putBinding("new_cache", replaced.id(), replaced.revision(), "quiet_below:forge", 12, ChestPolicy.MANUAL, false));
        assertEquals(before, service.state());
    }

    @Test
    void policiesUseOnlyDurableCompletionReceipts() throws IOException {
        for (ChestPolicy policy : ChestPolicy.values()) {
            ChestContentService service = ready(policy);
            ChestBinding binding = service.state().bindings().get("cache");
            PendingChestOperation first = operation(binding, service.state().templates().get("kit"), 7);
            service.begin(first, true);
            service.complete(first.operationId(), "2026-10-19T12:01:00Z");
            PendingChestOperation second = operation(binding, service.state().templates().get("kit"), policy == ChestPolicy.ONCE_SESSION ? 8 : 7);
            if (policy == ChestPolicy.ONCE_CAMPAIGN) {
                assertThrows(IllegalStateException.class, () -> service.begin(second, true));
            } else {
                service.begin(second, true);
            }
        }
    }

    @Test
    void onceSessionRejectsRepeatAndAllowsTheNextSession() throws IOException {
        ChestContentService service = ready(ChestPolicy.ONCE_SESSION);
        ChestBinding binding = service.state().bindings().get("cache");
        ChestTemplate template = service.state().templates().get("kit");
        PendingChestOperation first = operation(binding, template, 4);
        service.begin(first, true);
        service.complete(first.operationId(), "2026-10-19T12:01:00Z");
        assertThrows(IllegalStateException.class, () -> service.begin(operation(binding, template, 4), true));
        service.begin(operation(binding, template, 5), true);
    }

    @Test
    void pendingIntentSurvivesSaveFailureAndRecoveryNeverGuesses() throws IOException {
        MemoryRepository repository = new MemoryRepository();
        ChestContentService service = configure(repository, ChestPolicy.MANUAL);
        PendingChestOperation operation = operation(service.state().bindings().get("cache"), service.state().templates().get("kit"), 1);
        service.begin(operation, true);
        assertEquals(RecoveryDecision.ABORT_UNCHANGED, RecoveryDecision.compare(operation, operation.before()));
        assertEquals(RecoveryDecision.COMMIT_APPLIED, RecoveryDecision.compare(operation, operation.after()));
        assertEquals(RecoveryDecision.CONFLICT, RecoveryDecision.compare(operation, snapshot("player-edit", "empty")));

        repository.fail = true;
        assertThrows(IOException.class, () -> service.complete(operation.operationId(), "2026-10-19T12:01:00Z"));
        assertTrue(service.state().pending().containsKey(operation.operationId()));
        assertTrue(service.state().receipts().isEmpty());
        repository.fail = false;
        service.abortUnchanged(operation.operationId());
        assertFalse(service.state().pending().containsKey(operation.operationId()));
        assertTrue(service.state().receipts().isEmpty());
    }

    @Test
    void rejectsNonEmptyOrCompetingPendingTargetsBeforePublishing() throws IOException {
        ChestContentService service = ready(ChestPolicy.MANUAL);
        ChestBinding binding = service.state().bindings().get("cache");
        ChestTemplate template = service.state().templates().get("kit");
        PendingChestOperation first = operation(binding, template, 1);
        assertThrows(IllegalStateException.class, () -> service.begin(first, false));
        service.begin(first, true);
        PendingChestOperation other = operation(binding, template, 1);
        assertThrows(IllegalStateException.class, () -> service.begin(other, true));
    }

    @Test
    void rejectsAJournalTargetThatDoesNotMatchTheBindingsPinnedAnchor() throws IOException {
        ChestContentService service = ready(ChestPolicy.MANUAL);
        ChestBinding binding = service.state().bindings().get("cache");
        ChestTemplate template = service.state().templates().get("kit");
        PendingChestOperation valid = operation(binding, template, 1);
        PendingChestOperation forged = new PendingChestOperation(valid.operationId(), valid.actorUuid(), valid.actorName(), valid.bindingId(), valid.bindingRevision(),
                valid.sessionNumber(), new ChestTarget("other_anchor", 99, valid.target().dimension(), valid.target().x(), valid.target().y(), valid.target().z(), valid.target().expectedBlock()),
                valid.before(), valid.after(), valid.createdAt());
        assertThrows(IllegalStateException.class, () -> service.begin(forged, true));
        assertTrue(service.state().pending().isEmpty());
    }

    private static ChestContentService ready(ChestPolicy policy) throws IOException {
        return configure(new MemoryRepository(), policy);
    }

    private static ChestContentService configure(MemoryRepository repository, ChestPolicy policy) throws IOException {
        ChestContentService service = new ChestContentService(repository);
        ChestTemplate template = service.putTemplate("kit", snapshot("empty", "diamond"), false);
        service.putBinding("cache", template.id(), template.revision(), "quiet_below:forge", 3, policy, false);
        return service;
    }

    private static PendingChestOperation operation(ChestBinding binding, ChestTemplate template, int session) {
        return new PendingChestOperation(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "operator", binding.id(), binding.revision(), session,
                new ChestTarget(binding.anchorId(), binding.anchorRevision(), "minecraft:overworld", 1, 64, 2, "minecraft:barrel"),
                snapshot("empty", "empty"), template.contents(), "2026-10-19T12:00:00Z");
    }

    private static InventorySnapshot snapshot(String... values) {
        return new InventorySnapshot(values.length, java.util.Arrays.stream(values).map(value -> "\"" + value + "\"").toList());
    }

    private static final class MemoryRepository implements ChestContentRepository {
        private ChestContentState state = ChestContentState.empty();
        private boolean fail;
        @Override public ChestContentState load() { return state; }
        @Override public void save(ChestContentState state) throws IOException { if (fail) throw new IOException("deliberate"); this.state = state; }
    }
}
