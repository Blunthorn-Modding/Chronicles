package io.github.chronicle.core.server;

import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.menu.CoreMenuModel.Fact;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import java.util.List;
import java.util.Map;
import static net.minecraft.server.command.CommandManager.literal;

/** One shared operator entry point for reclaiming completed Event evidence safely. */
public final class EventHistoryTools {
    public static final String ID = "chronicle_core:event_history";
    private EventHistoryTools() { }

    public static void register() {
        GameSetupTools.register(new GameSetupTools.Tool(ID, "Event history",
                "Archive completed Event records to make room for future play. Progress and pending recovery are retained.",
                List.of(new GameSetupTools.Verb("archive", "Archive completed records", GameSetupTools.Guard.EDIT, true)),
                EventHistoryTools::status, (source, verb) -> run(source)));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(literal("chronicle")
                .then(literal("maintenance").requires(source -> source.hasPermissionLevel(2))
                        .then(literal("events").executes(context -> command(context.getSource()))))));
    }

    private static GameSetupTools.Status status(ServerCommandSource source) {
        var server = source.getServer();
        try {
            int sources = EventSources.liveIds(server).size();
            int flags = ChronicleRuntime.service(server).state().eventFlagOutcomes().size();
            int batches = EventWorldBatches.committedEntries(server).size();
            int encounters=Encounters.liveOperationCount(server);
            int temporaryOfferings=TemporaryOfferings.ledger(server).claims().size();
            String unavailable = RuntimeHolds.reason(server);
            if (unavailable == null && (!EventSources.pending(server).isEmpty() || EventRuntime.hasPending(server) || EventWorldBatches.hasReservation(server)))
                unavailable = "Finish or recover the current Event operation first.";
            long revision = Integer.toUnsignedLong(java.util.Objects.hash(sources, flags, batches,encounters,temporaryOfferings, EventProgressRuntime.revision(server), unavailable));
            return new GameSetupTools.Status(revision,
                    List.of(new Fact("Source records", Integer.toString(sources)), new Fact("Flag receipts", Integer.toString(flags)),
                            new Fact("Completed world batches", Integer.toString(batches)),new Fact("Encounter operations",Integer.toString(encounters)),
                            new Fact("Temporary offering claims",Integer.toString(temporaryOfferings))),
                    unavailable == null ? Map.of() : Map.of("archive", unavailable));
        } catch (RuntimeException failure) {
            return new GameSetupTools.Status(0, List.of(), Map.of("archive", "Event evidence is unavailable. Resolve its recovery before archiving."));
        }
    }

    private static String run(ServerCommandSource source) throws java.io.IOException {
        if (!source.hasPermissionLevel(2)) throw new IllegalStateException("Operator permission level 2 is required.");
        var result = EventHistoryMaintenance.maintain(source.getServer());
        ChronicleRuntime.service(source.getServer()).recordOperation(
                new Actor(source.getName(), source.getPlayer() == null ? null : source.getPlayer().getUuidAsString()),
                "event.history.archive", ID, Map.of("sources", Integer.toString(result.sourcesRetired()),
                        "flags", Integer.toString(result.flagReceiptsRetired()), "batches", Integer.toString(result.worldBatchesRetired()),
                        "encounters",Integer.toString(result.encounterOperationsRetired()),"temporary_offerings",Integer.toString(result.temporaryOfferingsRetired())));
        return "Completed records archived: " + result.sourcesRetired() + " sources, " + result.flagReceiptsRetired()
                + " flag receipts, " + result.worldBatchesRetired() + " world batches, "+result.encounterOperationsRetired()+" encounter operations, and "
                +result.temporaryOfferingsRetired()+" temporary offering claims. Campaign progress is retained.";
    }

    private static int command(ServerCommandSource source) {
        try { String result = run(source); source.sendFeedback(() -> Text.literal(result), false); return 1; }
        catch (Exception failure) { source.sendError(Text.literal(failure.getMessage() == null ? "Event maintenance failed; evidence was retained." : failure.getMessage())); return 0; }
    }
}
