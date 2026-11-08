package io.github.chronicle.lastcouncil.network;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Bounded operator-only source projections, independent of live cast data. */
public final class CataloguePayloads {
    private CataloguePayloads() { }
    public enum Category { HOUSES, SECRETS, GOALS, KNOWLEDGE }
    public static void register() { PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC); PayloadTypeRegistry.playS2C().register(View.ID,View.CODEC); }
    public record Request(UUID request,Category category,String query,int page,int selected,int part) implements CustomPayload {
        public static final Id<Request> ID=id("catalogue_request");
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request);b.writeEnumConstant(v.category);b.writeString(v.query,128);b.writeVarInt(v.page);b.writeVarInt(v.selected);b.writeVarInt(v.part);},b->new Request(b.readUuid(),b.readEnumConstant(Category.class),b.readString(128),b.readVarInt(),b.readVarInt(),b.readVarInt()));
        public Id<Request> getId(){return ID;}
    }
    public record View(UUID request,boolean opening,Category category,String query,int page,int pages,int selected,List<String> labels,int part,int parts,String detail) implements CustomPayload {
        public View { labels=List.copyOf(labels);if(labels.size()>6||page<0||pages<1||page>=pages||selected<0||selected>5||part<0||parts<1||part>=parts)throw new IllegalArgumentException("Invalid catalogue page"); }
        public static final Id<View> ID=id("catalogue_view");
        public static final PacketCodec<RegistryByteBuf,View> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request);b.writeBoolean(v.opening);b.writeEnumConstant(v.category);b.writeString(v.query,128);b.writeVarInt(v.page);b.writeVarInt(v.pages);b.writeVarInt(v.selected);b.writeVarInt(v.labels.size());for(String label:v.labels)b.writeString(label,256);b.writeVarInt(v.part);b.writeVarInt(v.parts);b.writeString(v.detail,4096);},b->{UUID id=b.readUuid();boolean open=b.readBoolean();Category c=b.readEnumConstant(Category.class);String q=b.readString(128);int page=b.readVarInt(),pages=b.readVarInt(),selected=b.readVarInt(),n=b.readVarInt();if(n<0||n>6)throw new IllegalArgumentException("Invalid catalogue labels");List<String> labels=new ArrayList<>();for(int i=0;i<n;i++)labels.add(b.readString(256));return new View(id,open,c,q,page,pages,selected,labels,b.readVarInt(),b.readVarInt(),b.readString(4096));});
        public Id<View> getId(){return ID;}
    }
    private static <T extends CustomPayload> CustomPayload.Id<T> id(String path){return new CustomPayload.Id<>(Identifier.of("last_council",path));}
}
