package io.github.chronicle.core.voice;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.*;
import io.github.chronicle.core.server.VoiceRooms;

import java.util.UUID;

/** Loaded only by Simple Voice Chat through its optional Fabric entrypoint. */
public final class ChronicleVoicechatPlugin implements VoicechatPlugin {
    private volatile SimpleVoiceRoomBackend backend;
    @Override public String getPluginId() { return "chronicle_core:voice_rooms"; }
    @Override public void registerEvents(EventRegistration events) {
        events.registerEvent(VoicechatServerStartedEvent.class, e -> {
            SimpleVoiceRoomBackend next = new SimpleVoiceRoomBackend(e.getVoicechat());
            backend = next; VoiceRooms.attach(next);
            io.github.chronicle.core.server.testing.VoiceRoomsTrial.voiceStarted(e.getVoicechat());
        });
        events.registerEvent(VoicechatServerStoppedEvent.class, e -> {
            SimpleVoiceRoomBackend current = backend;
            if (current != null && current.isApi(e.getVoicechat())) {
                VoiceRooms.detach(current); backend = null;
            }
        });
        events.registerEvent(JoinGroupEvent.class, e -> {
            if (internal(e)) return;
            var view = snapshot(e);
            if (active(view) && !view.permitsJoin(player(e.getConnection()), e.getGroup().getId()) || stale(e, view)) e.cancel();
        });
        events.registerEvent(LeaveGroupEvent.class, e -> {
            if (internal(e)) return;
            var view = snapshot(e);
            if (active(view) && !view.permitsLeave(player(e.getConnection())) || stale(e, view)) e.cancel();
        });
        events.registerEvent(CreateGroupEvent.class, e -> {
            if (internal(e)) return;
            var view = snapshot(e);
            UUID player = player(e.getConnection());
            if (active(view) && (view.blocked() || player != null && view.membership().containsKey(player)) || stale(e, view)) e.cancel();
        });
        events.registerEvent(RemoveGroupEvent.class, e -> {
            if (internal(e)) return;
            var view = snapshot(e);
            if (active(view) && (view.blocked() || view.groups().containsValue(e.getGroup().getId())) || stale(e, view)) e.cancel();
        });
        events.registerEvent(EntitySoundPacketEvent.class, this::sound);
        events.registerEvent(LocationalSoundPacketEvent.class, this::sound);
        events.registerEvent(StaticSoundPacketEvent.class, this::sound);
    }

    private void sound(SoundPacketEvent<?> event) {
        var view = snapshot(event);
        if (SoundPacketEvent.SOURCE_PLUGIN.equals(event.getSource())) return;
        // Per-recipient guard also covers proximity/spectator paths and group membership races.
        if (active(view) && !view.permitsAudio(player(event.getSenderConnection()), player(event.getReceiverConnection())) || stale(event, view)) event.cancel();
    }

    private VoiceRoomGroups.Routing snapshot(ServerEvent event) {
        SimpleVoiceRoomBackend adapter = backend;
        if (adapter == null || !adapter.isApi(event.getVoicechat()))
            return new VoiceRoomGroups.Routing(java.util.Map.of(), java.util.Map.of(), true);
        return VoiceRooms.routing(adapter);
    }
    private boolean internal(ServerEvent event) {
        SimpleVoiceRoomBackend adapter = backend;
        return adapter != null && adapter.isApi(event.getVoicechat()) && adapter.internalOperation();
    }
    private boolean stale(ServerEvent event, VoiceRoomGroups.Routing view) { return snapshot(event) != view; }
    private static boolean active(VoiceRoomGroups.Routing view) { return view.blocked() || !view.groups().isEmpty(); }
    private static UUID player(VoicechatConnection connection) {
        return connection == null || connection.getPlayer() == null ? null : connection.getPlayer().getUuid();
    }
}
