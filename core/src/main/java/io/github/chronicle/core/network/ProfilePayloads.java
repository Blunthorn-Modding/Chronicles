package io.github.chronicle.core.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** Bounded wire formats. Save deliberately carries no target identity. */
public final class ProfilePayloads {
    private ProfilePayloads() { }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playC2S().register(Save.ID, Save.CODEC);
        PayloadTypeRegistry.playS2C().register(View.ID, View.CODEC);
        PayloadTypeRegistry.playS2C().register(Notice.ID, Notice.CODEC);
    }

    public record Request(UUID owner) implements CustomPayload {
        public static final Id<Request> ID = new Id<>(Identifier.of("chronicle_core", "profile_request"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.ofStatic(
                (buf, value) -> buf.writeUuid(value.owner()), buf -> new Request(buf.readUuid()));
        @Override public Id<Request> getId() { return ID; }
    }

    public record Save(long expectedRevision, String name, String pronouns, String description) implements CustomPayload {
        public static final Id<Save> ID = new Id<>(Identifier.of("chronicle_core", "profile_save"));
        public static final PacketCodec<RegistryByteBuf, Save> CODEC = PacketCodec.ofStatic((buf, value) -> {
            buf.writeLong(value.expectedRevision());
            buf.writeString(value.name(), 96);
            buf.writeString(value.pronouns(), 64);
            buf.writeString(value.description(), 1024);
        }, buf -> new Save(buf.readLong(), buf.readString(96), buf.readString(64), buf.readString(1024)));
        @Override public Id<Save> getId() { return ID; }
    }

    public record View(UUID owner, String name, String pronouns, String description,
                       long revision, boolean editable, boolean saved) implements CustomPayload {
        public static final Id<View> ID = new Id<>(Identifier.of("chronicle_core", "profile_view"));
        public static final PacketCodec<RegistryByteBuf, View> CODEC = PacketCodec.ofStatic((buf, value) -> {
            buf.writeUuid(value.owner());
            buf.writeString(value.name(), 96);
            buf.writeString(value.pronouns(), 64);
            buf.writeString(value.description(), 1024);
            buf.writeLong(value.revision());
            buf.writeBoolean(value.editable());
            buf.writeBoolean(value.saved());
        }, buf -> new View(buf.readUuid(), buf.readString(96), buf.readString(64), buf.readString(1024),
                buf.readLong(), buf.readBoolean(), buf.readBoolean()));
        @Override public Id<View> getId() { return ID; }
    }

    public record Notice(String translationKey) implements CustomPayload {
        public static final Id<Notice> ID = new Id<>(Identifier.of("chronicle_core", "profile_notice"));
        public static final PacketCodec<RegistryByteBuf, Notice> CODEC = PacketCodec.ofStatic(
                (buf, value) -> buf.writeString(value.translationKey(), 128), buf -> new Notice(buf.readString(128)));
        @Override public Id<Notice> getId() { return ID; }
    }
}
