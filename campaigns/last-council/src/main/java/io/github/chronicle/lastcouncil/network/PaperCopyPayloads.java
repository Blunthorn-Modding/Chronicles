package io.github.chronicle.lastcouncil.network;

import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class PaperCopyPayloads {
    private PaperCopyPayloads() { }
    public static void register(){PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);}
    public record Request(UUID request,CouncilPayloads.Section section,int index) implements CustomPayload {
        public Request {Objects.requireNonNull(request);Objects.requireNonNull(section);if(index<0||index>123)throw new IllegalArgumentException("Invalid paper index");}
        public static final Id<Request> ID=new Id<>(Identifier.of("last_council","paper_copy"));
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request);b.writeEnumConstant(v.section);b.writeVarInt(v.index);},b->new Request(b.readUuid(),b.readEnumConstant(CouncilPayloads.Section.class),b.readVarInt()));
        public Id<Request> getId(){return ID;}
    }
}
