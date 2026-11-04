package io.github.chronicle.core.server;

import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.play.EventExecution;
import io.github.chronicle.core.play.PlayService;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.*;

/** Explicit bounded maintenance for terminal Event/source authorities. Registration belongs to the server coordinator. */
public final class EventHistoryMaintenance {
    private EventHistoryMaintenance() { }

    public record Result(int sourcesRetired, int progressFactsTrimmed, int flagReceiptsRetired,
                         int worldBatchesRetired, int encounterOperationsRetired, int temporaryOfferingsRetired, int actorRecordsRetired) {
        public Result(int sourcesRetired,int progressFactsTrimmed,int flagReceiptsRetired,int worldBatchesRetired,int encounterOperationsRetired,int temporaryOfferingsRetired){
            this(sourcesRetired,progressFactsTrimmed,flagReceiptsRetired,worldBatchesRetired,encounterOperationsRetired,temporaryOfferingsRetired,0);
        }
        public Result(int sourcesRetired,int progressFactsTrimmed,int flagReceiptsRetired,int worldBatchesRetired,int encounterOperationsRetired){
            this(sourcesRetired,progressFactsTrimmed,flagReceiptsRetired,worldBatchesRetired,encounterOperationsRetired,0);
        }
    }

    public static Result maintain(MinecraftServer server) throws IOException {
        Objects.requireNonNull(server, "server is required");
        if (!server.isOnThread()) throw new IllegalStateException("Event history maintenance requires the server thread");
        var campaign = ChronicleRuntime.service(server);
        if (campaign.state().phase() == SessionPhase.ACTIVE)
            throw new IllegalStateException("Pause or end play before Event history maintenance");
        String hold = RuntimeHolds.reason(server);
        if (hold != null&&!EventOccurrenceRuntime.onlyBackpressureHold(server)) throw new IllegalStateException("Resolve the current Chronicle operation before Event history maintenance: " + hold);
        if (!EventWorldBatches.ready(server) || EventWorldBatches.hasReservation(server))
            throw new IllegalStateException("Resolve Event Region batch recovery before history maintenance");
        if (Encounters.hasUnresolved(server)) throw new IllegalStateException("Resolve encounter spawn recovery before history maintenance");
        if (!EventSources.pending(server).isEmpty())
            throw new IllegalStateException("Prepared Event source evidence cannot be maintained; resolve its exact outcome first");
        if (EventOccurrenceRuntime.liveClaims(server) > 0)
            throw new IllegalStateException("Queued repeating Event occurrences cannot be maintained; let their exact executions finish first");

        PlayService play = PlayRuntime.service(server);
        if (play.state().executions().values().stream().anyMatch(execution -> execution.phase() != EventExecution.Phase.COMPLETE))
            throw new IllegalStateException("Unresolved Event execution/media evidence cannot be maintained");

        int retiredTemporaryOfferings=TemporaryOfferings.maintain(server);

        int trimmed = EventProgressRuntime.trimFactsMissingFromLiveLedger(server, EventSources.liveIds(server));

        Set<String> sources = new LinkedHashSet<>();
        for (var receipt : EventSources.committed(server))
            if (EventProgressRuntime.observedByEverySubscriber(server, receipt)) sources.add(receipt.id());
        if (!sources.isEmpty()) EventSources.retireCommitted(server, sources);
        trimmed += EventProgressRuntime.trimFactsMissingFromLiveLedger(server, EventSources.liveIds(server));

        Map<UUID,Boolean> completed = new HashMap<>();
        Set<UUID> flagReceipts = new LinkedHashSet<>();
        for (UUID operation : campaign.state().eventFlagOutcomes().keySet())
            if (completedExecution(play, operation, completed)) flagReceipts.add(operation);
        int retiredFlags = campaign.retireEventFlagOutcomes(flagReceipts);

        Set<UUID> batches = new LinkedHashSet<>();
        for (UUID operation : EventWorldBatches.committedEntries(server).keySet())
            if (completedExecution(play, operation, completed)) batches.add(operation);
        int retiredBatches = EventWorldBatches.retireCommitted(server, batches);
        int retiredEncounters=Encounters.maintainCompleted(server);
        int retiredActors=EventOccurrenceRuntime.maintainActors(server);
        return new Result(sources.size(), trimmed, retiredFlags, retiredBatches,retiredEncounters,retiredTemporaryOfferings,retiredActors);
    }

    private static boolean completedExecution(PlayService play, UUID operation, Map<UUID,Boolean> cache) throws IOException {
        Boolean known = cache.get(operation);
        if (known != null) return known;
        boolean complete = play.state().executions().values().stream()
                .anyMatch(execution -> execution.operationId().equals(operation) && execution.phase() == EventExecution.Phase.COMPLETE);
        if (!complete) complete = play.archivedExecution(operation).map(execution -> execution.phase() == EventExecution.Phase.COMPLETE).orElse(false);
        cache.put(operation, complete); return complete;
    }
}
