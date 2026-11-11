package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.NarratorMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.block.SculkSensorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.util.math.BlockPos;

/** Opt-in real-client rehearsal of the complete vanilla warning and Warden lifecycle. */
final class QuietBelowWardenSmoke {
    private static final String OUTPUT = System.getenv("CHRONICLE_WARDEN_SMOKE");
    private static final String ADDRESS = "127.0.0.1:25575";
    private final List<String> messages = new ArrayList<>();
    private final List<String> checks = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private final long overallDeadline = System.nanoTime() + 240_000_000_000L;
    private int index;
    private int baseline;
    private long deadline;
    private long until;
    private boolean connected;
    private boolean built;
    private boolean started;
    private boolean finished;

    static void register() {
        if (OUTPUT == null) {
            return;
        }
        var smoke = new QuietBelowWardenSmoke();
        ClientReceiveMessageEvents.GAME.register((text, overlay) -> smoke.messages.add(text.getString()));
        ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
    }

    private void tick(MinecraftClient client) {
        if (finished) {
            return;
        }
        try {
            require(System.nanoTime() < overallDeadline, "Warden smoke overall timeout");
            if (client.player == null && client.getOverlay() == null
                    && client.currentScreen instanceof AccessibilityOnboardingScreen) {
                throw new IllegalStateException("pack preferences failed to suppress accessibility onboarding");
            }
            if (!connected && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                require(client.options.getGuiScale().getValue() == 2, "pack GUI scale not loaded");
                require(client.options.getNarrator().getValue() == NarratorMode.OFF, "pack narrator setting not loaded");
                require(!client.options.onboardAccessibility, "pack onboarding preference not loaded");
                require(client.options.skipMultiplayerWarning, "pack multiplayer acknowledgement not loaded");
                checks.add("fresh client loaded the shipped client defaults");
                connected = true;
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS),
                        new ServerInfo("Chronicle Warden fixture", ADDRESS, ServerInfo.ServerType.OTHER), false, null);
            }
            if (client.player == null || client.world == null || client.currentScreen != null) {
                return;
            }
            require(client.getCurrentServerEntry() != null && ADDRESS.equals(client.getCurrentServerEntry().address),
                    "Warden fixture address mismatch");
            if (!built) {
                build(client);
                built = true;
            }
            if (index >= steps.size()) {
                finish(client, true, "vanilla Warden warning, emergence, dig, and removal rehearsal complete");
                return;
            }
            Step step = steps.get(index);
            if (!started) {
                baseline = messages.size();
                deadline = System.nanoTime() + step.timeoutMillis * 1_000_000L;
                step.start.run();
                started = true;
            }
            if (step.done.getAsBoolean()) {
                checks.add(step.name);
                index++;
                started = false;
            } else {
                require(System.nanoTime() < deadline, "timed out: " + step.name);
            }
        } catch (Exception error) {
            finish(client, false, error.toString());
        }
    }

    private void build(MinecraftClient client) {
        command(client, "verify strict disposable fixture guard", "quiet_below_warden_trial guard",
                "PASS vanilla Warden: strict offline loopback test-runs guard accepted");
        command(client, "set fixture difficulty", "difficulty hard", ".*difficulty.*Hard.*");
        command(client, "disable ambient mob spawning", "gamerule doMobSpawning false", ".*doMobSpawning.*false.*");
        command(client, "enable vanilla Warden spawning", "gamerule doWardenSpawning true", ".*doWardenSpawning.*true.*");
        command(client, "build sealed dark chamber", "fill -20 50 -20 20 61 20 deepslate", ".*filled.*");
        command(client, "open loaded chamber interior", "fill -19 51 -19 19 60 19 air", ".*filled.*");
        command(client, "isolate the ecology fixture from growth substrate",
                "fill -19 50 -19 19 50 19 polished_deepslate", ".*filled.*");
        command(client, "place temporary Heart", "setblock 0 50 -2 reinforced_deepslate", ".*Changed.*");
        command(client, "place rooted sculk link one", "setblock 0 50 -1 sculk", ".*Changed.*");
        command(client, "place rooted sculk link two", "setblock 0 50 0 sculk", ".*Changed.*");
        command(client, "extend rooted sculk to sensor", "fill 0 50 1 0 50 7 sculk", ".*filled.*");
        command(client, "place summoning source shrieker",
                "setblock 0 51 0 sculk_shrieker[can_summon=true,shrieking=false,waterlogged=false]", ".*Changed.*");
        command(client, "place vanilla source sensor", "setblock 0 51 7 sculk_sensor", ".*Changed.*");
        command(client, "place isolated sculk support", "setblock 18 50 18 sculk", ".*Changed.*");
        command(client, "place disconnected summoning shrieker",
                "setblock 18 51 18 sculk_shrieker[can_summon=true,shrieking=false,waterlogged=false]", ".*Changed.*");
        command(client, "enable Editing", "chronicle editing on", "Editing enabled.*");
        command(client, "create source draft", "chronicle regions create warden_source", "Region selection warden_source opened.*");
        command(client, "set source first corner", "chronicle regions pos1 0 50 -2", "Set Region draft warden_source position 1.*");
        command(client, "set source second corner", "chronicle regions pos2 0 51 7", "Set Region draft warden_source position 2.*");
        command(client, "save source region", "chronicle regions save", "Saved Region 'warden_source'\\.");
        command(client, "look down at Heart", "tp @s 0.5 51 -1.5 0 90", ".*Teleported.*");
        command(client, "bind Heart anchor", "chronicle locations blocks bind warden_heart target", "Saved Block site warden_heart.*");
        command(client, "assign source role", "quiet_below_authoring_trial assign-source warden_source", "PASS canonical authoring assign-source:.*");
        command(client, "assign Heart role", "quiet_below_authoring_trial assign-heart warden_heart", "PASS canonical authoring assign-heart:.*");
        command(client, "index source", "chronicle setup tool \"quiet_below:ecology\" index", "Source indexing started.*");
        delay("finish source index", 1_000);
        command(client, "enable ecology hooks", "chronicle setup tool \"quiet_below:ecology\" enable", "Growth enabled.*");
        // This rehearsal tests vanilla shrieker warnings, separately from the Heart's direct
        // last-stand summon. Use the ordinary editable delay rather than bypassing either path.
        for (int tuning = 0; tuning < 6; tuning++) {
            command(client, "isolate vanilla warnings from Heart last stand " + tuning,
                    "chronicle setup tool \"quiet_below:heart\" delay_longer",
                    "Heart weakness rules saved.*");
        }
        command(client, "protected Heart assignment cannot be cleared", "quiet_below_authoring_trial clear-heart", ".*bound or historical Relic Heart assignment cannot be changed.*");
        command(client, "create disconnected draft", "chronicle regions create warden_disconnected", "Region selection warden_disconnected opened.*");
        command(client, "set disconnected first corner", "chronicle regions pos1 18 50 18", "Set Region draft warden_disconnected position 1.*");
        command(client, "set disconnected second corner", "chronicle regions pos2 18 51 18", "Set Region draft warden_disconnected position 2.*");
        command(client, "save disconnected region", "chronicle regions save", "Saved Region 'warden_disconnected'\\.");
        command(client, "assign takeover role", "quiet_below_authoring_trial assign-takeover warden_disconnected", "PASS canonical authoring assign-takeover:.*");
        command(client, "claim disconnected shrieker", "chronicle setup tool \"quiet_below:ownership\" claim", "Region takeover started.*");
        delay("finish disconnected takeover", 1_000);
        command(client, "settle away from the source before active play", "tp @s 18.5 51 14.5 180 0", ".*Teleported.*");
        delay("finish inactive neutral-floor settlement", 500);
        command(client, "start session for pause gate", "chronicle session start", "Started Chronicle session.*");
        command(client, "join active participation", "chronicle players join", ".*Participation enabled.*");
        delay("resolve active root topology and vitality", 3_000);
        command(client, "assert fresh viable fixture", "quiet_below_warden_trial fixture", "PASS vanilla Warden: fixture is hard.*");
        command(client, "pause occupied session", "chronicle session pause", "Paused Chronicle session.*");
        land(client, "paused direct shrieker landing", 0.5, 0.5);
        delay("allow paused rooted vibration travel", 1_000);
        command(client, "paused rooted intake stays inert", "quiet_below_warden_trial inert", "PASS vanilla Warden: suppressed shrieker.*");
        command(client, "leave rooted shrieker while still paused", "tp @s 18.5 51 14.5 180 0", ".*Teleported.*");
        delay("settle on neutral floor while paused", 500);
        command(client, "paused relocation stays inert", "quiet_below_warden_trial inert", "PASS vanilla Warden: suppressed shrieker.*");
        command(client, "resume active Warden rehearsal", "chronicle session resume", "Resumed Chronicle session.*");

        land(client, "disconnected direct shrieker landing", 18.5, 18.5);
        delay("allow disconnected vibration travel", 1_000);
        command(client, "disconnected owned shrieker stays inert", "quiet_below_warden_trial inert", "PASS vanilla Warden: suppressed shrieker.*");

        land(client, "rooted landing queued before disconnection", 0.5, 13.5);
        step("vanilla sensor relays the landing toward the shrieker", () -> { },
                () -> !SculkSensorBlock.isInactive(client.world.getBlockState(new BlockPos(0, 51, 7))), 5_000);
        command(client, "disconnect root before queued vibration delivery", "setblock 0 50 -1 white_wool", ".*Changed.*");
        delay("allow queued disconnected response and topology to settle", 2_000);
        command(client, "disconnected queued response stays inert", "quiet_below_warden_trial disconnected",
                "PASS vanilla Warden: the physically disconnected queued response.*");
        command(client, "restore physical root link", "setblock 0 50 -1 sculk", ".*Changed.*");
        delay("resolve restored physical root", 2_000);
        command(client, "assert restored viable fixture", "quiet_below_warden_trial fixture", "PASS vanilla Warden: fixture is hard.*");
        command(client, "protect survival landings without changing warning eligibility",
                "effect give @s resistance infinite 4 true", ".*Applied effect.*Resistance.*");
        command(client, "use survival warning eligibility", "gamemode survival @s", ".*(Survival|survival).*");

        for (int level = 1; level <= 4; level++) {
            land(client, "physical warning landing " + level, 0.5, 13.5);
            delay("allow warning vibration " + level + " to arrive", 1_000);
            command(client, "assert vanilla warning " + level, "quiet_below_warden_trial warning " + level,
                    "PASS vanilla Warden: warning level " + level + ".*");
            if (level < 4) {
                delay("respect vanilla 200-tick warning cooldown " + level, 11_000);
            }
        }

        command(client, "become quiet creative observer", "gamemode creative @s", ".*(Creative|creative).*");
        step("observe one Warden emerging naturally", () -> { },
                () -> wardens(client).size() == 1 && wardens(client).getFirst().getPose() == EntityPose.EMERGING, 20_000);
        command(client, "assert triggered emergence and vanilla dig cooldown", "quiet_below_warden_trial emerging",
                "PASS vanilla Warden: one triggered Warden is EMERGING.*");
        screenshot(client, "warden-emerging.png");
        step("wait through vanilla 1200-tick dig cooldown", () -> { },
                () -> wardens(client).size() == 1 && wardens(client).getFirst().getPose() == EntityPose.DIGGING, 90_000);
        command(client, "assert vanilla digging pose", "quiet_below_warden_trial digging",
                "PASS vanilla Warden: the quiet untargeted Warden entered.*");
        screenshot(client, "warden-digging.png");
        step("observe vanilla Warden removal after digging", () -> { }, () -> wardens(client).isEmpty(), 15_000);
        command(client, "assert removal without kill", "quiet_below_warden_trial gone",
                "PASS vanilla Warden: the digging Warden removed itself.*");
    }

    private void land(MinecraftClient client, String name, double x, double z) {
        command(client, name + " teleport above floor", "tp @s " + x + " 57 " + z + " 180 0", ".*Teleported.*");
        step(name + " becomes airborne", () -> { }, () -> !client.player.isOnGround(), 5_000);
        step(name + " produces a real landing vibration", () -> { }, () -> client.player.isOnGround(), 10_000);
    }

    private List<WardenEntity> wardens(MinecraftClient client) {
        List<WardenEntity> result = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (entity instanceof WardenEntity warden) {
                result.add(warden);
            }
        }
        return result;
    }

    private void screenshot(MinecraftClient client, String name) {
        check("clear overlays for " + name, () -> {
            client.inGameHud.getChatHud().clear(false);
            client.getToastManager().clear();
        });
        delay("render " + name, 400);
        step("capture " + name,
                () -> ScreenshotRecorder.saveScreenshot(Path.of(OUTPUT).toFile(), name, client.getFramebuffer(), 1, ignored -> { }),
                () -> Files.isRegularFile(Path.of(OUTPUT, "screenshots", name)), 20_000);
    }

    private void command(MinecraftClient client, String name, String command, String response) {
        step(name, () -> send(client, command), () -> received(response), 20_000);
    }

    private void delay(String name, long millis) {
        step(name, () -> until = System.nanoTime() + millis * 1_000_000L,
                () -> System.nanoTime() >= until, millis + 5_000);
    }

    private void check(String name, Runnable assertion) {
        step(name, assertion, () -> true, 1_000);
    }

    private void step(String name, Runnable start, BooleanSupplier done, long timeoutMillis) {
        steps.add(new Step(name, start, done, timeoutMillis));
    }

    private void send(MinecraftClient client, String command) {
        client.player.networkHandler.sendChatCommand(command);
    }

    private boolean received(String expression) {
        Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
        return messages.subList(baseline, messages.size()).stream().anyMatch(message -> pattern.matcher(message).matches());
    }

    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (finished) {
            return;
        }
        finished = true;
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("result", pass ? "PASS" : "FAIL");
            report.put("detail", detail);
            report.put("checks", checks);
            report.put("step", index < steps.size() ? steps.get(index).name : "complete");
            report.put("messages", messages);
            report.put("screenshots", List.of("screenshots/warden-emerging.png", "screenshots/warden-digging.png"));
            Files.writeString(Path.of(OUTPUT).resolve("warden-smoke.json"), new Gson().toJson(report));
        } catch (Exception error) {
            error.printStackTrace();
        }
        client.scheduleStop();
    }

    private static void require(boolean condition, String detail) {
        if (!condition) {
            throw new IllegalStateException(detail);
        }
    }

    private record Step(String name, Runnable start, BooleanSupplier done, long timeoutMillis) {
    }
}
