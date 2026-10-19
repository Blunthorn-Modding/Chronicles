package io.github.chronicle.core.network;

import com.google.gson.Gson;
import io.github.chronicle.core.authoring.ui.AuthoringUiModel.Action;
import io.github.chronicle.core.authoring.ui.AuthoringUiModel.Snapshot;
import io.github.chronicle.core.authoring.ui.AuthoringUiModel.Tab;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class AuthoringUiPayloads {
    private static final Gson JSON = new Gson();
    private AuthoringUiPayloads() { }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(View.ID, View.CODEC);
        PayloadTypeRegistry.playS2C().register(Notice.ID, Notice.CODEC);
    }
    public record Request(Action action, String id, String argument, long token, Tab tab, int page) implements CustomPayload {
        public static final Id<Request> ID = new Id<>(Identifier.of("chronicle_core", "author_ui_request_v1"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.ofStatic((buf, value) -> {
            buf.writeEnumConstant(value.action()); buf.writeString(value.id(), 160); buf.writeString(value.argument(), 160);
            buf.writeLong(value.token()); buf.writeEnumConstant(value.tab()); buf.writeVarInt(value.page());
        }, buf -> new Request(buf.readEnumConstant(Action.class), buf.readString(160), buf.readString(160), buf.readLong(), buf.readEnumConstant(Tab.class), buf.readVarInt()));
        public Request {
            if (action == null || tab == null || id == null || argument == null || id.length() > 160 || argument.length() > 160
                    || token < 0 || page < 0 || page > 1024) throw new IllegalArgumentException("Invalid authoring request");
        }
        @Override public Id<Request> getId() { return ID; }
    }
    public record View(Snapshot state, boolean open) implements CustomPayload {
        public static final Id<View> ID = new Id<>(Identifier.of("chronicle_core", "author_ui_view_v1"));
        public static final PacketCodec<RegistryByteBuf, View> CODEC = PacketCodec.ofStatic((buf, value) -> {
            buf.writeString(JSON.toJson(value.state()), 131072); buf.writeBoolean(value.open());
        }, buf -> new View(JSON.fromJson(buf.readString(131072), Snapshot.class), buf.readBoolean()));
        public View { if (state == null) throw new IllegalArgumentException("Missing authoring state"); }
        @Override public Id<View> getId() { return ID; }
    }
    public record Notice(String message) implements CustomPayload {
        public static final Id<Notice> ID = new Id<>(Identifier.of("chronicle_core", "author_ui_notice_v1"));
        public static final PacketCodec<RegistryByteBuf, Notice> CODEC = PacketCodec.ofStatic((buf, value) -> buf.writeString(value.message(), 512), buf -> new Notice(buf.readString(512)));
        public Notice { if (message == null || message.length() > 512) throw new IllegalArgumentException("Invalid notice"); }
        @Override public Id<Notice> getId() { return ID; }
    }
}
