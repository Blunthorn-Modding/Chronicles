package io.github.chronicle.core.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Bounded server-authored transport for the dedicated two-stage departure review. */
public final class DeparturePayloads {
    public static final int CONTINUE_OR_CONFIRM=0,BACK=1,CANCEL=2;
    public static final UUID NO_PARENT=new UUID(0,0);
    private DeparturePayloads(){}
    public static void register(){PayloadTypeRegistry.playS2C().register(View.ID,View.CODEC);PayloadTypeRegistry.playC2S().register(Action.ID,Action.CODEC);}
    public record View(UUID token,UUID parentToken,int stage,String title,String details,String acceptLabel,String status,boolean available,boolean opening,boolean completed)implements CustomPayload{
        public static final int MAX_DETAILS_BYTES=65536;
        public View{if(token==null||parentToken==null)throw new IllegalArgumentException("Departure tokens are required");if(stage<1||stage>2)throw new IllegalArgumentException("Departure stage must be 1 or 2");if(stage==1&&!parentToken.equals(NO_PARENT)||stage==2&&parentToken.equals(NO_PARENT))throw new IllegalArgumentException("Departure parent token does not match its stage");bounded(title,128,false,"title");bounded(details,MAX_DETAILS_BYTES,false,"details");bounded(acceptLabel,64,false,"accept label");bounded(status,512,true,"status");}
        public static final Id<View>ID=new Id<>(Identifier.of("chronicle_core","departure_view"));
        public static final PacketCodec<RegistryByteBuf,View>CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.token);b.writeUuid(v.parentToken);b.writeByte(v.stage);b.writeString(v.title,128);b.writeString(v.details,MAX_DETAILS_BYTES);b.writeString(v.acceptLabel,64);b.writeString(v.status,512);b.writeBoolean(v.available);b.writeBoolean(v.opening);b.writeBoolean(v.completed);},b->new View(b.readUuid(),b.readUuid(),b.readUnsignedByte(),b.readString(128),b.readString(MAX_DETAILS_BYTES),b.readString(64),b.readString(512),b.readBoolean(),b.readBoolean(),b.readBoolean()));
        @Override public Id<View>getId(){return ID;}
    }
    public record Action(UUID token,int action)implements CustomPayload{
        public Action{if(token==null||action<CONTINUE_OR_CONFIRM||action>CANCEL)throw new IllegalArgumentException("Invalid departure action");}
        public static final Id<Action>ID=new Id<>(Identifier.of("chronicle_core","departure_action"));
        public static final PacketCodec<RegistryByteBuf,Action>CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.token);b.writeByte(v.action);},b->new Action(b.readUuid(),b.readUnsignedByte()));
        @Override public Id<Action>getId(){return ID;}
    }
    private static void bounded(String value,int max,boolean empty,String field){if(value==null||!empty&&value.isBlank()||value.getBytes(StandardCharsets.UTF_8).length>max)throw new IllegalArgumentException("Departure "+field+" is outside its UTF-8 byte limit");for(int i=0;i<value.length();){int point=value.codePointAt(i);if(Character.isISOControl(point)&&point!='\n'&&point!='\t')throw new IllegalArgumentException("Departure "+field+" contains control characters");i+=Character.charCount(point);}}
}
