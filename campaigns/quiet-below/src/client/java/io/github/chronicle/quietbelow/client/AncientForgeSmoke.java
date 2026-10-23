package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.chronicle.core.client.ItemEnchantingScreen;
import io.github.chronicle.core.screen.ItemEnchantingScreenHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;

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
    private java.util.concurrent.CompletableFuture<Void> packReload;
    private List<String> originalPacks;
    private String modelObservation = "";

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
        } catch (Throwable error) { finish(client, false, error.getClass().getSimpleName() + ": " + error.getMessage() + modelObservation); }
    }

    private void buildA(MinecraftClient client) {
        if (phase.equals("restart")) {
            command("restart saved Forge state", "quiet_below_forge_trial restart", "restart");
            step("client receives dormant Forge state after restart",()->{},()->{
                var state=client.world.getBlockState(new net.minecraft.util.math.BlockPos(2,65,3));
                return state.isOf(io.github.chronicle.quietbelow.QuietBelow.ANCIENT_FORGE_BLOCK)
                        &&state.get(io.github.chronicle.quietbelow.content.AncientForgeBlock.HEAT)==0
                        &&state.getLuminance()==0
                        &&client.world.getLightLevel(net.minecraft.world.LightType.BLOCK,new net.minecraft.util.math.BlockPos(2,66,3))==0;
            },20_000);return;
        }
        step("wait for passive Forge account", () -> { }, () -> Files.isRegularFile(readyMarker), 30_000);
        command("prepare Forge fixture", "quiet_below_forge_trial prepare", "prepare");
        delay("settle Forge preparation", 100);
        command("verify Forge fixture ready", "quiet_below_forge_trial ready", "ready");
        readinessModels(client);
        command("run Forge real-slot transaction matrix", "quiet_below_forge_trial transactions", "transactions");
        delay("settle transaction matrix", 100);
        command("recheck Forge power and station", "quiet_below_forge_trial ready", "ready");
        command("open Forge workshop offer", "quiet_below_forge_trial offer", "offer");
        step("await rendered ItemEnchantingScreen", () -> { }, () -> client.currentScreen instanceof ItemEnchantingScreen, 20_000);
        step("assert empty real staging slots and supplied native inputs", () -> { }, () -> {
            if (!(client.currentScreen instanceof ItemEnchantingScreen screen)) return false;
            var view = screen.state();
            require(view.state() == io.github.chronicle.core.network.ItemEnchantingPayloads.State.EMPTY_TARGET, "empty Forge did not report an empty target");
            require(!screen.getScreenHandler().getSlot(30).getStack().isEmpty() && !screen.getScreenHandler().getSlot(31).getStack().isEmpty(), "supplied target/material are not in the native hotbar slots");
            return view.recipes() != null && !view.recipes().isEmpty();
        }, 10_000);
        screenshot("capture Forge workshop at 640x480", client, "forge-workshop-640x480.png");
        step("open Forge recipe guide at 640x480", () -> require(((ItemEnchantingScreen) client.currentScreen).triggerForTest("Recipes"), "Recipes button unavailable at 640x480"), () -> client.currentScreen instanceof ItemEnchantingScreen, 5_000);
        screenshot("capture Forge recipe guide at 640x480", client, "forge-guide-640x480.png");
        step("return from Forge recipe guide at 640x480", () -> require(((ItemEnchantingScreen) client.currentScreen).triggerForTest("Back"), "Recipe guide back button unavailable at 640x480"), () -> client.currentScreen instanceof ItemEnchantingScreen, 5_000);
        step("resize Forge workshop to 1280x720", () -> client.getWindow().setWindowedSize(1280, 720), () -> client.getWindow().getFramebufferWidth() == 1280 && client.getWindow().getFramebufferHeight() == 720, 10_000);
        screenshot("capture Forge workshop at 1280x720", client, "forge-workshop-1280x720.png");
        step("open same-handler recipe guide", () -> {
            var screen = (ItemEnchantingScreen) client.currentScreen;
            require(screen.triggerForTest("Recipes"), "Recipes button unavailable");
        }, () -> client.currentScreen instanceof ItemEnchantingScreen screen && screen.recipePageForTest() == 0, 5_000);
        screenshot("capture Forge recipe guide at 1280x720", client, "forge-guide-1280x720.png");
        step("guide hides native slots and hotkeys while preserving the same handler", () -> {
            var screen = (ItemEnchantingScreen) client.currentScreen;
            require(screen.getScreenHandler() == client.player.currentScreenHandler, "guide replaced native handler");
            require(screen.getScreenHandler().getSlot(0).getStack().isEmpty() && screen.getScreenHandler().getSlot(1).getStack().isEmpty(), "guide began with hidden staged inputs");
            var click = slotClick(screen, 0);
            require(screen.mouseClicked(click, false), "guide did not consume hidden-slot mouse click"); screen.mouseReleased(click);
            require(screen.keyPressed(new net.minecraft.client.input.KeyInput(49, 0, 0)), "guide did not consume hidden hotbar key");
            require(screen.getScreenHandler().getSlot(0).getStack().isEmpty() && screen.getScreenHandler().getSlot(1).getStack().isEmpty(), "guide hidden action staged an item");
            require(screen.triggerForTest("Back"), "Recipe guide back button unavailable");
        }, () -> client.currentScreen instanceof ItemEnchantingScreen, 5_000);
        resourcePack(client);
        step("stage target and material through actual native shift-clicks", () -> {
            var screen = (ItemEnchantingScreen) client.currentScreen;
            client.interactionManager.clickSlot(screen.getScreenHandler().syncId, 30, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, client.player);
            client.interactionManager.clickSlot(screen.getScreenHandler().syncId, 31, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, client.player);
        }, () -> client.currentScreen instanceof ItemEnchantingScreen screen
                && screen.state().state() == io.github.chronicle.core.network.ItemEnchantingPayloads.State.READY
                && !screen.getScreenHandler().getSlot(2).getStack().isEmpty(), 20_000);
        step("move native cursor over result for tooltip capture", () -> {
            var screen = (ItemEnchantingScreen) client.currentScreen;
            double scale = client.getWindow().getScaleFactor();
            moveCursor(client, ((screen.width - ItemEnchantingScreenHandler.WIDTH) / 2.0 + screen.getScreenHandler().getSlot(2).x + 8) * scale, ((screen.height - ItemEnchantingScreenHandler.HEIGHT) / 2.0 + screen.getScreenHandler().getSlot(2).y + 8) * scale);
        }, () -> true, 5_000);
        screenshot("capture staged Forge result tooltip", client, "forge-result-tooltip.png");
        step("take Forge result using its actual screen left-click", () -> {
            var screen = (ItemEnchantingScreen) client.currentScreen;
            var click = slotClick(screen, 2);
            require(screen.mouseClicked(click, false), "result screen click was unhandled");
            screen.mouseReleased(click);
        }, () -> client.currentScreen instanceof ItemEnchantingScreen screen && !screen.pending() && screen.getScreenHandler().getSlot(0).getStack().isEmpty() && !screen.getScreenHandler().getCursorStack().isEmpty(), 20_000);
        command("check Forge completed real-slot transaction", "quiet_below_forge_trial check", "check");
        step("place real cursor result into a native player inventory slot", () -> {
            var screen = (ItemEnchantingScreen) client.currentScreen;
            client.interactionManager.clickSlot(screen.getScreenHandler().syncId, 30, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, client.player);
        }, () -> client.currentScreen instanceof ItemEnchantingScreen screen && screen.getScreenHandler().getCursorStack().isEmpty() && !screen.getScreenHandler().getSlot(30).getStack().isEmpty(), 10_000);
        step("close Forge using the normal screen close path", () -> ((ItemEnchantingScreen) client.currentScreen).close(), () -> client.currentScreen == null, 5_000);
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

    private void readinessModels(MinecraftClient client) {
        modelStep(client, "model_off", 0, "small network is off");
        screenshot("capture actual off Forge at night", client, "forge-off-network.png");
        modelStep(client, "model_kindling", 1, "halfway network shows kindling");
        screenshot("capture actual kindling Forge at night", client, "forge-kindling-network.png");
        modelStep(client, "model_active", 2, "powered Forge stays active and emits light");
        screenshot("capture actual active Forge light at night", client, "forge-active-light.png");
        modelStep(client, "model_cut", 0, "cut roots remove power and light");
        modelStep(client, "model_reconnect", 2, "physical reconnection restores active light");
        modelStep(client, "model_isolated", 0, "healthy remote sculk cannot power an isolated Forge");
        modelStep(client, "model_adjacent", 2, "restored physical adjacency restores power");
        modelStep(client, "model_disable", 0, "disabled ecology cannot warm the Forge");
        modelStep(client, "model_enable", 2, "enabled ecology restores active readiness");
        modelStep(client, "model_saturated_pause", 0, "full bounded tracker extinguishes its final Forge");
        modelStep(client, "model_resume", 2, "resuming returns to the active Forge");
    }
    private void modelStep(MinecraftClient client, String verb, int expected, String description) {
        command(description + " request", "quiet_below_forge_trial " + verb, verb);
        step(description + " native state/light", () -> { }, () -> {
            var at = new net.minecraft.util.math.BlockPos(2, 65, 3);
            var state = client.world.getBlockState(at);
            modelObservation = " [" + verb + ": " + state + ", block light=" + client.world.getLightLevel(net.minecraft.world.LightType.BLOCK, at.up()) + "]";
            if (!state.isOf(io.github.chronicle.quietbelow.QuietBelow.ANCIENT_FORGE_BLOCK)
                    || state.get(io.github.chronicle.quietbelow.content.AncientForgeBlock.HEAT) != expected
                    || state.getLuminance() != (expected == 2 ? 12 : 0)) return false;
            int light = client.world.getLightLevel(net.minecraft.world.LightType.BLOCK, at.up());
            return expected == 2 ? light >= 10 : light == 0;
        }, 25_000);
        command(description + " server proof", "quiet_below_forge_trial model_check", "model_check");
    }

    private void resourcePack(MinecraftClient client) {
        step("load original native workstation sprite/font fixture", () -> loadPack(client),
                () -> packReload != null && packReload.isDone() && !packReload.isCompletedExceptionally() && client.getOverlay() == null, 40_000);
        step("native resource reload preserves workstation identity", () -> {
            require(client.currentScreen instanceof ItemEnchantingScreen, "resource reload replaced the workstation");
            require(client.textRenderer.getWidth("Ancient Forge") <= 160, "pack font clips workstation title");
        }, () -> true, 5_000);
        screenshot("capture resource-pack workstation", client, "forge-resource-pack.png");
        step("open resource-pack guide", () -> require(((ItemEnchantingScreen) client.currentScreen).triggerForTest("Recipes"), "pack guide unavailable"), () -> true, 5_000);
        screenshot("capture resource-pack guide", client, "forge-guide-resource-pack.png");
        step("restore vanilla resource selection", () -> {
            require(((ItemEnchantingScreen) client.currentScreen).triggerForTest("Back"), "pack guide back unavailable");
            client.getResourcePackManager().setEnabledProfiles(originalPacks);
            packReload = client.reloadResources();
        }, () -> packReload.isDone() && !packReload.isCompletedExceptionally() && client.getOverlay() == null, 40_000);
    }

    private void loadPack(MinecraftClient client) {
        try {
            Path root = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("resourcepacks/chronicle-forge-contrast");
            Files.createDirectories(root);
            Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"min_format\":[69,0],\"max_format\":[69,0],\"description\":\"Original Chronicle workstation contrast fixture\"}}");
            Path assets = root.resolve("assets/minecraft");
            // Original test artwork, never copied from a third-party resource pack or distributed.
            Path workshop = root.resolve("assets/chronicle_core/textures/gui/sprites/workshop");
            fixtureSprite(workshop.resolve("panel.png"), 24, 24, 0xFFC2DFCE, 0xFF315B48, 2);
            Files.writeString(workshop.resolve("panel.png.mcmeta"), "{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":24,\"height\":24,\"border\":2}}}");
            fixtureSprite(workshop.resolve("slot.png"), 18, 18, 0xFF9BBCAF, 0xFF315B48);
            fixtureSprite(workshop.resolve("result.png"), 18, 18, 0xFF9BBCAF, 0xFF547A68);
            fixtureSprite(workshop.resolve("plus.png"), 10, 10, 0xFF284B3D, 0xFF59846D);
            fixtureSprite(workshop.resolve("arrow.png"), 28, 14, 0xFF284B3D, 0xFF59846D);
            fixtureSprite(assets.resolve("textures/gui/sprites/popup/background.png"), 236, 34, 0xFF14372D, 0xFF8DBCA4);
            Files.writeString(assets.resolve("textures/gui/sprites/popup/background.png.mcmeta"), "{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":236,\"height\":34,\"border\":6}}}");
            for (String name : List.of("button", "button_highlighted", "button_disabled"))
                fixtureSprite(assets.resolve("textures/gui/sprites/widget/" + name + ".png"), 200, 20, name.equals("button_disabled") ? 0xFF54645C : 0xFF315B48, 0xFF8DBCA4);
            Path font = assets.resolve("font/default.json");
            Files.createDirectories(font.getParent());
            Files.writeString(font, "{\"providers\":[{\"type\":\"reference\",\"id\":\"minecraft:uniform\"}]}");
            var manager = client.getResourcePackManager();
            originalPacks = List.copyOf(manager.getEnabledIds());
            manager.scanPacks();
            String id = manager.getProfiles().stream().filter(profile -> profile.getId().endsWith("chronicle-forge-contrast")).findFirst().orElseThrow().getId();
            var enabled = new ArrayList<>(originalPacks);
            enabled.add(id);
            manager.setEnabledProfiles(enabled);
            packReload = client.reloadResources();
        } catch (IOException error) { throw new IllegalStateException(error); }
    }

    private static void fixtureSprite(Path path, int width, int height, int interior, int border) throws IOException {
        fixtureSprite(path, width, height, interior, border, 3);
    }
    private static void fixtureSprite(Path path, int width, int height, int interior, int border, int borderWidth) throws IOException {
        Files.createDirectories(path.getParent());
        var png = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            png.setRGB(x, y, x < borderWidth || y < borderWidth || x >= width - borderWidth || y >= height - borderWidth ? border : interior);
        javax.imageio.ImageIO.write(png, "PNG", path.toFile());
    }

    private void buildB() {
        if (!phase.equals("initial")) { step("passive restart account is not required", () -> { }, () -> true, 1_000); return; }
        step("announce passive Forge account ready", () -> writeMarker(readyMarker), () -> true, 2_000);
        step("wait for active Forge account completion", () -> { }, () -> Files.isRegularFile(doneMarker), 300_000);
    }

    private void command(String name, String command, String verb) { step(name, () -> send(command), () -> received(verb), 30_000); }
    private void send(String command) { MinecraftClient.getInstance().player.networkHandler.sendChatCommand(command); }
    private void delay(String name, int ticks) { step(name, () -> until = System.nanoTime() + ticks * 50_000_000L, () -> System.nanoTime() >= until, ticks * 50L + 5_000); }
    private static net.minecraft.client.gui.Click slotClick(ItemEnchantingScreen screen, int index) {
        var slot = screen.getScreenHandler().getSlot(index);
        return new net.minecraft.client.gui.Click((screen.width - ItemEnchantingScreenHandler.WIDTH) / 2 + slot.x + 8,
                (screen.height - ItemEnchantingScreenHandler.HEIGHT) / 2 + slot.y + 8, new net.minecraft.client.input.MouseInput(0, 0));
    }

    private static void moveCursor(MinecraftClient client, double x, double y) {
        GLFW.glfwFocusWindow(client.getWindow().getHandle());
        GLFW.glfwSetCursorPos(client.getWindow().getHandle(), x, y);
        // Concurrent native test windows may not receive a Windows cursor callback.
        // Dispatch the same pinned native callback so the real screen renders its ordinary hover tooltip.
        try {
            var callback = net.minecraft.client.Mouse.class.getDeclaredMethod("onCursorPos", long.class, double.class, double.class);
            callback.setAccessible(true);
            callback.invoke(client.mouse, client.getWindow().getHandle(), x, y);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("native cursor fixture callback failed", error); }
    }

    private void screenshot(String name, MinecraftClient client, String filename) {
        boolean[] requested = {false};
        step(name, () -> {
            client.inGameHud.getChatHud().clear(false);
            client.getToastManager().clear();
            if (!filename.contains("tooltip")) {
                moveCursor(client, 0, 0);
            }
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
