package io.github.chronicle.core.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

/** Private server-authored confirmation text. Clients submit only a current offer token and verb. */
public final class ActionConfirmationPayloads {
    private ActionConfirmationPayloads() { }
    public static void register() {
        PayloadTypeRegistry.playS2C().register(View.ID, View.CODEC);
        PayloadTypeRegistry.playC2S().register(Action.ID, Action.CODEC);
    }
    public record View(UUID token, String title, String details, String acceptLabel, String status,
                       boolean available, boolean opening, boolean completed) implements CustomPayload {
        public View {
            if (token == null) throw new IllegalArgumentException("Confirmation token is required");
            bounded(title, 80); bounded(details, 2048); bounded(acceptLabel, 48); bounded(status, 384);
        }
        public static final Id<View> ID = new Id<>(Identifier.of("chronicle_core", "action_confirmation_view"));
        public static final PacketCodec<RegistryByteBuf, View> CODEC = PacketCodec.ofStatic((buffer, value) -> {
            buffer.writeUuid(value.token); buffer.writeString(value.title, 80); buffer.writeString(value.details, 2048);
            buffer.writeString(value.acceptLabel, 48); buffer.writeString(value.status, 384); buffer.writeBoolean(value.available);
            buffer.writeBoolean(value.opening); buffer.writeBoolean(value.completed);
        }, buffer -> new View(buffer.readUuid(), buffer.readString(80), buffer.readString(2048), buffer.readString(48),
                buffer.readString(384), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()));
        @Override public Id<View> getId() { return ID; }
    }
    /** 0 accept, 1 refresh, 2 cancel. The server never accepts a client supplied title or terms. */
    public record Action(UUID token, int action) implements CustomPayload {
        public Action { if (token == null || action < 0 || action > 2) throw new IllegalArgumentException("Invalid confirmation action"); }
        public static final Id<Action> ID = new Id<>(Identifier.of("chronicle_core", "action_confirmation_action"));
        public static final PacketCodec<RegistryByteBuf, Action> CODEC = PacketCodec.ofStatic((buffer, value) -> {
            buffer.writeUuid(value.token); buffer.writeByte(value.action);
        }, buffer -> new Action(buffer.readUuid(), buffer.readUnsignedByte()));
        @Override public Id<Action> getId() { return ID; }
    }
    private static void bounded(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit) throw new IllegalArgumentException("Confirmation text exceeds bound");
    }
}
