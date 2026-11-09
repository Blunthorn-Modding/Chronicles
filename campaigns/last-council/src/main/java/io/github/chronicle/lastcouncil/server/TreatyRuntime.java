package io.github.chronicle.lastcouncil.server;

import com.google.gson.*;
import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.persistence.AtomicDataFile;
import io.github.chronicle.core.server.*;
import io.github.chronicle.lastcouncil.network.TreatyPayloads;
import io.github.chronicle.lastcouncil.network.TreatyPayloads.*;
import io.github.chronicle.lastcouncil.treaties.TreatyService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import static net.minecraft.server.command.CommandManager.literal;

/** Physical ceremony sessions and audience-specific projections; treaty consent remains world metadata. */
public final class TreatyRuntime {
    public static final String LECTERN="last_council:treaty_lectern",HOLD="last_council:treaties";
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final Map<MinecraftServer,Runtime> WORLDS=new IdentityHashMap<>();
    private static final class Runtime {
        final TreatyService service;final Bindings bindings;
        final Map<UUID,Session> sessions=new HashMap<>();
        Runtime(TreatyService service,Bindings bindings){this.service=service;this.bindings=bindings;}
    }
    private static final class Session {
        final ServerPlayerEntity player;final AuthoringSite.Anchor anchor;final long token;
        UUID selected=TreatyPayloads.NONE;int page;View sent;long visibleRevision;boolean ready;
        long lastTick=-1;int requests;
        Session(ServerPlayerEntity player,AuthoringSite.Anchor anchor){this.player=player;this.anchor=anchor;token=RANDOM.nextLong(1,Long.MAX_VALUE);}
    }
    private TreatyRuntime() { }
    public static void register(){
        if(CouncilRuntime.active())SiteSubtypes.register(new SiteSubtypes.Definition(LECTERN,"Treaty ceremony",
                "A separate lectern where parties sign terms and the Chancellor sanctifies their agreement.",Set.of(SiteSubtypes.Kind.BLOCK),new SiteSubtypes.Handler(){
            public boolean assigned(MinecraftServer server,AuthoringSite site){var r=WORLDS.get(server);return r!=null&&r.bindings.ids.contains(site.id());}
            public String refusal(MinecraftServer server,AuthoringSite site,boolean enabling){
                if(!(site instanceof AuthoringSite.Anchor a)||!a.expectedBlock().equals("minecraft:lectern"))return "Choose a Block site bound to a lectern.";
                var r=WORLDS.get(server);if(r==null)return "Treaty records are unavailable.";
                if(enabling&&motionAt(server,a))return "Use a separate lectern for treaties and Council motions.";
                if(enabling&&r.bindings.ids.stream().filter(id->!id.equals(site.id())).map(id->site(server,id)).filter(AuthoringSite.Anchor.class::isInstance).map(AuthoringSite.Anchor.class::cast).anyMatch(other->samePlace(a,other)))return "This physical lectern already has a treaty binding.";
                return "";
            }
            public SiteSubtypes.Change change(net.minecraft.server.command.ServerCommandSource source,AuthoringSite site,boolean enabled)throws IOException{
                var r=runtime(source.getServer());try{r.bindings.set(site.id(),enabled);}catch(IOException e){hold(source.getServer());throw e;}
                refresh(source.getServer(),r);return SiteSubtypes.Change.APPLIED;
            }
        }));
        ServerLifecycleEvents.SERVER_STARTED.register(server->{if(CouncilRuntime.active())start(server);});
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
        ServerTickEvents.END_SERVER_TICK.register(TreatyRuntime::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{var r=WORLDS.get(server);if(r!=null){r.sessions.remove(handler.player.getUuid());refresh(server,r);}});
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(request,context)->context.server().execute(()->receive(context.player(),request)));
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(hand!=Hand.MAIN_HAND||!(world instanceof ServerWorld w)||!(player instanceof ServerPlayerEntity))return ActionResult.PASS;
            var r=WORLDS.get(w.getServer());if(r==null)return ActionResult.PASS;
            return anchors(w.getServer(),r,w.getRegistryKey().getValue().toString(),hit.getBlockPos()).isEmpty()?ActionResult.PASS:ActionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("chronicle").then(literal("council")
                .then(literal("treaties").then(literal("recover").requires(s->s.hasPermissionLevel(2)).executes(c->{
                    if(!CouncilRuntime.active())return 0;start(c.getSource().getServer());
                    boolean ready=WORLDS.containsKey(c.getSource().getServer());c.getSource().sendFeedback(()->Text.literal(ready?"Treaty records reloaded; saved consent and history were retained.":"Treaty records still need repair. Existing files were preserved."),false);return ready?1:0;
                }))))));
    }
    private static void start(MinecraftServer server){
        try{var root=server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/last-council");
            var next=new Runtime(new TreatyService(root.resolve("treaties.json")),new Bindings(root.resolve("treaty-lecterns.json")));
            var old=WORLDS.put(server,next);if(old!=null)for(var session:List.copyOf(old.sessions.values()))revoke(old,session,"Records reloaded. Return to the treaty lectern.");RuntimeHolds.release(server,HOLD);
        }catch(IOException|RuntimeException invalid){var old=WORLDS.remove(server);if(old!=null)for(var session:List.copyOf(old.sessions.values()))revoke(old,session,"Treaty records need repair. Return after recovery.");hold(server);}
    }
    private static Runtime runtime(MinecraftServer server){var r=WORLDS.get(server);if(r==null)throw new IllegalStateException("Treaty records are unavailable. Ask an operator to recover them.");return r;}
    private static void hold(MinecraftServer server){RuntimeHolds.hold(server,HOLD,"Treaty records need exact reload or stopped-file repair. Use /chronicle council treaties recover.");}
    private static AuthoringSite site(MinecraftServer server,String id){return AuthoringRuntime.get(server).service().state().sites().get(id);}
    private static boolean samePlace(AuthoringSite.Anchor a,AuthoringSite.Anchor b){return a.dimension().equals(b.dimension())&&a.position().equals(b.position());}
    public static boolean boundAt(MinecraftServer server,AuthoringSite site){var r=WORLDS.get(server);return r!=null&&site instanceof AuthoringSite.Anchor a&&r.bindings.ids.stream().map(id->site(server,id)).filter(AuthoringSite.Anchor.class::isInstance).map(AuthoringSite.Anchor.class::cast).anyMatch(other->samePlace(a,other));}
    private static boolean motionAt(MinecraftServer server,AuthoringSite.Anchor a){return ProceedingsRuntime.state(server).lecterns().stream().map(id->site(server,id)).filter(AuthoringSite.Anchor.class::isInstance).map(AuthoringSite.Anchor.class::cast).anyMatch(other->samePlace(a,other));}
    private static List<AuthoringSite.Anchor> anchors(MinecraftServer server,Runtime r,String dimension,BlockPos pos){return r.bindings.ids.stream().map(id->site(server,id)).filter(AuthoringSite.Anchor.class::isInstance).map(AuthoringSite.Anchor.class::cast).filter(a->a.dimension().equals(dimension)&&a.position().equals(new BlockPosition(pos.getX(),pos.getY(),pos.getZ()))).toList();}
    private static void usable(ServerPlayerEntity player,Runtime r,AuthoringSite.Anchor anchor){
        var server=player.getEntityWorld().getServer();
        if(server.getPlayerManager().getPlayer(player.getUuid())!=player||!r.bindings.ids.contains(anchor.id())||!Objects.equals(site(server,anchor.id()),anchor)
                ||!anchor.dimension().equals(player.getEntityWorld().getRegistryKey().getValue().toString())||!player.isAlive()||player.isSpectator()
                ||player.squaredDistanceTo(anchor.position().x()+.5,anchor.position().y()+.5,anchor.position().z()+.5)>36)throw new IllegalStateException("Return to the bound treaty lectern.");
        var chunk=player.getEntityWorld().getChunkManager().getWorldChunk(anchor.position().x()>>4,anchor.position().z()>>4);
        if(chunk==null||!chunk.getBlockState(new BlockPos(anchor.position().x(),anchor.position().y(),anchor.position().z())).isOf(Blocks.LECTERN))throw new IllegalStateException("The treaty lectern is no longer here.");
        if(motionAt(server,anchor))throw new IllegalStateException("Use separate lecterns for treaties and motions.");
    }
    private static boolean playing(MinecraftServer server){return ChronicleRuntime.service(server).state().phase()==SessionPhase.ACTIVE&&!SessionBuilding.enabled(server)&&RuntimeHolds.reason(server)==null;}
    private static boolean partyEligible(ServerPlayerEntity player){var server=player.getEntityWorld().getServer();return !CouncilRuntime.isChancellor(server,player.getUuid())&&PlayRuntime.eligibleIds(server).contains(player.getUuid())&&CouncilRuntime.roster(server).assignments().containsKey(player.getUuid());}
    private static boolean present(MinecraftServer server,Runtime r,Session session,AuthoringSite.Anchor anchor){try{usable(session.player,r,session.anchor);return session.ready&&samePlace(session.anchor,anchor)&&!AuthoringRuntime.isAuthor(session.player)&&(CouncilRuntime.isChancellor(server,session.player.getUuid())||partyEligible(session.player));}catch(RuntimeException invalid){return false;}}
    private static Set<UUID> present(MinecraftServer server,Runtime r,AuthoringSite.Anchor anchor){var ids=new HashSet<UUID>();for(var s:r.sessions.values())if(present(server,r,s,anchor))ids.add(s.player.getUuid());return ids;}
    private static List<Person> people(MinecraftServer server,Runtime r,AuthoringSite.Anchor anchor)throws IOException{
        List<Person> result=new ArrayList<>();for(var s:r.sessions.values())if(present(server,r,s,anchor)&&partyEligible(s.player)){
            var assignment=CouncilRuntime.roster(server).assignments().get(s.player.getUuid());
            result.add(new Person(s.player.getUuid(),assignment.houseId(),Profiles.publicCard(server,s.player.getUuid(),s.player.getName().getString()).name()));
        }result.sort(Comparator.comparing(Person::name).thenComparing(p->p.owner().toString()));return List.copyOf(result);
    }
    private static void receive(ServerPlayerEntity player,Request q){
        var server=player.getEntityWorld().getServer();if(!CouncilRuntime.active()||server.getPlayerManager().getPlayer(player.getUuid())!=player)return;
        var r=WORLDS.get(server);if(r==null)return;
        try{
            Session s=r.sessions.get(player.getUuid());
            if(q.action()==Action.OPEN){
                var matches=anchors(server,r,player.getEntityWorld().getRegistryKey().getValue().toString(),q.block());if(matches.isEmpty())return;
                if(matches.size()!=1)throw new IllegalStateException("This lectern has conflicting treaty bindings.");
                usable(player,r,matches.getFirst());if(!playing(server))throw new IllegalStateException("Treaty ceremonies wait for active play.");
                s=new Session(player,matches.getFirst());r.sessions.put(player.getUuid(),s);send(server,r,s,q.request(),true,"");refresh(server,r);return;
            }
            if(s==null||s.player!=player||q.token()!=s.token)return;
            if(q.action()==Action.CLOSE){r.sessions.remove(player.getUuid());refresh(server,r);return;}
            usable(player,r,s.anchor);
            long tick=server.getTicks();if(s.lastTick!=tick){s.lastTick=tick;s.requests=0;}if(++s.requests>8)throw new IllegalStateException("Wait for the previous treaty request.");
            if(q.action()==Action.READY){if(!playing(server))throw new IllegalStateException("Treaty ceremonies wait for active play.");s.ready=true;send(server,r,s,q.request(),false,"");refresh(server,r);return;}
            if(!s.ready)throw new IllegalStateException("Open this treaty lectern before acting.");
            if(q.action()==Action.STATE){s.selected=q.treaty();s.page=q.historyPage();send(server,r,s,q.request(),false,"");return;}
            if(!playing(server)||AuthoringRuntime.isAuthor(player))throw new IllegalStateException("Treaty actions require active play outside Editing and Building Mode.");
            boolean host=CouncilRuntime.isChancellor(server,player.getUuid());
            if(!host&&!partyEligible(player))throw new IllegalStateException("A participating House representative is required.");
            var current=r.service.view(player.getUuid(),host).stream().filter(v->v.document().id().equals(q.treaty())).findFirst();
            if(q.action()!=Action.CREATE&&current.isEmpty())throw new IllegalStateException("That agreement is not available.");
            String message;
            switch(q.action()){
                case CREATE,EDIT->{
                    if(host)throw new IllegalStateException("The Chancellor cannot create or edit as a House.");
                    var available=people(server,r,s.anchor);var selected=new ArrayList<TreatyService.Party>();
                    if(new HashSet<>(q.parties()).size()!=q.parties().size()||!q.parties().contains(player.getUuid()))throw new IllegalArgumentException("Include yourself and distinct representatives.");
                    for(UUID id:q.parties()){var p=available.stream().filter(person->person.owner().equals(id)).findFirst().orElseThrow(()->new IllegalStateException("Every selected party must have this same lectern open."));selected.add(new TreatyService.Party(p.house(),p.owner(),p.name()));}
                    UUID id=q.treaty().equals(TreatyPayloads.NONE)?q.request():q.treaty();
                    var doc=new TreatyService.Document(id,q.action()==Action.CREATE?1:Math.addExact(q.revision(),1),q.title().strip(),q.terms().strip(),q.privateTerms()?TreatyService.Visibility.PRIVATE:TreatyService.Visibility.PUBLIC,selected);
                    if(q.action()==Action.CREATE)r.service.create(id,player.getUuid(),doc);else r.service.edit(id,q.revision(),player.getUuid(),doc);
                    s.selected=id;message=q.action()==Action.CREATE?"Treaty draft submitted.":"Draft saved. Changed terms require new signatures.";
                }
                case SIGN->{if(host)throw new IllegalStateException("The Chancellor cannot sign for a House.");r.service.sign(q.treaty(),q.revision(),player.getUuid());s.selected=q.treaty();message="Your signature is recorded for these exact terms.";}
                case SANCTIFY->{if(!host)throw new IllegalStateException("Only the assigned Chancellor can sanctify an agreement.");r.service.sanctify(q.treaty(),q.revision(),player.getUuid(),present(server,r,s.anchor));s.selected=q.treaty();message="Agreement sanctified.";}
                case BREAK->{if(host)throw new IllegalStateException("Only a party can break its agreement.");r.service.breach(q.treaty(),q.revision(),player.getUuid());s.selected=q.treaty();message="Breach recorded. The agreement's visibility is unchanged.";}
                default->throw new IllegalArgumentException("Choose a treaty action.");
            }
            s.page=0;send(server,r,s,q.request(),false,message);refresh(server,r);
        }catch(IOException failure){hold(server);error(player,r,q,"Treaty records need exact reload; the saved files were preserved.");}
        catch(RuntimeException refused){error(player,r,q,Objects.toString(refused.getMessage(),"Treaty action refused."));}
    }
    private static void error(ServerPlayerEntity player,Runtime r,Request q,String message){var s=r.sessions.get(player.getUuid());if(s==null){player.sendMessage(Text.literal(message),false);return;}try{send(player.getEntityWorld().getServer(),r,s,q.request(),false,message);}catch(Exception unavailable){player.sendMessage(Text.literal("Return to the treaty lectern after the records are recovered."),false);}}
    private static void send(MinecraftServer server,Runtime r,Session s,UUID request,boolean opening,String message)throws IOException{
        if(!ServerPlayNetworking.canSend(s.player,View.ID))return;
        var current=project(server,r,s,TreatyPayloads.NONE,false,"");
        if(!current.equals(s.sent))s.visibleRevision=Math.incrementExact(s.visibleRevision);
        View packet=project(server,r,s,request,opening,message);if(!TreatyPayloads.fits(packet))throw new IllegalStateException("Treaty history is too large to display safely.");
        ServerPlayNetworking.send(s.player,packet);s.sent=project(server,r,s,TreatyPayloads.NONE,false,"");
    }
    private static View project(MinecraftServer server,Runtime r,Session s,UUID request,boolean opening,String message)throws IOException{
        boolean host=CouncilRuntime.isChancellor(server,s.player.getUuid()),active=playing(server),actor=partyEligible(s.player),actionable=active&&s.ready;
        var visible=r.service.view(s.player.getUuid(),host);var rows=visible.stream().map(t->new Row(t.document().id(),t.document().title(),status(t.status()),t.document().visibility()==TreatyService.Visibility.PRIVATE)).toList();
        var selected=visible.stream().filter(t->t.document().id().equals(s.selected)).findFirst().orElse(visible.isEmpty()?null:visible.getFirst());
        Detail detail=null;int pages=1;var attendees=present(server,r,s.anchor);
        boolean witness=attendees.stream().anyMatch(id->CouncilRuntime.isChancellor(server,id));
        if(selected!=null){var d=selected.document();s.selected=d.id();pages=Math.max(1,(selected.history().size()+31)/32);s.page=Math.clamp(s.page,0,pages-1);
            var parties=d.parties().stream().map(p->new PartyView(p.owner(),p.houseId(),p.publicName(),attendees.contains(p.owner()),Objects.equals(selected.signatures().get(p.owner()),d.contentRevision()))).toList();
            boolean own=d.parties().stream().anyMatch(p->p.owner().equals(s.player.getUuid())),draft=selected.status()==TreatyService.Status.DRAFT;
            var history=selected.history().stream().skip(s.page*32L).limit(32).map(TreatyRuntime::history).toList();
            detail=new Detail(d.id(),d.contentRevision(),d.title(),d.terms(),status(selected.status()),d.visibility()==TreatyService.Visibility.PRIVATE,parties,witness,
                    actionable&&actor&&own&&draft,actionable&&actor&&own&&draft&&!selected.signatures().containsKey(s.player.getUuid()),
                    actionable&&host&&draft&&witness&&parties.stream().allMatch(p->p.present()&&p.signed()),actionable&&actor&&own&&selected.status()==TreatyService.Status.ACTIVE,history);
        }else{s.selected=TreatyPayloads.NONE;s.page=0;}
        return new View(request,opening,s.token,s.visibleRevision,rows,detail,people(server,r,s.anchor),actor,host,active,message.substring(0,Math.min(512,message.length())),s.page,pages);
    }
    private static String status(TreatyService.Status status){return switch(status){case DRAFT->"Draft";case ACTIVE->"Active";case BROKEN->"Broken";};}
    static String history(TreatyService.Event event){var d=event.document();String who=d.parties().stream().filter(p->p.owner().equals(event.actor())).map(TreatyService.Party::publicName).findFirst().orElse("Chancellor");String text=event.action()+" / revision "+d.contentRevision()+" / "+who;if(event.action()==TreatyService.Action.CREATED||event.action()==TreatyService.Action.EDITED)text+="\n"+d.title()+"\n"+d.terms();return text;}
    private static void refresh(MinecraftServer server,Runtime r){for(var s:List.copyOf(r.sessions.values()))try{usable(s.player,r,s.anchor);var next=project(server,r,s,TreatyPayloads.NONE,false,"");if(!next.equals(s.sent))send(server,r,s,TreatyPayloads.NONE,false,"");}catch(IOException e){hold(server);}catch(RuntimeException stale){revoke(r,s,"Return to the bound treaty lectern.");}}
    private static void revoke(Runtime r,Session s,String message){r.sessions.remove(s.player.getUuid());if(ServerPlayNetworking.canSend(s.player,View.ID))ServerPlayNetworking.send(s.player,new View(TreatyPayloads.NONE,false,s.token,s.visibleRevision,List.of(),null,List.of(),false,false,false,message,0,1,true));}
    private static void tick(MinecraftServer server){if(server.getTicks()%10==0){var r=WORLDS.get(server);if(r!=null)refresh(server,r);}}
    private static final class Bindings {
        final AtomicDataFile file;Set<String> ids;
        Bindings(java.nio.file.Path path)throws IOException{file=new AtomicDataFile(path,32768);byte[] bytes=file.load();if(bytes==null){ids=Set.of();return;}
            try{var root=BookConfig.strictObject(StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString());if(!root.keySet().equals(Set.of("schema","lecterns"))||!root.get("schema").toString().equals("1"))throw new IllegalArgumentException();
                var found=new TreeSet<String>();for(var value:root.getAsJsonArray("lecterns")){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||!AuthoringSite.ID.matcher(value.getAsString()).matches()||!found.add(value.getAsString()))throw new IllegalArgumentException();}if(found.size()>64)throw new IllegalArgumentException();ids=Set.copyOf(found);
            }catch(RuntimeException invalid){throw new IOException("Invalid treaty lectern bindings; preserved",invalid);}
        }
        void set(String id,boolean enabled)throws IOException{var next=new TreeSet<>(ids);if(enabled)next.add(id);else next.remove(id);if(next.size()>64)throw new IllegalStateException("Treaty lectern limit reached.");var root=new JsonObject();root.addProperty("schema",1);var all=new JsonArray();next.forEach(all::add);root.add("lecterns",all);file.save((root+"\n").getBytes(StandardCharsets.UTF_8));ids=Set.copyOf(next);}
    }
}
