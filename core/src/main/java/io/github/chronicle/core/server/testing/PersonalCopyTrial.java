package io.github.chronicle.core.server.testing;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.books.BookDefinition;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.content.InventorySnapshot;
import io.github.chronicle.core.copies.CopyJournal;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.core.server.PersonalCopies;
import io.github.chronicle.core.server.PlayRuntime;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/** Explicit hard-stop seam for a disposable native personal-copy fixture. */
public final class PersonalCopyTrial {
    public static final UUID REQUEST = UUID.fromString("cab00000-0000-4000-8000-000000000001");
    private static final Gson JSON = new Gson();
    private static final List<BookDefinition> BOOKS = List.of(new BookDefinition("test:crash_copy", "Crash fixture", "Fixture", "", List.of("Exact private fixture text."), BookDefinition.Rule.OFF));
    private static int ticks;
    private static boolean done;
    private PersonalCopyTrial() { }

    public static void register() {
        if ("true".equals(System.getenv("CHRONICLE_PERSONAL_COPY_TRIAL"))) ServerTickEvents.END_SERVER_TICK.register(PersonalCopyTrial::tick);
    }

    private static void tick(MinecraftServer server) {
        if (done) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayerList().stream().filter(p -> "ChronicleFabricA".equals(p.getName().getString())).findFirst().orElse(null);
        if (player == null) { ticks = 0; return; }
        if (++ticks < 40) return;
        done = true;
        try {
            Path root = fixture(server, player);
            Path markerPath = root.resolve(".personal-copy-halt.json");
            String phase = System.getenv("CHRONICLE_PERSONAL_COPY_HALT");
            if (phase != null && !phase.isBlank()) {
                if (!Set.of("claim", "apply", "save").contains(phase) || Files.exists(markerPath)) throw new IllegalStateException("Invalid armed trial phase.");
                for (int i = 0; i < 36; i++) player.getInventory().setStack(i, ItemStack.EMPTY);
                player.getInventory().markDirty(); player.changeGameMode(GameMode.SURVIVAL);
                ChronicleRuntime.service(server).start(new Actor("personal-copy-trial", player.getUuidAsString()), 1);
                PlayRuntime.setSelfParticipant(player, true);
                server.getPlayerManager().saveAllPlayerData();
                PersonalCopies.give(player, REQUEST, "test:provider", "crash-fixture", BOOKS);
                throw new IllegalStateException("Armed crash seam did not halt.");
            }
            JsonObject marker = BookConfig.strictObject(Files.readString(markerPath));
            String halted = marker.get("phase").getAsString();
            CopyJournal journal = new CopyJournal(CopyJournal.file(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/personal-copies.json")));
            CopyJournal.Stage expected = halted.equals("save") ? CopyJournal.Stage.COMPLETE : CopyJournal.Stage.CANCELLED;
            if (journal.get(REQUEST) == null || journal.get(REQUEST).stage() != expected) throw new IllegalStateException("Unexpected recovered receipt.");
            JsonElement evidence = marker.get(expected == CopyJournal.Stage.COMPLETE ? "after" : "before");
            InventorySnapshot live = snapshot(server, player);
            if (!JSON.toJsonTree(live).equals(evidence)) throw new IllegalStateException("Recovered inventory differs from recorded evidence.");
            String replay = PersonalCopies.give(player, REQUEST, "test:provider", "crash-fixture", BOOKS);
            if (!replay.contains(expected == CopyJournal.Stage.COMPLETE ? "already completed" : "cancelled") || !snapshot(server, player).equals(live)) throw new IllegalStateException("Replay changed inventory or receipt.");
            writeResult(root, true, halted + ": " + expected + ", exact inventory and replay verified");
        } catch (Exception failure) {
            try { writeResult(fixture(server, player), false, failure.toString()); }
            catch (Exception ignored) { }
            io.github.chronicle.core.ChronicleCore.LOGGER.error("Personal-copy crash trial failed", failure);
        }
    }

    private static InventorySnapshot snapshot(MinecraftServer server, ServerPlayerEntity player) {
        var stacks = new ArrayList<String>(36);
        RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, server.getRegistryManager());
        for (int i = 0; i < 36; i++) stacks.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, player.getInventory().getStack(i).copy()).getOrThrow().toString());
        return new InventorySnapshot(36, stacks);
    }

    private static void writeResult(Path root, boolean pass, String detail) throws IOException {
        JsonObject result = new JsonObject(); result.addProperty("result", pass ? "PASS" : "FAIL"); result.addProperty("detail", detail);
        Files.writeString(root.resolve(".personal-copy-result.json"), result + "\n", StandardCharsets.UTF_8);
    }

    public static void haltAt(MinecraftServer server, ServerPlayerEntity player, UUID request, String phase) throws IOException {
        if (!"true".equals(System.getenv("CHRONICLE_PERSONAL_COPY_TRIAL"))
                || !phase.equals(System.getenv("CHRONICLE_PERSONAL_COPY_HALT"))
                || !REQUEST.equals(request)) return;
        Path root = fixture(server, player);
        if (!Set.of("claim", "apply", "save").contains(phase)) throw new IllegalStateException("Invalid personal-copy crash phase.");
        CopyJournal journal = new CopyJournal(CopyJournal.file(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/personal-copies.json")));
        CopyJournal.Operation op = journal.get(request);
        if (op == null || op.stage() != CopyJournal.Stage.CLAIMED || !op.actor().equals(player.getUuid())) throw new IllegalStateException("Missing claimed personal-copy evidence.");
        JsonObject marker = new JsonObject();
        marker.addProperty("phase", phase); marker.addProperty("request", request.toString());
        marker.addProperty("player", player.getUuidAsString());
        marker.add("before", JSON.toJsonTree(op.before())); marker.add("after", JSON.toJsonTree(op.after()));
        Files.writeString(root.resolve(".personal-copy-halt.json"), marker + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Runtime.getRuntime().halt(99);
    }

    private static Path fixture(MinecraftServer server, ServerPlayerEntity player) {
        Path root = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
        if (root == null || root.getParent() == null || !root.getParent().getFileName().toString().equals("test-runs")
                || !root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")
                || !Files.isRegularFile(root.resolve(".personal-copy-fixture"))
                || !server.isDedicated() || server.isOnlineMode() || server.getServerPort() != 25575
                || !"127.0.0.1".equals(server.getServerIp())
                || !"ChronicleFabricA".equals(player.getName().getString())
                || server.getPlayerManager().getPlayer(player.getUuid()) != player) {
            throw new IllegalStateException("Unsafe personal-copy crash fixture.");
        }
        return root;
    }
}
