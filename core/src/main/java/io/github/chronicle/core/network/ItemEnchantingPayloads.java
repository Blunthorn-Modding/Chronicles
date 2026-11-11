package io.github.chronicle.core.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

/** Bounded presentation and one-use gestures for the sequential item workshop. */
public final class ItemEnchantingPayloads {
    private ItemEnchantingPayloads() { }

    /** Coarse station authority retained for campaign gates; it is never sent to clients. */
    public enum State { READY, QUIET, UNPOWERED, UNAVAILABLE }
    public enum Phase { EMPTY, OFFERED, WORKING, RESULT }

    /** Campaign-owned, resource-packable art. Core does not select campaign textures. */
    public record Artwork(Identifier offSprite,Identifier idleSprite,Identifier workingSprite) {
        public Artwork {
            if(offSprite==null||idleSprite==null||workingSprite==null)throw new IllegalArgumentException("Workshop artwork is required");
        }
    }

    /** Items themselves are synchronized only through the native screen-handler slots. */
    public record View(int syncId,UUID sessionId,UUID actionToken,long revision,String title,Phase phase,
                       boolean available,boolean offerable,Artwork artwork,boolean opening) implements CustomPayload {
        public View {
            if(syncId<0||sessionId==null||actionToken==null||revision<0||phase==null||artwork==null)
                throw new IllegalArgumentException("Invalid workshop view");
            text(title,80);if(title.isBlank())throw new IllegalArgumentException("Missing workshop title");
            if(offerable&&!available)throw new IllegalArgumentException("Unavailable workshop cannot accept an offering");
            if(offerable&&(phase==Phase.WORKING||phase==Phase.RESULT))throw new IllegalArgumentException("Busy workshop cannot accept an offering");
        }
        public static final Id<View> ID=new Id<>(Identifier.of("chronicle_core","workshop_ritual_view_v1"));
        public static final PacketCodec<RegistryByteBuf,View> CODEC=PacketCodec.ofStatic((b,v)->{
            b.writeVarInt(v.syncId);b.writeUuid(v.sessionId);b.writeUuid(v.actionToken);b.writeVarLong(v.revision);b.writeString(v.title,80);
            b.writeByte(v.phase.ordinal());b.writeBoolean(v.available);b.writeBoolean(v.offerable);
            identifier(b,v.artwork.offSprite);identifier(b,v.artwork.idleSprite);identifier(b,v.artwork.workingSprite);b.writeBoolean(v.opening);
        },b->{
            int sync=b.readVarInt();UUID session=b.readUuid(),token=b.readUuid();long revision=b.readVarLong();String title=b.readString(80);int phase=b.readUnsignedByte();
            if(phase>=Phase.values().length)throw new IllegalArgumentException("Invalid workshop phase");
            boolean available=b.readBoolean(),offerable=b.readBoolean();
            Artwork artwork=new Artwork(identifier(b),identifier(b),identifier(b));
            return new View(sync,session,token,revision,title,Phase.values()[phase],available,offerable,artwork,b.readBoolean());
        });
        @Override public Id<View> getId(){return ID;}
    }

    /** Offers the complete stack currently in the one visible entry slot. */
    public record Offer(int syncId,UUID sessionId,UUID actionToken) implements CustomPayload {
        public Offer {
            if(syncId<0||sessionId==null||actionToken==null)throw new IllegalArgumentException("Invalid workshop offer");
        }
        public static final Id<Offer> ID=new Id<>(Identifier.of("chronicle_core","workshop_ritual_offer_v1"));
        public static final PacketCodec<RegistryByteBuf,Offer> CODEC=PacketCodec.ofStatic(
                (b,v)->{b.writeVarInt(v.syncId);b.writeUuid(v.sessionId);b.writeUuid(v.actionToken);},
                b->new Offer(b.readVarInt(),b.readUuid(),b.readUuid()));
        @Override public Id<Offer> getId(){return ID;}
    }

    public static void register(){PayloadTypeRegistry.playS2C().register(View.ID,View.CODEC);PayloadTypeRegistry.playC2S().register(Offer.ID,Offer.CODEC);}
    private static void identifier(RegistryByteBuf buffer,Identifier value){buffer.writeString(value.toString(),256);}
    private static Identifier identifier(RegistryByteBuf buffer){return Identifier.of(buffer.readString(256));}
    private static void text(String value,int max){if(value==null||value.length()>max)throw new IllegalArgumentException("Invalid workshop text");}
}
