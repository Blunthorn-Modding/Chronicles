package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.persistence.AtomicDataFile;
import io.github.chronicle.core.play.EventSource;
import io.github.chronicle.core.server.*;
import io.github.chronicle.lastcouncil.conflicts.ConflictService;
import io.github.chronicle.lastcouncil.conflicts.ConflictService.House;
import io.github.chronicle.lastcouncil.network.ConflictPayloads;
import io.github.chronicle.lastcouncil.network.ConflictPayloads.*;
import io.github.chronicle.lastcouncil.network.TreatyPayloads;
import io.github.chronicle.lastcouncil.treaties.TreatyService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.*;
import static net.minecraft.server.command.CommandManager.literal;

/** Physical offers, server-authoritative actions, public projections and portable configuration. */
public final class ConflictRuntime {
    public static final String HOLD="last_council:conflicts",SOURCE="last_council:conflict_changed";
    private static final Map<MinecraftServer,Runtime> WORLDS=new IdentityHashMap<>();
    private static final SecureRandom RANDOM=new SecureRandom();
    private static Item token;
    private static final class Runtime {
        final ConflictService service;final ConflictSettings settings;final ConflictOperations operations;
        final Map<UUID,Session> sessions=new HashMap<>();boolean failed;
        Runtime(ConflictService s,ConflictSettings c,ConflictOperations o){service=s;settings=c;operations=o;}
    }
    private static final class Session {
        final ServerPlayerEntity player;final long token=RANDOM.nextLong(1,Long.MAX_VALUE);
        ServerPlayerEntity target;House ownHouse,targetHouse;long expires;UUID selected=ConflictPayloads.NONE;boolean settings;long tick=-1;int requests;View sent;long visibleRevision;
        Session(ServerPlayerEntity p){player=p;}
    }
    private ConflictRuntime(){}
    public static void register(){
        var id=Identifier.of("last_council","challenge_token");var key=RegistryKey.of(RegistryKeys.ITEM,id);
        token=Registry.register(Registries.ITEM,key,new Item(new Item.Settings().registryKey(key).maxCount(1)));
        ScenarioContentRuntime.registerExtension(HOLD,new ScenarioContentRuntime.ExtensionAdapter(){
            public void validate(String json)throws IOException{ConflictSettings.decode(json.getBytes(StandardCharsets.UTF_8));}
            public String capture(MinecraftServer server)throws IOException{if(!CouncilRuntime.active())return null;var r=runtime(server);r.settings.verify();return new String(ConflictSettings.encode(r.settings.threshold()),StandardCharsets.UTF_8);}
            public void initialize(Path dir,String initial,boolean legacy)throws IOException{ConflictSettings.initialize(dir,initial,legacy);}
            public void verify(Path dir)throws IOException{ConflictSettings.initialize(dir,null,true);}
        });
        if(CouncilRuntime.active()){
            GameSetupTools.register(new GameSetupTools.Tool(HOLD,"Conflicts","Configure the house count used for the Civil war label.",List.of(new GameSetupTools.Verb("open","Open conflict settings",GameSetupTools.Guard.READ_ONLY,false)),s->new GameSetupTools.Status(runtime(s.getServer()).settings.threshold(),List.of(),Map.of()),(s,v)->{open(s.getPlayerOrThrow(),true);return "";}));
            EventSources.registerProvider(SOURCE,"Conflict changed","A public house war declaration, side change, peace or ceasefire was recorded.",s->List.of(new EventSources.Choice("*","Any conflict","Public political records.")),Arrays.stream(ConflictService.Action.values()).map(a->new EventSources.Choice(activity(a),activityLabel(a),"Recorded public activity.")).toList());
            EventSources.registerAuthority("council-conflict:",(server,receipt)->{
                var r=WORLDS.get(server);if(r==null||r.failed||r.operations.pending()!=null||r.operations.uncertain())return false;
                try{r.service.verify();}catch(IOException e){r.failed=true;hold(server);return false;}
                String[] parts=receipt.authority().split(":");if(parts.length!=3)return false;
                UUID conflict=UUID.fromString(parts[1]);long rev=Long.parseLong(parts[2]);
                return r.service.publicViews().stream().map(ConflictService.PublicView::record).filter(c->c.id().equals(conflict)).flatMap(c->c.history().stream()).anyMatch(e->e.recordRevision()==rev&&activity(e.action()).equals(receipt.activity())&&e.actor().owner().equals(receipt.actor())&&receipt.type().equals(SOURCE)&&receipt.target().equals("*"));
            });
        }
        ServerLifecycleEvents.SERVER_STARTED.register(s->{if(CouncilRuntime.active())start(s,false);});
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{var r=WORLDS.get(s);if(r!=null){r.sessions.remove(h.player.getUuid());r.sessions.values().removeIf(v->v.target==h.player);}});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTicks()%10==0){var r=WORLDS.get(s);if(r!=null)for(var session:List.copyOf(r.sessions.values()))try{if(session.target!=null&&s.getTicks()>session.expires)session.target=null;send(s,r,session,ConflictPayloads.NONE,false,"");}catch(Exception e){r.sessions.remove(session.player.getUuid());}}});
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(q,c)->c.server().execute(()->receive(c.player(),q)));
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(hand!=Hand.MAIN_HAND||!player.getStackInHand(hand).isOf(token)||!(entity instanceof ServerPlayerEntity target)||!(player instanceof ServerPlayerEntity actor))return ActionResult.PASS;
            if(!CouncilRuntime.active())return ActionResult.PASS;
            try{offer(actor,target);}catch(RuntimeException e){actor.sendMessage(Text.literal(Objects.toString(e.getMessage(),"Challenge refused.")),false);}return ActionResult.SUCCESS_SERVER;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("chronicle").then(literal("council")
            .then(literal("conflicts").executes(c->{open(c.getSource().getPlayerOrThrow(),false);return 1;})
                .then(literal("settings").requires(s->s.hasPermissionLevel(2)).executes(c->{open(c.getSource().getPlayerOrThrow(),true);return 1;}))
                .then(literal("recover").requires(s->s.hasPermissionLevel(2)).executes(c->{start(c.getSource().getServer(),true);boolean ok=WORLDS.containsKey(c.getSource().getServer())&&!RuntimeHolds.heldByOther(c.getSource().getServer(),EventSources.HOLD);c.getSource().sendFeedback(()->Text.literal(ok?"Conflict records reloaded; pinned operations recovered.":"Conflict recovery remains held; preserve saved files."),false);return ok?1:0;})))
            .then(literal("challenge-token").executes(c->{var p1=c.getSource().getPlayerOrThrow();if(!eligible(p1)&&!p1.hasPermissionLevel(2))throw new IllegalStateException("A House representative or operator is required.");if(!p1.getInventory().insertStack(new ItemStack(token)))throw new IllegalStateException("Make one free inventory slot.");return 1;})))));
    }
    private static void start(MinecraftServer server,boolean reloadTreaties){
        try{if(reloadTreaties)TreatyRuntime.reloadAuthority(server);Path root=server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/last-council");
            AtomicDataFile marker=new AtomicDataFile(root.resolve("conflicts-initialized"),32);byte[] mark=marker.load();
            if(mark!=null){if(!Arrays.equals(mark,new byte[]{'1','\n'}))throw new IOException("Invalid conflict initialization marker");for(String name:List.of("conflicts.json","conflict-operations.json","treaties.json"))if(!Files.isRegularFile(root.resolve(name),LinkOption.NOFOLLOW_LINKS))throw new IOException("An initialized political journal is missing");}
            var service=new ConflictService(root.resolve("conflicts.json"));if(mark==null&&!Files.exists(root.resolve("conflicts.json")))service.checkpoint();
            var treaties=TreatyRuntime.authority(server);if(mark==null&&!Files.exists(root.resolve("treaties.json")))treaties.checkpoint();
            var operations=new ConflictOperations(root.resolve("conflict-operations.json"));Path dir=ScenarioAuthoringProfiles.chronicleConfig().resolve(ScenarioAuthoringProfiles.active().isPresent()?"profile-content":"");
            var settings=new ConflictSettings(dir.resolve("last-council-conflicts.json"),4);if(mark==null)marker.save(new byte[]{'1','\n'});
            Runtime r=new Runtime(service,settings,operations);var old=WORLDS.put(server,r);if(old!=null)revoke(old,"Conflict records reloaded. Reopen the register.");operations.recover(service,treaties);reconcileSources(server,r);RuntimeHolds.release(server,HOLD);
        }catch(Exception e){var old=WORLDS.remove(server);if(old!=null)revoke(old,"Conflict records need operator recovery.");hold(server);io.github.chronicle.core.ChronicleCore.LOGGER.error("Conflict recovery failed; records retained",e);}
    }
    private static void revoke(Runtime r,String message){for(var v:r.sessions.values())if(ServerPlayNetworking.canSend(v.player,View.ID))ServerPlayNetworking.send(v.player,new View(ConflictPayloads.NONE,false,v.token,v.visibleRevision,List.of(),null,null,v.settings,false,r.settings.threshold(),message,true));r.sessions.clear();}
    public static boolean publicationReady(MinecraftServer s){var r=WORLDS.get(s);return r!=null&&!r.failed&&r.operations.pending()==null&&!r.operations.uncertain();}
    private static String activityLabel(ConflictService.Action a){return switch(a){case DECLARED->"War declared";case JOINED->"House joined";case CHANGED->"House changed sides";case WITHDREW->"House withdrew";case AGREEMENT->"Status changed";};}
    private static String activity(ConflictService.Action a){return a==ConflictService.Action.AGREEMENT?"status_changed":a.name().toLowerCase(Locale.ROOT);}
    private static Runtime runtime(MinecraftServer server){Runtime r=WORLDS.get(server);if(r==null)throw new IllegalStateException("Conflict records need operator recovery.");return r;}
    private static void hold(MinecraftServer server){RuntimeHolds.hold(server,HOLD,"Political records need exact reload. Use /chronicle council conflicts recover; preserve all journals.");}
    private static boolean playing(MinecraftServer s){return ChronicleRuntime.service(s).state().phase()==SessionPhase.ACTIVE&&!SessionBuilding.enabled(s)&&RuntimeHolds.reason(s)==null;}
    private static boolean eligible(ServerPlayerEntity p){var s=p.getEntityWorld().getServer();return p.isAlive()&&!p.isSpectator()&&!AuthoringRuntime.isAuthor(p)&&!CouncilRuntime.isChancellor(s,p.getUuid())&&PlayRuntime.eligibleIds(s).contains(p.getUuid())&&CouncilRuntime.roster(s).assignments().containsKey(p.getUuid());}
    private static House house(ServerPlayerEntity p){if(!eligible(p))throw new IllegalStateException("A participating House representative is required.");var s=p.getEntityWorld().getServer();try{return new House(CouncilRuntime.roster(s).assignments().get(p.getUuid()).houseId(),p.getUuid(),Profiles.publicCard(s,p.getUuid(),p.getName().getString()).name());}catch(IOException e){throw new IllegalStateException("Character records are unavailable.",e);}}
    private static String title(MinecraftServer s,String id){var d=CouncilRuntime.houses(s).get(id);return d==null?id:d.title();}
    private static boolean editable(ServerPlayerEntity p){var s=p.getEntityWorld().getServer();return p.hasPermissionLevel(2)&&AuthoringRuntime.isAuthor(p)&&AuthoringRuntime.editable(s)&&RuntimeHolds.reason(s)==null;}
    private static void physical(ServerPlayerEntity p,ServerPlayerEntity target){var s=p.getEntityWorld().getServer();if(s.getPlayerManager().getPlayer(p.getUuid())!=p||s.getPlayerManager().getPlayer(target.getUuid())!=target||p==target||p.getEntityWorld()!=target.getEntityWorld()||!p.canInteractWithEntity(target,0)||!p.canSee(target)||!p.getMainHandStack().isOf(token)||p.currentScreenHandler!=p.playerScreenHandler)throw new IllegalStateException("Use your Challenge token on the nearby representative again.");if(!playing(s))throw new IllegalStateException("Challenges require active play.");house(p);house(target);}
    private static void offer(ServerPlayerEntity p,ServerPlayerEntity target){physical(p,target);var s=p.getEntityWorld().getServer();var r=runtime(s);var own=house(p);var other=house(target);if(own.houseId().equals(other.houseId()))throw new IllegalStateException("You cannot challenge your own House.");Session v=new Session(p);v.target=target;v.ownHouse=own;v.targetHouse=other;v.expires=s.getTicks()+1200;r.sessions.put(p.getUuid(),v);send(s,r,v,UUID.randomUUID(),true,"");}
    public static void open(ServerPlayerEntity p,boolean settings){var s=p.getEntityWorld().getServer();if(!CouncilRuntime.active())return;var r=runtime(s);if(settings&&!p.hasPermissionLevel(2))throw new IllegalStateException("Operator access required.");var v=new Session(p);v.settings=settings;r.sessions.put(p.getUuid(),v);send(s,r,v,UUID.randomUUID(),true,"");}
    private static void receive(ServerPlayerEntity p,Request q){var s=p.getEntityWorld().getServer();if(!CouncilRuntime.active()||s.getPlayerManager().getPlayer(p.getUuid())!=p)return;Runtime r=WORLDS.get(s);if(r==null)return;Session v=r.sessions.get(p.getUuid());if(v==null||v.player!=p||q.token()!=v.token)return;
        boolean started=false;try{if(q.action()==Action.CLOSE){r.sessions.remove(p.getUuid());return;}if(v.tick!=s.getTicks()){v.tick=s.getTicks();v.requests=0;}if(++v.requests>8)throw new IllegalStateException("Wait for the previous request.");
            if(q.action()==Action.STATE){v.selected=q.conflict();send(s,r,v,q.request(),false,"");return;}
            if(q.action()==Action.SAVE_SETTINGS){if(!v.settings||!editable(p)||q.revision()!=r.settings.threshold())throw new IllegalStateException("Pause play, enable Editing and reopen conflict settings.");r.settings.verify();r.settings.save(q.threshold());send(s,r,v,q.request(),false,"Settings saved.");return;}
            if(!playing(s)||!eligible(p))throw new IllegalStateException("Conflict actions require active House participation.");
            String receipt;
            if(q.action()==Action.DECLARE){if(v.target==null||s.getTicks()>v.expires)throw new IllegalStateException("Challenge expired. Use the token again.");physical(p,v.target);if(!v.ownHouse.equals(house(p))||!v.targetHouse.equals(house(v.target)))throw new IllegalStateException("The challenged representatives changed. Use the token again.");
                UUID id=q.request();String reason=q.reason().strip();var treaties=TreatyRuntime.authority(s);var breaches=treaties.allAuthority().stream().filter(t->ConflictOperations.applicableNonAggression(t,v.ownHouse,v.targetHouse)).map(t->new ConflictOperations.Breach(t.document().id(),t.document().contentRevision())).toList();
                var op=new ConflictOperations.Operation(ConflictOperations.Kind.DECLARATION,id,p.getUuid(),v.ownHouse,v.targetHouse,reason,breaches,null,Set.of(),0);ConflictOperations.validate(op,r.service,treaties);
                for(var breach:breaches)PoliticalSourcesRuntime.planTreaty(s,copy->copy.breach(breach.treaty(),breach.revision(),p.getUuid()));
                receipt=prepareSource(s,id,1,ConflictService.Action.DECLARED,p.getUuid());started=true;r.operations.begin(op,r.service,treaties);v.selected=id;v.target=null;announce(s,title(s,v.ownHouse.houseId())+" declared war on "+title(s,v.targetHouse.houseId())+": "+reason);notifyDefense(s,v.ownHouse.houseId(),v.targetHouse.houseId());
            }else{var c=record(s,q.conflict());var h=house(p);ConflictService.Action action=switch(q.action()){case JOIN->ConflictService.Action.JOINED;case CHANGE->ConflictService.Action.CHANGED;case WITHDRAW->ConflictService.Action.WITHDREW;default->throw new IllegalArgumentException("Choose a conflict action.");};
                ConflictService.Mutation mutation=c1->{switch(q.action()){case JOIN->c1.join(c.id(),q.revision(),h,q.side());case CHANGE->c1.change(c.id(),q.revision(),h,q.side());case WITHDRAW->c1.withdraw(c.id(),q.revision(),h);default->throw new AssertionError();}};r.service.preview(mutation);receipt=prepareSource(s,c.id(),c.revision()+1,action,p.getUuid());started=true;mutation.apply(r.service);v.selected=c.id();announce(s,title(s,h.houseId())+switch(q.action()){case JOIN->" joined "+title(s,q.side())+" in ";case CHANGE->" changed allegiance to "+title(s,q.side())+" in ";default->" withdrew from ";}+conflictTitle(s,c));}
            EventSources.complete(s,receipt,true);send(s,r,v,q.request(),false,"Public conflict record updated.");TreatyRuntime.refreshAuthority(s);
        }catch(IOException e){r.failed=true;hold(s);send(s,r,v,q.request(),false,"Political records need recovery; preserve the saved files.");}catch(RuntimeException e){if(started||r.failed||r.operations.pending()!=null||r.operations.uncertain()){r.failed=true;hold(s);}send(s,r,v,q.request(),false,Objects.toString(e.getMessage(),"Conflict action refused."));}
    }
    private static void reconcileSources(MinecraftServer s,Runtime r)throws IOException{
        r.service.verify();TreatyRuntime.authority(s).verify();if(r.operations.pending()!=null||r.operations.uncertain()||r.failed)return;
        for(var receipt:EventSources.pending(s).stream().filter(e->e.authority().startsWith("council-conflict:")).toList()){
            String[] parts=receipt.authority().split(":");if(parts.length!=3)throw new IOException("Invalid conflict source authority");UUID id=UUID.fromString(parts[1]);long revision=Long.parseLong(parts[2]);
            boolean committed=r.service.publicViews().stream().map(ConflictService.PublicView::record).filter(c->c.id().equals(id)).flatMap(c->c.history().stream()).anyMatch(e->e.recordRevision()==revision&&e.actor().owner().equals(receipt.actor())&&activity(e.action()).equals(receipt.activity())&&receipt.type().equals(SOURCE)&&receipt.target().equals("*"));
            if(committed)EventSources.complete(s,receipt.id(),true);else EventSources.cancelAuthority(s,receipt.authority());
        }
    }
    private static String prepareSource(MinecraftServer s,UUID id,long rev,ConflictService.Action a,UUID actor){String authority="council-conflict:"+id+":"+rev;EventSource source=new EventSource(SOURCE,"*",activity(a));return EventSources.prepare(s,EventSources.id(authority,SOURCE,"*",source.activity()),source,actor,true,authority);}
    private static void announce(MinecraftServer s,String line){s.getPlayerManager().broadcast(Text.literal(line+" ").append(Text.literal("[View conflict]").styled(style->style.withColor(Formatting.GOLD).withClickEvent(new ClickEvent.RunCommand("/chronicle council conflicts")))),false);}
    private static void notifyDefense(MinecraftServer s,String attacker,String defender){for(var t:TreatyRuntime.authority(s).allAuthority())if(t.status()==TreatyService.Status.ACTIVE&&t.document().purpose()==TreatyService.Purpose.MUTUAL_DEFENSE&&t.document().parties().stream().anyMatch(p->p.houseId().equals(defender)))for(var p:t.document().parties())if(!p.houseId().equals(defender)){var online=s.getPlayerManager().getPlayer(p.owner());if(online!=null)online.sendMessage(Text.literal("Your mutual-defense partner "+title(s,defender)+" was attacked by "+title(s,attacker)+". You may choose whether to support them."),false);}}
    private static ConflictService.Record record(MinecraftServer s,UUID id){return runtime(s).service.records().stream().map(ConflictService.View::record).filter(c->c.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Select an available conflict."));}
    private static List<House> members(ConflictService.Record c){var all=new ArrayList<>(c.firstSide());all.addAll(c.secondSide());return List.copyOf(all);}
    public static List<TreatyPayloads.ConflictChoice> choices(MinecraftServer s){var r=WORLDS.get(s);if(!publicationReady(s))return List.of();return r.service.records().stream().map(ConflictService.View::record).filter(c->c.status()!=ConflictService.Status.ENDED).map(c->new TreatyPayloads.ConflictChoice(c.id(),conflictTitle(s,c),c.membershipRevision(),members(c).stream().map(h->new TreatyPayloads.Person(h.owner(),h.houseId(),h.publicName(),title(s,h.houseId()))).toList(),c.status()==ConflictService.Status.ACTIVE)).toList();}
    public static List<TreatyService.Party> requiredParties(MinecraftServer s,UUID id,long revision,TreatyService.Purpose purpose){var c=record(s,id);if(c.membershipRevision()!=revision||c.status()==ConflictService.Status.ENDED||purpose==TreatyService.Purpose.CEASEFIRE&&c.status()!=ConflictService.Status.ACTIVE)throw new IllegalStateException("Conflict membership or status changed. Reopen its current parties.");return members(c).stream().map(h->new TreatyService.Party(h.houseId(),h.owner(),h.publicName())).toList();}
    public static boolean currentAgreement(MinecraftServer s,TreatyService.Document d){if(d.conflictLink()==null)return true;try{return new HashSet<>(requiredParties(s,d.conflictLink().conflictId(),d.conflictLink().membershipRevision(),d.purpose())).equals(new HashSet<>(d.parties()));}catch(RuntimeException changed){return false;}}
    public static void sanctify(MinecraftServer s,TreatyService.Document d,UUID chancellor,Set<UUID> present)throws IOException{var r=runtime(s);var c=record(s,d.conflictLink().conflictId());if(!currentAgreement(s,d))throw new IllegalStateException("Conflict membership changed. Revise the draft and sign again.");var op=new ConflictOperations.Operation(ConflictOperations.Kind.AGREEMENT,UUID.randomUUID(),chancellor,null,null,"",List.of(),d,present,c.revision());var treaties=TreatyRuntime.authority(s);ConflictOperations.validate(op,r.service,treaties);PoliticalSourcesRuntime.planTreaty(s,copy->copy.sanctify(d.id(),d.contentRevision(),chancellor,present));String receipt=prepareSource(s,c.id(),c.revision()+1,ConflictService.Action.AGREEMENT,chancellor);try{r.operations.begin(op,r.service,treaties);}catch(IOException|RuntimeException e){r.failed=true;hold(s);throw e;}EventSources.complete(s,receipt,true);announce(s,conflictTitle(s,c)+(d.purpose()==TreatyService.Purpose.PEACE?" ended in peace.":" entered a ceasefire."));}
    private static String conflictTitle(MinecraftServer s,ConflictService.Record c){return title(s,c.initiator().houseId())+" / "+title(s,c.target().houseId());}
    private static void send(MinecraftServer s,Runtime r,Session v,UUID request,boolean opening,String message){if(!ServerPlayNetworking.canSend(v.player,View.ID))return;View next=project(s,r,v,request,opening,message);View compare=project(s,r,v,ConflictPayloads.NONE,false,"");if(!opening&&request.equals(ConflictPayloads.NONE)&&compare.equals(v.sent))return;v.visibleRevision++;next=project(s,r,v,request,opening,message);ServerPlayNetworking.send(v.player,next);v.sent=project(s,r,v,ConflictPayloads.NONE,false,"");}
    private static View project(MinecraftServer s,Runtime r,Session v,UUID request,boolean opening,String message){
        if(r.failed||r.operations.pending()!=null||r.operations.uncertain())return new View(request,opening,v.token,v.visibleRevision,List.of(),null,null,v.settings,false,r.settings.threshold(),"Political records need recovery; preserve the saved journals.",false);
        var views=r.service.publicViews().stream().map(ConflictService.PublicView::record).toList();var rows=views.reversed().stream().map(c->new Row(c.id(),title(s,c.initiator().houseId())+" / "+title(s,c.target().houseId()),c.status().name())).toList();
        var c=views.stream().filter(x->x.id().equals(v.selected)).findFirst().orElse(views.isEmpty()?null:views.getLast());Detail detail=null;
        if(c!=null){v.selected=c.id();String own=eligible(v.player)?house(v.player).houseId():"";String side=c.firstSide().stream().anyMatch(h->h.houseId().equals(own))?c.initiator().houseId():c.secondSide().stream().anyMatch(h->h.houseId().equals(own))?c.target().houseId():"";boolean founder=c.initiator().houseId().equals(own)||c.target().houseId().equals(own);String label=(c.firstSide().size()+c.secondSide().size()>=r.settings.threshold()?"Civil war":"House war")+" / "+c.status().name();detail=new Detail(c.id(),c.revision(),title(s,c.initiator().houseId())+" / "+title(s,c.target().houseId()),label,c.history().getFirst().detail(),c.firstSide().stream().map(h->publicHouse(s,h)).toList(),c.secondSide().stream().map(h->publicHouse(s,h)).toList(),c.history().stream().map(e->e.action()==ConflictService.Action.DECLARED?title(s,e.actor().houseId())+" declared war: "+e.detail():e.action()==ConflictService.Action.AGREEMENT?e.detail():e.action()==ConflictService.Action.WITHDREW?title(s,e.actor().houseId())+" withdrew.":title(s,e.actor().houseId())+(e.action()==ConflictService.Action.JOINED?" joined ":" changed allegiance to ")+title(s,r.service.sideAt(c.id(),e.recordRevision(),e.actor().houseId()))+".").toList(),side,founder,eligible(v.player)&&playing(s)&&c.status()==ConflictService.Status.ACTIVE);}
        Challenge challenge=null;if(v.target!=null){String warning=TreatyRuntime.authority(s).view(v.player.getUuid(),false).stream().anyMatch(t->ConflictOperations.applicableNonAggression(t,v.ownHouse,v.targetHouse))?"You will break your non-aggression agreement. Its private details remain private.":"";challenge=new Challenge(v.token,v.targetHouse.publicName(),title(s,v.targetHouse.houseId()),warning);}
        return new View(request,opening,v.token,v.visibleRevision,rows,detail,challenge,v.settings,editable(v.player),r.settings.threshold(),message.substring(0,Math.min(512,message.length())),false);
    }
    private static ConflictPayloads.House publicHouse(MinecraftServer s,House h){return new ConflictPayloads.House(h.houseId(),title(s,h.houseId()),h.publicName());}
}
