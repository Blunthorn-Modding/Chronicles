package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.core.network.ProfilePayloads;
import io.github.chronicle.core.persistence.AtomicDataFile;
import io.github.chronicle.core.server.*;
import io.github.chronicle.lastcouncil.LastCouncil;
import io.github.chronicle.lastcouncil.content.*;
import io.github.chronicle.lastcouncil.house.*;
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
        ServerLifecycleEvents.SERVER_STARTED.register(server->{if(active())start(server);});
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            if(ServerPlayNetworking.canSend(handler.player,Capability.ID))ServerPlayNetworking.send(handler.player,new Capability(active()));
        });
        ServerDisconnects.register((handler,server)->{var state=WORLDS.get(server);if(state!=null){state.pending.remove(handler.player.getUuid());state.limits.remove(handler.player.getUuid());}});
        ServerPlayNetworking.registerGlobalReceiver(PersonalRequest.ID,(request,context)->context.server().execute(()->personal(context.player(),request)));
        ServerPlayNetworking.registerGlobalReceiver(AdminRequest.ID,(request,context)->context.server().execute(()->admin(context.player(),request)));
        PlayerMenuExtensions.register("lc:houses","House & papers…",(viewer,target)->active()&&WORLDS.containsKey(viewer.getEntityWorld().getServer()),CouncilRuntime::openAdmin);
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(literal("chronicle").then(literal("council")
            .executes(c->{var player=c.getSource().getPlayer();if(player==null)return 0;return openPersonal(player);})
            .then(literal("characters").requires(s->s.hasPermissionLevel(2)).executes(c->{var player=c.getSource().getPlayer();if(player==null)return 0;openAdmin(player,player.getUuid());return 1;})))));
    }
    public static HouseRoster roster(MinecraftServer server) {
        if(!server.isOnThread()||!WORLDS.containsKey(server))throw new IllegalStateException("House assignments unavailable.");
        return WORLDS.get(server).roster.snapshot();
    }
    public static Map<String,HouseDefinition> houses(MinecraftServer server) {
        if(!server.isOnThread()||!WORLDS.containsKey(server))throw new IllegalStateException("House definitions unavailable.");
        return WORLDS.get(server).content.houses();
    }
    public static boolean active(){return PackagedScenarios.mechanicsFor(LastCouncil.SCENARIO,LastCouncil.MECHANICS);}
    private static void start(MinecraftServer server){
        try{
            var file=new AtomicDataFile(ScenarioAuthoringProfiles.chronicleConfig().resolve("last-council/houses.json"),CouncilContentJson.MAX_BYTES);
            byte[] bytes=file.load();
            if(bytes==null){
                if(ScenarioAuthoringProfiles.active().isPresent())bytes="{\"schema\":1,\"note\":\"Empty custom scenario\",\"houses\":{},\"secrets\":[],\"goals\":[],\"secretsPerHouse\":0,\"goalsPerHouse\":0,\"knowledgeChancePercent\":0}".getBytes(StandardCharsets.UTF_8);
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
            WORLDS.put(server,new State(content,file,roster,castFile,cast));RuntimeHolds.release(server,HOLD);
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
            var assignment=state.roster.snapshot().assignments().values().stream().filter(a->a.houseId().equals(house.id())).findFirst().orElse(null);
            String representative="";if(assignment!=null){var online=server.getPlayerManager().getPlayer(assignment.owner());representative=Profiles.publicCard(server,assignment.owner(),online==null?"Assigned character":online.getName().getString()).name();}
            result.add(new PublicHouse(house.id(),house.title(),house.reputation(),house.description(),house.interests(),representative,assignment!=null&&assignment.councilMember(),assignment!=null&&assignment.candidate(),house.defaultCandidate()));
        }
        return result;
    }
    private static void personal(ServerPlayerEntity player,PersonalRequest request){
        try{
            State state=require(player);if(!allow(state,player))return;
            if(request.offset()<0||request.offset()>124||request.offset()%8!=0)throw new IllegalArgumentException("Choose a valid papers page.");
            var assignment=state.roster.snapshot().assignments().get(player.getUuid());String houseId=assignment==null?"":assignment.houseId();
            List<String> all=new ArrayList<>();String message="";
            if(assignment==null)message="The Chancellor has not assigned your house yet.";
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
    private static void operator(ServerPlayerEntity player){if(!player.getCommandSource().hasPermissionLevel(2))throw new IllegalStateException("Only the Chancellor can manage house assignments.");}
    private static String editReason(ServerPlayerEntity player,State state){
        if(ProceedingsRuntime.ballotOpen(player.getEntityWorld().getServer()))return "Close or void the current ballot before changing the cast.";
        if(state.cast!=null)return "The cast is committed. Character-to-house assignments are fixed for this experience.";
        if(!AuthoringRuntime.editable(player.getEntityWorld().getServer())||!AuthoringRuntime.isAuthor(player))return "Pause play and enable editing before changing the cast.";
        if(RuntimeHolds.heldByOther(player.getEntityWorld().getServer(),HOLD))return "Resolve the active Chronicle operation hold first.";
        return "";
    }
    private static void admin(ServerPlayerEntity player,AdminRequest request){
        try{
            State state=require(player);operator(player);if(!allow(state,player))return;
            UUID target=request.target();var server=player.getEntityWorld().getServer();
            if(request.operation()!=Operation.OPEN){String reason=editReason(player,state);if(!reason.isBlank())throw new IllegalStateException(reason);if(request.revision()!=state.roster.snapshot().revision())throw new IllegalStateException("The roster changed. Review it again.");state.contentFile.verifyUnchanged();}
            String message="";
            switch(request.operation()){
                case OPEN->{ }
                case ASSIGN->{
                    if(server.getPlayerManager().getPlayer(target)==null&&!state.roster.snapshot().assignments().containsKey(target))throw new IllegalArgumentException("Choose an online or already assigned character.");
                    state.roster.assign(request.revision(),new HouseAssignment(target,request.houseId(),true,request.candidate()));state.pending.clear();message="House assigned. Public card and starting supplies are unchanged.";
                }
                case UNASSIGN->{state.roster.unassign(request.revision(),target);state.pending.clear();message="House assignment removed.";}
                case PREVIEW->{
                    if(state.roster.snapshot().assignments().isEmpty())throw new IllegalStateException("Assign at least one house before drawing papers.");
                    var draft=HouseCastGenerator.generate(state.content,state.roster.snapshot(),RANDOM.nextLong());
                    state.pending.put(player.getUuid(),new Pending(UUID.randomUUID(),draft,System.nanoTime()));message="Character draw ready to review. No papers have been committed.";
                }
                case COMMIT->{
                    Pending pending=state.pending.get(player.getUuid());
                    if(pending==null||!pending.token().equals(request.preview())||System.nanoTime()-pending.createdAt()>300_000_000_000L||pending.cast().rosterRevision()!=state.roster.snapshot().revision())throw new IllegalStateException("Review a current character draw before committing.");
                    validateCast(state.roster.snapshot(),pending.cast());state.castFile.save(pending.cast());state.cast=pending.cast();state.pending.clear();message="House papers committed. Reconnects and restarts keep this same cast.";
                }
                case RELOAD->throw new IllegalStateException("Reload definitions by restarting this development server; committed papers are retained.");
            }
            sendAdmin(player,state,request.request(),target,false,message);
        }catch(IOException|RuntimeException failure){notice(player,request.request(),failure);}
    }
    private static void sendAdmin(ServerPlayerEntity player,State state,UUID request,UUID target,boolean opening,String message)throws IOException{
        var server=player.getEntityWorld().getServer();Map<UUID,String> options=new TreeMap<>();
        for(var online:server.getPlayerManager().getPlayerList().stream().limit(128).toList())options.put(online.getUuid(),online.getName().getString());
        for(var owner:state.roster.snapshot().assignments().keySet())options.putIfAbsent(owner,Profiles.publicCard(server,owner,"Assigned character").name());
        if(!options.containsKey(target))target=player.getUuid();
        List<CouncilPayloads.Player> players=new ArrayList<>();for(var option:options.entrySet()){
            var assignment=state.roster.snapshot().assignments().get(option.getKey());players.add(new CouncilPayloads.Player(option.getKey(),option.getValue(),assignment==null?"":assignment.houseId(),assignment!=null&&assignment.candidate()));
        }
        var pending=state.pending.get(player.getUuid());if(pending!=null&&System.nanoTime()-pending.createdAt()>300_000_000_000L){state.pending.remove(player.getUuid());pending=null;}
        HouseCast.HousePapers preview=pending==null?null:pending.cast().papers().get(target);
        String reason=editReason(player,state);
        ServerPlayNetworking.send(player,new AdminView(request,opening,state.roster.snapshot().revision(),target,players,publicHouses(server,state),reason.isBlank(),reason,state.cast!=null,pending==null?CouncilPayloads.NONE:pending.token(),preview==null?List.of():preview.secrets(),preview==null?List.of():preview.goals(),message));
    }
    private static void notice(ServerPlayerEntity player,UUID request,Throwable failure){if(ServerPlayNetworking.canSend(player,Notice.ID))ServerPlayNetworking.send(player,new Notice(request,shortMessage(failure)));}
    private static String shortMessage(Throwable failure){String value=failure instanceof IOException?"Data could not be read or saved. Existing authority was preserved; inspect the server configuration and logs.":Objects.toString(failure.getMessage(),"The operation is unavailable.");return value.substring(0,Math.min(512,value.length()));}
    private static final class State {
        final CouncilContent content;final AtomicDataFile contentFile;final HouseRosterService roster;final HouseCastStore castFile;HouseCast cast;
        final Map<UUID,Pending> pending=new HashMap<>();final Map<UUID,Limit> limits=new HashMap<>();
        State(CouncilContent c,AtomicDataFile f,HouseRosterService r,HouseCastStore s,HouseCast cast){content=c;contentFile=f;roster=r;castFile=s;this.cast=cast;}
    }
    private record Pending(UUID token,HouseCast cast,long createdAt) { }
    private record Limit(long window,int count) { }
}
