package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.history.EventHistoryMaintenanceConfig;
import io.github.chronicle.core.history.EventHistoryMaintenancePolicy;
import io.github.chronicle.core.offering.temporary.TemporaryOfferingLedger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;

/** Bounded, opt-out automatic archival; manual Event history maintenance remains authoritative. */
public final class EventHistoryScheduler {
    private static final Map<MinecraftServer, Runtime> SERVERS = new IdentityHashMap<>();
    private static boolean registered;
    private EventHistoryScheduler() { }

    public record Status(boolean enabled, boolean failed, int quietSeconds, int intervalSeconds,
                         long quietElapsedSeconds, String detail) { }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ServerLifecycleEvents.SERVER_STARTED.register(EventHistoryScheduler::start);
        ServerTickEvents.END_SERVER_TICK.register(EventHistoryScheduler::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(SERVERS::remove);
    }

    /** Explicit reload clears failure state and all quiet/attempt credit, without touching Event evidence. */
    public static void reload(MinecraftServer server) {
        requireServerThread(server);
        Runtime runtime = SERVERS.computeIfAbsent(server, ignored -> new Runtime());
        runtime.policy.reset();
        runtime.failure = null;
        try { runtime.config = EventHistoryMaintenanceConfig.loadOrCreate(configFile()); }
        catch (Exception failure) { disable(server, runtime, failure); }
    }

    public static Status status(MinecraftServer server) {
        requireServerThread(server);
        Runtime runtime = SERVERS.get(server);
        if (runtime == null) return new Status(false, true, 0, 0, 0, "Automatic Event history maintenance has not started.");
        EventHistoryMaintenanceConfig config = runtime.config;
        long quiet = runtime.policy.quietElapsedTicks(Math.max(0, server.getTicks())) / EventHistoryMaintenancePolicy.TICKS_PER_SECOND;
        boolean enabled = config != null && config.enabled() && runtime.failure == null;
        String detail = runtime.failure == null ? (config != null && !config.enabled() ? "Disabled by config." : "Ready; automatic attempts require a quiet, non-active empty server.") : runtime.failure;
        return new Status(enabled, runtime.failure != null, config == null ? 0 : config.quietSeconds(), config == null ? 0 : config.intervalSeconds(), quiet, detail);
    }

    private static void start(MinecraftServer server) { reload(server); }

    private static void tick(MinecraftServer server) {
        Runtime runtime = SERVERS.get(server);
        if (runtime == null || runtime.config == null || runtime.failure != null) return;
        try {
            if(ChronicleRuntime.service(server).state().phase()==SessionPhase.ACTIVE||!server.getPlayerManager().getPlayerList().isEmpty()||RuntimeHolds.reason(server)!=null){runtime.policy.interruptQuiet();return;}
            if(server.getTicks()%EventHistoryMaintenancePolicy.TICKS_PER_SECOND!=0)return;
            boolean eligible = eligible(server);
            boolean hasWork = eligible && hasPlausibleWork(server);
            var decision = runtime.policy.observe(server.getTicks(), runtime.config, eligible, hasWork);
            if (!decision.attempt()) return;
            // Re-read every guard directly before I/O; maintain() independently retains its original guards.
            if (!eligible(server) || !hasPlausibleWork(server)) return;
            archiveAndAudit(()->EventHistoryMaintenance.maintain(server),result->recordAudit(server,result));
        } catch (Exception failure) { disable(server, runtime, failure); }
    }

    private static boolean eligible(MinecraftServer server) {
        return ChronicleRuntime.service(server).state().phase() != SessionPhase.ACTIVE
                && server.getPlayerManager().getPlayerList().isEmpty()
                && RuntimeHolds.reason(server) == null
                && !EventRuntime.hasPending(server)
                && !EventOccurrenceRuntime.hasPending(server)
                && EventWorldBatches.ready(server) && !EventWorldBatches.hasReservation(server)
                && !Encounters.hasUnresolved(server)
                && EventSources.pending(server).isEmpty()
                && TemporaryOfferings.ledger(server).unresolved().isEmpty();
    }

    /** All queried ledgers have their own finite live-capacity bounds; this never loads worlds/chunks. */
    private static boolean hasPlausibleWork(MinecraftServer server) {
        return EventOccurrenceRuntime.liveActors(server)>0 || EventProgressRuntime.hasFactsMissingFromLiveLedger(server,EventSources.liveIds(server))
                || !EventSources.committed(server).isEmpty()
                || !ChronicleRuntime.service(server).state().eventFlagOutcomes().isEmpty()
                || !EventWorldBatches.committedEntries(server).isEmpty()
                || Encounters.liveOperationCount(server) > 0
                || TemporaryOfferings.ledger(server).claims().stream().anyMatch(TemporaryOfferingLedger::terminal);
    }

    private static boolean changed(EventHistoryMaintenance.Result result) {
        return result.sourcesRetired() != 0 || result.progressFactsTrimmed() != 0 || result.flagReceiptsRetired() != 0
                || result.worldBatchesRetired() != 0 || result.encounterOperationsRetired() != 0 || result.temporaryOfferingsRetired() != 0 || result.actorRecordsRetired() != 0;
    }

    @FunctionalInterface interface ArchiveCall{EventHistoryMaintenance.Result run()throws Exception;}
    @FunctionalInterface interface AuditCall{void write(EventHistoryMaintenance.Result result)throws Exception;}
    static final class AuditFailedAfterArchive extends Exception{
        final EventHistoryMaintenance.Result completed;
        AuditFailedAfterArchive(EventHistoryMaintenance.Result completed,Exception cause){super("Archival completed ("+completed.sourcesRetired()+" sources, "+completed.flagReceiptsRetired()+" flag receipts), but its campaign audit could not be saved. Immutable archives remain valid; no retirement was rolled back or retried. Cause: "+cause.getMessage(),cause);this.completed=completed;}
    }
    static EventHistoryMaintenance.Result archiveAndAudit(ArchiveCall archive,AuditCall audit)throws Exception{
        var result=archive.run();if(changed(result))try{audit.write(result);}catch(Exception failure){throw new AuditFailedAfterArchive(result,failure);}return result;
    }

    private static void recordAudit(MinecraftServer server, EventHistoryMaintenance.Result result) throws java.io.IOException {
        ChronicleRuntime.service(server).recordOperation(new Actor("Automatic Event maintenance", null), "event.history.archive.automatic", "chronicle_core:event_history",
                Map.of("sources", Integer.toString(result.sourcesRetired()), "progress_facts", Integer.toString(result.progressFactsTrimmed()),
                        "flags", Integer.toString(result.flagReceiptsRetired()), "batches", Integer.toString(result.worldBatchesRetired()),
                        "encounters", Integer.toString(result.encounterOperationsRetired()), "temporary_offerings", Integer.toString(result.temporaryOfferingsRetired()),"actor_records",Integer.toString(result.actorRecordsRetired())));
    }

    private static void disable(MinecraftServer server, Runtime runtime, Exception failure) {
        runtime.policy.reset();
        if (runtime.failure != null) return;
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        runtime.failure = "Automatic Event history maintenance disabled. Manual maintenance remains available. Repair the reported storage/configuration failure, then explicitly reload the automatic policy. Cause: " + message;
        ChronicleCore.LOGGER.error(runtime.failure, failure);
    }
    private static Path configFile() { return FabricLoader.getInstance().getConfigDir().resolve("chronicle/event-history-maintenance.json"); }
    private static void requireServerThread(MinecraftServer server) { if (server == null || !server.isOnThread()) throw new IllegalStateException("Event history scheduler requires the server thread"); }
    private static final class Runtime { final EventHistoryMaintenancePolicy policy = new EventHistoryMaintenancePolicy(); EventHistoryMaintenanceConfig config; String failure; }
}
