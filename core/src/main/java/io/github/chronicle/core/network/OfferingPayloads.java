package io.github.chronicle.core.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Private workstation snapshots; the server owns terms, stats, selection and station access. */
public final class OfferingPayloads {
    private OfferingPayloads() { }
    public static void register() {
        PayloadTypeRegistry.playS2C().register(View.ID, View.CODEC);
        PayloadTypeRegistry.playC2S().register(Action.ID, Action.CODEC);
    }
    public record Stats(double health, int food, double speed) {
        public Stats {
            if (!Double.isFinite(health) || health < 0 || food < 2 || food > 40
                    || !Double.isFinite(speed) || speed < 0) throw new IllegalArgumentException("Invalid offering stats");
        }
    }
    public record Choice(String label, int count, int limit, String description, String cost, String bonus,
                         Stats before, Stats after, String problem) {
        public Choice {
            bounded(label, 64); bounded(description, 512); term(cost); term(bonus); bounded(problem, 256);
            if (before == null || count < 0 || count > 128 || limit < 1 || limit > 16) throw new IllegalArgumentException("Invalid offering choice");
        }
    }
    /** Compact default-font terms, including enough row space for the Cost/Bonus prefix. */
    public static void term(String value) {
        bounded(value,32);
        if(value.isBlank()||value.chars().anyMatch(c->c<32||c>126))throw new IllegalArgumentException("Offering terms require 1..32 printable ASCII characters; put details in the description");
    }
    public record View(int syncId, UUID token, UUID parent, String title, String status, boolean stationReady,
                       String stationProblem, List<Choice> choices, int selected, boolean opening,
                       boolean completed) implements CustomPayload {
        public View {
            if (syncId < 0 || token == null || choices == null || choices.isEmpty() || choices.size() > 16
                    || selected < 0 || selected >= choices.size()) throw new IllegalArgumentException("Invalid offering view");
            bounded(title, 80); bounded(status, 384); bounded(stationProblem, 384); choices = List.copyOf(choices);
        }
        public static final Id<View> ID = new Id<>(Identifier.of("chronicle_core", "offering_workstation_view"));
        public static final PacketCodec<RegistryByteBuf, View> CODEC = PacketCodec.ofStatic((buffer, view) -> {
            buffer.writeVarInt(view.syncId); buffer.writeUuid(view.token); buffer.writeBoolean(view.parent != null);
            if (view.parent != null) buffer.writeUuid(view.parent);
            buffer.writeString(view.title, 80); buffer.writeString(view.status, 384);
            buffer.writeBoolean(view.stationReady); buffer.writeString(view.stationProblem, 384); buffer.writeByte(view.choices.size());
            for (Choice c : view.choices) {
                buffer.writeString(c.label, 64); buffer.writeByte(c.count); buffer.writeByte(c.limit);
                buffer.writeString(c.description, 512); buffer.writeString(c.cost, 32); buffer.writeString(c.bonus, 32);
                writeStats(buffer, c.before); buffer.writeBoolean(c.after != null); if (c.after != null) writeStats(buffer, c.after);
                buffer.writeString(c.problem, 256);
            }
            buffer.writeByte(view.selected); buffer.writeBoolean(view.opening); buffer.writeBoolean(view.completed);
        }, buffer -> {
            int syncId = buffer.readVarInt(); UUID token = buffer.readUuid(), parent = buffer.readBoolean() ? buffer.readUuid() : null;
            String title = buffer.readString(80), status = buffer.readString(384); boolean ready = buffer.readBoolean();
            String stationProblem = buffer.readString(384); int size = buffer.readUnsignedByte();
            if (size < 1 || size > 16) throw new IllegalArgumentException("Invalid offering choice count");
            List<Choice> choices = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                String label = buffer.readString(64); int count = buffer.readUnsignedByte(), limit = buffer.readUnsignedByte();
                String description = buffer.readString(512), cost = buffer.readString(32), bonus = buffer.readString(32);
                Stats before = readStats(buffer), after = buffer.readBoolean() ? readStats(buffer) : null;
                choices.add(new Choice(label, count, limit, description, cost, bonus, before, after, buffer.readString(256)));
            }
            return new View(syncId, token, parent, title, status, ready, stationProblem, choices, buffer.readUnsignedByte(), buffer.readBoolean(), buffer.readBoolean());
        });
        @Override public Id<View> getId() { return ID; }
    }
    /** 0 select,1 accept,2 refresh,3 close; the server also binds the actual current handler. */
    public record Action(UUID token, int action, int selected) implements CustomPayload {
        public Action {
            if (token == null || action < 0 || action > 3 || selected < 0 || selected > 15) throw new IllegalArgumentException("Invalid offering action");
        }
        public static final Id<Action> ID = new Id<>(Identifier.of("chronicle_core", "offering_workstation_action"));
        public static final PacketCodec<RegistryByteBuf, Action> CODEC = PacketCodec.ofStatic((b, v) -> {
            b.writeUuid(v.token); b.writeByte(v.action); b.writeByte(v.selected);
        }, b -> new Action(b.readUuid(), b.readUnsignedByte(), b.readUnsignedByte()));
        @Override public Id<Action> getId() { return ID; }
    }
    private static void writeStats(RegistryByteBuf b, Stats s) { b.writeDouble(s.health); b.writeByte(s.food); b.writeDouble(s.speed); }
    private static Stats readStats(RegistryByteBuf b) { return new Stats(b.readDouble(), b.readUnsignedByte(), b.readDouble()); }
    private static void bounded(String value, int size) { if (value == null || value.length() > size) throw new IllegalArgumentException("Offering text exceeds bound"); }
}
