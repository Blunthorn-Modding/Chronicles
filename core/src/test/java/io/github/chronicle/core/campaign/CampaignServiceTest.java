package io.github.chronicle.core.campaign;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignServiceTest {
    private static final Actor GM = new Actor("Game Master", "123e4567-e89b-12d3-a456-426614174000");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

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
