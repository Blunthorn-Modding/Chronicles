package io.github.chronicle.quietbelow.content;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayerConfigurationTask;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Configuration-phase compatibility gate for sculk and campaign block-state layouts.
 * A mismatch must never reach PLAY because numeric block-state palettes would differ.
 */
public final class SculkAppearanceHandshake {
    public static final int LAYOUT_VERSION = 2;
    private static final ServerPlayerConfigurationTask.Key TASK_KEY =
            new ServerPlayerConfigurationTask.Key("quiet_below:sculk_appearance_v1");

    private SculkAppearanceHandshake() { }

    public static void register() {
        PayloadTypeRegistry.configurationS2C().register(Hello.ID, Hello.CODEC);
        PayloadTypeRegistry.configurationC2S().register(Acknowledgement.ID, Acknowledgement.CODEC);
        ServerConfigurationNetworking.registerGlobalReceiver(Acknowledgement.ID, (payload, context) -> {
            if (payload.layoutVersion() != LAYOUT_VERSION) {
                context.responseSender().disconnect(versionMismatch("server", LAYOUT_VERSION,
                        "client", payload.layoutVersion()));
                return;
            }
            try {
                ((FabricServerConfigurationNetworkHandler) context.networkHandler()).completeTask(TASK_KEY);
            } catch (IllegalStateException ignored) {
                context.responseSender().disconnect(Text.literal(
                        "The Quiet Below connection was refused: the sculk appearance handshake was unexpected. "
                                + "Install the matching Quiet Below pack on both client and server."));
            }
        });
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (!ServerConfigurationNetworking.canSend(handler, Hello.ID)) {
                handler.disconnect(Text.literal(
                        "The Quiet Below connection was refused: the client pack is missing or outdated and "
                                + "cannot safely decode sculk appearance states. Install the matching Quiet Below "
                                + "pack on both client and server (layout " + LAYOUT_VERSION + ")."));
                return;
            }
            ((FabricServerConfigurationNetworkHandler) handler).addTask(new AppearanceTask());
        });
    }

    public record Hello(int layoutVersion) implements CustomPayload {
        public static final Id<Hello> ID = new Id<>(Identifier.of("quiet_below", "sculk_appearance_hello_v1"));
        public static final PacketCodec<PacketByteBuf, Hello> CODEC = PacketCodec.ofStatic(
                (buffer, payload) -> buffer.writeVarInt(payload.layoutVersion()),
                buffer -> new Hello(buffer.readVarInt()));

        @Override
        public Id<Hello> getId() {
            return ID;
        }
    }

    public record Acknowledgement(int layoutVersion) implements CustomPayload {
        public static final Id<Acknowledgement> ID =
                new Id<>(Identifier.of("quiet_below", "sculk_appearance_ack_v1"));
        public static final PacketCodec<PacketByteBuf, Acknowledgement> CODEC = PacketCodec.ofStatic(
                (buffer, payload) -> buffer.writeVarInt(payload.layoutVersion()),
                buffer -> new Acknowledgement(buffer.readVarInt()));

        @Override
        public Id<Acknowledgement> getId() {
            return ID;
        }
    }

    private static final class AppearanceTask implements ServerPlayerConfigurationTask {
        @Override
        public void sendPacket(Consumer<Packet<?>> sender) {
            sender.accept(ServerConfigurationNetworking.createS2CPacket(new Hello(LAYOUT_VERSION)));
        }

        @Override
        public ServerPlayerConfigurationTask.Key getKey() {
            return TASK_KEY;
        }
    }

    public static Text versionMismatch(String firstSide, int firstVersion, String secondSide, int secondVersion) {
        return Text.literal("The Quiet Below connection was refused: sculk appearance layouts differ ("
                + firstSide + " " + firstVersion + ", " + secondSide + " " + secondVersion + "). "
                + "Install the matching Quiet Below pack on both client and server.");
    }
}
