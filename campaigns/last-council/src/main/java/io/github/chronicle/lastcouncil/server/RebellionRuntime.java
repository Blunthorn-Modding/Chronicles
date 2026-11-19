package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.persistence.AtomicDataFile;
import io.github.chronicle.core.server.*;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.lastcouncil.council.*;
import io.github.chronicle.lastcouncil.house.*;
import io.github.chronicle.lastcouncil.network.ProceedingsPayloads;
import io.github.chronicle.lastcouncil.network.RebellionPayloads;
import io.github.chronicle.lastcouncil.network.RebellionPayloads.*;
import io.github.chronicle.lastcouncil.rebellion.RebellionService;
import java.io.IOException;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import static net.minecraft.server.command.CommandManager.literal;

/** World membership is the single political authority; Core supplies generic spatial enforcement. */
public final class RebellionRuntime {
    public static final String HOLD="last_council:rebellions", POLICY="last_council:palace_access";
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final Map<MinecraftServer,State> WORLDS=new IdentityHashMap<>();
    private static final class State {
        final RebellionService service;final AtomicDataFile marker;final Map<UUID,Session> sessions=new HashMap<>();final Map<UUID,int[]> rates=new HashMap<>();boolean failed;
        State(RebellionService service,AtomicDataFile marker){this.service=service;this.marker=marker;}
    }
    private static final class Session {
        final ServerPlayerEntity player;final long token=RANDOM.nextLong(1,Long.MAX_VALUE);UUID selected=RebellionPayloads.NONE;View sent;
        Session(ServerPlayerEntity player){this.player=player;}
    }
    private RebellionRuntime(){}
    public static void register(){
        RegionAccess.registerPolicy(POLICY,new RegionAccess.Policy(){
            public boolean active(MinecraftServer s){var r=WORLDS.get(s);return CouncilRuntime.active()&&r!=null&&!r.failed&&!r.service.needsReload();}
            public boolean allows(ServerPlayerEntity p,AuthoringSite.Region region){
                var s=p.getEntityWorld().getServer();var a=CouncilRuntime.rawRoster(s).assignments().get(p.getUuid());if(a==null)return true;
                var r=runtime(s);if(r.service.membership(a.houseId())==null)return true;
                var visit=r.service.snapshot().visits().get(a.houseId());return visit!=null&&visit.status()!=RebellionService.VisitStatus.ENDED;
            }
            public String editRefusal(MinecraftServer s){var r=WORLDS.get(s);return r==null||r.failed?"Recover rebellion records before changing access.":ProceedingsRuntime.ballotOpen(s)?"Close the ballot before changing access.":!r.service.snapshot().memberships().isEmpty()?"Region access cannot change while rebel Houses remain.":"";}
            public void entered(ServerPlayerEntity p,AuthoringSite.Region region){visitTransition(p,true,region.id());}
            public void left(ServerPlayerEntity p,AuthoringSite.Region region){visitTransition(p,false,region.id());}
        });
        SiteSubtypes.register(new SiteSubtypes.Definition(POLICY,"Region access","Control entry to this Region using the scenario's access rule.",Set.of(SiteSubtypes.Kind.REGION),new SiteSubtypes.Handler(){
            public boolean assigned(MinecraftServer s,AuthoringSite site){return RegionAccess.binding(s,site.id()).map(b->b.policyId().equals(POLICY)).orElse(false);}
            public String refusal(MinecraftServer s,AuthoringSite site,boolean enabling){if(!CouncilRuntime.active())return "This access rule requires The Last Council mechanics.";return RegionAccess.editRefusal(s,POLICY);}
            public SiteSubtypes.Change change(net.minecraft.server.command.ServerCommandSource source,AuthoringSite site,boolean enable)throws Exception{if(enable)RegionAccess.enable(source,site.id(),POLICY);else RegionAccess.disable(source,site.id());return SiteSubtypes.Change.APPLIED;}
        }));
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->reconcileOutsideVisit(handler.player));
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((old,p,alive)->reconcileOutsideVisit(p));
        ServerDisconnects.register((handler,s)->{var r=WORLDS.get(s);if(r!=null){r.sessions.remove(handler.player.getUuid());r.rates.remove(handler.player.getUuid());}});
        ServerTickEvents.END_SERVER_TICK.register(RebellionRuntime::tick);
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(q,c)->c.server().execute(()->receive(c.player(),q)));
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(literal("chronicle").then(literal("council").then(literal("rebellions").executes(c->{open(c.getSource().getPlayerOrThrow(),UUID.randomUUID());return 1;})))));
    }
    public static void reloadAuthority(MinecraftServer s)throws IOException{
        try{
            var previous=WORLDS.get(s);if(previous!=null)previous.marker.verifyUnchanged();
            var root=s.getSavePath(WorldSavePath.ROOT).resolve("chronicle/last-council");var marker=new AtomicDataFile(root.resolve("rebellions-initialized"),32);byte[] mark=marker.load();
            if(mark!=null&&(!Arrays.equals(mark,new byte[]{'1','\n'})||!Files.isRegularFile(root.resolve("rebellions.json"),LinkOption.NOFOLLOW_LINKS)))throw new IOException("Initialized rebellion journal is missing; restore it.");
            var next=new State(new RebellionService(root.resolve("rebellions.json")),marker);if(mark==null)marker.save(new byte[]{'1','\n'});
            WORLDS.put(s,next);RuntimeHolds.release(s,HOLD);
        }catch(IOException|RuntimeException failure){hold(s);throw failure;}
    }
    private static State runtime(MinecraftServer s){var r=WORLDS.get(s);if(r==null||r.failed||r.service.needsReload())throw new IllegalStateException("Rebellion records need Council recovery.");return r;}
    private static void hold(MinecraftServer s){var r=WORLDS.get(s);if(r!=null)r.failed=true;RuntimeHolds.hold(s,HOLD,"Rebellion records need exact Council recovery; preserve the journals.");}
    public static ProceedingsService.MotionEffects effects(MinecraftServer s){var r=runtime(s);var delegate=new ReinstatementMotionEffects(r.service);return new ProceedingsService.MotionEffects(){
        public void verifyUnchanged()throws IOException{verify(r);}
        public void validate(ProceedingsState.Motion m)throws IOException{verify(r);delegate.validate(m);}
        public void apply(ProceedingsState.Motion m,io.github.chronicle.core.ballots.Ballot b)throws IOException{verify(r);delegate.apply(m,b);}
        public void validateHistory(ProceedingsState state,io.github.chronicle.core.ballots.BallotState ballots)throws IOException{verify(r);delegate.validateHistory(state,ballots);}
    };}
    private static void verify(State r)throws IOException{r.marker.verifyUnchanged();r.service.verify();}
    public static boolean seated(MinecraftServer s,HouseAssignment a){var r=WORLDS.get(s);return a.councilMember()&&r!=null&&!r.failed&&!r.service.needsReload()&&r.service.membership(a.houseId())==null;}
    public static HouseRoster project(MinecraftServer s,HouseRoster raw){
        Map<UUID,HouseAssignment> assignments=new HashMap<>();for(var a:raw.assignments().values()){boolean seated=seated(s,a);assignments.put(a.owner(),new HouseAssignment(a.owner(),a.houseId(),seated,seated&&a.candidate()));}return new HouseRoster(raw.revision(),assignments);
    }
    public static boolean rebel(MinecraftServer s,String house){return runtime(s).service.membership(house)!=null;}
    public static List<ProceedingsPayloads.ReinstatementChoice> choices(MinecraftServer s){
        var r=runtime(s);if(!ProceedingsRuntime.effectsReady(s))return List.of();
        try{return r.service.snapshot().memberships().keySet().stream().sorted().map(id->{try{return new ProceedingsPayloads.ReinstatementChoice(id,title(s,id),r.service.targets(List.of(id)).getFirst());}catch(IOException failure){throw new java.io.UncheckedIOException(failure);}}).toList();}
        catch(java.io.UncheckedIOException failure){hold(s);throw new IllegalStateException("Recover rebellion records.",failure);}
    }
    public static void reconcile(MinecraftServer s)throws IOException{
        var r=runtime(s);verify(r);for(var a:CouncilRuntime.rawRoster(s).assignments().values())if(r.service.membership(a.houseId())!=null)ProceedingsRuntime.reconcileRebel(s,a.houseId(),a.owner());
        for(var player:s.getPlayerManager().getPlayerList())reconcileOutsideVisit(player);if(r.failed)throw new IOException("Visit records need recovery.");
    }
    private static String title(MinecraftServer s,String house){var h=CouncilRuntime.content(s).houses().get(house);return h==null?house:h.title();}
    private static boolean playing(MinecraftServer s){return ChronicleRuntime.service(s).state().phase()==SessionPhase.ACTIVE&&!SessionBuilding.enabled(s)&&RuntimeHolds.reason(s)==null;}
    private static HouseAssignment actor(ServerPlayerEntity p){var s=p.getEntityWorld().getServer();if(CouncilRuntime.isChancellor(s,p.getUuid())||!PlayRuntime.eligiblePlayers(s).contains(p))throw new IllegalStateException("Active House participation is required.");var a=CouncilRuntime.rawRoster(s).assignments().get(p.getUuid());if(a==null)throw new IllegalStateException("No House is assigned.");return a;}
    private static List<io.github.chronicle.core.access.RegionAccessStore.Binding> preflight(MinecraftServer s){var bindings=RegionAccess.bindings(s,POLICY);if(bindings.isEmpty())throw new IllegalStateException("Set Region access and an outside Point on the palace Region first.");for(var b:bindings)RegionAccess.preflight(s,b.regionId());return bindings;}
    private static String membershipRefusal(ServerPlayerEntity p){try{
        var s=p.getEntityWorld().getServer();if(!playing(s))return "Rebellion requires active play outside Editing and Building Mode.";var a=actor(p);var r=runtime(s);
        if(!a.councilMember()||r.service.membership(a.houseId())!=null)return "Your House already has no Council seat.";
        ProceedingsRuntime.preflightRebellion(s,a.houseId(),p.getUuid());preflight(s);return "";
    }catch(RuntimeException failure){return Objects.toString(failure.getMessage(),"Rebellion is unavailable.");}}
    public static void open(ServerPlayerEntity p,UUID request){try{var s=p.getEntityWorld().getServer();var r=runtime(s);var v=new Session(p);r.sessions.put(p.getUuid(),v);send(s,r,v,request,true,"");}catch(RuntimeException|IOException failure){p.sendMessage(Text.literal("Rebellion records unavailable. Ask an operator to recover the Council."),false);}}
    private static void receive(ServerPlayerEntity p,Request q){
        var s=p.getEntityWorld().getServer();if(!CouncilRuntime.active()||s.getPlayerManager().getPlayer(p.getUuid())!=p)return;var r=WORLDS.get(s);if(r==null)return;
        int second=s.getTicks()/20;int[] rate=r.rates.computeIfAbsent(p.getUuid(),k->new int[]{second,0});if(rate[0]!=second){rate[0]=second;rate[1]=0;}if(++rate[1]>12)return;
        if(q.action()==Action.OPEN){open(p,q.request());return;}var v=r.sessions.get(p.getUuid());if(v==null||v.player!=p||v.token!=q.token())return;
        if(q.action()==Action.CLOSE){r.sessions.remove(p.getUuid());return;}
        boolean committed=false;
        try{
            r=runtime(s);verify(r);if(q.action()==Action.STATE){v.selected=q.faction();send(s,r,v,q.request(),false,"");return;}
            if(!playing(s)||!ProceedingsRuntime.effectsReady(s))throw new IllegalStateException("Resume active play after recovering pending operations first.");
            String message;
            if(q.action()==Action.ALLOW_VISIT){
                if(!CouncilRuntime.isChancellor(s,p.getUuid()))throw new IllegalStateException("Only the Chancellor can allow a palace visit.");preflight(s);r.service.grantVisit(q.request(),q.revision(),q.house());message="One palace visit allowed for "+title(s,q.house())+".";
            }else{
                String refusal=membershipRefusal(p);if(!refusal.isBlank())throw new IllegalStateException(refusal);var a=actor(p);var bindings=preflight(s);var h=new RebellionService.House(a.houseId(),title(s,a.houseId()));
                if(q.action()==Action.DECLARE){v.selected=r.service.declare(q.request(),q.revision(),h,q.statement()).id();message=h.publicName()+" declared rebellion: "+q.statement();}
                else if(q.action()==Action.JOIN){v.selected=r.service.join(q.request(),q.revision(),h,q.faction()).id();message=h.publicName()+" joined the rebellion founded by "+r.service.snapshot().factions().get(v.selected).founder().publicName()+".";}
                else throw new IllegalArgumentException("Choose a rebellion action.");
                committed=true;reconcile(s);for(var b:bindings)if(RegionAccess.contains(p,b.regionId()))RegionAccess.eject(p,b.regionId());
                s.getPlayerManager().broadcast(Text.literal(message+" ").append(Text.literal("[View rebellions]").styled(style->style.withColor(net.minecraft.util.Formatting.GOLD).withClickEvent(new net.minecraft.text.ClickEvent.RunCommand("/chronicle council rebellions")))),false);
            }
            send(s,r,v,q.request(),false,message,true);refresh(s,r);
        }catch(IOException failure){hold(s);safeReply(s,v,q.request(),"Saved rebellion operation needs recovery; preserve all journals.");}
        catch(RuntimeException failure){if(committed||r.service.needsReload())hold(s);safeReply(s,v,q.request(),Objects.toString(failure.getMessage(),"Action refused."));}
    }
    private static void safeReply(MinecraftServer s,Session v,UUID request,String message){var r=WORLDS.get(s);if(r==null)return;try{send(s,r,v,request,false,message);}catch(Exception failed){hold(s);}}
    private static void send(MinecraftServer s,State r,Session v,UUID request,boolean opening,String message)throws IOException{
        send(s,r,v,request,opening,message,false);
    }
    private static void send(MinecraftServer s,State r,Session v,UUID request,boolean opening,String message,boolean saved)throws IOException{
        if(!ServerPlayNetworking.canSend(v.player,View.ID))return;boolean ready=!r.failed&&!r.service.needsReload()&&ProceedingsRuntime.effectsReady(s);
        var snapshot=r.service.snapshot();var a=CouncilRuntime.rawRoster(s).assignments().get(v.player.getUuid());String own=a==null?"":a.houseId();boolean gm=CouncilRuntime.isChancellor(s,v.player.getUuid());
        var factions=snapshot.factions().values().stream().sorted(Comparator.comparing(f->f.id().toString())).toList();var selected=snapshot.factions().get(v.selected);if(selected==null&&!factions.isEmpty()){selected=factions.getFirst();v.selected=selected.id();}
        Detail detail=null;List<Row> rows=List.of();List<HouseVisit> visits=List.of();String refusal=ready?(gm?"":membershipRefusal(v.player)):"Recover Council and rebellion records first.";
        if(ready){rows=factions.stream().map(f->new Row(f.id(),f.founder().publicName()+" rebellion")).toList();
            if(selected!=null){var f=selected;int start=Math.max(0,f.history().size()-32);detail=new Detail(f.id(),f.founder().publicName()+" rebellion",f.founder().publicName(),f.statement(),f.members().stream().map(RebellionService.House::publicName).toList(),f.history().subList(start,f.history().size()).stream().map(e->e.house().publicName()+switch(e.action()){case DECLARED->" declared rebellion.";case JOINED->" joined.";case REINSTATED->" was reinstated by the Council.";}).toList());}
            if(gm)visits=snapshot.memberships().keySet().stream().sorted().map(id->{var visit=snapshot.visits().get(id);return new HouseVisit(id,title(s,id),visit==null?"NONE":visit.status().name());}).toList();
        }
        View packet=new View(request,opening,v.token,snapshot.revision(),rows,detail,own,ready&&!gm&&refusal.isBlank(),ready&&!gm&&refusal.isBlank()&&detail!=null,gm,visits,shorten(message,1024),shorten(refusal,1024),saved);
        if(!RebellionPayloads.fits(packet))throw new IOException("Rebellion projection exceeds its packet budget.");
        if(!opening&&request.equals(RebellionPayloads.NONE)&&packet.equals(v.sent))return;ServerPlayNetworking.send(v.player,packet);
        v.sent=new View(RebellionPayloads.NONE,false,packet.token(),packet.revision(),packet.rows(),packet.detail(),packet.ownHouse(),packet.canDeclare(),packet.canJoin(),packet.chancellor(),packet.visits(),"",packet.refusal());
    }
    private static String shorten(String s,int max){return s.substring(0,Math.min(s.length(),max));}
    private static void refresh(MinecraftServer s,State r){for(var v:List.copyOf(r.sessions.values()))try{send(s,r,v,RebellionPayloads.NONE,false,"");}catch(IOException|RuntimeException failed){hold(s);}}
    private static void tick(MinecraftServer s){var r=WORLDS.get(s);if(r==null||s.getTicks()%20!=0)return;try{if(!r.failed){verify(r);for(var player:s.getPlayerManager().getPlayerList())reconcileOutsideVisit(player);}refresh(s,r);}catch(IOException|RuntimeException failed){hold(s);}}
    private static void reconcileOutsideVisit(ServerPlayerEntity p){
        var s=p.getEntityWorld().getServer();var r=WORLDS.get(s);if(r==null||r.failed)return;var a=CouncilRuntime.rawRoster(s).assignments().get(p.getUuid());if(a==null)return;
        var visit=r.service.snapshot().visits().get(a.houseId());if(visit==null||visit.status()!=RebellionService.VisitStatus.INSIDE)return;
        try{verify(r);if(RegionAccess.bindings(s,POLICY).stream().noneMatch(b->RegionAccess.contains(p,b.regionId())))r.service.markExited(UUID.randomUUID(),r.service.snapshot().revision(),a.houseId(),visit.grant());}
        catch(IOException|RuntimeException failure){hold(s);}
    }
    private static void visitTransition(ServerPlayerEntity p,boolean entered,String region){
        var s=p.getEntityWorld().getServer();var r=WORLDS.get(s);if(r==null||r.failed)return;var a=CouncilRuntime.rawRoster(s).assignments().get(p.getUuid());if(a==null)return;
        var visit=r.service.snapshot().visits().get(a.houseId());if(visit==null||visit.status()==RebellionService.VisitStatus.ENDED)return;
        try{verify(r);if(entered&&visit.status()==RebellionService.VisitStatus.WAITING)r.service.markEntered(UUID.randomUUID(),r.service.snapshot().revision(),a.houseId(),visit.grant());
            else if(!entered&&visit.status()==RebellionService.VisitStatus.INSIDE&&RegionAccess.bindings(s,POLICY).stream().noneMatch(b->!b.regionId().equals(region)&&RegionAccess.contains(p,b.regionId())))r.service.markExited(UUID.randomUUID(),r.service.snapshot().revision(),a.houseId(),visit.grant());
        }catch(IOException|RuntimeException failed){hold(s);throw new IllegalStateException("Visit records need Council recovery.",failed);}
    }
}
