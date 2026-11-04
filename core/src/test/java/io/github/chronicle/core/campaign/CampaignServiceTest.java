package io.github.chronicle.core.campaign;

import io.github.chronicle.core.history.ImmutableHistoryArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignServiceTest {
    private static final Actor GM = new Actor("Game Master", "123e4567-e89b-12d3-a456-426614174000");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);
    private static final String FINGERPRINT = "a".repeat(64);

    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultFlagsPreserveActiveSavedValuesAndNoopWithoutAudit() throws IOException {
        CampaignService service = service(new CampaignEvents());
        service.ensureDefaultFlags(GM, Map.of("test:a", false));
        service.start(GM, null);
        service.setFlag(GM, "test:a", true);
        service.ensureDefaultFlags(GM, Map.of("test:a", false, "test:b", true));
        assertEquals(Map.of("test:a", true, "test:b", true), service.state().flags());
        CampaignState before = service.state();
        service.ensureDefaultFlags(GM, Map.of("test:a", false, "test:b", false));
        assertEquals(before, service.state());
        assertThrows(IllegalArgumentException.class, () -> service.ensureDefaultFlags(GM, Map.of("invalid", true)));
        assertEquals(before, service.state());
        assertEquals(before, service(new CampaignEvents()).state());
    }

    @Test
    void failedDefaultFlagSaveDoesNotPublishBatch() throws IOException {
        Path blockedParent = temporaryDirectory.resolve("blocked-defaults");
        CampaignService service = new CampaignService(new CampaignStore(blockedParent.resolve("state.json")), new CampaignEvents(), CLOCK);
        Files.writeString(blockedParent, "not a directory");
        CampaignState before = service.state();
        assertThrows(IOException.class, () -> service.ensureDefaultFlags(GM, Map.of("test:a", true, "test:b", false)));
        assertEquals(before, service.state());
    }

    @Test
    void flagDefinitionsPersistAcrossNonActivePhasesAndNeverOverwrite() throws IOException {
        CampaignService service = service(new CampaignEvents());
        service.defineFlag(GM, "quiet:before");
        assertEquals(Boolean.FALSE, service.state().flags().get("quiet:before"));
        assertEquals("flag.define", service.state().history().getLast().action());
        assertEquals("quiet:before", service.state().history().getLast().target());
        assertEquals(service.state(), service(new CampaignEvents()).state());

        service.start(GM, null);
        service.setFlag(GM, "quiet:before", true);
        CampaignState active = service.state();
        assertThrows(IllegalStateException.class, () -> service.defineFlag(GM, "quiet:active"));
        assertEquals(active, service.state());
        service.pause(GM);
        service.defineFlag(GM, "quiet:paused");
        CampaignState paused = service.state();
        assertThrows(IllegalArgumentException.class, () -> service.defineFlag(GM, "quiet:before"));
        assertEquals(paused, service.state());
        assertTrue(service.state().flag("quiet:before"));
        service.end(GM);
        service.defineFlag(GM, "quiet:ended");
        assertEquals(Boolean.FALSE, service.state().flags().get("quiet:paused"));
        assertEquals(Boolean.FALSE, service.state().flags().get("quiet:ended"));
        assertEquals(service.state(), service(new CampaignEvents()).state());
    }

    @Test
    void flagDefinitionsValidateInputsAndCapacity() throws IOException {
        CampaignService service = service(new CampaignEvents());
        CampaignState before = service.state();
        assertThrows(IllegalArgumentException.class, () -> service.defineFlag(null, "quiet:valid"));
        assertThrows(IllegalArgumentException.class, () -> service.defineFlag(GM, "invalid id"));
        assertEquals(before, service.state());
        for (int i = 0; i < CampaignValidation.MAX_FLAGS; i++) {
            service.defineFlag(GM, "quiet:flag_" + i);
        }
        CampaignState full = service.state();
        assertThrows(IllegalStateException.class, () -> service.defineFlag(GM, "quiet:overflow"));
        assertEquals(full, service.state());
        assertEquals(full, service(new CampaignEvents()).state());
    }

    @Test
    void failedFlagDefinitionDoesNotPublishStateOrAudit() throws IOException {
        Path blockedParent = temporaryDirectory.resolve("blocked-flag");
        CampaignService service = new CampaignService(new CampaignStore(blockedParent.resolve("state.json")), new CampaignEvents(), CLOCK);
        Files.writeString(blockedParent, "not a directory");
        CampaignState before = service.state();
        assertThrows(IOException.class, () -> service.defineFlag(GM, "quiet:failed"));
        assertEquals(before, service.state());
        assertFalse(Files.exists(blockedParent.resolve("state.json")));
    }

    @Test
    void administrativeEvidencePersistsWithoutChangingProgress() throws IOException {
        CampaignService service = service(new CampaignEvents());
        service.start(GM, null);
        service.setFlag(GM, "quiet:ready", true);
        service.pause(GM);
        var prior = service.state();
        service.recordOperation(GM, "observation.reset.requested", "quiet:bell", Map.of("priorStatus", "DELIVERED", "recipients.0", "[123e4567-e89b-12d3-a456-426614174000]"));
        assertEquals(prior.phase(), service.state().phase());
        assertEquals(prior.flags(), service.state().flags());
        assertEquals(prior.completedEvents(), service.state().completedEvents());
        assertEquals("observation.reset.requested", service.state().history().getLast().action());
        assertEquals(service.state(), service(new CampaignEvents()).state());
    }

    @Test
    void lifecycleAllowsOnlyExpectedTransitionsAndAuditsThem() throws IOException {
        CampaignService service = service(new CampaignEvents());

        assertThrows(IllegalStateException.class, () -> service.pause(GM));
        service.start(GM, null);
        assertEquals(SessionPhase.ACTIVE, service.state().phase());
        assertEquals(1, service.state().sessionNumber());
        service.pause(GM);
        assertEquals(SessionPhase.PAUSED, service.state().phase());
        service.resume(GM);
        service.end(GM);
        assertEquals(SessionPhase.ENDED, service.state().phase());
        assertEquals(4, service.state().history().size());
        assertEquals(4, service.state().nextSequence());
        assertEquals("2026-10-18T12:00:00Z", service.state().history().getFirst().timestamp());
        assertThrows(IllegalStateException.class, () -> service.resume(GM));
    }

    @Test
    void sessionZeroAndLaterSessionNumberRulesAreEnforced() throws IOException {
        CampaignService service = service(new CampaignEvents());

        service.start(GM, 0);
        assertEquals(0, service.state().sessionNumber());
        service.end(GM);
        assertThrows(IllegalArgumentException.class, () -> service.start(GM, 0));
        service.start(GM, null);
        assertEquals(1, service.state().sessionNumber());
        service.end(GM);
        service.start(GM, 9);
        assertEquals(9, service.state().sessionNumber());
    }

    @Test
    void flagAndEventMutationsNeedAnActiveSession() throws IOException {
        CampaignEvents events = events(new CampaignEvent("quiet:bell", "Rings a bell", false, Map.of(), state -> new EventOutcome(Map.of(), "Rang.")));
        CampaignService service = service(events);

        assertThrows(IllegalStateException.class, () -> service.setFlag(GM, "quiet:ready", true));
        assertThrows(IllegalStateException.class, () -> service.trigger(GM, "quiet:bell"));
        service.start(GM, null);
        service.pause(GM);
        assertThrows(IllegalStateException.class, () -> service.setFlag(GM, "quiet:ready", true));
        assertThrows(IllegalStateException.class, () -> service.trigger(GM, "quiet:bell"));
    }

    @Test
    void eventPrerequisitesAndOnceOnlyCompletionAreAppliedAtomically() throws IOException {
        CampaignEvents events = events(new CampaignEvent("quiet:reveal", "Reveal the clue", true,
                Map.of("quiet:ready", true), state -> new EventOutcome(Map.of("quiet:revealed", true), "The clue is revealed.")));
        CampaignService service = service(events);
        service.start(GM, null);

        assertEquals("Required flag does not match: quiet:ready", service.preview("quiet:reveal"));
        assertThrows(IllegalStateException.class, () -> service.trigger(GM, "quiet:reveal"));
        service.setFlag(GM, "quiet:ready", true);
        assertEquals("Ready.", service.preview("quiet:reveal"));
        assertEquals("The clue is revealed.", service.trigger(GM, "quiet:reveal"));
        assertTrue(service.state().flag("quiet:revealed"));
        assertTrue(service.state().completedEvents().contains("quiet:reveal"));
        assertEquals("Event has already been completed.", service.preview("quiet:reveal"));
    }

    @Test
    void repeatableEventsAreRecordedAsCompletedButMayRunAgain() throws IOException {
        CampaignEvents events = events(new CampaignEvent("quiet:repeat", "Repeatable", false, Map.of(),
                state -> new EventOutcome(Map.of(), "Done.")));
        CampaignService service = service(events);
        service.start(GM, null);

        service.trigger(GM, "quiet:repeat");
        assertTrue(service.state().completedEvents().contains("quiet:repeat"));
        assertEquals("Ready.", service.preview("quiet:repeat"));
        assertEquals("Done.", service.trigger(GM, "quiet:repeat"));
    }

    @Test
    void handlerFailureDoesNotCommitStateOrAudit() throws IOException {
        CampaignEvents events = events(new CampaignEvent("quiet:broken", "Fails deliberately", false, Map.of(),
                state -> {
                    throw new IllegalStateException("test failure");
                }));
        CampaignService service = service(events);
        service.start(GM, null);
        CampaignState before = service.state();

        assertThrows(IllegalStateException.class, () -> service.trigger(GM, "quiet:broken"));
        assertEquals(before, service.state());
    }

    @Test
    void registryPreservesOrderAndRejectsDuplicateOrLateEvents() {
        CampaignEvents events = new CampaignEvents();
        CampaignEvent first = new CampaignEvent("quiet:first", "First", false, Map.of(), state -> new EventOutcome(Map.of(), "First."));
        CampaignEvent second = new CampaignEvent("quiet:second", "Second", false, Map.of(), state -> new EventOutcome(Map.of(), "Second."));
        events.register(first);
        events.register(second);

        assertEquals(java.util.List.of(first, second), java.util.List.copyOf(events.events()));
        assertThrows(IllegalArgumentException.class, () -> events.register(first));
        events.freeze();
        assertThrows(IllegalStateException.class, () -> events.register(new CampaignEvent("quiet:third", "Third", false, Map.of(),
                state -> new EventOutcome(Map.of(), "Third."))));
    }

    @Test
    void persistenceFailureLeavesInMemoryStateAndAuditUnchanged() throws IOException {
        Path blockedParent = temporaryDirectory.resolve("blocked");
        CampaignStore store = new CampaignStore(blockedParent.resolve("state.json"));
        CampaignService service = new CampaignService(store, new CampaignEvents(), CLOCK);
        Files.writeString(blockedParent, "not a directory");
        CampaignState before = service.state();

        assertThrows(IOException.class, () -> service.start(GM, null));
        assertEquals(before, service.state());
        assertFalse(Files.exists(blockedParent.resolve("state.json")));
    }

    @Test
    void eventOutcomeCommitsAtomicallyAndReplayNeverRevertsLaterGmChanges() throws IOException {
        CampaignService service = service(new CampaignEvents());
        service.start(GM, null);
        UUID operation = UUID.randomUUID();
        Map<String, Boolean> patch = Map.of("quiet:ready", true, "quiet:waiting", false);
        assertTrue(service.applyEventFlags(GM, operation, "quiet:arrival", FINGERPRINT, patch));
        assertTrue(service.state().flag("quiet:ready"));
        assertEquals(patch, service.state().eventFlagOutcomes().get(operation).changes());
        assertEquals("event.flags", service.state().history().getLast().action());
        assertEquals("true", service.state().history().getLast().parameters().get("flag.quiet:ready"));
        service.setFlag(GM, "quiet:ready", false);
        CampaignState overridden = service.state();
        assertFalse(service.applyEventFlags(GM, operation, "quiet:arrival", FINGERPRINT, patch));
        assertEquals(overridden, service.state());
        CampaignService reloaded = service(new CampaignEvents());
        assertFalse(reloaded.applyEventFlags(GM, operation, "quiet:arrival", FINGERPRINT, patch));
        assertEquals(overridden, reloaded.state());
        assertThrows(IllegalStateException.class, () -> reloaded.applyEventFlags(GM, operation, "quiet:other", FINGERPRINT, patch));
        assertThrows(IllegalStateException.class, () -> reloaded.applyEventFlags(GM, operation, "quiet:arrival", "b".repeat(64), patch));
        assertThrows(IllegalStateException.class, () -> reloaded.applyEventFlags(GM, operation, "quiet:arrival", FINGERPRINT, Map.of("quiet:ready", false)));
        assertEquals(overridden, reloaded.state());
    }

    @Test
    void trustedOutcomeCanFinalizeAfterEndedWithoutRelaxingManualMutations() throws IOException {
        CampaignService service = service(new CampaignEvents());
        service.start(GM, null);
        service.end(GM);
        assertTrue(service.applyEventFlags(GM, UUID.randomUUID(), "quiet:departure", FINGERPRINT, Map.of("quiet:departed", true)));
        assertEquals(SessionPhase.ENDED, service.state().phase());
        assertTrue(service.state().flag("quiet:departed"));
        assertThrows(IllegalStateException.class, () -> service.setFlag(GM, "quiet:departed", false));
    }

    @Test
    void everyTrueToFalseTransitionRetainsItsVersionAcrossOnAndRestart() throws IOException {
        CampaignEvents events = events(new CampaignEvent("quiet:clear", "Clear", false, Map.of(),
                state -> new EventOutcome(Map.of("quiet:ready", false), "Cleared.")));
        CampaignService service = service(events);
        service.start(GM, null);
        assertEquals(0, service.state().flagOffVersion("quiet:ready"));
        service.setFlag(GM, "quiet:ready", true);
        long firstOff = service.state().nextSequence();
        service.setFlag(GM, "quiet:ready", false);
        assertEquals(firstOff, service.state().flagOffVersion("quiet:ready"));
        service.setFlag(GM, "quiet:ready", true);
        assertEquals(firstOff, service.state().flagOffVersion("quiet:ready"));
        long eventOff = service.state().nextSequence();
        service.applyEventFlags(GM, UUID.randomUUID(), "quiet:reset", FINGERPRINT, Map.of("quiet:ready", false));
        assertEquals(eventOff, service.state().flagOffVersion("quiet:ready"));
        service.setFlag(GM, "quiet:ready", true);
        long legacyOff = service.state().nextSequence();
        service.trigger(GM, "quiet:clear");
        assertEquals(legacyOff, service.state().flagOffVersion("quiet:ready"));
        service.setFlag(GM, "quiet:ready", false);
        assertEquals(legacyOff, service.state().flagOffVersion("quiet:ready"));
        assertEquals(service.state(), service(events).state());
    }

    @Test
    void invalidOrOverCapacityPatchPublishesNoPartialFlagsReceiptOrAudit() throws IOException {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (int i = 0; i < CampaignValidation.MAX_FLAGS; i++) {
            flags.put("quiet:flag_" + i, true);
        }
        new CampaignStore(temporaryDirectory.resolve("campaign.json")).save(new CampaignState(2, 1, SessionPhase.ACTIVE,
                flags, Set.of(), List.of(), 0));
        CampaignService service = service(new CampaignEvents());
        CampaignState before = service.state();
        assertThrows(IllegalArgumentException.class, () -> service.applyEventFlags(GM, UUID.randomUUID(), "quiet:event", FINGERPRINT,
                Map.of("quiet:flag_0", false, "quiet:overflow", true)));
        assertThrows(IllegalArgumentException.class, () -> service.applyEventFlags(GM, UUID.randomUUID(), "quiet:event", "bad", Map.of("quiet:flag_0", false)));
        assertThrows(IllegalArgumentException.class, () -> service.applyEventFlags(GM, UUID.randomUUID(), "quiet:event", FINGERPRINT, Map.of()));
        Map<String, Boolean> oversizedPatch = new LinkedHashMap<>();
        for (int i = 0; i <= FlagOutcomeReceipt.MAX_FLAG_CHANGES; i++) {
            oversizedPatch.put("quiet:flag_" + i, false);
        }
        assertThrows(IllegalArgumentException.class, () -> service.applyEventFlags(GM, UUID.randomUUID(), "quiet:event", FINGERPRINT, oversizedPatch));
        assertEquals(before, service.state());
        assertEquals(before, service(new CampaignEvents()).state());
    }

    @Test
    void fullReceiptLedgerAllowsExactReplayButRefusesNewOperationBeforeAnyMutation() throws IOException {
        Map<UUID, FlagOutcomeReceipt> receipts = new LinkedHashMap<>();
        for (int i = 0; i < CampaignState.MAX_EVENT_FLAG_OUTCOMES; i++) {
            UUID id = new UUID(0, i);
            receipts.put(id, new FlagOutcomeReceipt(id, "quiet:event", FINGERPRINT, Map.of("quiet:ready", true)));
        }
        CampaignState full = new CampaignState(2, 1, SessionPhase.ACTIVE, Map.of("quiet:ready", false), Set.of(), List.of(), 0, Map.of(), receipts);
        new CampaignStore(temporaryDirectory.resolve("campaign.json")).save(full);
        CampaignService service = service(new CampaignEvents());
        UUID known = receipts.keySet().iterator().next();
        service.requireEventFlagCapacity(known);
        assertFalse(service.applyEventFlags(GM, known, "quiet:event", FINGERPRINT, Map.of("quiet:ready", true)));
        UUID unknown = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> service.requireEventFlagCapacity(unknown));
        assertThrows(IllegalStateException.class, () -> service.applyEventFlags(GM, unknown, "quiet:event", FINGERPRINT, Map.of("quiet:ready", true)));
        assertEquals(full, service.state());
    }

    @Test
    void archivedReceiptsFreeCapacityButExactOldRetriesNeverReapplyFlags() throws IOException {
        Map<UUID, FlagOutcomeReceipt> receipts = new LinkedHashMap<>();
        for (int i = 0; i < CampaignState.MAX_EVENT_FLAG_OUTCOMES; i++) {
            UUID id = new UUID(0, i);
            receipts.put(id, new FlagOutcomeReceipt(id, "quiet:event", FINGERPRINT, Map.of("quiet:ready", true)));
        }
        new CampaignStore(temporaryDirectory.resolve("campaign.json")).save(new CampaignState(2, 1, SessionPhase.PAUSED,
                Map.of("quiet:ready", false), Set.of(), List.of(), 0, Map.of(), receipts));
        CampaignService service = service(new CampaignEvents());
        assertEquals(CampaignState.MAX_EVENT_FLAG_OUTCOMES, service.retireEventFlagOutcomes(receipts.keySet()));
        service.resume(GM);
        UUID fresh = UUID.randomUUID();
        assertTrue(service.applyEventFlags(GM, fresh, "quiet:event", FINGERPRINT, Map.of("quiet:ready", true)));
        service.setFlag(GM, "quiet:ready", false);
        UUID old = receipts.keySet().iterator().next();
        assertFalse(service.applyEventFlags(GM, old, "quiet:event", FINGERPRINT, Map.of("quiet:ready", true)));
        assertFalse(service.state().flag("quiet:ready"));
        assertThrows(IllegalStateException.class, () -> service.applyEventFlags(GM, old, "quiet:event", FINGERPRINT, Map.of("quiet:ready", false)));
    }

    @Test
    void conflictingArchiveRefusesBeforeCampaignReceiptPrune() throws Exception {
        UUID id=UUID.randomUUID();FlagOutcomeReceipt receipt=new FlagOutcomeReceipt(id,"quiet:event",FINGERPRINT,Map.of("quiet:ready",true));
        Path file=temporaryDirectory.resolve("campaign.json");new CampaignStore(file).save(new CampaignState(2,1,SessionPhase.PAUSED,
                Map.of("quiet:ready",false),Set.of(),List.of(),0,Map.of(),Map.of(id,receipt)));
        new ImmutableHistoryArchive(temporaryDirectory.resolve("event-flag-history"),"event-flag",64*1024).publish(id.toString(),new byte[]{1});
        CampaignService service=service(new CampaignEvents());
        assertThrows(IOException.class,()->service.retireEventFlagOutcomes(Set.of(id)));
        assertEquals(receipt,new CampaignStore(file).load().eventFlagOutcomes().get(id));
    }

    @Test
    void trimmingAuditHistoryDoesNotForgetOutcomeReceipts() throws IOException {
        UUID id = UUID.randomUUID();
        FlagOutcomeReceipt receipt = new FlagOutcomeReceipt(id, "quiet:event", FINGERPRINT, Map.of("quiet:ready", true));
        List<AuditEntry> history = new java.util.ArrayList<>();
        for (int i = 0; i < CampaignValidation.MAX_HISTORY; i++) {
            history.add(new AuditEntry(i, CLOCK.instant().toString(), GM, "flag.set", 1, "quiet:ready", Map.of("value", "false")));
        }
        new CampaignStore(temporaryDirectory.resolve("campaign.json")).save(new CampaignState(2, 1, SessionPhase.ACTIVE,
                Map.of("quiet:ready", false), Set.of(), history, CampaignValidation.MAX_HISTORY, Map.of(), Map.of(id, receipt)));
        CampaignService service = service(new CampaignEvents());
        service.recordOperation(GM, "example.audit", "quiet:ready", Map.of());
        assertEquals(CampaignValidation.MAX_HISTORY, service.state().history().size());
        assertEquals(1, service.state().history().getFirst().sequence());
        assertFalse(service.applyEventFlags(GM, id, "quiet:event", FINGERPRINT, receipt.changes()));
        assertFalse(service.state().flag("quiet:ready"));
        assertEquals(service.state(), service(new CampaignEvents()).state());
    }

    @Test
    void failedOutcomeSavePublishesNeitherOffVersionNorReceipt() throws IOException {
        CampaignService service = service(new CampaignEvents());
        service.start(GM, null);
        service.setFlag(GM, "quiet:ready", true);
        Path file = temporaryDirectory.resolve("campaign.json");
        Files.writeString(file, "invalid evidence must not be overwritten");
        CampaignState before = service.state();
        assertThrows(IOException.class, () -> service.applyEventFlags(GM, UUID.randomUUID(), "quiet:event", FINGERPRINT,
                Map.of("quiet:ready", false, "quiet:other", true)));
        assertEquals(before, service.state());
        assertEquals("invalid evidence must not be overwritten", Files.readString(file));
    }

    private CampaignService service(CampaignEvents events) throws IOException {
        return new CampaignService(new CampaignStore(temporaryDirectory.resolve("campaign.json")), events, CLOCK);
    }

    private static CampaignEvents events(CampaignEvent event) {
        CampaignEvents events = new CampaignEvents();
        events.register(event);
        events.freeze();
        return events;
    }
}
