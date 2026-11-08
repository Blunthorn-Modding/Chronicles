package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.ballots.*;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.play.EventSource;
import io.github.chronicle.core.server.*;
import io.github.chronicle.lastcouncil.council.*;
import io.github.chronicle.lastcouncil.house.HouseAssignment;
import io.github.chronicle.lastcouncil.network.CouncilPayloads;
import io.github.chronicle.lastcouncil.network.ProceedingsPayloads;
import io.github.chronicle.lastcouncil.network.ProceedingsPayloads.*;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import static net.minecraft.server.command.CommandManager.literal;
import static io.github.chronicle.lastcouncil.council.ProceedingsState.*;

/** Last Council authority/presence adapter. Raw secret ballots never cross this class's packet boundary. */
public final class ProceedingsRuntime {
    public static final String HOLD="last_council:proceedings", REGION="last_council:council_region",
            LECTERN="last_council:motion_lectern", CALL="last_council:council_called";
    private static final EventSource CALL_SOURCE=new EventSource(CALL,"council","");
    private static final Map<MinecraftServer,Runtime> WORLDS=new IdentityHashMap<>();
    private ProceedingsRuntime() { }
    public static void register() {
        if(CouncilRuntime.active()) {
            subtype(REGION,"Council voting","Representatives inside this Region when a ballot opens form its fixed electorate.",SiteSubtypes.Kind.REGION);
            subtype(LECTERN,"Council motions","A lectern for reading, proposing and sponsoring Council motions.",SiteSubtypes.Kind.BLOCK);
            EventSources.registerProvider(CALL,"Council called","The Chancellor called the houses to meeting. Configure a recurring message/sound Event for each call.",
                    server->List.of(new EventSources.Choice("council","Council gathering","Each accepted call has its own durable occurrence.")),List.of(),false);
            EventSources.registerAuthority("council-call:",(server,receipt)-> {
                Runtime r=WORLDS.get(server);if(r==null)return false;
                try {
                    UUID id=UUID.fromString(receipt.authority().substring("council-call:".length()));Call c=r.service.state().calls().get(id);
                    return c!=null&&c.actor().equals(receipt.actor())&&receipt.type().equals(CALL)&&receipt.target().equals("council")&&receipt.activity().isEmpty();
                }catch(RuntimeException invalid){return false;}
            });
        }
        ServerLifecycleEvents.SERVER_STARTED.register(server->{if(CouncilRuntime.active())start(server);});
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
        ServerTickEvents.END_SERVER_TICK.register(ProceedingsRuntime::tick);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)-> {
            Runtime r=WORLDS.get(server);if(r!=null&&(captured(r,handler.player)||chancellor(handler.player)))signal(handler.player,r.service.state().activeBallot(),r);
        });
        ServerDisconnects.register((handler,server)->{Runtime r=WORLDS.get(server);if(r!=null){UUID id=handler.player.getUuid();r.readers.remove(id);r.gm.remove(id);r.sentProgress.remove(id);r.voters.remove(id);r.sentVotes.remove(id);r.limits.remove(id);r.selected.remove(id);r.historyPages.remove(id);}});
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(request,context)->context.server().execute(()->request(context.player(),request)));
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(hand!=Hand.MAIN_HAND||!(world instanceof ServerWorld w)||!(player instanceof ServerPlayerEntity actor))return ActionResult.PASS;
            Runtime r=WORLDS.get(w.getServer());if(r==null)return ActionResult.PASS;
            var anchors=r.service.state().lecterns().stream().map(id->site(w.getServer(),id)).filter(AuthoringSite.Anchor.class::isInstance).map(AuthoringSite.Anchor.class::cast)
                    .filter(a->a.dimension().equals(w.getRegistryKey().getValue().toString())&&a.position().equals(position(hit.getBlockPos()))).toList();
            if(anchors.isEmpty())return ActionResult.PASS;
            try {
                if(anchors.size()!=1)throw new IllegalStateException("This lectern has conflicting bindings. Ask the Chancellor to repair them.");
                AnchorSession session=new AnchorSession(anchors.getFirst());r.readers.put(actor.getUuid(),session);requireLectern(actor,r,anchors.getFirst().id());
                sendView(actor,r,CouncilPayloads.NONE,true,Mode.MOTIONS,null,"");
            }catch(Exception failure){notice(actor,CouncilPayloads.NONE,failure);}
            return ActionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("chronicle").then(literal("council")
            .then(literal("proceedings").requires(s->s.getPlayer()!=null&&CouncilRuntime.isChancellor(s.getServer(),s.getPlayer().getUuid())).executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();openProceedings(p);return 1;}))
            .then(literal("vote").executes(c->{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();openVote(p);return 1;})))));
    }
    private static void subtype(String id,String label,String description,SiteSubtypes.Kind kind) {
        SiteSubtypes.register(new SiteSubtypes.Definition(id,label,description,Set.of(kind),new SiteSubtypes.Handler(){
            public boolean assigned(MinecraftServer server,AuthoringSite site){Runtime r=WORLDS.get(server);return r!=null&&(id.equals(REGION)?r.service.state().votingRegion().equals(site.id()):r.service.state().lecterns().contains(site.id()));}
            public String refusal(MinecraftServer server,AuthoringSite site,boolean enabling){
                if(ballotOpen(server))return "Close or void the current ballot before changing Council bindings.";
                if(id.equals(LECTERN)&&(!(site instanceof AuthoringSite.Anchor a)||!a.expectedBlock().equals("minecraft:lectern")))return "Choose a Block site bound to a lectern.";
                Runtime r=WORLDS.get(server);if(r==null)return "Council data is unavailable.";
                if(id.equals(REGION)&&enabling&&!r.service.state().votingRegion().isEmpty()&&!r.service.state().votingRegion().equals(site.id()))return "Remove Council voting from its current Region first.";
                return "";
            }
            public SiteSubtypes.Change change(net.minecraft.server.command.ServerCommandSource source,AuthoringSite site,boolean enabled)throws Exception{
                Runtime r=runtime(source.getPlayerOrThrow());
                if(id.equals(REGION))r.service.region(enabled?site.id():"");else r.service.lectern(site.id(),enabled);
                return SiteSubtypes.Change.APPLIED;
            }
        }));
    }
    private static void start(MinecraftServer server) {
        try {
            var root=server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/last-council");
            var ballots=new BallotService(new BallotStore(root.resolve("ballots.json")),Clock.systemUTC());
            var service=new ProceedingsService(new ProceedingsStore(root.resolve("proceedings.json")),ballots,Clock.systemUTC());
            Runtime r=new Runtime(service);WORLDS.put(server,r);
            recoverAndNotify(server,r);reconcileCalls(server,r);RuntimeHolds.release(server,HOLD);
        }catch(EventSources.Unavailable sourceOwned){RuntimeHolds.release(server,HOLD);}
        catch(Exception failure){hold(server,failure);}
    }
    public static boolean successionStarted(MinecraftServer server){Runtime r=WORLDS.get(server);return r!=null&&r.service.state().succession().started();}
    public static boolean candidate(MinecraftServer server,String house,boolean configured) {
        Runtime r=WORLDS.get(server);return r!=null&&r.service.state().succession().started()?r.service.state().succession().stage()==SuccessionState.Stage.CONTESTED&&r.service.state().succession().remaining().contains(house):configured;
    }
    public static boolean ballotOpen(MinecraftServer server){Runtime r=WORLDS.get(server);return r!=null&&r.service.state().activeBallot()!=null;}
    private static Runtime runtime(ServerPlayerEntity player) {
        MinecraftServer server=player.getEntityWorld().getServer();
        if(!server.isOnThread()||server.getPlayerManager().getPlayer(player.getUuid())!=player)throw new IllegalStateException("This connection is no longer current.");
        Runtime r=WORLDS.get(server);if(r==null||!CouncilRuntime.active())throw new IllegalStateException("Council proceedings are unavailable. Inspect the server log.");return r;
    }
    public static void openProceedings(ServerPlayerEntity player) {
        try{if(!chancellor(player))throw new IllegalStateException("Only the Chancellor controls proceedings.");Runtime r=runtime(player);r.gm.add(player.getUuid());sendView(player,r,CouncilPayloads.NONE,true,Mode.PROCEEDINGS,null,"");}
        catch(Exception failure){notice(player,CouncilPayloads.NONE,failure);}
    }
    public static void openVote(ServerPlayerEntity player) {
        try{Runtime r=runtime(player);UUID id=r.service.state().activeBallot();if(id==null)throw new IllegalStateException("No ballot is open.");sendVote(player,r,CouncilPayloads.NONE,id,true);}
        catch(Exception failure){notice(player,CouncilPayloads.NONE,failure);}
    }
    private static void request(ServerPlayerEntity player,Request q) {
        try {
            Runtime r=runtime(player);if(!rate(r,player))return;
            if(q.action()==Action.EXIT){if(q.site().equals("vote")){r.voters.remove(player.getUuid());r.sentVotes.remove(player.getUuid());}else if(q.site().equals("motions"))r.readers.remove(player.getUuid());else {r.gm.remove(player.getUuid());r.sentProgress.remove(player.getUuid());}return;}
            if(q.action()==Action.BALLOT){UUID id=q.reference().equals(CouncilPayloads.NONE)?r.service.state().activeBallot():q.reference();if(id==null)throw new IllegalStateException("No ballot is open.");sendVote(player,r,q.request(),id,!q.site().equals("vote"));return;}
            if(q.action()==Action.CAST) {
                String refusal=voteRefusal(player,r,q.reference());if(!refusal.isBlank())throw new IllegalStateException(refusal);
                r.service.cast(q.reference(),player.getUuid(),q.text());sendVote(player,r,q.request(),q.reference(),false);return;
            }
            boolean host=q.action()!=Action.MOTIONS&&q.action()!=Action.PROPOSE&&q.action()!=Action.SPONSOR&&q.action()!=Action.WITHDRAW;
            if(host){if(!chancellor(player))throw new IllegalStateException("Only the Chancellor controls proceedings.");}else requireLectern(player,r,q.site());
            if(q.action()==Action.STATE||q.action()==Action.MOTIONS){UUID selection=none(q.reference());r.historyPages.put(player.getUuid(),Objects.equals(selection,r.selected.get(player.getUuid()))?q.historyPage():0);if(q.action()==Action.STATE)r.gm.add(player.getUuid());sendView(player,r,q.request(),q.site().equals("open")&&q.action()==Action.STATE,q.action()==Action.STATE?Mode.PROCEEDINGS:Mode.MOTIONS,selection,"");return;}
            if(q.action()==Action.RECOVER) {
                // Metadata-only completion of the exact saved intent; no fresh electorate or world effects.
                recoverAndNotify(player.getEntityWorld().getServer(),r);reconcileCalls(player.getEntityWorld().getServer(),r);r.failed=false;RuntimeHolds.release(player.getEntityWorld().getServer(),HOLD);
                sendView(player,r,q.request(),false,Mode.PROCEEDINGS,none(q.reference()),"Council operation recovered.");refresh(player.getEntityWorld().getServer(),r);return;
            }
            if(q.action()!=Action.PROPOSE&&q.action()!=Action.PREPARE&&q.action()!=Action.SPONSOR&&q.action()!=Action.WITHDRAW)r.service.revision(q.revision());
            if(q.action()!=Action.VOID_BALLOT&&q.action()!=Action.PREPARE)requirePlaying(player.getEntityWorld().getServer());
            if(q.action()==Action.PREPARE&&RuntimeHolds.reason(player.getEntityWorld().getServer())!=null)throw new IllegalStateException("Resolve the Chronicle operation hold first.");
            UUID selected=none(q.reference());String message="";
            switch(q.action()) {
                case PROPOSE,PREPARE -> {
                    boolean prepared=q.action()==Action.PREPARE;HouseAssignment member=prepared?null:member(player,true);
                    UUID id=q.reference().equals(CouncilPayloads.NONE)?UUID.randomUUID():q.reference();
                    var motion=new Motion(id,q.title(),q.text(),player.getUuid(),member==null?"":member.houseId(),q.secret()?BallotVisibility.SECRET:BallotVisibility.PUBLIC,
                            q.twoThirds()?Threshold.TWO_THIRDS:Threshold.MAJORITY,prepared,Set.of(),MotionStatus.SUBMITTED,List.of(),List.of());
                    r.service.propose(motion);selected=id;message=prepared?"Motion added to the agenda.":"Proposal submitted.";
                }
                case SPONSOR -> {r.service.sponsor(q.reference(),member(player,true).houseId());message="Sponsorship recorded.";}
                case WITHDRAW -> {r.service.withdraw(q.reference(),member(player,true).houseId());message="Proposal withdrawn.";}
                case ADMIT -> {r.service.admit(q.reference());message="Motion added to the agenda.";}
                case DEFER -> {r.service.defer(q.reference(),q.text());message="Deferral recorded.";}
                case CALL -> {
                    if(!EventSources.activeOperation(player.getEntityWorld().getServer()))throw new IllegalStateException("A gathering call needs participating players during active play.");
                    call(player,r,q.request());message="Council call recorded. Events linked to Council called will play.";
                }
                case START -> {r.service.openMeeting();message="Proceedings opened.";}
                case RECESS -> {r.service.recess();message="Proceedings in recess.";}
                case RESUME -> {r.service.resumeMeeting();message="Proceedings resumed.";}
                case FINISH -> {r.service.closeMeeting();message="Meeting closed. Roleplay continues.";}
                case OPEN_BALLOT,OPEN_REGENCY -> {
                    AuthoringSite.Region region=votingRegion(player.getEntityWorld().getServer(),r);
                    Map<String,BallotElector> electorate=electorate(player.getEntityWorld().getServer(),region);
                    if(electorate.isEmpty())throw new IllegalStateException(emptyElectorateReason(player.getEntityWorld().getServer(),region));
                    Map<String,String> presentCandidates=presentCandidates(player.getEntityWorld().getServer(),electorate,r.service.state().succession());
                    if(q.action()==Action.OPEN_REGENCY&&r.service.state().succession().started()) {
                        var missing=new TreeSet<>(r.service.state().succession().remaining());missing.removeAll(presentCandidates.keySet());
                        if(!missing.isEmpty())throw new IllegalStateException("All remaining candidates must be present: "+String.join(", ",missing.stream().map(id->candidateLabel(player.getEntityWorld().getServer(),id)).toList()));
                    }
                    Ballot b=q.action()==Action.OPEN_REGENCY?r.service.openRegency(player.getUuid(),region,electorate,presentCandidates)
                            :r.service.open(q.reference(),player.getUuid(),region,electorate);
                    signalOpen(player.getEntityWorld().getServer(),r,b.definition().id());
                    r.historyPages.put(player.getUuid(),0);
                    message="Ballot opened. Its electorate is fixed.";
                }
                case CLOSE_BALLOT -> {r.service.close(q.reference(),player.getUuid());announce(player.getEntityWorld().getServer(),r,q.reference());message="Ballot recorded.";selected=null;}
                case VOID_BALLOT -> {r.service.voidBallot(q.reference(),player.getUuid(),q.text());announce(player.getEntityWorld().getServer(),r,q.reference());message="Ballot voided. A replacement needs a new opening.";selected=null;}
                default -> throw new IllegalArgumentException("Choose a Council action.");
            }
            Mode mode=host&&q.site().isEmpty()?Mode.PROCEEDINGS:Mode.MOTIONS;
            sendView(player,r,q.request(),false,mode,selected,message);refresh(player.getEntityWorld().getServer(),r);
        }catch(IOException failure){hold(player.getEntityWorld().getServer(),failure);notice(player,q.request(),failure);}
        catch(EventSources.Unavailable failure){Runtime r=WORLDS.get(player.getEntityWorld().getServer());if(r!=null&&r.service.state().pending()==null)try{r.service.verifyUnchanged();r.failed=false;RuntimeHolds.release(player.getEntityWorld().getServer(),HOLD);}catch(IOException invalid){hold(player.getEntityWorld().getServer(),invalid);}notice(player,q.request(),failure);}
        catch(RuntimeException failure){Runtime r=WORLDS.get(player.getEntityWorld().getServer());if(r!=null&&r.service.state().pending()!=null)hold(player.getEntityWorld().getServer(),failure);notice(player,q.request(),failure);}
    }
    private static void call(ServerPlayerEntity player,Runtime r,UUID id)throws IOException {
        MinecraftServer server=player.getEntityWorld().getServer();if(r.service.state().calls().containsKey(id)){r.service.call(id,player.getUuid());return;}
        String authority="council-call:"+id;String receipt=EventSources.prepare(server,EventSources.id(authority,CALL,"council",""),CALL_SOURCE,player.getUuid(),true,authority);
        try{r.service.call(id,player.getUuid());}
        catch(IOException|RuntimeException failure){EventSources.cancelAuthority(server,authority);throw failure;}
        EventSources.complete(server,receipt,true);
    }
    private static void recoverAndNotify(MinecraftServer server,Runtime r)throws IOException {
        Pending original=r.service.state().pending();r.service.recover();
        if(original==null)return;
        if(original.operation()==Operation.OPEN) {
            signalOpen(server,r,original.ballot());
        }else announce(server,r,original.ballot());
    }
    /** A fully loaded, unchanged producer journal can prove a prepared call never committed. */
    private static void reconcileCalls(MinecraftServer server,Runtime r)throws IOException {
        var prepared=EventSources.pending(server).stream().filter(receipt->receipt.authority().startsWith("council-call:")).toList();
        if(prepared.isEmpty())return;
        r.service.verifyUnchanged();
        for(var receipt:prepared) {
            boolean accepted=CouncilCallEvidence.accepted(receipt,r.service.state().calls(),EventSources.id(receipt.authority(),CALL,"council",""));
            EventSources.complete(server,receipt.id(),accepted);
        }
    }
    private static void requireLectern(ServerPlayerEntity player,Runtime r,String id) {
        AnchorSession session=r.readers.get(player.getUuid());
        if(session==null||!session.anchor().id().equals(id)||!r.service.state().lecterns().contains(id)||!Objects.equals(site(player.getEntityWorld().getServer(),id),session.anchor()))throw new IllegalStateException("Use the bound motion lectern again.");
        AuthoringSite.Anchor anchor=session.anchor();
        if(!anchor.dimension().equals(player.getEntityWorld().getRegistryKey().getValue().toString())||player.isSpectator()||!player.isAlive()
                ||player.squaredDistanceTo(anchor.position().x()+0.5,anchor.position().y()+0.5,anchor.position().z()+0.5)>36)
            throw new IllegalStateException("Return to the motion lectern.");
        var chunk=player.getEntityWorld().getChunkManager().getWorldChunk(anchor.position().x()>>4,anchor.position().z()>>4);
        if(chunk==null||!chunk.getBlockState(new BlockPos(anchor.position().x(),anchor.position().y(),anchor.position().z())).isOf(Blocks.LECTERN))throw new IllegalStateException("The bound lectern is no longer here.");
    }
    private static HouseAssignment member(ServerPlayerEntity p,boolean eligible) {
        if(chancellor(p))throw new IllegalStateException("The Chancellor cannot participate as a house.");
        HouseAssignment a=CouncilRuntime.roster(p.getEntityWorld().getServer()).assignments().get(p.getUuid());
        if(a==null||!a.councilMember())throw new IllegalStateException("Your house has no Council seat.");
        if(eligible&&!PlayRuntime.eligiblePlayers(p.getEntityWorld().getServer()).contains(p))throw new IllegalStateException("Join participation and leave editing or spectator mode first.");return a;
    }
    private static String voteRefusal(ServerPlayerEntity p,Runtime r,UUID id) {
        if(!Objects.equals(id,r.service.state().activeBallot()))return "This ballot is no longer open.";
        if(r.service.state().pending()!=null)return "The Council operation is being recovered.";
        if(!playing(p.getEntityWorld().getServer()))return "Voting waits for active play without an operation hold.";
        Attempt a=r.service.state().attempts().get(id);HouseAssignment member;
        try{member=member(p,true);}catch(RuntimeException e){return e.getMessage();}
        BallotElector elector=a.definition().electorate().get(member.houseId());
        if(elector==null||!elector.actor().equals(p.getUuid()))return "Your house was not present when this ballot opened.";
        if(!Objects.equals(site(p.getEntityWorld().getServer(),a.region().id()),a.region()))return "The voting Region changed. Restore it or ask the Chancellor to void this ballot.";
        if(!inside(p,a.region()))return "Return to the voting Region to cast your vote.";
        if(!r.service.view(id,p.getUuid()).ownChoice().isEmpty())return "Your confirmed vote is final.";return "";
    }
    private static void sendVote(ServerPlayerEntity p,Runtime r,UUID request,UUID id,boolean opening) {
        if(!ServerPlayNetworking.canSend(p,Vote.ID))throw new IllegalStateException("Install the matching Last Council client to read a ballot.");
        Attempt attempt=r.service.state().attempts().get(id);if(attempt==null)throw new IllegalArgumentException("Choose a recorded ballot.");
        if(r.service.state().pending()!=null&&r.service.state().pending().ballot().equals(id))throw new IllegalStateException("Recover this ballot operation first.");
        BallotView projection=r.service.view(id,p.getUuid());
        boolean captured=attempt.definition().electorate().values().stream().anyMatch(e->e.actor().equals(p.getUuid()));
        if(projection.status()==BallotStatus.OPEN&&!captured&&!chancellor(p))throw new IllegalStateException("Your house was not present when this ballot opened.");
        if(!chancellor(p)&&!captured)member(p,false);
        String reason=voteRefusal(p,r,id);
        String description=r.service.state().motions().get(attempt.motion()).text();
        var regency=r.service.state().succession().rounds().get(id);
        if(regency!=null&&regency.result()!=null)description+="\n\n"+regencyOutcome(p.getEntityWorld().getServer(),regency.result());
        List<Voter> voters=attempt.definition().electorate().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new Voter(e.getKey(),e.getValue().actor(),e.getValue().label())).toList();
        Vote packet=new Vote(request,opening,projection,description,reason.isBlank(),reason,voters);
        r.voters.put(p.getUuid(),id);r.sentVotes.put(p.getUuid(),packet);
        ServerPlayNetworking.send(p,packet);
    }
    private static void sendView(ServerPlayerEntity p,Runtime r,UUID request,boolean opening,Mode mode,UUID selected,String message) {
        MinecraftServer server=p.getEntityWorld().getServer();boolean gm=chancellor(p);
        if(!ServerPlayNetworking.canSend(p,View.ID))throw new IllegalStateException("Install the matching Last Council client to read Council business.");
        if(mode==Mode.PROCEEDINGS&&!gm)throw new IllegalStateException("Only the Chancellor controls proceedings.");
        var state=r.service.state();var all=state.motions().values().stream().filter(m->state.activeBallot()!=null&&state.attempts().get(state.activeBallot()).motion().equals(m.id())||state.succession().rounds().keySet().stream().noneMatch(id->state.attempts().get(id).motion().equals(m.id()))).sorted(Comparator.comparing(Motion::title).thenComparing(Motion::id)).toList();
        List<Row> rows=all.stream().map(m->new Row(m.id(),shortLabel(m.title(),48),(r.service.deferredThisMeeting(m.id())?"DEFERRED":m.status().name()),m.hostPrepared()?"Chancellor":shortLabel(house(server,m.proposerHouse()),24),m.ready(1))).toList();
        if(mode==Mode.PROCEEDINGS&&state.activeBallot()!=null)selected=state.attempts().get(state.activeBallot()).motion();
        if(!Objects.equals(selected,r.selected.get(p.getUuid())))r.historyPages.put(p.getUuid(),0);
        Motion motion=selected==null?null:state.motions().get(selected);Detail detail=null;
        HouseAssignment own=CouncilRuntime.roster(server).assignments().get(p.getUuid());boolean eligible=PlayRuntime.eligiblePlayers(server).contains(p)&&playing(server);
        if(motion!=null){
            boolean deferred=r.service.deferredThisMeeting(motion.id());boolean awaiting=motion.status()==MotionStatus.SUBMITTED&&!deferred;boolean seated=own!=null&&own.councilMember();
            int pages=(motion.ballots().size()+3)/4;int page=pages==0?0:Math.min(r.historyPages.getOrDefault(p.getUuid(),0),pages-1);r.historyPages.put(p.getUuid(),page);
            detail=new Detail(motion.id(),motion.title(),motion.text(),motion.hostPrepared()?"Chancellor":house(server,motion.proposerHouse()),deferred?"DEFERRED":motion.status().name(),motion.visibility().name(),motion.threshold().name(),
                    motion.sponsors().stream().map(id->house(server,id)).sorted().toList(),motion.notes(),
                    seated&&eligible&&awaiting&&!motion.proposerHouse().equals(own.houseId())&&!motion.sponsors().contains(own.houseId()),
                    seated&&eligible&&motion.ballots().isEmpty()&&motion.status()!=MotionStatus.WITHDRAWN&&motion.proposerHouse().equals(own.houseId()),
                    gm&&playing(server)&&awaiting&&motion.ready(1),gm&&playing(server)&&state.phase()==Phase.DISCUSSION&&state.activeBallot()==null&&!deferred&&motion.status()==MotionStatus.AGENDA,
                    gm&&playing(server)&&state.activeBallot()==null&&!deferred&&(state.phase()==Phase.DISCUSSION||state.phase()==Phase.RECESS)
                        &&(motion.status()==MotionStatus.SUBMITTED||motion.status()==MotionStatus.AGENDA),
                    ProceedingsPayloads.attemptPage(motion.ballots(),page).stream().map(id->{Attempt a=state.attempts().get(id);BallotView view=historyProjection(id,state.pending(),()->r.service.view(id,null));return view==null?null:new AttemptView(id,a.meeting(),view,voters(a));}).filter(Objects::nonNull).toList(),page,pages);
        }
        List<History> history=state.attempts().values().stream().map(a->{BallotView v;try{v=r.service.view(a.definition().id(),p.getUuid());}catch(RuntimeException absent){return null;}return new History(v.id(),shortLabel(v.title(),48),v.status().name());}).filter(Objects::nonNull).sorted(Comparator.comparing(History::title).thenComparing(History::id)).toList();
        String problem=state.pending()!=null?"A saved Council operation needs recovery.":r.failed?"Council records need recovery.":RuntimeHolds.reason(server)!=null?"A Chronicle operation hold is active.":playing(server)?"":"Play is paused or stopped.";
        if(mode==Mode.PROCEEDINGS)r.gm.add(p.getUuid());else r.gm.remove(p.getUuid());
        r.selected.put(p.getUuid(),selected);
        Progress progress=mode==Mode.PROCEEDINGS&&gm?progress(r):null;
        Regency regency=regencyView(server,r);
        View packet=new View(request,opening,mode,mode==Mode.MOTIONS?r.readers.get(p.getUuid()).anchor().id():"",state.revision(),state.meeting(),state.phase().name(),rows,detail,history,state.activeBallot(),gm,playing(server),state.pending()!=null||r.failed,problem,message,regency,mode==Mode.MOTIONS&&!gm&&own!=null&&own.councilMember()&&eligible,progress);
        if(!ProceedingsPayloads.fitsView(packet))throw new IllegalStateException("This motion's ballot history is too large to display safely.");
        if(mode==Mode.PROCEEDINGS){r.sentProgress.put(p.getUuid(),progress);r.sentRegency.put(p.getUuid(),regency);}
        ServerPlayNetworking.send(p,packet);
    }
    private static List<Voter> voters(Attempt attempt) {
        return attempt.definition().electorate().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new Voter(e.getKey(),e.getValue().actor(),e.getValue().label())).toList();
    }
    static BallotView historyProjection(UUID id,Pending pending,java.util.function.Supplier<BallotView> projection) {
        try{return projection.get();}
        catch(IllegalArgumentException failure){
            if(pending!=null&&pending.operation()==Operation.OPEN&&pending.ballot().equals(id)&&"Unknown ballot".equals(failure.getMessage()))return null;
            throw failure;
        }
    }
    private static Progress progress(Runtime r) {
        UUID id=r.service.state().activeBallot();if(id==null||r.service.state().pending()!=null)return null;
        Attempt a=r.service.state().attempts().get(id);BallotView view=r.service.view(id,null);
        List<String> outstanding=view.visibility()==BallotVisibility.SECRET?List.of():a.definition().electorate().entrySet().stream()
                .filter(e->!view.namedVotes().containsKey(e.getKey())).sorted(Map.Entry.comparingByKey()).map(e->e.getValue().label()).toList();
        return new Progress(id,r.service.submissionCount(id),a.definition().electorate().size(),outstanding);
    }
    private static void refresh(MinecraftServer server,Runtime r) {
        for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList()) {
            if(r.gm.contains(p.getUuid())&&chancellor(p))try{sendView(p,r,CouncilPayloads.NONE,false,Mode.PROCEEDINGS,r.selected.get(p.getUuid()),"");}catch(RuntimeException failure){r.gm.remove(p.getUuid());r.sentProgress.remove(p.getUuid());notice(p,CouncilPayloads.NONE,failure);}
            else if(r.readers.containsKey(p.getUuid()))try{requireLectern(p,r,r.readers.get(p.getUuid()).anchor().id());sendView(p,r,CouncilPayloads.NONE,false,Mode.MOTIONS,r.selected.get(p.getUuid()),"");}catch(RuntimeException failure){r.readers.remove(p.getUuid());notice(p,CouncilPayloads.NONE,failure);}
        }
    }
    private static void tick(MinecraftServer server) {
        Runtime r=WORLDS.get(server);if(r==null||server.getTicks()%10!=0)return;
        if(server.getTicks()%100==0)try{reconcileCalls(server,r);}catch(EventSources.Unavailable sourceOwned){/* Core owns its source recovery hold. */}catch(Exception failure){hold(server,failure);}
        String publicStatus=ChronicleRuntime.service(server).state().phase()+"|"+RuntimeHolds.reason(server)+"|"+r.failed;
        if(!publicStatus.equals(r.publicStatus)){r.publicStatus=publicStatus;refresh(server,r);}
        for(UUID id:new HashSet<>(r.gm)) {
            ServerPlayerEntity p=server.getPlayerManager().getPlayer(id);
            if(p==null||!chancellor(p)){r.gm.remove(id);r.sentProgress.remove(id);r.sentRegency.remove(id);if(p!=null)notice(p,CouncilPayloads.NONE,new IllegalStateException("Only the Chancellor controls proceedings."));continue;}
            try { if(!Objects.equals(r.sentProgress.get(id),progress(r))||!Objects.equals(r.sentRegency.get(id),regencyView(server,r)))sendView(p,r,CouncilPayloads.NONE,false,Mode.PROCEEDINGS,r.selected.get(id),""); }
            catch(RuntimeException failure){r.gm.remove(id);r.sentProgress.remove(id);notice(p,CouncilPayloads.NONE,failure);}
        }
        for(var e:new HashMap<>(r.voters).entrySet()) {
            ServerPlayerEntity p=server.getPlayerManager().getPlayer(e.getKey());if(p==null){r.voters.remove(e.getKey());continue;}
            try {
                Vote previous=r.sentVotes.get(e.getKey());BallotView view=r.service.view(e.getValue(),p.getUuid());String reason=voteRefusal(p,r,e.getValue());
                if(previous==null||!previous.ballot().equals(view)||previous.canCast()!=reason.isBlank()||!previous.reason().equals(reason))sendVote(p,r,CouncilPayloads.NONE,e.getValue(),false);
            }catch(RuntimeException ignored){r.voters.remove(e.getKey());r.sentVotes.remove(e.getKey());}
        }
    }
    private static boolean captured(Runtime r,ServerPlayerEntity p){UUID id=r.service.state().activeBallot();return id!=null&&r.service.state().attempts().get(id).definition().electorate().values().stream().anyMatch(e->e.actor().equals(p.getUuid()));}
    private static void signal(ServerPlayerEntity p,UUID id,Runtime r){if(id!=null&&ServerPlayNetworking.canSend(p,Signal.ID))ServerPlayNetworking.send(p,new Signal(id,r.service.state().attempts().get(id).definition().title(),true));}
    private static void signalOpen(MinecraftServer server,Runtime r,UUID id) {
        for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList())if(captured(r,p)||chancellor(p))signal(p,id,r);
    }
    private static void announce(MinecraftServer server,Runtime r,UUID id) {
        BallotView view=r.service.view(id,CouncilPayloads.NONE);
        var round=r.service.state().succession().rounds().get(id);
        String text=round!=null&&round.result()!=null?regencyOutcome(server,round.result()):view.status()==BallotStatus.VOID?"Ballot voided: "+view.title()+". Reason: "+view.reason():"Ballot recorded: "+view.title()+". "+(view.passedOptions().isEmpty()?"The motion did not pass.":"The motion passed.");
        for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList()) {
            if(ServerPlayNetworking.canSend(p,Signal.ID))ServerPlayNetworking.send(p,new Signal(id,view.title(),false));
            p.sendMessage(Text.literal(text),false);
        }
    }
    private static Map<String,String> presentCandidates(MinecraftServer server,Map<String,BallotElector> electorate,SuccessionState election) {
        Map<String,String> result=new TreeMap<>();
        CouncilRuntime.roster(server).assignments().values().stream().filter(a->a.councilMember()&&(election.started()?election.remaining().contains(a.houseId()):a.candidate())&&electorate.containsKey(a.houseId()))
                .forEach(a->result.put(a.houseId(),electorate.get(a.houseId()).label()+" ("+house(server,a.houseId())+")"));
        return result;
    }
    private static Map<String,BallotElector> electorate(MinecraftServer server,AuthoringSite.Region region) throws IOException {
        Map<String,BallotElector> result=new LinkedHashMap<>();
        for(ServerPlayerEntity voter:PlayRuntime.eligiblePlayers(server)) {
            HouseAssignment a=CouncilRuntime.roster(server).assignments().get(voter.getUuid());
            if(a!=null&&a.councilMember()&&!chancellor(voter)&&inside(voter,region))result.put(a.houseId(),new BallotElector(voter.getUuid(),Profiles.publicCard(server,voter.getUuid(),voter.getName().getString()).name()));
        }
        return result;
    }
    private static String emptyElectorateReason(MinecraftServer server,AuthoringSite.Region region) {
        var online=server.getPlayerManager().getPlayerList();var eligible=PlayRuntime.eligiblePlayers(server);
        long inside=online.stream().filter(p->inside(p,region)).count();
        long seated=online.stream().filter(p->inside(p,region)).filter(p->{var a=CouncilRuntime.roster(server).assignments().get(p.getUuid());return a!=null&&a.councilMember();}).count();
        long outside=eligible.stream().filter(p->!chancellor(p)&&!inside(p,region)).filter(p->{var a=CouncilRuntime.roster(server).assignments().get(p.getUuid());return a!=null&&a.councilMember();}).count();
        return emptyElectorateMessage(inside,seated,outside);
    }
    static String emptyElectorateMessage(long inside,long seated,long outside) {
        return "No eligible representatives are inside the voting Region; no ballot opened. Online inside: "+inside+"; inside with a Council House: "+seated+"; eligible Council representatives outside: "+outside+". Representatives must join participation, leave spectator and author modes, and cannot be the Chancellor.";
    }
    private static String candidateLabel(MinecraftServer server,String id) {
        var assignment=CouncilRuntime.roster(server).assignments().values().stream().filter(a->a.houseId().equals(id)).findFirst();
        if(assignment.isEmpty())return house(server,id);
        UUID owner=assignment.get().owner();ServerPlayerEntity online=server.getPlayerManager().getPlayer(owner);
        try{return Profiles.publicCard(server,owner,online==null?"Assigned character":online.getName().getString()).name()+" ("+house(server,id)+")";}
        catch(IOException failure){return house(server,id);}
    }
    private static Regency regencyView(MinecraftServer server,Runtime r) {
        var election=r.service.state().succession();
        Map<String,String> present=Map.of();boolean available=true;
        try{present=presentCandidates(server,electorate(server,votingRegion(server,r)),election);}catch(IOException|RuntimeException unavailable){available=false;}
        Set<String> slate=election.started()?election.remaining():present.keySet();
        var missing=new TreeSet<>(slate);missing.removeAll(present.keySet());
        List<String> labels=election.stage()==SuccessionState.Stage.NOT_STARTED||election.stage()==SuccessionState.Stage.CONTESTED?slate.stream().sorted().map(id->candidateLabel(server,id)).toList():List.of();
        String summary=switch(election.stage()) {
            case NOT_STARTED -> "The candidate slate locks when the first regency ballot opens.";
            case CONTESTED -> "The crown remains undecided.";
            case CROWNED -> house(server,election.crowned())+" was crowned.";
            case VACANT -> "The throne remains vacant.";
            case UNRESOLVED -> "Succession ended unresolved; no monarch was elected.";
        };
        var latest=election.rounds().values().stream().filter(round->round.result()!=null)
                .max(Comparator.comparingInt(SuccessionState.Round::meeting));
        if(latest.isPresent()&&election.stage()==SuccessionState.Stage.CONTESTED)summary+="\n"+regencyOutcome(server,latest.get().result());
        if(!available)summary+="\nThe voting Region or candidate information is unavailable.";
        else if(!election.started()&&(slate.size()<2||slate.size()>15))summary+="\nTwo to fifteen eligible candidates must be present in the voting Region.";
        else if(!missing.isEmpty())summary+="\nWaiting for: "+String.join(", ",missing.stream().map(id->candidateLabel(server,id)).toList());
        if(!r.service.pendingMotions().isEmpty())summary+="\nVote on or defer "+r.service.pendingMotions().size()+" pending motions first.";
        boolean open=(election.stage()==SuccessionState.Stage.NOT_STARTED||election.stage()==SuccessionState.Stage.CONTESTED)
                &&r.service.state().meeting()>election.lastCompletedMeeting()&&r.service.state().phase()==Phase.DISCUSSION
                &&r.service.state().activeBallot()==null&&r.service.state().pending()==null&&!r.failed&&playing(server)
                &&available&&missing.isEmpty()&&(election.started()||slate.size()>=2&&slate.size()<=15)&&r.service.pendingMotions().isEmpty();
        return new Regency(election.stage().name(),labels,summary,open);
    }
    private static String regencyOutcome(MinecraftServer server,SuccessionRules.Result result) {
        return switch(result.decision()) {
            case CROWNED -> "The Council crowns "+house(server,result.house())+".";
            case VACANT -> "No candidate secured the required majority. The throne remains vacant.";
            case ELIMINATED -> (result.tiedLast().isEmpty()?"":"A draw among "+String.join(", ",result.tiedLast().stream().map(id->house(server,id)).toList())+" selected "+house(server,result.house())+". ")
                    +house(server,result.house())+" leaves the crown slate and retains its Council vote. The next regency ballot requires a later meeting.";
        };
    }
    private static boolean rate(Runtime r,ServerPlayerEntity p){int tick=p.getEntityWorld().getServer().getTicks()/20;int[] value=r.limits.computeIfAbsent(p.getUuid(),id->new int[]{tick,0});if(value[0]!=tick){value[0]=tick;value[1]=0;}return ++value[1]<=20;}
    private static boolean chancellor(ServerPlayerEntity p){return CouncilRuntime.isChancellor(p.getEntityWorld().getServer(),p.getUuid());}
    private static void requirePlaying(MinecraftServer s){if(!playing(s))throw new IllegalStateException("This action needs active play without an operation hold.");}
    private static boolean playing(MinecraftServer s){return ChronicleRuntime.service(s).state().phase()==SessionPhase.ACTIVE&&!SessionBuilding.enabled(s)&&RuntimeHolds.reason(s)==null;}
    private static AuthoringSite site(MinecraftServer s,String id){return AuthoringRuntime.get(s).service().state().sites().get(id);}
    private static AuthoringSite.Region votingRegion(MinecraftServer s,Runtime r){AuthoringSite value=site(s,r.service.state().votingRegion());if(!(value instanceof AuthoringSite.Region region))throw new IllegalStateException("Assign Council voting to a Region first.");return region;}
    private static BlockPosition position(BlockPos p){return new BlockPosition(p.getX(),p.getY(),p.getZ());}
    private static boolean inside(ServerPlayerEntity p,AuthoringSite.Region r){return p.getEntityWorld().getRegistryKey().getValue().toString().equals(r.dimension())&&r.contains(position(p.getBlockPos()));}
    private static String house(MinecraftServer s,String id){var h=CouncilRuntime.houses(s).get(id);return h==null?id:h.title();}
    private static String shortLabel(String text,int count){return text.codePointCount(0,text.length())<=count?text:text.substring(0,text.offsetByCodePoints(0,count-3))+"...";}
    private static UUID none(UUID id){return id.equals(CouncilPayloads.NONE)?null:id;}
    private static void notice(ServerPlayerEntity p,UUID request,Exception failure){String message=failure instanceof IOException?"Council data could not be saved. Existing records were preserved; inspect the server log.":Objects.toString(failure.getMessage(),"Council action unavailable.");if(message.length()>512)message=message.substring(0,512);if(ServerPlayNetworking.canSend(p,CouncilPayloads.Notice.ID))ServerPlayNetworking.send(p,new CouncilPayloads.Notice(request,message));p.sendMessage(Text.literal(message),false);}
    private static void hold(MinecraftServer s,Exception failure){Runtime r=WORLDS.get(s);if(r!=null)r.failed=true;RuntimeHolds.hold(s,HOLD,"Council records need recovery. Open proceedings or inspect the stopped world's Council journals.");io.github.chronicle.core.ChronicleCore.LOGGER.error("Council operation retained for recovery",failure);}
    private record AnchorSession(AuthoringSite.Anchor anchor) { }
    private static final class Runtime {
        final ProceedingsService service;boolean failed;String publicStatus="";final Map<UUID,AnchorSession> readers=new HashMap<>();final Set<UUID> gm=new HashSet<>();
        final Map<UUID,UUID> selected=new HashMap<>(),voters=new HashMap<>();final Map<UUID,Vote> sentVotes=new HashMap<>();final Map<UUID,int[]> limits=new HashMap<>();final Map<UUID,Integer> historyPages=new HashMap<>();
        final Map<UUID,Progress> sentProgress=new HashMap<>();
        final Map<UUID,Regency> sentRegency=new HashMap<>();
        Runtime(ProceedingsService service){this.service=service;}
    }
}
