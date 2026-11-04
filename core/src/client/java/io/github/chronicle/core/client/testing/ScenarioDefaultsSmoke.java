package io.github.chronicle.core.client.testing;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.client.EventEditorScreen;
import io.github.chronicle.core.menu.CoreMenuModel;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.*;

/** Opens the ordinary Events menu on the disposable scenario-defaults fixture. */
public final class ScenarioDefaultsSmoke {
    private final Path output; private final boolean copied; private final String phase; private final List<String> messages = new ArrayList<>(), checks = new ArrayList<>();
    private boolean connecting, done; private int step, wait; private final long started = System.nanoTime();
    private ScenarioDefaultsSmoke(String destination) {
        output = Path.of(destination);
        phase = System.getenv().getOrDefault("CHRONICLE_SCENARIO_DEFAULTS_PHASE", "true".equals(System.getenv("CHRONICLE_SCENARIO_DEFAULTS_COPIED")) ? "copied" : "fresh");
        copied = phase.equals("copied");
        Path root = Path.of(System.getenv("CHRONICLE_SCENARIO_DEFAULTS_ROOT")).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve(".scenario-defaults-fixture")) || root.getParent() == null || !root.getParent().getFileName().toString().equals("test-runs") || output.toAbsolutePath().normalize().startsWith(root)) throw new IllegalArgumentException("unsafe fixture");
    }
    public static void register() {
        String path = System.getenv("CHRONICLE_SCENARIO_DEFAULTS_SMOKE"); if (path == null) return;
        var smoke = new ScenarioDefaultsSmoke(path); ClientReceiveMessageEvents.GAME.register((text, overlay) -> { if (!overlay) smoke.messages.add(text.getString()); });
        ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
    }
    private void tick(MinecraftClient client) {
        if (done) return;
        try {
            if (System.nanoTime() - started > 120_000_000_000L) throw new IllegalStateException("timeout step " + step + " " + messages);
            if (client.player == null && client.getOverlay() == null && client.currentScreen instanceof AccessibilityOnboardingScreen) client.setScreen(new TitleScreen());
            if (!connecting && client.player == null && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                connecting = true; ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse("127.0.0.1:25575"), new ServerInfo("Scenario defaults proof", "127.0.0.1:25575", ServerInfo.ServerType.OTHER), false, null); return;
            }
            if (client.player == null) return;
            if (messages.stream().anyMatch(m -> m.contains("Scenario defaults trial FAIL"))) throw new IllegalStateException(messages.toString());
            CoreMenuScreen menu = client.currentScreen instanceof CoreMenuScreen m ? m : null;
            if (!phase.equals("fresh") && !phase.equals("copied")) { followup(client, menu); return; }
            if (step == 0) { command(client, copied ? "copied" : "fresh"); step = copied ? 20 : 1; return; }
            if (step == 20) { if (!passed("copied")) return; checks.add("copied world retained Event pins, completion receipt, deletion tombstone and flag; server content reinitialized"); finish(client, null); return; }
            if (step == 1) { if (!passed("fresh")) return; client.player.networkHandler.sendChatCommand("chronicle"); step = 2; return; }
            if (step == 2) { if (menu == null || menu.pending()) return; if (menu.debugForTest()) menu.toggleDebugForTest(); require(menu.triggerForTest("Events"), "Events navigation missing"); step = 3; return; }
            if (step == 3) {
                if (menu == null || menu.pending() || menu.state().page() != CoreMenuModel.Page.EVENTS) return;
                require(menu.state().entries().stream().filter(e->e.kind().equals("CUE")).count() == 9, "nine default Events not visible");
                var entry = menu.state().entries().stream().filter(e -> e.reference().equals("quiet_below:sinkhole_opening")).findFirst().orElseThrow();
                require(!menu.debugForTest() && entry.label().startsWith("! ") && entry.description().contains("Needs setup"), "missing visible setup warning"); menu.selectForTest(entry.id()); step = 4; return;
            }
            if (step == 4) {
                if (menu == null || menu.pending() || menu.state().detail() == null || !menu.state().detail().id().contains("sinkhole_opening")) return;
                require(menu.state().detail().description().contains("Needs setup"), "detail lacks setup reason");
                require(menu.triggerForTest("Preview privately"), "private preview unavailable"); wait = 40; step = 5; return;
            }
            if (step == 5) { if (wait-- > 0) return; require(messages.stream().anyMatch(m -> m.contains("The Earth quakes beneath your feet")), "private preview text was not delivered"); checks.add("ordinary Events menu shows nine defaults and missing-Region detail with Debug Off; private preview delivered"); command(client, "prepare"); step = 6; return; }
            if (step == 6) { if (!passed("prepare")) return; command(client, "captured"); wait = 10; step = 7; return; }
            if (step == 7) { if (!passed("captured")) { if (wait-- <= 0) { command(client, "captured"); wait = 10; } return; } wait = 30; step = 8; return; }
            if (step == 8) { if (wait-- > 0) return; command(client, "held"); step = 9; return; }
            if (step == 9) { if (!passed("held")) return; command(client, "release"); step = 10; return; }
            if (step == 10) { if (!passed("release")) return; command(client, "promoted"); wait = 10; step = 11; return; }
            if (step == 11) { if (!passed("promoted")) { if (wait-- <= 0) { command(client, "promoted"); wait = 10; } return; } command(client, "run"); step = 12; return; }
            if (step == 12) { if (!passed("run")) return; command(client, "completed"); wait = 10; step = 13; return; }
            if (step == 13) { if (!passed("completed")) { if (wait-- <= 0) { command(client, "completed"); wait = 10; } return; } checks.add("captured End state, held and promoted map drafts, then ran real sinkhole Event and verified terrain and flag"); finish(client, null); }

        } catch (Throwable error) { finish(client, error.toString()); }
    }
    private void followup(MinecraftClient client, CoreMenuScreen menu) {
        switch (phase) {
            case "active_prepare" -> {
                if (step == 0) { command(client, "fresh"); step++; }
                else if (step == 1 && passed("fresh")) { command(client, "active_prepare"); step++; }
                else if (step == 2 && passed("active_prepare")) { wait = 50; step++; }
                else if (step == 3 && wait-- <= 0) { command(client, "active_waiting"); wait = 10; step++; }
                else if (step == 4) {
                    if (!passed("active_waiting")) { if (wait-- <= 0) { command(client, "active_waiting"); wait = 10; } return; }
                    checks.add("resolvable default remains a draft during occupied ACTIVE play without a recovery hold"); finish(client, null);
                }
            }
            case "active_restart" -> {
                if (step == 0) { command(client, "active_resumed"); wait = 10; step++; }
                else if (step == 1) {
                    if (!passed("active_resumed")) { if (wait-- <= 0) { command(client, "active_resumed"); wait = 10; } return; }
                    checks.add("actual server restart retained ACTIVE and did not promote the now-resolvable default"); command(client, "active_pause"); step++;
                } else if (step == 2 && passed("active_pause")) { command(client, "active_promoted"); wait = 10; step++; }
                else if (step == 3) {
                    if (!passed("active_promoted")) { if (wait-- <= 0) { command(client, "active_promoted"); wait = 10; } return; }
                    checks.add("pausing after restart promotes the same default with exact Region pins and no gameplay effects"); finish(client, null);
                }
            }
            case "ui_save" -> {
                if (step == 0) { command(client, "ui_prepare"); step++; }
                else if (step == 1 && passed("ui_prepare")) { client.player.networkHandler.sendChatCommand("chronicle"); step++; }
                else if (step == 2 && menu != null && !menu.pending()) { require(menu.state().editing(), "server did not enable Editing"); require(menu.triggerForTest("Events"), "Events navigation unavailable"); step++; }
                else if (step == 3 && menu != null && !menu.pending() && menu.state().page() == CoreMenuModel.Page.EVENTS) { menu.selectForTest("cue:quiet_below:sinkhole_opening"); step++; }
                else if (step == 4 && menu != null && !menu.pending() && menu.state().detail() != null && menu.state().detail().id().contains("sinkhole_opening")) { require(menu.triggerForTest("Edit event"), "default editor unavailable"); step++; }
                else if (step == 5 && client.currentScreen instanceof EventEditorScreen editor) {
                    require(editor.valuesForTest().creating() && editor.valuesForTest().id().equals("quiet_below:sinkhole_opening"), "did not open the incomplete default template");
                    require(!editor.pressForTest("Create event"), "unresolved Region action allowed saving");
                    editor.setDraftForTest("name", "Native edited default"); editor.setDraftForTest("description", "Saved through the ordinary default Event editor");
                    require(editor.valuesForTest().id().equals("quiet_below:sinkhole_opening"), "renaming the default changed its identity");
                    for (int i = 0; i < 4; i++) editor.navigateForTest(1);
                    step++;
                } else if (step == 6 && client.currentScreen instanceof EventEditorScreen editor) {
                    require(editor.valuesForTest().changes().size() == 1, "missing unresolved Region action");
                    var removals = editor.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast).filter(b -> b.active && b.getMessage().getString().equals("Remove")).toList();
                    require(removals.size() == 4, "unexpected default action list");
                    removals.get(3).onPress(new net.minecraft.client.input.MouseInput(0, 0));
                    require(editor.valuesForTest().changes().isEmpty(), "Remove did not edit the draft");
                    require(editor.pressForTest("Create event"), "completed default could not save through UI"); step++;
                } else if (step == 7 && menu != null && !menu.pending()) {
                    require(menu.state().success(), "normal editor save failed: " + menu.state().message()); command(client, "ui_saved"); step++;
                } else if (step == 8 && passed("ui_saved")) {
                    checks.add("incomplete default opens in ordinary native editor, rejects unresolved save, accepts widget edits and persists under its original ID"); finish(client, null);
                }
            }
            case "ui_restart" -> {
                if (step == 0) { command(client, "ui_restarted"); step++; }
                else if (passed("ui_restarted")) { checks.add("actual server restart preserves the UI-saved default fingerprint and leaves gameplay progress untouched"); finish(client, null); }
            }
            default -> throw new IllegalArgumentException("unknown follow-up phase " + phase);
        }
    }
    private void command(MinecraftClient c, String verb) { c.player.networkHandler.sendChatCommand("chronicle_scenario_defaults " + verb); }
    private boolean passed(String verb) { return messages.stream().anyMatch(m -> m.contains("Scenario defaults trial PASS " + verb)); }
    private void finish(MinecraftClient c, String error) { if (done) return; done = true; try { var json = new JsonObject(); json.addProperty("result", error == null ? "PASS" : "FAIL"); json.addProperty("detail", error == null ? "Completed" : error); json.add("checks", new Gson().toJsonTree(checks)); Files.writeString(output, new Gson().toJson(json)); } catch (Exception ignored) { } c.scheduleStop(); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
