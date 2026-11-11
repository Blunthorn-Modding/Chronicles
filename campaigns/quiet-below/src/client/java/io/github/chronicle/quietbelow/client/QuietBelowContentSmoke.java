package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import io.github.chronicle.quietbelow.QuietBelow;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opt-in real-client M3 content verification against the disposable fixed-address fixture only. */
final class QuietBelowContentSmoke {
    private static final String OUTPUT = System.getenv("CHRONICLE_CONTENT_SMOKE");
    private static final String FIXTURE_ADDRESS = "127.0.0.1:25575";
    private static final BlockPos TARGET = new BlockPos(0, -60, 0);
    private static final BlockPos REFERENCE = new BlockPos(0, -61, 0);
    private static final String DORMANT = "The session is dormant.";
    private static final String OBSERVATION = "Fine grooves converge toward a dark seam in the stone.";
    private static final String LENS = "The fracture is recent. Something below has shifted; no tool marks explain it.";
    private static final String MARKER_TOKEN = "chronicle.test.marker_placed";
    private static final String AIR_TOKEN = "chronicle.test.marker_mined";
    private static final Gson GSON = new Gson();

    private final File outputDirectory;
    private final long deadlineNanos = System.nanoTime() + 180_000_000_000L;
    private final List<String> messages = new ArrayList<>();
    private final List<String> checks = new ArrayList<>();
    private int stage;
    private int waitTicks;
    private int screenshotsPending;
    private boolean finished;
    private boolean connecting;

    private QuietBelowContentSmoke(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    static void registerIfRequested() {
        if (OUTPUT == null || OUTPUT.isBlank()) {
            return;
        }
        File directory = new File(OUTPUT);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            QuietBelow.LOGGER.warn("Content smoke output directory could not be created: {}", directory);
            return;
        }
        QuietBelowContentSmoke smoke = new QuietBelowContentSmoke(directory);
        ClientReceiveMessageEvents.GAME.register((text, overlay) -> smoke.messages.add(text.getString()));
        ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
        QuietBelow.LOGGER.info("Quiet Below M3 content smoke enabled for isolated fixture {}", FIXTURE_ADDRESS);
    }

    private void tick(MinecraftClient client) {
        if (finished) return;
        if (System.nanoTime() >= deadlineNanos) {
            finish(client, false, "timed out at stage " + stage + "; screen=" + screenName(client));
            return;
        }
        bypassOnboarding(client);
        if (!connecting && client.player == null && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
            connecting = true;
            ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(FIXTURE_ADDRESS),
                    new ServerInfo("Chronicle content fixture", FIXTURE_ADDRESS, ServerInfo.ServerType.OTHER), false, null);
        }
        if (!isFixture(client)) return;
        if (stage == 24 && !client.world.getBlockState(TARGET).isAir()) {
            client.interactionManager.updateBlockBreakingProgress(TARGET, Direction.SOUTH);
        }
        if (++waitTicks < 5) return;
        waitTicks = 0;
        try {
            runStage(client);
        } catch (RuntimeException exception) {
            finish(client, false, exception.getMessage());
        }
    }

    private void bypassOnboarding(MinecraftClient client) {
        if (client.player == null && client.getOverlay() == null && client.currentScreen instanceof AccessibilityOnboardingScreen) {
            client.setScreen(new TitleScreen());
        }
    }

    private boolean isFixture(MinecraftClient client) {
        return client.getCurrentServerEntry() != null && FIXTURE_ADDRESS.equals(client.getCurrentServerEntry().address)
                && client.player != null && client.world != null && client.getNetworkHandler() != null;
    }

    private void runStage(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        switch (stage) {
            case 0 -> command(player, "clear @s");
            case 1 -> command(player, "setblock 0 -60 0 air");
            case 2 -> command(player, "setblock 0 -61 0 stone");
            case 3 -> command(player, "tp @s 0.5 -60 3.5 180 15");
            case 4 -> command(player, "give @s quiet_below:resonance_marker 1");
            case 5 -> command(player, "give @s quiet_below:echo_lens 1");
            case 6 -> command(player, "give @s minecraft:diamond_pickaxe 1");
            case 7 -> command(player, "gamemode survival @s");
            case 8 -> verifyFixtureInventory(client);
            case 9 -> placeMarker(client);
            case 10 -> verifyPlaced(client);
            case 11 -> command(player, "execute if block 0 -60 0 quiet_below:resonance_marker run tellraw @s {\"text\":\"" + MARKER_TOKEN + "\"}");
            case 12 -> requireMessage(MARKER_TOKEN, "server confirmed marker placement");
            case 13 -> inactiveInteraction(client);
            case 14 -> requireMessage(DORMANT, "dormant empty-hand interaction");
            case 15 -> command(player, "chronicle session start");
            case 16 -> requireMessage("Started Chronicle session", "session became active");
            case 17 -> activeObservation(client);
            case 18 -> requireMessage(OBSERVATION, "active marker observation");
            case 19 -> lensInteraction(client);
            case 20 -> requireMessage(LENS, "active Echo Lens clue");
            case 21 -> {
                verifyLensUnchanged(player);
                client.inGameHud.getChatHud().clear(false);
                client.getToastManager().clear();
            }
            case 22 -> capture(client, "chronicle-content-marker-world.png");
            case 23 -> mineMarker(client);
            case 24 -> waitForBlock(client, null, "marker mined client state");
            case 25 -> command(player, "execute if block 0 -60 0 air run tellraw @s {\"text\":\"" + AIR_TOKEN + "\"}");
            case 26 -> requireMessage(AIR_TOKEN, "server confirmed marker mining");
            case 27 -> command(player, "tp @s 0.5 -60 0.5 180 15");
            case 28 -> verifyMarkerRecovered(player);
            case 29 -> showSheet(client);
            case 30 -> capture(client, "chronicle-content-item-sheet.png");
            case 31 -> closeSheet(client);
            case 32 -> command(player, "chronicle session end");
            case 33 -> requireMessage("Ended Chronicle session", "session ended after content checks");
            case 34 -> finishWhenScreenshotsComplete(client);
            default -> { }
        }
        if (stage < 34) stage++;
    }

    private void command(ClientPlayerEntity player, String command) {
        player.networkHandler.sendChatCommand(command);
    }

    private void verifyFixtureInventory(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        require(client.interactionManager.getCurrentGameMode() == GameMode.SURVIVAL, "fixture did not enter survival mode");
        require(player.getBlockPos().getY() == -60 && player.getBlockPos().getX() == 0, "fixture teleport did not arrive at target");
        require(slot(player, QuietBelow.RESONANCE_MARKER) >= 0 && slot(player, QuietBelow.ECHO_LENS) >= 0
                && slot(player, Items.DIAMOND_PICKAXE) >= 0, "fixture inventory lacks M3 items");
        checks.add("fixture inventory, survival mode, and target location confirmed");
    }

    private void placeMarker(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        player.getInventory().setSelectedSlot(slot(player, QuietBelow.RESONANCE_MARKER));
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                new BlockHitResult(new Vec3d(0.5, -60.0, 0.5), Direction.UP, REFERENCE, false));
    }

    private void inactiveInteraction(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        int markerCount = count(player, QuietBelow.RESONANCE_MARKER);
        player.getInventory().setSelectedSlot(8);
        require(player.getInventory().getStack(8).isEmpty(), "fixture slot 8 must be empty");
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitTarget());
        require(count(player, QuietBelow.RESONANCE_MARKER) == markerCount && client.world.getBlockState(TARGET).isOf(QuietBelow.BLOCK),
                "dormant interaction changed inventory or world");
    }

    private void activeObservation(MinecraftClient client) {
        client.player.getInventory().setSelectedSlot(8);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hitTarget());
    }

    private void lensInteraction(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        player.getInventory().setSelectedSlot(slot(player, QuietBelow.ECHO_LENS));
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitTarget());
    }

    private void verifyLensUnchanged(ClientPlayerEntity player) {
        require(count(player, QuietBelow.ECHO_LENS) == 1, "Echo Lens was consumed");
        require(count(player, QuietBelow.RESONANCE_MARKER) == 0, "lens interaction consumed an additional marker");
        checks.add("active observation and lens clue delivered without consuming lens");
    }

    private void mineMarker(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        player.getInventory().setSelectedSlot(slot(player, Items.DIAMOND_PICKAXE));
        client.interactionManager.attackBlock(TARGET, Direction.SOUTH);
    }

    private void verifyMarkerRecovered(ClientPlayerEntity player) {
        if (count(player, QuietBelow.RESONANCE_MARKER) != 1) {
            stage--;
            return;
        }
        checks.add("survival placement consumed marker and pickaxe mining recovered its loot");
    }

    private void verifyPlaced(MinecraftClient client) {
        if (!client.world.getBlockState(TARGET).isOf(QuietBelow.BLOCK)) {
            stage--;
            return;
        }
        require(count(client.player, QuietBelow.RESONANCE_MARKER) == 0, "marker was not consumed by survival placement");
        checks.add("marker placement client state and inventory consumption confirmed");
    }

    private void waitForBlock(MinecraftClient client, net.minecraft.block.Block expected, String check) {
        boolean matches = expected == null ? client.world.getBlockState(TARGET).isAir() : client.world.getBlockState(TARGET).isOf(expected);
        if (!matches) {
            stage--;
            return;
        }
        checks.add(check);
    }

    private void requireMessage(String expected, String check) {
        String found = messages.stream().filter(message -> message.equals(expected)
                || ((expected.equals("Started Chronicle session") || expected.equals("Ended Chronicle session"))
                && message.startsWith(expected + " "))).findFirst().orElse(null);
        if (found == null) {
            stage--;
            return;
        }
        messages.remove(found);
        checks.add(check);
    }

    private void showSheet(MinecraftClient client) {
        client.getToastManager().clear();
        client.setScreen(new ContentSheetScreen());
    }

    private void closeSheet(MinecraftClient client) {
        if (client.currentScreen instanceof ContentSheetScreen) client.setScreen(null);
    }

    private void capture(MinecraftClient client, String filename) {
        screenshotsPending++;
        File expected = new File(new File(outputDirectory, ScreenshotRecorder.SCREENSHOTS_DIRECTORY), filename);
        ScreenshotRecorder.saveScreenshot(outputDirectory, filename, client.getFramebuffer(), 1, ignored -> client.execute(() -> {
            if (!expected.isFile()) finish(client, false, "screenshot was not written: " + filename);
            screenshotsPending--;
        }));
    }

    private void finishWhenScreenshotsComplete(MinecraftClient client) {
        if (screenshotsPending > 0) {
            stage--;
            return;
        }
        checks.add("world marker and localized item sheet screenshots captured");
        finish(client, true, "M3 marker placement, dormant/active interactions, lens, mining, loot, and models verified");
    }

    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (finished) return;
        finished = true;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("result", pass ? "PASS" : "FAIL");
        report.put("checks", checks);
        report.put("detail", detail);
        try {
            Files.writeString(outputDirectory.toPath().resolve("content-smoke.json"), GSON.toJson(report));
        } catch (IOException exception) {
            QuietBelow.LOGGER.error("Content smoke could not write report", exception);
        }
        QuietBelow.LOGGER.info("Quiet Below M3 content smoke {}: {}", pass ? "PASS" : "FAIL", detail);
        client.setScreen(null);
        client.scheduleStop();
    }

    private static int slot(ClientPlayerEntity player, net.minecraft.item.Item item) {
        for (int index = 0; index < 9; index++) if (player.getInventory().getStack(index).isOf(item)) return index;
        return -1;
    }

    private static int count(ClientPlayerEntity player, net.minecraft.item.Item item) {
        int total = 0;
        for (int index = 0; index < 9; index++) if (player.getInventory().getStack(index).isOf(item)) total += player.getInventory().getStack(index).getCount();
        return total;
    }

    private static BlockHitResult hitTarget() {
        return new BlockHitResult(new Vec3d(0.5, -59.5, 0.5), Direction.UP, TARGET, false);
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }

    private static String screenName(MinecraftClient client) {
        return client.currentScreen == null ? "none" : client.currentScreen.getClass().getName();
    }

    private static final class ContentSheetScreen extends Screen {
        private ContentSheetScreen() { super(Text.literal("Quiet Below content")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 28, 0xFFFFFFFF);
            drawItem(context, new ItemStack(QuietBelow.RESONANCE_MARKER), width / 2 - 80, 72);
            drawItem(context, new ItemStack(QuietBelow.ECHO_LENS), width / 2 + 32, 72);
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable(QuietBelow.RESONANCE_MARKER.getTranslationKey()), width / 2 - 48, 150, 0xFFFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable(QuietBelow.ECHO_LENS.getTranslationKey()), width / 2 + 64, 150, 0xFFFFFFFF);
            super.render(context, mouseX, mouseY, deltaTicks);
        }

        private void drawItem(DrawContext context, ItemStack stack, int x, int y) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(4.0f, 4.0f);
            context.drawItem(stack, 0, 0);
            context.getMatrices().popMatrix();
        }

        @Override public boolean shouldPause() { return false; }
    }
}
