package io.github.chronicle.core.voice;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VoiceRoomGroupsTest {
    static final UUID A = UUID.randomUUID(), B = UUID.randomUUID(), OUTSIDE = UUID.randomUUID();
    static class Fake implements VoiceRoomGroups.Backend {
        Map<UUID, String> groups = new HashMap<>();
        Map<UUID, UUID> players = new HashMap<>();
        Set<UUID> owned = new java.util.HashSet<>();
        int creates, assignments;
        boolean failAssign, uncertainCreate, failCreate, failRemove, uncertainRemove;
        public void ensure(UUID id, String name) throws IOException {
            if (failCreate) throw new IOException("Not created");
            if (groups.containsKey(id) && !owned.contains(id)) throw new IOException("Foreign group collision");
            String previous = groups.putIfAbsent(id, name);
            owned.add(id);
            if (previous == null) creates++; else assertEquals(name, previous);
            if (uncertainCreate) throw new IOException("Created but acknowledgement lost");
        }
        public void remove(UUID id) throws IOException {
            if (failRemove) throw new IOException("Not removed");
            if (groups.containsKey(id) && !owned.contains(id)) throw new IOException("Cannot remove a foreign group");
            groups.remove(id);
            owned.remove(id);
            if (uncertainRemove) throw new IOException("Removed but acknowledgement lost");
        }
        public UUID groupOf(UUID player) { return players.get(player); }
        public void assign(UUID player, UUID group) throws IOException {
            if (failAssign) throw new IOException("Cannot assign");
            assignments++;
            if (group == null) players.remove(player); else players.put(player, group);
        }
    }

    @Test void registrationCreatesEmptyGroupAndEntryExitAreAutomatic() throws Exception {
        Fake fake = new Fake(); VoiceRoomGroups service = new VoiceRoomGroups(fake);
        service.reconcile(Set.of("chamber"), Map.of());
        assertEquals(1, fake.creates); UUID group = service.routing().groups().get("chamber");
        service.reconcile(Set.of("chamber"), Map.of(A, "chamber", B, "chamber"));
        assertEquals(group, fake.players.get(A)); assertEquals(group, fake.players.get(B));
        service.reconcile(Set.of("chamber"), Map.of(A, "chamber", B, "chamber"));
        assertEquals(1, fake.creates); assertEquals(2, fake.assignments);
        service.reconcile(Set.of("chamber"), Map.of(B, "chamber"));
        assertNull(fake.players.get(A)); assertEquals(group, fake.players.get(B));
        service.reconcile(Set.of(), Map.of());
        assertTrue(fake.groups.isEmpty()); assertTrue(fake.players.isEmpty());
    }

    @Test void unconfiguredWorldKeepsStableAuthorizationWithoutBackendWork() throws Exception {
        Fake fake = new Fake(); VoiceRoomGroups service = new VoiceRoomGroups(fake);
        var original = service.routing();
        service.reconcile(Set.of(), Map.of()); service.reconcile(Set.of(), Map.of());
        assertSame(original, service.routing());
        assertEquals(0, fake.creates); assertEquals(0, fake.assignments);
    }

    @Test void privateRoutingAndManualGroupGuardsDoNotDependOnClientClaims() throws Exception {
        Fake fake = new Fake(); VoiceRoomGroups service = new VoiceRoomGroups(fake);
        service.reconcile(Set.of("a", "b"), Map.of(A, "a", B, "b"));
        var view = service.routing(); UUID a = view.groups().get("a"), foreign = UUID.randomUUID();
        assertFalse(view.permitsAudio(A, B)); assertFalse(view.permitsAudio(A, OUTSIDE));
        assertFalse(view.permitsAudio(OUTSIDE, A)); assertFalse(view.permitsAudio(null, A));
        assertTrue(view.permitsAudio(OUTSIDE, UUID.randomUUID()));
        assertTrue(view.permitsJoin(A, a)); assertFalse(view.permitsJoin(B, a));
        assertFalse(view.permitsJoin(OUTSIDE, a)); assertFalse(view.permitsJoin(A, foreign));
        assertTrue(view.permitsJoin(OUTSIDE, foreign)); assertFalse(view.permitsLeave(A));
        assertTrue(view.permitsLeave(OUTSIDE));
        service.reconcile(Set.of("a", "b"), Map.of(A, "a", B, "a"));
        assertTrue(service.routing().permitsAudio(A, B));
        assertFalse(view.permitsAudio(A, B)); // Old snapshots never mutate under an audio callback.
    }

    @Test void groupRemovalNeverTouchesAnUnrelatedGroupOrOutsideMember() throws Exception {
        Fake fake = new Fake(); UUID foreign = UUID.randomUUID();
        fake.groups.put(foreign, "Friends"); fake.players.put(OUTSIDE, foreign);
        VoiceRoomGroups service = new VoiceRoomGroups(fake);
        service.reconcile(Set.of("room"), Map.of(A, "room")); service.close();
        assertEquals(Map.of(foreign, "Friends"), fake.groups);
        assertEquals(Map.of(OUTSIDE, foreign), fake.players);
    }

    @Test void assignmentFailureBlocksAudioUntilReconciliationSucceeds() throws Exception {
        Fake fake = new Fake(); VoiceRoomGroups service = new VoiceRoomGroups(fake);
        service.reconcile(Set.of("room"), Map.of(A, "room")); fake.failAssign = true;
        assertThrows(IOException.class, () -> service.reconcile(Set.of(), Map.of()));
        assertTrue(service.routing().blocked()); assertFalse(service.routing().permitsAudio(A, B));
        assertFalse(service.routing().permitsJoin(OUTSIDE, UUID.randomUUID()));
        fake.failAssign = false; service.close();
        assertFalse(service.routing().blocked()); assertTrue(fake.groups.isEmpty());
    }

    @Test void uncertainCreatedGroupIsRetainedForSafeCleanup() throws Exception {
        Fake fake = new Fake(); fake.uncertainCreate = true;
        VoiceRoomGroups service = new VoiceRoomGroups(fake);
        assertThrows(IOException.class, () -> service.reconcile(Set.of("room"), Map.of()));
        assertTrue(service.routing().blocked()); assertEquals(1, fake.groups.size());
        fake.uncertainCreate = false; service.close(); assertTrue(fake.groups.isEmpty());
    }

    @Test void uncertainCreationCanBeRetriedBeforeAndAfterItsEffect() throws Exception {
        Fake fake = new Fake(); fake.failCreate = true;
        VoiceRoomGroups service = new VoiceRoomGroups(fake);
        assertThrows(IOException.class, () -> service.reconcile(Set.of("room"), Map.of()));
        assertTrue(service.routing().blocked()); assertTrue(fake.groups.isEmpty());
        fake.failCreate = false; fake.uncertainCreate = true;
        assertThrows(IOException.class, () -> service.reconcile(Set.of("room"), Map.of()));
        assertTrue(service.routing().blocked()); assertEquals(1, fake.creates);
        UUID retained = fake.groups.keySet().iterator().next();
        fake.uncertainCreate = false;
        service.reconcile(Set.of("room"), Map.of(A, "room"));
        assertEquals(retained, fake.players.get(A)); assertEquals(1, fake.creates);
        assertFalse(service.routing().blocked());
    }

    @Test void uncertainRemovalCanBeRetriedBeforeAndAfterItsEffect() throws Exception {
        Fake fake = new Fake(); VoiceRoomGroups service = new VoiceRoomGroups(fake);
        service.reconcile(Set.of("room"), Map.of(A, "room")); fake.failRemove = true;
        assertThrows(IOException.class, service::close); assertTrue(service.routing().blocked());
        fake.failRemove = false; fake.uncertainRemove = true;
        assertThrows(IOException.class, service::close);
        assertTrue(service.routing().blocked()); assertTrue(fake.groups.isEmpty());
        fake.uncertainRemove = false; service.close();
        assertFalse(service.routing().blocked()); assertTrue(service.routing().groups().isEmpty());
    }

    @Test void membershipIsNotArtificiallyLimitedAndInvalidChangesBlockOldAuthorization() throws Exception {
        Fake fake = new Fake(); VoiceRoomGroups service = new VoiceRoomGroups(fake);
        Map<UUID, String> members = new HashMap<>();
        for (int i = 0; i < 1025; i++) members.put(UUID.randomUUID(), "room");
        service.reconcile(Set.of("room"), members);
        assertEquals(1025, service.routing().membership().size());
        assertThrows(IllegalArgumentException.class, () -> service.reconcile(Set.of(), members));
        assertTrue(service.routing().blocked());
        assertFalse(service.routing().permitsAudio(OUTSIDE, A));
        service.close(); assertTrue(fake.players.isEmpty());
    }

    @Test void invalidMembershipCannotBecomeAnAudioAuthorization() throws Exception {
        Fake fake = new Fake(); VoiceRoomGroups service = new VoiceRoomGroups(fake);
        assertThrows(IllegalArgumentException.class, () -> service.reconcile(Set.of("room"), Map.of(A, "missing")));
        assertTrue(fake.groups.isEmpty()); assertTrue(service.routing().membership().isEmpty());
        service.reconcile(Set.of("room"), Map.of(A, "room")); service.block();
        assertFalse(service.routing().permitsAudio(A, A));
    }

    @Test void aRejectedForeignIdCollisionCannotBeDeletedDuringCleanup() {
        Fake fake = new Fake(); UUID foreign = UUID.randomUUID(); fake.groups.put(foreign, "Friends");
        VoiceRoomGroups service = new VoiceRoomGroups(fake, () -> foreign);
        assertThrows(IOException.class, () -> service.reconcile(Set.of("room"), Map.of()));
        assertThrows(IOException.class, service::close);
        assertTrue(service.routing().blocked());
        assertEquals(Map.of(foreign, "Friends"), fake.groups);
    }
}
