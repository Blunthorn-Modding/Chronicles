package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import io.github.chronicle.quietbelow.content.ForgeRecipeDefaults;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Real loot book use, vanilla pages, layout measurements and saved content. */
final class ForgeNotesSmoke {
    private static final String OUTPUT = System.getenv("CHRONICLE_FORGE_NOTES_SMOKE");
    private static final String PHASE = System.getenv("CHRONICLE_FORGE_NOTES_PHASE");
    private static final String ADDRESS = "127.0.0.1:25575";
    private final Path output;
    private final List<String> messages = new ArrayList<>(), checks = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private final long overall = System.nanoTime() + 180_000_000_000L;
    private int index, baseline;
    private long deadline, until;
    private boolean connected, built, started, finished;
    private ForgeNotesSmoke(Path output) { this.output = output; }
    static void register() {
        if (OUTPUT == null) return;
        var smoke = new ForgeNotesSmoke(Path.of(OUTPUT));
        ClientReceiveMessageEvents.GAME.register((text, overlay) -> { if (!overlay && !smoke.finished) smoke.messages.add(text.getString()); });
        ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
    }
    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            require(System.nanoTime() < overall, "Notes overall timeout");
            if (!connected && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                connected = true;
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS),
                        new ServerInfo("Disposable Forge notes", ADDRESS, ServerInfo.ServerType.OTHER), false, null); return;
            }
            if (client.player == null || client.player.age < 40) return;
            require(client.getCurrentServerEntry() != null && ADDRESS.equals(client.getCurrentServerEntry().address), "Wrong endpoint");
            if (!built) { build(client); built = true; }
            if (index == steps.size()) { finish(client, true, "Native notes checks passed"); return; }
            Step step = steps.get(index);
            if (!started) { baseline = messages.size(); deadline = System.nanoTime() + 20_000_000_000L; step.start.run(); started = true; }
            if (step.done.getAsBoolean()) { checks.add(step.name); index++; started = false; }
            else require(System.nanoTime() < deadline, "Timed out: " + step.name);
        } catch (Throwable error) { finish(client, false, error.toString()); }
    }
    private void build(MinecraftClient client) {
        require(List.of("initial", "restart").contains(PHASE), "Unknown notes phase");
        command(client, "move to naturally loaded chest", "tp @s 2 66 2");
        delay("load the chest chunk", 2500);
        if (PHASE.equals("restart")) { probe(client, "restart"); return; }
        probe(client, "prepare"); delay("allow real session-start fill", 1500); probe(client, "collect");
        step("receive the real loot book in hand", () -> { }, () -> client.player.getMainHandStack().isOf(Items.WRITTEN_BOOK));
        step("use written book to open vanilla reader", () -> client.interactionManager.interactItem(client.player, Hand.MAIN_HAND),
                () -> client.currentScreen instanceof BookScreen);
        for (int page = 0; page < ForgeRecipeDefaults.pages().size(); page++) {
            int number = page;
            step("measure vanilla page " + (page + 1), () -> {
                require(client.options.getGuiScale().getValue() == 2, "Expected GUI scale 2");
                require(client.textRenderer.wrapLines(Text.literal(ForgeRecipeDefaults.pages().get(number)), 114).size() * 9 <= 128, "Page overflows vanilla text area");
                require(((BookScreen) client.currentScreen).setPage(number) || number == 0, "Cannot select book page");
            }, () -> true);
            delay("render page " + (page + 1), 300);
            screenshot(client, "notes-page-" + (page + 1) + ".png");
        }
        step("close vanilla reader", () -> client.setScreen(null), () -> client.currentScreen == null);
        probe(client, "zero"); delay("allow zero-chance replacement", 1500); probe(client, "empty");
        delay("allow custom-components fill", 1500); probe(client, "custom");
    }
    private void probe(MinecraftClient client, String verb) {
        step("native notes " + verb, () -> send(client, "quiet_below_notes_trial " + verb), () -> {
            for (String message : messages.subList(baseline, messages.size())) {
                require(!message.startsWith("FAIL Notes"), message);
                if (message.equals("PASS Notes " + verb)) return true;
            }
            return false;
        });
    }
    private void command(MinecraftClient client, String name, String command) {
        step(name, () -> send(client, command), () -> messages.size() > baseline);
    }
    private void delay(String name, long millis) {
        step(name, () -> until = System.nanoTime() + millis * 1_000_000L, () -> System.nanoTime() >= until);
    }
    private void screenshot(MinecraftClient client, String filename) {
        boolean[] requested = {false};
        step("capture " + filename, () -> {
            client.inGameHud.getChatHud().clear(false);
            client.getToastManager().clear();
            until = System.nanoTime() + 200_000_000L;
        }, () -> {
            if (System.nanoTime() < until) return false;
            if (!requested[0]) {
                ScreenshotRecorder.saveScreenshot(output.toFile(), filename, client.getFramebuffer(), 1, ignored -> { });
                requested[0] = true;
            }
            return Files.isRegularFile(output.resolve("screenshots").resolve(filename));
        });
    }
    private void step(String name, Runnable start, BooleanSupplier done) { steps.add(new Step(name, start, done)); }
    private void send(MinecraftClient client, String command) { client.player.networkHandler.sendChatCommand(command); }
    private void finish(MinecraftClient client, boolean pass, String detail) {
        finished = true;
        try { Files.writeString(output.resolve("client-" + PHASE + ".json"), new Gson().toJson(Map.of(
                "result", pass ? "PASS" : "FAIL", "detail", detail, "checks", checks, "messages", messages))); }
        catch (Exception ignored) { }
        client.scheduleStop();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private record Step(String name, Runnable start, BooleanSupplier done) { }
}
