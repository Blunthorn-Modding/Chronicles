package io.github.chronicle.core.client;

import io.github.chronicle.core.network.BundledSoundHandshake;
import io.github.chronicle.core.server.EventSoundRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.network.ClientConfigurationNetworkHandler;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;

final class BundledSoundHandshakeClient {
    private BundledSoundHandshakeClient(){}
    private static final Set<ClientConfigurationNetworkHandler> ACCEPTED=ConcurrentHashMap.newKeySet();
    static void register(){
        ClientConfigurationConnectionEvents.INIT.register((handler,client)->ACCEPTED.remove(handler));
        ClientConfigurationConnectionEvents.START.register((handler,client)->{if(!ClientConfigurationNetworking.canSend(BundledSoundHandshake.Ack.ID))handler.onDisconnect(new DisconnectS2CPacket(Text.literal("This server lacks the required Chronicle audio compatibility check. Use matching complete mods/pack.")));});
        ClientConfigurationNetworking.registerGlobalReceiver(BundledSoundHandshake.Hello.ID,(payload,context)->{
        String local=EventSoundRegistry.fingerprint();if(!payload.digest().equals(local)){context.responseSender().disconnect(Text.literal("Chronicle audio assets differ from this server. Install the matching complete client pack."));return;}
        ACCEPTED.add(context.networkHandler());context.responseSender().sendPacket(new BundledSoundHandshake.Ack(local));
    });
        ClientConfigurationConnectionEvents.COMPLETE.register((handler,client)->{if(!ACCEPTED.remove(handler))handler.onDisconnect(new DisconnectS2CPacket(Text.literal("Chronicle audio compatibility was not confirmed. Use matching complete mods/pack.")));});
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler,client)->ACCEPTED.remove(handler));
    }
}
