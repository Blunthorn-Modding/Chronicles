package io.github.chronicle.core.server;

import io.github.chronicle.core.eventpool.*;
import io.github.chronicle.core.play.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

/** Optional, trusted server API. Draws are private world authority, never client projections. */
public final class EventPools {
    public static final String HOLD="chronicle_core:event-pools";
    private static final Map<MinecraftServer,EventPoolService> WORLDS=new IdentityHashMap<>();
    private static final SecureRandom RANDOM=new SecureRandom();
    public enum Status { RESERVED, PREPARING, RUNNING, COMPLETE, MEDIA_UNCERTAIN, CONFLICT }
    private EventPools() { }
    public static void register() { ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove); }

    /** The producer supplies a stable occurrence key; retrying it never chooses different content. */
    public static EventPoolDraw draw(MinecraftServer server,EventPoolDefinition pool,String key)throws IOException {
        thread(server);
        try {
            var service=service(server);
            var previous=service.draws().get(key);
            if(previous!=null) {
                if(!previous.poolId().equals(pool.id()))throw new IllegalStateException("Pool occurrence identity changed.");
                return previous;
            }
            requireActive(server);
            Map<String,EventPoolService.Candidate> candidates=new LinkedHashMap<>();
            for(var entry:pool.entries()) {
                try {
                    var review=EventRuntime.review(server,entry.eventId());
                    var c=review.configuration();
                    if(!c.enabled()||c.mode()!=PlayState.Mode.MANUAL||c.delayTicks()!=0||c.occurrencePolicy()!=EventOccurrencePolicy.ONCE)continue;
                    candidates.put(entry.eventId(),new EventPoolService.Candidate(c,review.session(),null,review.recipients()));
                }catch(IllegalArgumentException|IllegalStateException notEligible) { /* Not an eligible draw; no live selection is logged. */ }
            }
            return service.claim(pool,key,candidates,RANDOM::nextInt,UUID::randomUUID);
        }catch(IOException failure){unavailable(server);throw failure;}
    }

    /** Dispatches only the saved selection through the ordinary Event executor. It never retries uncertain media. */
    public static Status dispatch(MinecraftServer server,String key)throws IOException {
        thread(server);
        try {
            var draw=requireDraw(server,key);
            Status status=status(server,key);
            if(status!=Status.RESERVED)return status;
            requireActive(server);
            EventRuntime.beginPool(server,draw);
            return status(server,key);
        }catch(IOException failure){unavailable(server);throw failure;}
    }

    public static Status status(MinecraftServer server,String key)throws IOException {
        thread(server);
        try {
            var draw=requireDraw(server,key);
            var play=PlayRuntime.service(server);
            var live=play.state().executions().get(draw.configuration().definition().id());
            var archived=play.archivedExecution(draw.operationId()).orElse(null);
            Status status=executionStatus(draw,live,archived);
            if(status==Status.RESERVED&&play.state().deliveries().containsKey(draw.configuration().definition().id()))return Status.CONFLICT;
            return status==Status.RESERVED&&EventRuntime.preparingPool(server,draw.operationId())?Status.PREPARING:status;
        }catch(IOException failure){unavailable(server);throw failure;}
    }

    static Status executionStatus(EventPoolDraw draw,EventExecution live,EventExecution archived) {
        // Archives retain exact authority even after a deliberate reset creates a newer live invocation.
        EventExecution execution=archived!=null?archived:live;
        if(execution==null)return Status.RESERVED;
        if(!execution.operationId().equals(draw.operationId())
                ||!execution.configuration().equals(draw.configuration())
                ||execution.source()!=PlayState.DeliverySource.POOL||execution.session()!=draw.session()
                ||!Objects.equals(execution.actor(),draw.actor())
                ||!new HashSet<>(execution.recipients()).equals(new HashSet<>(draw.recipients())))return Status.CONFLICT;
        return switch(execution.phase()) {
            case COMPLETE -> Status.COMPLETE;
            case MEDIA_UNCERTAIN -> Status.MEDIA_UNCERTAIN;
            case CONFLICT -> Status.CONFLICT;
            default -> Status.RUNNING;
        };
    }
    /** Trusted producer recovery only; never include retained draws in ordinary projections. */
    public static Optional<EventPoolDraw> retainedDraw(MinecraftServer server,String key)throws IOException {
        thread(server);return Optional.ofNullable(service(server).draws().get(key));
    }
    public static void reload(MinecraftServer server)throws IOException {
        thread(server);
        var next=new EventPoolService(new EventPoolStore(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/event-pools.json")));
        next.verifyUnchanged();WORLDS.put(server,next);RuntimeHolds.release(server,HOLD);
    }
    private static EventPoolDraw requireDraw(MinecraftServer server,String key)throws IOException {
        var draw=service(server).draws().get(key);
        if(draw==null)throw new IllegalArgumentException("Unknown pool occurrence.");
        return draw;
    }
    private static EventPoolService service(MinecraftServer server)throws IOException {
        var service=WORLDS.get(server);
        if(service==null) {
            service=new EventPoolService(new EventPoolStore(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/event-pools.json")));
            WORLDS.put(server,service);
        }
        service.verifyUnchanged();
        return service;
    }
    private static void requireActive(MinecraftServer server) {
        if(!EventSources.activeOperation(server)||RuntimeHolds.reason(server)!=null)
            throw new IllegalStateException("Pool dispatch requires occupied active play outside Building Mode and operation holds.");
    }
    private static void unavailable(MinecraftServer server) {
        RuntimeHolds.hold(server,HOLD,"Event pool records need recovery. Retained selections must be reloaded after repair; do not reroll them.");
    }
    private static void thread(MinecraftServer server) {
        Objects.requireNonNull(server);
        if(!server.isOnThread())throw new IllegalStateException("Event pools require the server thread.");
    }
}
