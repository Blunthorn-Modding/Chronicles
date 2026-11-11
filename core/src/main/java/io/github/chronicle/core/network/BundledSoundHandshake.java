package io.github.chronicle.core.network;

import io.github.chronicle.core.server.EventSoundRegistry;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.*;
import net.minecraft.server.network.ServerPlayerConfigurationTask;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Compatibility gate for packaged audio IDs/hashes; this is not an anti-cheat check. */
public final class BundledSoundHandshake {
    private static final ServerPlayerConfigurationTask.Key KEY=new ServerPlayerConfigurationTask.Key("chronicle_core:bundled_audio_v1");
    private BundledSoundHandshake(){}
    public static void register(){
        PayloadTypeRegistry.configurationS2C().register(Hello.ID,Hello.CODEC);PayloadTypeRegistry.configurationC2S().register(Ack.ID,Ack.CODEC);
        ServerConfigurationNetworking.registerGlobalReceiver(Ack.ID,(payload,context)->{
            if(!payload.digest().equals(EventSoundRegistry.fingerprint())){context.responseSender().disconnect(mismatch());return;}
            try{((FabricServerConfigurationNetworkHandler)context.networkHandler()).completeTask(KEY);}catch(IllegalStateException unexpected){context.responseSender().disconnect(mismatch());}
        });
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler,server)->{
            if(!ServerConfigurationNetworking.canSend(handler,Hello.ID)){handler.disconnect(mismatch());return;}
            ((FabricServerConfigurationNetworkHandler)handler).addTask(new ServerPlayerConfigurationTask(){
                @Override public void sendPacket(Consumer<Packet<?>> sender){sender.accept(ServerConfigurationNetworking.createS2CPacket(new Hello(EventSoundRegistry.fingerprint())));}
                @Override public Key getKey(){return KEY;}
            });
        });
    }
    private static Text mismatch(){return Text.literal("Chronicle bundled audio is missing, outdated or different. Install the matching Chronicle and scenario mods/client pack on both sides.");}
    public record Hello(String digest)implements CustomPayload{
        public Hello{valid(digest);}
        public static final Id<Hello> ID=new Id<>(Identifier.of("chronicle_core","bundled_audio_hello_v1"));
        public static final PacketCodec<PacketByteBuf,Hello> CODEC=PacketCodec.ofStatic((b,p)->b.writeString(p.digest,64),b->new Hello(b.readString(64)));
        @Override public Id<Hello> getId(){return ID;}
    }
    public record Ack(String digest)implements CustomPayload{
        public Ack{valid(digest);}
        public static final Id<Ack> ID=new Id<>(Identifier.of("chronicle_core","bundled_audio_ack_v1"));
        public static final PacketCodec<PacketByteBuf,Ack> CODEC=PacketCodec.ofStatic((b,p)->b.writeString(p.digest,64),b->new Ack(b.readString(64)));
        @Override public Id<Ack> getId(){return ID;}
    }
    private static void valid(String digest){if(digest==null||!digest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid bundled sound fingerprint.");}
}
