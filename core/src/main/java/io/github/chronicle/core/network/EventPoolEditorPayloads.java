package io.github.chronicle.core.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

/** Private, bounded catalogue authoring transport. */
public final class EventPoolEditorPayloads {
    private EventPoolEditorPayloads() {}
    public static void register() { PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC); PayloadTypeRegistry.playS2C().register(Response.ID, Response.CODEC); }
    public record Request(int action, UUID token, long revision, String data) implements CustomPayload {
        public static final Id<Request> ID = new Id<>(Identifier.of("chronicle_core", "event_pool_editor_request"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.ofStatic((b,v)->{b.writeVarInt(v.action);b.writeUuid(v.token);b.writeLong(v.revision);b.writeString(v.data,32768);}, b->new Request(b.readVarInt(),b.readUuid(),b.readLong(),b.readString(32768)));
        public Request { if(action<0||action>2||token==null||revision<0||data==null||data.length()>32768) throw new IllegalArgumentException("Invalid pool request"); }
        public Id<Request> getId(){return ID;}
    }
    public record Response(UUID token, long revision, String data, String message, boolean editable) implements CustomPayload {
        public static final Id<Response> ID = new Id<>(Identifier.of("chronicle_core", "event_pool_editor_response"));
        public static final PacketCodec<RegistryByteBuf, Response> CODEC = PacketCodec.ofStatic((b,v)->{b.writeUuid(v.token);b.writeLong(v.revision);b.writeString(v.data,1048576);b.writeString(v.message,512);b.writeBoolean(v.editable);},b->new Response(b.readUuid(),b.readLong(),b.readString(1048576),b.readString(512),b.readBoolean()));
        public Response { if(token==null||revision<0||data==null||data.length()>1048576||message==null||message.length()>512)throw new IllegalArgumentException("Invalid pool response"); }
        public Id<Response> getId(){return ID;}
    }
}
