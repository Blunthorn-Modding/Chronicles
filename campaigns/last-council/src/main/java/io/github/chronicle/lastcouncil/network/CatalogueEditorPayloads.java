package io.github.chronicle.lastcouncil.network;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import io.github.chronicle.lastcouncil.network.CataloguePayloads.Category;

/** Operator-only bounded source editor protocol. */
public final class CatalogueEditorPayloads {
    private CatalogueEditorPayloads() {}
    public static final int CHUNK_BYTES=48*1024;
    public enum Action { OPEN, VIEW, LOAD, CREATE, SAVE, DELETE }
    public record Row(int index,String label) {}
    public record Card(String title,String body) {}
    public record HouseRef(String id,String label) {}
    public static void register(){PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playC2S().register(Upload.ID,Upload.CODEC);PayloadTypeRegistry.playS2C().register(View.ID,View.CODEC);PayloadTypeRegistry.playS2C().register(Chunk.ID,Chunk.CODEC);}
    public record Request(UUID request,Action action,Category category,String query,int page,int selected,int part,String digest) implements CustomPayload {
        public static final Id<Request> ID=id("content_editor_request");
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request);b.writeEnumConstant(v.action);b.writeEnumConstant(v.category);b.writeString(v.query,128);b.writeVarInt(v.page);b.writeVarInt(v.selected);b.writeVarInt(v.part);b.writeString(v.digest,64);},b->new Request(b.readUuid(),b.readEnumConstant(Action.class),b.readEnumConstant(Category.class),b.readString(128),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readString(64)));
        public Id<Request> getId(){return ID;}
    }
    public record View(UUID request,boolean opening,Category category,String query,int page,int pages,int selected,List<Row> rows,int part,int parts,List<Card> cards,List<HouseRef> houses,boolean editable,String reason,String message,String digest) implements CustomPayload {
        public View { rows=List.copyOf(rows);cards=List.copyOf(cards);houses=List.copyOf(houses);if(rows.size()>6||cards.size()>4||houses.size()>32)throw new IllegalArgumentException("editor view too large"); }
        public static final Id<View> ID=id("content_editor_view");
        public static final PacketCodec<RegistryByteBuf,View> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request);b.writeBoolean(v.opening);b.writeEnumConstant(v.category);b.writeString(v.query,128);b.writeVarInt(v.page);b.writeVarInt(v.pages);b.writeVarInt(v.selected);b.writeVarInt(v.rows.size());for(var r:v.rows){b.writeVarInt(r.index);b.writeString(r.label,256);}b.writeVarInt(v.part);b.writeVarInt(v.parts);b.writeVarInt(v.cards.size());for(var c:v.cards){b.writeString(c.title,256);b.writeString(c.body,16384);}b.writeVarInt(v.houses.size());for(var h:v.houses){b.writeString(h.id,128);b.writeString(h.label,256);}b.writeBoolean(v.editable);b.writeString(v.reason,512);b.writeString(v.message,512);b.writeString(v.digest,64);},b->{UUID id=b.readUuid();boolean open=b.readBoolean();Category c=b.readEnumConstant(Category.class);String q=b.readString(128);int page=b.readVarInt(),pages=b.readVarInt(),selected=b.readVarInt(),n=bounded(b.readVarInt(),6);List<Row> rows=new ArrayList<>();for(int i=0;i<n;i++)rows.add(new Row(b.readVarInt(),b.readString(256)));int part=b.readVarInt(),parts=b.readVarInt(),nc=bounded(b.readVarInt(),4);List<Card> cards=new ArrayList<>();for(int i=0;i<nc;i++)cards.add(new Card(b.readString(256),b.readString(16384)));int nh=bounded(b.readVarInt(),32);List<HouseRef> houses=new ArrayList<>();for(int i=0;i<nh;i++)houses.add(new HouseRef(b.readString(128),b.readString(256)));return new View(id,open,c,q,page,pages,selected,rows,part,parts,cards,houses,b.readBoolean(),b.readString(512),b.readString(512),b.readString(64));});
        public Id<View> getId(){return ID;}
    }
    public record Chunk(UUID request,String digest,int index,int count,byte[] data) implements CustomPayload {
        public static final Id<Chunk> ID=id("content_editor_chunk");
        public static final PacketCodec<RegistryByteBuf,Chunk> CODEC=PacketCodec.ofStatic((b,v)->writeChunk(b,v.request,v.digest,v.index,v.count,v.data),b->new Chunk(b.readUuid(),b.readString(64),b.readVarInt(),b.readVarInt(),b.readByteArray(CHUNK_BYTES)));
        public Id<Chunk> getId(){return ID;}
    }
    public record Upload(UUID request,String digest,int index,int count,byte[] data) implements CustomPayload {
        public static final Id<Upload> ID=id("content_editor_upload");
        public static final PacketCodec<RegistryByteBuf,Upload> CODEC=PacketCodec.ofStatic((b,v)->writeChunk(b,v.request,v.digest,v.index,v.count,v.data),b->new Upload(b.readUuid(),b.readString(64),b.readVarInt(),b.readVarInt(),b.readByteArray(CHUNK_BYTES)));
        public Id<Upload> getId(){return ID;}
    }
    private static void writeChunk(RegistryByteBuf b,UUID r,String d,int i,int n,byte[] data){if(data.length>CHUNK_BYTES)throw new IllegalArgumentException("chunk too large");b.writeUuid(r);b.writeString(d,64);b.writeVarInt(i);b.writeVarInt(n);b.writeByteArray(data);}
    private static int bounded(int n,int max){if(n<0||n>max)throw new IllegalArgumentException("invalid count");return n;}
    private static <T extends CustomPayload> CustomPayload.Id<T> id(String path){return new CustomPayload.Id<>(Identifier.of("last_council",path));}
}
