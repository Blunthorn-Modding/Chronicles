package io.github.chronicle.lastcouncil.network;

import com.google.gson.Gson;
import io.github.chronicle.core.books.BookConfig;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Public election history and receiver-specific permissions; no secret ballots or papers. */
public final class ElectionPayloads {
    public static final UUID NONE=new UUID(0,0);
    public enum Action { OPEN, STATE, WITHDRAW, END, CLOSE }
    private static final Gson JSON=new Gson();
    private static final int MAX=128*1024;
    private ElectionPayloads() { }
    public static void register(){PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playS2C().register(View.ID,View.CODEC);}
    private static <T extends CustomPayload> CustomPayload.Id<T> id(String name){return new CustomPayload.Id<>(Identifier.of("last_council",name));}
    public record Request(UUID request,Action action,long token,long revision,String reason,int page) implements CustomPayload {
        public Request{Objects.requireNonNull(request);Objects.requireNonNull(action);bounded(reason,1600);if(revision<0||page<0||page>31)throw new IllegalArgumentException("Invalid election revision or page");}
        public static final Id<Request> ID=id("election_request");
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request);b.writeEnumConstant(v.action);b.writeLong(v.token);b.writeLong(v.revision);b.writeString(v.reason,1600);b.writeVarInt(v.page);},b->new Request(b.readUuid(),b.readEnumConstant(Action.class),b.readLong(),b.readLong(),b.readString(1600),b.readVarInt()));
        public Id<Request> getId(){return ID;}
    }
    public record View(UUID request,boolean opening,long token,long revision,String stage,List<String> candidates,List<String> history,
                       int page,int pages,String ownCandidate,boolean chancellor,boolean canWithdraw,String withdrawRefusal,
                       boolean canEnd,String endRefusal,String warning,String message,boolean saved) implements CustomPayload {
        public View{
            Objects.requireNonNull(request);candidates=List.copyOf(candidates);history=List.copyOf(history);
            if(revision<0||candidates.size()>15||history.size()>16||pages<1||pages>32||page<0||page>=pages)throw new IllegalArgumentException("Invalid election view bounds");
            bounded(stage,80);bounded(ownCandidate,512);bounded(withdrawRefusal,1024);bounded(endRefusal,1024);bounded(warning,1024);bounded(message,1024);
            candidates.forEach(s->bounded(s,512));history.forEach(s->bounded(s,2048));
            if(!chancellor&&(!warning.isEmpty()||canEnd))throw new IllegalArgumentException("Chancellor-only election controls");
            if(chancellor&&canWithdraw)throw new IllegalArgumentException("Chancellor cannot withdraw a House");
        }
        public static final Id<View> ID=id("election_view");
        public static final PacketCodec<RegistryByteBuf,View> CODEC=PacketCodec.ofStatic((b,v)->b.writeString(encode(v),MAX),b->{String text=b.readString(MAX);if(text.getBytes(StandardCharsets.UTF_8).length>MAX)throw new IllegalArgumentException("Election view too large");return Objects.requireNonNull(JSON.fromJson(BookConfig.strictObject(text),View.class));});
        public Id<View> getId(){return ID;}
    }
    private static void bounded(String s,int max){if(s==null||s.length()>max)throw new IllegalArgumentException("Election text exceeds bounds");}
    private static String encode(View v){String text=JSON.toJson(v);if(text.length()>MAX||text.getBytes(StandardCharsets.UTF_8).length>MAX)throw new IllegalArgumentException("Election view too large");return text;}
    public static boolean fits(View v){try{encode(v);return true;}catch(RuntimeException failure){return false;}}
}
