package io.github.chronicle.core.client.testing;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.client.DepartureConfirmScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Matching-client driver for the disposable two-passenger departure proof. */
public final class DeparturesSmoke {
    private static final String OUTPUT = System.getenv("CHRONICLE_DEPARTURES_SMOKE");
    private static final String ROOT = System.getenv("CHRONICLE_DEPARTURES_SERVER_DIR");
    private static final String PHASE = System.getenv("CHRONICLE_DEPARTURES_PHASE");
    private static final String ROLE = System.getenv("CHRONICLE_DEPARTURES_ROLE");
    private static final String ADDRESS = "127.0.0.1:25575";
    private static final BlockPos HELM = new BlockPos(4, 64, 4);
    private static final BlockPos FINAL_HELM = new BlockPos(8, 64, 4);
    private static DeparturesSmoke instance;

    private final Path output;
    private final List<String> checks = new ArrayList<>(), messages = new ArrayList<>();
    private boolean connecting, done;
    private long began = System.nanoTime();
    private int step, wait;

    private DeparturesSmoke() {
        output = Path.of(OUTPUT).toAbsolutePath().normalize();
        Path root = Path.of(ROOT).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve(".departures-fixture"))) throw new IllegalArgumentException("refusing departure smoke outside disposable fixture");
        if (output.startsWith(root)) throw new IllegalArgumentException("departure evidence must be outside fixture");
        if (!List.of("initial", "restart", "recovery-a", "recovery-b", "recovery-c").contains(PHASE)) throw new IllegalArgumentException("invalid departure smoke phase");
        if (!List.of("A", "B", "C").contains(ROLE)) throw new IllegalArgumentException("invalid departure smoke role");
    }

    public static void register() {
        if (OUTPUT == null) return;
        try {
            if (ROOT == null || PHASE == null || ROLE == null) throw new IllegalArgumentException("departure smoke fixture, phase and role are required");
            instance = new DeparturesSmoke();
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay) instance.messages.add(message.getString()); });
            ClientTickEvents.END_CLIENT_TICK.register(instance::tick);
        } catch (Exception error) {
            ChronicleCore.LOGGER.error("Departure smoke setup failed", error);
        }
    }

    private void tick(MinecraftClient client) {
        if (done) return;
        try {
            if (System.nanoTime() - began > 240_000_000_000L) throw new IllegalStateException("departure smoke timeout step " + step);
            if (client.player == null && client.getOverlay() == null && client.currentScreen instanceof AccessibilityOnboardingScreen) client.setScreen(new TitleScreen());
            if (!connecting && client.player == null && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                connecting = true;
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS), new ServerInfo("Departure smoke", ADDRESS, ServerInfo.ServerType.OTHER), false, null);
                return;
            }
            if (client.player == null) return;
            client.getToastManager().clear();
            if (PHASE.startsWith("recovery-")) recovery(client);
            else if (PHASE.equals("restart")) restart(client);
            else if (ROLE.equals("A")) actor(client);
            else passenger(client);
        } catch (Throwable error) {
            finish(client, false, error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void actor(MinecraftClient client) {
        if (step == 0) { wait = 40; step++; return; }
        if (step == 1) { if (wait-- > 0) return; command(client, "chronicle_departures_trial prepare"); step++; return; }
        if (step == 2) { if (!passed("prepare") || !seen("Saved Area change 'Departure ship' with its START state.")) return; command(client, "chronicle_departures_trial capture_end"); step++; return; }
        if (step == 3) { if (!passed("capture_end") || !seen("Saved current area as END for 'Departure ship'.")) return; command(client, "chronicle_departures_trial arm"); step++; return; }
        if (step == 4) { if (!passed("arm")) return; interact(client); step++; return; }
        DepartureConfirmScreen screen = client.currentScreen instanceof DepartureConfirmScreen value ? value : null;
        if (step == 5) { if (screen == null || screen.state().stage() != 1) return; verifyFirst(screen); wait = 4; step++; return; }
        if (step == 6) { if (wait-- > 0) return; require(client.getWindow().getScaledWidth() == 640 && client.getWindow().getScaledHeight() == 360, "first review viewport is not 640x360"); capture(client, "first-review"); require(screen.triggerForTest("Continue"), "Continue was unavailable"); step++; return; }
        if (step == 7) { if (screen == null || screen.state().stage() != 2) return; verifyFinal(screen); wait = 4; step++; return; }
        if (step == 8) { if (wait-- > 0) return; capture(client, "final-stacked"); require(screen.triggerForTest("Back"), "Back was unavailable"); step++; return; }
        if (step == 9) { if (screen == null || screen.state().stage() != 1) return; checks.add("final Back returned to a fresh first review"); require(screen.triggerForTest("Cancel"), "Cancel was unavailable"); wait = 8; step++; return; }
        if (step == 10) { if (wait-- > 0) return; require(client.currentScreen == null, "Cancel did not close the review"); client.getWindow().setWindowedSize(960, 960); wait = 8; step++; return; }
        if (step == 11) { if (wait-- > 0) return; require(client.getWindow().getScaledWidth() == 480 && client.getWindow().getScaledHeight() == 480, "second review viewport is not 480x480"); interact(client); step++; return; }
        if (step == 12) { if (screen == null || screen.state().stage() != 1) return; verifyFirst(screen); wait = 4; step++; return; }
        if (step == 13) { if (wait-- > 0) return; capture(client, "first-review-square"); require(screen.triggerForTest("Continue"), "second Continue was unavailable"); step++; return; }
        if (step == 14) { if (screen == null || screen.state().stage() != 2) return; verifyFinal(screen); wait = 4; step++; return; }
        if (step == 15) { if (wait-- > 0) return; capture(client, "final-stacked-square"); require(screen.triggerForTest("Depart"), "Depart was unavailable"); step++; return; }
        if (step == 16) { if (!seen("Departure complete.")) return; command(client, "chronicle_departures_trial verify"); step++; return; }
        if (step == 17) { if (!passed("verify")) return; checks.add("exact A+B linked departure completed while offline enrolled C remained"); finish(client, true, "native two-stage linked departure passed"); }
    }

    private void passenger(MinecraftClient client) {
        if (!PHASE.equals("initial")) throw new IllegalStateException("unexpected passenger phase");
        if (client.player.isSpectator()) {
            if(step==0){step=1;wait=200;return;}
            if(wait-->0)return;
            checks.add("passenger B became Spectator only after actor A confirmed the shared exact roster");
            finish(client, true, "passenger departure observed");
        }
    }

    private void restart(MinecraftClient client) {
        if (step == 0) { wait = ROLE.equals("A") ? 35 : 100; step++; return; }
        if (step == 1) {
            if (wait-- > 0) return;
            require(client.player.isSpectator(), "departed player did not reconnect as Spectator");
            checks.add("durable departure enforced Spectator on real reconnect");
            if (ROLE.equals("A")) command(client, "chronicle_departures_trial restart"); else { finish(client, true, "passenger restart enforcement passed"); return; }
            step++;
            return;
        }
        if (step == 2 && passed("restart")) {
            checks.add("server retained exact departure flags, linked Area result and offline-C open campaign");
            finish(client, true, "restart departure enforcement passed");
        }
    }

    private void recovery(MinecraftClient client) {
        if (PHASE.equals("recovery-c")) { conclusion(client); return; }
        if (PHASE.equals("recovery-a")) {
            require(ROLE.equals("A"), "recovery-a requires fixture actor A");
            if (step == 0) { wait = 25; step++; return; }
            if (step == 1) { if (wait-- > 0) return; command(client, "chronicle_departures_trial seed_recovery"); step++; return; }
            if (step == 2) { if (!passed("seed_recovery") || !client.player.isSpectator()) return; wait = 20; step++; return; }
            if (step == 3) { if (wait-- > 0) return; command(client, "chronicle_departures_trial verify_recovery_a"); step++; return; }
            if (step == 4 && passed("verify_recovery_a")) {
                checks.add("A-only tick saved one real Spectator receipt while the A+B operation remained COMMITTED");
                finish(client, true, "first staggered recovery boot passed");
            }
            return;
        }
        require(ROLE.equals("B"), "recovery-b requires fixture passenger B");
        if (step == 0) { wait = 60; step++; return; }
        if (step == 1) {
            if (wait-- > 0) return;
            require(client.player.isSpectator(), "B did not rejoin as Spectator");
            command(client, "chronicle_departures_trial verify_recovery_b");
            step++;
            return;
        }
        if (step == 2 && passed("verify_recovery_b")) {
            checks.add("real restart with only B saved the second receipt and completed no-Area/no-conclusion recovery");
            finish(client, true, "second staggered recovery boot passed");
        }
    }

    private void conclusion(MinecraftClient client) {
        require(ROLE.equals("C"), "recovery-c requires remaining fixture passenger C");
        if (step == 0) { wait = 30; step++; return; }
        if (step == 1) { if (wait-- > 0) return; command(client, "chronicle_departures_trial prepare_conclusion"); step++; return; }
        if (step == 2) { if (!passed("prepare_conclusion")) return; interact(client, FINAL_HELM); step++; return; }
        DepartureConfirmScreen screen = client.currentScreen instanceof DepartureConfirmScreen value ? value : null;
        if (step == 3) {
            if (screen == null || screen.state().stage() != 1) return;
            require(screen.state().title().equals("Leave via Final vessel?"), "C first review title is incorrect");
            require(screen.state().details().contains("ChronicleDepartC") && !screen.state().details().contains("ChronicleDepartA")
                    && !screen.state().details().contains("ChronicleDepartB"), "C first review roster is not exact");
            require(screen.triggerForTest("Continue"), "C Continue was unavailable");
            step++;
            return;
        }
        if (step == 4) {
            if (screen == null || screen.state().stage() != 2) return;
            require(screen.state().details().contains("Every enrolled participant") && screen.state().acceptLabel().equals("Depart"),
                    "C final review did not disclose permanent campaign conclusion");
            require(screen.triggerForTest("Depart"), "C Depart was unavailable");
            step++;
            return;
        }
        if (step == 5) { if (!seen("campaign has concluded")) return; command(client, "chronicle_departures_trial verify_conclusion"); step++; return; }
        if (step == 6 && passed("verify_conclusion")) {
            checks.add("C-only native two-confirm departure concluded the campaign and preserved non-active authoring");
            finish(client, true, "remaining-participant conclusion passed");
        }
    }

    private static void verifyFirst(DepartureConfirmScreen screen) {
        require(screen.state().title().equals("Leave via Smoke vessel?"), "first review title is not departure-specific");
        require(screen.state().details().contains("ChronicleDepartA") && screen.state().details().contains("ChronicleDepartB"), "first review omitted A or B");
        require(!screen.state().details().contains("ChronicleDepartC"), "first review included offline C");
        require(screen.state().acceptLabel().equals("Continue"), "first review action is not Continue");
    }

    private static void verifyFinal(DepartureConfirmScreen screen) {
        require(screen.state().title().equals("Point of no return"), "final review wording is not distinct");
        require(screen.state().details().contains("permanently") && screen.state().details().contains("Everyone else will remain behind."), "final consequences are incomplete");
        require(screen.state().acceptLabel().equals("Depart"), "final action is not Depart");
    }

    private static void interact(MinecraftClient client) {
        interact(client, HELM);
    }

    private static void interact(MinecraftClient client, BlockPos position) {
        var hit = new BlockHitResult(Vec3d.ofCenter(position), Direction.NORTH, position, false);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
    }

    private boolean passed(String verb) {
        for (String message : messages) {
            if (message.contains("Departures trial FAIL " + verb)) throw new IllegalStateException(message);
            if (message.contains("Departures trial PASS " + verb)) return true;
        }
        return false;
    }

    private boolean seen(String text) { return messages.stream().anyMatch(message -> message.contains(text)); }
    private static void command(MinecraftClient client, String command) { client.player.networkHandler.sendChatCommand(command); }
    private void capture(MinecraftClient client, String kind) { ScreenshotRecorder.saveScreenshot(output.getParent().toFile(), "departures-" + kind + "-" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight() + ".png", client.getFramebuffer(), 1, text -> { }); }

    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (done) return;
        done = true;
        try {
            Files.createDirectories(output.getParent());
            JsonObject value = new JsonObject();
            value.addProperty("result", pass ? "PASS" : "FAIL");
            value.addProperty("phase", PHASE);
            value.addProperty("role", ROLE);
            value.addProperty("detail", detail);
            JsonArray array = new JsonArray();
            checks.forEach(array::add);
            value.add("checks", array);
            Files.writeString(output, new Gson().toJson(value));
        } catch (Exception ignored) { }
        client.scheduleStop();
    }

    private static void require(boolean condition, String text) { if (!condition) throw new IllegalStateException(text); }
}
