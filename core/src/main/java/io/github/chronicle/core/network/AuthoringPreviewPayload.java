package io.github.chronicle.core.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** A single private, short-lived geometry preview. No client-to-server mutation packet exists. */
public record AuthoringPreviewPayload(String dimension, boolean visible, double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ, boolean facing, float yaw, float pitch) implements CustomPayload {
    public static final Id<AuthoringPreviewPayload> ID = new Id<>(Identifier.of("chronicle_core", "authoring_preview"));
    public static final PacketCodec<RegistryByteBuf, AuthoringPreviewPayload> CODEC = PacketCodec.ofStatic((buf, value) -> {
        buf.writeString(value.dimension(), 256);
        buf.writeBoolean(value.visible());
        buf.writeDouble(value.minX()); buf.writeDouble(value.minY()); buf.writeDouble(value.minZ());
        buf.writeDouble(value.maxX()); buf.writeDouble(value.maxY()); buf.writeDouble(value.maxZ());
        buf.writeBoolean(value.facing()); buf.writeFloat(value.yaw()); buf.writeFloat(value.pitch());
    }, buf -> new AuthoringPreviewPayload(buf.readString(256), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean(), buf.readFloat(), buf.readFloat()));

    public AuthoringPreviewPayload {
        if (dimension == null || dimension.length() > 256 || Identifier.tryParse(dimension) == null
                || !Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || minX > maxX || minY > maxY || minZ > maxZ
                || Math.max(Math.abs(minX), Math.abs(maxX)) > 30_000_001
                || Math.max(Math.abs(minY), Math.abs(maxY)) > 2_000_001
                || Math.max(Math.abs(minZ), Math.abs(maxZ)) > 30_000_001) {
            throw new IllegalArgumentException("Invalid authoring preview geometry.");
        }
    }

    public static AuthoringPreviewPayload clear(String dimension) {
        return new AuthoringPreviewPayload(dimension, false, 0, 0, 0, 0, 0, 0, false, 0, 0);
    }
    public static void register() { PayloadTypeRegistry.playS2C().register(ID, CODEC); }
    @Override public Id<AuthoringPreviewPayload> getId() { return ID; }
}
