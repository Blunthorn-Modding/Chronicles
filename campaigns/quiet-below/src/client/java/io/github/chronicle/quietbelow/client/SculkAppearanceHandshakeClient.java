package io.github.chronicle.quietbelow.client;

import io.github.chronicle.quietbelow.content.SculkAppearanceHandshake;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.minecraft.client.network.ClientConfigurationNetworkHandler;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;

/** Client half of the mandatory sculk block-state layout handshake. */
public final class SculkAppearanceHandshakeClient {
    private static final Set<ClientConfigurationNetworkHandler> ACCEPTED = ConcurrentHashMap.newKeySet();

    private SculkAppearanceHandshakeClient() { }

    public static void register() {
        ClientConfigurationConnectionEvents.INIT.register((handler, client) -> ACCEPTED.remove(handler));
        ClientConfigurationConnectionEvents.START.register((handler, client) -> {
            if (!ClientConfigurationNetworking.canSend(SculkAppearanceHandshake.Acknowledgement.ID)) {
                refuse(handler, Text.literal(
                        "The Quiet Below connection was refused: the server pack is missing or outdated and "
                                + "cannot safely encode sculk appearance states. Install the matching Quiet Below "
                                + "pack on both client and server (layout "
                                + SculkAppearanceHandshake.LAYOUT_VERSION + ")."));
            }
        });
        ClientConfigurationNetworking.registerGlobalReceiver(SculkAppearanceHandshake.Hello.ID,
                (payload, context) -> {
                    if (payload.layoutVersion() != SculkAppearanceHandshake.LAYOUT_VERSION) {
                        context.responseSender().disconnect(SculkAppearanceHandshake.versionMismatch(
                                "client", SculkAppearanceHandshake.LAYOUT_VERSION,
                                "server", payload.layoutVersion()));
                        return;
                    }
                    ACCEPTED.add(context.networkHandler());
                    context.responseSender().sendPacket(new SculkAppearanceHandshake.Acknowledgement(
                            SculkAppearanceHandshake.LAYOUT_VERSION));
                });
        ClientConfigurationConnectionEvents.COMPLETE.register((handler, client) -> {
            if (!ACCEPTED.remove(handler)) {
                refuse(handler, Text.literal(
                        "The Quiet Below connection was refused: the server did not complete the required sculk "
                                + "appearance handshake. Install the matching Quiet Below pack on both client and server."));
            }
        });
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> ACCEPTED.remove(handler));
    }

    private static void refuse(ClientConfigurationNetworkHandler handler, Text reason) {
        handler.onDisconnect(new DisconnectS2CPacket(reason));
    }
}
