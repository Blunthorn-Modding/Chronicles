package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import io.github.chronicle.core.client.AuthoringPreviewRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/** Opt-in two-process ecology rehearsal on the disposable loopback fixture only. */
final class QuietBelowEcologySmoke {
    private static final String OUTPUT = System.getenv("CHRONICLE_ECOLOGY_SMOKE");
    private static final String PHASE = System.getenv("CHRONICLE_ECOLOGY_PHASE");
    private static final String ADDRESS = "127.0.0.1:25575";
    private final List<String> messages = new ArrayList<>(), checks = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private final long overallDeadline = System.nanoTime() + 180_000_000_000L;
    private int index, baseline, sculkBefore, pausedSculk;
    private long deadline, until, rootFrames;
    private boolean started, connected, built, finished;

    static void register() {
        if (OUTPUT == null) return;
        if (!"initial".equals(PHASE) && !"restart".equals(PHASE)) throw new IllegalArgumentException("Invalid ecology smoke phase");
        var smoke = new QuietBelowEcologySmoke();
        ClientReceiveMessageEvents.GAME.register((text, overlay) -> smoke.messages.add(text.getString()));
        ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
    }

    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            require(System.nanoTime() < overallDeadline, "ecology smoke overall timeout");
            if (client.player == null && client.getOverlay() == null && client.currentScreen instanceof AccessibilityOnboardingScreen) client.setScreen(new TitleScreen());
            if (!connected && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                connected = true;
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS),
                        new ServerInfo("Chronicle ecology fixture", ADDRESS, ServerInfo.ServerType.OTHER), false, null);
            }
            if (client.player == null || client.world == null || client.currentScreen != null) return;
            require(client.getCurrentServerEntry() != null && ADDRESS.equals(client.getCurrentServerEntry().address), "ecology fixture address mismatch");
            if (!built) { build(client); built = true; }
            if (index >= steps.size()) { finish(client, true, "ecology rehearsal complete"); return; }
            Step step = steps.get(index);
            if (!started) { baseline = messages.size(); deadline = System.nanoTime() + step.timeoutMillis * 1_000_000L; step.start.run(); started = true; }
            if (step.done.getAsBoolean()) { checks.add(step.name); index++; started = false; }
            else require(System.nanoTime() < deadline, "timed out: " + step.name);
        } catch (Exception error) { finish(client, false, error.toString()); }
    }

    private void build(MinecraftClient client) {
        if ("initial".equals(PHASE)) initial(client); else restart(client);
    }

    private void initial(MinecraftClient client) {
        command(client, "bury corridor in solid stone", "fill -2 60 -2 16 70 4 stone", ".*filled.*");
        command(client, "open corridor interior", "fill 0 65 1 13 66 1 air", ".*filled.*");
        command(client, "place temporary Heart", "setblock 0 64 1 reinforced_deepslate", ".*Changed.*");
        command(client, "place source sculk", "setblock 1 64 1 sculk", ".*Changed.*");
        command(client, "place catalyst support", "setblock 2 64 1 sculk", ".*Changed.*");
        command(client, "place native source catalyst", "setblock 2 65 1 sculk_catalyst", ".*Changed.*");
        command(client, "place native source sensor", "setblock 1 65 1 sculk_sensor", ".*Changed.*");
        command(client, "close barrier", "fill 8 65 1 8 66 1 stone", ".*filled.*");
        command(client, "enable author mode", "chronicle author mode on", "Author mode enabled.*");
        command(client, "create source region draft", "chronicle author region create \"quiet_below:source_city\"", "Region draft quiet_below:source_city opened.*");
        command(client, "set source region first corner", "chronicle author region pos1 0 64 1", "Set region draft quiet_below:source_city position 1.*");
        command(client, "set source region second corner", "chronicle author region pos2 2 66 1", "Set region draft quiet_below:source_city position 2.*");
        command(client, "save source region", "chronicle author region save", "Saved region quiet_below:source_city.*");
        command(client, "look down at Heart", "tp @s 0.5 65 1.5 0 90", ".*Teleported.*");
        command(client, "bind Heart anchor", "chronicle author block bind \"quiet_below:relic_heart\" target", "Saved block quiet_below:relic_heart.*");
        command(client, "assign source city", "chronicles setup assign \"quiet_below:source_city\" \"quiet_below:source_city\"", "Requirement assigned.*");
        command(client, "assign Heart", "chronicles setup assign \"quiet_below:relic_heart\" \"quiet_below:relic_heart\"", "Requirement assigned.*");
        command(client, "start explicit source indexing", "chronicles setup tool \"quiet_below:ecology\" index", "Source indexing started.*");
        delay("allow small loaded source index", 1_000);
        command(client, "enable indexed ecology", "chronicles setup tool \"quiet_below:ecology\" enable", "Growth enabled.*");
        command(client, "clear previous region outline before root test", "chronicles setup tool \"quiet_below:ecology\" hide_root", "Root outline hidden.*");
        delay("settle cleared outline",200);
        check("record private root renderer",()->rootFrames=AuthoringPreviewRenderer.renderedFrames());
        command(client, "show source root without editing terrain", "chronicles setup tool \"quiet_below:ecology\" show_root", "Root outlined privately.*");
        step("root outline renders in frozen world",()->{},()->AuthoringPreviewRenderer.renderedFrames()>rootFrames,10_000);
        check("root location delivered privately to operator",()->require(messages.stream().anyMatch(m->m.contains("Sculk root: 0, 64, 1 in minecraft:overworld")),"missing root coordinates"));
        screenshot(client,"ecology-root-paused.png");
        command(client, "hide source root", "chronicles setup tool \"quiet_below:ecology\" hide_root", "Root outline hidden.*");
        command(client, "start session", "chronicle session start", "Started Chronicle session.*");
        check("active session without participant has no growth", () -> sculkBefore = sculk(client));
        delay("wait without participation", 1_100);
        check("no participant remains inert", () -> require(sculk(client) == sculkBefore, "ecology grew without an eligible participant"));
        command(client, "owned native features reject inactive intake", "quiet_below_native_trial inactive", "PASS native inactive hooks:.*");
        command(client, "join participation", "chronicles join", ".*(joined|already).*Chronicle participation.*");
        step("living source grows before closed barrier", () -> {}, () -> {
            int count = sculk(client); require(count <= 8, "closed corridor exceeded bounded initial growth before barrier: " + count); return count > sculkBefore;
        }, 30_000);
        command(client, "owned catalyst ignores deaths and legacy charge", "quiet_below_native_trial active", "PASS native active hooks:.*");
        delay("observe closed barrier during occupied play", 10_000);
        check("closed barrier has no growth beyond x8", () -> require(sculkBeyondBarrier(client) == 0, "growth crossed the closed barrier"));
        command(client, "open physical barrier", "fill 8 65 1 8 66 1 air", ".*filled.*");
        step("growth reaches corridor beyond opened barrier", () -> {}, () -> sculkBeyondBarrier(client) > 0, 60_000);
        command(client, "pause active ecology", "chronicle session pause", "Paused Chronicle session.*");
        delay("settle pause", 400);
        check("capture paused terrain", () -> pausedSculk = sculk(client));
        delay("paused world remains stable", 1_100);
        check("pause prevents further ecology writes", () -> require(sculk(client) == pausedSculk, "ecology changed terrain while paused"));
        command(client,"place disconnected takeover sculk","setblock 12 64 8 sculk",".*Changed.*");
        command(client,"place disconnected takeover catalyst","setblock 12 65 8 sculk_catalyst",".*Changed.*");
        command(client,"reenter paused editing","chronicle author mode on","Author mode enabled.*");
        command(client,"create takeover region","chronicle author region create extra_sculk","Region draft extra_sculk opened.*");
        command(client,"takeover corner one","chronicle author region pos1 12 64 8","Set region draft extra_sculk position 1.*");
        command(client,"takeover corner two","chronicle author region pos2 12 65 8","Set region draft extra_sculk position 2.*");
        command(client,"save takeover geometry","chronicle author region save","Saved region extra_sculk.*");
        command(client,"assign optional takeover region","chronicles setup assign \"quiet_below:sculk_takeover\" extra_sculk","Requirement assigned.*");
        command(client,"start explicit region takeover","chronicles setup tool \"quiet_below:ownership\" claim","Region takeover started.*");
        delay("finish small loaded region takeover",600);
        command(client,"place non-campaign sculk for cleanup","setblock 6 -64 6 sculk",".*Changed.*");
        command(client,"enable persistent world cleanup","chronicles setup tool \"quiet_below:ownership\" cleanup_on","Non-campaign sculk cleanup enabled.*");
        delay("cleanup waits for occupied active play",600);
        check("paused cleanup preserves stray sculk",()->require(client.world.getBlockState(new BlockPos(6,-64,6)).isOf(Blocks.SCULK),"cleanup changed paused terrain"));
        command(client,"resume cleanup test","chronicle session resume","Resumed Chronicle session.*");
        step("world cleanup deletes non-campaign sculk",()->{},()->client.world.getBlockState(new BlockPos(6,-64,6)).isAir(),30_000);
        check("takeover preserves disconnected sculk and catalyst",()->require(client.world.getBlockState(new BlockPos(12,64,8)).isOf(Blocks.SCULK)&&client.world.getBlockState(new BlockPos(12,65,8)).isOf(Blocks.SCULK_CATALYST),"cleanup deleted explicitly adopted sculk"));
        command(client,"new stray catalyst queues cleanup","setblock 7 -64 6 sculk_catalyst",".*Changed.*");
        step("world cleanup also removes newly placed stray catalyst",()->{},()->client.world.getBlockState(new BlockPos(7,-64,6)).isAir(),10_000);
        command(client,"pause after ownership trial","chronicle session pause","Paused Chronicle session.*");
        screenshot(client, "ecology-corridor-paused.png");
    }

    private void restart(MinecraftClient client) {
        check("restart begins paused without catch-up", () -> sculkBefore = sculk(client));
        delay("wait paused after saving restart", 1_100);
        check("restart pause preserves terrain", () -> require(sculk(client) == sculkBefore, "ecology caught up while paused after restart"));
        command(client, "temporarily remove Heart anchor", "setblock 0 64 1 air", ".*Changed.*");
        command(client, "missing Heart has explicit reversible status", "chronicles setup tool \"quiet_below:ecology\" refresh", ".*Heart block is missing.*");
        command(client, "restore Heart anchor", "setblock 0 64 1 reinforced_deepslate", ".*Changed.*");
        command(client, "restored Heart is not a permanent fault", "chronicles setup tool \"quiet_below:ecology\" refresh", ".*Paused; resume Chronicle play.*");
        command(client, "prune source while paused", "setblock 1 64 1 stone", ".*Changed.*");
        command(client, "prune and seal the Heart upper face", "setblock 0 65 1 stone", ".*Changed.*");
        command(client, "resume after source pruning", "chronicle session resume", "Resumed Chronicle session.*");
        check("capture pruned source terrain", () -> sculkBefore = sculk(client));
        delay("allow reconciliation after prune", 2_100);
        check("pruned source does not regrow ownership", () -> require(sculk(client) == sculkBefore, "ecology restored or grew from a removed source"));
        command(client, "disconnected branch has clear dormant status", "chronicles setup tool \"quiet_below:ecology\" refresh", ".*Dormant: no living sculk connection to the Heart.*");
        command(client, "replace physical source connection", "setblock 1 64 1 sculk", ".*Changed.*");
        check("capture manually restored connection",()->sculkBefore=sculk(client));
        step("restored sculk wakes branch and resumes growth",()->{},()->sculk(client)>sculkBefore,60_000);
        command(client, "reconnected branch reports active growth", "chronicles setup tool \"quiet_below:ecology\" refresh", ".*Active; spreading along reachable exposed surfaces.*");
        command(client,"persisted cleanup sees newly loaded/restarted world","setblock 7 -64 7 sculk",".*Changed.*");
        step("cleanup remains enabled after saving restart",()->{},()->client.world.getBlockState(new BlockPos(7,-64,7)).isAir(),10_000);
        check("takeover ownership survives restart",()->require(client.world.getBlockState(new BlockPos(12,64,8)).isOf(Blocks.SCULK),"restarted cleanup lost takeover ownership"));
        command(client,"stop cleanup independently of growth","chronicles setup tool \"quiet_below:ownership\" cleanup_off","Non-campaign sculk cleanup stopped.*");
        command(client,"place stray after cleanup disabled","setblock 7 -64 7 sculk",".*Changed.*");
        delay("disabled cleanup does not delete stray",600);
        check("cleanup off preserves non-campaign sculk",()->require(client.world.getBlockState(new BlockPos(7,-64,7)).isOf(Blocks.SCULK),"disabled cleanup deleted sculk"));
        command(client, "disable ecology without terrain rollback", "chronicles setup tool \"quiet_below:ecology\" disable", "Growth disabled; owned terrain and history retained.*");
        check("capture disabled terrain", () -> sculkBefore = sculk(client));
        delay("disabled ecology remains inert", 1_100);
        check("disable preserves existing terrain", () -> require(sculk(client) == sculkBefore, "disable changed terrain"));
        command(client,"leave safe paused snapshot for human review","chronicle session pause","Paused Chronicle session.*");
        screenshot(client, "ecology-disabled-after-prune.png");
    }

    private int sculk(MinecraftClient client) {
        int count = 0;
        for (int x = 0; x <= 14; x++) for (int y = 64; y <= 66; y++) for (int z = 0; z <= 2; z++)
            if (client.world.getBlockState(new BlockPos(x, y, z)).isOf(Blocks.SCULK)) count++;
        return count;
    }

    private int sculkBeyondBarrier(MinecraftClient client) {
        int count = 0;
        for (int x = 9; x <= 14; x++) for (int y = 64; y <= 66; y++) for (int z = 0; z <= 2; z++)
            if (client.world.getBlockState(new BlockPos(x, y, z)).isOf(Blocks.SCULK)) count++;
        return count;
    }

    private void screenshot(MinecraftClient client, String name) {
        check("clear overlays for " + name, () -> { client.inGameHud.getChatHud().clear(false); client.getToastManager().clear(); });
        delay("render " + name, 400);
        step("capture " + name, () -> ScreenshotRecorder.saveScreenshot(Path.of(OUTPUT).toFile(), name, client.getFramebuffer(), 1, ignored -> {}),
                () -> Files.isRegularFile(Path.of(OUTPUT, "screenshots", name)), 20_000);
    }

    private void command(MinecraftClient client, String name, String command, String response) { step(name, () -> send(client, command), () -> received(response), 20_000); }
    private void delay(String name, long millis) { step(name, () -> until = System.nanoTime() + millis * 1_000_000L, () -> System.nanoTime() >= until, millis + 5_000); }
    private void check(String name, Runnable assertion) { step(name, assertion, () -> true, 1_000); }
    private void step(String name, Runnable start, BooleanSupplier done, long timeoutMillis) { steps.add(new Step(name, start, done, timeoutMillis)); }
    private void send(MinecraftClient client, String command) { client.player.networkHandler.sendChatCommand(command); }
    private boolean received(String expression) { Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE); return messages.subList(baseline, messages.size()).stream().anyMatch(message -> pattern.matcher(message).matches()); }

    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (finished) return;
        finished = true;
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Files.writeString(Path.of(OUTPUT).resolve("ecology-" + PHASE + ".json"), new Gson().toJson(Map.of(
                    "result", pass ? "PASS" : "FAIL", "detail", detail, "checks", checks,
                    "step", index < steps.size() ? steps.get(index).name : "complete", "messages", messages)));
        } catch (Exception error) { error.printStackTrace(); }
        client.scheduleStop();
    }

    private static void require(boolean condition, String detail) { if (!condition) throw new IllegalStateException(detail); }
    private record Step(String name, Runnable start, BooleanSupplier done, long timeoutMillis) { }
}
