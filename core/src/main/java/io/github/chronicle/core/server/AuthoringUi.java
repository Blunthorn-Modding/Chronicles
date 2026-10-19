package io.github.chronicle.core.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.AuthoringCatalog;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.ui.AuthoringUiModel.*;
import io.github.chronicle.core.content.ChestBinding;
import io.github.chronicle.core.content.ChestTarget;
import io.github.chronicle.core.loot.LootType;
import io.github.chronicle.core.network.AuthoringUiPayloads.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Operator-only UI adapter. Every mutation is checked against an exact server-derived snapshot. */
public final class AuthoringUi {
    private static final Map<MinecraftServer, Map<UUID, Context>> CONTEXTS = new IdentityHashMap<>();
    private AuthoringUi() { }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(Request.ID, (request, context) -> handle(context.player(), request));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var contexts = CONTEXTS.get(server); if (contexts != null) contexts.remove(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(CONTEXTS::remove);
    }
    public static int open(ServerCommandSource source) { return open(source, Tab.CHESTS); }
    public static int open(ServerCommandSource source, Tab tab) {
        if (source.getPlayer() == null) { source.sendError(Text.literal("Authoring UI requires a player. Advanced commands remain available.")); return 0; }
        if (!ServerPlayNetworking.canSend(source.getPlayer(), View.ID)) { source.sendError(Text.literal("Install the matching Chronicle authoring UI client pack. Chat fallback: /chronicle author menu")); return 0; }
        handle(source.getPlayer(), new Request(Action.OPEN, "", "", 0, tab, 0));
        return 1;
    }

    private static void handle(ServerPlayerEntity player, Request request) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (!player.getCommandSource().hasPermissionLevel(2)) {
            var contexts = CONTEXTS.get(server); if (contexts != null) contexts.remove(player.getUuid());
            notice(player, "Operator permission level 2 is required for Core authoring."); return;
        }
        Context context = CONTEXTS.computeIfAbsent(server, ignored -> new HashMap<>()).computeIfAbsent(player.getUuid(), ignored -> new Context());
        long now = System.nanoTime();
        if (now - context.window > 1_000_000_000L) { context.window = now; context.requests = 0; }
        if (++context.requests > 12) { notice(player, "Please wait a moment before refreshing authoring again."); return; }
        try {
            Frame frame = frame(player);
            boolean read = request.action() == Action.OPEN || request.action() == Action.REFRESH;
            if (!read && (request.token() != context.token || !frame.equals(context.frame))) {
                send(player, request, context, frame, "Selection, mode or configuration changed. Review the refreshed screen and retry.", false, true); return;
            }
            String message = "";
            boolean success = true;
            if (!read) {
                try { message = act(player, request, context, frame); }
                catch (IOException | IllegalArgumentException | IllegalStateException | com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                    message = exception.getMessage() == null ? "Authoring action failed. Refresh and inspect the server log." : exception.getMessage(); success = false;
                }
            }
            send(player, request, context, frame(player), message, success, !read);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            notice(player, "Authoring service unavailable: " + shortText(exception.getMessage(), 420));
        }
    }

    private static Frame frame(ServerPlayerEntity player) {
        var server = player.getEntityWorld().getServer();
        var campaign = ChronicleRuntime.service(server).state();
        var authoring = AuthoringRuntime.get(server);
        boolean author = AuthoringRuntime.isAuthor(player);
        return new Frame(player.getEntityWorld().getRegistryKey().getValue().toString(), target(player), campaign.phase().name(), campaign.sessionNumber(), author,
                author ? authoring.selection(player.getUuid()) : null, authoring.service().state().revision(),
                ChestAuthoring.service(server).state().bindings(), SpawnAuthoring.service(server).state().revision(),
                TypedChestLoot.types(server), RuntimeHolds.reason(server), server.getPlayerManager().getPlayerList().stream().map(ServerPlayerEntity::getUuid).toList());
    }
    private static Target target(ServerPlayerEntity player) {
        var source = player.getCommandSource();
        try {
            BlockPos pos = ChestAuthoring.target(source);
            var state = source.getWorld().getBlockState(pos);
            String dimension = source.getWorld().getRegistryKey().getValue().toString();
            String block = Registries.BLOCK.getId(state.getBlock()).toString();
            String label = block + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
            ChestTarget container = null;
            try { ChestAuthoring.supported(source.getWorld(), pos); container = new ChestTarget("target", 1, dimension, pos.getX(), pos.getY(), pos.getZ(), block); }
            catch (IllegalStateException ignored) { label += " (not a supported loot container)"; }
            return new Target(dimension + ":" + pos.toShortString() + ":" + state, label, container);
        } catch (IllegalArgumentException | IllegalStateException exception) { return new Target("", "Look at a block within six blocks; Refresh after moving.", null); }
    }

    private static String act(ServerPlayerEntity player, Request request, Context context, Frame frame) throws IOException, com.mojang.brigadier.exceptions.CommandSyntaxException {
        var server = player.getEntityWorld().getServer();
        String id = request.id(), argument = request.argument();
        switch (request.action()) {
            case CHEST_RETYPE, CHEST_UNBIND -> {
                requireAuthor(player);
                ChestBinding binding = chest(server, id);
                if (request.action() == Action.CHEST_UNBIND) {
                    ChestAuthoring.service(server).removeTypedBinding(id);
                    return "Chest unbound. Its contents and refill history are preserved.";
                }
                if (!TypedChestLoot.types(server).containsKey(argument)) throw new IllegalArgumentException("Choose a registered loot type.");
                ChestAuthoring.resolve(server, binding.directTarget());
                ChestAuthoring.service(server).putTypedBinding(argument, binding.directTarget(), true);
                return "Chest type changed. Contents are unchanged; future scheduled fills replace all contents.";
            }
            case CHEST_SHOW -> {
                var target = chest(server, id).directTarget();
                AuthoringRuntime.get(server).preview(player, new AuthoringSite.Anchor("preview", target.dimension(), 1,
                        new io.github.chronicle.core.authoring.BlockPosition(target.x(), target.y(), target.z()), target.expectedBlock()));
                return target.dimension().equals(frame.dimension) ? "Chest outline shown. Close the screen to inspect it." : "Chest is in " + target.dimension() + "; travel there to view its outline.";
            }
            default -> { }
        }
        String command = switch (request.action()) {
            case MODE_ON -> "chronicle author mode on";
            case MODE_OFF -> "chronicle author mode off";
            case CORNER_1 -> "chronicle author select pos1";
            case CORNER_2 -> "chronicle author select pos2";
            case TARGET_1 -> "chronicle author select target1";
            case TARGET_2 -> "chronicle author select target2";
            case CLEAR_SELECTION -> "chronicle author select clear";
            case HIDE_PREVIEW -> "chronicle author preview hide";
            case CHEST_BIND -> {
                if (!TypedChestLoot.types(server).containsKey(id)) throw new IllegalArgumentException("Choose a registered loot type.");
                if (frame.target.container == null) throw new IllegalArgumentException("Look at a single chest, barrel or shulker box, then Refresh.");
                yield "chronicle author chest bind " + quote(id);
            }
            case LOOT_RELOAD -> "chronicle author chest reload";
            case ROLE_SAVE, ROLE_REPLACE -> {
                var role = ChronicleCore.authoring().get(id).orElseThrow(() -> new IllegalArgumentException("Choose a registered place role."));
                yield siteCommand(role.kind().name(), id, request.action() == Action.ROLE_REPLACE);
            }
            case SITE_REPLACE -> siteCommand(kind(site(server, id)), id, true);
            case SITE_REMOVE -> { site(server, id); yield "chronicle author remove " + quote(id); }
            case SITE_SHOW -> { site(server, id); yield "chronicle author show " + quote(id); }
            case SPAWN_BIND, SPAWN_REBIND -> {
                requirePurpose(server, id);
                if (!(site(server, argument) instanceof AuthoringSite.Location)) throw new IllegalArgumentException("Choose a saved location.");
                yield "chronicle spawn " + (request.action() == Action.SPAWN_BIND ? "bind " : "rebind ") + quote(id) + " " + quote(argument);
            }
            case SPAWN_REMOVE -> { if (!SpawnAuthoring.service(server).state().bindings().containsKey(id)) throw new IllegalArgumentException("Choose a saved spawn purpose."); yield "chronicle spawn remove " + quote(id); }
            case SPAWN_PREVIEW -> {
                requirePurpose(server, id);
                var target = online(server, argument);
                yield "chronicle spawn preview " + quote(id) + " " + target.getName().getString();
            }
            case SPAWN_CONFIRM -> {
                if (!context.previewPlayer.equals(argument) || !context.previewPurpose.equals(id)) throw new IllegalStateException("Preview this purpose for this player first.");
                yield "chronicle spawn confirm " + online(server, argument).getName().getString();
            }
            case SESSION_START -> "chronicle session start";
            case SESSION_PAUSE -> "chronicle session pause";
            case SESSION_RESUME -> "chronicle session resume";
            case SESSION_END -> "chronicle session end";
            default -> throw new IllegalArgumentException("Unsupported authoring action.");
        };
        var messages = new ArrayList<String>();
        var output = new CommandOutput() {
            @Override public void sendMessage(Text text) { if (messages.size() < 8) messages.add(shortText(text.getString(), 512)); }
            @Override public boolean shouldReceiveFeedback() { return true; }
            @Override public boolean shouldTrackOutput() { return true; }
            @Override public boolean shouldBroadcastConsoleToOps() { return false; }
        };
        if (request.action() == Action.SPAWN_CONFIRM) { context.previewPlayer = ""; context.previewPurpose = ""; }
        int result = server.getCommandManager().getDispatcher().execute(command, player.getCommandSource().withOutput(output));
        String feedback = shortText(String.join("\n", messages), 1000);
        if (result <= 0) throw new IllegalStateException(feedback.isEmpty() ? "Action was refused. Refresh and check mode, target and session." : feedback);
        if (request.action() == Action.SPAWN_PREVIEW) { context.previewPlayer = argument; context.previewPurpose = id; context.previewExpires = System.nanoTime() + 60_000_000_000L; }
        else if (request.action() == Action.SPAWN_CONFIRM || request.action() == Action.SPAWN_REBIND || request.action() == Action.SPAWN_REMOVE) { context.previewPlayer = ""; context.previewPurpose = ""; }
        return feedback.isEmpty() ? "Authoring action completed." : feedback;
    }

    private static void send(ServerPlayerEntity player, Request request, Context context, Frame frame, String message, boolean success, boolean consume) {
        if (!ServerPlayNetworking.canSend(player, View.ID)) return;
        if (System.nanoTime() > context.previewExpires) { context.previewPlayer = ""; context.previewPurpose = ""; }
        if (context.frame != null && !context.frame.equals(frame)) { context.previewPlayer = ""; context.previewPurpose = ""; }
        if (consume || context.token == 0 || !frame.equals(context.frame)) context.token = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        context.frame = frame;
        List<Entry> all = entries(player, request.tab());
        int pages = Math.max(1, (all.size() + 11) / 12), page = Math.min(request.page(), pages - 1);
        List<Entry> choices = request.tab() == Tab.CHESTS ? frame.types.values().stream().map(type -> {
            long bound = frame.chests.values().stream().filter(chest -> type.id().equals(chest.lootType())).count();
            return new Entry(type.id(), type.label(), "Bound " + bound + " / recommended " + type.recommendedChests() + "; refill "
                    + (type.refillMinutes() == 0 ? "each new session" : type.refillMinutes() + " active minutes") + ". " + type.description(), "LOOT", "", 0);
        }).toList() : List.of();
        var state = new Snapshot(context.token, request.tab(), page, pages, frame.phase, frame.session, frame.author,
                AuthoringRuntime.editable(player.getEntityWorld().getServer()), frame.hold == null ? "" : shortText(frame.hold, 512),
                frame.selection == null ? "No author selection. Enable author mode to select corners." : selection(frame.selection), frame.target.label,
                shortText(message, 1024), success, all.subList(page * 12, Math.min((page + 1) * 12, all.size())), choices, context.previewPlayer);
        ServerPlayNetworking.send(player, new View(state, request.action() == Action.OPEN));
    }

    private static List<Entry> entries(ServerPlayerEntity player, Tab tab) {
        var server = player.getEntityWorld().getServer();
        var sites = AuthoringRuntime.get(server).service().state().sites();
        return switch (tab) {
            case CHESTS -> ChestAuthoring.service(server).state().bindings().values().stream().filter(ChestBinding::typed).map(binding -> {
                var target = binding.directTarget(); var type = TypedChestLoot.types(server).get(binding.lootType());
                return new Entry(binding.id(), (type == null ? binding.lootType() : type.label()) + " @ " + target.x() + " " + target.y() + " " + target.z(),
                        target.dimension() + "; " + target.expectedBlock() + "; managed contents replaced on fill", "CHEST", binding.lootType(), binding.revision());
            }).toList();
            case PLACES, SPAWN_LOCATIONS -> sites.values().stream().filter(site -> tab != Tab.SPAWN_LOCATIONS || site instanceof AuthoringSite.Location)
                    .map(site -> new Entry(site.id(), shortText(site.id(), 128), describe(site), kind(site), "", site.revision())).toList();
            case ROLES -> ChronicleCore.authoring().roles().stream().map(role -> new Entry(role.id(), role.label(), role.description(), role.kind().name(),
                    sites.containsKey(role.id()) ? role.id() : "", sites.containsKey(role.id()) ? sites.get(role.id()).revision() : 0)).toList();
            case SPAWNS -> {
                var purposes = new TreeMap<String, String>();
                ChronicleCore.authoring().roles().stream().filter(role -> role.kind() == AuthoringCatalog.Kind.LOCATION).forEach(role -> purposes.put(role.id(), role.label()));
                var bindings = SpawnAuthoring.service(server).state().bindings(); bindings.keySet().forEach(id -> purposes.putIfAbsent(id, id));
                yield purposes.entrySet().stream().map(purpose -> {
                    var binding = bindings.get(purpose.getKey());
                    return new Entry(purpose.getKey(), shortText(purpose.getValue(), 128), binding == null ? "No location assigned" : "Location: " + binding.locationId(), "SPAWN",
                            binding == null ? "" : binding.locationId(), binding == null ? 0 : binding.locationRevision());
                }).toList();
            }
            case SPAWN_PLAYERS -> server.getPlayerManager().getPlayerList().stream().map(target -> new Entry(target.getUuidAsString(), target.getName().getString(), "Online player", "PLAYER", "", 0)).toList();
            case SESSION -> List.of();
        };
    }

    private static ChestBinding chest(MinecraftServer server, String id) {
        var binding = ChestAuthoring.service(server).state().bindings().get(id);
        if (binding == null || !binding.typed()) throw new IllegalArgumentException("Choose a saved typed chest."); return binding;
    }
    private static AuthoringSite site(MinecraftServer server, String id) {
        var site = AuthoringRuntime.get(server).service().state().sites().get(id);
        if (site == null) throw new IllegalArgumentException("Choose a saved place."); return site;
    }
    private static void requireAuthor(ServerPlayerEntity player) {
        if (!AuthoringRuntime.editable(player.getEntityWorld().getServer())) throw new IllegalStateException("Pause or end the active session before editing.");
        if (!AuthoringRuntime.isAuthor(player)) throw new IllegalStateException("Enable author mode using Mode on at the bottom of this screen.");
    }
    private static void requirePurpose(MinecraftServer server, String id) {
        if (!SpawnAuthoring.service(server).state().bindings().containsKey(id) && ChronicleCore.authoring().get(id).filter(role -> role.kind() == AuthoringCatalog.Kind.LOCATION).isEmpty())
            throw new IllegalArgumentException("Choose a registered or saved spawn purpose.");
    }
    private static ServerPlayerEntity online(MinecraftServer server, String id) {
        var player = server.getPlayerManager().getPlayer(UUID.fromString(id));
        if (player == null) throw new IllegalArgumentException("That player is no longer online."); return player;
    }
    private static String siteCommand(String kind, String id, boolean replace) {
        return switch (kind) {
            case "REGION" -> "chronicle author region " + (replace ? "replace " : "save ") + quote(id);
            case "LOCATION" -> "chronicle author location " + (replace ? "replace " : "save ") + quote(id) + " here";
            case "BLOCK" -> "chronicle author block " + (replace ? "replace " : "bind ") + quote(id) + " target";
            default -> throw new IllegalArgumentException("Unsupported place geometry.");
        };
    }
    private static String kind(AuthoringSite site) { return site instanceof AuthoringSite.Region ? "REGION" : site instanceof AuthoringSite.Location ? "LOCATION" : "BLOCK"; }
    private static String describe(AuthoringSite site) {
        if (site instanceof AuthoringSite.Region region) return region.dimension() + "; " + region.min() + " to " + region.max() + "; volume " + region.volume();
        if (site instanceof AuthoringSite.Location point) return String.format(Locale.ROOT, "%s; %.2f %.2f %.2f; yaw %.1f pitch %.1f", point.dimension(), point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        var anchor = (AuthoringSite.Anchor) site; return anchor.dimension() + "; " + anchor.position() + "; " + anchor.expectedBlock();
    }
    private static String selection(AuthoringRuntime.Selection selection) { return selection.dimension() == null ? "No corners selected" : shortText(selection.dimension() + "; first " + selection.first() + "; second " + selection.second(), 512); }
    private static String quote(String value) { return StringArgumentType.escapeIfRequired(value); }
    private static String shortText(String value, int max) { return value == null ? "" : value.substring(0, Math.min(value.length(), max)); }
    private static void notice(ServerPlayerEntity player, String message) { if (ServerPlayNetworking.canSend(player, Notice.ID)) ServerPlayNetworking.send(player, new Notice(shortText(message, 512))); }
    private record Target(String key, String label, ChestTarget container) { }
    private record Frame(String dimension, Target target, String phase, int session, boolean author, AuthoringRuntime.Selection selection, long siteRevision,
                         Map<String, ChestBinding> chests, long spawnRevision, Map<String, LootType> types, String hold, List<UUID> players) { }
    private static final class Context {
        long token, window, previewExpires; int requests; Frame frame;
        String previewPlayer = "", previewPurpose = "";
    }
}
