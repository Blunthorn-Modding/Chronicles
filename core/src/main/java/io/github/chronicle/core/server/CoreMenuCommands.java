package io.github.chronicle.core.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.menu.CoreMenuModel.Action;
import io.github.chronicle.core.menu.CoreMenuModel.Page;
import io.github.chronicle.core.server.CoreMenuServer;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Compact command fallback for the Core menu. It adds no persistence or game behavior:
 * mutations delegate to shared Core services or bounded native-menu actions.
 */
public final class CoreMenuCommands {
    private CoreMenuCommands() { }

    /** Registers the public menu entry points and the command facade below the sole owned root. */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Brigadier merges this literal into the existing mutable root and keeps all its children.
        dispatcher.register(literal("chronicle").executes(context -> CoreMenuServer.open(context.getSource())));
        var canonical = dispatcher.getRoot().getChild("chronicle");
        canonical.addChild(literal("debug").requires(CoreMenuCommands::isGm).build());
        canonical.addChild(literal("game").requires(CoreMenuCommands::isGm).build());
        canonical.addChild(session().build());
        canonical.addChild(players().build());
        canonical.addChild(events().build());
        canonical.addChild(literal("chests").requires(CoreMenuCommands::isGm).executes(context -> CoreMenuServer.open(context.getSource(), Page.CHESTS))
                .then(literal("inspect").requires(CoreMenuCommands::isGm).then(argument("chest", StringArgumentType.string()).suggests((c, b) -> chests(c.getSource(), b))
                        .executes(context -> execute(context.getSource(), Action.SHOW, Page.CHESTS, string(context, "chest"), ""))))
                .build());
        canonical.addChild(literal("roles").requires(CoreMenuCommands::isGm).executes(context -> CoreMenuServer.open(context.getSource(),Page.PLAYER_ROLES)).then(literal("reload").requires(source->source.hasPermissionLevel(2)).executes(context->{try{PlayerRoles.reload(context.getSource());context.getSource().sendFeedback(()->Text.literal("Player roles reloaded."),false);return 1;}catch(Exception failure){context.getSource().sendError(Text.literal(failure.getMessage()));return 0;}})).build());
        canonical.addChild(lootTables().build());
        canonical.addChild(literal("books").requires(CoreMenuCommands::isGm).executes(context -> CoreMenuServer.open(context.getSource(), Page.BOOKS_LORE)).build());
        canonical.addChild(literal("regions").requires(CoreMenuCommands::isGm).executes(context -> CoreMenuServer.open(context.getSource(), Page.REGIONS)).build());
        canonical.addChild(literal("locations").requires(CoreMenuCommands::isGm).executes(context -> CoreMenuServer.open(context.getSource(), Page.LOCATIONS)).build());
        canonical.addChild(setup().build());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> session() {
        return literal("session").executes(context -> CoreMenuServer.open(context.getSource(), Page.SESSION));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> players() {
        return literal("players").executes(context -> CoreMenuServer.open(context.getSource(), Page.PLAYERS))
                .then(literal("join").executes(context -> selfParticipation(context.getSource(), true)))
                .then(literal("leave").executes(context -> AutomaticPlayerSpawns.leave(context.getSource())))
                .then(literal("add").requires(CoreMenuCommands::isGm).then(argument("player", EntityArgumentType.player()).executes(context -> execute(context.getSource(), Action.PARTICIPANT_ADD, Page.PLAYERS, target(context).getUuidAsString(), ""))))
                .then(literal("remove").requires(CoreMenuCommands::isGm).then(argument("player", EntityArgumentType.player()).executes(context -> execute(context.getSource(), Action.PARTICIPANT_REMOVE, Page.PLAYERS, target(context).getUuidAsString(), ""))))
                .then(destination("starting-position", Action.STARTING_SET, Action.STARTING_OFF, Action.STARTING_RESET))
                .then(destination("respawn", Action.RESPAWN_SET, Action.RESPAWN_OFF, null))
                .then(literal("move").requires(CoreMenuCommands::isGm).then(literal("review").then(argument("player", EntityArgumentType.player()).then(argument("point", StringArgumentType.string()).suggests((c, b) -> points(c.getSource(), b))
                        .executes(context -> execute(context.getSource(), Action.PLAYER_MOVE_REVIEW, Page.PLAYERS, target(context).getUuidAsString(), string(context, "point"))))))
                        .then(literal("confirm").then(argument("player", EntityArgumentType.player()).executes(context -> execute(context.getSource(), Action.PLAYER_MOVE, Page.PLAYERS, target(context).getUuidAsString(), "")))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> destination(String name, Action set, Action disable, Action reset) {
        var node = literal(name).requires(CoreMenuCommands::isGm)
                .then(literal("set").then(argument("player", EntityArgumentType.player()).then(argument("point", StringArgumentType.string()).suggests((c, b) -> points(c.getSource(), b))
                        .executes(context -> execute(context.getSource(), set, Page.PLAYERS, target(context).getUuidAsString(), string(context, "point"))))))
                .then(literal("disable").then(argument("player", EntityArgumentType.player()).executes(context -> execute(context.getSource(), disable, Page.PLAYERS, target(context).getUuidAsString(), ""))));
        if (reset != null) node.then(literal("reset").then(argument("player", EntityArgumentType.player()).executes(context -> execute(context.getSource(), reset, Page.PLAYERS, target(context).getUuidAsString(), ""))));
        return node;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> events() {
        var node = literal("events").requires(CoreMenuCommands::isGm).executes(context -> CoreMenuServer.open(context.getSource(), Page.EVENTS));
        node.then(eventOperation("preview", Action.EVENT_PREVIEW));
        node.then(eventOperation("check", Action.EVENT_CHECK));
        node.then(literal("run").then(eventOperation("review", Action.EVENT_REVIEW)).then(eventOperation("confirm", Action.EVENT_RUN)));
        return node;
    }

    /** Event kind is explicit because cue and state-action registrations may share a raw ID. */
    private static LiteralArgumentBuilder<ServerCommandSource> eventOperation(String verb, Action action) {
        return literal(verb).requires(CoreMenuCommands::isGm)
                .then(literal("cue").then(argument("id", StringArgumentType.string()).suggests((c, b) -> cues(b))
                        .executes(context -> execute(context.getSource(), action, Page.EVENTS, "cue:" + string(context, "id"), ""))))
                .then(literal("action").then(argument("id", StringArgumentType.string()).suggests((c, b) -> stateActions(b))
                        .executes(context -> execute(context.getSource(), action, Page.EVENTS, "action:" + string(context, "id"), ""))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> lootTables() {
        return literal("loot-tables").requires(CoreMenuCommands::isGm).executes(context -> CoreMenuServer.open(context.getSource(), Page.LOOT_TABLES))
                .then(literal("list").executes(context->{try{var tables=TypedChestLoot.types(context.getSource().getServer());context.getSource().sendFeedback(()->Text.literal("Loot tables="+tables.size()+": "+String.join(", ",tables.keySet())),false);return 1;}catch(RuntimeException failure){context.getSource().sendError(Text.literal(failure.getMessage()));return 0;}}))
                .then(literal("inspect").requires(CoreMenuCommands::isGm).then(argument("table", StringArgumentType.string()).suggests((c, b) -> lootTables(c.getSource(), b))
                        .executes(context -> inspectLoot(context.getSource(), string(context, "table")))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> setup() {
        return literal("setup").requires(CoreMenuCommands::isGm).executes(context -> CoreMenuServer.open(context.getSource(), Page.SETUP))
                .then(literal("tool").requires(CoreMenuCommands::isGm).then(argument("id", StringArgumentType.string()).suggests((c, b) -> gameTools(b))
                        .then(argument("verb", StringArgumentType.string()).suggests((c, b) -> gameToolVerbs(string(c, "id"), b))
                                .executes(context -> execute(context.getSource(), Action.TOOL_ACTION, Page.SETUP, "tool:" + string(context, "id"), string(context, "verb"))))))
                .then(literal("check").requires(CoreMenuCommands::isGm).executes(context -> checkSetup(context.getSource())));
    }

    private static boolean isGm(ServerCommandSource source) { return source.hasPermissionLevel(2); }

    private static int execute(ServerCommandSource source, Action action, Page page, String id, String argument) {
        return CoreMenuServer.executeCommand(source, action, page, id, argument);
    }

    private static int selfParticipation(ServerCommandSource source, boolean join) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) { source.sendError(Text.literal("This action requires a player.")); return 0; }
        try {
            // Self opt-in is allowed during play, including for operators. GM assignment uses
            // PARTICIPANT_ADD/REMOVE and deliberately retains its non-active Editing gate.
            PlayRuntime.setSelfParticipant(player, join);
            source.sendFeedback(() -> Text.literal(join
                    ? "Participation enabled. Spectating and editing do not count as active play."
                    : "Participation removed; character card and automatic destinations are unchanged."), false);
            return 1;
        } catch (java.io.IOException | IllegalArgumentException | IllegalStateException failure) {
            source.sendError(Text.literal(PlayRuntime.message(failure)));
            return 0;
        }
    }

    private static int inspectLoot(ServerCommandSource source, String id) {
        if (!source.hasPermissionLevel(2)) { source.sendError(Text.literal("Operator permission level 2 is required.")); return 0; }
        try {
            var type = TypedChestLoot.types(source.getServer()).get(id);
            if (type == null) throw new IllegalArgumentException("Unknown loot table: " + id);
            source.sendFeedback(() -> Text.literal(type.id() + " · " + type.label() + "; entries=" + type.entries().size()
                    + "; recommended chests=" + type.recommendedChests() + ". Open Loot Tables to edit its independent item chances."), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) { source.sendError(Text.literal(exception.getMessage())); return 0; }
    }

    private static int checkSetup(ServerCommandSource source) {
        if (!source.hasPermissionLevel(2)) { source.sendError(Text.literal("Operator permission level 2 is required.")); return 0; }
        try {
            List<String> messages = new ArrayList<>(); int assigned = 0, stale = 0;
            Map<String, io.github.chronicle.core.authoring.SiteAssignments.Binding> bindings = MapSiteAssignments.bindings(source.getServer());
            for (var role : ChronicleCore.authoring().roles()) {
                var binding = bindings.get(role.id());
                if (binding == null) continue;
                assigned++;
                try { MapSiteAssignments.resolve(source.getServer(), role.id()); }
                catch (IllegalStateException exception) { stale++; if (messages.size() < 12) messages.add(role.id() + ": " + exception.getMessage()); }
            }
            for (String message : messages) source.sendFeedback(() -> Text.literal(message), false);
            String summary = "Game setup: assigned=" + assigned + "; stale=" + stale + ".";
            source.sendFeedback(() -> Text.literal(summary), false);
            return stale == 0 ? 1 : 0;
        } catch (IllegalArgumentException | IllegalStateException exception) { source.sendError(Text.literal(exception.getMessage())); return 0; }
    }

    private static ServerPlayerEntity target(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return EntityArgumentType.getPlayer(context, "player");
    }
    private static String string(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context, String name) { return StringArgumentType.getString(context, name); }
    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> points(ServerCommandSource source, SuggestionsBuilder builder) { return siteSuggestions(source, builder, AuthoringSite.Location.class); }
    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> sites(ServerCommandSource source, SuggestionsBuilder builder) { return siteSuggestions(source, builder, AuthoringSite.class); }
    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> siteSuggestions(ServerCommandSource source, SuggestionsBuilder builder, Class<?> kind) {
        try {
            List<String> values = AuthoringRuntime.get(source.getServer()).service().state().sites().values().stream()
                    .filter(site -> kind == AuthoringSite.class || kind.isInstance(site)).map(AuthoringSite::id).sorted().toList();
            return CommandSource.suggestMatching(values, builder);
        } catch (IllegalStateException exception) { return builder.buildFuture(); }
    }
    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> lootTables(ServerCommandSource source, SuggestionsBuilder builder) {
        try { return CommandSource.suggestMatching(TypedChestLoot.types(source.getServer()).keySet(), builder); }
        catch (IllegalStateException exception) { return builder.buildFuture(); }
    }
    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> chests(ServerCommandSource source, SuggestionsBuilder builder) {
        try { return CommandSource.suggestMatching(ChestAuthoring.service(source.getServer()).state().bindings().keySet(), builder); }
        catch (IllegalStateException exception) { return builder.buildFuture(); }
    }
    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> requirements(SuggestionsBuilder builder) { return CommandSource.suggestMatching(ChronicleCore.authoring().roles().stream().map(r -> r.id()), builder); }
    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> gameTools(SuggestionsBuilder builder) { return CommandSource.suggestMatching(GameSetupTools.tools().stream().map(GameSetupTools.Tool::id), builder); }
    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> gameToolVerbs(String toolId, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(GameSetupTools.get(toolId).map(GameSetupTools.Tool::verbs).orElse(List.of()).stream().map(GameSetupTools.Verb::id), builder);
    }
    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> cues(SuggestionsBuilder builder) { return CommandSource.suggestMatching(ChronicleCore.observations().definitions().stream().map(d -> d.id()), builder); }
    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> stateActions(SuggestionsBuilder builder) { return CommandSource.suggestMatching(ChronicleCore.events().events().stream().map(e -> e.id()), builder); }
}
