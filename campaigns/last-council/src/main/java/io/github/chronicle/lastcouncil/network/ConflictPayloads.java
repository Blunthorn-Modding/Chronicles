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

/** Public political state only. No private agreement identifiers or terms enter this protocol. */
public final class ConflictPayloads {
    public static final UUID NONE=new UUID(0,0);
    public enum Action { OPEN, STATE, DECLARE, JOIN, CHANGE, WITHDRAW, SETTINGS, SAVE_SETTINGS, CLOSE }
    private static final Gson JSON=new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static final int MAX=256*1024;
    private ConflictPayloads(){}
    public static void register(){PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playS2C().register(View.ID,View.CODEC);}
    private static <T extends CustomPayload> CustomPayload.Id<T> id(String p){return new CustomPayload.Id<>(Identifier.of("last_council",p));}
    public record Request(UUID request,Action action,long token,UUID conflict,long revision,String side,String reason,int threshold) implements CustomPayload {
        public Request{Objects.requireNonNull(request);Objects.requireNonNull(action);Objects.requireNonNull(conflict);bounded(side,128);bounded(reason,1024);}
        public static final Id<Request> ID=id("conflict_request");
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request);b.writeEnumConstant(v.action);b.writeLong(v.token);b.writeUuid(v.conflict);b.writeLong(v.revision);b.writeString(v.side,128);b.writeString(v.reason,1024);b.writeVarInt(v.threshold);},b->new Request(b.readUuid(),b.readEnumConstant(Action.class),b.readLong(),b.readUuid(),b.readLong(),b.readString(128),b.readString(1024),b.readVarInt()));
        public Id<Request> getId(){return ID;}
    }
    public record House(String id,String title,String name){public House{bounded(id,128);bounded(title,160);bounded(name,160);}}
    public record Row(UUID id,String title,String status){public Row{Objects.requireNonNull(id);bounded(title,340);bounded(status,64);}}
    public record Detail(UUID id,long revision,String title,String status,String reason,List<House> first,List<House> second,List<String> history,String ownSide,boolean founder,boolean canAct){
        public Detail{Objects.requireNonNull(id);bounded(title,340);bounded(status,64);bounded(reason,1024);first=list(first,16);second=list(second,16);history=list(history,128);history.forEach(x->bounded(x,1400));bounded(ownSide,128);}
    }
    public record Challenge(long token,String targetName,String targetHouse,String privateWarning){public Challenge{bounded(targetName,160);bounded(targetHouse,160);bounded(privateWarning,512);}}
    public record View(UUID request,boolean opening,long token,long revision,List<Row> rows,Detail detail,Challenge challenge,boolean settings,boolean editable,int threshold,String message,boolean closed) implements CustomPayload {
        public View{Objects.requireNonNull(request);rows=list(rows,64);bounded(message,512);}
        public static final Id<View> ID=id("conflict_view");
        public static final PacketCodec<RegistryByteBuf,View> CODEC=PacketCodec.ofStatic((b,v)->{String s=encode(v);b.writeString(s,MAX);},b->{String s=b.readString(MAX);if(s.getBytes(StandardCharsets.UTF_8).length>MAX)throw new IllegalArgumentException("Conflict view too large");BookConfig.strictObject(s);return Objects.requireNonNull(JSON.fromJson(s,View.class));});
        public Id<View> getId(){return ID;}
    }
    private static String encode(View v){String text=JSON.toJson(v);if(text.length()>MAX||text.getBytes(StandardCharsets.UTF_8).length>MAX)throw new IllegalArgumentException("Conflict view too large");return text;}
    public static boolean fits(View v){try{encode(v);return true;}catch(RuntimeException e){return false;}}
    private static void bounded(String s,int max){Objects.requireNonNull(s);if(s.length()>max)throw new IllegalArgumentException("Conflict text too long");}
    private static <T> List<T> list(List<T> xs,int max){Objects.requireNonNull(xs);if(xs.size()>max)throw new IllegalArgumentException("Conflict list too long");return List.copyOf(xs);}
}
