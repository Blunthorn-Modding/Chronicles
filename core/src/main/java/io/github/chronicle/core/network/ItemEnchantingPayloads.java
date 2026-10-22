package io.github.chronicle.core.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.*;

/** Bounded presentation and nonce-bound output gestures; items use native handler synchronization. */
public final class ItemEnchantingPayloads {
    private ItemEnchantingPayloads() { }
    public enum State { READY, EMPTY_TARGET, EMPTY_MATERIAL, SHORT_MATERIAL, INCOMPATIBLE, UNCHANGED, QUIET, UNPOWERED, UNAVAILABLE }
    public record RecipeHint(String ingredient,String enchantment,String label,int level,int count) {
        public RecipeHint { id(ingredient);id(enchantment);text(label,160);if(label.isBlank()||level<1||level>255||count<1||count>64)throw new IllegalArgumentException("Invalid workshop recipe hint"); }
    }
    /** A null recipe catalog means unchanged; opening always supplies a complete bounded list. */
    public record View(int syncId,UUID session,UUID token,long revision,String title,State state,List<RecipeHint> recipes,boolean opening) implements CustomPayload {
        public View {
            if(syncId<0||session==null||token==null||revision<0||state==null||opening&&recipes==null)throw new IllegalArgumentException("Invalid workshop view");
            text(title,80);if(title.isBlank())throw new IllegalArgumentException("Missing workshop title");
            if(recipes!=null){if(recipes.size()>256)throw new IllegalArgumentException("Too many workshop recipes");recipes=List.copyOf(recipes);}
        }
        public View withRecipes(List<RecipeHint> catalog){return new View(syncId,session,token,revision,title,state,catalog,opening);}
        public static final Id<View> ID=new Id<>(Identifier.of("chronicle_core","workshop_view"));
        public static final PacketCodec<RegistryByteBuf,View> CODEC=PacketCodec.ofStatic((b,v)->{
            b.writeVarInt(v.syncId);b.writeUuid(v.session);b.writeUuid(v.token);b.writeVarLong(v.revision);b.writeString(v.title,80);b.writeByte(v.state.ordinal());
            b.writeBoolean(v.recipes!=null);if(v.recipes!=null){b.writeVarInt(v.recipes.size());for(var r:v.recipes){b.writeString(r.ingredient,256);b.writeString(r.enchantment,256);b.writeString(r.label,160);b.writeVarInt(r.level);b.writeVarInt(r.count);}}
            b.writeBoolean(v.opening);
        },b->{
            int sync=b.readVarInt();UUID session=b.readUuid(),token=b.readUuid();long revision=b.readVarLong();String title=b.readString(80);int state=b.readUnsignedByte();
            if(state>=State.values().length)throw new IllegalArgumentException("Invalid workshop state");
            List<RecipeHint> rows=null;if(b.readBoolean()){int n=b.readVarInt();if(n<0||n>256)throw new IllegalArgumentException("Invalid workshop catalog");rows=new ArrayList<>(n);for(int i=0;i<n;i++)rows.add(new RecipeHint(b.readString(256),b.readString(256),b.readString(160),b.readVarInt(),b.readVarInt()));}
            return new View(sync,session,token,revision,title,State.values()[state],rows,b.readBoolean());
        });
        @Override public Id<View> getId(){return ID;}
    }
    /** Destination0 cursor,1 player inventory,2 empty hotbar/offhand,3 intentional drop. */
    public record Take(int syncId,UUID session,UUID token,int destination,int button) implements CustomPayload {
        public Take {
            if(syncId<0||session==null||token==null||destination<0||destination>3
                    ||(destination==0||destination==3?button<0||button>1:destination==1?button!=0:button!=40&&(button<0||button>8)))throw new IllegalArgumentException("Invalid workshop take");
        }
        public static final Id<Take> ID=new Id<>(Identifier.of("chronicle_core","workshop_take"));
        public static final PacketCodec<RegistryByteBuf,Take> CODEC=PacketCodec.ofStatic((b,v)->{b.writeVarInt(v.syncId);b.writeUuid(v.session);b.writeUuid(v.token);b.writeByte(v.destination);b.writeByte(v.button);},b->new Take(b.readVarInt(),b.readUuid(),b.readUuid(),b.readUnsignedByte(),b.readUnsignedByte()));
        @Override public Id<Take> getId(){return ID;}
    }
    public static void register(){PayloadTypeRegistry.playS2C().register(View.ID,View.CODEC);PayloadTypeRegistry.playC2S().register(Take.ID,Take.CODEC);}
    private static void id(String value){if(value==null||value.length()>256||!value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new IllegalArgumentException("Invalid workshop item/enchantment id");}
    private static void text(String value,int max){if(value==null||value.length()>max)throw new IllegalArgumentException("Invalid workshop text");}
}
