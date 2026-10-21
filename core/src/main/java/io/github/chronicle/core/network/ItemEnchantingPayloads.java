package io.github.chronicle.core.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

/** Text-only workshop snapshots: clients never submit items, recipes, levels or destinations. */
public final class ItemEnchantingPayloads {
    private ItemEnchantingPayloads() { }
    public static void register() {
        PayloadTypeRegistry.playS2C().register(View.ID,View.CODEC);
        PayloadTypeRegistry.playC2S().register(Action.ID,Action.CODEC);
    }
    public record View(UUID token,String title,String status,String target,String ingredient,String result,
                       String recipes,boolean available,boolean opening,boolean completed) implements CustomPayload {
        public static final Id<View> ID=new Id<>(Identifier.of("chronicle_core","item_enchanting_view"));
        public static final PacketCodec<RegistryByteBuf,View> CODEC=PacketCodec.ofStatic((b,v)->{
            b.writeUuid(v.token);b.writeString(v.title,80);b.writeString(v.status,384);b.writeString(v.target,160);
            b.writeString(v.ingredient,160);b.writeString(v.result,160);b.writeString(v.recipes,2048);
            b.writeBoolean(v.available);b.writeBoolean(v.opening);b.writeBoolean(v.completed);
        },b->new View(b.readUuid(),b.readString(80),b.readString(384),b.readString(160),b.readString(160),b.readString(160),b.readString(2048),b.readBoolean(),b.readBoolean(),b.readBoolean()));
        @Override public Id<View> getId(){return ID;}
    }
    /** 0 confirm,1 refresh,2 close; every action is scoped to this player's server offer. */
    public record Action(UUID token,int action) implements CustomPayload {
        public Action { if(token==null||action<0||action>2)throw new IllegalArgumentException("Invalid workshop action"); }
        public static final Id<Action> ID=new Id<>(Identifier.of("chronicle_core","item_enchanting_action"));
        public static final PacketCodec<RegistryByteBuf,Action> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.token);b.writeByte(v.action);},b->new Action(b.readUuid(),b.readUnsignedByte()));
        @Override public Id<Action> getId(){return ID;}
    }
}
