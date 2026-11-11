package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.core.network.ProfilePayloads;
import io.github.chronicle.core.persistence.AtomicDataFile;
import io.github.chronicle.core.server.*;
import io.github.chronicle.lastcouncil.LastCouncil;
import io.github.chronicle.lastcouncil.content.*;
import io.github.chronicle.lastcouncil.house.*;
import io.github.chronicle.lastcouncil.office.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.lastcouncil.network.CouncilPayloads;
import io.github.chronicle.lastcouncil.network.CouncilPayloads.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import static net.minecraft.server.command.CommandManager.literal;

/** Server-thread-only house authority. The authenticated connection is the only private recipient selector. */
public final class CouncilRuntime {
    private static final String HOLD="last_council:houses";
    private static final Map<MinecraftServer,State> WORLDS=new IdentityHashMap<>();
    private static final SecureRandom RANDOM=new SecureRandom();
    private CouncilRuntime() { }
    public static void register(){
        ScenarioContentRuntime.registerExtension(HOLD,new ScenarioContentRuntime.ExtensionAdapter() {
            public void validate(String json) throws IOException { CouncilContentJson.decode(json.getBytes(StandardCharsets.UTF_8)); }
            public String capture(MinecraftServer server) throws IOException {
                if(!active()) return null;
                if(!server.isOnThread() || !WORLDS.containsKey(server)) throw new IOException("House definitions unavailable.");
                State state=WORLDS.get(server); state.contentFile.verifyUnchanged();
                return new String(CouncilContentJson.encode(state.content),StandardCharsets.UTF_8);
            }
            public void initialize(java.nio.file.Path directory,String initial,boolean legacy) throws IOException { CouncilContentProfiles.initialize(directory,initial,legacy); }
            public void verify(java.nio.file.Path directory) throws IOException { CouncilContentProfiles.verify(directory); }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server->{if(active())start(server);});
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
        ServerTickEvents.START_SERVER_TICK.register(CouncilRuntime::issueAtFirstPlay);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            sendCapability(handler.player);
        });
        ServerDisconnects.register((handler,server)->{var state=WORLDS.get(server);if(state!=null){state.pending.remove(handler.player.getUuid());state.limits.remove(handler.player.getUuid());}});
        ServerPlayNetworking.registerGlobalReceiver(PersonalRequest.ID,(request,context)->context.server().execute(()->personal(context.player(),request)));
        ServerPlayNetworking.registerGlobalReceiver(AdminRequest.ID,(request,context)->context.server().execute(()->admin(context.player(),request)));
        PlayerMenuExtensions.register("lc:houses","House & papers…",(viewer,target)->active()&&WORLDS.containsKey(viewer.getEntityWorld().getServer()),CouncilRuntime::openAdmin);
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(literal("chronicle").then(literal("council")
            .executes(c->{var player=c.getSource().getPlayer();if(player==null)return 0;return openPersonal(player);})
            .then(literal("characters").requires(s->s.hasPermissionLevel(2)).executes(c->{var player=c.getSource().getPlayer();if(player==null)return 0;openAdmin(player,player.getUuid());return 1;})))));
    }
    public static boolean isChancellor(MinecraftServer server,UUID player){State state=WORLDS.get(server);return state!=null&&player.equals(state.office.snapshot().chancellor());}
    private static void sendCapability(ServerPlayerEntity player){if(ServerPlayNetworking.canSend(player,Capability.ID))ServerPlayNetworking.send(player,new Capability(active(),isChancellor(player.getEntityWorld().getServer(),player.getUuid())));}
    public static HouseRoster roster(MinecraftServer server) {
        if(!server.isOnThread()||!WORLDS.containsKey(server))throw new IllegalStateException("House assignments unavailable.");
        return WORLDS.get(server).rosterSnapshot();
    }
    /** Reusable definitions only; administrative projections must enforce their own permission. */
    public static CouncilContent content(MinecraftServer server) {
        if(!server.isOnThread()||!WORLDS.containsKey(server))throw new IllegalStateException("House definitions unavailable.");
        return WORLDS.get(server).content;
    }
    public static String paperSetupProblem(MinecraftServer server){
        if(!server.isOnThread()||!WORLDS.containsKey(server))return "House definitions are unavailable.";
        State state=WORLDS.get(server);if(state.cast!=null||state.rosterSnapshot().assignments().isEmpty())return "";
        try{HouseCastGenerator.validateAssignments(state.content,state.rosterSnapshot());return "";}catch(IllegalArgumentException failure){return Objects.toString(failure.getMessage(),"House papers cannot be assigned with this setup.");}
    }
    public static boolean papersCommitted(MinecraftServer server) {
        if(!server.isOnThread()||!WORLDS.containsKey(server))throw new IllegalStateException("House definitions unavailable.");
        State state=WORLDS.get(server);return state.cast!=null||state.issuer.hasPending();
    }
    public static String contentEditReason(ServerPlayerEntity player) {
        var server=player.getEntityWorld().getServer();
        if(server.getPlayerManager().getPlayer(player.getUuid())!=player||!player.getCommandSource().hasPermissionLevel(2))return "Operator access is required.";
        if(!active()||!WORLDS.containsKey(server))return "House definitions are unavailable.";
        if(!AuthoringRuntime.editable(server)||!AuthoringRuntime.isAuthor(player))return "Pause play and enable Editing to change source definitions.";
        if(RuntimeHolds.heldByOther(server,HOLD))return "Resolve the pending operation before editing source definitions.";
        if(papersCommitted(server))return "House papers have been issued. Source definitions are read-only for this experience.";
        return "";
    }
    /** Server-thread CAS; the editor independently checks the authenticated operator on each request. */
    public static void replaceContent(MinecraftServer server,CouncilContent expected,CouncilContent replacement)throws IOException {
        if(!server.isOnThread()||!WORLDS.containsKey(server))throw new IllegalStateException("House definitions unavailable.");
        State state=WORLDS.get(server);
        if(!AuthoringRuntime.editable(server)||RuntimeHolds.heldByOther(server,HOLD)||papersCommitted(server))throw new IllegalStateException("Source definitions are currently read-only.");
        if(!state.content.equals(expected))throw new IllegalStateException("Source definitions changed. Reopen the editor before saving.");
        for(var assignment:state.rosterSnapshot().assignments().values())if(!replacement.houses().containsKey(assignment.houseId()))throw new IllegalStateException("An assigned House cannot be removed.");
        byte[] bytes=CouncilContentJson.encode(replacement);
        try {state.contentFile.verifyUnchanged();state.contentFile.save(bytes);}
        catch(IOException failure){RuntimeHolds.hold(server,"last_council:house_content","House content could not be saved exactly. Preserve the files and restart to load the saved definitions.");throw failure;}
        state.roster.replaceDefinitions(replacement.houses());state.content=replacement;state.pending.clear();state.issuanceFailed=false;RuntimeHolds.release(server,HOLD);
    }
    static HouseCast committedCast(MinecraftServer server){
        if(!server.isOnThread()||!WORLDS.containsKey(server))throw new IllegalStateException("House papers are unavailable.");
        return WORLDS.get(server).cast;
    }
    public static Map<String,HouseDefinition> houses(MinecraftServer server) {
        if(!server.isOnThread()||!WORLDS.containsKey(server))throw new IllegalStateException("House definitions unavailable.");
        return WORLDS.get(server).content.houses();
    }
    public static boolean active(){return PackagedScenarios.mechanicsFor(LastCouncil.SCENARIO,LastCouncil.MECHANICS);}
    private static void start(MinecraftServer server){
        try{
            var profile=ScenarioAuthoringProfiles.active().isPresent();
            var directory=ScenarioAuthoringProfiles.chronicleConfig().resolve(profile?"profile-content":"");
            var file=new AtomicDataFile(CouncilContentProfiles.file(directory),CouncilContentJson.MAX_BYTES);
            byte[] bytes=file.load();
            if(bytes==null){
                if(profile)throw new IOException("Initialized profile House content is missing; restore it.");
                else try(var input=CouncilRuntime.class.getResourceAsStream("/data/last_council/chronicle/houses.json")){
                    if(input==null)throw new IOException("Bundled house content missing");bytes=input.readNBytes(CouncilContentJson.MAX_BYTES+1);
                }
                CouncilContentJson.decode(bytes);file.save(bytes);
            }
            CouncilContent content=CouncilContentJson.decode(bytes);
            var root=server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/last-council");
            var roster=new HouseRosterService(content.houses(),new HouseRosterStore(root.resolve("roster.json")));
            var castFile=new HouseCastStore(root.resolve("cast.json"));var cast=castFile.load();
            validateCast(roster.snapshot(),cast);
            var office=new CouncilOfficeService(new CouncilOfficeStore(root.resolve("chancellor.json")));
            if(office.snapshot().chancellor()!=null&&roster.snapshot().assignments().containsKey(office.snapshot().chancellor()))
                throw new IllegalStateException("The Chancellor also has a house assignment. Repair the stopped world; neither record was removed.");
            var issuer=new HouseCastIssuer(root);
            if(cast!=null&&issuer.hasPending())cast=issuer.ensure(content,roster.snapshot(),()->{throw new IllegalStateException("A committed automatic cast must not be regenerated.");});
            WORLDS.put(server,new State(root,content,file,roster,castFile,cast,office,issuer));RuntimeHolds.release(server,HOLD);
            issueAtFirstPlay(server);
        }catch(IOException|RuntimeException failure){WORLDS.remove(server);io.github.chronicle.core.ChronicleCore.LOGGER.error("Last Council house data unavailable; existing files were preserved",failure);RuntimeHolds.hold(server,HOLD,"Last Council house data is unavailable. Check houses.json and the world roster/cast while stopped.");}
    }
    private static void validateCast(HouseRoster roster,HouseCast cast){
        if(cast==null)return;
        if(!roster.assignments().keySet().equals(cast.papers().keySet()))throw new IllegalStateException("Committed cast and house assignments differ.");
        roster.assignments().forEach((owner,assignment)->{if(!assignment.houseId().equals(cast.papers().get(owner).houseId()))throw new IllegalStateException("Committed cast owner changed.");});
    }
    private static State require(ServerPlayerEntity player){
        var server=player.getEntityWorld().getServer();
        if(!server.isOnThread()||server.getPlayerManager().getPlayer(player.getUuid())!=player)throw new IllegalStateException("This connection is no longer current.");
        if(!active()||!WORLDS.containsKey(server))throw new IllegalStateException("Last Council house data is unavailable on this server.");
        return WORLDS.get(server);
    }
    private static boolean allow(State state,ServerPlayerEntity player){
        long now=System.nanoTime();var prior=state.limits.get(player.getUuid());
        if(prior==null||now-prior.window()>1_000_000_000L){state.limits.put(player.getUuid(),new Limit(now,1));return true;}
        state.limits.put(player.getUuid(),new Limit(prior.window(),Math.min(21,prior.count()+1)));return prior.count()<20;
    }
    private static List<PublicHouse> publicHouses(MinecraftServer server,State state)throws IOException{
        List<PublicHouse> result=new ArrayList<>();
        for(var house:state.content.houses().values().stream().sorted(Comparator.comparing(HouseDefinition::title).thenComparing(HouseDefinition::id)).toList()){
            var assignment=state.rosterSnapshot().assignments().values().stream().filter(a->a.houseId().equals(house.id())).findFirst().orElse(null);
            String representative="";if(assignment!=null){var online=server.getPlayerManager().getPlayer(assignment.owner());representative=Profiles.publicCard(server,assignment.owner(),online==null?"Assigned character":online.getName().getString()).name();}
            result.add(new PublicHouse(house.id(),house.title(),house.reputation(),house.description(),house.interests(),representative,assignment!=null&&assignment.councilMember(),assignment!=null&&ProceedingsRuntime.candidate(server,house.id(),assignment.candidate()),house.defaultCandidate()));
        }
        return result;
    }
    private static void personal(ServerPlayerEntity player,PersonalRequest request){
        try{
            State state=require(player);if(!allow(state,player))return;
            if(request.offset()<0||request.offset()>124||request.offset()%8!=0)throw new IllegalArgumentException("Choose a valid papers page.");
            var assignment=state.rosterSnapshot().assignments().get(player.getUuid());String houseId=assignment==null?"":assignment.houseId();
            List<String> all=new ArrayList<>();String message="";
            if(assignment==null)message=isChancellor(player.getEntityWorld().getServer(),player.getUuid())?"As Chancellor, you do not represent a house.":"Your house has not been assigned yet.";
            else if(request.section()==Section.INFORMATION){var h=state.content.houses().get(houseId);all.add(h.reputation());all.add(h.description());all.add(h.interests());}
            else if(state.cast==null)message="Your house papers have not been issued yet.";
            else{
                var papers=state.cast.papers().get(player.getUuid());
                switch(request.section()){
                    case SECRETS->all.addAll(papers.secrets());case GOALS->all.addAll(papers.goals());
                    case KNOWN_SECRETS->{for(var known:papers.knownSecrets())all.add(state.content.houses().get(known.targetHouseId()).title()+"\n"+known.body());}
                    default->throw new IllegalArgumentException("Unknown papers section.");
                }
            }
            int offset=Math.min(request.offset(),all.isEmpty()?0:((all.size()-1)/8)*8),end=Math.min(all.size(),offset+8);
            ServerPlayNetworking.send(player,new PersonalView(request.request(),houseId,publicHouses(player.getEntityWorld().getServer(),state),request.section(),offset,offset>0,end<all.size(),all.subList(offset,end),message));
        }catch(IOException|RuntimeException failure){notice(player,request.request(),failure);}
    }
    private static int openPersonal(ServerPlayerEntity player){
        try{require(player);if(!ServerPlayNetworking.canSend(player,PersonalView.ID))throw new IllegalStateException("Install the matching Last Council client.");var card=Profiles.publicCard(player.getEntityWorld().getServer(),player.getUuid(),player.getName().getString());ServerPlayNetworking.send(player,new ProfilePayloads.View(card.owner(),card.name(),card.pronouns(),card.description(),card.revision(),true,false));return 1;}
        catch(IOException|RuntimeException failure){player.sendMessage(Text.literal(shortMessage(failure)),false);return 0;}
    }
    public static void openAdmin(ServerPlayerEntity player,UUID target){
        try{State state=require(player);operator(player);if(!ServerPlayNetworking.canSend(player,AdminView.ID))throw new IllegalStateException("Install the matching Last Council client.");sendAdmin(player,state,CouncilPayloads.NONE,target,true,"");}
        catch(IOException|RuntimeException failure){player.sendMessage(Text.literal(shortMessage(failure)),false);}
    }
    private static void operator(ServerPlayerEntity player){if(!player.getCommandSource().hasPermissionLevel(2))throw new IllegalStateException("Only an operator can manage Council setup.");}
    private static String editReason(ServerPlayerEntity player,State state){
        if(ProceedingsRuntime.ballotOpen(player.getEntityWorld().getServer()))return "Close or void the current ballot before changing the cast.";
        if(ProceedingsRuntime.successionStarted(player.getEntityWorld().getServer()))return "The succession slate is locked. House setup cannot reset an election.";
        if(state.issuer.hasPending()&&state.cast==null)return "House papers need issuance recovery before assignments can change.";
        if(state.cast!=null)return "The cast is committed. Character-to-house assignments are fixed for this experience.";
        if(!AuthoringRuntime.editable(player.getEntityWorld().getServer())||!AuthoringRuntime.isAuthor(player))return "Pause play and enable editing before changing the cast.";
        if(RuntimeHolds.heldByOther(player.getEntityWorld().getServer(),HOLD))return "Resolve the active Chronicle operation hold first.";
        return "";
    }
    private static String lateAssignmentReason(ServerPlayerEntity player,State state,UUID target){
        var server=player.getEntityWorld().getServer();
        if(state.cast==null)return "Papers have not been issued; use normal House assignment.";
        if(state.rosterSnapshot().assignments().containsKey(target))return "This character already has issued House papers. Reassignment is not supported.";
        if(isChancellor(server,target))return "The Chancellor cannot also represent a House.";
        if(ProceedingsRuntime.ballotOpen(server))return "Close or void the current ballot before adding a House representative.";
        if(!AuthoringRuntime.editable(server)||!AuthoringRuntime.isAuthor(player))return "Pause play and enable Editing to add a House representative.";
        if(RuntimeHolds.reason(server)!=null)return "Resolve the Chronicle operation hold before adding a House representative.";
        return "";
    }
    private static String officeReason(ServerPlayerEntity player){
        var server=player.getEntityWorld().getServer();
        if(ProceedingsRuntime.ballotOpen(server))return "Close or void the current ballot before changing the Chancellor.";
        if(!AuthoringRuntime.editable(server)||!AuthoringRuntime.isAuthor(player))return "Pause play and enable Editing to change the Chancellor.";
        if(RuntimeHolds.heldByOther(server,HOLD))return "Resolve the Chronicle operation hold first.";
        return "";
    }
    private static void issueAtFirstPlay(MinecraftServer server){
        State state=WORLDS.get(server);if(state==null||state.cast!=null||state.issuanceFailed)return;
        var campaign=ChronicleRuntime.service(server).state();
        if(campaign.sessionNumber()<1||campaign.phase()!=SessionPhase.ACTIVE||SessionBuilding.enabled(server)||RuntimeHolds.heldByOther(server,HOLD))return;
        try {state.contentFile.verifyUnchanged();HouseCast issued=state.issuer.ensure(state.content,state.roster.snapshot(),RANDOM::nextLong);state.publishBaseCast(issued);state.pending.clear();RuntimeHolds.release(server,HOLD);}
        catch(IOException|RuntimeException failure){state.issuanceFailed=true;RuntimeHolds.hold(server,HOLD,"House papers need recovery. Pause, enable Editing, then use Retry paper issuance in Players > House & papers.");io.github.chronicle.core.ChronicleCore.LOGGER.error("House paper issuance held; existing cast preserved",failure);}
    }
    private static void admin(ServerPlayerEntity player,AdminRequest request){
        try{
            State state=require(player);operator(player);if(!allow(state,player))return;
            UUID target=request.target();var server=player.getEntityWorld().getServer();
            boolean officeAction=request.operation()==Operation.APPOINT_CHANCELLOR||request.operation()==Operation.CLEAR_CHANCELLOR||request.operation()==Operation.RETRY_PAPERS;
            if(officeAction){String reason=officeReason(player);if(!reason.isBlank())throw new IllegalStateException(reason);
                if(request.officeRevision()!=state.office.snapshot().revision())throw new IllegalStateException("The Chancellor assignment changed. Reopen setup.");}
            boolean lateAssignment=request.operation()==Operation.ASSIGN&&state.cast!=null;
            if(request.operation()!=Operation.OPEN&&!officeAction){String reason=lateAssignment?lateAssignmentReason(player,state,target):editReason(player,state);if(!reason.isBlank())throw new IllegalStateException(reason);if(request.revision()!=state.rosterSnapshot().revision())throw new IllegalStateException("The roster changed. Review it again.");state.contentFile.verifyUnchanged();}
            String message="";
            switch(request.operation()){
                case OPEN->{ }
                case APPOINT_CHANCELLOR->{
                    if(server.getPlayerManager().getPlayer(target)==null)throw new IllegalArgumentException("Choose an online Chancellor.");
                    state.office.appoint(target,state.rosterSnapshot().assignments().keySet());
                    for(var connected:server.getPlayerManager().getPlayerList()){sendCapability(connected);server.getCommandManager().sendCommandTree(connected);}
                    message="Chancellor appointed. Operator privileges and house participation remain separate.";
                }
                case CLEAR_CHANCELLOR->{state.office.clear();for(var connected:server.getPlayerManager().getPlayerList()){sendCapability(connected);server.getCommandManager().sendCommandTree(connected);}message="Chancellor assignment cleared.";}
                case RETRY_PAPERS->{
                    if(ChronicleRuntime.service(server).state().sessionNumber()<1)throw new IllegalStateException("Papers issue automatically when play first starts.");
                    state.contentFile.verifyUnchanged();HouseCast issued=state.issuer.ensure(state.content,state.roster.snapshot(),RANDOM::nextLong);state.publishBaseCast(issued);state.issuanceFailed=false;RuntimeHolds.release(server,HOLD);message="House papers issued.";
                }
                case ASSIGN->{
                    if(isChancellor(server,target))throw new IllegalStateException("The Chancellor cannot also represent a house.");
                    if(server.getPlayerManager().getPlayer(target)==null&&!state.rosterSnapshot().assignments().containsKey(target))throw new IllegalArgumentException("Choose an online or already assigned character.");
                    if(lateAssignment){
                        if(ProceedingsRuntime.successionStarted(server)&&request.candidate())throw new IllegalStateException("The crown candidate slate is locked. Add this character without candidacy.");
                        state.enrollment.verifyUnchanged();
                        HouseRoster base=new HouseRosterStore(state.root.resolve("roster.json")).load();
                        HouseCast baseCast=new HouseCastStore(state.root.resolve("cast.json")).load();
                        state.enrollment.verifyBaseline(base,baseCast);
                        HouseRoster before=state.rosterSnapshot();var assignments=new HashMap<>(before.assignments());
                        assignments.put(target,new HouseAssignment(target,request.houseId(),true,request.candidate()));
                        HouseRoster next=new HouseRoster(Math.addExact(before.revision(),1),assignments);
                        long drawSeed=RANDOM.nextLong();HouseCast papers=HouseCastGenerator.append(state.content,before,state.cast,next,target,drawSeed);
                        state.enrollment.append(next,papers,drawSeed);state.cast=state.enrollment.snapshot().cast();
                        message="House assigned and new papers issued. Existing papers and prior ballots are unchanged.";
                    }else {state.roster.assign(request.revision(),new HouseAssignment(target,request.houseId(),true,request.candidate()));message="House assigned. Public card and starting supplies are unchanged.";}
                    state.pending.clear();
                }
                case UNASSIGN->{state.roster.unassign(request.revision(),target);state.pending.clear();message="House assignment removed.";}
                case PREVIEW->{
                    if(state.rosterSnapshot().assignments().isEmpty())throw new IllegalStateException("Assign at least one house before drawing papers.");
                    var draft=HouseCastGenerator.generate(state.content,state.rosterSnapshot(),RANDOM.nextLong());
                    state.pending.put(player.getUuid(),new Pending(UUID.randomUUID(),draft,System.nanoTime()));message="Character draw ready to review. No papers have been committed.";
                }
                case COMMIT->{
                    Pending pending=state.pending.get(player.getUuid());
                    if(pending==null||!pending.token().equals(request.preview())||System.nanoTime()-pending.createdAt()>300_000_000_000L||pending.cast().rosterRevision()!=state.rosterSnapshot().revision())throw new IllegalStateException("Review a current character draw before committing.");
                    validateCast(state.rosterSnapshot(),pending.cast());state.castFile.save(pending.cast());state.publishBaseCast(pending.cast());state.pending.clear();message="House papers committed. Reconnects and restarts keep this same cast.";
                }
                case RELOAD->throw new IllegalStateException("Reload definitions by restarting this development server; committed papers are retained.");
            }
            sendAdmin(player,state,request.request(),target,false,message);
        }catch(IOException failure){
            State state=WORLDS.get(player.getEntityWorld().getServer());
            if(request.operation()==Operation.ASSIGN&&state!=null&&state.cast!=null)RuntimeHolds.hold(player.getEntityWorld().getServer(),HOLD,"Late House assignment needs recovery. Restart the server to load its exact saved enrollment; do not reset papers.");
            notice(player,request.request(),failure);
        }catch(RuntimeException failure){notice(player,request.request(),failure);}
    }
    private static void sendAdmin(ServerPlayerEntity player,State state,UUID request,UUID target,boolean opening,String message)throws IOException{
        var server=player.getEntityWorld().getServer();Map<UUID,String> options=new TreeMap<>();
        for(var online:server.getPlayerManager().getPlayerList().stream().limit(128).toList())options.put(online.getUuid(),online.getName().getString());
        for(var owner:state.rosterSnapshot().assignments().keySet())options.putIfAbsent(owner,Profiles.publicCard(server,owner,"Assigned character").name());
        if(!options.containsKey(target))target=player.getUuid();
        List<CouncilPayloads.Player> players=new ArrayList<>();for(var option:options.entrySet()){
            var assignment=state.rosterSnapshot().assignments().get(option.getKey());players.add(new CouncilPayloads.Player(option.getKey(),option.getValue(),assignment==null?"":assignment.houseId(),assignment!=null&&assignment.candidate()));
        }
        var pending=state.pending.get(player.getUuid());if(pending!=null&&System.nanoTime()-pending.createdAt()>300_000_000_000L){state.pending.remove(player.getUuid());pending=null;}
        HouseCast.HousePapers preview=pending==null?null:pending.cast().papers().get(target);
        String reason=editReason(player,state);
        ServerPlayNetworking.send(player,new AdminView(request,opening,state.rosterSnapshot().revision(),target,players,publicHouses(server,state),reason.isBlank(),reason,state.cast!=null,pending==null?CouncilPayloads.NONE:pending.token(),preview==null?List.of():preview.secrets(),preview==null?List.of():preview.goals(),message,state.office.snapshot().chancellor()==null?CouncilPayloads.NONE:state.office.snapshot().chancellor(),state.office.snapshot().revision(),officeReason(player).isBlank(),officeReason(player),state.cast!=null&&lateAssignmentReason(player,state,target).isBlank(),state.cast==null?reason:lateAssignmentReason(player,state,target),ProceedingsRuntime.successionStarted(server)));
    }
    private static void notice(ServerPlayerEntity player,UUID request,Throwable failure){if(ServerPlayNetworking.canSend(player,Notice.ID))ServerPlayNetworking.send(player,new Notice(request,shortMessage(failure)));}
    private static String shortMessage(Throwable failure){String value=failure instanceof IOException?"Data could not be read or saved. Existing authority was preserved; inspect the server configuration and logs.":Objects.toString(failure.getMessage(),"The operation is unavailable.");return value.substring(0,Math.min(512,value.length()));}
    private static final class State {
        CouncilContent content;final AtomicDataFile contentFile;final HouseRosterService roster;final HouseCastStore castFile;HouseCast cast;final CouncilOfficeService office;final HouseCastIssuer issuer;boolean issuanceFailed;
        final Map<UUID,Pending> pending=new HashMap<>();final Map<UUID,Limit> limits=new HashMap<>();
        final java.nio.file.Path root;HouseEnrollmentStore enrollment;
        State(java.nio.file.Path root,CouncilContent c,AtomicDataFile f,HouseRosterService r,HouseCastStore s,HouseCast cast,CouncilOfficeService office,HouseCastIssuer issuer)throws IOException{this.root=root;content=c;contentFile=f;roster=r;castFile=s;this.office=office;this.issuer=issuer;publishBaseCast(cast);if(office.snapshot().chancellor()!=null&&rosterSnapshot().assignments().containsKey(office.snapshot().chancellor()))throw new IOException("Chancellor also has an enrolled House; records preserved");}
        HouseRoster rosterSnapshot(){return enrollment==null?roster.snapshot():enrollment.snapshot().roster();}
        void publishBaseCast(HouseCast base)throws IOException{
            if(base==null){enrollment=null;cast=null;return;}
            HouseEnrollmentStore next=new HouseEnrollmentStore(root,roster.snapshot(),base);
            if(next.snapshot().roster().assignments().values().stream().anyMatch(a->!content.houses().containsKey(a.houseId())))throw new IOException("An enrolled House definition is missing; records preserved");
            enrollment=next;cast=next.snapshot().cast();
        }
    }
    private record Pending(UUID token,HouseCast cast,long createdAt) { }
    private record Limit(long window,int count) { }
}
