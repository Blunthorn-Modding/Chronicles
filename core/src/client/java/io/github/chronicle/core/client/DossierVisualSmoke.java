package io.github.chronicle.core.client;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.network.ProfilePayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

/** Opt-in visual smoke checks: synthetic by default, or a real isolated server when CHRONICLE_UI_SERVER=true. */
final class DossierVisualSmoke {
    private static final String OUTPUT_DIRECTORY = System.getenv("CHRONICLE_UI_SMOKE");
    private static final boolean SERVER_MODE = Boolean.parseBoolean(System.getenv("CHRONICLE_UI_SERVER"));
    private static final UUID EDITABLE_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID READ_ONLY_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final int FRAMES_PER_STAGE = 5;
    private static final String LONG_DESCRIPTION = "A patient mason who knows every old road into the valley. ".repeat(10).substring(0, 512);

    private final File outputDirectory;
    private final long deadlineNanos = System.nanoTime() + (SERVER_MODE ? 90_000_000_000L : 60_000_000_000L);
    private int stage = -1;
    private int stageFrames;
    private int screenshotsCompleted;
    private boolean failed;
    private boolean finished;
    private DossierScreen editable;
    private boolean connectionRequested;

    private DossierVisualSmoke(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    static void registerIfRequested() {
        if (OUTPUT_DIRECTORY == null || OUTPUT_DIRECTORY.isBlank()) {
            return;
        }
        File directory = new File(OUTPUT_DIRECTORY);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            ChronicleCore.LOGGER.warn("Chronicle dossier visual smoke output directory could not be created: {}", directory);
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(new DossierVisualSmoke(directory)::tick);
        ChronicleCore.LOGGER.info("Chronicle dossier {} visual smoke is enabled; screenshots will be written under {}",
                SERVER_MODE ? "server" : "synthetic", directory);
    }

    private void tick(MinecraftClient client) {
        if (finished) {
            return;
        }
        if (System.nanoTime() >= deadlineNanos) {
            finish(client, false, "timed out waiting for visual smoke; current screen=" + screenName(client));
            return;
        }
        bypassOnboardingWhenSafe(client);
        if (SERVER_MODE) {
            tickServer(client);
        } else {
            tickSynthetic(client);
        }
    }

    private void bypassOnboardingWhenSafe(MinecraftClient client) {
        if (client.player == null && client.getOverlay() == null
                && client.currentScreen instanceof AccessibilityOnboardingScreen) {
            client.setScreen(new TitleScreen());
        }
    }

    private void tickSynthetic(MinecraftClient client) {
        if (stage < 0) {
            if (!(client.currentScreen instanceof TitleScreen) || client.getOverlay() != null) {
                return;
            }
            if (++stageFrames < FRAMES_PER_STAGE) {
                return;
            }
            stage = 0;
            stageFrames = 0;
        }
        if (++stageFrames < FRAMES_PER_STAGE) {
            return;
        }
        stageFrames = 0;
        switch (stage++) {
            case 0 -> {
                editable = new DossierScreen(new ProfilePayloads.View(EDITABLE_OWNER, "A. Ash", "", "", 0, true, false));
                client.setScreen(editable);
            }
            case 1 -> {
                editable.setVisualSmokeDraft();
                editable.resize(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
                if (!editable.visualSmokeDraftRetained()) {
                    finish(client, false, "draft was not retained after resize");
                }
            }
            case 2 -> capture(client, "chronicle-dossier-editable.png");
            case 3 -> {
                DossierScreen readOnly = new DossierScreen(new ProfilePayloads.View(
                        READ_ONLY_OWNER, "Borin Vale of the Old Roads", "they/them and he/him",
                        LONG_DESCRIPTION, 4, false, false));
                client.setScreen(readOnly);
                readOnly.resize(client, 320, 240);
            }
            case 5 -> capture(client, "chronicle-dossier-read-only-320x240.png");
            default -> { }
        }
    }

    private void tickServer(MinecraftClient client) {
        switch (stage) {
            case -1 -> {
                if (!connectionRequested && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                    connectionRequested = true;
                    String address = "127.0.0.1:25575";
                    ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(address),
                            new ServerInfo("Chronicle isolated UI test", address, ServerInfo.ServerType.OTHER), false, null);
                }
                if (client.player != null && client.world != null && client.currentScreen == null && client.getNetworkHandler() != null
                        && ClientPlayNetworking.canSend(ProfilePayloads.Request.ID)) {
                    ClientPlayNetworking.send(new ProfilePayloads.Request(client.player.getUuid()));
                    stage = 0;
                }
            }
            case 0 -> {
                if (client.currentScreen instanceof DossierScreen screen && screen.owner().equals(client.player.getUuid())) {
                    editable = screen;
                    editable.setVisualSmokeDraft();
                    editable.saveForVisualSmoke();
                    stage = 1;
                }
            }
            case 1 -> {
                if (client.currentScreen instanceof DossierScreen screen && screen == editable && screen.visualSmokeSaved()) {
                    stage = 2;
                    stageFrames = 0;
                }
            }
            case 2 -> {
                if (++stageFrames >= FRAMES_PER_STAGE) {
                    capture(client, "chronicle-dossier-saved.png");
                    stage = 3;
                }
            }
            default -> { }
        }
    }

    private void capture(MinecraftClient client, String filename) {
        File expected = new File(new File(outputDirectory, ScreenshotRecorder.SCREENSHOTS_DIRECTORY), filename);
        ScreenshotRecorder.saveScreenshot(outputDirectory, filename, client.getFramebuffer(), 1,
                ignored -> client.execute(() -> screenshotCompleted(client, expected)));
    }

    private void screenshotCompleted(MinecraftClient client, File expected) {
        screenshotsCompleted++;
        if (!expected.isFile()) {
            failed = true;
        }
        int expectedScreenshots = SERVER_MODE ? 1 : 2;
        if (screenshotsCompleted == expectedScreenshots) {
            String detail = SERVER_MODE
                    ? "server profile request/save acknowledgement and canonical fields verified"
                    : "editable/read-only screenshots written; resize draft retained";
            finish(client, !failed, failed ? "a screenshot was not written" : detail);
        }
    }

    private void finish(MinecraftClient client, boolean passed, String detail) {
        if (finished) {
            return;
        }
        finished = true;
        client.setScreen(null);
        String result = passed ? "PASS" : "FAIL";
        try {
            Files.writeString(outputDirectory.toPath().resolve("chronicle-ui-smoke.json"),
                    "{\"result\":\"" + result + "\",\"mode\":\"" + (SERVER_MODE ? "server" : "synthetic")
                            + "\",\"detail\":\"" + detail + "\"}\n");
        } catch (IOException exception) {
            ChronicleCore.LOGGER.error("Chronicle dossier visual smoke could not write its result", exception);
            result = "FAIL";
        }
        ChronicleCore.LOGGER.info("Chronicle dossier {} visual smoke {}: {}", SERVER_MODE ? "server" : "synthetic", result, detail);
        client.scheduleStop();
    }

    private static String screenName(MinecraftClient client) {
        return client.currentScreen == null ? "none" : client.currentScreen.getClass().getName();
    }
}
