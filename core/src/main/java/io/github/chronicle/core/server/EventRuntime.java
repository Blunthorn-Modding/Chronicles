package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.areas.*;
import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.campaign.*;
import io.github.chronicle.core.menu.EventEditorForm;
import io.github.chronicle.core.play.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** The single live dispatcher for legacy cues and configured Events. All mutation is server-thread confined. */
public final class EventRuntime {
    public static final String HOLD="chronicle_core:events";
    private static final UUID AUTOMATIC_ACTOR=new UUID(0,0);
    private static final Set<String> OWN_HOLDS=Set.of(HOLD,EventWorldBatches.HOLD,"chronicle_core:chests");
    private static final Map<MinecraftServer,Runtime> WORLDS=new IdentityHashMap<>();
    private EventRuntime(){}

    public static void register(){
        ServerLifecycleEvents.SERVER_STARTED.register(EventRuntime::start);
        ServerTickEvents.END_SERVER_TICK.register(EventRuntime::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
    }

    public static Map<String,EventConfiguration> configurations(MinecraftServer server){
        PlayState state=PlayRuntime.service(server).state();Map<String,EventConfiguration> result=new TreeMap<>();
        for(var d:ChronicleCore.observations().definitions()){
            var b=state.bindings().get(d.id());
            Optional<EventRegionReference> pin=b==null||b.regionId().isEmpty()?Optional.empty():Optional.of(new EventRegionReference(b.regionId(),b.regionRevision()));
            var definition=new EventDefinition(d.id(),b==null?1:b.revision(),d.label(),"",d.text(),d.sound(),d.audience(),d.audience()==ObservationCatalog.Audience.REGION?pin:Optional.empty(),List.of());
            result.put(d.id(),new EventConfiguration(definition,b==null?PlayState.Mode.MANUAL:b.mode(),pin,b==null?0:b.armedAtTicks(),b==null?0:b.delayTicks(),b==null?"":b.requiredFlag(),b!=null));
        }
        result.putAll(state.events());return Collections.unmodifiableMap(result);
    }
    public static EventConfiguration configuration(MinecraftServer server,String id){var c=configurations(server).get(id);if(c==null)throw new IllegalArgumentException("Choose an available Event.");return c;}
    public static EventConfiguration stored(MinecraftServer server,String id){return PlayRuntime.service(server).state().events().get(id);}

    /** Resolves every selector pin on the server; client display labels/current flags are never authority. */
    public static String save(ServerPlayerEntity player,EventEditorForm form,EventConfiguration expected,boolean creating)throws IOException{
        MinecraftServer server=server(player);requireEditing(player);var service=PlayRuntime.service(server);
        if(form.creating()!=creating)throw new IllegalStateException("The Event editor changed; reopen it.");
        String id=form.id();if(creating&&id.isBlank())id="chronicle_core:event_"+UUID.randomUUID().toString().replace("-","");
        if(creating&&(configurations(server).containsKey(id)||configurations(server).size()>=ObservationCatalog.MAX_DEFINITIONS))throw new IllegalStateException("That Event already exists, or the Event limit has been reached.");
        if(!creating){var current=configuration(server,id);if(current.definition().revision()!=form.revision())throw new IllegalStateException("The Event changed; reopen it.");}
        Optional<EventRegionReference> audience=pin(server,form.audienceRegion(),form.audienceRegionRevision());
        Optional<EventRegionReference> trigger=pin(server,form.triggerRegion(),form.triggerRegionRevision());
        var audienceType=ObservationCatalog.Audience.valueOf(form.audience());
        if(audienceType==ObservationCatalog.Audience.PARTICIPANTS)audience=Optional.empty();
        List<EventRegionChange> changes=new ArrayList<>();
        for(var choice:form.changes()){
            var area=AreaChanges.forRegion(server,choice.regionId()).orElseThrow(()->new IllegalStateException("Saved states are missing for Region "+choice.regionId()));
            AreaState state=AreaState.valueOf(choice.state());var snapshot=area.requireSnapshot(state);
            if(!area.bounds().matches(AreaChanges.currentRegion(server,area))||area.revision()!=choice.areaRevision()||!snapshot.sha256().equals(choice.snapshotHash()))throw new IllegalStateException("A saved Region state changed; select it again.");
            changes.add(new EventRegionChange(new EventRegionReference(area.bounds().regionId(),area.bounds().regionRevision()),area.id(),area.revision(),state,snapshot));
        }
        var definition=new EventDefinition(id,service.nextEventRevision(),form.name(),form.description(),form.message(),form.sound(),audienceType,audience,changes);
        sound(definition.sound());validateFlag(server,form.flag());
        var mode=PlayState.Mode.valueOf(form.mode());long delay=mode==PlayState.Mode.TIMER?Math.multiplyExact(form.seconds(),20):0;
        var configuration=new EventConfiguration(definition,mode,trigger,service.elapsedTicks(),delay,form.flag(),true);
        write(server,()->service.putEvent(configuration,expected,creating));
        return definition.id();
    }
    public static void delete(ServerPlayerEntity player,String id,EventConfiguration expected)throws IOException{
        requireEditing(player);var server=server(player);if(ChronicleCore.observations().get(id).isPresent())throw new IllegalStateException("Default Events retain their registered definition; remove their configuration instead.");
        write(server,()->PlayRuntime.service(server).deleteEvent(id,expected));
    }
    public static void disableAutomatic(ServerPlayerEntity player,String id)throws IOException{changeEnabled(player,id,true);}
    public static void removeConfiguration(ServerPlayerEntity player,String id)throws IOException{
        requireEditing(player);var server=server(player);
        if(ChronicleCore.observations().get(id).isEmpty())throw new IllegalStateException("Custom Events have their own definition; delete the Event to remove it.");
        var expected=stored(server,id);write(server,()->{var service=PlayRuntime.service(server);if(expected!=null)service.deleteEvent(id,expected);else service.off(id);});
    }
    private static void changeEnabled(ServerPlayerEntity player,String id,boolean enabled)throws IOException{
        requireEditing(player);var server=server(player);var old=configuration(server,id);var service=PlayRuntime.service(server);var d=old.definition();
        var next=new EventDefinition(d.id(),service.nextEventRevision(),d.label(),d.description(),d.text(),d.sound(),d.audience(),d.audienceRegion(),d.changes());
        var configuration=new EventConfiguration(next,PlayState.Mode.MANUAL,old.triggerRegion(),service.elapsedTicks(),0,old.requiredFlag(),enabled);
        var expected=stored(server,id);write(server,()->service.putEvent(configuration,expected,false));
    }

    public static void previewMedia(ServerPlayerEntity player,EventEditorForm form){requireOperator(player);media(player,form.message(),sound(form.sound()));}
    public static void previewMedia(ServerPlayerEntity player,String id){requireOperator(player);var d=configuration(server(player),id).definition();media(player,d.text(),sound(d.sound()));}
    public static void showBounds(ServerPlayerEntity player,String regionId,String state){
        requireOperator(player);var server=server(player);var area=AreaChanges.forRegion(server,regionId).orElseThrow(()->new IllegalStateException("That Region has no saved states."));
        area.requireSnapshot(AreaState.valueOf(state));if(!area.bounds().matches(AreaChanges.currentRegion(server,area)))throw new IllegalStateException("That Region moved; recapture its saved states.");
        AuthoringRuntime.get(server).preview(player,region(server,new EventRegionReference(regionId,area.bounds().regionRevision())));
    }

    public record Review(EventConfiguration configuration,int session,List<UUID> recipients,long createdAtNanos,String description){public Review{recipients=List.copyOf(recipients);}}
    public static Review review(MinecraftServer server,String id){
        runtime(server);requireNoPending(server);if(RuntimeHolds.reason(server)!=null)throw new IllegalStateException("Resolve the current operation before reviewing an Event.");
        var c=configuration(server,id);validate(server,c);var campaign=active(server);requireFlag(c,campaign);unclaimed(server,id);
        var recipients=audience(server,c);if(recipients.isEmpty())throw new IllegalStateException("No participating players are in this Event's audience.");
        String names=recipients.stream().limit(8).map(p->p.getName().getString()).reduce((a,b)->a+", "+b).orElse("");
        return new Review(c,campaign.sessionNumber(),ids(recipients),System.nanoTime(),c.definition().label()+": "+recipients.size()+" recipients ("+names+"). "+c.definition().changes().size()+" saved Region changes. All targets are checked before any change.");
    }
    public static void run(ServerPlayerEntity player,String id,Review review)throws IOException{
        requireOperator(player);var server=server(player);if(review==null||!review.configuration().definition().id().equals(id))throw new IllegalStateException("Review this Event before running it.");
        long age=System.nanoTime()-review.createdAtNanos();if(age<0||age>TimeUnit.SECONDS.toNanos(30))throw new IllegalStateException("The review expired; review it again.");
        var current=review(server,id);if(!current.configuration().equals(review.configuration())||current.session()!=review.session()||!current.recipients().equals(review.recipients()))throw new IllegalStateException("The Event or its audience changed; review it again.");
        begin(server,current.configuration(),PlayState.DeliverySource.MANUAL,player.getUuid(),current.recipients());
    }

    /** Called only by PlayRuntime's occupied, active, unheld clock. There is no second trigger scheduler. */
    static void automatic(MinecraftServer server)throws IOException{
        Runtime r=WORLDS.get(server);if(r==null||r.failure!=null||r.preparing!=null||hasPending(server)||!EventWorldBatches.ready(server))return;
        long clock=PlayRuntime.elapsedTicks(server);if(clock<r.retryAt)return;var campaign=active(server);
        for(var c:configurations(server).values()){
            String id=c.definition().id();var state=PlayRuntime.service(server).state();
            if(!c.enabled()||c.mode()==PlayState.Mode.MANUAL||state.deliveries().containsKey(id)||state.executions().containsKey(id))continue;
            try{
                validate(server,c);if(!flagSatisfied(c,campaign))continue;
                var entrants=c.triggerRegion().isPresent()?PlayRuntime.eligiblePlayers(server).stream().filter(p->inside(p,region(server,c.triggerRegion().get()))).toList():List.<ServerPlayerEntity>of();
                boolean due=switch(c.mode()){case MANUAL->false;case ENTRY->!entrants.isEmpty();case TIMER->clock>=c.armedAtTicks()&&clock-c.armedAtTicks()>=c.delayTicks();case CONDITION->true;};
                if(!due)continue;var recipients=audience(server,c);if(recipients.isEmpty())continue;
                var source=PlayState.DeliverySource.valueOf(c.mode().name());UUID actor=source==PlayState.DeliverySource.ENTRY?entrants.getFirst().getUuid():null;
                begin(server,c,source,actor,ids(recipients));return;
            }catch(IllegalArgumentException|IllegalStateException refused){
                String key=c.definition().revision()+":"+PlayRuntime.message(refused);if(!key.equals(r.invalid.put(id,key)))ChronicleCore.LOGGER.warn("Event {} is not ready: {}",id,PlayRuntime.message(refused));
            }
        }
    }

    private static void begin(MinecraftServer server,EventConfiguration c,PlayState.DeliverySource source,UUID actor,List<UUID> recipients)throws IOException{
        Runtime r=runtime(server);requireNoPending(server);unclaimed(server,c.definition().id());var campaign=active(server);validate(server,c);requireFlag(c,campaign);
        UUID operation=UUID.randomUUID();int session=campaign.sessionNumber();
        if(c.definition().changes().isEmpty()){
            EventExecution execution=new EventExecution(c.definition().id(),operation,c,EventExecution.Phase.EVENT_OUTCOME,source,actor,session,PlayRuntime.elapsedTicks(server),recipients,"","");
            write(server,()->PlayRuntime.service(server).beginExecution(execution));RuntimeHolds.hold(server,HOLD,"An Event is completing its outcome.");finishMedia(server,execution);return;
        }
        r.preparing=operation;RuntimeHolds.hold(server,HOLD,"An Event is checking its saved Region changes.");
        var eligible=(java.util.function.BooleanSupplier)()->eligible(server,c,session,source,actor,true)&&ids(audience(server,c)).equals(recipients);
        try{
            EventWorldBatches.preflight(server,operation,actor==null?AUTOMATIC_ACTOR:actor,c.definition().changes(),eligible,prepared->{
                r.preparing=null;
                try{
                    if(!eligible.getAsBoolean())throw new IllegalStateException("Event conditions changed during preparation.");
                    EventExecution execution=new EventExecution(c.definition().id(),operation,c,EventExecution.Phase.PREPARED,source,actor,session,PlayRuntime.elapsedTicks(server),recipients,prepared.sha256(),"");
                    write(server,()->PlayRuntime.service(server).beginExecution(execution));
                    EventWorldBatches.commit(server,operation,prepared.sha256(),()->eligible(server,c,session,source,actor,false));r.attached=operation;
                }catch(Exception failure){
                    if(r.failure!=null){notifyActor(server,actor,"Event persistence needs recovery; its evidence is retained.");return;}
                    if(PlayRuntime.service(server).state().executions().containsKey(c.definition().id())){
                        if(failure instanceof IOException)fail(server,failure);
                        else{var execution=PlayRuntime.service(server).state().executions().get(c.definition().id());try{advance(server,execution,EventExecution.Phase.CONFLICT,PlayRuntime.message(failure));}catch(IOException persistenceFailure){fail(server,persistenceFailure);}}
                    }else{EventWorldBatches.cancelUncommitted(server,operation);RuntimeHolds.release(server,HOLD);r.retryAt=PlayRuntime.elapsedTicks(server)+200;}
                    notifyActor(server,actor,"Event could not start: "+PlayRuntime.message(failure));
                }
            },reason->{r.preparing=null;r.retryAt=PlayRuntime.elapsedTicks(server)+200;RuntimeHolds.release(server,HOLD);notifyActor(server,actor,"Event made no changes: "+reason);});
        }catch(RuntimeException failure){r.preparing=null;RuntimeHolds.release(server,HOLD);throw failure;}
    }

    private static void start(MinecraftServer server){
        var r=new Runtime();WORLDS.put(server,r);
        try{
            var pending=pending(server);if(pending.size()>1)throw new IllegalStateException("Multiple unfinished Event runs require stopped-world repair.");
            if(!pending.isEmpty())RuntimeHolds.hold(server,HOLD,"An Event is awaiting exact recovery.");
            for(var execution:pending)if(execution.phase()==EventExecution.Phase.MEDIA_CLAIMED){
                PlayRuntime.service(server).advanceExecution(execution.eventId(),execution.operationId(),EventExecution.Phase.MEDIA_UNCERTAIN,"Restart after media claim; delivery is uncertain and will not be replayed.");RuntimeHolds.release(server,HOLD);
            }
        }catch(Exception failure){fail(server,failure);}
    }
    private static void tick(MinecraftServer server){
        Runtime r=WORLDS.get(server);if(r==null||r.failure!=null||r.preparing!=null)return;
        try{
            var pending=pending(server);if(pending.isEmpty())return;EventExecution e=pending.getFirst();
            if(e.phase()==EventExecution.Phase.MEDIA_CLAIMED){advance(server,e,EventExecution.Phase.MEDIA_UNCERTAIN,"Media was claimed but final delivery is uncertain; no automatic replay.");RuntimeHolds.release(server,HOLD);return;}
            if(e.phase()==EventExecution.Phase.EVENT_OUTCOME){if(eligible(server,e.configuration(),e.session(),e.source(),e.actor(),false))finishMedia(server,e);return;}
            if(e.phase()==EventExecution.Phase.CONFLICT)return;
            if(!EventWorldBatches.ready(server))return;
            var status=EventWorldBatches.status(server,e.operationId());
            if(status.isPresent()&&status.get()==EventWorldBatches.Status.CONFLICT){advance(server,e,EventExecution.Phase.CONFLICT,EventWorldBatches.problem(server));return;}
            if(status.isPresent()&&status.get()==EventWorldBatches.Status.WORLD_COMMITTED){
                if(e.phase()!=EventExecution.Phase.WORLD_COMMITTED)e=advance(server,e,EventExecution.Phase.WORLD_COMMITTED,"All Region terrain and aggregate metadata committed.");
                advance(server,e,EventExecution.Phase.EVENT_OUTCOME,"");return;
            }
            if(!eligible(server,e.configuration(),e.session(),e.source(),e.actor(),false))return;
            if(!e.operationId().equals(r.attached))attach(server,e);
            status=EventWorldBatches.status(server,e.operationId());
            if(status.isPresent()&&status.get()==EventWorldBatches.Status.APPLYING&&e.phase()==EventExecution.Phase.PREPARED)advance(server,e,EventExecution.Phase.APPLYING,"");
        }catch(Exception failure){fail(server,failure);}
    }
    private static void attach(MinecraftServer server,EventExecution e)throws IOException{
        var callback=(java.util.function.BooleanSupplier)()->eligible(server,e.configuration(),e.session(),e.source(),e.actor(),false);
        if(EventWorldBatches.status(server,e.operationId()).isEmpty())EventWorldBatches.commit(server,e.operationId(),e.worldPlanHash(),callback);
        else EventWorldBatches.resume(server,e.operationId(),e.worldPlanHash(),callback);
        runtime(server).attached=e.operationId();
    }
    public static void recover(ServerPlayerEntity player,String id)throws IOException{
        requireOperator(player);var server=server(player);Runtime r=runtime(server);var e=PlayRuntime.service(server).state().executions().get(id);
        if(e==null||e.terminal()||e.phase()==EventExecution.Phase.MEDIA_CLAIMED)throw new IllegalStateException("That Event has no recoverable world operation.");
        // Recovery may be supervised by another live GM; retain the original actor in immutable run evidence.
        if(!eligible(server,e.configuration(),e.session(),e.source(),e.actor(),false))throw new IllegalStateException("Restore active play, original operator authority and prerequisites before recovering this Event.");
        if(e.phase()==EventExecution.Phase.CONFLICT){attach(server,e);advance(server,e,EventExecution.Phase.PREPARED,"Exact recovery requested by "+player.getUuidAsString());}
        else if(e.phase()!=EventExecution.Phase.EVENT_OUTCOME&&e.phase()!=EventExecution.Phase.WORLD_COMMITTED)attach(server,e);
        r.attached=e.operationId();
    }
    public static void acknowledge(ServerPlayerEntity player,String id)throws IOException{
        requireOperator(player);var server=server(player);write(server,()->PlayRuntime.service(server).acknowledgeEventMedia(id));
    }

    private static void finishMedia(MinecraftServer server,EventExecution e)throws IOException{
        if(!eligible(server,e.configuration(),e.session(),e.source(),e.actor(),false))return;
        SoundEvent sound=sound(e.configuration().definition().sound());
        var eligible=PlayRuntime.eligiblePlayers(server);var pinnedRecipients=e.recipients();var recipients=eligible.stream().filter(p->pinnedRecipients.contains(p.getUuid())).toList();
        e=advance(server,e,EventExecution.Phase.MEDIA_CLAIMED,"Media claim persisted before network delivery.");
        try{
            for(var player:recipients)media(player,e.configuration().definition().text(),sound);
            advance(server,e,EventExecution.Phase.COMPLETE,"Outcome committed; media sent to "+recipients.size()+" online eligible recipients.");
            RuntimeHolds.release(server,HOLD);
        }catch(RuntimeException failure){advance(server,e,EventExecution.Phase.MEDIA_UNCERTAIN,"Media delivery failed after its durable claim; no automatic replay.");RuntimeHolds.release(server,HOLD);ChronicleCore.LOGGER.error("Event media delivery is uncertain; acknowledge it before deliberately allowing another run",failure);}
    }
    private static EventExecution advance(MinecraftServer server,EventExecution e,EventExecution.Phase phase,String detail)throws IOException{
        try{return PlayRuntime.service(server).advanceExecution(e.eventId(),e.operationId(),phase,detail);}catch(IOException failure){fail(server,failure);throw failure;}
    }
    private static boolean eligible(MinecraftServer server,EventConfiguration c,int session,PlayState.DeliverySource source,UUID actor,boolean initial){
        try{
            var campaign=active(server);if(campaign.sessionNumber()!=session||!flagSatisfied(c,campaign)||RuntimeHolds.heldByOthers(server,OWN_HOLDS))return false;
            if(!configuration(server,c.definition().id()).equals(c))return false;
            if(source==PlayState.DeliverySource.MANUAL){var player=server.getPlayerManager().getPlayer(actor);if(player==null||!player.getCommandSource().hasPermissionLevel(2))return false;}
            if(PlayRuntime.eligiblePlayers(server).isEmpty())return false;
            if(initial)validate(server,c);return true;
        }catch(RuntimeException failure){return false;}
    }
    private static void validate(MinecraftServer server,EventConfiguration c){
        if(!c.enabled())throw new IllegalStateException("Configure this Event before running it.");
        if(stored(server,c.definition().id())==null){var old=ChronicleCore.observations().get(c.definition().id()).orElseThrow();PlayRuntime.service(server).validate(old,AuthoringRuntime.get(server).service().state(),c.definition().id());}
        c.definition().audienceRegion().ifPresent(p->region(server,p));c.triggerRegion().ifPresent(p->region(server,p));sound(c.definition().sound());validateFlag(server,c.requiredFlag());
        for(var change:c.definition().changes()){
            region(server,change.region());var area=AreaChanges.forRegion(server,change.region().id()).orElseThrow(()->new IllegalStateException("Saved Region states are missing."));
            if(!area.id().equals(change.areaId())||area.revision()!=change.areaRevision()||!area.bounds().matches(AreaChanges.currentRegion(server,area))||!area.requireSnapshot(change.state()).equals(change.snapshot()))throw new IllegalStateException("A saved Region target changed; edit the Event to select it again.");
        }
    }
    private static Optional<EventRegionReference> pin(MinecraftServer server,String id,long revision){if(id.isEmpty())return Optional.empty();var pin=new EventRegionReference(id,revision);region(server,pin);return Optional.of(pin);}
    private static AuthoringSite.Region region(MinecraftServer server,EventRegionReference pin){var site=AuthoringRuntime.get(server).service().state().sites().get(pin.id());if(!(site instanceof AuthoringSite.Region region)||region.revision()!=pin.revision())throw new IllegalStateException("Region is missing or changed: "+pin.id());return region;}
    private static List<ServerPlayerEntity> audience(MinecraftServer server,EventConfiguration c){var players=PlayRuntime.eligiblePlayers(server);return c.definition().audience()==ObservationCatalog.Audience.PARTICIPANTS?players:players.stream().filter(p->inside(p,region(server,c.definition().audienceRegion().orElseThrow(()->new IllegalStateException("Choose an audience Region."))))).toList();}
    private static boolean inside(ServerPlayerEntity player,AuthoringSite.Region region){var p=player.getBlockPos();return region.dimension().equals(player.getEntityWorld().getRegistryKey().getValue().toString())&&region.contains(new BlockPosition(p.getX(),p.getY(),p.getZ()));}
    private static List<UUID> ids(List<ServerPlayerEntity> players){return players.stream().map(ServerPlayerEntity::getUuid).toList();}
    private static SoundEvent sound(String value){if(value.isEmpty())return null;var id=Identifier.tryParse(value);if(id==null)throw new IllegalArgumentException("Choose a valid sound ID.");return Registries.SOUND_EVENT.getOptionalValue(id).orElseThrow(()->new IllegalArgumentException("Unknown sound: "+value));}
    private static void media(ServerPlayerEntity player,String text,SoundEvent sound){if(text==null||text.length()>512||text.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'))throw new IllegalArgumentException("Event messages allow up to 512 plain-text characters.");if(!text.isBlank())player.sendMessage(Text.literal(text),false);if(sound!=null)player.playSoundToPlayer(sound,SoundCategory.AMBIENT,1F,1F);}
    private static void validateFlag(MinecraftServer server,String id){if(!id.isEmpty()&&!ChronicleRuntime.service(server).state().flags().containsKey(id))throw new IllegalArgumentException("Choose an existing condition flag.");}
    private static boolean flagSatisfied(EventConfiguration c,CampaignState campaign){return c.requiredFlag().isEmpty()||campaign.flags().getOrDefault(c.requiredFlag(),false);}
    private static void requireFlag(EventConfiguration c,CampaignState campaign){if(!flagSatisfied(c,campaign))throw new IllegalStateException("The Event's required condition is not satisfied.");}
    private static CampaignState active(MinecraftServer server){var state=ChronicleRuntime.service(server).state();if(state.phase()!=SessionPhase.ACTIVE)throw new IllegalStateException("Event runs require active play.");return state;}
    private static void unclaimed(MinecraftServer server,String id){var state=PlayRuntime.service(server).state();if(state.deliveries().containsKey(id)||state.executions().containsKey(id))throw new IllegalStateException("This Event has already run or is pending; allow it again in Editing after resolving it.");}
    public static boolean hasPending(MinecraftServer server){var r=WORLDS.get(server);return r==null||r.failure!=null||r.preparing!=null||!pending(server).isEmpty();}
    public static void requireNoPending(MinecraftServer server){if(hasPending(server))throw new IllegalStateException("Finish or recover the current Event before changing play boundaries or starting another Event.");}
    private static List<EventExecution> pending(MinecraftServer server){return PlayRuntime.service(server).state().executions().values().stream().filter(e->!e.terminal()).toList();}
    private static Runtime runtime(MinecraftServer server){var r=WORLDS.get(server);if(r==null||r.failure!=null)throw new IllegalStateException("Events are unavailable; inspect the server log and retained execution evidence.");return r;}
    private static MinecraftServer server(ServerPlayerEntity player){return player.getEntityWorld().getServer();}
    private static void requireOperator(ServerPlayerEntity player){if(player==null||!player.getCommandSource().hasPermissionLevel(2))throw new IllegalStateException("Events require operator permission.");}
    private static void requireEditing(ServerPlayerEntity player){requireOperator(player);var server=server(player);if(!AuthoringRuntime.isAuthor(player)||!AuthoringRuntime.editable(server)||RuntimeHolds.reason(server)!=null)throw new IllegalStateException("Event edits require non-active operator Editing and no pending operation.");}
    private static void notifyActor(MinecraftServer server,UUID actor,String text){if(actor!=null){var player=server.getPlayerManager().getPlayer(actor);if(player!=null)player.sendMessage(Text.literal(text),false);}}
    private static void write(MinecraftServer server,Write action)throws IOException{try{action.run();}catch(IOException failure){fail(server,failure);throw failure;}}
    private static void fail(MinecraftServer server,Exception failure){var r=WORLDS.get(server);if(r!=null)r.failure=PlayRuntime.message(failure);RuntimeHolds.hold(server,HOLD,"An Event needs recovery; execution evidence has been preserved.");ChronicleCore.LOGGER.error("Event coordination stopped; no automatic replay",failure);}
    private static final class Runtime{UUID preparing,attached;long retryAt;String failure;final Map<String,String> invalid=new HashMap<>();}
    @FunctionalInterface private interface Write{void run()throws IOException;}
}
