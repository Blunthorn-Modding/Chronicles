package io.github.chronicle.core.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Private text-only consent snapshots. No client-supplied terms, prices or character identity. */
public final class OfferingPayloads {
    private OfferingPayloads() { }
    public static void register() {
        PayloadTypeRegistry.playS2C().register(View.ID, View.CODEC);
        PayloadTypeRegistry.playC2S().register(Action.ID, Action.CODEC);
    }
    public record Choice(String label, int count, int limit, String description, String result, String problem) {
        public Choice {
            bounded(label, 64); bounded(description, 512); bounded(result, 256); bounded(problem, 256);
            if (count < 0 || count > 128 || limit < 1 || limit > 16) throw new IllegalArgumentException("Invalid offering count");
        }
    }
    public record View(UUID token, String title, String status, List<Choice> choices, int selected,
                       boolean opening, boolean completed) implements CustomPayload {
        public View {
            if (token == null || choices == null || choices.isEmpty() || choices.size() > 16
                    || selected < 0 || selected >= choices.size()) throw new IllegalArgumentException("Invalid offering view");
            bounded(title, 80); bounded(status, 384); choices = List.copyOf(choices);
        }
        public static final Id<View> ID = new Id<>(Identifier.of("chronicle_core", "offering_view"));
        public static final PacketCodec<RegistryByteBuf, View> CODEC = PacketCodec.ofStatic((buffer, view) -> {
            buffer.writeUuid(view.token); buffer.writeString(view.title, 80); buffer.writeString(view.status, 384);
            buffer.writeByte(view.choices.size());
            for (Choice choice : view.choices) {
                buffer.writeString(choice.label, 64); buffer.writeByte(choice.count); buffer.writeByte(choice.limit);
                buffer.writeString(choice.description, 512); buffer.writeString(choice.result, 256); buffer.writeString(choice.problem, 256);
            }
            buffer.writeByte(view.selected); buffer.writeBoolean(view.opening); buffer.writeBoolean(view.completed);
        }, buffer -> {
            UUID token = buffer.readUuid(); String title = buffer.readString(80), status = buffer.readString(384);
            int size = buffer.readUnsignedByte();
            if (size < 1 || size > 16) throw new IllegalArgumentException("Invalid offering choice count");
            List<Choice> choices = new ArrayList<>();
            for (int index = 0; index < size; index++) choices.add(new Choice(buffer.readString(64), buffer.readUnsignedByte(),
                    buffer.readUnsignedByte(), buffer.readString(512), buffer.readString(256), buffer.readString(256)));
            return new View(token, title, status, choices, buffer.readUnsignedByte(), buffer.readBoolean(), buffer.readBoolean());
        });
        @Override public Id<View> getId() { return ID; }
    }
    /** 0 select,1 accept,2 refresh,3 close. Selection is a bounded server-list index. */
    public record Action(UUID token, int action, int selected) implements CustomPayload {
        public Action {
            if (token == null || action < 0 || action > 3 || selected < 0 || selected > 15) throw new IllegalArgumentException("Invalid offering action");
        }
        public static final Id<Action> ID = new Id<>(Identifier.of("chronicle_core", "offering_action"));
        public static final PacketCodec<RegistryByteBuf, Action> CODEC = PacketCodec.ofStatic((buffer, value) -> {
            buffer.writeUuid(value.token); buffer.writeByte(value.action); buffer.writeByte(value.selected);
        }, buffer -> new Action(buffer.readUuid(), buffer.readUnsignedByte(), buffer.readUnsignedByte()));
        @Override public Id<Action> getId() { return ID; }
    }
    private static void bounded(String value, int size) {
        if (value == null || value.length() > size) throw new IllegalArgumentException("Offering text exceeds bound");
    }
}
