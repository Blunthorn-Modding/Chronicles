package io.github.chronicle.core.voice;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatServerApi;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Uses only the public Simple Voice Chat API. No Enhanced Groups dependency is required. */
public final class SimpleVoiceRoomBackend implements VoiceRoomGroups.Backend {
    private final VoicechatServerApi api;
    private final Map<UUID, String> owned = new HashMap<>();
    private final ThreadLocal<Boolean> internal = ThreadLocal.withInitial(() -> false);

    public SimpleVoiceRoomBackend(VoicechatServerApi api) { this.api = java.util.Objects.requireNonNull(api); }

    @Override public void ensure(UUID id, String name) {
        String label = name.substring(0, Math.min(name.length(), 16));
        Group existing = find(id);
        if (existing != null) {
            if (!label.equals(owned.get(id)) || !valid(existing, id, label))
                throw new IllegalStateException("Voice room group ownership or privacy changed.");
            return;
        }
        // An ID collision is checked above before reserving our new group ID.
        owned.put(id, label);
        Group created;
        internal.set(true);
        try { created = api.groupBuilder().setId(id).setName(label)
                .setPersistent(true).setHidden(true).setType(Group.Type.ISOLATED).build(); }
        finally { internal.remove(); }
        if (!valid(created, id, label) || !valid(find(id), id, label))
            throw new IllegalStateException("Simple Voice Chat did not create the private room group.");
    }

    @Override public void remove(UUID id) {
        Group existing = find(id);
        if (existing == null) { owned.remove(id); return; }
        String name = owned.get(id);
        if (name == null || !valid(existing, id, name))
            throw new IllegalStateException("Refusing to remove an unowned or changed voice group.");
        internal.set(true);
        try { api.removeGroup(id); }
        finally { internal.remove(); }
        if (find(id) != null) throw new IllegalStateException("Voice room group removal did not complete.");
        owned.remove(id);
    }

    @Override public UUID groupOf(UUID player) {
        var connection = api.getConnectionOf(player);
        Group group = connection == null ? null : connection.getGroup();
        return group == null ? null : group.getId();
    }

    @Override public void assign(UUID player, UUID group) {
        var connection = api.getConnectionOf(player);
        if (connection == null) return; // Reconciliation retries when the client connects.
        Group target = group == null ? null : find(group);
        if (group != null && (target == null || !owned.containsKey(group)))
            throw new IllegalStateException("The managed voice room group is unavailable.");
        internal.set(true);
        try { connection.setGroup(target); }
        finally { internal.remove(); }
        if (!java.util.Objects.equals(group, groupOf(player)))
            throw new IllegalStateException("Voice room membership was not accepted.");
    }

    public boolean isApi(VoicechatServerApi other) { return api == other; }
    public boolean internalOperation() { return internal.get(); }
    private Group find(UUID id) {
        // SVC 2.6.22 getGroup(missingId) returns a non-null wrapper around null.
        // The public collection contains only real groups and works across that API quirk.
        return api.getGroups().stream().filter(group -> id.equals(group.getId())).findFirst().orElse(null);
    }
    private static boolean valid(Group group, UUID id, String name) {
        return group != null && id.equals(group.getId()) && name.equals(group.getName())
                && group.isHidden() && group.isPersistent() && group.getType() == Group.Type.ISOLATED;
    }
}
