package io.github.chronicle.core.voice;

import io.github.chronicle.core.authoring.AuthoringSite;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Server-thread group lifecycle. Audio callbacks read only the immutable routing snapshot. */
public final class VoiceRoomGroups {
    /** Calls are idempotent by owned ID; the adapter must never reuse a foreign group. */
    public interface Backend {
        /** Create if absent, otherwise verify the same name and hidden/isolated policy. */
        void ensure(UUID group, String name) throws Exception;
        /** Ensure absence; never delete foreign groups. Refuse unknown ownership rather than guessing. */
        void remove(UUID group) throws Exception;
        UUID groupOf(UUID player) throws Exception;
        void assign(UUID player, UUID group) throws Exception;
    }

    public record Routing(Map<UUID, String> membership, Map<String, UUID> groups, boolean blocked) {
        public Routing {
            membership = Map.copyOf(membership);
            groups = Map.copyOf(groups);
        }

        /** Privacy applies in both directions, independently of a client's current group. */
        public boolean permitsAudio(UUID sender, UUID receiver) {
            if (blocked || sender == null || receiver == null) return false;
            return Objects.equals(membership.get(sender), membership.get(receiver));
        }

        public boolean permitsJoin(UUID player, UUID target) {
            if (blocked || player == null || target == null) return false;
            String room = membership.get(player);
            if (room != null) return target.equals(groups.get(room));
            return !groups.containsValue(target);
        }

        public boolean permitsLeave(UUID player) { return !blocked && player != null && !membership.containsKey(player); }
    }

    private final Backend backend;
    private final Supplier<UUID> ids;
    private final Map<String, UUID> owned = new HashMap<>();
    private Set<UUID> previousMembers = Set.of();
    private volatile Routing routing = new Routing(Map.of(), Map.of(), false);

    public VoiceRoomGroups(Backend backend) { this(backend, UUID::randomUUID); }
    VoiceRoomGroups(Backend backend, Supplier<UUID> ids) {
        this.backend = Objects.requireNonNull(backend);
        this.ids = Objects.requireNonNull(ids);
    }
    public Routing routing() { return routing; }

    /** Includes empty rooms: assigning the subtype creates its group before anybody arrives. */
    public void reconcile(Set<String> roomIds, Map<UUID, String> membership) throws Exception {
        if (roomIds != null && membership != null && roomIds.isEmpty() && membership.isEmpty()
                && owned.isEmpty() && previousMembers.isEmpty()) {
            if (routing.blocked()) routing = new Routing(Map.of(), Map.of(), false);
            return;
        }
        block();
        validate(roomIds, membership);
        // No audio is authorized against a half-created/deleted assignment, even on failure.
        routing = new Routing(membership, owned, true);
        for (String room : roomIds.stream().sorted().toList()) {
            // Retain the ID across both pre-effect and post-effect creation failures.
            UUID group = owned.computeIfAbsent(room, ignored -> Objects.requireNonNull(ids.get()));
            backend.ensure(group, displayName(room));
        }
        Set<UUID> reconcile = new HashSet<>(previousMembers);
        reconcile.addAll(membership.keySet());
        previousMembers = Set.copyOf(reconcile);
        for (UUID player : reconcile) {
            String room = membership.get(player);
            UUID desired = room == null ? null : owned.get(room);
            UUID actual = backend.groupOf(player);
            if (desired != null) {
                if (!desired.equals(actual)) backend.assign(player, desired);
            } else if (actual != null && owned.containsValue(actual)) {
                backend.assign(player, null);
            }
        }
        for (String room : Set.copyOf(owned.keySet())) {
            if (!roomIds.contains(room)) {
                backend.remove(owned.get(room));
                owned.remove(room);
            }
        }
        previousMembers = Set.copyOf(membership.keySet());
        routing = new Routing(membership, owned, false);
    }

    /** Keep privacy closed until the owner reconciles or shuts down successfully. */
    public void block() {
        Routing previous = routing;
        routing = new Routing(previous.membership(), previous.groups(), true);
    }

    public void close() throws Exception { reconcile(Set.of(), Map.of()); }

    public static String displayName(String room) {
        String name = "Room " + room.substring(room.lastIndexOf(':') + 1).replace('_', ' ');
        return name.substring(0, Math.min(name.length(), 16));
    }

    private static void validate(Set<String> rooms, Map<UUID, String> members) {
        if (rooms == null || rooms.size() > 1024 || members == null)
            throw new IllegalArgumentException("Too many or missing voice rooms");
        for (String room : rooms)
            if (room == null || !AuthoringSite.ID.matcher(room).matches())
                throw new IllegalArgumentException("Invalid voice room ID");
        for (var entry : members.entrySet())
            if (entry.getKey() == null || !rooms.contains(entry.getValue()))
                throw new IllegalArgumentException("Voice member references an unknown room");
    }
}
