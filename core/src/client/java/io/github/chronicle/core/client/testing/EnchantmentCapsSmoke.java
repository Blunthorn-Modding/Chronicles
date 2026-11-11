package io.github.chronicle.core.client.testing;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.menu.CoreMenuModel.Page;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Opt-in client proof for the permanent native enchantment-cap path. */
public final class EnchantmentCapsSmoke {
    private static final String OUTPUT_ENV = System.getenv("CHRONICLE_CAPS_SMOKE");
    private static final String PHASE_ENV = System.getenv("CHRONICLE_CAPS_PHASE");
    private static final String SERVER_ENV = System.getenv("CHRONICLE_CAPS_SERVER_DIR");
    private static final String ADDRESS = "127.0.0.1:25575";
    private static final long DEADLINE_NANOS = 240_000_000_000L;
    private static final Pattern PASS = Pattern.compile("^Enchantment trial PASS (basic|vanilla|check_creative|save|restart)(?: .*)?$", Pattern.CASE_INSENSITIVE);
    private static EnchantmentCapsSmoke instance;

    private final String phase;
    private final Path output;
    private final Path serverRoot;
    private final long deadline = System.nanoTime() + DEADLINE_NANOS;
    private final List<String> messages = new ArrayList<>();
    private final List<String> checks = new ArrayList<>();
    private int step;
    private boolean connecting;
    private boolean complete;
    private long stepDeadline;

    private EnchantmentCapsSmoke(String phase, Path output, Path serverRoot) throws IOException {
        if (!List.of("initial", "restart").contains(phase)) throw new IllegalArgumentException("CHRONICLE_CAPS_PHASE must be initial or restart");
        this.phase = phase;
        this.output = output.toAbsolutePath().normalize();
        this.serverRoot = serverRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.serverRoot)) throw new IllegalArgumentException("CHRONICLE_CAPS_SERVER_DIR must be a staged disposable server");
        if (this.output.startsWith(this.serverRoot)) throw new IllegalArgumentException("caps smoke output must be outside the staged server");
        Files.createDirectories(this.output);
    }

    /** Registers only for the dedicated disposable capability smoke. */
    public static void register() {
        if (OUTPUT_ENV == null) return;
        try {
            if (PHASE_ENV == null || SERVER_ENV == null) throw new IllegalArgumentException("caps smoke phase and staged server directory are required");
            instance = new EnchantmentCapsSmoke(PHASE_ENV, Path.of(OUTPUT_ENV), Path.of(SERVER_ENV));
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay && !instance.complete) instance.messages.add(message.getString()); });
            ClientTickEvents.END_CLIENT_TICK.register(instance::tick);
        } catch (IOException | RuntimeException error) {
            ChronicleCore.LOGGER.error("Enchantment caps smoke setup failed", error);
            writeSetupFailure(error);
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private void tick(MinecraftClient client) {
        if (complete) return;
        try {
            require(System.nanoTime() < deadline, "overall 240 second deadline exceeded");
            if (!connecting && client.player == null && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                connecting = true;
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS), new ServerInfo("Chronicle enchantment caps smoke", ADDRESS, ServerInfo.ServerType.OTHER), false, null);
                return;
            }
            if (client.player == null) return;
            require(client.getCurrentServerEntry() != null && ADDRESS.equals(client.getCurrentServerEntry().address), "caps smoke connected outside loopback fixture");
            if (phase.equals("initial")) initial(client); else restart(client);
        } catch (Throwable error) {
            finish(client, false, error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void initial(MinecraftClient client) {
        switch (step) {
            case 0 -> { checkNativeClientLevel(client); advance("client retains vanilla Sharpness V and max level 5"); }
            case 1 -> { sendCreativeSword(client); advance("sent Sharpness V creative packet and predicted local level 5"); }
            case 2 -> await(() -> level(client.player.getInventory().getStack(0), client) == 1, "authoritative creative slot lowered to Sharpness I");
            case 3 -> command(client, "chronicle_enchantment_trial check_creative", "check_creative");
            case 4 -> command(client, "chronicle editing on", null);
            case 5 -> command(client, "chronicle_enchantment_trial basic", "basic");
            case 6 -> command(client, "chronicle_enchantment_trial vanilla", "vanilla");
            case 7 -> command(client, "chronicle_enchantment_trial save", "save");
            case 8 -> openMenu(client);
            case 9 -> navigateSetup(client);
            case 10 -> selectCapsTool(client);
            case 11 -> awaitCapsTool(client);
            case 12 -> { capture(client, "cap-tool-640x480.png"); advance("captured enchantment caps tool at 640x480"); }
            case 13 -> await(() -> screenshotComplete("cap-tool-640x480.png"),"small screenshot finished writing");
            case 14 -> { client.getWindow().setWindowedSize(1280, 720); advance("resized enchantment caps tool to 1280x720"); }
            case 15 -> await(() -> client.getWindow().getFramebufferWidth()==1280&&client.getWindow().getFramebufferHeight()==720,"large framebuffer resize completed");
            case 16 -> { capture(client, "cap-tool-1280x720.png"); advance("captured enchantment caps tool at 1280x720"); }
            case 17 -> await(() -> screenshotComplete("cap-tool-1280x720.png"),"large screenshot finished writing");
            case 18 -> assertCapsControls(client);
            default -> finish(client, true, "initial enchantment cap rehearsal complete");
        }
    }

    private void restart(MinecraftClient client) {
        if (step == 0) command(client, "chronicle_enchantment_trial restart", "restart");
        else finish(client, true, "restart enchantment cap persistence rehearsal complete");
    }

    private void openMenu(MinecraftClient client) {
        client.player.networkHandler.sendChatCommand("chronicle setup");
        advance("opened Core Game setup menu");
    }

    private void navigateSetup(MinecraftClient client) {
        if (!(client.currentScreen instanceof CoreMenuScreen screen) || screen.pending()) return;
        if (screen.state().page() == Page.SETUP) { advance("Core Game setup page rendered"); return; }
        require(screen.triggerForTest("Game setup"), "Game setup navigation unavailable");
    }

    private void selectCapsTool(MinecraftClient client) {
        if (!(client.currentScreen instanceof CoreMenuScreen screen) || screen.pending() || screen.state().page() != Page.SETUP) return;
        if (screen.state().entries().stream().anyMatch(entry -> entry.id().equals("tool:chronicle_core:enchantment_caps"))) {
            screen.selectForTest("tool:chronicle_core:enchantment_caps");
            advance("selected registered Enchantment caps tool");
        } else {
            require(screen.state().index() + 1 < screen.state().pages(), "Enchantment caps tool is not reachable in setup pages");
            require(screen.triggerForTest(">"), "setup page navigation unavailable");
        }
    }

    private void awaitCapsTool(MinecraftClient client) {
        if (!(client.currentScreen instanceof CoreMenuScreen screen) || screen.pending()) return;
        require(screen.state().page() == Page.SETUP, "Core Game setup page closed while opening Enchantment caps");
        require(screen.state().detail() != null && screen.state().detail().id().equals("tool:chronicle_core:enchantment_caps"), "Enchantment caps detail did not arrive");
        advance("Enchantment caps detail rendered with authoritative state");
    }

    private void assertCapsControls(MinecraftClient client) {
        CoreMenuScreen screen = menu(client);
        require(screen.state().detail() != null && screen.state().detail().id().equals("tool:chronicle_core:enchantment_caps"), "Enchantment caps detail disappeared before assertions");
        var controls = screen.state().detail().controls();
        var enable = controls.stream().filter(control -> control.label().equals("Enable permanent caps")).findFirst().orElseThrow();
        var disable = controls.stream().filter(control -> control.label().equals("Disable future reductions")).findFirst().orElseThrow();
        var lower = controls.stream().filter(control -> control.label().equals("Lower default maximum")).findFirst().orElseThrow();
        require(!enable.enabled() && !enable.reason().isBlank(), "already-enabled cap action was not disabled with guidance");
        require(disable.enabled() && disable.confirm(), "disable action is not enabled and confirmation-gated");
        require(lower.enabled() && lower.confirm(), "lower action is not enabled and confirmation-gated");
        require(screen.state().detail().facts().stream().anyMatch(fact -> fact.label().equals("Irreversible") && fact.value().toLowerCase().contains("permanent")), "irreversible warning is missing from cap tool facts");
        advance("verified cap tool disabled Enable, enabled confirmation-gated Disable/Lower, and irreversible warning");
    }

    private static CoreMenuScreen menu(MinecraftClient client) {
        if (!(client.currentScreen instanceof CoreMenuScreen screen)) throw new IllegalStateException("Core Game setup screen is not open");
        return screen;
    }

    private void capture(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(output.toFile(), name, client.getFramebuffer(), 1, ignored -> { });
    }

    private boolean screenshotComplete(String name) {
        Path file=output.resolve("screenshots").resolve(name);
        try {
            if (!Files.isRegularFile(file)||Files.size(file)<24) return false;
            try(var channel=Files.newByteChannel(file)) {
                channel.position(channel.size()-12);var end=java.nio.ByteBuffer.allocate(12);
                while(end.hasRemaining())if(channel.read(end)<0)return false;
                return java.util.Arrays.equals(end.array(),new byte[]{0,0,0,0,73,69,78,68,(byte)174,66,96,(byte)130});
            }
        } catch(IOException error) { return false; }
    }

    private void checkNativeClientLevel(MinecraftClient client) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        RegistryEntry<Enchantment> sharpness = sharpness(client);
        var builder=new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);builder.set(sharpness,5);
        sword.set(DataComponentTypes.ENCHANTMENTS,builder.build());
        require(level(sword, client) == 5, "client Sharpness V component was changed");
        require(sharpness.value().getMaxLevel() == 5, "native Sharpness max level is not 5");
    }

    private void sendCreativeSword(MinecraftClient client) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        RegistryEntry<Enchantment> sharpness = sharpness(client);
        var builder=new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);builder.set(sharpness,5);
        sword.set(DataComponentTypes.ENCHANTMENTS,builder.build());
        client.player.getInventory().setStack(0, sword.copy());
        require(level(client.player.getInventory().getStack(0), client) == 5, "local creative prediction did not retain Sharpness V");
        client.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(36, sword));
    }

    private int level(ItemStack stack, MinecraftClient client) {
        return stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).getLevel(sharpness(client));
    }

    private RegistryEntry<Enchantment> sharpness(MinecraftClient client) {
        return client.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
    }

    private void command(MinecraftClient client, String command, String expectedVerb) {
        if (stepDeadline == 0) {
            client.player.networkHandler.sendChatCommand(command);
            stepDeadline = System.nanoTime() + 20_000_000_000L;
        }
        require(System.nanoTime() < stepDeadline, "timed out waiting for Enchantment trial PASS " + expectedVerb);
        if (expectedVerb==null) {
            if(messages.stream().anyMatch(m->m.startsWith("Editing enabled."))) advance("Editing acknowledged by server");
        } else if (received(expectedVerb)) advance("received Enchantment trial PASS " + expectedVerb);
    }

    private boolean received(String verb) {
        for (String message : messages) {
            if (message.toLowerCase().contains("enchantment trial fail")) throw new IllegalStateException(message);
            if (PASS.matcher(message).matches() && message.toLowerCase().contains("pass " + verb.toLowerCase())) return true;
        }
        return false;
    }

    private void await(Check check, String description) {
        if (stepDeadline == 0) stepDeadline = System.nanoTime() + 20_000_000_000L;
        require(System.nanoTime() < stepDeadline, "timed out waiting for " + description);
        if (check.ok()) { checks.add(description); step++; stepDeadline = 0; }
    }

    private void advance(String description) { checks.add(description); step++; stepDeadline = 0; }

    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (complete) return;
        complete = true;
        try {
            JsonObject result = new JsonObject();
            result.addProperty("result", pass ? "PASS" : "FAIL"); result.addProperty("phase", phase);
            result.addProperty("detail", detail); result.add("checks", new Gson().toJsonTree(checks));
            result.add("messages", new Gson().toJsonTree(messages));
            Files.writeString(output.resolve("cap-" + phase + ".json"), new Gson().toJson(result) + "\n", StandardCharsets.UTF_8);
        } catch (IOException error) { ChronicleCore.LOGGER.error("Unable to write enchantment caps smoke result", error); }
        client.scheduleStop();
    }

    private static void require(boolean condition, String detail) { if (!condition) throw new IllegalStateException(detail); }
    @FunctionalInterface private interface Check { boolean ok(); }

    private static void writeSetupFailure(Throwable error) {
        try {
            if (OUTPUT_ENV == null) return;
            Path directory = Path.of(OUTPUT_ENV).toAbsolutePath().normalize();
            if (SERVER_ENV != null && directory.startsWith(Path.of(SERVER_ENV).toAbsolutePath().normalize())) return;
            Files.createDirectories(directory);
            JsonObject result = new JsonObject(); result.addProperty("result", "FAIL"); result.addProperty("phase", String.valueOf(PHASE_ENV)); result.addProperty("detail", "setup: " + error.getMessage());
            Files.writeString(directory.resolve("cap-" + PHASE_ENV + ".json"), new Gson().toJson(result) + "\n", StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) { }
    }
}
