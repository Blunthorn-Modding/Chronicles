package io.github.chronicle.core.network;

import com.google.gson.Gson;
import io.github.chronicle.core.menu.CoreMenuModel.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class CoreMenuPayloads {
    private static final Gson JSON=new Gson();
    private CoreMenuPayloads() { }
    public static void register(){PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playS2C().register(Response.ID,Response.CODEC);}
    public record Request(Action action,Page page,String id,String argument,long token,int index,String filter,String query) implements CustomPayload {
        public static final Id<Request> ID=new Id<>(Identifier.of("chronicle_core","core_menu_request_v3"));
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,v)->{b.writeEnumConstant(v.action);b.writeEnumConstant(v.page);b.writeString(v.id,160);b.writeString(v.argument,131072);b.writeLong(v.token);b.writeVarInt(v.index);b.writeString(v.filter,32);b.writeString(v.query,80);},b->new Request(b.readEnumConstant(Action.class),b.readEnumConstant(Page.class),b.readString(160),b.readString(131072),b.readLong(),b.readVarInt(),b.readString(32),b.readString(80)));
        public Request {if(action==null||page==null||id==null||id.length()>160||argument==null||argument.length()>131072||token<0||index<0||index>1024||filter==null||filter.length()>32||query==null||query.length()>80)throw new IllegalArgumentException("Invalid menu request");}
        @Override public Id<Request> getId(){return ID;}
    }
    public record Response(View view,boolean open) implements CustomPayload {
        public static final Id<Response> ID=new Id<>(Identifier.of("chronicle_core","core_menu_view_v3"));
        public static final PacketCodec<RegistryByteBuf,Response> CODEC=PacketCodec.ofStatic((b,v)->{b.writeString(JSON.toJson(v.view),262144);b.writeBoolean(v.open);},b->new Response(JSON.fromJson(b.readString(262144),View.class),b.readBoolean()));
        public Response {if(view==null)throw new IllegalArgumentException("Missing menu view");}
        @Override public Id<Response> getId(){return ID;}
    }
}
