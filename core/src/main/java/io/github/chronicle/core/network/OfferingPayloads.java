package io.github.chronicle.core.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Private offering presentation only: immutable terms, stats, counts and gate diagnostics never leave the server. */
public final class OfferingPayloads {
    private OfferingPayloads() { }
    public static void register() {
        PayloadTypeRegistry.playS2C().register(View.ID,View.CODEC);
        PayloadTypeRegistry.playC2S().register(Action.ID,Action.CODEC);
    }
    public enum State { READY,UNAVAILABLE,EXHAUSTED,EXPIRED,ANSWERED }
    public record Choice(String label,boolean available,boolean complete) {
        public Choice {
            bounded(label,64);
            if(label.isBlank() || available&&complete)throw new IllegalArgumentException("Invalid offering choice");
        }
    }
    /** Server-only compact term validation retained for shared registration compatibility. */
    public static void term(String value) {
        bounded(value,32);
        if(value.isBlank()||value.chars().anyMatch(c->c<32||c>126))throw new IllegalArgumentException("Invalid offering terms");
    }
    /** Campaign-owned wording; no costs, hidden statistics or authority are carried here. */
    public record Presentation(String pending,String unavailable,String ready,String partial,String exhausted,String expired,String answered){
        public Presentation {for(String text:new String[]{pending,unavailable,ready,partial,exhausted,expired,answered}){bounded(text,80);if(text.isBlank())throw new IllegalArgumentException("Offering presentation text is required");}}
        public static Presentation neutral(){return new Presentation("Waiting...","Unavailable.","Choose an offering.","Some offerings are complete.","No offerings remain.","Reopen this workstation.","Offering accepted.");}
    }
    public record View(int syncId,UUID token,UUID parent,String title,State state,List<Choice> choices,boolean opening,Presentation presentation) implements CustomPayload {
        public View(int syncId,UUID token,UUID parent,String title,State state,List<Choice> choices,boolean opening){this(syncId,token,parent,title,state,choices,opening,Presentation.neutral());}
        public View {
            if(syncId<0||token==null||state==null||choices==null||choices.isEmpty()||choices.size()>16||presentation==null)throw new IllegalArgumentException("Invalid offering view");
            bounded(title,80);choices=List.copyOf(choices);
        }
        public static final Id<View> ID=new Id<>(Identifier.of("chronicle_core","offering_view_v2"));
        public static final PacketCodec<RegistryByteBuf,View> CODEC=PacketCodec.ofStatic((buffer,view)->{
            buffer.writeVarInt(view.syncId);buffer.writeUuid(view.token);buffer.writeBoolean(view.parent!=null);
            if(view.parent!=null)buffer.writeUuid(view.parent);
            buffer.writeString(view.title,80);buffer.writeByte(view.state.ordinal());buffer.writeByte(view.choices.size());
            for(Choice choice:view.choices){buffer.writeString(choice.label,64);buffer.writeBoolean(choice.available);buffer.writeBoolean(choice.complete);}
            buffer.writeBoolean(view.opening);
            var copy=view.presentation;for(String text:List.of(copy.pending(),copy.unavailable(),copy.ready(),copy.partial(),copy.exhausted(),copy.expired(),copy.answered()))buffer.writeString(text,80);
        },buffer->{
            int syncId=buffer.readVarInt();UUID token=buffer.readUuid(),parent=buffer.readBoolean()?buffer.readUuid():null;
            String title=buffer.readString(80);int state=buffer.readUnsignedByte(),size=buffer.readUnsignedByte();
            if(state>=State.values().length||size<1||size>16)throw new IllegalArgumentException("Invalid offering bounds");
            List<Choice> choices=new ArrayList<>(size);
            for(int i=0;i<size;i++)choices.add(new Choice(buffer.readString(64),buffer.readBoolean(),buffer.readBoolean()));
            boolean opening=buffer.readBoolean();var copy=new Presentation(buffer.readString(80),buffer.readString(80),buffer.readString(80),buffer.readString(80),buffer.readString(80),buffer.readString(80),buffer.readString(80));
            return new View(syncId,token,parent,title,State.values()[state],choices,opening,copy);
        });
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Action(UUID token,int action,int selected) implements CustomPayload {
        public Action {
            if(token==null||action<0||action>3||selected<0||selected>15)throw new IllegalArgumentException("Invalid offering action");
        }
        public static final Id<Action> ID=new Id<>(Identifier.of("chronicle_core","offering_action_v2"));
        public static final PacketCodec<RegistryByteBuf,Action> CODEC=PacketCodec.ofStatic((buffer,value)->{
            buffer.writeUuid(value.token);buffer.writeByte(value.action);buffer.writeByte(value.selected);
        },buffer->new Action(buffer.readUuid(),buffer.readUnsignedByte(),buffer.readUnsignedByte()));
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    private static void bounded(String value,int max){if(value==null||value.length()>max)throw new IllegalArgumentException("Invalid offering text");}
}
