package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.play.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.util.*;

/** Durable claim queue for the two explicitly repeating, media-only Event policies. */
public final class EventOccurrenceRuntime {
    public static final String HOLD = "chronicle_core:event_occurrences";
    public static final String BACKPRESSURE = "chronicle_core:event_occurrence_capacity";
    private static final Map<MinecraftServer, Runtime> WORLDS = new IdentityHashMap<>();
    private EventOccurrenceRuntime() { }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(EventOccurrenceRuntime::start);
        ServerTickEvents.END_SERVER_TICK.register(EventOccurrenceRuntime::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
    }

    private static void start(MinecraftServer server) {
        EventOccurrenceStore store = new EventOccurrenceStore(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/event-occurrences.json"));
        try { WORLDS.put(server, new Runtime(store, store.load())); refreshPressure(server, runtime(server)); }
        catch (IOException failure) { WORLDS.put(server, new Runtime(store, EventOccurrenceState.empty())); fail(server, failure); }
    }

    /** Archives all currently visible matching facts before a new fingerprint becomes runnable. */
    public static void baseline(MinecraftServer server, EventConfiguration configuration) throws IOException {
        if (configuration.occurrencePolicy() == EventOccurrencePolicy.ONCE) return;
        requireNoPending(server, configuration.definition().id());
        Runtime runtime = runtime(server);
        for (EventSources.Occurrence fact : EventSources.facts(server, configuration.source())) {
            if (liveClaim(runtime.state, configuration.definition().id(), fact.id()) != null
                    || runtime.store.decision(configuration.definition().id(), fact.id()).isPresent()) continue;
            runtime.store.publishDecision(new EventOccurrenceState.Decision(EventOccurrenceState.Decision.Kind.BASELINE,
                    configuration.definition().id(), configuration.fingerprint(), snapshot(fact), null, List.of()));
        }
    }

    public static boolean interested(MinecraftServer server, EventConfiguration configuration) {
        if (configuration.occurrencePolicy() == EventOccurrencePolicy.ONCE) return false;
        Runtime runtime = WORLDS.get(server);
        return runtime != null && runtime.failure == null && configuration.enabled();
    }

    static boolean interested(MinecraftServer server, EventConfiguration configuration, UUID actor) {
        if (!interested(server, configuration)) return false;
        if (configuration.occurrencePolicy() != EventOccurrencePolicy.ONCE_PER_ACTOR) return true;
        if (actor == null) return false;
        Runtime runtime = runtime(server);
        if (runtime.state.actors().containsKey(EventOccurrenceState.actorKey(configuration.definition().id(), actor))) return false;
        try { return runtime.store.actor(configuration.definition().id(), actor).isEmpty(); }
        catch (IOException failure) {
            fail(server, failure);
            throw new EventSources.Unavailable("Per-player Event history needs repair before recording more activity", failure);
        }
    }

    /** Reload exact repaired evidence only; this never discards a claim or acknowledges uncertain media. */
    public static void reload(net.minecraft.server.command.ServerCommandSource source) throws IOException {
        MinecraftServer server = source.getServer();
        if (!source.hasPermissionLevel(2) || ChronicleRuntime.service(server).state().phase() == SessionPhase.ACTIVE
                || source.getPlayer() == null || !AuthoringRuntime.isAuthor(source.getPlayer()))
            throw new IllegalStateException("Pause or stop play and enable Editing as an operator before reloading Event occurrence evidence.");
        if (RuntimeHolds.heldByOthers(server, Set.of(HOLD,BACKPRESSURE))) throw new IllegalStateException("Resolve the other operation hold first.");
        Runtime runtime = WORLDS.get(server);
        if (runtime == null) throw new IllegalStateException("Event occurrence service is unavailable");
        EventOccurrenceState loaded = runtime.store.load();
        for (var claim : loaded.claims().values()) {
            EventExecution live = PlayRuntime.service(server).state().executions().get(claim.eventId());
            if (live != null && live.operationId().equals(claim.operationId())) requireExact(claim, live);
            else {
                var archived = PlayRuntime.service(server).archivedExecution(claim.operationId());
                if (archived.isPresent()) requireExact(claim, archived.get());
                else if (claim.stage() != EventOccurrenceState.Stage.CLAIMED)
                    throw new IOException("Restore the missing exact execution/archive before reloading this occurrence");
            }
            if (!EventRuntime.configuration(server, claim.eventId()).fingerprint().equals(claim.configurationFingerprint()))
                throw new IOException("Restore the pinned Event configuration before reloading this occurrence");
        }
        ChronicleRuntime.service(server).recordOperation(new io.github.chronicle.core.campaign.Actor(source.getName(), source.getPlayer().getUuidAsString()),
                "event.occurrence.reload", HOLD, Map.of("revision", Long.toString(loaded.revision())));
        runtime.state = loaded; runtime.failure = null; RuntimeHolds.release(server, HOLD);refreshPressure(server,runtime);
    }

    public static void requireNoPending(MinecraftServer server, String eventId) {
        if (runtime(server).state.claims().values().stream().anyMatch(claim -> claim.eventId().equals(eventId))) {
            throw new IllegalStateException("Finish this Event's queued source occurrence before editing, disabling, deleting, or resetting it.");
        }
    }

    public static boolean hasPending(MinecraftServer server) {
        Runtime runtime = WORLDS.get(server);
        return runtime == null || runtime.failure != null || !runtime.state.claims().isEmpty();
    }

    /** Explicit non-active maintenance; actor identities are archived, never discarded. */
    static int maintainActors(MinecraftServer server) throws IOException {
        Runtime runtime = runtime(server);
        if (!runtime.state.claims().isEmpty()) throw new IllegalStateException("Queued Event occurrences cannot be maintained");
        for (EventOccurrenceState.ActorConsumption actor : runtime.state.actors().values()) runtime.store.publishActor(actor);
        int count = runtime.state.actors().size();
        if (count > 0) save(server, new EventOccurrenceState(Math.incrementExact(runtime.state.revision()), Map.of(), Map.of()));
        return count;
    }

    static int liveClaims(MinecraftServer server) { return runtime(server).state.claims().size(); }
    static int liveActors(MinecraftServer server) { return runtime(server).state.actors().size(); }
    public static boolean onlyBackpressureHold(MinecraftServer server) {
        Runtime runtime=WORLDS.get(server);
        return runtime!=null&&runtime.pressureHeld&&!RuntimeHolds.heldByOther(server,BACKPRESSURE);
    }
    static boolean atCapacity(EventOccurrenceState state) {
        return state.claims().size()>=EventOccurrenceState.MAX_LIVE_CLAIMS||state.actors().size()>=EventOccurrenceState.MAX_LIVE_ACTORS;
    }
    private static void refreshPressure(MinecraftServer server,Runtime runtime) {
        if(!atCapacity(runtime.state)){if(runtime.pressureHeld)RuntimeHolds.release(server,BACKPRESSURE);runtime.pressureHeld=false;return;}
        // Another in-flight operation already stops intake. Do not obstruct its exact completion.
        if(RuntimeHolds.heldByOther(server,BACKPRESSURE))return;
        if(runtime.pressureHeld)return;
        runtime.pressureHeld=true;
        RuntimeHolds.hold(server,BACKPRESSURE,"Repeating Event capacity is full. Restore queued prerequisites under Flags, or pause and archive player cue history when the queue is empty. Existing cues drain before new intake.");
    }

    private static void tick(MinecraftServer server) {
        Runtime runtime = WORLDS.get(server);
        if (runtime == null || runtime.failure != null) return;
        try {
            drain(server, runtime);
            scan(server, runtime);
            refreshPressure(server,runtime);
        } catch (EventSources.Unavailable failure) { /* The source owner already holds exact recovery. */ }
        catch (Exception failure) { fail(server, failure); }
    }
    static void collectForTrial(MinecraftServer server)throws IOException {
        if(!"true".equals(System.getenv("CHRONICLE_FOUNDATION_TRIAL"))||server.isOnlineMode()||server.getServerPort()!=25575)
            throw new IllegalStateException("Disposable occurrence collection is not enabled");
        scan(server,runtime(server));refreshPressure(server,runtime(server));
    }

    private static void scan(MinecraftServer server, Runtime runtime) throws IOException {
        if (ChronicleRuntime.service(server).state().phase() != SessionPhase.ACTIVE
                || RuntimeHolds.reason(server) != null || SessionBuilding.enabled(server)
                || PlayRuntime.eligiblePlayers(server).isEmpty()) return;
        Map<String, Boolean> flags = ChronicleRuntime.service(server).state().flags();
        for (EventConfiguration configuration : EventRuntime.configurations(server).values()) {
            if (!interested(server, configuration) || configuration.mode() != PlayState.Mode.SYSTEM) continue;
            EventProgressState.Entry progress = EventProgressRuntime.entry(server, configuration);
            if (progress == null) { requireNoPending(server,configuration.definition().id());EventProgressRuntime.arm(server, configuration); progress = EventProgressRuntime.entry(server, configuration); }
            for (EventSources.Occurrence fact : EventSources.facts(server, configuration.source())) {
                if (progress.seenFacts().contains(fact.id())) continue;
                if (liveClaim(runtime.state, configuration.definition().id(), fact.id()) != null
                        || runtime.store.decision(configuration.definition().id(), fact.id()).isPresent()) {
                    EventProgressRuntime.markFactSeen(server, configuration, fact.id()); progress = EventProgressRuntime.entry(server, configuration); continue;
                }
                if (inapplicable(configuration,fact)) {
                    if(fact.activeOrigin())runtime.store.publishDecision(new EventOccurrenceState.Decision(EventOccurrenceState.Decision.Kind.BASELINE,
                            configuration.definition().id(),configuration.fingerprint(),snapshot(fact),null,List.of()));
                    EventProgressRuntime.markFactSeen(server,configuration,fact.id());progress=EventProgressRuntime.entry(server,configuration);continue;
                }
                if (!required(configuration, flags)) continue;
                UUID actor = fact.actor();
                if (configuration.occurrencePolicy() == EventOccurrencePolicy.ONCE_PER_ACTOR) {
                    if (actor == null) continue;
                    EventOccurrenceState.ActorConsumption consumed = runtime.state.actors().get(EventOccurrenceState.actorKey(configuration.definition().id(), actor));
                    if (consumed == null) consumed = runtime.store.actor(configuration.definition().id(), actor).orElse(null);
                    if (consumed != null) {
                        runtime.store.publishDecision(new EventOccurrenceState.Decision(EventOccurrenceState.Decision.Kind.BASELINE,
                                configuration.definition().id(), configuration.fingerprint(), snapshot(fact), null, List.of()));
                        EventProgressRuntime.markFactSeen(server, configuration, fact.id()); progress = EventProgressRuntime.entry(server, configuration); continue;
                    }
                }
                List<UUID> recipients = EventRuntime.occurrenceRecipients(server, configuration, actor);
                if (recipients.isEmpty()) continue;
                if(runtime.state.claims().size()>=EventOccurrenceState.MAX_LIVE_CLAIMS||configuration.occurrencePolicy()==EventOccurrencePolicy.ONCE_PER_ACTOR&&runtime.state.actors().size()>=EventOccurrenceState.MAX_LIVE_ACTORS)return;
                claim(server, runtime, configuration, fact, recipients);
                EventProgressRuntime.markFactSeen(server, configuration, fact.id()); progress = EventProgressRuntime.entry(server, configuration);
            }
        }
    }

    private static void claim(MinecraftServer server, Runtime runtime, EventConfiguration configuration,
                              EventSources.Occurrence fact, List<UUID> recipients) throws IOException {
        if (runtime.state.claims().size() >= EventOccurrenceState.MAX_LIVE_CLAIMS) {
            throw new IOException("Event occurrence queue capacity reached; preserve the world and run Event history maintenance after resolving queued claims");
        }
        UUID operation = UUID.randomUUID();
        EventOccurrenceState.Claim claim = new EventOccurrenceState.Claim(configuration.definition().id(), operation,
                configuration.fingerprint(), snapshot(fact), ChronicleRuntime.service(server).state().sessionNumber(),
                PlayRuntime.elapsedTicks(server), recipients, EventOccurrenceState.Stage.CLAIMED, "Source occurrence claimed before Event execution.");
        Map<UUID, EventOccurrenceState.Claim> claims = new LinkedHashMap<>(runtime.state.claims()); claims.put(operation, claim);
        Map<String, EventOccurrenceState.ActorConsumption> actors = new TreeMap<>(runtime.state.actors());
        if (configuration.occurrencePolicy() == EventOccurrencePolicy.ONCE_PER_ACTOR) {
            if (actors.size() >= EventOccurrenceState.MAX_LIVE_ACTORS) {
                throw new IOException("Event actor history capacity reached; identities were preserved. Run Event history maintenance while play is non-active.");
            }
            UUID actor = Objects.requireNonNull(fact.actor(), "once-per-player source actor is required");
            EventOccurrenceState.ActorConsumption consumption = new EventOccurrenceState.ActorConsumption(configuration.definition().id(), actor,
                    operation, fact.id(), configuration.fingerprint());
            if (actors.putIfAbsent(consumption.key(), consumption) != null) throw new IllegalStateException("Event actor was consumed concurrently");
        }
        save(server, new EventOccurrenceState(Math.incrementExact(runtime.state.revision()), claims, actors));
    }

    private static void drain(MinecraftServer server, Runtime runtime) throws IOException {
        for (EventOccurrenceState.Claim claim : List.copyOf(runtime.state.claims().values())) {
            EventExecution execution = PlayRuntime.service(server).state().executions().get(claim.eventId());
            if (execution != null) {
                if (!execution.operationId().equals(claim.operationId())) continue;
                requireExact(claim, execution);
                if (execution.phase() == EventExecution.Phase.MEDIA_UNCERTAIN) {
                    RuntimeHolds.hold(server, HOLD, "A repeating Event's media is uncertain. Acknowledge that exact Event in Events before more activities can be recorded.");
                    return;
                }
                if (execution.phase() == EventExecution.Phase.COMPLETE) update(server, claim, EventOccurrenceState.Stage.COMPLETE, "Event execution completed; exact archive is pending.");
                else if (claim.stage() == EventOccurrenceState.Stage.CLAIMED) update(server, claim, EventOccurrenceState.Stage.EXECUTION, "Pinned Event execution was published.");
                claim = runtime(server).state.claims().get(claim.operationId());
                if (claim.stage() != EventOccurrenceState.Stage.COMPLETE) return;
                PlayRuntime.service(server).archiveAndRetireExecution(claim.eventId(), claim.operationId());
                retire(server, claim); RuntimeHolds.release(server, HOLD); return;
            }
            Optional<EventExecution> archived = PlayRuntime.service(server).archivedExecution(claim.operationId());
            if (archived.isPresent()) {
                EventExecution exact = archived.get();
                requireExact(claim, exact);
                if (!exact.eventId().equals(claim.eventId()) || exact.phase() != EventExecution.Phase.COMPLETE
                        || !exact.configurationFingerprint().equals(claim.configurationFingerprint())) {
                    throw new IOException("Archived Event occurrence does not match its durable claim");
                }
                if (claim.stage() != EventOccurrenceState.Stage.COMPLETE) update(server, claim, EventOccurrenceState.Stage.COMPLETE, "Recovered completion from the exact immutable Event archive.");
                retire(server, runtime(server).state.claims().get(claim.operationId())); RuntimeHolds.release(server, HOLD); return;
            }
            if (claim.stage() != EventOccurrenceState.Stage.CLAIMED) {
                throw new IOException("Event occurrence execution pointer disappeared before an exact archive was proven");
            }
            EventConfiguration configuration = EventRuntime.configuration(server, claim.eventId());
            if (!configuration.fingerprint().equals(claim.configurationFingerprint())) throw new IOException("Queued Event configuration changed despite its edit lock");
            if (ChronicleRuntime.service(server).state().phase() != SessionPhase.ACTIVE) return;
            if (!required(configuration, ChronicleRuntime.service(server).state().flags())) continue;
            if (PlayRuntime.elapsedTicks(server) - claim.claimedAtTicks() < configuration.delayTicks()) return;
            if (onlyBackpressureHold(server)) {RuntimeHolds.release(server,BACKPRESSURE);runtime.pressureHeld=false;}
            if (RuntimeHolds.reason(server) != null || EventRuntime.hasPending(server)) return;
            EventRuntime.beginOccurrence(server, configuration, claim); return;
        }
    }

    private static void update(MinecraftServer server, EventOccurrenceState.Claim claim,
                               EventOccurrenceState.Stage stage, String detail) throws IOException {
        Runtime runtime = runtime(server); EventOccurrenceState.Claim current = runtime.state.claims().get(claim.operationId());
        if (current == null) throw new IllegalStateException("Event occurrence claim disappeared");
        EventOccurrenceState.Claim next = current.with(stage, detail);
        if (next.equals(current)) return;
        Map<UUID, EventOccurrenceState.Claim> claims = new LinkedHashMap<>(runtime.state.claims()); claims.put(next.operationId(), next);
        save(server, new EventOccurrenceState(Math.incrementExact(runtime.state.revision()), claims, runtime.state.actors()));
    }

    private static void retire(MinecraftServer server, EventOccurrenceState.Claim claim) throws IOException {
        if (claim == null || claim.stage() != EventOccurrenceState.Stage.COMPLETE) throw new IllegalStateException("Only a completed occurrence can retire");
        Runtime runtime = runtime(server);
        runtime.store.publishDecision(new EventOccurrenceState.Decision(EventOccurrenceState.Decision.Kind.RETIRED,
                claim.eventId(), claim.configurationFingerprint(), claim.source(), claim.operationId(), claim.recipients()));
        Map<UUID, EventOccurrenceState.Claim> claims = new LinkedHashMap<>(runtime.state.claims()); claims.remove(claim.operationId());
        save(server, new EventOccurrenceState(Math.incrementExact(runtime.state.revision()), claims, runtime.state.actors()));
    }

    private static EventOccurrenceState.Claim liveClaim(EventOccurrenceState state, String eventId, String sourceId) {
        return state.claims().values().stream().filter(claim -> claim.eventId().equals(eventId) && claim.source().id().equals(sourceId)).findFirst().orElse(null);
    }

    static void requireExact(EventOccurrenceState.Claim claim, EventExecution execution) throws IOException {
        if (!execution.operationId().equals(claim.operationId()) || !execution.eventId().equals(claim.eventId())
                || !execution.configurationFingerprint().equals(claim.configurationFingerprint())
                || !Objects.equals(execution.actor(), claim.source().actor())
                || execution.source() != PlayState.DeliverySource.SYSTEM || !execution.recipients().equals(claim.recipients()))
            throw new IOException("Event execution does not match its exact durable source occurrence claim");
    }

    private static EventOccurrenceState.SourceSnapshot snapshot(EventSources.Occurrence fact) {
        return new EventOccurrenceState.SourceSnapshot(fact.id(), fact.type(), fact.target(), fact.activity(), fact.actor(),
                fact.activeOrigin(), fact.flags(), fact.authority());
    }

    private static boolean required(EventConfiguration configuration, Map<String, Boolean> flags) {
        return configuration.requiredFlags().stream().allMatch(id -> Boolean.TRUE.equals(flags.get(id)));
    }
    static boolean inapplicable(EventConfiguration configuration,EventSources.Occurrence fact) {
        return !fact.activeOrigin()||!required(configuration,fact.flags())
                ||configuration.definition().audience()==ObservationCatalog.Audience.SOURCE_ACTOR&&fact.actor()==null;
    }

    private static void save(MinecraftServer server, EventOccurrenceState state) throws IOException {
        Runtime runtime = runtime(server);
        try { runtime.store.save(state); runtime.state = state; }
        catch (IOException failure) { fail(server, failure); throw failure; }
    }

    private static Runtime runtime(MinecraftServer server) {
        Runtime runtime = WORLDS.get(server);
        if (runtime == null || runtime.failure != null) throw new IllegalStateException("Event occurrence evidence is unavailable; inspect its journal and server log.");
        return runtime;
    }

    private static void fail(MinecraftServer server, Exception failure) {
        Runtime runtime = WORLDS.get(server); if (runtime != null) runtime.failure = failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();
        RuntimeHolds.hold(server, HOLD, "Repeating Event evidence needs recovery; no occurrence was evicted or replayed.");
        ChronicleCore.LOGGER.error("Repeating Event coordination stopped; evidence was preserved", failure);
    }

    private static final class Runtime {
        final EventOccurrenceStore store; EventOccurrenceState state; String failure; boolean pressureHeld;
        Runtime(EventOccurrenceStore store, EventOccurrenceState state) { this.store = store; this.state = state; }
    }
}
