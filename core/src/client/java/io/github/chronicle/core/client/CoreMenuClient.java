package io.github.chronicle.core.client;

import io.github.chronicle.core.network.CoreMenuPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/** Transport for the server-authoritative Chronicle control menu.  The server decides when it opens. */
public final class CoreMenuClient {
    private static long closedToken;
    private CoreMenuClient() { }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CoreMenuPayloads.Response.ID,
                (payload, context) -> context.client().execute(() -> receive(context.client(), payload)));
    }

    static void receive(MinecraftClient client, CoreMenuPayloads.Response response) {
        io.github.chronicle.core.client.testing.CoreMenuSmoke.onView(response.view());
        if (client.currentScreen instanceof CoreMenuScreen screen) {
            screen.accept(response.view());
        } else if (response.open() && response.view().token() != closedToken) {
            client.setScreen(new CoreMenuScreen(response.view()));
        }
    }

    /** A late mutation response must not re-open an operator's deliberately closed menu. */
    static void closed(long token) {
        closedToken = token;
        if (ClientPlayNetworking.canSend(CoreMenuPayloads.Request.ID)) {
            ClientPlayNetworking.send(new CoreMenuPayloads.Request(io.github.chronicle.core.menu.CoreMenuModel.Action.CLOSE,
                    io.github.chronicle.core.menu.CoreMenuModel.Page.SESSION, "", "", token, 0, "", ""));
        }
    }
}
