package io.github.chronicle.core.play;

import io.github.chronicle.core.authoring.AuthoringRegistry;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayServiceTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GM = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final ObservationCatalog.Definition CUE = new ObservationCatalog.Definition(
            "quiet_below:cue", "Cue", "The stone carries a low vibration.", "minecraft:ambient.cave", ObservationCatalog.Audience.PARTICIPANTS);
    private static final ObservationCatalog.Definition REGION_CUE = new ObservationCatalog.Definition(
            "quiet_below:region_cue", "Region cue", "The chamber answers.", "", ObservationCatalog.Audience.REGION);
    private static final AuthoringRegistry REGISTRY = registry(4);

    @Test
    void clockAdvancesOnlyWhenToldAndCheckpointsWithoutWritingNoOps() throws IOException {
        Memory repository = new Memory();
        PlayService service = new PlayService(repository);
        service.advance(false);
        assertEquals(0, service.elapsedTicks());
        service.advance(true);
        assertEquals(1, service.elapsedTicks());
        assertEquals(0, service.state().elapsedTicks());
        service.checkpoint();
        assertEquals(1, service.state().elapsedTicks());
        assertEquals(1, repository.saves);
        service.checkpoint();
        assertEquals(1, repository.saves);
    }

    @Test
    void timerThresholdPausesAndResetFreshlyRearmsSharedCompletion() throws IOException {
        Memory repository = new Memory();
        PlayService service = new PlayService(repository);
        service.setParticipant(PLAYER, true);
        PlayState.Binding binding = service.bind(CUE, REGISTRY, new ObservationConfiguration(PlayState.Mode.TIMER, "", "", 2), false);
        for (int index = 0; index < 39; index++) service.advance(true);
        for (int index = 0; index < 50; index++) service.advance(false);
        assertFalse(service.due(binding, false, Map.of()));
        service.advance(true);
        assertTrue(service.due(binding, false, Map.of()));
        assertTrue(service.claim(CUE, REGISTRY, CUE.id(), PlayState.DeliverySource.TIMER, null, 1, List.of(PLAYER), Map.of()));
        assertFalse(service.claim(CUE, REGISTRY, CUE.id(), PlayState.DeliverySource.MANUAL, GM, 1, List.of(PLAYER), Map.of()));
        service.finish(CUE.id());
        assertEquals(PlayState.Status.DELIVERED, service.state().deliveries().get(CUE.id()).status());

        long resetAt = service.elapsedTicks();
        service.reset(CUE.id());
        PlayState.Binding reset = service.state().bindings().get(CUE.id());
        assertEquals(resetAt, reset.armedAtTicks());
        assertFalse(service.due(reset, false, Map.of()));
        assertFalse(service.state().deliveries().containsKey(CUE.id()));
        PlayService restarted = new PlayService(repository);
        assertEquals(resetAt, restarted.elapsedTicks());
        assertEquals(reset, restarted.state().bindings().get(CUE.id()));
    }

    @Test
    void prerequisitesApplyToManualAndAutomaticClaimsAndRecipientsMustParticipate() throws IOException {
        PlayService service = new PlayService(new Memory());
        service.setParticipant(PLAYER, true);
        service.bind(CUE, REGISTRY, new ObservationConfiguration(PlayState.Mode.CONDITION, "", "quiet_below:ready", 0), false);
        PlayState.Binding binding = service.state().bindings().get(CUE.id());
        assertFalse(service.due(binding, false, Map.of("quiet_below:ready", false)));
        assertTrue(service.due(binding, false, Map.of("quiet_below:ready", true)));
        assertThrows(IllegalStateException.class, () -> service.claim(CUE, REGISTRY, CUE.id(), PlayState.DeliverySource.MANUAL, GM, 1, List.of(PLAYER), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> service.claim(CUE, REGISTRY, CUE.id(), PlayState.DeliverySource.MANUAL, GM, 1, List.of(SECOND), Map.of("quiet_below:ready", true)));
        assertTrue(service.claim(CUE, REGISTRY, CUE.id(), PlayState.DeliverySource.MANUAL, GM, 1, List.of(PLAYER), Map.of("quiet_below:ready", true)));
    }

    @Test
    void saveReplaceOffAndRegionValidationAreExplicitAndPinned() throws IOException {
        PlayService service = new PlayService(new Memory());
        assertThrows(IllegalArgumentException.class, () -> service.bind(REGION_CUE, REGISTRY, new ObservationConfiguration(PlayState.Mode.MANUAL, "", "", 0), false));
        service.bind(REGION_CUE, REGISTRY, new ObservationConfiguration(PlayState.Mode.ENTRY, "city", "", 0), false);
        assertThrows(IllegalStateException.class, () -> service.bind(REGION_CUE, REGISTRY, new ObservationConfiguration(PlayState.Mode.ENTRY, "city", "", 0), false));
        assertThrows(IllegalStateException.class, () -> service.bind(CUE, REGISTRY, new ObservationConfiguration(PlayState.Mode.MANUAL, "", "", 0), true));
        service.validate(REGION_CUE, REGISTRY, REGION_CUE.id());
        assertThrows(IllegalStateException.class, () -> service.validate(
                new ObservationCatalog.Definition(REGION_CUE.id(), REGION_CUE.label(), REGION_CUE.text() + " changed", "", REGION_CUE.audience()), REGISTRY, REGION_CUE.id()));
        assertThrows(IllegalStateException.class, () -> service.validate(REGION_CUE, registry(5), REGION_CUE.id()));
        service.bind(REGION_CUE, REGISTRY, new ObservationConfiguration(PlayState.Mode.MANUAL, "city", "quiet_below:ready", 0), true);
        assertEquals(PlayState.Mode.MANUAL, service.state().bindings().get(REGION_CUE.id()).mode());
        service.off(REGION_CUE.id());
        assertFalse(service.state().bindings().containsKey(REGION_CUE.id()));
        long revision = service.state().revision();
        service.off(REGION_CUE.id());
        assertEquals(revision, service.state().revision());
    }

    @Test
    void participantLeaveRetainsDeliveryAndFailedSaveNeverPublishes() throws IOException {
        Memory repository = new Memory();
        PlayService service = new PlayService(repository);
        service.setParticipant(PLAYER, true);
        service.bind(CUE, REGISTRY, new ObservationConfiguration(PlayState.Mode.MANUAL, "", "", 0), false);
        assertTrue(service.claim(CUE, REGISTRY, CUE.id(), PlayState.DeliverySource.MANUAL, GM, 2, List.of(PLAYER), Map.of()));
        service.setParticipant(PLAYER, false);
        assertFalse(service.state().participants().contains(PLAYER));
        assertTrue(service.state().deliveries().containsKey(CUE.id()));

        PlayState before = service.state();
        repository.fail = true;
        assertThrows(IOException.class, () -> service.setParticipant(SECOND, true));
        assertSame(before, service.state());
    }

    @Test
    void prematureAutomaticClaimAndFailedFinishPreserveUncertainClaim() throws IOException {
        Memory repository = new Memory();
        PlayService service = new PlayService(repository);
        service.setParticipant(PLAYER, true);
        service.bind(CUE, REGISTRY, new ObservationConfiguration(PlayState.Mode.TIMER, "", "", 2), false);
        assertThrows(IllegalStateException.class, () -> service.claim(CUE, REGISTRY, CUE.id(),
                PlayState.DeliverySource.TIMER, null, 1, List.of(PLAYER), Map.of()));
        for (int index = 0; index < 40; index++) service.advance(true);
        assertTrue(service.claim(CUE, REGISTRY, CUE.id(), PlayState.DeliverySource.TIMER, null, 1, List.of(PLAYER), Map.of()));
        PlayState claimed = service.state();
        repository.fail = true;
        assertThrows(IOException.class, () -> service.finish(CUE.id()));
        assertSame(claimed, service.state());
        assertEquals(PlayState.Status.CLAIMED, service.state().deliveries().get(CUE.id()).status());
    }

    @Test
    void bindingAndDeliveryCapacityRefuseNewEntriesWithoutSaving() throws IOException {
        Memory repository = new Memory();
        Map<String, PlayState.Binding> bindings = new LinkedHashMap<>();
        for (int index = 0; index < PlayState.MAX_BINDINGS; index++) {
            String id = "test:item" + index;
            bindings.put(id, new PlayState.Binding(id, 1, CUE.fingerprint(), PlayState.Mode.MANUAL, "", 0, 0, 0, ""));
        }
        repository.state = new PlayState(1, 0, java.util.Set.of(), bindings, Map.of());
        PlayService bindingService = new PlayService(repository);
        assertThrows(IllegalArgumentException.class, () -> bindingService.bind(CUE, REGISTRY, new ObservationConfiguration(PlayState.Mode.MANUAL, "", "", 0), false));
        assertEquals(0, repository.saves);

        Map<String, PlayState.Delivery> deliveries = new LinkedHashMap<>();
        for (int index = 0; index < PlayState.MAX_DELIVERIES; index++) {
            String id = "test:receipt" + index;
            deliveries.put(id, new PlayState.Delivery(id, PlayState.Status.DELIVERED, PlayState.DeliverySource.TIMER,
                    null, 1, 0, List.of(PLAYER)));
        }
        PlayState.Binding binding = new PlayState.Binding(CUE.id(), 1, CUE.fingerprint(), PlayState.Mode.MANUAL, "", 0, 0, 0, "");
        repository.state = new PlayState(1, 0, java.util.Set.of(PLAYER), Map.of(CUE.id(), binding), deliveries);
        repository.saves = 0;
        PlayService deliveryService = new PlayService(repository);
        assertThrows(IllegalArgumentException.class, () -> deliveryService.claim(CUE, REGISTRY, CUE.id(),
                PlayState.DeliverySource.MANUAL, GM, 1, List.of(PLAYER), Map.of()));
        assertEquals(0, repository.saves);
    }

    private static AuthoringRegistry registry(long regionRevision) {
        AuthoringSite.Region region = new AuthoringSite.Region("city", "minecraft:overworld", regionRevision,
                new BlockPosition(0, 0, 0), new BlockPosition(10, 10, 10));
        return new AuthoringRegistry(regionRevision, Map.of(region.id(), region));
    }

    private static final class Memory implements PlayRepository {
        PlayState state = PlayState.empty();
        boolean fail;
        int saves;

        @Override public PlayState load() { return state; }

        @Override public void save(PlayState state) throws IOException {
            if (fail) throw new IOException("deliberate save failure");
            this.state = state;
            saves++;
        }
    }
}
