package io.github.chronicle.lastcouncil.network;

import io.github.chronicle.lastcouncil.complications.ComplicationConfig;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Bounded administrative projection; never includes a live draw or schedule. */
public final class ComplicationPayloads {
    private ComplicationPayloads() { }
    public enum Action { OPEN, SAVE, CANCEL_FUTURE, EARLY_END, RECOVER, ACKNOWLEDGE }
    public static void register(){PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playS2C().register(View.ID,View.CODEC);}
    public record PoolChoice(String id,String label) {
        public PoolChoice { Objects.requireNonNull(id); Objects.requireNonNull(label); if(id.length()>256||label.length()>160)throw new IllegalArgumentException("Pool choice too long"); }
    }
    public record Request(UUID request,Action action,long revision,ComplicationConfig config,List<Integer> remainingMeetings,String reason) implements CustomPayload {
        public Request { Objects.requireNonNull(request);Objects.requireNonNull(action);remainingMeetings=List.copyOf(remainingMeetings);Objects.requireNonNull(reason);if(remainingMeetings.size()>64||reason.length()>512)throw new IllegalArgumentException("Request too large"); }
        public static final Id<Request> ID=id("complication_request");
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request);b.writeEnumConstant(v.action);b.writeLong(v.revision);writeConfig(b,v.config);writeMeetings(b,v.remainingMeetings);b.writeString(v.reason,512);},b->new Request(b.readUuid(),b.readEnumConstant(Action.class),b.readLong(),readConfig(b),readMeetings(b),b.readString(512)));
        public Id<Request> getId(){return ID;}
    }
    public record View(UUID request,boolean opening,long revision,ComplicationConfig config,List<PoolChoice> pools,boolean editable,boolean started,int currentMeeting,boolean recoverable,boolean uncertain,String status,String message) implements CustomPayload {
        public View { Objects.requireNonNull(request);Objects.requireNonNull(config);pools=List.copyOf(pools);Objects.requireNonNull(status);Objects.requireNonNull(message);if(pools.size()>64||status.length()>512||message.length()>512)throw new IllegalArgumentException("View too large"); }
        public static final Id<View> ID=id("complication_view");
        public static final PacketCodec<RegistryByteBuf,View> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request);b.writeBoolean(v.opening);b.writeLong(v.revision);writeConfig(b,v.config);b.writeVarInt(v.pools.size());for(PoolChoice p:v.pools){b.writeString(p.id,256);b.writeString(p.label,160);}b.writeBoolean(v.editable);b.writeBoolean(v.started);b.writeVarInt(v.currentMeeting);b.writeBoolean(v.recoverable);b.writeBoolean(v.uncertain);b.writeString(v.status,512);b.writeString(v.message,512);},b->{UUID request=b.readUuid();boolean opening=b.readBoolean();long revision=b.readLong();ComplicationConfig config=readConfig(b);int n=b.readVarInt();if(n<0||n>64)throw new IllegalArgumentException("Too many pools");List<PoolChoice> pools=new ArrayList<>();for(int i=0;i<n;i++)pools.add(new PoolChoice(b.readString(256),b.readString(160)));return new View(request,opening,revision,config,pools,b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readString(512),b.readString(512));});
        public Id<View> getId(){return ID;}
    }
    private static void writeConfig(RegistryByteBuf b,ComplicationConfig c){b.writeBoolean(c!=null);if(c!=null){b.writeString(c.poolId(),256);b.writeVarInt(c.total());writeMeetings(b,c.meetings());b.writeLong(c.earliestTicks());b.writeLong(c.latestTicks());b.writeLong(c.spacingTicks());}}
    private static ComplicationConfig readConfig(RegistryByteBuf b){return b.readBoolean()?new ComplicationConfig(b.readString(256),b.readVarInt(),readMeetings(b),b.readLong(),b.readLong(),b.readLong()):null;}
    private static void writeMeetings(RegistryByteBuf b,List<Integer> values){b.writeVarInt(values.size());for(int value:values)b.writeVarInt(value);}
    private static List<Integer> readMeetings(RegistryByteBuf b){int n=b.readVarInt();if(n<0||n>64)throw new IllegalArgumentException("Too many meetings");List<Integer> values=new ArrayList<>();for(int i=0;i<n;i++)values.add(b.readVarInt());return values;}
    private static <T extends CustomPayload> CustomPayload.Id<T> id(String path){return new CustomPayload.Id<>(Identifier.of("last_council",path));}
}
