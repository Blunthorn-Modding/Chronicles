package io.github.chronicle.core.client;

import com.google.gson.Gson;
import io.github.chronicle.core.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import java.util.UUID;

public final class EventPoolEditorClient {
    private static final Gson JSON = new Gson();
    private static CoreMenuScreen waiting;
    private static UUID request;
    private EventPoolEditorClient() {}
    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { waiting = null; request = null; });
        ClientPlayNetworking.registerGlobalReceiver(EventPoolEditorPayloads.Response.ID, (payload, context) -> context.client().execute(() -> {
            var client = context.client();
            if (waiting != null && client.currentScreen == waiting && payload.token().equals(request)) {
                var parent = waiting; waiting = null; request = null;
                client.setScreen(new EventPoolEditorScreen(parent, payload, JSON.fromJson(payload.data(), EventPoolEditorView.class)));
            } else if (client.currentScreen instanceof EventPoolEditorScreen screen) screen.accept(payload);
        }));
    }
    public static void open(CoreMenuScreen parent) {
        if (!ClientPlayNetworking.canSend(EventPoolEditorPayloads.Request.ID)) return;
        waiting = parent; request = UUID.randomUUID(); ClientPlayNetworking.send(new EventPoolEditorPayloads.Request(0, request, 0, ""));
    }
    static void cancel(CoreMenuScreen parent) { if (waiting == parent) { waiting = null; request = null; } }
    static EventPoolEditorView parse(String text) { return JSON.fromJson(text, EventPoolEditorView.class); }
}
