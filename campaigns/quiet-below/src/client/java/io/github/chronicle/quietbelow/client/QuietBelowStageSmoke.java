package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import io.github.chronicle.quietbelow.QuietBelow;
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
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/** Opt-in bounded campaign rehearsal on the disposable loopback fixture only. */
final class QuietBelowStageSmoke {
    private static final String OUTPUT = System.getenv("CHRONICLE_STAGE_SMOKE");
    private static final String PHASE = System.getenv("CHRONICLE_STAGE_PHASE");
    private static final String ADDRESS = "127.0.0.1:25575";
    private final List<String> messages = new ArrayList<>();
    private final List<String> checks = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private final long overallDeadline = System.nanoTime() + 180_000_000_000L;
    private int index;
    private int baseline;
    private long deadline;
    private long until;
    private boolean started;
    private boolean connected;
    private boolean built;
    private boolean finished;
    private String token;
    private int frozenCount;

    static void register() {
        if (OUTPUT == null) return;
        if (!"initial".equals(PHASE) && !"restart".equals(PHASE) && !"pilot".equals(PHASE)) throw new IllegalArgumentException("Invalid stage smoke phase");
        var smoke = new QuietBelowStageSmoke();
        ClientReceiveMessageEvents.GAME.register((text, overlay) -> smoke.messages.add(text.getString()));
        ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
    }

    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            require(System.nanoTime() < overallDeadline, "stage smoke overall timeout");
            if (client.player == null && client.getOverlay() == null && client.currentScreen instanceof AccessibilityOnboardingScreen) client.setScreen(new TitleScreen());
            if (!connected && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                connected = true;
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS),
                        new ServerInfo("Chronicle stage fixture", ADDRESS, ServerInfo.ServerType.OTHER), false, null);
            }
            if (client.player == null || client.world == null || client.currentScreen != null) return;
            require(client.getCurrentServerEntry() != null && ADDRESS.equals(client.getCurrentServerEntry().address), "stage fixture address mismatch");
            if (!built) { build(client); built = true; }
            if (index >= steps.size()) { finish(client, true, "stage rehearsal complete"); return; }
            Step step = steps.get(index);
            if (!started) {
                baseline = messages.size();
                deadline = System.nanoTime() + 20_000_000_000L;
                step.start.run();
                started = true;
            }
            if (step.done.getAsBoolean()) {
                checks.add(step.name);
                index++;
                started = false;
            } else require(System.nanoTime() < deadline, "timed out: " + step.name);
        } catch (Exception error) { finish(client, false, error.toString()); }
    }

    private void build(MinecraftClient client) {
        if (PHASE.equals("pilot")) {
            buildPilot(client);
        } else if (PHASE.equals("initial")) {
            command(client, "prepare exact natural template", "fill 0 -60 8 7 -59 11 stone", ".*filled.*");
            command(client, "move to template", "tp @s 4 -60 17 180 10", ".*Teleported.*");
            command(client, "define 64-position zone", "chronicle game quiet-below zone define rehearsal 0 -60 8 7 -59 11", "Defined stage zone rehearsal.*64 positions.*");
            step("preview exact zone and token", () -> send(client, "chronicle game quiet-below stage preview rehearsal"), () -> {
                var pattern = Pattern.compile("Stage preview rehearsal: positions=64; token=([a-f0-9]{12})\\.");
                for (String message : messages.subList(baseline, messages.size())) {
                    var match = pattern.matcher(message);
                    if (match.matches()) { token = match.group(1); return true; }
                }
                return false;
            });
            step("inactive apply denied", () -> send(client, "chronicle game quiet-below stage apply rehearsal " + token), () -> received(".*active.*"));
            check("inactive apply changed no blocks", () -> require(count(client, Blocks.STONE) == 64, "inactive apply wrote blocks"));
            command(client, "activate rehearsal", "chronicle session start", "Started Chronicle session.*");
            command(client, "reject wrong token", "chronicle game quiet-below stage apply rehearsal 000000000000", ".*(token|preview).*" );
            command(client, "make player edit before apply", "setblock 0 -60 8 gold_block", "Changed the block.*");
            step("changed template denied", () -> send(client, "chronicle game quiet-below stage apply rehearsal " + token), () -> received(".*(changed|match|conflict).*"));
            check("player edit preserved", () -> require(client.world.getBlockState(new BlockPos(0, -60, 8)).isOf(Blocks.GOLD_BLOCK), "apply overwrote player edit"));
            command(client, "restore approved natural block", "setblock 0 -60 8 stone", "Changed the block.*");
            step("queue and pause bounded stage", () -> {
                send(client, "chronicle game quiet-below stage apply rehearsal " + token);
                send(client, "chronicle session pause");
            }, () -> received("Stage apply queued:.*") && received("Paused Chronicle session.*"));
            delay("settle pause", 400);
            check("capture paused progress", () -> frozenCount = count(client, Blocks.SCULK));
            delay("allow frozen job observation", 1_100);
            check("pause prevents further stage writes", () -> {
                require(frozenCount < 64, "stage finished before pause; suspension untested");
                require(count(client, Blocks.SCULK) == frozenCount && count(client, Blocks.STONE) + frozenCount == 64, "paused stage advanced or escaped its template");
            });
            command(client, "persist paused stage status", "chronicle game quiet-below stage status", ".*status=APPLYING.*");
        } else {
            command(client, "restart requires explicit recovery", "chronicle game quiet-below stage status", ".*status=INTERRUPTED.*");
            command(client, "inactive resume denied", "chronicle game quiet-below stage resume", ".*active.*");
            command(client, "resume campaign", "chronicle session resume", "Resumed Chronicle session.*");
            command(client, "explicitly resume stage", "chronicle game quiet-below stage resume", "Stage resumed:.*");
            step("complete exactly 64 sculk placements", () -> {}, () -> count(client, Blocks.SCULK) == 64);
            command(client, "completed stage status", "chronicle game quiet-below stage status", ".*status=APPLIED.*");
            command(client, "review clue event", "chronicle events run review action \"test:marker_clue\"", ".*Ready.*");
            command(client, "reveal clue event", "chronicle events run confirm action \"test:marker_clue\"", "Chronicle event test:marker_clue completed.*");
            command(client, "place clue marker outside stage", "setblock 4 -60 13 quiet_below:resonance_marker[facing=south]", "Changed the block.*");
            command(client, "empty hand for public clue", "clear @s", ".*(Removed|No items).*" );
            step("new clue is accessible without lens", () -> {
                client.player.getInventory().setSelectedSlot(0);
                client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                        new BlockHitResult(new Vec3d(4.5, -59.5, 13.5), Direction.SOUTH, new BlockPos(4, -60, 13), false));
            }, () -> received("The new trace follows the supports rather than the ore\\. The survey notes show the same pattern\\."));
            check("clear rehearsal overlays", () -> {
                client.inGameHud.getChatHud().clear(false);
                client.getToastManager().clear();
            });
            delay("render applied stage", 300);
            step("capture applied stage", () -> ScreenshotRecorder.saveScreenshot(Path.of(OUTPUT).toFile(), "stage-applied.png",
                    client.getFramebuffer(), 1, ignored -> {}),
                    () -> Files.isRegularFile(Path.of(OUTPUT, "screenshots", "stage-applied.png")));
            command(client, "edit one completed position", "setblock 0 -60 8 gold_block", "Changed the block.*");
            command(client, "undo refuses player edit", "chronicle game quiet-below stage undo", ".*(changed|match|conflict).*" );
            check("undo conflict preserves all positions", () -> require(count(client, Blocks.GOLD_BLOCK) == 1 && count(client, Blocks.SCULK) == 63, "undo overwrote conflict or partially undid stage"));
            command(client, "restore expected sculk", "setblock 0 -60 8 sculk", "Changed the block.*");
            command(client, "change one cosmetic sculk band", "setblock 0 -60 8 sculk[quiet_below_vitality=3]", "Changed the block.*");
            command(client, "undo refuses changed cosmetic band", "chronicle game quiet-below stage undo", ".*(changed|match|conflict).*" );
            check("cosmetic conflict preserves all blocks", () -> require(count(client, Blocks.SCULK) == 64
                    && client.world.getBlockState(new BlockPos(0, -60, 8)).get(io.github.chronicle.quietbelow.content.SculkAppearance.VITALITY) == 3,
                    "undo normalized away a changed cosmetic state"));
            command(client, "restore healthy canonical sculk", "setblock 0 -60 8 sculk", "Changed the block.*");
            command(client, "queue safe latest undo", "chronicle game quiet-below stage undo", "Stage undo queued:.*");
            step("undo restores all original states", () -> {}, () -> count(client, Blocks.STONE) == 64);
            command(client, "undo persisted", "chronicle game quiet-below stage status", ".*status=UNDONE.*");
            command(client, "end rehearsal", "chronicle session end", "Ended Chronicle session.*");
        }
    }

    private void buildPilot(MinecraftClient client) {
        step("normal player chunk loading covers pilot site", () -> {}, () ->
                client.world.isChunkLoaded(new BlockPos(-48,64,-48)) && client.world.isChunkLoaded(new BlockPos(48,64,48)));
        command(client, "build fresh isolated pilot", "function chronicle_pilot:build", ".*(Executed|function).*" );
        delay("synchronize pilot block entities", 1_000);
        step("pilot chunks and custom marker loaded", () -> {}, () -> client.world.getBlockState(new BlockPos(2, 57, -37)).isOf(QuietBelow.BLOCK));
        check("six native sign clues arrived", () -> {
            int[][] positions = {{-2,65,6},{0,65,6},{2,65,6},{1,55,-37},{23,65,1},{-11,55,-36}};
            for (int[] p : positions) {
                var block = client.world.getBlockEntity(new BlockPos(p[0], p[1], p[2]));
                require(block instanceof SignBlockEntity, "missing clue sign at " + java.util.Arrays.toString(p));
                var lines = ((SignBlockEntity) block).getFrontText().getMessages(false);
                require(java.util.Arrays.stream(lines).allMatch(line -> !line.getString().isBlank() && !line.getString().startsWith("{")), "empty or unparsed clue text");
            }
        });
        check("reserved pilot seam remains natural", () -> {
            for (int x=3;x<=6;x++) for (int y=55;y<=58;y++) for (int z=-36;z<=-33;z++)
                require(client.world.getBlockState(new BlockPos(x,y,z)).isOf(Blocks.DEEPSLATE), "pilot seam escaped its original state");
            require(client.world.getBlockState(new BlockPos(4,65,5)).isAir() && client.world.getBlockState(new BlockPos(4,66,5)).isAir(), "common entrance is blocked");
            require(client.world.getBlockState(new BlockPos(0,65,0)).isAir(), "pilot spawn is obstructed");
        });
        command(client, "overview position", "tp @s 0 86 46 180 34", ".*Teleported.*");
        photograph(client, "pilot-overview.png");
        command(client, "walk in survival", "gamemode survival @s", ".*(Survival|already).*" );
        command(client, "arrive at mine stair", "tp @s -0.5 64 -31.5 180 0", ".*Teleported.*");
        step("walk down the physical mine stairs", () -> until = System.nanoTime() + 4_000_000_000L, () -> {
            client.options.forwardKey.setPressed(true);
            if (System.nanoTime() < until) return false;
            client.options.forwardKey.setPressed(false);
            require(client.player.getZ() < -39 && client.player.getY() <= 56, "mine stairs are not traversable: " + client.player.getX() + "," + client.player.getY() + "," + client.player.getZ());
            return true;
        });
        command(client, "view marker alcove", "tp @s -2 55 -37 -90 5", ".*Teleported.*");
        photograph(client, "pilot-marker-alcove.png");
        command(client, "public board position", "tp @s 0 65 10 180 8", ".*Teleported.*");
        photograph(client, "pilot-public-clues.png");
    }

    private void photograph(MinecraftClient client, String filename) {
        check("clear overlays for " + filename, () -> {
            client.inGameHud.getChatHud().clear(false);
            client.getToastManager().clear();
        });
        delay("render " + filename, 500);
        step("capture " + filename, () -> ScreenshotRecorder.saveScreenshot(Path.of(OUTPUT).toFile(), filename,
                client.getFramebuffer(), 1, ignored -> {}), () -> Files.isRegularFile(Path.of(OUTPUT, "screenshots", filename)));
    }

    private int count(MinecraftClient client, net.minecraft.block.Block block) {
        int count = 0;
        for (int x = 0; x < 8; x++) for (int y = -60; y <= -59; y++) for (int z = 8; z < 12; z++)
            if (client.world.getBlockState(new BlockPos(x, y, z)).isOf(block)) count++;
        return count;
    }
    private void command(MinecraftClient client, String name, String command, String response) { step(name, () -> send(client, command), () -> received(response)); }
    private void send(MinecraftClient client, String command) { client.player.networkHandler.sendChatCommand(command); }
    private boolean received(String expression) {
        Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
        return messages.subList(baseline, messages.size()).stream().anyMatch(message -> pattern.matcher(message).matches());
    }
    private void check(String name, Runnable assertion) { step(name, assertion, () -> true); }
    private void delay(String name, long ms) { step(name, () -> until = System.nanoTime() + ms * 1_000_000, () -> System.nanoTime() >= until); }
    private void step(String name, Runnable start, BooleanSupplier done) { steps.add(new Step(name, start, done)); }
    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (finished) return;
        finished = true;
        client.options.forwardKey.setPressed(false);
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Files.writeString(Path.of(OUTPUT).resolve("stage-" + PHASE + ".json"), new Gson().toJson(Map.of(
                    "result", pass ? "PASS" : "FAIL", "detail", detail, "checks", checks,
                    "step", index < steps.size() ? steps.get(index).name : "complete", "messages", messages)));
        } catch (Exception error) { error.printStackTrace(); }
        client.scheduleStop();
    }
    private static void require(boolean condition, String error) { if (!condition) throw new IllegalStateException(error); }
    private record Step(String name, Runnable start, BooleanSupplier done) { }
}
