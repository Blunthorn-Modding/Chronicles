package io.github.chronicle.core.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.campaign.AuditEntry;
import io.github.chronicle.core.campaign.CampaignEvent;
import io.github.chronicle.core.campaign.CampaignService;
import io.github.chronicle.core.campaign.CampaignState;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Read-only status is public; all campaign administration requires operator level two. */
public final class ChronicleCommands {
    private ChronicleCommands() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var session = literal("session")
                .then(literal("building").requires(ChronicleCommands::isGm)
                        .then(literal("on").executes(context -> building(context.getSource(), true)))
                        .then(literal("off").executes(context -> building(context.getSource(), false))))
                .then(literal("status").executes(context -> status(context.getSource())))
                .then(literal("start").requires(ChronicleCommands::isGm)
                        .executes(context -> start(context.getSource(), null))
                        .then(argument("number", IntegerArgumentType.integer(0, 1_000_000))
                                .executes(context -> start(context.getSource(), IntegerArgumentType.getInteger(context, "number")))))
                .then(literal("pause").requires(ChronicleCommands::isGm).executes(context -> mutate(context.getSource(), (service, actor) -> {
                    service.pause(actor);
                    return "Paused Chronicle session " + service.state().sessionNumber() + ".";
                })))
                .then(literal("resume").requires(ChronicleCommands::isGm).executes(context -> mutate(context.getSource(), (service, actor) -> {
                    service.resume(actor);
                    return "Resumed Chronicle session " + service.state().sessionNumber() + ".";
                })))
                .then(literal("end").requires(ChronicleCommands::isGm).executes(context -> mutate(context.getSource(), (service, actor) -> {
                    service.end(actor);
                    return "Ended Chronicle session " + service.state().sessionNumber() + ".";
                })));

        var flag = literal("flag").requires(ChronicleCommands::isGm)
                .then(literal("list").executes(context -> query(context.getSource(), service ->
                        "Chronicle flags (" + service.state().flags().size() + "): " + new TreeMap<>(service.state().flags()))))
                .then(literal("get").then(argument("id", IdentifierArgumentType.identifier())
                        .executes(context -> {
                            String id = IdentifierArgumentType.getIdentifier(context, "id").toString();
                            return query(context.getSource(), service -> "Chronicle flag " + id + " = " + service.state().flag(id) + ".");
                        })))
                .then(literal("set").then(argument("id", IdentifierArgumentType.identifier())
                        .then(argument("value", BoolArgumentType.bool()).executes(context -> {
                            String id = IdentifierArgumentType.getIdentifier(context, "id").toString();
                            boolean value = BoolArgumentType.getBool(context, "value");
                            return mutate(context.getSource(), (service, actor) -> {
                                service.setFlag(actor, id, value);
                                return "Chronicle flag " + id + " = " + value + ".";
                            });
                        }))));

        var event = literal("event").requires(ChronicleCommands::isGm)
                .then(literal("list").executes(context -> query(context.getSource(), service ->
                        "Chronicle events (" + ChronicleCore.events().events().size() + "): "
                                + String.join(", ", ChronicleCore.events().events().stream().map(CampaignEvent::id).toList()))))
                .then(literal("preview").then(argument("id", IdentifierArgumentType.identifier())
                        .suggests((context, builder) -> CommandSource.suggestMatching(ChronicleCore.events().events().stream().map(CampaignEvent::id), builder))
                        .executes(context -> query(context.getSource(), service -> {
                            String id = IdentifierArgumentType.getIdentifier(context, "id").toString();
                            CampaignEvent definition = ChronicleCore.events().get(id)
                                    .orElseThrow(() -> new IllegalArgumentException("Unknown campaign event: " + id));
                            return "Chronicle event " + id + ": " + definition.description()
                                    + " onceOnly=" + definition.onceOnly() + "; " + service.preview(id);
                        }))))
                .then(literal("trigger").then(argument("id", IdentifierArgumentType.identifier())
                        .suggests((context, builder) -> CommandSource.suggestMatching(ChronicleCore.events().events().stream().map(CampaignEvent::id), builder))
                        .executes(context -> mutate(context.getSource(), (service, actor) -> {
                            String id = IdentifierArgumentType.getIdentifier(context, "id").toString();
                            String message = service.trigger(actor, id);
                            return "Chronicle event " + id + " completed. " + message;
                        }))))
                .then(literal("history").executes(context -> history(context.getSource(), 20, true))
                        .then(argument("limit", IntegerArgumentType.integer(1, 50))
                                .executes(context -> history(context.getSource(), IntegerArgumentType.getInteger(context, "limit"), true))));

        dispatcher.register(literal("chronicle")
                .then(literal("status").executes(context -> status(context.getSource())))
                .then(session).then(flag).then(event)
                .then(literal("history").requires(ChronicleCommands::isGm)
                        .executes(context -> history(context.getSource(), 20, false))
                        .then(argument("limit", IntegerArgumentType.integer(1, 50))
                                .executes(context -> history(context.getSource(), IntegerArgumentType.getInteger(context, "limit"), false)))));
    }

    private static boolean isGm(ServerCommandSource source) {
        return source.hasPermissionLevel(2);
    }

    private static int building(ServerCommandSource source, boolean value) {
        try {
            String message = SessionBuilding.set(source, value);
            source.sendFeedback(() -> Text.literal(message), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException error) {
            source.sendError(Text.literal(error.getMessage()));
            return 0;
        }
    }

    private static int status(ServerCommandSource source) {
        return query(source, service -> {
            CampaignState state = service.state();
            return "Chronicle session status: " + state.phase().name().toLowerCase(Locale.ROOT) + "; number=" + state.sessionNumber() + ".";
        });
    }

    private static int start(ServerCommandSource source, Integer number) {
        return mutate(source, (service, actor) -> {
            service.start(actor, number);
            return "Started Chronicle session " + service.state().sessionNumber() + ".";
        });
    }

    private static int history(ServerCommandSource source, int limit, boolean eventsOnly) {
        return query(source, service -> {
            List<AuditEntry> history = service.state().history().stream()
                    .filter(entry -> !eventsOnly || "event.trigger".equals(entry.action())).toList();
            List<AuditEntry> visible = history.subList(Math.max(0, history.size() - limit), history.size());
            for (AuditEntry entry : visible) {
                String identity = entry.actor().name() + (entry.actor().uuid() == null ? "" : " (" + entry.actor().uuid() + ")");
                source.sendFeedback(() -> Text.literal(entry.sequence() + " | " + entry.timestamp() + " | " + identity
                        + " | session=" + entry.sessionNumber() + " | " + entry.action() + " | " + entry.target()
                        + " | " + new TreeMap<>(entry.parameters())), false);
            }
            return "Chronicle history (showing " + visible.size() + " of " + history.size() + ").";
        });
    }

    private static int query(ServerCommandSource source, Query operation) {
        try {
            String message = operation.run(ChronicleRuntime.service(source.getServer()));
            source.sendFeedback(() -> Text.literal(message), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendError(Text.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int mutate(ServerCommandSource source, Mutation operation) {
        // Retain an execution-time guard in addition to Brigadier's command-tree permission checks.
        if (!isGm(source)) {
            source.sendError(Text.literal("Chronicle administration requires operator permission level 2."));
            return 0;
        }
        try {
            CampaignService service = ChronicleRuntime.service(source.getServer());
            ServerPlayerEntity player = source.getPlayer();
            Actor actor = new Actor(source.getName(), player == null ? null : player.getUuidAsString());
            String message = operation.run(service, actor);
            SessionPresentation.changed(source.getServer());
            AuditEntry entry = service.state().history().getLast();
            ChronicleCore.LOGGER.info("Chronicle audit #{}: {} by {} ({}), session={}, target={}, parameters={}",
                    entry.sequence(), entry.action(), actor.name(), actor.uuid(), entry.sessionNumber(), entry.target(), entry.parameters());
            source.sendFeedback(() -> Text.literal(message), false);
            return 1;
        } catch (IOException exception) {
            ChronicleCore.LOGGER.error("Chronicle state could not be saved; the requested mutation was not committed", exception);
            source.sendError(Text.literal("Chronicle data could not be saved. No change was applied; check the server log."));
            return 0;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            if (exception.getCause() != null) {
                ChronicleCore.LOGGER.error("Chronicle campaign operation failed", exception);
            }
            source.sendError(Text.literal(exception.getMessage()));
            return 0;
        }
    }

    @FunctionalInterface
    private interface Query { String run(CampaignService service); }
    @FunctionalInterface
    private interface Mutation { String run(CampaignService service, Actor actor) throws IOException; }
}
