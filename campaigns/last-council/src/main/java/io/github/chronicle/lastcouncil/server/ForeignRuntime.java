package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.core.persistence.AtomicDataFile;
import io.github.chronicle.core.server.*;
import io.github.chronicle.lastcouncil.council.*;
import io.github.chronicle.lastcouncil.foreign.*;
import io.github.chronicle.lastcouncil.network.ForeignPayloads;
import io.github.chronicle.lastcouncil.network.ForeignPayloads.*;
import io.github.chronicle.lastcouncil.network.ProceedingsPayloads;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

/** Public world diplomacy; operator catalogue changes never impose a political outcome. */
public final class ForeignRuntime {
    public static final String HOLD="last_council:foreign_realms";
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final Map<MinecraftServer,Runtime> WORLDS=new IdentityHashMap<>();
    private static final class Runtime {
        final ForeignRealmService realms; final ForeignAffairsService affairs;
        final Map<UUID,Session> sessions=new HashMap<>(); final Map<UUID,int[]> limits=new HashMap<>();
        boolean failed;
        Runtime(ForeignRealmService realms,ForeignAffairsService affairs){this.realms=realms;this.affairs=affairs;}
    }
    private static final class Session {
        final ServerPlayerEntity player;final long token=RANDOM.nextLong(1,Long.MAX_VALUE);final boolean editing;
        String selected="";int page;long revision;View sent;
        Session(ServerPlayerEntity player,boolean editing){this.player=player;this.editing=editing;}
    }
    private ForeignRuntime(){}
    public static void register(){
        ScenarioContentRuntime.registerExtension(HOLD,new ScenarioContentRuntime.ExtensionAdapter(){
            public void validate(String json)throws IOException{ForeignRealmService.decode(json.getBytes(StandardCharsets.UTF_8));}
            public String capture(MinecraftServer server)throws IOException{if(!CouncilRuntime.active())return null;var r=runtime(server);r.realms.verify();return r.realms.exportJson();}
            public void initialize(Path directory,String initial,boolean legacy)throws IOException{initializeCatalogue(directory,initial,legacy);}
            public void verify(Path directory)throws IOException{initializeCatalogue(directory,null,true);}
        });
        if(CouncilRuntime.active())GameSetupTools.register(new GameSetupTools.Tool(HOLD,"Foreign realms","Define external realms used by Council war and peace motions.",
                List.of(new GameSetupTools.Verb("open","Open foreign realms",GameSetupTools.Guard.READ_ONLY,false)),
                source->new GameSetupTools.Status(runtime(source.getServer()).realms.revision(),List.of(),Map.of()),
                (source,verb)->{open(source.getPlayerOrThrow(),true);return "";}));
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
        ServerDisconnects.register((handler,server)->{var r=WORLDS.get(server);if(r!=null){r.sessions.remove(handler.player.getUuid());r.limits.remove(handler.player.getUuid());}});
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(q,c)->c.server().execute(()->receive(c.player(),q)));
        ServerTickEvents.END_SERVER_TICK.register(server->{var r=WORLDS.get(server);if(r==null||server.getTicks()%10!=0)return;
            for(var session:List.copyOf(r.sessions.values()))try{send(server,r,session,ForeignPayloads.NONE,false,"");}catch(IOException|RuntimeException failure){r.failed=true;hold(server);}
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->dispatcher.register(literal("chronicle").then(literal("council")
                .then(literal("recover").requires(source->source.hasPermissionLevel(2)).executes(context->{boolean ready=ProceedingsRuntime.recoverAuthority(context.getSource().getServer());context.getSource().sendFeedback(()->Text.literal(ready?"Council authority reloaded; pinned operations recovered.":"Council recovery remains held; preserve saved files."),false);return ready?1:0;}))
                .then(literal("foreign-affairs")
                .executes(c->{open(c.getSource().getPlayerOrThrow(),false);return 1;})
                .then(literal("settings").requires(s->s.hasPermissionLevel(2)).executes(c->{open(c.getSource().getPlayerOrThrow(),true);return 1;}))))));
    }
    /** Called before Proceedings is reconstructed, so one recovery reloads all three observed stores. */
    public static void reloadAuthority(MinecraftServer server)throws IOException{
        var old=WORLDS.remove(server);
        if(old!=null)for(var session:old.sessions.values())if(ServerPlayNetworking.canSend(session.player,View.ID))
            ServerPlayNetworking.send(session.player,new View(ForeignPayloads.NONE,false,session.token,session.revision+1,old.realms.revision(),List.of(),null,session.editing,false,"Foreign records reloaded. Reopen the screen.",true));
        try{
            Path directory=ScenarioAuthoringProfiles.chronicleConfig().resolve(ScenarioAuthoringProfiles.active().isPresent()?"profile-content":"");
            initializeCatalogue(directory,null,true);
            Path root=server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/last-council");
            AtomicDataFile marker=new AtomicDataFile(root.resolve("foreign-initialized"),32);byte[] mark=marker.load();
            if(mark!=null&&(!Arrays.equals(mark,new byte[]{'1','\n'})||!Files.isRegularFile(root.resolve("foreign-affairs.json"),LinkOption.NOFOLLOW_LINKS)))
                throw new IOException("Initialized foreign affairs journal missing or invalid; restore it.");
            var realms=new ForeignRealmService(directory.resolve("last-council-foreign-realms.json"));
            var affairs=new ForeignAffairsService(root.resolve("foreign-affairs.json"));
            if(mark==null)marker.save(new byte[]{'1','\n'});
            WORLDS.put(server,new Runtime(realms,affairs));RuntimeHolds.release(server,HOLD);
        }catch(IOException|RuntimeException failure){hold(server);throw failure;}
    }
    private static void initializeCatalogue(Path directory,String initial,boolean legacy)throws IOException{
        var marker=new AtomicDataFile(directory.resolve("last-council-foreign-realms.initialized"),32);byte[] mark=marker.load();
        var file=new AtomicDataFile(directory.resolve("last-council-foreign-realms.json"),512*1024);byte[] existing=file.load();
        if(mark!=null){if(!Arrays.equals(mark,new byte[]{'1','\n'})||existing==null)throw new IOException("Initialized foreign realm catalogue missing; restore it.");ForeignRealmService.decode(existing);return;}
        byte[] desired=initial!=null&&!legacy?initial.getBytes(StandardCharsets.UTF_8):ForeignRealmService.encode(0,List.of());
        ForeignRealmService.decode(desired);
        if(existing==null)file.save(desired);else{ForeignRealmService.decode(existing);if(initial!=null&&!legacy&&!Arrays.equals(existing,desired))throw new IOException("Foreign realm catalogue differs from import; preserved.");}
        marker.save(new byte[]{'1','\n'});
    }
    private static Runtime runtime(MinecraftServer s){var r=WORLDS.get(s);if(r==null)throw new IllegalStateException("Foreign records need Council recovery.");return r;}
    private static void hold(MinecraftServer s){RuntimeHolds.hold(s,HOLD,"Foreign records need exact Council recovery; preserve all journals and catalogue files.");}
    public static ProceedingsService.MotionEffects effects(MinecraftServer s){var r=runtime(s);return new ForeignMotionEffects(r.realms,r.affairs);}
    public static List<ProceedingsPayloads.RealmChoice> choices(MinecraftServer s){var r=runtime(s);if(r.failed||!ProceedingsRuntime.effectsReady(s))return List.of();try{r.realms.verify();r.affairs.verify();}catch(IOException failure){r.failed=true;hold(s);throw new IllegalStateException("Foreign records changed; recover Council authority.",failure);}return r.realms.realms().stream().map(realm->{var state=r.affairs.view(realm.id());return new ProceedingsPayloads.RealmChoice(realm.id(),realm.name(),state.revision(),state.atWar());}).toList();}
    public static String title(MinecraftServer s,String id){var realm=runtime(s).realms.get(id);return realm==null?"Unavailable realm":realm.name();}
    public static Set<String> referenced(MinecraftServer s){var r=runtime(s);Set<String> ids=new HashSet<>(r.affairs.referencedRealms());for(var motion:ProceedingsRuntime.state(s).motions().values())if(motion.foreign()!=null)ids.add(motion.foreign().realmId());return Set.copyOf(ids);}
    private static boolean editable(ServerPlayerEntity p){var s=p.getEntityWorld().getServer();return p.hasPermissionLevel(2)&&AuthoringRuntime.isAuthor(p)&&AuthoringRuntime.editable(s)&&RuntimeHolds.reason(s)==null&&ProceedingsRuntime.effectsReady(s);}
    public static void open(ServerPlayerEntity p,boolean editing){var s=p.getEntityWorld().getServer();if(!CouncilRuntime.active())return;
        try{var r=runtime(s);if(editing&&!p.hasPermissionLevel(2))throw new IllegalStateException("Operator access required.");var v=new Session(p,editing);r.sessions.put(p.getUuid(),v);send(s,r,v,UUID.randomUUID(),true,"");}
        catch(IOException|RuntimeException failure){p.sendMessage(Text.literal(Objects.toString(failure.getMessage(),"Foreign records unavailable.")),false);}
    }
    private static void receive(ServerPlayerEntity p,Request q){var s=p.getEntityWorld().getServer();Runtime r=WORLDS.get(s);if(r==null||!CouncilRuntime.active())return;
        int second=s.getTicks()/20;int[] limit=r.limits.computeIfAbsent(p.getUuid(),id->new int[]{second,0});if(limit[0]!=second){limit[0]=second;limit[1]=0;}if(++limit[1]>12)return;
        if(q.action()==Action.OPEN){open(p,false);return;}
        Session v=r.sessions.get(p.getUuid());if(v==null||v.player!=p||v.token!=q.token())return;
        if(q.action()==Action.CLOSE){r.sessions.remove(p.getUuid());return;}
        try{
            if(q.action()==Action.STATE){v.page=q.realm().equals(v.selected)?q.historyPage():0;v.selected=q.realm();send(s,r,v,q.request(),false,"");return;}
            if(!v.editing||!editable(p))throw new IllegalStateException("Pause play and enable Editing to change foreign realms.");
            r.realms.verify();r.affairs.verify();
            switch(q.action()){
                case CREATE->{v.selected=r.realms.create(q.revision(),q.name(),q.description()).id();}
                case UPDATE->{v.selected=r.realms.update(q.revision(),q.realm(),q.name(),q.description()).id();}
                case DELETE->{r.realms.delete(q.revision(),q.realm(),referenced(s));v.selected="";}
                default->throw new IllegalArgumentException("Choose a realm action.");
            }
            send(s,r,v,q.request(),false,"Realm catalogue saved.",true);
        }catch(IOException failure){r.failed=true;hold(s);safeReply(s,r,v,q.request(),"Foreign records need recovery; preserve the saved files.");}
        catch(RuntimeException failure){try{r.realms.verify();r.affairs.verify();}catch(IOException uncertain){r.failed=true;hold(s);}safeReply(s,r,v,q.request(),Objects.toString(failure.getMessage(),"Realm action refused."));}
    }
    private static void safeReply(MinecraftServer s,Runtime r,Session v,UUID request,String message){try{send(s,r,v,request,false,message);}catch(IOException|RuntimeException failure){r.failed=true;hold(s);}}
    private static void send(MinecraftServer s,Runtime r,Session v,UUID request,boolean opening,String message)throws IOException{
        send(s,r,v,request,opening,message,false);
    }
    private static void send(MinecraftServer s,Runtime r,Session v,UUID request,boolean opening,String message,boolean saved)throws IOException{
        if(!ServerPlayNetworking.canSend(v.player,View.ID))return;
        View comparison=project(s,r,v,ForeignPayloads.NONE,false,"");
        if(!opening&&request.equals(ForeignPayloads.NONE)&&comparison.equals(v.sent))return;
        v.revision++;View next=project(s,r,v,request,opening,message);
        if(saved)next=new View(next.request(),next.opening(),next.token(),next.revision(),next.catalogRevision(),next.rows(),next.detail(),next.editing(),next.editable(),next.message(),next.closed(),true);
        if(!ForeignPayloads.fits(next))throw new IOException("Foreign view exceeds its packet budget.");
        ServerPlayNetworking.send(v.player,next);v.sent=project(s,r,v,ForeignPayloads.NONE,false,"");
    }
    private static View project(MinecraftServer s,Runtime r,Session v,UUID request,boolean opening,String message)throws IOException{
        if(r.failed||!ProceedingsRuntime.effectsReady(s))return new View(request,opening,v.token,v.revision,r.realms.revision(),List.of(),null,v.editing,false,"Council/foreign records need recovery; preserve saved files.",false);
        r.realms.verify();r.affairs.verify();
        List<Row> rows=r.realms.realms().stream().map(realm->new Row(realm.id(),realm.name(),r.affairs.view(realm.id()).atWar())).toList();
        var selected=r.realms.realms().stream().filter(realm->realm.id().equals(v.selected)).findFirst().orElse(rows.isEmpty()?null:r.realms.get(rows.getFirst().id()));
        Detail detail=null;
        if(selected!=null){v.selected=selected.id();var state=r.affairs.view(selected.id());int pages=Math.max(1,(state.history().size()+31)/32);v.page=Math.min(v.page,pages-1);int end=Math.max(0,state.history().size()-v.page*32);detail=new Detail(selected.id(),selected.name(),selected.description(),state.atWar(),state.revision(),state.history().subList(Math.max(0,end-32),end).stream().map(e->e.motionTitle()+": Council voted "+(e.kind()==ForeignAffairsService.Kind.DECLARE_WAR?"for war.":"for peace.")).toList(),referenced(s).contains(selected.id()),v.page,pages);}
        return new View(request,opening,v.token,v.revision,r.realms.revision(),rows,detail,v.editing,v.editing&&editable(v.player),message.substring(0,Math.min(1024,message.length())),false);
    }
}
