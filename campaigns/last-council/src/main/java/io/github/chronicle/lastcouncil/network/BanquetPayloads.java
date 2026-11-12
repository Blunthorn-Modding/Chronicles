package io.github.chronicle.lastcouncil.network;

import io.github.chronicle.lastcouncil.banquet.BanquetConfig;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Bounded, recipient-specific banquet projections. */
public final class BanquetPayloads {
    private BanquetPayloads() { }
    public static final UUID NONE = new UUID(0, 0);
    public enum Action { OPEN, SAVE, GIFT, LOG, RECOVER }
    public static void register() { PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC); PayloadTypeRegistry.playS2C().register(View.ID, View.CODEC); }
    public record PlayerChoice(UUID id, String name) {
        public PlayerChoice { Objects.requireNonNull(id); Objects.requireNonNull(name); if (name.length() > 160) throw new IllegalArgumentException("Name too long"); }
    }
    public record LogEntry(String text) {
        public LogEntry { Objects.requireNonNull(text); if (text.length() > 512) throw new IllegalArgumentException("Log entry too long"); }
    }
    public record Request(UUID request, Action action, long revision, BanquetConfig config, UUID recipient, int drinks, int poison, int antidotes) implements CustomPayload {
        public Request { Objects.requireNonNull(request); Objects.requireNonNull(action); Objects.requireNonNull(recipient); if (drinks < 0 || drinks > 16 || poison < 0 || poison > 16 || antidotes < 0 || antidotes > 16 || drinks + poison + antidotes > 36) throw new IllegalArgumentException("Invalid supply quantities"); }
        public static final Id<Request> ID = id("banquet_request");
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request);b.writeEnumConstant(v.action);b.writeLong(v.revision);writeConfig(b,v.config);b.writeUuid(v.recipient);b.writeVarInt(v.drinks);b.writeVarInt(v.poison);b.writeVarInt(v.antidotes);}, b->new Request(b.readUuid(),b.readEnumConstant(Action.class),b.readLong(),readConfig(b),b.readUuid(),b.readVarInt(),b.readVarInt(),b.readVarInt()));
        public Id<Request> getId(){return ID;}
    }
    public record View(UUID request, boolean opening, long revision, BanquetConfig config, boolean editable, boolean canGift, boolean canLog, List<PlayerChoice> players, List<LogEntry> log, String message, boolean logView, boolean held, UUID worldId) implements CustomPayload {
        public View { Objects.requireNonNull(request); Objects.requireNonNull(config); players=List.copyOf(players); log=List.copyOf(log); Objects.requireNonNull(message); Objects.requireNonNull(worldId); if(players.size()>256 || log.size()>64 || message.length()>512 || (!logView && !log.isEmpty()) || (!canLog && !log.isEmpty())) throw new IllegalArgumentException("Invalid banquet projection"); }
        public static final Id<View> ID=id("banquet_view");
        public static final PacketCodec<RegistryByteBuf, View> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request);b.writeBoolean(v.opening);b.writeLong(v.revision);writeConfig(b,v.config);b.writeBoolean(v.editable);b.writeBoolean(v.canGift);b.writeBoolean(v.canLog);b.writeVarInt(v.players.size());for(var p:v.players){b.writeUuid(p.id);b.writeString(p.name,160);}b.writeVarInt(v.log.size());for(var e:v.log)b.writeString(e.text,512);b.writeString(v.message,512);b.writeBoolean(v.logView);b.writeBoolean(v.held);b.writeUuid(v.worldId);}, b->{UUID request=b.readUuid();boolean opening=b.readBoolean();long revision=b.readLong();BanquetConfig config=readConfig(b);boolean editable=b.readBoolean(),canGift=b.readBoolean(),canLog=b.readBoolean();int n=b.readVarInt();if(n<0||n>256)throw new IllegalArgumentException("Too many players");List<PlayerChoice> players=new ArrayList<>();for(int i=0;i<n;i++)players.add(new PlayerChoice(b.readUuid(),b.readString(160)));n=b.readVarInt();if(n<0||n>64)throw new IllegalArgumentException("Too many log entries");List<LogEntry> log=new ArrayList<>();for(int i=0;i<n;i++)log.add(new LogEntry(b.readString(512)));return new View(request,opening,revision,config,editable,canGift,canLog,players,log,b.readString(512),b.readBoolean(),b.readBoolean(),b.readUuid());});
        public Id<View> getId(){return ID;}
    }
    private static void writeConfig(RegistryByteBuf b,BanquetConfig c){b.writeBoolean(c!=null);if(c!=null){b.writeBoolean(c.enabled());b.writeVarInt(c.onsetMinutes());b.writeVarInt(c.durationMinutes());}}
    private static BanquetConfig readConfig(RegistryByteBuf b){return b.readBoolean()?new BanquetConfig(b.readBoolean(),b.readVarInt(),b.readVarInt()):null;}
    private static <T extends CustomPayload> CustomPayload.Id<T> id(String path){return new CustomPayload.Id<>(Identifier.of("last_council",path));}
}
