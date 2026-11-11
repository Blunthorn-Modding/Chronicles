package io.github.chronicle.core.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Player survival can be held independently of vanilla's world tick manager. No mutation request. */
public record SessionStatePayload(boolean survivalFrozen) implements CustomPayload {
    public static final Id<SessionStatePayload> ID = new Id<>(Identifier.of("chronicle_core", "session_state_v1"));
    public static final PacketCodec<RegistryByteBuf, SessionStatePayload> CODEC = PacketCodec.ofStatic(
            (buf, value) -> buf.writeBoolean(value.survivalFrozen()), buf -> new SessionStatePayload(buf.readBoolean()));
    public static void register() { PayloadTypeRegistry.playS2C().register(ID, CODEC); }
    @Override public Id<SessionStatePayload> getId() { return ID; }
}
