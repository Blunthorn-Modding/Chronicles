package io.github.chronicle.core.server;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeparturesTest {
    private static final UUID ACTOR=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ABOARD=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OFFLINE=UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test void offlineEnrolledParticipantPreventsConclusion(){
        assertFalse(Departures.conclusionRequired(Set.of(ACTOR,ABOARD,OFFLINE),Set.of(),Set.of(ACTOR,ABOARD)));
        assertTrue(Departures.conclusionRequired(Set.of(ACTOR,ABOARD,OFFLINE),Set.of(OFFLINE),Set.of(ACTOR,ABOARD)));
    }

    @Test void unrelatedAndAlreadyDepartedAccountsDoNotChangeExactOutcome(){
        assertTrue(Departures.conclusionRequired(Set.of(ACTOR),Set.of(UUID.randomUUID()),Set.of(ACTOR)));
        assertFalse(Departures.conclusionRequired(Set.of(ACTOR,OFFLINE),Set.of(UUID.randomUUID()),Set.of(ACTOR)));
    }

    @Test void areaOperationIdentityIsStableAndNamespacedFromDeparture(){
        UUID departure=UUID.fromString("00000000-0000-0000-0000-000000000010");
        assertEquals(UUID.nameUUIDFromBytes((departure+":area").getBytes(StandardCharsets.UTF_8)),Departures.areaOperationId(departure));
        assertEquals(Departures.areaOperationId(departure),Departures.areaOperationId(departure));
    }
}
