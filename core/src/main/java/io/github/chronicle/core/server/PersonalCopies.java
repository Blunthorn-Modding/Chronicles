package io.github.chronicle.core.server;

import com.google.gson.Gson;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.books.BookDefinition;
import io.github.chronicle.core.books.BookItems;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.content.InventorySnapshot;
import io.github.chronicle.core.copies.CopyJournal;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Server-authenticated, explicit private written-book copies with conservative recovery. */
public final class PersonalCopies {
    private static final String ROOT = "chronicle_core:personal_copies:", JOURNAL = ROOT + "journal";
    private static final Map<MinecraftServer, Runtime> WORLDS = new IdentityHashMap<>();
    private static boolean registered;
    private PersonalCopies() { }
    public static synchronized void register() {
        if (registered) return; registered = true;
        ServerLifecycleEvents.SERVER_STARTED.register(PersonalCopies::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
        ServerTickEvents.END_SERVER_TICK.register(PersonalCopies::recoverOnline);
        io.github.chronicle.core.server.testing.PersonalCopyTrial.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(literal("chronicle")
                .then(literal("players").then(literal("copies").then(literal("recover").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("player", EntityArgumentType.player()).executes(c -> recoverCommand(c.getSource(), EntityArgumentType.getPlayer(c, "player")))))))));
    }
    private static void start(MinecraftServer server) {
        try {
            Runtime runtime = new Runtime(new CopyJournal(CopyJournal.file(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/personal-copies.json"))));
            WORLDS.put(server, runtime); holdPending(server, runtime);
        } catch (IOException | RuntimeException e) {
            RuntimeHolds.hold(server, JOURNAL, "Personal-copy evidence is unavailable; inspect its journal.");
            ChronicleCore.LOGGER.error("Personal-copy journal unavailable", e);
        }
    }
    public static String give(ServerPlayerEntity player, UUID request, String provider, String source, List<BookDefinition> books) throws IOException {
        Objects.requireNonNull(player); Objects.requireNonNull(request);
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null || !server.isOnThread() || server.getPlayerManager().getPlayer(player.getUuid()) != player) throw new IllegalStateException("Your connection is no longer current.");
        Runtime runtime = WORLDS.get(server); if (runtime == null || runtime.journal.poisoned()) throw new IOException("Personal-copy storage is unavailable; ask an operator to recover it.");
        if (books == null || books.isEmpty() || books.size() > 36 || books.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Choose between one and 36 books.");
        books.forEach(BookItems::validateLiteral);
        String hash = hash(new Gson().toJson(books));
        CopyJournal.Operation prior = runtime.journal.get(request);
        if (prior != null) {
            if (!prior.actor().equals(player.getUuid()) || !prior.provider().equals(provider) || !prior.source().equals(source) || !prior.hash().equals(hash) || prior.count() != books.size()) throw new IllegalArgumentException("This request identifier belongs to different copy content or recipient.");
            return switch (prior.stage()) { case COMPLETE -> "This copy request was already completed."; case CANCELLED -> "This copy request was cancelled; make a new request."; case CLAIMED -> "This copy request is awaiting recovery."; };
        }
        requireEligible(server, player);
        InventorySnapshot before = snapshot(server, player);
        List<ItemStack> stacks = new ArrayList<>(ChestAuthoring.decode(server, before));
        List<Integer> empty = new ArrayList<>(); for (int i = 0; i < 36; i++) if (stacks.get(i).isEmpty()) empty.add(i);
        if (empty.size() < books.size()) throw new IllegalStateException("Make room for " + books.size() + " books in empty inventory slots; nothing was given.");
        for (int i = 0; i < books.size(); i++) {
            ItemStack stack = ChestAuthoring.decode(server, new InventorySnapshot(1, List.of(BookItems.personal(books.get(i), request)))).getFirst();
            if (stack.isEmpty() || stack.getCount() != 1) throw new IllegalArgumentException("A personal book could not be encoded.");
            stacks.set(empty.get(i), stack);
        }
        InventorySnapshot after = ChestAuthoring.snapshot(server, new SimpleInventory(stacks.toArray(ItemStack[]::new)));
        CopyJournal.Operation operation = new CopyJournal.Operation(request, player.getUuid(), provider, source, hash, books.size(), CopyJournal.Stage.CLAIMED, before, after);
        try {
            runtime.journal.claim(operation);
            hold(server, operation);
            io.github.chronicle.core.server.testing.PersonalCopyTrial.haltAt(server, player, request, "claim");
            if (!snapshot(server, player).equals(before) || server.getPlayerManager().getPlayer(player.getUuid()) != player) throw new IllegalStateException("Inventory or connection changed during copy preparation.");
            for (int slot = 0; slot < 36; slot++) if (!before.stacks().get(slot).equals(after.stacks().get(slot))) player.getInventory().setStack(slot, stacks.get(slot).copy());
            player.getInventory().markDirty(); player.currentScreenHandler.sendContentUpdates();
            io.github.chronicle.core.server.testing.PersonalCopyTrial.haltAt(server, player, request, "apply");
            server.getPlayerManager().saveAllPlayerData();
            if (server.getPlayerManager().getPlayer(player.getUuid()) != player || !snapshot(server, player).equals(after)) throw new IllegalStateException("Inventory or connection changed during copy save; exact recovery is required.");
            io.github.chronicle.core.server.testing.PersonalCopyTrial.haltAt(server, player, request, "save");
            runtime.journal.finish(request, CopyJournal.Stage.COMPLETE);
            RuntimeHolds.release(server, owner(player.getUuid()));
            return "Your personal " + (books.size() == 1 ? "copy is" : "copies are") + " in your inventory.";
        } catch (IOException | RuntimeException failure) {
            if (runtime.journal.poisoned()) RuntimeHolds.hold(server, JOURNAL, "Personal-copy storage outcome is uncertain; use operator recovery before continuing.");
            if (runtime.journal.get(request) != null && runtime.journal.get(request).stage() == CopyJournal.Stage.CLAIMED) hold(server, operation);
            throw failure;
        }
    }
    private static void requireEligible(MinecraftServer server, ServerPlayerEntity player) {
        var campaign = ChronicleRuntime.available(server); var play = PlayRuntime.available(server);
        if (campaign == null || campaign.state().phase() != SessionPhase.ACTIVE || SessionBuilding.enabled(server) || RuntimeHolds.reason(server) != null || play == null || !play.participants().contains(player.getUuid()) || !player.isAlive() || player.isSpectator() || AuthoringRuntime.isAuthor(player) || !Departures.ready(server) || Departures.concluded(server) || Departures.departed(server, player.getUuid())) throw new IllegalStateException("Personal copies require active participation without an operation hold.");
    }
    private static InventorySnapshot snapshot(MinecraftServer server, ServerPlayerEntity player) {
        SimpleInventory inventory = new SimpleInventory(36); for (int i = 0; i < 36; i++) inventory.setStack(i, player.getInventory().getStack(i).copy()); return ChestAuthoring.snapshot(server, inventory);
    }
    private static String hash(String text) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private static String owner(UUID player) { return ROOT + player; }
    private static void hold(MinecraftServer server, CopyJournal.Operation operation) { RuntimeHolds.hold(server, owner(operation.actor()), "A personal-copy operation needs exact inventory recovery; preserve the inventory."); }
    private static void holdPending(MinecraftServer server, Runtime runtime) { for (var op : runtime.journal.pending()) hold(server, op); }
    private static void recoverOnline(MinecraftServer server) {
        Runtime runtime = WORLDS.get(server); if (runtime == null || runtime.journal.poisoned()) return;
        for (var op : runtime.journal.pending()) {
            if (op.stage() != CopyJournal.Stage.CLAIMED) continue;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(op.actor());
            if (player == null) { runtime.inspected.remove(op.request()); continue; }
            if (runtime.inspected.contains(op.request())) continue;
            runtime.inspected.add(op.request()); recover(server, runtime, op, player);
        }
    }
    private static void recover(MinecraftServer server, Runtime runtime, CopyJournal.Operation op, ServerPlayerEntity player) {
        hold(server, op);
        try {
            InventorySnapshot live = snapshot(server, player);
            CopyJournal.Evidence evidence = CopyJournal.inspect(op, live);
            if (evidence == CopyJournal.Evidence.BEFORE || evidence == CopyJournal.Evidence.AFTER) {
                server.getPlayerManager().saveAllPlayerData();
                if (!snapshot(server, player).equals(live)) throw new IllegalStateException("Inventory changed during recovery save.");
                runtime.journal.finish(op.request(), evidence == CopyJournal.Evidence.BEFORE ? CopyJournal.Stage.CANCELLED : CopyJournal.Stage.COMPLETE);
            } else return;
            RuntimeHolds.release(server, owner(op.actor()));
        } catch (IOException | RuntimeException e) {
            if (runtime.journal.poisoned()) RuntimeHolds.hold(server, JOURNAL, "Personal-copy storage outcome is uncertain; use operator recovery before continuing.");
            ChronicleCore.LOGGER.error("Personal-copy recovery held: request={} player={}", op.request(), op.actor(), e);
        }
    }
    private static int recoverCommand(net.minecraft.server.command.ServerCommandSource source, ServerPlayerEntity player) {
        MinecraftServer server = source.getServer(); Runtime runtime = WORLDS.get(server);
        if (runtime == null) {
            try { runtime = new Runtime(new CopyJournal(CopyJournal.file(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/personal-copies.json")))); WORLDS.put(server, runtime); }
            catch (IOException | RuntimeException e) { RuntimeHolds.hold(server, JOURNAL, "Personal-copy evidence is unavailable; inspect its journal."); source.sendError(Text.literal("Could not load personal-copy evidence; nothing was changed.")); return 0; }
        }
        try { runtime.journal.reload(); RuntimeHolds.release(server, JOURNAL); holdPending(server, runtime); }
        catch (IOException | RuntimeException e) { RuntimeHolds.hold(server, JOURNAL, "Personal-copy evidence is unavailable; inspect its journal."); source.sendError(Text.literal("Could not reload personal-copy evidence; nothing was changed.")); return 0; }
        var op = runtime.journal.pending().stream().filter(o -> o.actor().equals(player.getUuid())).findFirst().orElse(null);
        if (op == null) { RuntimeHolds.release(server, owner(player.getUuid())); source.sendFeedback(() -> Text.literal("No pending personal-copy evidence remains for that player."), false); return 1; }
        runtime.inspected.add(op.request()); recover(server, runtime, op, player);
        if (runtime.journal.get(op.request()).stage() != CopyJournal.Stage.CLAIMED && !runtime.journal.poisoned()) { source.sendFeedback(() -> Text.literal("Personal-copy evidence recovered."), false); return 1; }
        source.sendError(Text.literal("Recovery remains held; restore the exact recorded before or after inventory and retry. Nothing was changed.")); return 0;
    }
    private static final class Runtime { final CopyJournal journal; final Set<UUID> inspected = new HashSet<>(); Runtime(CopyJournal journal) { this.journal = journal; } }
}
