package io.github.chronicle.lastcouncil.network;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Separate public/recipient-private projections. No client chooses whose private papers to read. */
public final class CouncilPayloads {
    public enum Section { INFORMATION, SECRETS, GOALS, KNOWN_SECRETS }
    public enum Operation { OPEN, ASSIGN, UNASSIGN, PREVIEW, COMMIT, RELOAD, APPOINT_CHANCELLOR, CLEAR_CHANCELLOR, RETRY_PAPERS }
    public static final UUID NONE = new UUID(0,0);
    private CouncilPayloads() { }
    public static void register() {
        PayloadTypeRegistry.playS2C().register(Capability.ID,Capability.CODEC);
        PayloadTypeRegistry.playC2S().register(PersonalRequest.ID,PersonalRequest.CODEC);
        PayloadTypeRegistry.playS2C().register(PersonalView.ID,PersonalView.CODEC);
        PayloadTypeRegistry.playC2S().register(AdminRequest.ID,AdminRequest.CODEC);
        PayloadTypeRegistry.playS2C().register(AdminView.ID,AdminView.CODEC);
        PayloadTypeRegistry.playS2C().register(Notice.ID,Notice.CODEC);
    }
    public record PublicHouse(String id,String title,String reputation,String description,String interests,
                              String representative,boolean councilMember,boolean candidate,boolean defaultCandidate) { }
    public record Player(UUID id,String label,String houseId,boolean candidate) { }
    public record Capability(boolean active,boolean chancellor) implements CustomPayload {
        public Capability(boolean active){this(active,false);}
        public static final Id<Capability> ID=id("capability");
        public static final PacketCodec<RegistryByteBuf,Capability> CODEC=PacketCodec.ofStatic((b,v)->{b.writeBoolean(v.active());b.writeBoolean(v.chancellor());},b->new Capability(b.readBoolean(),b.readBoolean()));
        public Id<Capability> getId(){return ID;}
    }
    public record PersonalRequest(UUID request,Section section,int offset) implements CustomPayload {
        public static final Id<PersonalRequest> ID=id("personal_request");
        public static final PacketCodec<RegistryByteBuf,PersonalRequest> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request());b.writeEnumConstant(v.section());b.writeVarInt(v.offset());},b->new PersonalRequest(b.readUuid(),b.readEnumConstant(Section.class),b.readVarInt()));
        public Id<PersonalRequest> getId(){return ID;}
    }
    public record PersonalView(UUID request,String ownHouse,List<PublicHouse> houses,Section section,int offset,
                               boolean previous,boolean next,List<String> paragraphs,String message) implements CustomPayload {
        public PersonalView { houses=List.copyOf(houses);paragraphs=List.copyOf(paragraphs);if(houses.size()>32||paragraphs.size()>8||offset<0||offset>124)throw new IllegalArgumentException("Personal view exceeds bounds"); }
        public static final Id<PersonalView> ID=id("personal_view");
        public static final PacketCodec<RegistryByteBuf,PersonalView> CODEC=PacketCodec.ofStatic((b,v)->{
            b.writeUuid(v.request());b.writeString(v.ownHouse(),128);writeHouses(b,v.houses());b.writeEnumConstant(v.section());b.writeVarInt(v.offset());b.writeBoolean(v.previous());b.writeBoolean(v.next());writeTexts(b,v.paragraphs(),8);b.writeString(v.message(),512);
        },b->new PersonalView(b.readUuid(),b.readString(128),readHouses(b),b.readEnumConstant(Section.class),b.readVarInt(),b.readBoolean(),b.readBoolean(),readTexts(b,8),b.readString(512)));
        public Id<PersonalView> getId(){return ID;}
    }
    public record AdminRequest(UUID request,Operation operation,long revision,UUID target,String houseId,
                               boolean candidate,UUID preview,long officeRevision) implements CustomPayload {
        public AdminRequest(UUID request,Operation operation,long revision,UUID target,String houseId,boolean candidate,UUID preview){this(request,operation,revision,target,houseId,candidate,preview,-1);}
        public static final Id<AdminRequest> ID=id("admin_request");
        public static final PacketCodec<RegistryByteBuf,AdminRequest> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request());b.writeEnumConstant(v.operation());b.writeLong(v.revision());b.writeUuid(v.target());b.writeString(v.houseId(),128);b.writeBoolean(v.candidate());b.writeUuid(v.preview());b.writeLong(v.officeRevision());},b->new AdminRequest(b.readUuid(),b.readEnumConstant(Operation.class),b.readLong(),b.readUuid(),b.readString(128),b.readBoolean(),b.readUuid(),b.readLong()));
        public Id<AdminRequest> getId(){return ID;}
    }
    public record AdminView(UUID request,boolean opening,long revision,UUID target,List<Player> players,
                            List<PublicHouse> houses,boolean editable,String reason,boolean committed,UUID preview,
                            List<String> previewSecrets,List<String> previewGoals,String message,UUID chancellor,long officeRevision,boolean officeEditable,String officeReason,boolean lateAssignable,String assignmentReason,boolean electionStarted) implements CustomPayload {
        public AdminView {players=List.copyOf(players);houses=List.copyOf(houses);previewSecrets=List.copyOf(previewSecrets);previewGoals=List.copyOf(previewGoals);if(players.size()>160||houses.size()>32||previewSecrets.size()>4||previewGoals.size()>4)throw new IllegalArgumentException("Admin view exceeds bounds");}
        public static final Id<AdminView> ID=id("admin_view");
        public static final PacketCodec<RegistryByteBuf,AdminView> CODEC=PacketCodec.ofStatic((b,v)->{
            b.writeUuid(v.request());b.writeBoolean(v.opening());b.writeLong(v.revision());b.writeUuid(v.target());b.writeVarInt(v.players().size());for(var p:v.players()){b.writeUuid(p.id());b.writeString(p.label(),160);b.writeString(p.houseId(),128);b.writeBoolean(p.candidate());}writeHouses(b,v.houses());b.writeBoolean(v.editable());b.writeString(v.reason(),512);b.writeBoolean(v.committed());b.writeUuid(v.preview());writeTexts(b,v.previewSecrets(),4);writeTexts(b,v.previewGoals(),4);b.writeString(v.message(),512);b.writeUuid(v.chancellor());b.writeLong(v.officeRevision());b.writeBoolean(v.officeEditable());b.writeString(v.officeReason(),512);b.writeBoolean(v.lateAssignable());b.writeString(v.assignmentReason(),512);b.writeBoolean(v.electionStarted());
        },b->{UUID request=b.readUuid();boolean opening=b.readBoolean();long revision=b.readLong();UUID target=b.readUuid();int count=count(b,160);List<Player> players=new ArrayList<>();for(int i=0;i<count;i++)players.add(new Player(b.readUuid(),b.readString(160),b.readString(128),b.readBoolean()));return new AdminView(request,opening,revision,target,players,readHouses(b),b.readBoolean(),b.readString(512),b.readBoolean(),b.readUuid(),readTexts(b,4),readTexts(b,4),b.readString(512),b.readUuid(),b.readLong(),b.readBoolean(),b.readString(512),b.readBoolean(),b.readString(512),b.readBoolean());});
        public Id<AdminView> getId(){return ID;}
    }
    public record Notice(UUID request,String message) implements CustomPayload {
        public static final Id<Notice> ID=id("notice");
        public static final PacketCodec<RegistryByteBuf,Notice> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.request());b.writeString(v.message(),512);},b->new Notice(b.readUuid(),b.readString(512)));
        public Id<Notice> getId(){return ID;}
    }
    private static <T extends CustomPayload> CustomPayload.Id<T> id(String path){return new CustomPayload.Id<>(Identifier.of("last_council",path));}
    private static int count(RegistryByteBuf b,int limit){int n=b.readVarInt();if(n<0||n>limit)throw new IllegalArgumentException("List size exceeds bounds");return n;}
    private static void writeTexts(RegistryByteBuf b,List<String> lines,int limit){if(lines.size()>limit)throw new IllegalArgumentException("Too many paragraphs");b.writeVarInt(lines.size());for(String s:lines)b.writeString(s,8192);}
    private static List<String> readTexts(RegistryByteBuf b,int limit){int n=count(b,limit);List<String> lines=new ArrayList<>();for(int i=0;i<n;i++)lines.add(b.readString(8192));return lines;}
    private static void writeHouses(RegistryByteBuf b,List<PublicHouse> houses){if(houses.size()>32)throw new IllegalArgumentException("Too many houses");b.writeVarInt(houses.size());for(var h:houses){b.writeString(h.id(),128);b.writeString(h.title(),160);b.writeString(h.reputation(),320);b.writeString(h.description(),4096);b.writeString(h.interests(),2048);b.writeString(h.representative(),96);b.writeBoolean(h.councilMember());b.writeBoolean(h.candidate());b.writeBoolean(h.defaultCandidate());}}
    private static List<PublicHouse> readHouses(RegistryByteBuf b){int n=count(b,32);List<PublicHouse> houses=new ArrayList<>();for(int i=0;i<n;i++)houses.add(new PublicHouse(b.readString(128),b.readString(160),b.readString(320),b.readString(4096),b.readString(2048),b.readString(96),b.readBoolean(),b.readBoolean(),b.readBoolean()));return houses;}
}
