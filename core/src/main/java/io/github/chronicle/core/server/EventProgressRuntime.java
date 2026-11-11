package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.campaign.CampaignState;
import io.github.chronicle.core.play.*;
import io.github.chronicle.core.progression.SourceLedger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import java.io.IOException;
import java.util.*;

/** Durable source arming and countdowns. All real actions still execute through EventRuntime. */
public final class EventProgressRuntime {
    private static final String HOLD = "chronicle_core:event_progress";
    private static final Map<MinecraftServer, Runtime> WORLDS = new IdentityHashMap<>();
    private EventProgressRuntime() { }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var store = new EventProgressStore(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/event-progress.json"));
            try { WORLDS.put(server,new Runtime(store,store.load())); }
            catch (IOException failure) { WORLDS.put(server,new Runtime(store,EventProgressState.empty())); fail(server,failure); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
    }

    public static boolean interested(MinecraftServer server, EventSource source) {
        return interested(server, source, null, false);
    }
    public static boolean interested(MinecraftServer server, EventSource source, UUID actor) {
        return interested(server, source, actor, true);
    }
    private static boolean interested(MinecraftServer server, EventSource source, UUID actor, boolean actorKnown) {
        if (!WORLDS.containsKey(server)) return false;
        try {
            var play = PlayRuntime.service(server).state();
            var campaign=ChronicleRuntime.service(server).state();
            return EventRuntime.configurations(server).values().stream().anyMatch(c -> c.enabled()
                    && c.mode()==PlayState.Mode.SYSTEM && c.source().type().equals(source.type())
                    && (c.source().target().equals("*")||c.source().target().equals(source.target()))
                    && (c.source().activity().isEmpty()||c.source().activity().equals(source.activity()))
                    && c.requiredFlags().stream().allMatch(id->Boolean.TRUE.equals(campaign.flags().get(id)))
                    && (c.occurrencePolicy()!=EventOccurrencePolicy.ONCE&&(actorKnown?EventOccurrenceRuntime.interested(server,c,actor):EventOccurrenceRuntime.interested(server,c))
                    ||c.occurrencePolicy()==EventOccurrencePolicy.ONCE
                    && !play.executions().containsKey(c.definition().id()) && !play.deliveries().containsKey(c.definition().id())
                    && (entry(server,c)==null || !entry(server,c).triggered() && !entry(server,c).skipped())));
        } catch (EventSources.Unavailable unavailable) { throw unavailable; }
        catch (RuntimeException unavailable) { return false; }
    }

    public static EventProgressState.Entry entry(MinecraftServer server,EventConfiguration c) {
        var runtime=WORLDS.get(server);if(runtime==null)return null;
        var e=runtime.state.entries().get(c.definition().id());return e!=null&&e.fingerprint().equals(c.fingerprint())?e:null;
    }
    public static long revision(MinecraftServer server){var r=WORLDS.get(server);return r==null?0:r.state.revision();}
    static Set<String> reservedEventIds(MinecraftServer server){return Set.copyOf(runtime(server).state.entries().keySet());}

    public static void arm(MinecraftServer server,EventConfiguration c)throws IOException {
        var facts=c.mode()==PlayState.Mode.SYSTEM?EventSources.facts(server,c.source()):List.<EventSources.Occurrence>of();
        if(c.occurrencePolicy()!=EventOccurrencePolicy.ONCE)EventOccurrenceRuntime.baseline(server,c);
        var seen=new TreeSet<String>();facts.forEach(f->seen.add(f.id()));
        var campaign=ChronicleRuntime.service(server).state();
        PlayRuntime.service(server).checkpoint();
        put(server,new EventProgressState.Entry(c.definition().id(),c.fingerprint(),-1,false,null,
                PlayState.DeliverySource.valueOf(c.mode().name()),"",false,seen,versions(c,campaign),false));
    }

    public static void remove(MinecraftServer server,String id)throws IOException {
        var r=runtime(server);if(!r.state.entries().containsKey(id))return;
        var entries=new TreeMap<>(r.state.entries());entries.remove(id);save(server,new EventProgressState(r.state.revision()+1,entries));
    }

    public static boolean legacyReviewRequired(EventConfiguration c){return c.formatVersion()==1&&c.mode()==PlayState.Mode.TIMER&&!c.requiredFlags().isEmpty();}

    public static EventProgressState.Entry observe(MinecraftServer server,EventConfiguration c,
            UUID entrant,boolean eligible)throws IOException {
        if(c.occurrencePolicy()!=EventOccurrencePolicy.ONCE)throw new IllegalArgumentException("Repeating source Events use the occurrence queue");
        if(c.formatVersion()==1)return null;
        var e=entry(server,c);if(e==null){arm(server,c);e=entry(server,c);}
        var campaign=ChronicleRuntime.service(server).state();
        var seen=new TreeSet<>(e.seenFacts());boolean triggered=e.triggered();UUID actor=e.actor();
        String occurrence=e.occurrence();boolean origin=e.activeOrigin();var source=e.source();
        boolean gates=satisfied(c,campaign.flags());
        if(!triggered&&!e.skipped()) {
            if(c.mode()==PlayState.Mode.SYSTEM){
                for(var fact:EventSources.facts(server,c.source())){
                    if(!seen.add(fact.id()))continue;
                    if(!triggered&&fact.activeOrigin()&&satisfied(c,fact.flags())){
                        triggered=true;actor=fact.actor();occurrence=fact.id();origin=true;source=PlayState.DeliverySource.SYSTEM;
                    }
                }
            } else if(eligible&&gates&&(c.mode()==PlayState.Mode.TIMER||c.mode()==PlayState.Mode.CONDITION
                    ||c.mode()==PlayState.Mode.ENTRY&&entrant!=null)){
                triggered=true;actor=c.mode()==PlayState.Mode.ENTRY?entrant:null;source=PlayState.DeliverySource.valueOf(c.mode().name());
            }
        }
        e=new EventProgressState.Entry(e.eventId(),e.fingerprint(),e.startedAt(),triggered,actor,source,occurrence,origin,seen,e.offVersions(),e.skipped());
        e=EventCountdown.observe(e,campaign.flags(),versions(c,campaign),PlayRuntime.elapsedTicks(server),eligible);
        if(campaign.phase()==io.github.chronicle.core.campaign.SessionPhase.ENDED&&triggered&&origin&&(!c.definition().progressOnly()||c.delayTicks()>0))
            e=new EventProgressState.Entry(e.eventId(),e.fingerprint(),-1,triggered,actor,source,occurrence,origin,seen,e.offVersions(),true);
        put(server,e);return e;
    }

    public static void manual(MinecraftServer server,EventConfiguration c,UUID actor)throws IOException {
        var e=entry(server,c);if(e!=null&&e.triggered()&&!e.skipped())throw new IllegalStateException("This Event already has a pending countdown.");
        if(e==null){arm(server,c);e=entry(server,c);}
        put(server,new EventProgressState.Entry(e.eventId(),e.fingerprint(),PlayRuntime.elapsedTicks(server),true,actor,
                PlayState.DeliverySource.MANUAL,"",false,e.seenFacts(),versions(c,ChronicleRuntime.service(server).state()),false));
    }

    public static boolean due(MinecraftServer server,EventConfiguration c){var e=entry(server,c);return e!=null&&EventCountdown.due(e,c.delayTicks(),PlayRuntime.elapsedTicks(server));}
    public static boolean trustedCompletion(MinecraftServer server,EventConfiguration c){var e=entry(server,c);return e!=null&&e.triggered()&&e.activeOrigin()&&!e.skipped()&&c.definition().progressOnly()&&e.source()==PlayState.DeliverySource.SYSTEM;}
    static boolean observedByEverySubscriber(MinecraftServer server,SourceLedger.Receipt receipt){
        return observedByEverySubscriber(EventRuntime.configurations(server).values(),runtime(server).state.entries(),receipt);
    }
    static boolean observedByEverySubscriber(Collection<EventConfiguration> configurations,Map<String,EventProgressState.Entry> progress,SourceLedger.Receipt receipt){
        for(EventConfiguration configuration:configurations){
            if(configuration.mode()!=PlayState.Mode.SYSTEM||!matches(configuration.source(),receipt))continue;
            EventProgressState.Entry entry=progress.get(configuration.definition().id());
            if(entry==null||!entry.fingerprint().equals(configuration.fingerprint())||!entry.seenFacts().contains(receipt.id())&&!entry.occurrence().equals(receipt.id()))return false;
        }
        return true;
    }
    /** Retry-safe cleanup after source publication/pruning; occurrence/countdown pins are deliberately retained. */
    static boolean hasFactsMissingFromLiveLedger(MinecraftServer server,Set<String> liveIds){return runtime(server).state.entries().values().stream().flatMap(entry->entry.seenFacts().stream()).anyMatch(id->!liveIds.contains(id));}
    static void markFactSeen(MinecraftServer server,EventConfiguration configuration,String fact)throws IOException{
        EventProgressState.Entry entry=entry(server,configuration);
        if(entry==null)throw new IllegalStateException("Repeating Event is not armed");
        if(entry.seenFacts().contains(fact))return;
        if(entry.seenFacts().size()>=EventProgressState.Entry.MAX_FACTS)throw new IOException("Event source observation capacity reached; preserve evidence for maintenance");
        Set<String> facts=new TreeSet<>(entry.seenFacts());facts.add(fact);
        put(server,new EventProgressState.Entry(entry.eventId(),entry.fingerprint(),entry.startedAt(),entry.triggered(),entry.actor(),
                entry.source(),entry.occurrence(),entry.activeOrigin(),facts,entry.offVersions(),entry.skipped()));
    }
    static int trimFactsMissingFromLiveLedger(MinecraftServer server,Set<String> liveIds)throws IOException{
        Runtime runtime=runtime(server);int before=runtime.state.entries().values().stream().mapToInt(entry->entry.seenFacts().size()).sum();
        EventProgressState next=runtime.state.retainLiveFacts(liveIds);int removed=before-next.entries().values().stream().mapToInt(entry->entry.seenFacts().size()).sum();
        if(next!=runtime.state)save(server,next);
        return removed;
    }
    public static String status(MinecraftServer server,EventConfiguration c){
        if(legacyReviewRequired(c))return "Edit and save this old gated timer before running it; its unlock time was not recorded.";
        var e=entry(server,c);if(e==null)return "Not armed";if(e.skipped())return "Skipped: play ended. No effects or flags were applied.";
        if(!e.triggered())return "Waiting for trigger";if(e.startedAt()<0)return "Waiting for all required flags to be On";
        return "Countdown: "+Math.max(0,c.delayTicks()-Math.max(0,PlayRuntime.elapsedTicks(server)-e.startedAt()))/20+" active seconds remaining";
    }
    private static boolean satisfied(EventConfiguration c,Map<String,Boolean> flags){return c.requiredFlags().stream().allMatch(id->Boolean.TRUE.equals(flags.get(id)));}
    private static boolean matches(EventSource source,SourceLedger.Receipt receipt){return source.type().equals(receipt.type())&&(source.target().equals("*")||source.target().equals(receipt.target()))&&(source.activity().isEmpty()||source.activity().equals(receipt.activity()));}
    private static Map<String,Long> versions(EventConfiguration c,CampaignState campaign){var v=new TreeMap<String,Long>();c.requiredFlags().forEach(id->v.put(id,campaign.flagOffVersion(id)));return v;}
    private static void put(MinecraftServer server,EventProgressState.Entry e)throws IOException{
        var r=runtime(server);if(e.equals(r.state.entries().get(e.eventId())))return;
        PlayRuntime.service(server).checkpoint();var entries=new TreeMap<>(r.state.entries());entries.put(e.eventId(),e);save(server,new EventProgressState(r.state.revision()+1,entries));
    }
    private static void save(MinecraftServer server,EventProgressState state)throws IOException{var r=runtime(server);try{r.store.save(state);r.state=state;}catch(IOException failure){fail(server,failure);throw failure;}}
    private static Runtime runtime(MinecraftServer server){var r=WORLDS.get(server);if(r==null||r.failure!=null)throw new IllegalStateException("Event progression is unavailable; inspect its journal and server log.");return r;}
    private static void fail(MinecraftServer server,Exception failure){var r=WORLDS.get(server);if(r!=null)r.failure=failure.getMessage();RuntimeHolds.hold(server,HOLD,"Event progression evidence needs recovery.");ChronicleCore.LOGGER.error("Event progression stopped; evidence was preserved",failure);}
    private static final class Runtime{final EventProgressStore store;EventProgressState state;String failure;Runtime(EventProgressStore store,EventProgressState state){this.store=store;this.state=state;}}
}
