package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.core.menu.CoreMenuModel.Fact;
import io.github.chronicle.core.eventpool.EventPoolDefinition;
import io.github.chronicle.core.play.*;
import io.github.chronicle.core.server.*;
import io.github.chronicle.lastcouncil.LastCouncil;
import io.github.chronicle.lastcouncil.complications.*;
import io.github.chronicle.lastcouncil.council.ProceedingsState.Phase;
import io.github.chronicle.lastcouncil.network.ComplicationPayloads.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

/** Council pacing owns the private schedule; Core remains the only Event executor. */
public final class ComplicationsRuntime {
    public static final String HOLD="last_council:complications";
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final Map<MinecraftServer,Runtime> WORLDS=new IdentityHashMap<>();
    private static final UUID NONE=new UUID(0,0);
    private ComplicationsRuntime() { }
    private static final class Runtime {
        final ComplicationSettings settings;
        final ComplicationService plan;
        long revision=RANDOM.nextLong(Long.MAX_VALUE),observedAt=-1;
        int observedMeeting=-1;
        boolean observedAdvance;
        String problem="";
        Runtime(ComplicationSettings settings,ComplicationService plan){this.settings=settings;this.plan=plan;}
        ComplicationConfig config(){return plan.isInitialized()?plan.config():settings.config();}
    }
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(ComplicationsRuntime::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            var r=WORLDS.get(server);if(r==null)return;
            try { ready(r);if(!finished(r))observe(server,r,true); }
            catch(IOException|RuntimeException unavailable) {
                RuntimeHolds.hold(server,HOLD,"Council complication clock could not be checkpointed; retained records were preserved.");
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
        ServerTickEvents.END_SERVER_TICK.register(ComplicationsRuntime::tick);
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(q,c)->c.server().execute(()->request(c.player(),q)));
        if(PackagedScenarios.mechanicsFor(LastCouncil.SCENARIO,LastCouncil.MECHANICS))GameSetupTools.register(new GameSetupTools.Tool(
                "last_council:complications","Council complications","Configure random interruptions for Council meetings.",
                List.of(new GameSetupTools.Verb("open","Configure complications",GameSetupTools.Guard.READ_ONLY,false)),
                source->new GameSetupTools.Status(runtime(source.getServer()).revision,List.of(),Map.of()),
                (source,verb)->{if(source.getPlayer()==null)throw new IllegalStateException("Open this menu as a player.");send(source.getPlayer(),NONE,true,"");return "";}));
    }
    private static void start(MinecraftServer server) {
        if(!PackagedScenarios.mechanicsFor(LastCouncil.SCENARIO,LastCouncil.MECHANICS))return;
        try {
            var defaults=ComplicationConfig.defaults();
            if(!PackagedScenarios.defaultsFor(LastCouncil.SCENARIO))defaults=new ComplicationConfig(defaults.poolId(),0,defaults.meetings(),defaults.earliestTicks(),defaults.latestTicks(),defaults.spacingTicks());
            var directory=ScenarioAuthoringProfiles.chronicleConfig().resolve(ScenarioAuthoringProfiles.active().isPresent()?"profile-content":"");
            var settings=new ComplicationSettings(directory.resolve("last-council-complications.json"),defaults);
            var plan=ComplicationService.load(ComplicationStorage.file(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/last-council/complications.json")),RANDOM::nextInt);
            WORLDS.put(server,new Runtime(settings,plan));
        }catch(IOException|RuntimeException invalid){WORLDS.remove(server);RuntimeHolds.hold(server,HOLD,"Council complication records need repair; retained state was preserved.");}
    }
    private static Runtime runtime(MinecraftServer server) {
        var r=WORLDS.get(server);if(r==null)throw new IllegalStateException("Council complication records are unavailable; restore their retained files before continuing.");return r;
    }
    private static void ready(Runtime r)throws IOException {r.settings.verify();r.plan.verifyUnchanged();}
    private static void initialize(MinecraftServer server,Runtime r)throws IOException {
        // Commit even a disabled evening, so enabling later cannot allocate into past meetings.
        if(r.plan.isInitialized())return;
        validateSetup(server,r.settings.config());
        r.plan.initialize(r.settings.config(),UUID.randomUUID());r.revision=Math.incrementExact(r.revision);
    }
    private static void validateSetup(MinecraftServer server,ComplicationConfig c) {
        if(c.total()==0)return;
        EventPoolDefinition pool=EventPoolCatalogRuntime.definitions(server).get(c.poolId());
        if(pool==null||pool.entries().stream().filter(e->EventPoolCatalogRuntime.issue(server,e.eventId()).isBlank()).count()<c.total())
            throw new IllegalStateException("Choose a pool with enough enabled manual once-only Events, without delays.");
    }
    /** Called after the durable meeting transition, also observed by tick after recovery. */
    public static void beforeMeetingOpen(MinecraftServer server)throws IOException {
        var r=runtime(server);ready(r);if(!r.plan.isInitialized())validateSetup(server,r.settings.config());
    }
    public static void meetingChanged(MinecraftServer server)throws IOException {
        var r=runtime(server);ready(r);initialize(server,r);if(!finished(r))observe(server,r,true);
    }
    private static boolean finished(Runtime r){return r.plan.isInitialized()&&r.plan.snapshot().slots().stream().noneMatch(s->s.phase()==ComplicationService.Phase.WAITING||s.phase()==ComplicationService.Phase.CLAIMED);}
    public static void postTransitionFailed(MinecraftServer server){var r=WORLDS.get(server);if(r!=null)r.problem="Meeting transition saved. Exact complication records need recovery.";RuntimeHolds.hold(server,HOLD,"Meeting transition saved; reload the retained complication records before continuing.");}
    static boolean clockAdvances(Phase phase,boolean occupied,boolean held){return (phase==Phase.DISCUSSION||phase==Phase.RECESS)&&occupied&&!held;}
    private static void observe(MinecraftServer server,Runtime r,boolean force)throws IOException {
        if(!r.plan.isInitialized())return;
        var council=ProceedingsRuntime.state(server);int meeting=council.meeting();
        if(meeting==0)return;
        boolean advancing=clockAdvances(council.phase(),EventSources.activeOperation(server),RuntimeHolds.reason(server)!=null);
        long now=PlayRuntime.elapsedTicks(server);
        boolean transition=r.observedMeeting!=meeting||r.observedAdvance!=advancing||r.observedAt<0;
        if(!force&&!transition&&now-r.observedAt<200)return;
        // Parent clock becomes durable before the child records a reference to it.
        PlayRuntime.service(server).checkpoint();
        if(transition) {
            if(r.plan.snapshot().advancing())r.plan.observeClock(r.plan.currentMeeting(),now,true);
            r.plan.observeClock(meeting,now,false);
        }
        r.plan.observeClock(meeting,now,advancing);
        r.observedAt=now;r.observedMeeting=meeting;r.observedAdvance=advancing;
    }
    public static void beforeBoundary(MinecraftServer server,int meeting)throws IOException {
        var r=runtime(server);ready(r);initialize(server,r);if(finished(r))return;observe(server,r,true);
        if(!r.plan.isInitialized()||!r.plan.pendingCurrentMeeting(meeting))return;
        if(!EventSources.activeOperation(server))throw new IllegalStateException("Resume occupied active play before resolving this Council interruption.");
        ProceedingsRuntime.complicationRecess(server);
        try { deliverOne(server,r,meeting,true); }
        catch(IllegalArgumentException|IllegalStateException unavailable){r.problem="A complication needs its exact Event prerequisites or recovery. The saved selection is retained.";}
        ProceedingsRuntime.complicationChanged(server);
        throw new IllegalStateException(r.problem.isBlank()?"Council is in recess. Resume proceedings, then request the vote or meeting close again.":r.problem);
    }
    private static void deliverOne(MinecraftServer server,Runtime r,int meeting,boolean force)throws IOException {
        var due=r.plan.due(meeting,force);if(due.isEmpty())return;
        var claim=r.plan.claim(due.get().ordinal());
        // A saved Core draw remains authoritative even if its reusable pool was edited or deleted.
        var saved=EventPools.retainedDraw(server,claim.key());
        if(saved.isEmpty()) {
            var pool=EventPoolCatalogRuntime.definitions(server).get(r.config().poolId());
            if(pool==null)throw new IllegalStateException("The configured complication pool is missing.");
            EventPools.draw(server,pool,claim.key());
        }
        var result=EventPools.dispatch(server,claim.key());
        if(result==EventPools.Status.COMPLETE){markDelivered(server,r,claim.ordinal(),claim.key());}
        else if(result==EventPools.Status.MEDIA_UNCERTAIN||result==EventPools.Status.CONFLICT)
            r.problem="A complication needs exact Event recovery or delivery acknowledgement in Council complications.";
    }
    private static void tick(MinecraftServer server) {
        if(server.getTicks()%20!=0)return;
        var r=WORLDS.get(server);if(r==null)return;
        try {
            ready(r);var council=ProceedingsRuntime.state(server);
            if(council.meeting()==0)return;
            initialize(server,r);if(finished(r))return;observe(server,r,false);
            if(!r.plan.isInitialized())return;
            // Reconcile committed Core outcomes even while a technical pause prevents new effects.
            for(var slot:r.plan.snapshot().slots())if(slot.phase()==ComplicationService.Phase.CLAIMED) {
                String key=r.plan.snapshot().experience()+":"+slot.ordinal();
                var saved=EventPools.retainedDraw(server,key);
                if(saved.isPresent()&&EventPools.status(server,key)==EventPools.Status.COMPLETE){markDelivered(server,r,slot.ordinal(),key);}
            }
            if(!EventSources.activeOperation(server)||RuntimeHolds.reason(server)!=null||council.activeBallot()!=null)return;
            boolean recovering=r.plan.snapshot().slots().stream().anyMatch(s->s.meeting()==council.meeting()&&s.phase()==ComplicationService.Phase.CLAIMED);
            if(council.phase()==Phase.DISCUSSION||council.phase()==Phase.RECESS&&recovering)deliverOne(server,r,council.meeting(),false);
        }catch(IOException failure){RuntimeHolds.hold(server,HOLD,"Council complication records need recovery; retained selections were preserved.");}
        catch(IllegalArgumentException|IllegalStateException unavailable){r.problem="Council complications need valid setup or their exact Event prerequisites. No new draw replaces a saved selection.";}
    }
    public record Revealed(int meeting,String label,String message) { }
    public static List<Revealed> revealed(MinecraftServer server,UUID viewer) {
        var r=WORLDS.get(server);if(r==null)return List.of();
        List<Revealed> result=new ArrayList<>();
        try {
            ready(r);if(!r.plan.isInitialized())return List.of();
            for(var slot:r.plan.snapshot().slots())if(slot.phase()==ComplicationService.Phase.DELIVERED) {
                var draw=EventPools.retainedDraw(server,r.plan.snapshot().experience()+":"+slot.ordinal()).orElseThrow();
                var d=draw.configuration().definition();
                projectReveal(slot.meeting(),draw,viewer).ifPresent(result::add);
            }
        }catch(IOException|RuntimeException unavailable){return List.of();}
        return List.copyOf(result);
    }
    static Optional<Revealed> projectReveal(int meeting,io.github.chronicle.core.eventpool.EventPoolDraw draw,UUID viewer) {
        var d=draw.configuration().definition();
        if(d.audience()!=ObservationCatalog.Audience.PARTICIPANTS&&!draw.recipients().contains(viewer))return Optional.empty();
        return Optional.of(new Revealed(meeting,d.label(),d.text()));
    }
    private static void markDelivered(MinecraftServer server,Runtime r,int ordinal,String key)throws IOException {
        var draw=EventPools.retainedDraw(server,key).orElseThrow();
        r.plan.deliver(ordinal);r.problem="";
        var definition=draw.configuration().definition();
        // Public Council narration also reaches its non-House moderator, only after the reveal commits.
        if(definition.audience()==ObservationCatalog.Audience.PARTICIPANTS&&!definition.text().isBlank())
            for(var player:server.getPlayerManager().getPlayerList())if(CouncilRuntime.isChancellor(server,player.getUuid())&&!draw.recipients().contains(player.getUuid()))
                player.sendMessage(net.minecraft.text.Text.literal(definition.text()),false);
        ProceedingsRuntime.complicationChanged(server);
    }
    private static void request(ServerPlayerEntity player,Request q) {
        var server=player.getEntityWorld().getServer();
        if(!player.getCommandSource().hasPermissionLevel(2)||server.getPlayerManager().getPlayer(player.getUuid())!=player)return;
        String message="";
        try {
            var r=runtime(server);if(q.action()!=Action.RECOVER)ready(r);
            if(q.action()!=Action.OPEN&&q.revision()!=r.revision)throw new IllegalStateException("Setup changed. Reopen Council complications.");
            if(q.action()==Action.RECOVER) {
                EventPools.reload(server);
                RuntimeHolds.release(server,HOLD);start(server);r=runtime(server);ready(r);
                recoverEvent(player,r,false);message="Exact saved complication records reloaded. No selection was rerolled.";
            }else if(q.action()==Action.ACKNOWLEDGE){recoverEvent(player,r,true);message="Uncertain delivery acknowledged; its media was not replayed.";}
            else if(q.action()!=Action.OPEN) {
                requireEditing(player);
                if(q.action()==Action.SAVE) {
                    if(r.plan.isInitialized())throw new IllegalStateException("Scheduling has started; use the explicit future-opportunity controls.");
                    validateSetup(server,Objects.requireNonNull(q.config()));r.settings.save(q.config());message="Council complication settings saved.";
                }else if(q.action()==Action.CANCEL_FUTURE) {
                    if(!r.plan.isInitialized())throw new IllegalStateException("Edit the meeting list before scheduling starts.");
                    int current=ProceedingsRuntime.state(server).meeting();
                    var keep=q.remainingMeetings();
                    if(!keep.containsAll(r.config().meetings().stream().filter(m->m<=current).toList()))throw new IllegalStateException("Past and current meeting opportunities remain fixed.");
                    var future=keep.stream().filter(m->m>current).toList();
                    if(future.isEmpty()&&!q.reason().isBlank()) {
                        reconcileClaims(server,r);
                        if(r.plan.pendingCurrentMeeting(current))throw new IllegalStateException("Resolve this meeting's obligations before ending all future opportunities.");
                        r.plan.exceptionalEnd(q.reason());message="Future obligations recorded as undelivered, with your reason.";
                    }else {r.plan.cancelFuture(r.config(),current,future);message="Undelivered future obligations redistributed. No selection was revealed.";}
                }else if(q.action()==Action.EARLY_END) {
                    reconcileClaims(server,r);
                    r.plan.exceptionalEnd(q.reason());message="Remaining obligations recorded as undelivered, with your reason.";
                }
                r.revision=Math.incrementExact(r.revision);
            }
        }catch(IOException failure){RuntimeHolds.hold(server,HOLD,"Council complication setup needs recovery; retained files were preserved.");message="Data could not be read or saved; recover the retained records before continuing.";}
        catch(IllegalArgumentException|IllegalStateException refused){message=q.action()==Action.RECOVER||q.action()==Action.ACKNOWLEDGE?"Exact complication recovery is not ready. Restore its saved session and prerequisites.":Objects.toString(refused.getMessage(),"The action is unavailable.");}
        send(player,q.request(),q.action()==Action.OPEN,message);
    }
    private static void reconcileClaims(MinecraftServer server,Runtime r)throws IOException {
        if(!r.plan.isInitialized())return;
        for(var slot:r.plan.snapshot().slots())if(slot.phase()==ComplicationService.Phase.CLAIMED) {
            String key=r.plan.snapshot().experience()+":"+slot.ordinal();
            if(EventPools.retainedDraw(server,key).isPresent()&&EventPools.status(server,key)==EventPools.Status.COMPLETE)markDelivered(server,r,slot.ordinal(),key);
        }
    }
    private static void recoverEvent(ServerPlayerEntity player,Runtime r,boolean acknowledge)throws IOException {
        var server=player.getEntityWorld().getServer();reconcileClaims(server,r);
        if(!r.plan.isInitialized())return;
        var claimed=r.plan.snapshot().slots().stream().filter(s->s.phase()==ComplicationService.Phase.CLAIMED).findFirst();
        if(claimed.isEmpty()){if(acknowledge)throw new IllegalStateException("No uncertain delivery is pending.");return;}
        String key=r.plan.snapshot().experience()+":"+claimed.get().ordinal();
        var draw=EventPools.retainedDraw(server,key);
        if(draw.isEmpty())return;
        var status=EventPools.status(server,key);
        if(acknowledge) {
            if(status!=EventPools.Status.MEDIA_UNCERTAIN)throw new IllegalStateException("There is no uncertain delivery to acknowledge.");
            EventRuntime.acknowledge(player,draw.get().configuration().definition().id());
        }else if(status==EventPools.Status.CONFLICT||status==EventPools.Status.RUNNING)EventRuntime.recover(player,draw.get().configuration().definition().id());
        reconcileClaims(server,r);r.problem="";
    }
    private static void requireEditing(ServerPlayerEntity p) {
        if(!AuthoringRuntime.isAuthor(p)||!AuthoringRuntime.editable(p.getEntityWorld().getServer())||RuntimeHolds.reason(p.getEntityWorld().getServer())!=null)
            throw new IllegalStateException("Pause play and enable Editing before changing setup.");
    }
    private static void send(ServerPlayerEntity p,UUID request,boolean opening,String message) {
        if(!ServerPlayNetworking.canSend(p,View.ID))return;
        try {
            var r=runtime(p.getEntityWorld().getServer());
            var pools=EventPoolCatalogRuntime.definitions(p.getEntityWorld().getServer()).values().stream().sorted(Comparator.comparing(EventPoolDefinition::label)).map(d->new PoolChoice(d.id(),d.label())).toList();
            boolean editing=AuthoringRuntime.isAuthor(p)&&AuthoringRuntime.editable(p.getEntityWorld().getServer())&&RuntimeHolds.reason(p.getEntityWorld().getServer())==null;
            boolean started;ComplicationConfig config;boolean recoverable=!r.problem.isBlank(),uncertain=false;
            try{started=r.plan.isInitialized();config=r.config();
                if(started)for(var slot:r.plan.snapshot().slots())if(slot.phase()==ComplicationService.Phase.CLAIMED) {
                    String key=r.plan.snapshot().experience()+":"+slot.ordinal();
                    if(EventPools.retainedDraw(p.getEntityWorld().getServer(),key).isPresent()) {
                        var pending=EventPools.status(p.getEntityWorld().getServer(),key);uncertain=pending==EventPools.Status.MEDIA_UNCERTAIN;
                        recoverable|=pending==EventPools.Status.CONFLICT||pending==EventPools.Status.RUNNING||uncertain;
                    }
                }
            }catch(IOException|IllegalStateException unavailable){started=true;config=r.settings.config();editing=false;recoverable=true;}
            String status=!r.problem.isBlank()?r.problem:started&&config.total()==0?"Complications are disabled for this experience.":started?"Scheduling has started. Future opportunities can be revised explicitly.":"Configure random interruptions for Council meetings.";
            ServerPlayNetworking.send(p,new View(request,opening,r.revision,config,pools,editing,started,ProceedingsRuntime.state(p.getEntityWorld().getServer()).meeting(),recoverable,uncertain,status,message.substring(0,Math.min(message.length(),512))));
        }catch(RuntimeException unavailable){p.sendMessage(net.minecraft.text.Text.literal("Council complication records are unavailable; repair the retained files."),false);}
    }
}
