package io.github.chronicle.core.voice;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimpleVoiceRoomBackendTest {
    private record Room(UUID id, String name, boolean persistent, boolean hidden, Group.Type type) implements Group {
        public UUID getId() { return id; }
        public String getName() { return name; }
        public boolean isPersistent() { return persistent; }
        public boolean isHidden() { return hidden; }
        public Group.Type getType() { return type; }
        public boolean hasPassword() { return false; }
    }

    private static final class FakeApi {
        final Map<UUID, Group> groups = new HashMap<>();
        final Map<UUID, VoicechatConnection> connections = new HashMap<>();
        int builds;
        boolean failBefore, failAfter, rejectAssignment, missingWrapper;
        final VoicechatServerApi api = proxy(VoicechatServerApi.class, (method, args) -> switch (method) {
            case "getGroup" -> groups.containsKey(args[0]) || !missingWrapper ? groups.get(args[0])
                    : proxy(Group.class, (name, values) -> { throw new NullPointerException("missing wrapped group"); });
            case "getGroups" -> java.util.List.copyOf(groups.values());
            case "getConnectionOf" -> connections.get(args[0]);
            case "groupBuilder" -> builder();
            case "removeGroup" -> groups.remove(args[0]) != null;
            default -> throw new AssertionError("Unexpected API call: " + method);
        });

        Group.Builder builder() {
            class State { UUID id; String name; boolean persistent, hidden; Group.Type type; }
            State state = new State();
            return proxy(Group.Builder.class, (method, args, self) -> {
                switch (method) {
                    case "setId" -> state.id = (UUID) args[0];
                    case "setName" -> { String name = (String) args[0]; state.name = name.substring(0, Math.min(name.length(), 16)); }
                    case "setPersistent" -> state.persistent = (boolean) args[0];
                    case "setHidden" -> state.hidden = (boolean) args[0];
                    case "setType" -> state.type = (Group.Type) args[0];
                    case "build" -> {
                        builds++;
                        if (failBefore) throw new IllegalStateException("before creation");
                        Group group = new Room(state.id, state.name, state.persistent, state.hidden, state.type);
                        groups.put(state.id, group);
                        if (failAfter) throw new IllegalStateException("after creation");
                        return group;
                    }
                    default -> throw new AssertionError("Unexpected builder call: " + method);
                }
                return self;
            });
        }

        void connect(UUID player) {
            class State { Group group; }
            State state = new State();
            connections.put(player, proxy(VoicechatConnection.class, (method, args) -> switch (method) {
                case "getGroup" -> state.group;
                case "setGroup" -> { if (!rejectAssignment) state.group = (Group) args[0]; yield null; }
                default -> throw new AssertionError("Unexpected connection call: " + method);
            }));
        }
    }

    @FunctionalInterface private interface Call { Object invoke(String method, Object[] args); }
    @FunctionalInterface private interface BuilderCall { Object invoke(String method, Object[] args, Object self); }
    private static <T> T proxy(Class<T> type, Call call) {
        return proxy(type, (method, args, self) -> call.invoke(method, args));
    }
    private static <T> T proxy(Class<T> type, BuilderCall call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (self, method, args) -> call.invoke(method.getName(), args, self)));
    }

    @Test void lifecycleCreatesPrivateGroupAndReconcilesEntryAndExit() throws Exception {
        FakeApi fake = new FakeApi();
        UUID id = UUID.randomUUID(), player = UUID.randomUUID(), foreignId = UUID.randomUUID();
        Group foreign = new Room(foreignId, "foreign", false, false, Group.Type.NORMAL);
        fake.groups.put(foreignId, foreign);
        fake.connect(player);
        VoiceRoomGroups rooms = new VoiceRoomGroups(new SimpleVoiceRoomBackend(fake.api), () -> id);

        rooms.reconcile(Set.of("chamber"), Map.of());
        Group created = fake.api.getGroup(id);
        assertNotNull(created);
        assertEquals(id, created.getId());
        assertEquals("Room chamber", created.getName());
        assertTrue(created.isPersistent());
        assertTrue(created.isHidden());
        assertEquals(Group.Type.ISOLATED, created.getType());
        rooms.reconcile(Set.of("chamber"), Map.of(player, "chamber"));
        rooms.reconcile(Set.of("chamber"), Map.of(player, "chamber"));
        assertEquals(1, fake.builds);
        assertEquals(id, fake.connections.get(player).getGroup().getId());
        rooms.reconcile(Set.of("chamber"), Map.of());
        assertNull(fake.connections.get(player).getGroup());
        rooms.close();
        assertNull(fake.api.getGroup(id));
        assertSame(foreign, fake.api.getGroup(foreignId));
    }

    @Test void refusesForeignCollisionAndChangedPrivacy() {
        FakeApi fake = new FakeApi();
        SimpleVoiceRoomBackend backend = new SimpleVoiceRoomBackend(fake.api);
        UUID foreignId = UUID.randomUUID(), ownedId = UUID.randomUUID();
        Group foreign = new Room(foreignId, "Room chamber", true, true, Group.Type.ISOLATED);
        fake.groups.put(foreignId, foreign);
        assertThrows(IllegalStateException.class, () -> backend.ensure(foreignId, "Room chamber"));
        assertThrows(IllegalStateException.class, () -> backend.remove(foreignId));
        assertSame(foreign, fake.groups.get(foreignId));

        backend.ensure(ownedId, "Room chamber");
        Group changed = new Room(ownedId, "Room chamber", true, false, Group.Type.ISOLATED);
        fake.groups.put(ownedId, changed);
        assertThrows(IllegalStateException.class, () -> backend.ensure(ownedId, "Room chamber"));
        assertThrows(IllegalStateException.class, () -> backend.remove(ownedId));
        assertSame(changed, fake.groups.get(ownedId));
    }

    @Test void retriesUncertainCreationWithSameIdentity() {
        for (boolean after : new boolean[]{false, true}) {
            FakeApi fake = new FakeApi();
            SimpleVoiceRoomBackend backend = new SimpleVoiceRoomBackend(fake.api);
            UUID id = UUID.randomUUID();
            fake.failBefore = !after;
            fake.failAfter = after;
            assertThrows(IllegalStateException.class, () -> backend.ensure(id, "Room room"));
            fake.failBefore = fake.failAfter = false;
            backend.ensure(id, "Room room");
            assertEquals(after ? 1 : 2, fake.builds);
            assertNotNull(fake.api.getGroup(id));
            backend.remove(id);
            backend.remove(id);
            assertNull(fake.api.getGroup(id));
        }
    }

    @Test void absentConnectionCanBeRetriedAndRejectedAssignmentFails() {
        FakeApi fake = new FakeApi();
        SimpleVoiceRoomBackend backend = new SimpleVoiceRoomBackend(fake.api);
        UUID id = UUID.randomUUID(), player = UUID.randomUUID();
        backend.ensure(id, "Room room");
        backend.assign(player, id);
        assertNull(backend.groupOf(player));
        fake.connect(player);
        fake.rejectAssignment = true;
        assertThrows(IllegalStateException.class, () -> backend.assign(player, id));
        assertNull(backend.groupOf(player));
        fake.rejectAssignment = false;
        backend.assign(player, id);
        assertEquals(id, backend.groupOf(player));
        backend.assign(player, null);
        assertNull(backend.groupOf(player));
    }

    @Test void missingGroupWrapperInPinnedRuntimeCannotPreventCreationOrCleanup() {
        FakeApi fake = new FakeApi(); fake.missingWrapper = true;
        UUID id = UUID.randomUUID();
        Group absent = fake.api.getGroup(id);
        assertNotNull(absent); assertThrows(NullPointerException.class, absent::getId);
        SimpleVoiceRoomBackend backend = new SimpleVoiceRoomBackend(fake.api);
        backend.ensure(id, "Room room");
        assertEquals(1, fake.groups.size());
        backend.remove(id); backend.remove(id);
        assertTrue(fake.groups.isEmpty());
    }

    @Test void runtimeNameLimitDoesNotMergeDistinctManagedGroups() {
        FakeApi fake = new FakeApi(); SimpleVoiceRoomBackend backend = new SimpleVoiceRoomBackend(fake.api);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        backend.ensure(a, "Room an extremely long chamber name");
        backend.ensure(b, "Room an extremely long garden name");
        assertEquals(16, fake.groups.get(a).getName().length());
        assertEquals(fake.groups.get(a).getName(), fake.groups.get(b).getName());
        backend.remove(a); assertTrue(fake.groups.containsKey(b));
        backend.ensure(b, "Room an extremely long garden name");
    }
}
