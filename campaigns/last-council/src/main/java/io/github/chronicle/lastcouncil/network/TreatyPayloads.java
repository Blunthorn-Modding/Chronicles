package io.github.chronicle.lastcouncil.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Recipient-specific treaty views, bounded before transmission and after decoding. */
public final class TreatyPayloads {
    private TreatyPayloads() { }
    public static final UUID NONE = new UUID(0,0);
    private static final int MAX_FRAME = 700 * 1024;
    private static final Gson GSON = new GsonBuilder().setStrictness(com.google.gson.Strictness.STRICT).create();
    public enum Action { OPEN, STATE, CREATE, EDIT, SIGN, SANCTIFY, BREAK, CLOSE, READY }
    public static void register() { PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC); PayloadTypeRegistry.playS2C().register(View.ID,View.CODEC); }
    private static <T extends CustomPayload> CustomPayload.Id<T> id(String path){return new CustomPayload.Id<>(Identifier.of("last_council",path));}
    private static String text(String value,int max){Objects.requireNonNull(value);if(value.length()>max)throw new IllegalArgumentException("Treaty text too long");return value;}
    private static <T> List<T> list(List<T> values,int max){Objects.requireNonNull(values);if(values.size()>max)throw new IllegalArgumentException("Treaty list too long");return List.copyOf(values);}
    public record Request(UUID request,Action action,long token,UUID treaty,long revision,BlockPos block,String title,String terms,boolean privateTerms,List<UUID> parties,int historyPage) implements CustomPayload {
        public Request {Objects.requireNonNull(request);Objects.requireNonNull(action);Objects.requireNonNull(treaty);Objects.requireNonNull(block);text(title,160);text(terms,4096);parties=list(parties,16);if(historyPage<0)throw new IllegalArgumentException("Invalid history page");}
        public static final Id<Request> ID=id("treaty_request");
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request);b.writeEnumConstant(v.action);b.writeLong(v.token);b.writeUuid(v.treaty);b.writeLong(v.revision);b.writeBlockPos(v.block);b.writeString(v.title,160);b.writeString(v.terms,4096);b.writeBoolean(v.privateTerms);b.writeVarInt(v.parties.size());for(UUID p:v.parties)b.writeUuid(p);b.writeVarInt(v.historyPage);},b->{UUID request=b.readUuid();Action action=b.readEnumConstant(Action.class);long token=b.readLong();UUID treaty=b.readUuid();long revision=b.readLong();BlockPos block=b.readBlockPos();String title=b.readString(160),terms=b.readString(4096);boolean privacy=b.readBoolean();int n=b.readVarInt();if(n<0||n>16)throw new IllegalArgumentException("Too many parties");List<UUID> parties=new ArrayList<>();for(int i=0;i<n;i++)parties.add(b.readUuid());return new Request(request,action,token,treaty,revision,block,title,terms,privacy,parties,b.readVarInt());});
        public Id<Request> getId(){return ID;}
    }
    public record Row(UUID id,String title,String status,boolean privateTerms) {public Row {Objects.requireNonNull(id);text(title,160);text(status,128);}}
    public record Person(UUID owner,String house,String name) {public Person {Objects.requireNonNull(owner);text(house,160);text(name,160);}}
    public record PartyView(UUID owner,String house,String name,boolean present,boolean signed) {public PartyView {Objects.requireNonNull(owner);text(house,160);text(name,160);}}
    public record Detail(UUID id,long revision,String title,String terms,String status,boolean privateTerms,List<PartyView> parties,boolean chancellorPresent,boolean canEdit,boolean canSign,boolean canSanctify,boolean canBreak,List<String> history) {
        public Detail {Objects.requireNonNull(id);text(title,160);text(terms,4096);text(status,128);parties=list(parties,16);history=list(history,32);history.forEach(s->text(s,4608));}
    }
    public record View(UUID request,boolean opening,long token,long revision,List<Row> rows,Detail detail,List<Person> people,boolean houseActor,boolean chancellor,boolean active,String message,int historyPage,int historyPages,boolean closed) implements CustomPayload {
        public View {Objects.requireNonNull(request);rows=list(rows,64);people=list(people,32);text(message,512);if(historyPage<0||historyPages<0||(historyPages>0&&historyPage>=historyPages))throw new IllegalArgumentException("Invalid history page");}
        public View(UUID request,boolean opening,long token,long revision,List<Row> rows,Detail detail,List<Person> people,boolean houseActor,boolean chancellor,boolean active,String message,int historyPage,int historyPages){this(request,opening,token,revision,rows,detail,people,houseActor,chancellor,active,message,historyPage,historyPages,false);}
        public static final Id<View> ID=id("treaty_view");
        public static final PacketCodec<RegistryByteBuf,View> CODEC=PacketCodec.ofStatic((b,v)->{String json=encode(v);b.writeString(json,MAX_FRAME);},b->decode(b.readString(MAX_FRAME)));
        public Id<View> getId(){return ID;}
    }
    public static boolean fits(View view){try{encode(view);return true;}catch(RuntimeException ex){return false;}}
    private static String encode(View view){String json=GSON.toJson(view);if(json.length()>MAX_FRAME||json.getBytes(StandardCharsets.UTF_8).length>MAX_FRAME)throw new IllegalArgumentException("Treaty view exceeds frame");return json;}
    private static View decode(String json){if(json.getBytes(StandardCharsets.UTF_8).length>MAX_FRAME)throw new IllegalArgumentException("Treaty view exceeds frame");try{JsonReader reader=new JsonReader(new StringReader(json));reader.setStrictness(com.google.gson.Strictness.STRICT);View view=GSON.fromJson(reader,View.class);if(view==null||reader.peek()!=com.google.gson.stream.JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Invalid treaty view");return view;}catch(java.io.IOException ex){throw new IllegalArgumentException("Invalid treaty view",ex);}}
}
