package io.github.chronicle.core.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.network.ProfilePayloads;
import io.github.chronicle.core.profile.CharacterProfile;
import io.github.chronicle.core.profile.ProfileService;
import io.github.chronicle.core.profile.ProfileStore;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Public cards; every write derives its owner from the authenticated connection. */
public final class Profiles {
    private static final Map<MinecraftServer, Profiles> SERVICES = new IdentityHashMap<>();
    private final ProfileService service;
    private final Map<UUID, Long> reads = new HashMap<>();
    private final Map<UUID, Long> writes = new HashMap<>();

    private Profiles(MinecraftServer server) {
        service = new ProfileService(new ProfileStore(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/profiles")));
    }

    public static void start(MinecraftServer server) { SERVICES.put(server, new Profiles(server)); }
    public static void stop(MinecraftServer server) { SERVICES.remove(server); }
    public static void disconnected(ServerPlayerEntity player) {
        Profiles profiles = SERVICES.get(player.getEntityWorld().getServer());
        if (profiles != null) {
            profiles.reads.remove(player.getUuid());
            profiles.writes.remove(player.getUuid());
        }
    }

    public static void registerNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(ProfilePayloads.Request.ID, (payload, context) ->
                open(context.player(), payload.owner()));
        ServerPlayNetworking.registerGlobalReceiver(ProfilePayloads.Save.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            Profiles profiles = require(player);
            if (profiles == null || !profiles.allow(player, true)) return;
            try {
                CharacterProfile saved = profiles.service.update(player.getUuid(), payload.expectedRevision(),
                        payload.name(), payload.pronouns(), payload.description());
                sendView(player, saved, true);
            } catch (IOException exception) {
                failed(player, exception);
            } catch (IllegalStateException exception) {
                notice(player, "stale");
            } catch (IllegalArgumentException exception) {
                notice(player, "invalid");
            }
        });
    }

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        var profile = literal("profile")
                .then(literal("open").executes(context -> own(context.getSource(), true))
                        .then(argument("player", EntityArgumentType.player()).executes(context ->
                                show(context.getSource(), EntityArgumentType.getPlayer(context, "player"), true))))
                .then(literal("show").executes(context -> own(context.getSource(), false))
                        .then(argument("player", EntityArgumentType.player()).executes(context ->
                                show(context.getSource(), EntityArgumentType.getPlayer(context, "player"), false))));
        var set = literal("set");
        for (String field : new String[]{"name", "pronouns", "description"}) {
            set.then(literal(field).then(argument("text", StringArgumentType.greedyString()).executes(context ->
                    edit(context.getSource(), field, StringArgumentType.getString(context, "text")))));
        }
        var clear = literal("clear");
        for (String field : new String[]{"pronouns", "description"}) {
            clear.then(literal(field).executes(context -> edit(context.getSource(), field, "")));
        }
        profile.then(set).then(clear);
        dispatcher.register(literal("chronicle").then(profile));
    }

    private static int own(ServerCommandSource source, boolean gui) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Character card commands require a player."));
            return 0;
        }
        return show(source, player, gui);
    }

    private static int show(ServerCommandSource source, ServerPlayerEntity target, boolean gui) {
        ServerPlayerEntity viewer = source.getPlayer();
        if (viewer == null) {
            source.sendError(Text.literal("Character card commands require a player."));
            return 0;
        }
        if (gui && ServerPlayNetworking.canSend(viewer, ProfilePayloads.View.ID)) {
            return open(viewer, target.getUuid()) ? 1 : 0;
        }
        Profiles profiles = require(viewer);
        if (profiles == null || !profiles.allow(viewer, false)) return 0;
        try {
            CharacterProfile card = profiles.service.get(target.getUuid(), target.getName().getString());
            source.sendFeedback(() -> Text.literal("Chronicle profile " + target.getName().getString() + ": name="
                    + card.name() + "; pronouns=" + card.pronouns() + "; description=" + card.description()
                    + "; revision=" + card.revision() + "."), false);
            if (gui) source.sendFeedback(() -> Text.translatableWithFallback("chronicle.profile.error.unsupported",
                    "Install the matching Chronicle client to open the editor. Use /chronicle profile set for text editing."), false);
            return 1;
        } catch (IOException exception) {
            failed(viewer, exception);
            return 0;
        }
    }

    private static int edit(ServerCommandSource source, String field, String value) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Character card commands require a player."));
            return 0;
        }
        Profiles profiles = require(player);
        if (profiles == null || !profiles.allow(player, true)) return 0;
        try {
            CharacterProfile card = profiles.service.get(player.getUuid(), player.getName().getString());
            CharacterProfile saved = profiles.service.update(player.getUuid(), card.revision(),
                    field.equals("name") ? value : card.name(), field.equals("pronouns") ? value : card.pronouns(),
                    field.equals("description") ? value : card.description());
            source.sendFeedback(() -> Text.literal("Chronicle profile saved; revision=" + saved.revision() + "."), false);
            return 1;
        } catch (IOException exception) {
            failed(player, exception);
            return 0;
        } catch (IllegalStateException exception) {
            notice(player, "stale");
            return 0;
        } catch (IllegalArgumentException exception) {
            notice(player, "invalid");
            return 0;
        }
    }

    private static boolean open(ServerPlayerEntity viewer, UUID targetId) {
        Profiles profiles = require(viewer);
        if (profiles == null || !profiles.allow(viewer, false)) return false;
        ServerPlayerEntity target = viewer.getEntityWorld().getServer().getPlayerManager().getPlayer(targetId);
        if (target == null) {
            notice(viewer, "offline");
            return false;
        }
        try {
            CharacterProfile card = profiles.service.get(targetId, target.getName().getString());
            sendView(viewer, card, false);
            return true;
        } catch (IOException exception) {
            failed(viewer, exception);
            return false;
        }
    }

    private static void sendView(ServerPlayerEntity player, CharacterProfile card, boolean saved) {
        if (ServerPlayNetworking.canSend(player, ProfilePayloads.View.ID)) {
            ServerPlayNetworking.send(player, new ProfilePayloads.View(card.owner(), card.name(), card.pronouns(),
                    card.description(), card.revision(), player.getUuid().equals(card.owner()), saved));
        } else if (saved) {
            player.sendMessage(Text.literal("Chronicle profile saved; revision=" + card.revision() + "."), false);
        } else {
            notice(player, "unsupported");
        }
    }

    private static Profiles require(ServerPlayerEntity player) {
        if (!player.getEntityWorld().getServer().isOnThread()) throw new IllegalStateException("Profile operations require the server thread");
        Profiles profiles = SERVICES.get(player.getEntityWorld().getServer());
        if (profiles == null) notice(player, "unavailable");
        return profiles;
    }

    private boolean allow(ServerPlayerEntity player, boolean write) {
        Map<UUID, Long> limits = write ? writes : reads;
        long now = System.nanoTime();
        Long previous = limits.get(player.getUuid());
        if (previous != null && now - previous < (write ? 1_000_000_000L : 200_000_000L)) {
            notice(player, "throttled");
            return false;
        }
        limits.put(player.getUuid(), now);
        return true;
    }

    private static void failed(ServerPlayerEntity player, IOException exception) {
        ChronicleCore.LOGGER.error("Character card operation failed for {}", player.getUuid(), exception);
        notice(player, "unavailable");
    }

    private static void notice(ServerPlayerEntity player, String reason) {
        String key = "chronicle.profile.error." + reason;
        if (ServerPlayNetworking.canSend(player, ProfilePayloads.Notice.ID)) {
            ServerPlayNetworking.send(player, new ProfilePayloads.Notice(key));
        } else {
            String fallback = switch (reason) {
                case "stale" -> "This card changed. Reopen it before saving again.";
                case "invalid" -> "Invalid character card. Use a name up to 48 characters, pronouns up to 32 and description up to 512, without formatting or control characters.";
                case "throttled" -> "Please wait before requesting or saving another character card.";
                case "offline" -> "That player is no longer online.";
                case "unsupported" -> "Install the matching Chronicle client to open the editor. Use /chronicle profile show or set for text access.";
                default -> "Character card unavailable. Check the server log; no save was applied.";
            };
            player.sendMessage(Text.translatableWithFallback(key, fallback), false);
        }
    }
}
