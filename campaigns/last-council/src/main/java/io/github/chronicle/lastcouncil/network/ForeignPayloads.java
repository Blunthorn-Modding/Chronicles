package io.github.chronicle.lastcouncil.network;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Public diplomacy and separately authorized reusable realm editing. */
public final class ForeignPayloads {
    public static final UUID NONE=new UUID(0,0);
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();
    public enum Action { OPEN, STATE, CREATE, UPDATE, DELETE, CLOSE }
    private ForeignPayloads(){}
    public static void register(){PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playS2C().register(View.ID,View.CODEC);}
    public record Request(UUID request,Action action,long token,long revision,String realm,String name,String description,int historyPage) implements CustomPayload {
        public Request(UUID request,Action action,long token,long revision,String realm,String name,String description){this(request,action,token,revision,realm,name,description,0);}
        public Request {Objects.requireNonNull(request);Objects.requireNonNull(action);text(realm,128);text(name,320);text(description,8192);if(revision<0||historyPage<0||historyPage>7)throw new IllegalArgumentException("Invalid catalogue revision or history page");}
        public static final Id<Request> ID=id("foreign_request");
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request);b.writeEnumConstant(v.action);b.writeLong(v.token);b.writeLong(v.revision);b.writeString(v.realm,128);b.writeString(v.name,320);b.writeString(v.description,8192);b.writeVarInt(v.historyPage);},b->new Request(b.readUuid(),b.readEnumConstant(Action.class),b.readLong(),b.readLong(),b.readString(128),b.readString(320),b.readString(8192),b.readVarInt()));
        public Id<Request> getId(){return ID;}
    }
    public record Row(String id,String name,boolean atWar){public Row{text(id,128);text(name,320);}}
    public record Detail(String id,String name,String description,boolean atWar,long revision,List<String> history,boolean referenced,int historyPage,int historyPages){
        public Detail(String id,String name,String description,boolean atWar,long revision,List<String> history,boolean referenced){this(id,name,description,atWar,revision,history,referenced,0,1);}
        public Detail{text(id,128);text(name,320);text(description,8192);history=List.copyOf(history);if(revision<0||history.size()>32||historyPages<1||historyPages>8||historyPage<0||historyPage>=historyPages)throw new IllegalArgumentException("Invalid public history");history.forEach(s->text(s,2048));}}
    public record View(UUID request,boolean opening,long token,long revision,long catalogRevision,List<Row> rows,Detail detail,boolean editing,boolean editable,String message,boolean closed,boolean saved) implements CustomPayload {
        public View(UUID request,boolean opening,long token,long revision,long catalogRevision,List<Row> rows,Detail detail,boolean editing,boolean editable,String message,boolean closed){this(request,opening,token,revision,catalogRevision,rows,detail,editing,editable,message,closed,false);}
        public View{Objects.requireNonNull(request);rows=List.copyOf(rows);if(rows.size()>64||revision<0||catalogRevision<0)throw new IllegalArgumentException("Invalid realm view");text(message,1024);}
        public static final Id<View> ID=id("foreign_view");
        public static final PacketCodec<RegistryByteBuf,View> CODEC=PacketCodec.ofStatic((b,v)->b.writeString(encode(v),262144),b->JSON.fromJson(BookConfig.strictObject(b.readString(262144)),View.class));
        public Id<View> getId(){return ID;}
    }
    public static boolean fits(View view){return JSON.toJson(view).length()<=262144;}
    private static String encode(View v){String s=JSON.toJson(v);if(s.length()>262144)throw new IllegalArgumentException("Foreign view too large");return s;}
    private static void text(String s,int max){if(s==null||s.length()>max)throw new IllegalArgumentException("Text exceeds packet bounds");}
    private static <T extends CustomPayload>CustomPayload.Id<T> id(String s){return new CustomPayload.Id<>(Identifier.of("last_council",s));}
}
