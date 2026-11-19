package io.github.chronicle.lastcouncil.network;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Public factions and authenticated action availability; no private House papers. */
public final class RebellionPayloads {
    public static final UUID NONE=new UUID(0,0);
    public enum Action { OPEN, STATE, DECLARE, JOIN, ALLOW_VISIT, CLOSE }
    private static final Gson JSON=new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static final int MAX=192*1024;
    private RebellionPayloads(){}
    public static void register(){PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playS2C().register(View.ID,View.CODEC);}
    private static <T extends CustomPayload> CustomPayload.Id<T> id(String path){return new CustomPayload.Id<>(Identifier.of("last_council",path));}
    public record Request(UUID request,Action action,long token,long revision,UUID faction,String statement,String house) implements CustomPayload {
        public Request{Objects.requireNonNull(request);Objects.requireNonNull(action);Objects.requireNonNull(faction);bounded(statement,2048);bounded(house,128);if(revision<0)throw new IllegalArgumentException("Invalid rebellion revision");}
        public static final Id<Request> ID=id("rebellion_request");
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request);b.writeEnumConstant(v.action);b.writeLong(v.token);b.writeLong(v.revision);b.writeUuid(v.faction);b.writeString(v.statement,2048);b.writeString(v.house,128);},b->new Request(b.readUuid(),b.readEnumConstant(Action.class),b.readLong(),b.readLong(),b.readUuid(),b.readString(2048),b.readString(128)));
        public Id<Request> getId(){return ID;}
    }
    public record Row(UUID id,String title){public Row{Objects.requireNonNull(id);bounded(title,320);}}
    public record Detail(UUID id,String title,String founder,String statement,List<String> houses,List<String> history){
        public Detail{Objects.requireNonNull(id);bounded(title,320);bounded(founder,320);bounded(statement,2048);houses=copy(houses,32);history=copy(history,128);houses.forEach(s->bounded(s,320));history.forEach(s->bounded(s,2048));}
    }
    public record HouseVisit(String house,String title,String status){public HouseVisit{bounded(house,128);bounded(title,320);bounded(status,64);}}
    public record View(UUID request,boolean opening,long token,long revision,List<Row> rows,Detail detail,String ownHouse,
            boolean canDeclare,boolean canJoin,boolean chancellor,List<HouseVisit> visits,String message,String refusal,boolean saved) implements CustomPayload {
        public View(UUID request,boolean opening,long token,long revision,List<Row> rows,Detail detail,String ownHouse,boolean canDeclare,boolean canJoin,boolean chancellor,List<HouseVisit> visits,String message,String refusal){this(request,opening,token,revision,rows,detail,ownHouse,canDeclare,canJoin,chancellor,visits,message,refusal,false);}
        public View{Objects.requireNonNull(request);rows=copy(rows,128);visits=copy(visits,32);bounded(ownHouse,128);bounded(message,1024);bounded(refusal,1024);if(revision<0)throw new IllegalArgumentException("Invalid rebellion revision");}
        public static final Id<View> ID=id("rebellion_view");
        public static final PacketCodec<RegistryByteBuf,View> CODEC=PacketCodec.ofStatic((b,v)->b.writeString(encode(v),MAX),b->{String s=b.readString(MAX);if(s.getBytes(StandardCharsets.UTF_8).length>MAX)throw new IllegalArgumentException("Rebellion view too large");BookConfig.strictObject(s);return Objects.requireNonNull(JSON.fromJson(s,View.class));});
        public Id<View> getId(){return ID;}
    }
    private static String encode(View value){String s=JSON.toJson(value);if(s.length()>MAX||s.getBytes(StandardCharsets.UTF_8).length>MAX)throw new IllegalArgumentException("Rebellion view too large");return s;}
    public static boolean fits(View value){try{encode(value);return true;}catch(RuntimeException invalid){return false;}}
    private static void bounded(String s,int max){if(s==null||s.length()>max)throw new IllegalArgumentException("Rebellion text too large");}
    private static <T> List<T> copy(List<T> xs,int max){Objects.requireNonNull(xs);if(xs.size()>max)throw new IllegalArgumentException("Rebellion list too large");return List.copyOf(xs);}
}
