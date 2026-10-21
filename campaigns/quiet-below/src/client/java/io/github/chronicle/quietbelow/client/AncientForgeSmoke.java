package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.chronicle.core.client.ItemEnchantingScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/** Opt-in native client rehearsal for the supplied Ancient Forge workshop. */
public final class AncientForgeSmoke {
    private static final String OUTPUT_ENV = System.getenv("CHRONICLE_FORGE_SMOKE");
    private static final String PHASE_ENV = System.getenv("CHRONICLE_FORGE_PHASE");
    private static final String ROLE_ENV = System.getenv("CHRONICLE_FORGE_ROLE");
    private static final String SERVER_ENV = System.getenv("CHRONICLE_FORGE_SERVER_DIR");
    private static final String ADDRESS = "127.0.0.1:25575";
    private static final long DEADLINE_NANOS = 360_000_000_000L;
    private static final Pattern PASS = Pattern.compile("^PASS Forge ([a-z_]+) \\(\\d+ checks\\)$", Pattern.CASE_INSENSITIVE);
    private final String phase;
    private final String role;
    private final Path output;
    private final Path serverRoot;
    private final Path readyMarker;
    private final Path doneMarker;
    private final long deadline = System.nanoTime() + DEADLINE_NANOS;
    private final List<String> messages = new ArrayList<>();
    private final List<String> checks = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private int index;
    private int baseline;
    private long stepDeadline;
    private long until;
    private boolean connected;
    private boolean built;
    private boolean started;
    private boolean finished;

    private AncientForgeSmoke(String phase, String role, Path output, Path serverRoot) throws IOException {
        if (!List.of("initial", "restart").contains(phase)) throw new IllegalArgumentException("CHRONICLE_FORGE_PHASE must be initial or restart");
        if (!List.of("A", "B").contains(role)) throw new IllegalArgumentException("CHRONICLE_FORGE_ROLE must be A or B");
        this.phase = phase;
        this.role = role;
        this.output = output.toAbsolutePath().normalize();
        this.serverRoot = serverRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.serverRoot)) throw new IllegalArgumentException("CHRONICLE_FORGE_SERVER_DIR must be a staged disposable server");
        if (this.output.startsWith(this.serverRoot)) throw new IllegalArgumentException("Forge smoke output must be outside the staged server");
        Files.createDirectories(this.output);
        this.readyMarker = output.resolve("phase-B-ready.txt");
        this.doneMarker = output.resolve("phase-A-done.txt");
    }

    public static void register() {
        if (OUTPUT_ENV == null || PHASE_ENV == null || ROLE_ENV == null) return;
        try {
            if (SERVER_ENV == null) throw new IllegalArgumentException("Forge smoke staged server directory is required");
            var smoke = new AncientForgeSmoke(PHASE_ENV, ROLE_ENV, Path.of(OUTPUT_ENV), Path.of(SERVER_ENV));
            ClientReceiveMessageEvents.GAME.register((text, overlay) -> { if (!overlay && !smoke.finished) smoke.messages.add(text.getString()); });
            ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
        } catch (IOException | RuntimeException error) {
            writeSetupFailure(error);
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            require(System.nanoTime() < deadline, "Forge smoke overall timeout");
            if (client.player == null && client.currentScreen instanceof AccessibilityOnboardingScreen) throw new IllegalStateException("unexpected accessibility onboarding screen");
            if (!connected && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                connected = true;
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS), new ServerInfo("Chronicle Ancient Forge fixture", ADDRESS, ServerInfo.ServerType.OTHER), false, null);
                return;
            }
            if (client.player == null) return;
            require(client.getCurrentServerEntry() != null && ADDRESS.equals(client.getCurrentServerEntry().address), "Forge fixture address mismatch");
            if (!built) { if (role.equals("A")) buildA(client); else buildB(); built = true; }
            if (index >= steps.size()) { finish(client, true, "Forge native rehearsal complete"); return; }
            Step step = steps.get(index);
            if (!started) { baseline = messages.size(); stepDeadline = System.nanoTime() + step.timeoutMillis * 1_000_000L; step.start.run(); started = true; }
            if (step.done.getAsBoolean()) { checks.add(step.name); index++; started = false; stepDeadline = 0; }
            else require(System.nanoTime() < stepDeadline, "timed out: " + step.name);
        } catch (Throwable error) { finish(client, false, error.getClass().getSimpleName() + ": " + error.getMessage()); }
    }

    private void buildA(MinecraftClient client) {
        if (phase.equals("restart")) {
            command("restart saved Forge state", "quiet_below_forge_trial restart", "restart");
            step("client receives dormant Forge state after restart",()->{},()->{
                var state=client.world.getBlockState(new net.minecraft.util.math.BlockPos(2,65,3));
                return state.isOf(io.github.chronicle.quietbelow.QuietBelow.ANCIENT_FORGE_BLOCK)
                        &&state.get(io.github.chronicle.quietbelow.content.AncientForgeBlock.HEAT)==0;
            },20_000);return;
        }
        step("wait for passive Forge account", () -> { }, () -> Files.isRegularFile(readyMarker), 30_000);
        command("prepare Forge fixture", "quiet_below_forge_trial prepare", "prepare");
        delay("settle Forge preparation", 100);
        command("verify Forge fixture ready", "quiet_below_forge_trial ready", "ready");
        command("run Forge transaction matrix", "quiet_below_forge_trial transactions", "transactions");
        delay("settle transaction matrix", 100);
        command("recheck Forge power and station", "quiet_below_forge_trial ready", "ready");
        command("open Forge workshop offer", "quiet_below_forge_trial offer", "offer");
        step("await rendered ItemEnchantingScreen", () -> { }, () -> client.currentScreen instanceof ItemEnchantingScreen, 20_000);
        step("assert workshop fields and Sharpness I result", () -> { }, () -> {
            if (!(client.currentScreen instanceof ItemEnchantingScreen screen)) return false;
            var view = screen.state();
            require(view.target() != null && !view.target().isBlank(), "workshop target field is empty");
            require(view.ingredient() != null && !view.ingredient().isBlank(), "workshop ingredient field is empty");
            require(view.result().toLowerCase().contains("sharpness") && view.result().contains("I"), "workshop result is not Sharpness I: " + view.result());
            require(view.recipes() != null && !view.recipes().isBlank(), "workshop recipe list is empty");
            return true;
        }, 10_000);
        screenshot("capture Forge workshop at 640x480", client, "forge-workshop-640x480.png");
        step("resize Forge workshop to 1280x720", () -> client.getWindow().setWindowedSize(1280, 720), () -> client.getWindow().getFramebufferWidth() == 1280 && client.getWindow().getFramebufferHeight() == 720, 10_000);
        screenshot("capture Forge workshop at 1280x720", client, "forge-workshop-1280x720.png");
        step("refresh then close Forge workshop in one client tick", () -> {
            require(((ItemEnchantingScreen) client.currentScreen).triggerForTest("Refresh"), "Refresh button unavailable");
            ((ItemEnchantingScreen)client.currentScreen).close();
        }, () -> client.currentScreen == null, 5_000);
        delay("allow closed Forge refresh response to settle", 10);
        step("closed Forge refresh does not reopen the screen", () -> { }, () -> client.currentScreen == null, 5_000);
        command("reopen final Forge workshop offer", "quiet_below_forge_trial offer", "offer");
        step("await final rendered ItemEnchantingScreen", () -> { }, () -> client.currentScreen instanceof ItemEnchantingScreen, 20_000);
        step("confirm one real Forge enchant", () -> require(((ItemEnchantingScreen) client.currentScreen).triggerForTest("Enchant"), "Enchant button unavailable"), () -> ((ItemEnchantingScreen) client.currentScreen).state().completed(), 20_000);
        command("check Forge completed transaction", "quiet_below_forge_trial check", "check");
        step("close completed Forge workshop", () -> client.setScreen(null), () -> client.currentScreen == null, 5_000);
        command("render Forge facings and models", "quiet_below_forge_trial render", "render");
        screenshot("capture Forge facings and models", client, "forge-facings-models.png");
        command("render Forge held item and hotbar icon","quiet_below_forge_trial itemrender","itemrender");
        step("select Forge in the real client hotbar",()->{
            client.player.getInventory().setSelectedSlot(8);
            client.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(8));
        },()->client.player.getMainHandStack().isOf(io.github.chronicle.quietbelow.QuietBelow.ANCIENT_FORGE),10_000);
        delay("finish native Forge held-item equip animation",10);
        screenshot("capture Forge held item and hotbar icon",client,"forge-held-item.png");
        command("save Forge state", "quiet_below_forge_trial save", "save");
    }

    private void buildB() {
        if (!phase.equals("initial")) { step("passive restart account is not required", () -> { }, () -> true, 1_000); return; }
        step("announce passive Forge account ready", () -> writeMarker(readyMarker), () -> true, 2_000);
        step("wait for active Forge account completion", () -> { }, () -> Files.isRegularFile(doneMarker), 300_000);
    }

    private void command(String name, String command, String verb) { step(name, () -> send(command), () -> received(verb), 30_000); }
    private void send(String command) { MinecraftClient.getInstance().player.networkHandler.sendChatCommand(command); }
    private void delay(String name, int ticks) { step(name, () -> until = System.nanoTime() + ticks * 50_000_000L, () -> System.nanoTime() >= until, ticks * 50L + 5_000); }
    private void screenshot(String name, MinecraftClient client, String filename) {
        boolean[] requested = {false};
        step(name, () -> {
            client.inGameHud.getChatHud().clear(false);
            client.getToastManager().clear();
            until = System.nanoTime() + 100_000_000L;
            requested[0] = false;
        }, () -> {
            if (System.nanoTime() < until) return false;
            if (!requested[0]) {
                ScreenshotRecorder.saveScreenshot(output.toFile(), filename, client.getFramebuffer(), 1, ignored -> { });
                requested[0] = true;
            }
            return validPng(output.resolve("screenshots").resolve(filename));
        }, 20_000);
    }
    private boolean received(String verb) {
        for (String message : messages.subList(baseline, messages.size())) {
            if (message.toLowerCase().startsWith("fail forge")) throw new IllegalStateException(message);
            if (PASS.matcher(message).matches() && message.toLowerCase().contains("forge " + verb.toLowerCase())) return true;
        }
        return false;
    }
    private static boolean validPng(Path path) {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            if (channel.size() < 32) return false;
            ByteBuffer header = ByteBuffer.allocate(8), trailer = ByteBuffer.allocate(12);
            while (header.hasRemaining()) if (channel.read(header) < 0) return false;
            channel.position(channel.size() - 12);
            while (trailer.hasRemaining()) if (channel.read(trailer) < 0) return false;
            byte[] h = header.array(), t = trailer.array();
            return (h[0] & 0xFF) == 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47
                    && t[4] == 0x49 && t[5] == 0x45 && t[6] == 0x4E && t[7] == 0x44
                    && (t[8] & 0xFF) == 0xAE && (t[9] & 0xFF) == 0x42 && (t[10] & 0xFF) == 0x60 && (t[11] & 0xFF) == 0x82;
        }
        catch (IOException error) { return false; }
    }
    private void step(String name, Runnable start, BooleanSupplier done, long timeoutMillis) { steps.add(new Step(name, start, done, timeoutMillis)); }
    private void writeMarker(Path path) { try { Files.writeString(path, role + " connected\n", StandardCharsets.UTF_8); } catch (IOException error) { throw new IllegalStateException("could not write Forge coordination marker", error); } }
    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (finished) return; finished = true;
        if (role.equals("A")) writeMarker(doneMarker);
        try { JsonObject result = new JsonObject(); result.addProperty("result", pass ? "PASS" : "FAIL"); result.addProperty("phase", phase); result.addProperty("role", role); result.addProperty("detail", detail); result.add("checks", new Gson().toJsonTree(checks)); result.add("messages", new Gson().toJsonTree(messages)); Files.writeString(output.resolve("forge-" + phase + "-" + role + ".json"), new Gson().toJson(result) + "\n", StandardCharsets.UTF_8); }
        catch (IOException error) { error.printStackTrace(); }
        client.scheduleStop();
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private record Step(String name, Runnable start, BooleanSupplier done, long timeoutMillis) { }
    private static void writeSetupFailure(Throwable error) {
        try { if (OUTPUT_ENV == null) return; Path output = Path.of(OUTPUT_ENV).toAbsolutePath().normalize(); Files.createDirectories(output); JsonObject result = new JsonObject(); result.addProperty("result", "FAIL"); result.addProperty("phase", String.valueOf(PHASE_ENV)); result.addProperty("role", String.valueOf(ROLE_ENV)); result.addProperty("detail", "setup: " + error.getMessage()); Files.writeString(output.resolve("forge-" + PHASE_ENV + "-" + ROLE_ENV + ".json"), new Gson().toJson(result) + "\n", StandardCharsets.UTF_8); }
        catch (IOException | RuntimeException ignored) { }
    }
}
