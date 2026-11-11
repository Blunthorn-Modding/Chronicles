package io.github.chronicle.core.content;

import io.github.chronicle.core.books.BookCopyLocation;
import io.github.chronicle.core.books.BookDefinition;
import io.github.chronicle.core.books.BookDeliveryClaim;
import io.github.chronicle.core.books.BookKeepRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void typedBindingIsOneDurableLocationIdentityAndRetainsReceiptsAcrossRetypeAndRebind() throws IOException {
        MemoryRepository repository = new MemoryRepository();
        ChestContentService service = new ChestContentService(repository);
        ChestTarget physical = physical("minecraft:barrel", 10);
        ChestBinding binding = service.putTypedBinding("quiet_below:mine", physical, false);
        assertTrue(binding.typed());
        assertTrue(binding.id().matches("chest_[0-9a-f]{32}"));
        assertEquals(binding.id(), binding.anchorId());
        assertEquals(binding.revision(), binding.directTarget().anchorRevision());
        assertEquals(1, repository.saves);

        PendingChestOperation operation = typedOperation(binding, 2, 0, snapshot("player-item"), snapshot("rolled"));
        service.begin(operation, true);
        service.complete(operation.operationId(), "2026-10-19T12:01:00Z");
        ChestBinding retyped = service.putTypedBinding("quiet_below:city", physical("minecraft:chest", 10), true);
        assertEquals(binding.id(), retyped.id());
        assertEquals(1, service.state().receipts().size());

        service.removeTypedBinding(retyped.id());
        assertEquals(1, service.state().receipts().size());
        ChestBinding rebound = service.putTypedBinding("quiet_below:mine", physical("minecraft:shulker_box", 10), false);
        assertEquals(binding.id(), rebound.id());
        assertThrows(IllegalStateException.class, () -> service.begin(typedOperation(rebound, 1, 99, snapshot("x"), snapshot("y")), true));
        assertThrows(IllegalStateException.class, () -> service.begin(typedOperation(rebound, 2, 0, snapshot("x"), snapshot("y")), true));
    }

    @Test
    void retypeTranslatesCurrentSessionReceiptToTheNewTypesGeneration() throws IOException {
        ChestContentService service = new ChestContentService(new MemoryRepository());
        service.saveSchedule(new LootSchedule(9, 0, Map.of(
                "quiet_below:mine", new LootCycle(100, 20, 20),
                "quiet_below:city", new LootCycle(3, 20, 20))));
        ChestBinding mine = service.putTypedBinding("quiet_below:mine", physical("minecraft:barrel", 5), false);
        PendingChestOperation old = typedOperation(mine, 9, 100, snapshot("old"), snapshot("mine"));
        service.begin(old, true);
        service.complete(old.operationId(), "2026-10-19T12:01:00Z");

        service.removeTypedBinding(mine.id());
        ChestBinding city = service.putTypedBinding("quiet_below:city", physical("minecraft:barrel", 5), false);
        assertTrue(service.hasCompletedCycle(city.id(), 9, 3));
        PendingChestOperation next = typedOperation(city, 9, 4, snapshot("mine"), snapshot("city"));
        service.begin(next, true);
    }

    @Test
    void typedJournalAcceptsReplacementAndEmptyOrUnchangedRollsThenPrunesReceipts() throws IOException {
        ChestContentService service = new ChestContentService(new MemoryRepository());
        ChestBinding binding = service.putTypedBinding("quiet_below:mine", physical("minecraft:barrel", 1), false);
        PendingChestOperation replacement = typedOperation(binding, 3, 0, snapshot("player-item"), snapshot("rolled"));
        service.begin(replacement, true);
        service.complete(replacement.operationId(), "2026-10-19T12:01:00Z");
        assertTrue(service.hasCompletedCycle(binding.id(), 3, 0));

        PendingChestOperation empty = typedOperation(binding, 3, 1, snapshot("empty"), snapshot("empty"));
        service.begin(empty, true);
        service.complete(empty.operationId(), "2026-10-19T12:02:00Z");
        assertEquals(1, service.state().receipts().size());
        assertTrue(service.hasCompletedCycle(binding.id(), 3, 0));
        assertTrue(service.hasCompletedCycle(binding.id(), 3, 1));
        assertFalse(service.hasCompletedCycle(binding.id(), 3, 2));
    }

    @Test
    void typedJournalPinsTargetCycleAndPendingRemoval() throws IOException {
        ChestContentService service = new ChestContentService(new MemoryRepository());
        ChestBinding binding = service.putTypedBinding("quiet_below:mine", physical("minecraft:barrel", 1), false);
        PendingChestOperation pending = typedOperation(binding, 1, 0, snapshot("old"), snapshot("new"));
        assertThrows(IllegalStateException.class, () -> service.begin(pending, false));
        PendingChestOperation forged = new PendingChestOperation(pending.operationId(), pending.actorUuid(), pending.actorName(),
                pending.bindingId(), pending.bindingRevision(), pending.sessionNumber(), pending.cycle(),
                physical("minecraft:barrel", 2), pending.before(), pending.after(), pending.createdAt());
        assertThrows(IllegalStateException.class, () -> service.begin(forged, true));
        service.begin(pending, true);
        assertThrows(IllegalStateException.class, () -> service.removeTypedBinding(binding.id()));
    }

    @Test
    void scheduleSavesBeforePublishAndSkipsUnchangedWrites() throws IOException {
        MemoryRepository repository = new MemoryRepository();
        ChestContentService service = new ChestContentService(repository);
        LootSchedule schedule = LootSchedule.advance(LootSchedule.empty(), 1, 0, Map.of("quiet_below:mine", 20L));
        service.saveSchedule(schedule);
        assertEquals(schedule, service.state().schedule());
        assertEquals(1, repository.saves);
        service.saveSchedule(schedule);
        assertEquals(1, repository.saves);

        LootSchedule next = LootSchedule.advance(schedule, 1, 20, Map.of("quiet_below:mine", 20L));
        repository.fail = true;
        assertThrows(IOException.class, () -> service.saveSchedule(next));
        assertEquals(schedule, service.state().schedule());
    }

    @Test
    void managedOnceReservationIsAtomicAbortableAndFinalizedOnlyOnCompletion() throws IOException {
        ChestContentService service = new ChestContentService(new MemoryRepository());
        ChestBinding binding = service.putTypedBinding("quiet_below:mine", physical("minecraft:barrel", 1), false);
        String bookId = "quiet_below:journal";
        String token = service.deliveryToken(bookId);
        assertTrue(service.state().bookDelivery().deliveries().isEmpty());

        PendingChestOperation first = loreFill(binding, 1, 0, bookId, token, false);
        service.beginLore(first, Map.of(bookId, BookDefinition.Rule.ONCE), true);
        assertEquals(Set.of(first.operationId()), service.state().bookDelivery().deliveries().get(bookId).pendingOperationIds());
        assertThrows(IllegalStateException.class, () -> service.beginLore(
                loreFill(binding, 1, 0, bookId, token, false), Map.of(bookId, BookDefinition.Rule.ONCE), true));

        service.abortUnchanged(first.operationId());
        assertFalse(service.state().bookDelivery().deliveries().get(bookId).issued());
        assertTrue(service.state().bookDelivery().deliveries().get(bookId).pendingOperationIds().isEmpty());

        PendingChestOperation second = loreFill(binding, 1, 0, bookId, token, false);
        service.beginLore(second, Map.of(bookId, BookDefinition.Rule.ONCE), true);
        assertEquals(second.operationId(), service.complete(second.operationId(), "2026-10-25T12:01:00Z").operationId());
        assertTrue(service.state().bookDelivery().deliveries().get(bookId).issued());
        assertThrows(IllegalStateException.class, () -> service.beginLore(
                loreFill(binding, 1, 1, bookId, token, false), Map.of(bookId, BookDefinition.Rule.ONCE), true));
    }

    @Test
    void manualKeepInsertDoesNotConsumeFillCycleAndDiscoveryRetainsLocation() throws IOException {
        ChestContentService service = new ChestContentService(new MemoryRepository());
        ChestBinding binding = service.putTypedBinding("quiet_below:mine", physical("minecraft:barrel", 1), false);
        PendingChestOperation normal = typedOperation(binding, 4, 7, snapshot("old"), snapshot("loot"));
        service.begin(normal, true);
        service.complete(normal.operationId(), "2026-10-25T12:00:00Z");

        String bookId = "quiet_below:journal";
        assertThrows(IllegalStateException.class, () -> service.startKeep(bookId, binding.lootType(), "rule-a",
                BookDefinition.Rule.OFF, false, "2026-10-25T12:01:00Z"));
        service.startKeep(bookId, binding.lootType(), "rule-a", BookDefinition.Rule.OFF, true, "2026-10-25T12:01:00Z");
        PendingChestOperation insert = loreOperation(PendingChestOperation.Purpose.INSERT, binding, null, 4, -1,
                bookId, service.deliveryToken(bookId), true);
        service.beginLore(insert, Map.of(bookId, BookDefinition.Rule.OFF), true);
        assertNull(service.complete(insert.operationId(), "2026-10-25T12:02:00Z"));
        assertEquals(1, service.state().receipts().size());
        assertEquals(7, service.state().receipts().getFirst().cycle());
        BookKeepRequest placed = service.state().bookDelivery().keepRequests().get(bookId);
        assertTrue(placed.ownIssuance());
        assertEquals(BookCopyLocation.State.PRESENT, placed.location().state());
        assertEquals(-1, placed.lastCycle());

        var discovery = service.recordDiscovery(bookId, service.deliveryToken(bookId),
                "00000000-0000-0000-0000-000000000099", "2026-10-25T12:03:00Z");
        assertEquals(discovery, service.recordDiscovery(bookId, service.deliveryToken(bookId),
                "00000000-0000-0000-0000-000000000098", "2026-10-25T12:04:00Z"));
        BookKeepRequest completed = service.state().bookDelivery().keepRequests().get(bookId);
        assertEquals(BookKeepRequest.Status.COMPLETED_FOUND, completed.status());
        assertEquals(placed.location(), completed.location());
        assertThrows(IllegalStateException.class, () -> service.startKeep(bookId, binding.lootType(), "rule-a",
                BookDefinition.Rule.REPEAT, false, "2026-10-25T12:05:00Z"));
    }

    @Test
    void moveLocksBothEndpointsRecoversOnlyMatchingPairsAndStampsGeneration() throws IOException {
        ChestContentService service = new ChestContentService(new MemoryRepository());
        ChestBinding source = service.putTypedBinding("quiet_below:mine", physical("minecraft:barrel", 1), false);
        ChestBinding destination = service.putTypedBinding("quiet_below:mine", physical("minecraft:barrel", 2), false);
        String bookId = "quiet_below:journal";
        service.startKeep(bookId, source.lootType(), "rule-a", BookDefinition.Rule.REPEAT, false, "2026-10-25T12:00:00Z");
        PendingChestOperation move = loreOperation(PendingChestOperation.Purpose.MOVE, destination, source, 8, 4,
                bookId, service.deliveryToken(bookId), true);
        assertEquals(RecoveryDecision.ABORT_UNCHANGED, RecoveryDecision.compare(move, move.sourceBefore(), move.before()));
        assertEquals(RecoveryDecision.COMMIT_APPLIED, RecoveryDecision.compare(move, move.sourceAfter(), move.after()));
        assertEquals(RecoveryDecision.CONFLICT, RecoveryDecision.compare(move, move.sourceAfter(), move.before()));
        assertThrows(IllegalArgumentException.class, () -> RecoveryDecision.compare(move, move.before()));

        service.beginLore(move, Map.of(bookId, BookDefinition.Rule.REPEAT), true);
        assertThrows(IllegalStateException.class, () -> service.begin(typedOperation(source, 8, 4,
                snapshot("source-before"), snapshot("other")), true));
        assertNull(service.complete(move.operationId(), "2026-10-25T12:01:00Z"));
        BookKeepRequest request = service.state().bookDelivery().keepRequests().get(bookId);
        assertEquals(destination.id(), request.location().bindingId());
        assertEquals(8, request.lastSessionNumber());
        assertEquals(4, request.lastCycle());
        assertThrows(IllegalStateException.class, () -> service.saveKeepCycle(bookId, 8, 3, "2026-10-25T12:02:00Z"));
        assertEquals(5, service.saveKeepCycle(bookId, 8, 5, "2026-10-25T12:03:00Z").lastCycle());
    }

    @Test
    void externalOnceIssuanceRequiresExplicitKeepReviewOverride() throws IOException {
        ChestContentService service = new ChestContentService(new MemoryRepository());
        ChestBinding binding = service.putTypedBinding("quiet_below:mine", physical("minecraft:barrel", 1), false);
        String bookId = "quiet_below:journal";
        service.startKeep(bookId, binding.lootType(), "rule-a", BookDefinition.Rule.ONCE, false, "2026-10-25T12:00:00Z");
        PendingChestOperation external = loreFill(binding, 1, 0, bookId, service.deliveryToken(bookId), false);
        service.beginLore(external, Map.of(bookId, BookDefinition.Rule.ONCE), true);
        service.complete(external.operationId(), "2026-10-25T12:01:00Z");
        PendingChestOperation keep = loreOperation(PendingChestOperation.Purpose.INSERT, binding, null, 1, -1,
                bookId, service.deliveryToken(bookId), true);
        assertThrows(IllegalStateException.class, () -> service.beginLore(keep, Map.of(bookId, BookDefinition.Rule.ONCE), true));
        service.suspendKeep(bookId, BookKeepRequest.Status.SUSPENDED_ONCE, "2026-10-25T12:02:00Z");
        service.reviewKeep(bookId, "rule-a", BookDefinition.Rule.ONCE, true, "2026-10-25T12:03:00Z");
        service.beginLore(keep, Map.of(bookId, BookDefinition.Rule.ONCE), true);
    }

    @Test void stoppedRequestCanExplicitlySelectAnotherTableWithoutOwningTheOldCopy() throws IOException {
        var service=new ChestContentService(new MemoryRepository());
        var source=service.putTypedBinding("test:old",physical("minecraft:barrel",20),false);
        String id="test:retarget";
        service.startKeep(id,"test:old","UNTIL_FOUND",BookDefinition.Rule.UNTIL_FOUND,false,"2026-10-25T12:00:00Z");
        service.reconcileKeepLocation(id,new io.github.chronicle.core.books.BookCopyLocation(source.id(),source.revision(),source.directTarget(),io.github.chronicle.core.books.BookCopyLocation.State.PRESENT),"2026-10-25T12:00:01Z");
        String token=service.deliveryToken(id);
        service.stopKeep(id,"2026-10-25T12:00:02Z");
        var replacement=service.startKeep(id,"test:new","UNTIL_FOUND",BookDefinition.Rule.UNTIL_FOUND,false,"2026-10-25T12:00:03Z");
        assertNull(replacement.location());assertEquals("test:new",replacement.lootTableId());assertEquals(token,service.deliveryToken(id));
        assertEquals(source,service.state().bindings().get(source.id()));
        var marked=service.saveKeepCycle(id,1,4,"2026-10-25T12:00:04Z");
        long revision=service.state().revision();
        assertEquals(marked,service.saveKeepCycle(id,1,4,"2026-10-25T12:00:05Z"));assertEquals(revision,service.state().revision());
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

    private static ChestTarget physical(String block, int x) {
        return new ChestTarget("temporary", 1, "minecraft:overworld", x, 64, 2, block);
    }

    private static PendingChestOperation typedOperation(ChestBinding binding, int session, long cycle,
                                                        InventorySnapshot before, InventorySnapshot after) {
        return new PendingChestOperation(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "operator",
                binding.id(), binding.revision(), session, cycle, binding.directTarget(), before, after,
                "2026-10-19T12:00:00Z");
    }

    private static PendingChestOperation loreFill(ChestBinding binding, int session, long cycle,
                                                  String bookId, String token, boolean keep) {
        return loreOperation(PendingChestOperation.Purpose.FILL, binding, null, session, cycle, bookId, token, keep);
    }

    private static PendingChestOperation loreOperation(PendingChestOperation.Purpose purpose, ChestBinding destination,
                                                       ChestBinding source, int session, long cycle,
                                                       String bookId, String token, boolean keep) {
        InventorySnapshot before = snapshot("destination-before");
        InventorySnapshot after = snapshot("destination-after");
        return new PendingChestOperation(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "operator",
                destination.id(), destination.revision(), session, cycle, destination.directTarget(), before, after,
                "2026-10-25T12:00:00Z", purpose,
                source == null ? null : source.id(), source == null ? 0 : source.revision(),
                source == null ? null : source.directTarget(), source == null ? null : snapshot("source-before"),
                source == null ? null : snapshot("source-after"), List.of(new BookDeliveryClaim(bookId, token, keep)));
    }

    private static InventorySnapshot snapshot(String... values) {
        return new InventorySnapshot(values.length, java.util.Arrays.stream(values).map(value -> "\"" + value + "\"").toList());
    }

    @Test void operatorIssuancePreservesFirstDiscoveryAndNeverMarksFoundBeforeInventory() throws Exception {
        var repo=new MemoryRepository();var service=new ChestContentService(repo);String id="test:gift";
        service.claimOperatorIssuance(id);var issued=service.state().bookDelivery().deliveries().get(id);
        assertTrue(issued.issued());assertNull(issued.firstDiscovery());
        var first=service.recordDiscovery(id,service.deliveryToken(id),UUID.randomUUID().toString(),"2026-10-25T20:00:00Z");
        long revision=service.state().revision();service.claimOperatorIssuance(id);
        assertEquals(revision,service.state().revision());
        assertEquals(first,service.recordDiscovery(id,service.deliveryToken(id),UUID.randomUUID().toString(),"2026-10-25T20:01:00Z"));
        assertEquals(first,new ChestContentService(repo).state().bookDelivery().deliveries().get(id).firstDiscovery());
    }
    @Test void failedOperatorIssuanceSavePublishesNoProvenance() throws Exception {
        var repo=new MemoryRepository();var service=new ChestContentService(repo);var before=service.state();repo.fail=true;
        assertThrows(IOException.class,()->service.claimOperatorIssuance("test:gift"));assertEquals(before,service.state());
    }

    private static final class MemoryRepository implements ChestContentRepository {
        private ChestContentState state = ChestContentState.empty();
        private boolean fail;
        private int saves;
        @Override public ChestContentState load() { return state; }
        @Override public void save(ChestContentState state) throws IOException { if (fail) throw new IOException("deliberate"); this.state = state; saves++; }
    }
}
