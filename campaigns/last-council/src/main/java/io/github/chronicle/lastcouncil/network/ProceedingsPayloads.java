package io.github.chronicle.lastcouncil.network;

import com.google.gson.Gson;
import io.github.chronicle.core.ballots.BallotView;
import io.github.chronicle.core.books.BookConfig;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Purpose-built public projections. Never encode the ballot ledger or its raw stored votes. */
public final class ProceedingsPayloads {
    private static final Gson JSON=new Gson();
    public enum Action { STATE, MOTIONS, PREPARE, PROPOSE, SPONSOR, WITHDRAW, ADMIT, DEFER,
        CALL, START, RECESS, RESUME, FINISH, OPEN_BALLOT, CLOSE_BALLOT, VOID_BALLOT, RECOVER, BALLOT, CAST, EXIT, OPEN_REGENCY }
    public enum Mode { PROCEEDINGS, MOTIONS }
    private ProceedingsPayloads() { }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);
        PayloadTypeRegistry.playS2C().register(View.ID,View.CODEC);
        PayloadTypeRegistry.playS2C().register(Vote.ID,Vote.CODEC);
        PayloadTypeRegistry.playS2C().register(Signal.ID,Signal.CODEC);
    }
    public record Request(UUID request,Action action,long revision,UUID reference,String site,String text,
            String title,boolean secret,boolean twoThirds,int historyPage) implements CustomPayload {
        public Request(UUID request,Action action,long revision,UUID reference,String site,String text,String title,boolean secret,boolean twoThirds) {this(request,action,revision,reference,site,text,title,secret,twoThirds,0);}
        public Request { Objects.requireNonNull(request);Objects.requireNonNull(action);Objects.requireNonNull(reference);bounded(site,128);bounded(text,8192);bounded(title,320);if(historyPage<0||historyPage>63)throw new IllegalArgumentException("Invalid history page"); }
        public static final Id<Request> ID=id("proceedings_request");
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.ofStatic((b,v)->{
            b.writeUuid(v.request());b.writeEnumConstant(v.action());b.writeLong(v.revision());b.writeUuid(v.reference());
            b.writeString(v.site(),128);b.writeString(v.text(),8192);b.writeString(v.title(),320);b.writeBoolean(v.secret());b.writeBoolean(v.twoThirds());b.writeVarInt(v.historyPage());
        },b->new Request(b.readUuid(),b.readEnumConstant(Action.class),b.readLong(),b.readUuid(),b.readString(128),b.readString(8192),b.readString(320),b.readBoolean(),b.readBoolean(),b.readVarInt()));
        public Id<Request> getId(){return ID;}
    }
    public record Row(UUID id,String title,String status,String proposer,boolean ready) { }
    public record History(UUID id,String title,String status) { }
    public record AttemptView(UUID id,int meeting,BallotView ballot,List<Voter> voters) {
        public AttemptView { Objects.requireNonNull(id);Objects.requireNonNull(ballot);voters=List.copyOf(voters);if(voters.size()>32)throw new IllegalArgumentException("Too many voters"); }
    }
    public record Progress(UUID ballot,int submitted,int electorate,List<String> outstanding) {
        public Progress { Objects.requireNonNull(ballot);outstanding=List.copyOf(outstanding);if(electorate<0||electorate>32||submitted<0||submitted>electorate||outstanding.size()>32)throw new IllegalArgumentException("Invalid progress"); }
    }
    public record Detail(UUID id,String title,String text,String proposer,String status,String visibility,String threshold,
            List<String> sponsors,List<String> notes,boolean canSponsor,boolean canWithdraw,boolean canAdmit,boolean canOpen,boolean canDefer,List<AttemptView> attempts,int historyPage,int historyPages) {
        public Detail { sponsors=List.copyOf(sponsors);notes=List.copyOf(notes);attempts=List.copyOf(attempts);if(sponsors.size()>31||notes.size()>32||attempts.size()>4||historyPages<0||historyPages>64||historyPage<0||(historyPages==0?historyPage!=0:historyPage>=historyPages))throw new IllegalArgumentException("Motion detail exceeds bounds");bounded(title,320);bounded(text,8192); }
    }
    public record Regency(String status,List<String> candidates,String summary,boolean canOpen) {
        public Regency {candidates=List.copyOf(candidates);if(candidates.size()>32)throw new IllegalArgumentException("Candidate list exceeds bounds");bounded(summary,8192);}
    }
    public record View(UUID request,boolean opening,Mode mode,String site,long revision,int meeting,String phase,List<Row> motions,
            Detail detail,List<History> history,UUID activeBallot,boolean chancellor,boolean playing,boolean recoverable,String problem,String message,Regency regency,boolean canPropose,Progress progress) implements CustomPayload {
        public View { Objects.requireNonNull(regency);bounded(site,128);motions=List.copyOf(motions);history=List.copyOf(history);if(motions.size()>128||history.size()>256)throw new IllegalArgumentException("Council view exceeds bounds");bounded(problem,1024);bounded(message,1024); }
        public static final Id<View> ID=id("proceedings_view");
        public static final PacketCodec<RegistryByteBuf,View> CODEC=json(View.class,327000);
        public Id<View> getId(){return ID;}
    }
    public record Voter(String house,UUID player,String name) {
        public Voter {Objects.requireNonNull(player);bounded(house,128);bounded(name,256);}
    }
    public record Vote(UUID request,boolean opening,BallotView ballot,String description,boolean canCast,String reason,List<Voter> voters) implements CustomPayload {
        public Vote(UUID request,boolean opening,BallotView ballot,String description,boolean canCast,String reason){this(request,opening,ballot,description,canCast,reason,List.of());}
        public Vote { voters=List.copyOf(voters);if(voters.size()>32)throw new IllegalArgumentException("Too many voters");Objects.requireNonNull(ballot);bounded(description,8192);bounded(reason,1024);if(ballot.electorateLabels().size()>32||ballot.options().size()>16||ballot.totals().size()>16||ballot.namedVotes().size()>32)throw new IllegalArgumentException("Ballot projection exceeds bounds"); }
        public static final Id<Vote> ID=id("ballot_view");
        public static final PacketCodec<RegistryByteBuf,Vote> CODEC=json(Vote.class,131072);
        public Id<Vote> getId(){return ID;}
    }
    public record Signal(UUID ballot,String title,boolean open) implements CustomPayload {
        public Signal {Objects.requireNonNull(ballot);bounded(title,320);}
        public static final Id<Signal> ID=id("ballot_signal");
        public static final PacketCodec<RegistryByteBuf,Signal> CODEC=PacketCodec.ofStatic((b,v)->{b.writeUuid(v.ballot());b.writeString(v.title(),320);b.writeBoolean(v.open());},b->new Signal(b.readUuid(),b.readString(320),b.readBoolean()));
        public Id<Signal> getId(){return ID;}
    }
    private static <T> PacketCodec<RegistryByteBuf,T> json(Class<T> type,int limit) {
        return PacketCodec.ofStatic((b,v)->b.writeString(JSON.toJson(v),limit),b->JSON.fromJson(BookConfig.strictObject(b.readString(limit)),type));
    }
    private static void bounded(String value,int limit){if(value==null||value.length()>limit)throw new IllegalArgumentException("Text exceeds packet bounds");}
    private static <T extends CustomPayload> CustomPayload.Id<T> id(String path){return new CustomPayload.Id<>(Identifier.of("last_council",path));}
    public static boolean fitsView(View view) { return JSON.toJson(view).length() <= 327000; }
    public static List<UUID> attemptPage(List<UUID> attempts,int page) {
        if(page<0||page>63||attempts.size()>256)throw new IllegalArgumentException("Invalid history page");
        int end=Math.max(0,attempts.size()-page*4);
        return List.copyOf(attempts.subList(Math.max(0,end-4),end));
    }
}
