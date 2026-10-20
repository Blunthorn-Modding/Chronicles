package io.github.chronicle.core.client.testing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.client.DossierScreen;
import io.github.chronicle.core.client.AuthoringPreviewRenderer;
import io.github.chronicle.core.client.AuthoringScreen;
import io.github.chronicle.core.authoring.ui.AuthoringUiModel.*;
import io.github.chronicle.core.network.AuthoringUiPayloads;
import io.github.chronicle.core.network.ProfilePayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explicit opt-in, two-client M1/M2 regression. The host owns the disposable
 * server lifecycle; this client only sends normal player requests and reads the
 * staged world's JSON as an independent durability oracle.
 */
public final class FabricRegressionSmoke {
    private static final String OUTPUT_ENV = System.getenv("CHRONICLE_FABRIC_SMOKE");
    private static final String ROLE_ENV = System.getenv("CHRONICLE_FABRIC_ROLE");
    private static final String PHASE_ENV = System.getenv("CHRONICLE_FABRIC_PHASE");
    private static final String SERVER_ENV = System.getenv("CHRONICLE_FABRIC_SERVER_DIR");
    private static final long OVERALL_TIMEOUT_NANOS = 240_000_000_000L;
    private static final long DEFAULT_STEP_TIMEOUT_NANOS = 15_000_000_000L;
    private static final Pattern TIME_REPLY = Pattern.compile("^The time is ([0-9]+)\\.?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PIG_Y_REPLY = Pattern.compile(
            ".*following entity data:\\s*(-?[0-9]+(?:\\.[0-9]+)?)d?\\.?$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern UNAUTHORIZED_REPLY = Pattern.compile(
            ".*(unknown or incomplete command|incorrect argument for command|requires operator permission|permission).*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final UUID FABRIC_A = offlineUuid("ChronicleFabricA");
    private static final UUID FABRIC_B = offlineUuid("ChronicleFabricB");
    private static final List<String> INITIAL_AUDIT = List.of(
            "session.start", "flag.set", "flag.set", "event.trigger", "session.pause", "observation.reset.requested", "session.resume", "session.pause");
    private static final List<String> RESTART_AUDIT = List.of(
            "session.start", "flag.set", "flag.set", "event.trigger", "session.pause", "observation.reset.requested", "session.resume", "session.pause",
            "session.resume", "session.end", "session.start", "session.end");

    private static FabricRegressionSmoke instance;

    private final String role;
    private final String phase;
    private final Path output;
    private final Path serverRoot;
    private final Path campaignFile;
    private final Path profilesDirectory;
    private final Path playRuntimeFile;
    private final long overallDeadline = System.nanoTime() + OVERALL_TIMEOUT_NANOS;
    private final List<Message> messages = new ArrayList<>();
    private final List<ProfilePayloads.View> views = new ArrayList<>();
    private final List<ProfilePayloads.Notice> notices = new ArrayList<>();
    private final List<Task> tasks = new ArrayList<>();
    private final List<String> completedSteps = new ArrayList<>();
    private final Map<String, String> snapshots = new HashMap<>();

    private int authoringViews;
    private Snapshot lastAuthoringState;
    private final List<String> authoringNotices = new ArrayList<>();
    private boolean expectTestDeath;
    public static void onAuthoringView(Snapshot state) { if (instance != null) { instance.authoringViews++; instance.lastAuthoringState = state; } }
    public static void onAuthoringNotice(String notice) { if (instance != null) instance.authoringNotices.add(notice); }

    private long messageSequence;
    private int taskIndex;
    private long taskDeadline;
    private boolean taskStarted;
    private boolean connecting;
    private boolean scriptBuilt;
    private boolean complete;
    private int firstTime;
    private int secondTime;
    private double firstPigY;
    private double secondPigY;

    private FabricRegressionSmoke(String role, String phase, Path output, Path serverRoot) throws IOException {
        if (!role.equals("A") && !role.equals("B")) throw new IllegalArgumentException("CHRONICLE_FABRIC_ROLE must be A or B");
        if (!phase.equals("initial") && !phase.equals("restart")) throw new IllegalArgumentException("CHRONICLE_FABRIC_PHASE must be initial or restart");
        this.role = role;
        this.phase = phase;
        this.output = output.toAbsolutePath().normalize();
        this.serverRoot = serverRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.serverRoot)) throw new IllegalArgumentException("CHRONICLE_FABRIC_SERVER_DIR must be the staged server directory");
        if (this.output.startsWith(this.serverRoot)) throw new IllegalArgumentException("Fabric smoke output must be outside the staged server directory");
        Files.createDirectories(this.output);
        Path worldRoot = this.serverRoot.resolve("world").normalize();
        if (!worldRoot.startsWith(this.serverRoot)) throw new IllegalArgumentException("resolved test world escaped the staged server directory");
        this.campaignFile = worldRoot.resolve("chronicle/campaign-state.json");
        this.profilesDirectory = worldRoot.resolve("chronicle/profiles");
        this.playRuntimeFile = worldRoot.resolve("chronicle/play-runtime.json");
    }

    /** Registers only when the host explicitly supplies the smoke output path. */
    public static void register() {
        if (OUTPUT_ENV == null) return;
        try {
            if (ROLE_ENV == null || PHASE_ENV == null || SERVER_ENV == null) {
                throw new IllegalArgumentException("Fabric smoke role, phase and staged server directory are required");
            }
            instance = new FabricRegressionSmoke(ROLE_ENV, PHASE_ENV, Path.of(OUTPUT_ENV), Path.of(SERVER_ENV));
            ClientTickEvents.END_CLIENT_TICK.register(instance::tick);
            ClientReceiveMessageEvents.GAME.register(instance::onGameMessage);
        } catch (IOException | RuntimeException error) {
            ChronicleCore.LOGGER.error("Fabric regression smoke setup failed", error);
            writeSetupFailure(error);
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    public static void onView(ProfilePayloads.View view) {
        if (instance != null && !instance.complete) instance.views.add(view);
    }

    public static void onNotice(ProfilePayloads.Notice notice) {
        if (instance != null && !instance.complete) instance.notices.add(notice);
    }

    private void onGameMessage(Text message, boolean overlay) {
        if (complete) return;
        messages.add(new Message(++messageSequence, message.getString(), overlay));
        if (messages.size() > 300) messages.removeFirst();
    }

    private void tick(MinecraftClient client) {
        if (complete) return;
        try {
            if (System.nanoTime() > overallDeadline) throw new IllegalStateException("overall 240 second deadline exceeded");
            if (client.player == null && client.getOverlay() == null && client.currentScreen instanceof AccessibilityOnboardingScreen) {
                client.setScreen(new TitleScreen());
            }
            if (!connecting && client.player == null && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                connecting = true;
                String address = "127.0.0.1:25575";
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(address),
                        new ServerInfo("Chronicle Fabric regression", address, ServerInfo.ServerType.OTHER), false, null);
                return;
            }
            if (client.player == null) return;
            if (expectTestDeath && client.currentScreen instanceof DeathScreen) { client.player.requestRespawn(); client.setScreen(null); }
            if (client.currentScreen instanceof DossierScreen) client.setScreen(null);
            if (client.currentScreen != null && !(client.currentScreen instanceof AuthoringScreen)) return;
            if (!scriptBuilt) {
                ServerInfo server = client.getCurrentServerEntry();
                require(server != null && server.address.equals("127.0.0.1:25575"),
                        "refusing Fabric smoke mutations outside the exact isolated fixture address");
                require(client.player.getUuid().equals(role.equals("A") ? FABRIC_A : FABRIC_B),
                        "connected account UUID does not match deterministic offline UUID");
                require(ClientPlayNetworking.canSend(ProfilePayloads.Request.ID) && ClientPlayNetworking.canSend(ProfilePayloads.Save.ID),
                        "server did not advertise Chronicle profile payload support");
                buildScript(client);
                scriptBuilt = true;
            }
            drive(client);
        } catch (Throwable error) {
            fail(client, error);
        }
    }

    private void drive(MinecraftClient client) throws Exception {
        int immediateBudget = 32;
        while (!complete && taskIndex < tasks.size() && immediateBudget-- > 0) {
            Task task = tasks.get(taskIndex);
            if (!taskStarted) {
                task.start.run();
                taskStarted = true;
                taskDeadline = System.nanoTime() + task.timeoutNanos;
                ChronicleCore.LOGGER.info("Fabric regression {}/{} step: {}", role, phase, task.name);
            }
            if (!task.done.test()) {
                if (System.nanoTime() > taskDeadline) throw new IllegalStateException("step timed out: " + task.name);
                return;
            }
            completedSteps.add(task.name);
            taskIndex++;
            taskStarted = false;
        }
        if (!complete && taskIndex == tasks.size()) finish(client, true, "completed " + completedSteps.size() + " strict regression steps");
    }

    private void buildScript(MinecraftClient client) {
        if (phase.equals("initial")) {
            if (role.equals("A")) initialA(client); else initialB(client);
        } else if (role.equals("A")) restartA(client); else restartB(client);
    }

    private void initialA(MinecraftClient client) {
        long[] pausedLootCycle = new long[1];
        waitForMarker("wait for B unauthorized denial", marker("B", "initial", "denied"));
        initialAuthoring(client);
        initialContentAuthoring(client);
        setupTypedLoot(client);
        authoringUi(client);
        configureAutomaticSpawns(client);
        configureM8Observations(client);
        actionbar("inactive actionbar", "Chronicle | Session 0: Inactive | World frozen");
        timeQuery(client, "capture frozen time", value -> firstTime = value);
        movement(client, "player moves while inactive and frozen");
        timeQuery(client, "capture frozen time after movement", value -> secondTime = value);
        check("inactive world time stays frozen", () -> require(dayDelta(firstTime, secondTime) <= 2,
                "inactive world time advanced: " + firstTime + " -> " + secondTime));

        mutation(client, "start session 1", "chronicle session start 1", "^Started Chronicle session 1\\.$", () -> assertCampaign("ACTIVE", 1));
        actionbar("active session 1 actionbar", "Chronicle | Session 1: Active");
        previewAndFireTimerObservation(client);
        delay("allow queued session-start loot batch", 1_300);
        check("first arrival moves exactly once with facing", () -> {
            require(Math.abs(client.player.getX() - 16.5) < .05 && Math.abs(client.player.getZ() - 18.5) < .05 && Math.abs(client.player.getYaw() - 90) < .05, "automatic first arrival did not use the bound oriented point");
            require(automaticArrival(FABRIC_A).get("status").getAsString().equals("MOVED"), "automatic first arrival lacks a MOVED receipt");
        });
        verifyTypedStart(client);
        exerciseContent(client);
        activeAuthoringUi(client);
        automaticDeathSpawns(client);
        Path authoring = serverRoot.resolve("world/chronicle/authoring.json");
        capture("authoring before ACTIVE rejection", authoring);
        command(client, "author mode rejected during ACTIVE", "chronicle author mode on", ".*Authoring edits require a non-active.*");
        command(client, "site edits rejected during ACTIVE", "chronicle author remove smoke_city", ".*Authoring edits require a non-active.*");
        unchanged("ACTIVE author rejection preserves registry", "authoring before ACTIVE rejection", authoring);
        timeQuery(client, "capture active time", value -> firstTime = value);
        delay("allow active world time to advance", 1_100);
        timeQuery(client, "capture active time after delay", value -> secondTime = value);
        check("active world time advances", () -> require(dayDelta(firstTime, secondTime) >= 10,
                "active world time did not advance enough: " + firstTime + " -> " + secondTime));

        rejectedCommand(client, "reject resume while active", "chronicle session resume", "^cannot resume while campaign session is active$");
        mutation(client, "set Fabric smoke flag", "chronicle flag set quiet_below:fabric_smoke true",
                "^Chronicle flag quiet_below:fabric_smoke = true\\.$",
                () -> require(campaign().getAsJsonObject("flags").get("quiet_below:fabric_smoke").getAsBoolean(), "Fabric smoke flag was not persisted"));
        command(client, "read Fabric smoke flag", "chronicle flag get quiet_below:fabric_smoke",
                "^Chronicle flag quiet_below:fabric_smoke = true\\.$");
        command(client, "return discovery group to the city after respawn regressions", "tp @s 17.5 -59 17.5 0 0", ".*Teleported.*");
        mutation(client, "satisfy M8 city cue prerequisite", "chronicle flag set quiet_below:m8_gate true",
                "^Chronicle flag quiet_below:m8_gate = true\\.$", () -> require(campaign().getAsJsonObject("flags").get("quiet_below:m8_gate").getAsBoolean(),
                        "M8 cue prerequisite was not persisted as true"));
        awaitObservationDelivery(client, "city presence waits for its configured flag prerequisite", "quiet_below:city_presence");

        readOnlyCommand(client, "event list is read-only", "chronicle event list", ".*quiet_below:readiness_check.*");
        readOnlyCommand(client, "event preview is read-only", "chronicle event preview quiet_below:readiness_check",
                ".*quiet_below:readiness_check.*Ready\\..*");
        mutation(client, "trigger readiness once", "chronicle event trigger quiet_below:readiness_check",
                "^Chronicle event quiet_below:readiness_check completed\\..*$", () -> {
                    JsonObject state = campaign();
                    require(state.getAsJsonObject("flags").get("quiet_below:readiness_checked").getAsBoolean(), "readiness flag was not persisted");
                    require(jsonArrayContains(state.getAsJsonArray("completedEvents"), "quiet_below:readiness_check"), "readiness completion was not persisted");
                });
        rejectedCommand(client, "reject duplicate once-only event", "chronicle event trigger quiet_below:readiness_check",
                "^Event has already been completed\\.$");

        mutation(client, "pause session 1", "chronicle session pause", "^Paused Chronicle session 1\\.$", () -> assertCampaign("PAUSED", 1));
        final long[] pausedPlayTicks = new long[1];
        check("capture shared active-play clock while paused", () -> pausedPlayTicks[0] = playRuntime().get("elapsedTicks").getAsLong());
        delay("paused shared active-play clock does not advance", 1_100);
        check("shared active-play clock remains frozen while paused", () -> require(playRuntime().get("elapsedTicks").getAsLong() == pausedPlayTicks[0],
                "shared active-play clock advanced while paused"));
        rearmTimerForResume(client);
        actionbar("paused session 1 actionbar", "Chronicle | Session 1: Paused | World frozen");
        testDeath(client, "paused death retains vanilla position", 8.5, 8.5);
        check("capture cycle before paused refill wait", () -> pausedLootCycle[0] = typedReceipt("test:timed").get("cycle").getAsLong());
        capture("paused loot clock", serverRoot.resolve("world/chronicle/chest-content.json"));
        command(client, "put sentinel in managed timed chest while paused", "item replace block 26 -60 16 container.0 with minecraft:stone 1", ".*Replaced.*");
        delay("pause lasts longer than configured refill interval", 3_500);
        unchanged("paused scheduler does not advance persisted clock", "paused loot clock", serverRoot.resolve("world/chronicle/chest-content.json"));
        command(client, "paused timed chest is not refilled", "data get block 26 -60 16 Items", ".*minecraft:stone.*");
        timeQuery(client, "capture paused time", value -> firstTime = value);
        delay("observe paused time freeze", 1_100);
        timeQuery(client, "capture paused time after delay", value -> secondTime = value);
        check("paused world time stays frozen", () -> require(dayDelta(firstTime, secondTime) <= 2,
                "paused world time advanced: " + firstTime + " -> " + secondTime));
        rejectedCommand(client, "reject pause while paused", "chronicle session pause", "^cannot pause while campaign session is paused$");
        rejectedCommand(client, "reject event while paused", "chronicle event trigger quiet_below:readiness_check", "^Campaign session is not active\\.$");
        command(client, "summon uniquely tagged frozen pig",
                "summon minecraft:pig ~ ~6 ~ {Tags:[\"chronicle_fabric_smoke\"]}", ".*summoned.*");
        pigYQuery(client, "capture frozen pig Y", value -> firstPigY = value);
        delay("observe tagged pig while paused", 750);
        pigYQuery(client, "capture frozen pig Y after delay", value -> secondPigY = value);
        check("tagged pig gravity is frozen while paused", () -> require(Math.abs(firstPigY - secondPigY) < 0.01,
                "tagged pig moved while paused: " + firstPigY + " -> " + secondPigY));
        mutation(client, "resume session 1", "chronicle session resume", "^Resumed Chronicle session 1\\.$", () -> assertCampaign("ACTIVE", 1));
        awaitObservationDelivery(client, "reset timer delivers once after resumed active play", "quiet_below:distant_stir");
        delay("allow tagged pig gravity while active", 900);
        pigYQuery(client, "capture active pig Y", value -> secondPigY = value);
        check("tagged pig falls while active", () -> require(secondPigY < firstPigY - 0.1,
                "tagged pig did not fall after resume: " + firstPigY + " -> " + secondPigY));
        delay("allow resumed active refill timer", 3_500);
        command(client, "timer replaces contents after resume", "data get block 26 -60 16 Items", "^(?!.*minecraft:stone).*minecraft:coal.*$");
        check("resumed timer advances its generation", () -> require(typedReceipt("test:timed").get("cycle").getAsLong() > pausedLootCycle[0], "periodic refill did not advance generation after resume"));
        command(client, "remove only uniquely tagged pig",
                "kill @e[type=minecraft:pig,tag=chronicle_fabric_smoke,limit=1]", ".*killed.*");
        mutation(client, "leave session 1 paused", "chronicle session pause", "^Paused Chronicle session 1\\.$", () -> assertCampaign("PAUSED", 1));

        profileRequest("request A default profile", FABRIC_A, view -> assertView(view, FABRIC_A, "ChronicleFabricA", "", "", 0, true, false));
        profileSave("save A profile revision 1", 0, "Fabric A", "they/them", "A public test card",
                view -> assertView(view, FABRIC_A, "Fabric A", "they/them", "A public test card", 1, true, true));
        delay("settle A profile persistence", 1_000);
        check("A profile persisted", () -> assertProfile(FABRIC_A, "Fabric A", "they/them", "A public test card", 1));
        markerTask("publish A profile saved", marker("A", "initial", "profile-saved"));
        waitForMarker("wait for B read-only view and own save", marker("B", "initial", "profile-complete"));

        capture("capture A before stale save", profileFile(FABRIC_A));
        profileNotice("reject stale A revision", new ProfilePayloads.Save(0, "Stale A", "", ""), "chronicle.profile.error.stale");
        delay("settle stale A rejection and write throttle", 1_050);
        unchanged("stale A save leaves JSON unchanged", "capture A before stale save", profileFile(FABRIC_A));
        capture("capture A before invalid save", profileFile(FABRIC_A));
        profileNotice("reject blank A name", new ProfilePayloads.Save(1, "   ", "they/them", "A public test card"), "chronicle.profile.error.invalid");
        delay("settle invalid A rejection", 250);
        unchanged("invalid A save leaves JSON unchanged", "capture A before invalid save", profileFile(FABRIC_A));
        check("initial durable state and audit", () -> {
            assertCampaign("PAUSED", 1);
            assertProfile(FABRIC_A, "Fabric A", "they/them", "A public test card", 1);
            assertProfile(FABRIC_B, "Fabric B", "", "", 1);
            assertAudit(INITIAL_AUDIT);
        });
        command(client, "save restart sentinel in session-only chest", "item replace block 24 -60 16 container.0 with minecraft:emerald 1", ".*Replaced.*");
        command(client, "leave away from arrival before restart", "tp @s 38.5 -60 18.5 0 0", ".*Teleported.*");
        markerTask("publish initial final marker", marker("A", "initial", "final"));
    }

    private void initialB(MinecraftClient client) {
        actionbar("inactive actionbar", "Chronicle | Session 0: Inactive | World frozen");
        capture("capture state before unauthorized start", campaignFile);
        command(client, "ordinary player start is denied", "chronicle session start 1", UNAUTHORIZED_REPLY, matcher -> { });
        delay("settle unauthorized start rejection", 250);
        unchanged("unauthorized start leaves campaign unchanged", "capture state before unauthorized start", campaignFile);
        command(client, "public status works for ordinary player", "chronicle status", "^Chronicle session status: inactive; number=0\\.$");
        unchanged("public status leaves campaign unchanged", "capture state before unauthorized start", campaignFile);
        Path authoring = serverRoot.resolve("world/chronicle/authoring.json");
        capture("authoring before unauthorized edits", authoring);
        for (String command : List.of("chronicle author mode on", "chronicle author select pos1 0 -60 0",
                "chronicle author region save forbidden", "chronicle author location save forbidden here",
                "chronicle author block bind forbidden target", "chronicle author remove smoke_city",
                "chronicle author show smoke_city", "chronicle author list",
                "chronicle author chest legacy capture forbidden", "chronicle chest status", "chronicle spawn list",
                "chronicle author chest", "chronicle author chest bind \"test:timed\"", "chronicle author chest reload",
                "chronicle spawn automatic arrival ChronicleFabricB \"chronicle_core:arrival\"")) {
            delay("respect vanilla non-op command rate: " + command, 1_100);
            command(client, "non-op denied: " + command, command, UNAUTHORIZED_REPLY, matcher -> { });
        }
        unchanged("non-op author commands preserve registry", "authoring before unauthorized edits", authoring);
        task("forged non-op UI request is denied", () -> ClientPlayNetworking.send(new AuthoringUiPayloads.Request(Action.OPEN, "", "", 0, Tab.CHESTS, 0)),
                () -> authoringNotices.stream().anyMatch(notice -> notice.contains("Operator permission")));
        final int[] deniedM8Notices = new int[1];
        task("forged non-op observation screen is denied", () -> {
            deniedM8Notices[0] = authoringNotices.size();
            ClientPlayNetworking.send(new AuthoringUiPayloads.Request(Action.OPEN, "", "", 0, Tab.OBSERVATIONS, 0));
        }, () -> authoringNotices.size() > deniedM8Notices[0]
                && authoringNotices.subList(deniedM8Notices[0], authoringNotices.size()).stream().anyMatch(notice -> notice.contains("Operator permission")));
        check("non-op receives no private authoring snapshot", () -> require(authoringViews == 0, "private authoring data sent to ordinary player"));
        markerTask("publish B unauthorized denial", marker("B", "initial", "denied"));
        waitForMarker("wait for explicit automatic policy for B", marker("A", "initial", "automatic-policy"));
        command(client, "ordinary player can leave own automatic policies", "chronicle leave", ".*You left participation and automatic arrival and respawn policies.*");
        markerTask("publish B left automatic policies", marker("B", "initial", "automatic-left"));
        command(client, "ordinary player opts into Chronicle participation", "chronicle join", ".*You joined Chronicle participation.*");
        markerTask("publish B participation opt-in", marker("B", "initial", "m8-joined"));
        waitForMarker("wait for operator positioning in the configured city region", marker("A", "initial", "m8-positioned"));
        markerTask("publish B participation and city presence", marker("B", "initial", "m8-ready"));

        waitForMarker("wait for A profile save", marker("A", "initial", "profile-saved"));
        check("ordinary player never receives the private authoring preview", () -> require(AuthoringPreviewRenderer.renderedFrames() == 0,
                "private authoring geometry was broadcast to the ordinary player"));
        profileRequest("B views A read-only profile", FABRIC_A,
                view -> assertView(view, FABRIC_A, "Fabric A", "they/them", "A public test card", 1, false, false));
        delay("respect profile read throttle", 250);
        profileRequest("B requests own default profile", FABRIC_B,
                view -> assertView(view, FABRIC_B, "ChronicleFabricB", "", "", 0, true, false));
        capture("capture A before B save", profileFile(FABRIC_A));
        profileSave("save B own profile revision 1", 0, "Fabric B", "", "",
                view -> assertView(view, FABRIC_B, "Fabric B", "", "", 1, true, true));
        capture("capture B before burst rejection", profileFile(FABRIC_B));
        profileNotice("reject burst B save", new ProfilePayloads.Save(1, "Burst B", "", ""), "chronicle.profile.error.throttled");
        delay("settle B save persistence", 1_000);
        unchanged("burst B save leaves JSON unchanged", "capture B before burst rejection", profileFile(FABRIC_B));
        unchanged("B save cannot modify viewed A profile", "capture A before B save", profileFile(FABRIC_A));
        check("B own profile persisted", () -> assertProfile(FABRIC_B, "Fabric B", "", "", 1));
        markerTask("publish B profile complete", marker("B", "initial", "profile-complete"));

        waitForMarker("wait for M8 observation configuration", marker("A", "initial", "m8-configured"));
        awaitObservationMessage("B receives private city cue", "The ancient stone carries a low vibration.");
        awaitObservationMessage("B receives private distant cue", "A low, unfamiliar rumble breaks the quiet.");
        waitForMarker("wait for A initial final marker", marker("A", "initial", "final"));
        check("B observes final initial durability", () -> {
            assertCampaign("PAUSED", 1);
            assertProfile(FABRIC_A, "Fabric A", "they/them", "A public test card", 1);
            assertProfile(FABRIC_B, "Fabric B", "", "", 1);
            assertAudit(INITIAL_AUDIT);
        });
    }

    private void restartA(MinecraftClient client) {
        check("authoring persists all three site types across restart", this::assertAuthoring);
        check("restart preserves uncertain observation claim without replay", () -> {
            JsonObject delivery = observationDelivery("quiet_below:city_presence");
            require(delivery.get("status").getAsString().equals("CLAIMED"), "stopped-fixture observation claim was lost");
        });
        actionbar("restart pending-fill status is visible", "Chronicle | Session 1: Paused | World frozen: A Chronicle chest fill is unresolved; use /chronicle chest status and recover it before play continues.");
        command(client, "restart retains uncertain fill intent", "chronicle chest status", ".*templates=1; bindings=4; receipts=3; pending=1.*");
        command(client, "recover persisted after without replay", "chronicle chest recover 00000000-0000-0000-0000-000000000007", ".*applied contents verified; durable receipt recorded.*");
        command(client, "recovered receipt survives uncertainty", "chronicle chest status", ".*templates=1; bindings=4; receipts=4; pending=0.*");
        command(client, "recovery preserves item components", "data get block 16 -60 16 Items", ".*Chronicle supply.*");
        command(client, "restart preserves session-only sentinel", "data get block 24 -60 16 Items", ".*minecraft:emerald.*");
        command(client, "spawn binding survives restart", "chronicle spawn inspect \"chronicle_core:arrival\"", ".*smoke_arrival.*safety=safe.*");
        command(client, "author mode is not restored on restart", "chronicle author remove smoke_city", ".*Enable author mode first.*");
        command(client, "persisted region can be shown", "chronicle author show smoke_city", ".*region smoke_city.*volume=36.*validation=valid.*");
        command(client, "persisted anchor validates", "chronicle author validate", ".*valid=3; invalid=0; unresolved=0.*");
        actionbar("restart paused actionbar", "Chronicle | Session 1: Paused | World frozen");
        check("A observes restart persistence", () -> {
            assertCampaignAndM1Persistence("PAUSED", 1);
            assertProfile(FABRIC_A, "Fabric A", "they/them", "A public test card", 1);
            assertProfile(FABRIC_B, "Fabric B", "", "", 1);
            assertAudit(INITIAL_AUDIT);
            require(automaticArrival(FABRIC_A).get("status").getAsString().equals("CLAIMED"), "uncertain stopped-fixture arrival claim was lost");
        });
        profileRequest("A fetches own persisted profile", FABRIC_A,
                view -> assertView(view, FABRIC_A, "Fabric A", "they/them", "A public test card", 1, true, false));
        capture("capture restart A profile", profileFile(FABRIC_A));
        capture("capture restart B profile", profileFile(FABRIC_B));
        waitForMarker("wait for B restart checks and denied resume", marker("B", "restart", "ready"));

        mutation(client, "restart resume session 1", "chronicle session resume", "^Resumed Chronicle session 1\\.$", () -> assertCampaign("ACTIVE", 1));
        actionbar("restart active actionbar", "Chronicle | Session 1: Active");
        delay("uncertain arrival is not retried after restart and resume", 1_200);
        check("restart does not pull departed player back", () -> {
            require(Math.abs(client.player.getX() - 38.5) < .1 && Math.abs(client.player.getZ() - 18.5) < .1, "claimed first arrival repeated after restart");
            require(automaticArrival(FABRIC_A).get("status").getAsString().equals("CLAIMED"), "uncertain claim was silently rewritten");
        });
        check("uncertain observation claim remains unresolved after resumed eligibility", () -> require(
                observationDelivery("quiet_below:city_presence").get("status").getAsString().equals("CLAIMED"),
                "uncertain observation was silently replayed or completed after restart"));
        delay("resume does not create another session-start cycle", 1_300);
        command(client, "resume preserves session-only chest contents", "data get block 24 -60 16 Items", ".*minecraft:emerald.*");
        mutation(client, "end session 1", "chronicle session end", "^Ended Chronicle session 1\\.$", () -> assertCampaign("ENDED", 1));
        mutation(client, "start default session 2", "chronicle session start", "^Started Chronicle session 2\\.$", () -> assertCampaign("ACTIVE", 2));
        delay("new session queues fresh typed loot", 1_300);
        command(client, "new session replaces old contents", "data get block 24 -60 16 Items", "^(?!.*minecraft:emerald)(?!.*minecraft:cobblestone).*Typed supply.*$");
        check("session two receipts record new session", () -> require(typedReceipt("quiet_below:settlement_supplies").get("sessionNumber").getAsInt() == 2, "new session did not refill typed chest"));
        mutation(client, "end session 2", "chronicle session end", "^Ended Chronicle session 2\\.$", () -> assertCampaign("ENDED", 2));
        actionbar("ended session 2 actionbar", "Chronicle | Session 2: Ended | World frozen");
        timeQuery(client, "capture ended time", value -> firstTime = value);
        delay("observe ended time freeze", 1_100);
        timeQuery(client, "capture ended time after delay", value -> secondTime = value);
        check("ended world time stays frozen", () -> require(dayDelta(firstTime, secondTime) <= 2,
                "ended world time advanced: " + firstTime + " -> " + secondTime));
        unchanged("A profile unchanged across restart campaign mutations", "capture restart A profile", profileFile(FABRIC_A));
        unchanged("B profile unchanged across restart campaign mutations", "capture restart B profile", profileFile(FABRIC_B));
        check("A verifies final durable state", () -> {
            assertCampaignAndM1Persistence("ENDED", 2);
            assertAudit(RESTART_AUDIT);
        });
        markerTask("publish restart final marker", marker("A", "restart", "final"));
    }

    private void restartB(MinecraftClient client) {
        actionbar("restart paused actionbar", "Chronicle | Session 1: Paused | World frozen");
        command(client, "restart public status", "chronicle status", "^Chronicle session status: paused; number=1\\.$");
        check("B observes restart persistence", () -> {
            assertCampaignAndM1Persistence("PAUSED", 1);
            assertProfile(FABRIC_A, "Fabric A", "they/them", "A public test card", 1);
            assertProfile(FABRIC_B, "Fabric B", "", "", 1);
            assertAudit(INITIAL_AUDIT);
        });
        profileRequest("B fetches A persisted read-only profile", FABRIC_A,
                view -> assertView(view, FABRIC_A, "Fabric A", "they/them", "A public test card", 1, false, false));
        capture("capture restart state before B resume", campaignFile);
        command(client, "ordinary player restart resume is denied", "chronicle session resume", UNAUTHORIZED_REPLY, matcher -> { });
        delay("settle unauthorized restart resume", 250);
        unchanged("unauthorized restart resume leaves campaign unchanged", "capture restart state before B resume", campaignFile);
        markerTask("publish B restart ready", marker("B", "restart", "ready"));

        waitForMarker("wait for A restart final marker", marker("A", "restart", "final"));
        check("B verifies final durable state", () -> {
            assertCampaignAndM1Persistence("ENDED", 2);
            assertProfile(FABRIC_A, "Fabric A", "they/them", "A public test card", 1);
            assertProfile(FABRIC_B, "Fabric B", "", "", 1);
            assertAudit(RESTART_AUDIT);
        });
    }

    private void initialAuthoring(MinecraftClient client) {
        Path authoring = serverRoot.resolve("world/chronicle/authoring.json");
        capture("campaign before metadata authoring", campaignFile);
        command(client, "edits require author mode", "chronicle author remove smoke_city", ".*Enable author mode first.*");
        command(client, "enable author mode", "chronicle author mode on", "^Author mode enabled.*");
        command(client, "select reversed first corner", "chronicle author select pos1 18 -58 18", "^Selected corner 1.*");
        command(client, "reject cross-dimension command context", "execute in minecraft:the_nether run chronicle author select pos2 0 80 0", ".*player's actual dimension.*");
        command(client, "select second corner", "chronicle author select pos2 16 -60 16", "^Selected corner 2.*");
        command(client, "save normalized region", "chronicle author region save smoke_city", ".*region smoke_city.*volume=27.*");
        capture("registry before duplicate region", authoring);
        command(client, "save cannot overwrite a site", "chronicle author region save smoke_city", ".*already exists.*");
        unchanged("duplicate save leaves registry unchanged", "registry before duplicate region", authoring);
        command(client, "change selected corner", "chronicle author select pos1 19 -58 18", "^Selected corner 1.*");
        command(client, "explicitly replace region", "chronicle author region replace smoke_city", ".*region smoke_city.*revision=2.*volume=36.*");
        command(client, "move to precise oriented test location", "tp @s 16.5 -60 18.5 90 0", ".*Teleported.*");
        command(client, "save exact point with facing", "chronicle author location save smoke_arrival here", ".*location smoke_arrival.*yaw=90.00; pitch=0.00.*");
        command(client, "place fixture chest", "setblock 16 -60 16 minecraft:chest", ".*Changed the block.*");
        command(client, "put seven diamonds in fixture chest", "item replace block 16 -60 16 container.0 with minecraft:diamond 7", ".*Replaced.*");
        command(client, "look down at fixture chest", "tp @s 16.5 -58 16.5 0 90", ".*Teleported.*");
        command(client, "bind targeted chest metadata", "chronicle author block bind smoke_chest target", ".*block smoke_chest.*expected=minecraft:chest.*");
        command(client, "binding preserves chest contents", "data get block 16 -60 16 Items", ".*minecraft:diamond.*");
        command(client, "validate bound chest type", "chronicle author show smoke_chest", ".*expected=minecraft:chest.*validation=valid.*");
        command(client, "replace fixture chest to check stale anchor", "setblock 16 -60 16 minecraft:stone", ".*Changed the block.*");
        command(client, "stale anchor reports mismatched block", "chronicle author show smoke_chest", ".*validation=invalid: expected minecraft:chest, found minecraft:stone.*");
        command(client, "restore fixture chest", "setblock 16 -60 16 minecraft:chest", ".*Changed the block.*");
        command(client, "save disposable named region", "chronicle author region save smoke_delete", ".*region smoke_delete.*");
        command(client, "remove disposable definition", "chronicle author remove smoke_delete", ".*Removed authoring site smoke_delete; revision=6.*");
        command(client, "list all shared types", "chronicle author list", ".*sites=3: smoke_arrival, smoke_chest, smoke_city.*");
        command(client, "validate all metadata", "chronicle author validate", ".*valid=3; invalid=0; unresolved=0.*");
        command(client, "open registered campaign role menu", "chronicle author menu", ".*registered roles=[1-9][0-9]*.*");
        check("menu includes Quiet Below's registered choices", () -> require(messages.stream().anyMatch(message -> message.text.contains("quiet_below:source_city"))
                && messages.stream().anyMatch(message -> message.text.contains("quiet_below:relic_heart")), "campaign roles absent from menu"));
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>> suggestions = new java.util.concurrent.atomic.AtomicReference<>();
        task("registered region roles appear as command choices", () -> {
            var handler = client.player.networkHandler;
            var dispatcher = handler.getCommandDispatcher();
            suggestions.set(dispatcher.getCompletionSuggestions(dispatcher.parse("chronicle author region save ", handler.getCommandSource())));
        }, () -> {
            if (!suggestions.get().isDone()) return false;
            var values = suggestions.get().join().getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList();
            require(values.contains("\"quiet_below:source_city\""), "registered region completion missing: " + values);
            require(!values.contains("\"quiet_below:ancient_forge\""), "block role offered for region save");
            return true;
        });
        command(client, "registered role rejects wrong geometry", "chronicle author location save \"quiet_below:source_city\" here", ".*Source city requires region geometry.*");
        command(client, "save selected campaign role without custom ID", "chronicle author region save \"quiet_below:source_city\"", ".*region quiet_below:source_city.*revision=7.*");
        command(client, "remove temporary campaign role binding", "chronicle author remove \"quiet_below:source_city\"", ".*revision=8.*");
        check("independently inspect durable authoring metadata", this::assertAuthoring);
        unchanged("metadata authoring preserves campaign progress", "campaign before metadata authoring", campaignFile);
        command(client, "view region outline from outside", "tp @s 22 -56 22 135 30", ".*Teleported.*");
        command(client, "show private outline", "chronicle author show smoke_city", ".*region smoke_city.*validation=valid.*");
        long[] outlineFrames = new long[1];
        check("capture outline frames before movement", () -> outlineFrames[0] = AuthoringPreviewRenderer.renderedFrames());
        movement(client, "move while selection outline remains visible");
        delay("outline remains beyond the old five-second timeout", 6_000);
        check("capture frames after the old timeout", () -> outlineFrames[0] = AuthoringPreviewRenderer.renderedFrames());
        delay("observe rendering after the old timeout", 350);
        check("persistent outline still renders after movement", () -> require(AuthoringPreviewRenderer.renderedFrames() > outlineFrames[0],
                "outline expired while author was moving"));
        check("hide HUD for outline inspection", () -> client.options.hudHidden = true);
        delay("allow particle outline to render", 300);
        check("private outline renders while world frozen", () -> require(AuthoringPreviewRenderer.renderedFrames() > 0,
                "authoring outline skipped frozen world rendering"));
        check("capture authoring outline", () -> ScreenshotRecorder.saveScreenshot(output.toFile(), "m6-outline.png", client.getFramebuffer(), 1, ignored -> { }));
        delay("allow outline screenshot to complete", 300);
        check("restore HUD after outline inspection", () -> client.options.hudHidden = false);
        command(client, "hide persistent preview explicitly", "chronicle author preview hide", "^Authoring preview hidden.*");
        check("capture frames after hiding", () -> outlineFrames[0] = AuthoringPreviewRenderer.renderedFrames());
        delay("observe hidden preview", 350);
        check("explicitly hidden preview stops rendering", () -> require(AuthoringPreviewRenderer.renderedFrames() == outlineFrames[0], "hidden preview still rendered"));
        task("disconnect and reconnect same author", 45_000_000_000L, () -> {
            client.disconnect(new TitleScreen(), false);
            connecting = false;
        }, () -> client.player != null && client.getOverlay() == null && client.currentScreen == null);
        command(client, "disconnect clears author mode", "chronicle author remove smoke_city", ".*Enable author mode first.*");
        command(client, "enable fresh author mode after reconnect", "chronicle author mode on", "^Author mode enabled.*");
        command(client, "disconnect clears selection", "chronicle author region save stale_selection", ".*Select both corners first.*");
        check("reconnect preserves only saved metadata", this::assertAuthoring);
        // Leave author mode on: starting play must clear it, not let later pause resurrect it.
    }

    private void authoringUi(MinecraftClient client) {
        command(client, "disable author mode to test GUI guidance", "chronicle author mode off", ".*Author mode disabled.*");
        openUi(client, "open proper authoring screen", Tab.CHESTS);
        check("GUI explains missing author mode", () -> require(!ui(client).state().author() && ui(client).state().editable(), "UI mode state incorrect"));
        check("choose listed type before observing stable polling", () -> ui(client).chooseTypeForTest("test:empty"));
        stableAuthoringPolling(client);
        check("choose all-miss chest in proper UI", () -> {
            ui(client).chooseTypeForTest("test:empty");
            ui(client).selectForTest(ui(client).state().entries().stream().filter(entry -> entry.reference().equals("test:empty")).findFirst().orElseThrow().id());
            require(ui(client).triggerForTest("Unbind selected"), "unbind not enabled");
        });
        uiPress(client, "Confirm: Unbind selected", state -> state.entries().size() == 2 && state.success());
        uiPress(client, "Bind looked container", state -> state.entries().size() == 3 && state.success());
        check("clear offline auth toast for UI inspection", () -> client.getToastManager().clear());
        delay("settle unobstructed UI header", 250);
        check("capture small authoring UI", () -> ScreenshotRecorder.saveScreenshot(output.toFile(), "cf09-chests-small.png", client.getFramebuffer(), 1, ignored -> { }));
        delay("settle small UI capture", 250);
        // Change the looked-at fixture after receiving the snapshot, then send its old token.
        check("close UI retaining stale target token", () -> client.setScreen(null));
        final long[] token = new long[1];
        openUi(client, "capture view before stale target test", Tab.CHESTS);
        check("remember target snapshot token", () -> { token[0] = ui(client).state().token(); client.setScreen(null); });
        command(client, "place unbound target for stale test", "setblock 30 -60 16 minecraft:chest", ".*Changed the block.*");
        command(client, "change actual target after snapshot", "tp @s 30.5 -58 16.5 0 90", ".*Teleported.*");
        check("clear stale snapshot response marker", () -> lastAuthoringState = null);
        task("stale target request is rejected", () -> ClientPlayNetworking.send(new AuthoringUiPayloads.Request(Action.CHEST_BIND, "test:timed", "", token[0], Tab.CHESTS, 0)),
                () -> lastAuthoringState != null && !lastAuthoringState.success() && lastAuthoringState.message().contains("configuration changed"));
        check("late mutation response does not reopen closed screen", () -> require(client.currentScreen == null, "late response reopened the GUI"));
        openUi(client, "refresh after stale target rejection", Tab.ROLES);
        uiPress(client, "Corner 1", state -> !state.selection().contains("first null"));
        uiPress(client, "Corner 2", state -> !state.selection().contains("second null"));
        check("pick registered region role in UI", () -> ui(client).selectForTest("quiet_below:source_city"));
        uiPress(client, "Save role", state -> state.success() && state.entries().stream().anyMatch(entry -> entry.id().equals("quiet_below:source_city") && entry.revision() > 0));
        uiPress(client, "Places", state -> state.tab() == Tab.PLACES);
        check("select newly saved region in UI", () -> { ui(client).selectForTest("quiet_below:source_city"); require(ui(client).triggerForTest("Remove"), "remove confirmation unavailable"); });
        uiPress(client, "Confirm: Remove", state -> state.success() && state.entries().stream().noneMatch(entry -> entry.id().equals("quiet_below:source_city")));
        uiPress(client, "Chests", state -> state.tab() == Tab.CHESTS);
        check("resize GUI for large-screen inspection", () -> client.getWindow().setWindowedSize(1024, 768));
        delay("settle large authoring UI", 500);
        check("capture large authoring UI", () -> ScreenshotRecorder.saveScreenshot(output.toFile(), "cf09-chests-large.png", client.getFramebuffer(), 1, ignored -> { }));
        delay("settle large UI capture", 250);
        check("restore small fixture window", () -> { client.setScreen(null); client.getWindow().setWindowedSize(640, 480); });
        check("UI did not mutate world geometry or unrelated sites", this::assertAuthoring);
    }

    /**
     * The background refresh is intentionally a live read, rather than a modal request.
     * Prove two equal snapshots retain the same focused, active widget, then prove a later
     * changed server snapshot still rebuilds controls to reflect author mode.
     */
    private void stableAuthoringPolling(MinecraftClient client) {
        final int[] stableIdentity = new int[1];
        final int[] firstViews = new int[1];
        final boolean[] firstPending = new boolean[1];
        check("focus actual loot picker before polling", () -> ui(client).focusButtonForTest("Loot >"));
        task("first authoring background refresh keeps focused widget", () -> {
            AuthoringScreen.ButtonProbe probe = ui(client).probeButtonForTest("Loot >");
            require(probe.active() && probe.focused(), "baseline loot picker is not interactive and focused");
            stableIdentity[0] = probe.identity();
            firstViews[0] = authoringViews;
        }, () -> {
            AuthoringScreen screen = ui(client);
            AuthoringScreen.ButtonProbe probe = screen.probeButtonForTest("Loot >");
            require(probe.identity() == stableIdentity[0] && probe.active() && probe.focused(), "background refresh rebuilt or disabled the loot picker");
            firstPending[0] |= screen.pending();
            return firstPending[0] && authoringViews > firstViews[0] && !screen.pending();
        });

        final int[] secondViews = new int[1];
        final boolean[] secondPending = new boolean[1];
        task("second authoring background refresh keeps same widget", () -> secondViews[0] = authoringViews, () -> {
            AuthoringScreen screen = ui(client);
            AuthoringScreen.ButtonProbe probe = screen.probeButtonForTest("Loot >");
            require(probe.identity() == stableIdentity[0] && probe.active() && probe.focused(), "second background refresh rebuilt or disabled the loot picker");
            secondPending[0] |= screen.pending();
            return secondPending[0] && authoringViews > secondViews[0] && !screen.pending();
        });

        command(client, "change author state while UI is open", "chronicle author mode on", ".*Author mode enabled.*");
        final int[] changedViews = new int[1];
        task("changed authoring snapshot rebuilds stale controls", () -> changedViews[0] = authoringViews, () -> {
            AuthoringScreen screen = ui(client);
            if (authoringViews <= changedViews[0] || screen.pending()) return false;
            AuthoringScreen.ButtonProbe bind = screen.probeButtonForTest("Bind looked container");
            require(screen.state().author() && bind.active(), "changed author mode did not update stale UI controls");
            return true;
        });
    }

    private void activeAuthoringUi(MinecraftClient client) {
        openUi(client, "open UI during active play", Tab.SPAWNS);
        check("active GUI disables author entry", () -> require(!ui(client).triggerForTest("Mode on"), "GUI enabled edits during ACTIVE"));
        final long[] token = new long[1];
        check("capture active view for forged edit", () -> { token[0] = ui(client).state().token(); lastAuthoringState = null; });
        task("server rejects active edit even with valid GUI token", () -> ClientPlayNetworking.send(new AuthoringUiPayloads.Request(Action.MODE_ON, "", "", token[0], Tab.SPAWNS, 0)),
                () -> lastAuthoringState != null && !lastAuthoringState.success() && !lastAuthoringState.author() && lastAuthoringState.message().contains("non-active"));
        check("select saved spawn purpose", () -> ui(client).selectForTest("chronicle_core:arrival"));
        uiPress(client, "Choose player", state -> state.tab() == Tab.SPAWN_PLAYERS);
        check("pick exact named player", () -> ui(client).selectForTest(FABRIC_A.toString()));
        uiPress(client, "Use selected player", state -> state.tab() == Tab.SPAWNS);
        uiPress(client, "Preview player", state -> state.success() && state.previewPlayer().equals(FABRIC_A.toString()));
        check("confirm move is explicit", () -> require(ui(client).triggerForTest("Confirm move"), "confirm move not enabled"));
        uiPress(client, "Confirm: Confirm move", state -> state.success() && state.previewPlayer().isBlank());
        check("close active authoring UI", () -> client.setScreen(null));
    }

    private void configureAutomaticSpawns(MinecraftClient client) {
        openUi(client, "open automatic player policies", Tab.POLICIES);
        uiPress(client, "Purpose: choose a saved binding", state -> state.tab() == Tab.POLICY_PURPOSES);
        check("select actual saved arrival purpose", () -> ui(client).selectForTest("chronicle_core:arrival"));
        uiPress(client, "Use selected purpose", state -> state.tab() == Tab.POLICIES);
        check("select exact policy account", () -> { ui(client).selectForTest(FABRIC_A.toString()); require(ui(client).triggerForTest("First arrival once"), "arrival confirmation unavailable"); });
        uiPress(client, "Confirm: First arrival once", state -> state.success() && state.entries().stream().anyMatch(entry -> entry.id().equals(FABRIC_A.toString()) && entry.detail().contains("Arrival: chronicle_core:arrival")));
        check("confirm separate death policy", () -> require(ui(client).triggerForTest("Death respawn"), "death policy confirmation unavailable"));
        uiPress(client, "Confirm: Death respawn", state -> state.success() && state.entries().stream().anyMatch(entry -> entry.id().equals(FABRIC_A.toString()) && entry.detail().contains("death respawn: chronicle_core:arrival")));
        check("capture automatic policy screen", () -> ScreenshotRecorder.saveScreenshot(output.toFile(), "m7-automatic-policies.png", client.getFramebuffer(), 1, ignored -> { }));
        delay("settle policy screen capture", 250);
        check("configuring policies does not teleport players", () -> { require(Math.abs(client.player.getX() - 30.5) < .05, "saving policy moved the player"); client.setScreen(null); });
        command(client, "configure ordinary player policy for leave test", "chronicle spawn automatic arrival ChronicleFabricB \"chronicle_core:arrival\"", ".*Automatic first arrival enabled.*No player was moved.*");
        markerTask("publish B automatic policy configured", marker("A", "initial", "automatic-policy"));
        waitForMarker("wait for B voluntary departure", marker("B", "initial", "automatic-left"));
        check("ordinary leave removes policy without consuming arrival", () -> {
            var root = automaticState();
            require(root.getAsJsonArray("policies").size() == 1 && root.getAsJsonArray("arrivals").isEmpty(), "leave removed history or failed to remove B policy");
        });
    }

    /**
     * M8 deliberately has no implicit player membership or cue bindings.  Build both
     * examples through the operator screen, after B has independently opted in and
     * positioned itself in the saved test region.  The city cue proves ENTRY uses the
     * selected region audience; the second cue proves a timer uses shared active time.
     */
    private void configureM8Observations(MinecraftClient client) {
        waitForMarker("wait for B participation opt-in", marker("B", "initial", "m8-joined"));
        command(client, "author enters configured city region before arming entry cue", "tp @s 17.5 -59 17.5 0 0", ".*Teleported.*");
        command(client, "operator places participating B in configured city region", "tp ChronicleFabricB 17.5 -59 17.5 0 0", ".*Teleported.*");
        markerTask("publish M8 participant positioning", marker("A", "initial", "m8-positioned"));
        waitForMarker("wait for B participation and city presence", marker("B", "initial", "m8-ready"));
        openUi(client, "open Session screen for M8 authoring", Tab.SESSION);
        uiPress(client, "Participants", state -> state.tab() == Tab.PARTICIPANTS);
        check("M8 starts with no implicit author membership", () -> require(ui(client).state().entries().stream()
                .noneMatch(entry -> entry.id().equals(FABRIC_A.toString()) && entry.reference().equals("MEMBER")),
                "author was enrolled without opting in"));
        check("select author participation entry", () -> {
            ui(client).selectForTest(FABRIC_A.toString());
            require(ui(client).triggerForTest("Enroll"), "participant enrollment confirmation unavailable for author");
        });
        uiPress(client, "Confirm: Enroll", state -> state.tab() == Tab.PARTICIPANTS && state.success()
                && state.entries().stream().anyMatch(entry -> entry.id().equals(FABRIC_A.toString()) && entry.reference().equals("MEMBER")));
        check("self-service B membership appears in the operator screen", () -> {
            ui(client).selectForTest(FABRIC_B.toString());
            require(ui(client).triggerForTest("Exclude"), "self-service B membership was not visible to the operator");
        });
        uiPress(client, "Confirm: Exclude", state -> state.tab() == Tab.PARTICIPANTS && state.success()
                && state.entries().stream().anyMatch(entry -> entry.id().equals(FABRIC_B.toString()) && entry.reference().isBlank()));
        check("select excluded B for explicit operator re-enrollment", () -> {
            ui(client).selectForTest(FABRIC_B.toString());
            require(ui(client).triggerForTest("Enroll"), "participant enrollment confirmation unavailable for B");
        });
        uiPress(client, "Confirm: Enroll", state -> state.tab() == Tab.PARTICIPANTS && state.success()
                && state.entries().stream().anyMatch(entry -> entry.id().equals(FABRIC_B.toString()) && entry.reference().equals("MEMBER")));
        uiPress(client, "Back to Session", state -> state.tab() == Tab.SESSION);
        uiPress(client, "Observations", state -> state.tab() == Tab.OBSERVATIONS);

        check("select registered region-audience observation", () -> ui(client).selectForTest("quiet_below:city_presence"));
        uiPress(client, "Region: choose", state -> state.tab() == Tab.OBS_REGIONS);
        check("select saved city region for entry cue", () -> ui(client).selectForTest("smoke_city"));
        uiPress(client, "Use selected region", state -> state.tab() == Tab.OBSERVATIONS);
        uiPress(client, "Flag: none", state -> state.tab() == Tab.OBS_FLAGS);
        check("select false city-cue prerequisite", () -> ui(client).selectForTest("quiet_below:m8_gate"));
        uiPress(client, "Use selected flag", state -> state.tab() == Tab.OBSERVATIONS);
        uiPress(client, "Entry", state -> state.success() && state.entries().stream()
                .anyMatch(entry -> entry.id().equals("quiet_below:city_presence") && entry.revision() > 0));

        check("select registered participant-audience observation", () -> ui(client).selectForTest("quiet_below:distant_stir"));
        check("set long active-time timer for manual-preview smoke", () -> ui(client).setObservationDelayForTest("3600"));
        uiPress(client, "Timer", state -> state.success() && state.entries().stream()
                .anyMatch(entry -> entry.id().equals("quiet_below:distant_stir") && entry.revision() > 0));
        check("capture M8 participant and observation screen", () -> ScreenshotRecorder.saveScreenshot(output.toFile(), "m8-observations.png", client.getFramebuffer(), 1, ignored -> { }));
        delay("settle M8 screen capture", 250);
        check("M8 bindings and membership persist independently", () -> {
            JsonObject state = playRuntime();
            require(state.getAsJsonArray("participants").size() == 2, "expected exactly two explicit smoke participants");
            require(jsonArrayContains(state.getAsJsonArray("participants"), FABRIC_A.toString())
                    && jsonArrayContains(state.getAsJsonArray("participants"), FABRIC_B.toString()), "M8 participant UUIDs were not persisted");
            require(state.getAsJsonArray("bindings").size() == 2, "expected two explicitly configured observations");
            require(state.getAsJsonArray("deliveries").isEmpty(), "observation delivered before ACTIVE play");
        });
        check("close M8 authoring UI before ACTIVE", () -> client.setScreen(null));
        markerTask("publish M8 observation configuration", marker("A", "initial", "m8-configured"));
    }

    private void previewAndFireTimerObservation(MinecraftClient client) {
        capture("M8 timer state before private preview", playRuntimeFile);
        openUi(client, "open active M8 observation screen", Tab.OBSERVATIONS);
        check("select configured timer observation", () -> ui(client).selectForTest("quiet_below:distant_stir"));
        uiPress(client, "Preview", state -> state.success() && state.tab() == Tab.OBSERVATIONS && state.message().contains("Recipients="));
        unchanged("M8 preview is read-only", "M8 timer state before private preview", playRuntimeFile);
        final long[] previewToken = new long[1];
        check("capture private preview token before audience change", () -> previewToken[0] = ui(client).state().token());
        command(client, "make B temporarily ineligible for preview audience", "gamemode spectator ChronicleFabricB", ".*");
        capture("M8 state before stale audience fire", playRuntimeFile);
        check("clear stale audience fire response", () -> lastAuthoringState = null);
        task("server rejects preview fire after audience changes", () -> ClientPlayNetworking.send(new AuthoringUiPayloads.Request(
                Action.OBS_FIRE, "quiet_below:distant_stir", "", previewToken[0], Tab.OBSERVATIONS, 0)),
                () -> lastAuthoringState != null && !lastAuthoringState.success()
                        && (lastAuthoringState.message().toLowerCase(java.util.Locale.ROOT).contains("audience")
                        || lastAuthoringState.message().toLowerCase(java.util.Locale.ROOT).contains("configuration changed")));
        unchanged("audience-change rejection creates no delivery", "M8 state before stale audience fire", playRuntimeFile);
        command(client, "restore B after preview audience rejection", "gamemode creative ChronicleFabricB", ".*");
        uiPress(client, "Refresh", state -> state.success() && state.tab() == Tab.OBSERVATIONS);
        check("choose timer again after rejected preview", () -> ui(client).selectForTest("quiet_below:distant_stir"));
        uiPress(client, "Preview", state -> state.success() && state.tab() == Tab.OBSERVATIONS && state.message().contains("Recipients="));
        check("manual cue fire requires an explicit confirmation", () -> require(ui(client).triggerForTest("Fire"), "manual Fire control unavailable after preview"));
        uiPress(client, "Confirm: Fire", state -> state.success() && state.entries().stream().anyMatch(entry -> entry.id().equals("quiet_below:distant_stir")
                && entry.reference().equals("DELIVERED")));
        check("manual timer override records one private delivery", () -> require(observationDelivery("quiet_below:distant_stir").get("source").getAsString().equals("MANUAL"),
                "manual observation fire did not persist its source"));
        stableClockPolling(client);
        check("close active M8 preview", () -> client.setScreen(null));
        verifyEmptyParticipationClock(client);
    }

    private void verifyEmptyParticipationClock(MinecraftClient client) {
        command(client, "make B temporarily spectator for empty-play clock", "gamemode spectator ChronicleFabricB", ".*");
        command(client, "make author temporarily spectator for empty-play clock", "gamemode spectator @s", ".*");
        final long[] emptyTicks = new long[1];
        check("capture shared clock with no eligible participants", () -> emptyTicks[0] = playRuntime().get("elapsedTicks").getAsLong());
        delay("empty participation does not advance shared clock", 1_100);
        check("shared clock freezes with only spectators", () -> require(playRuntime().get("elapsedTicks").getAsLong() == emptyTicks[0],
                "shared clock advanced while every participant was a spectator"));
        command(client, "restore author after empty-play clock", "gamemode creative @s", ".*");
        command(client, "restore B after empty-play clock", "gamemode creative ChronicleFabricB", ".*");
    }

    /** The active-play header changes every poll; its clock must not recreate focused controls. */
    private void stableClockPolling(MinecraftClient client) {
        final int[] identity = new int[1];
        final int[] firstViews = new int[1];
        final boolean[] pending = new boolean[1];
        check("focus observation region picker before active clock polling", () -> ui(client).focusButtonForTest("Region: choose"));
        task("first active-play clock refresh keeps focused observation control", () -> {
            AuthoringScreen.ButtonProbe probe = ui(client).probeButtonForTest("Region: choose");
            require(probe.active() && probe.focused(), "baseline observation picker is not focused and active");
            identity[0] = probe.identity(); firstViews[0] = authoringViews;
        }, () -> {
            AuthoringScreen.ButtonProbe probe = ui(client).probeButtonForTest("Region: choose");
            require(probe.identity() == identity[0] && probe.active() && probe.focused(), "active clock refresh rebuilt the observation picker");
            pending[0] |= ui(client).pending();
            return pending[0] && authoringViews > firstViews[0] && !ui(client).pending();
        });
        final int[] secondViews = new int[1];
        task("second active-play clock refresh keeps focused observation control", () -> secondViews[0] = authoringViews, () -> {
            AuthoringScreen.ButtonProbe probe = ui(client).probeButtonForTest("Region: choose");
            require(probe.identity() == identity[0] && probe.active() && probe.focused(), "second active clock refresh rebuilt the observation picker");
            return authoringViews > secondViews[0] && !ui(client).pending();
        });
    }

    private void rearmTimerForResume(MinecraftClient client) {
        command(client, "enable author mode to reset delivered M8 timer", "chronicle author mode on", ".*Author mode enabled.*");
        openUi(client, "open paused M8 observation screen", Tab.OBSERVATIONS);
        check("select manually delivered timer for reset", () -> ui(client).selectForTest("quiet_below:distant_stir"));
        check("resetting a completed cue requires confirmation", () -> require(ui(client).triggerForTest("Reset"), "M8 Reset confirmation unavailable"));
        uiPress(client, "Confirm: Reset", state -> state.success() && state.entries().stream().anyMatch(entry -> entry.id().equals("quiet_below:distant_stir")
                && entry.reference().isBlank()));
        check("set one-second timer for resumed automatic delivery", () -> ui(client).setObservationDelayForTest("1"));
        check("replacing the timer requires confirmation", () -> require(ui(client).triggerForTest("Timer"), "M8 Timer replacement confirmation unavailable"));
        uiPress(client, "Confirm: Timer", state -> state.success() && state.entries().stream().anyMatch(entry -> entry.id().equals("quiet_below:distant_stir")
                && entry.revision() > 0 && entry.reference().isBlank()));
        check("close paused M8 screen", () -> client.setScreen(null));
        command(client, "disable author mode after M8 timer reset", "chronicle author mode off", ".*Author mode disabled.*");
    }

    private void awaitObservationDelivery(MinecraftClient client, String name, String id) {
        task(name, 30_000_000_000L, () -> { }, () -> {
            JsonObject delivery = observationDelivery(id);
            require(delivery.get("status").getAsString().equals("DELIVERED"), "observation was not durably completed: " + id);
            JsonArray recipients = delivery.getAsJsonArray("recipients");
            require(recipients.size() == 2 && jsonArrayContains(recipients, FABRIC_A.toString()) && jsonArrayContains(recipients, FABRIC_B.toString()),
                    "observation audience was not both enrolled participants: " + id);
            if (id.equals("quiet_below:city_presence")) {
                require(messages.stream().anyMatch(message -> !message.overlay
                                && message.text.equals("The ancient stone carries a low vibration.")),
                        "A did not receive the private city cue");
            }
            if (id.equals("quiet_below:distant_stir")) {
                require(messages.stream().anyMatch(message -> !message.overlay
                                && message.text.equals("A low, unfamiliar rumble breaks the quiet.")),
                        "A did not receive the private distant cue");
            }
            return true;
        });
    }

    private void awaitObservationMessage(String name, String expected) {
        task(name, 30_000_000_000L, () -> { }, () -> messages.stream().anyMatch(message -> !message.overlay && message.text.equals(expected)));
    }

    private void automaticDeathSpawns(MinecraftClient client) {
        command(client, "set distinct vanilla respawn", "spawnpoint @s 8 -60 8", ".*Set spawn point.*");
        final String[] vanilla = new String[1];
        command(client, "capture vanilla respawn metadata", "data get entity @s respawn", Pattern.compile("^(.*following entity data:.*)$"), matcher -> vanilla[0] = matcher.group(1));
        command(client, "automatic policy edits rejected during ACTIVE", "chronicle spawn automatic arrival_off ChronicleFabricA", ".*non-active author mode.*");
        testDeath(client, "active death follows configured policy", 16.5, 18.5);
        command(client, "automatic death preserves vanilla respawn metadata", "data get entity @s respawn", Pattern.compile("^(.*following entity data:.*)$"), matcher -> require(vanilla[0].equals(matcher.group(1)), "automatic respawn changed vanilla spawn settings"));
        command(client, "obstruct automatic respawn destination", "setblock 16 -60 18 minecraft:stone", ".*Changed the block.*");
        testDeath(client, "unsafe policy retains vanilla respawn", 8.5, 8.5);
        command(client, "restore automatic destination after fallback", "setblock 16 -60 18 minecraft:air", ".*Changed the block.*");
        command(client, "fallback preserves vanilla respawn metadata", "data get entity @s respawn", Pattern.compile("^(.*following entity data:.*)$"), matcher -> require(vanilla[0].equals(matcher.group(1)), "fallback changed vanilla spawn settings"));
        check("death respawn does not rearm first arrival", () -> require(automaticArrival(FABRIC_A).get("status").getAsString().equals("MOVED"), "death rewrote the arrival receipt"));
    }

    private void testDeath(MinecraftClient client, String name, double expectedX, double expectedZ) {
        command(client, name + " move away before death", "tp @s 40.5 -60 16.5 0 0", ".*Teleported.*");
        check(name + " arm real death", () -> expectTestDeath = true);
        command(client, name + " kill test account", "kill @s", ".*Killed.*");
        task(name + " completes through real respawn", () -> { }, () -> client.player != null && client.player.isAlive()
                && Math.abs(client.player.getX() - expectedX) < .1 && Math.abs(client.player.getZ() - expectedZ) < .1);
        check(name + " disarm test-only respawn", () -> expectTestDeath = false);
    }
    private JsonObject automaticState() throws IOException { return JsonParser.parseString(Files.readString(serverRoot.resolve("world/chronicle/automatic-spawns.json"))).getAsJsonObject(); }
    private JsonObject automaticArrival(UUID player) throws IOException {
        for (var element : automaticState().getAsJsonArray("arrivals")) if (element.getAsJsonObject().get("player").getAsString().equals(player.toString())) return element.getAsJsonObject();
        throw new IllegalStateException("Missing automatic arrival receipt for " + player);
    }
    private JsonObject playRuntime() throws IOException {
        require(Files.isRegularFile(playRuntimeFile), "M8 play runtime state file is missing: " + playRuntimeFile);
        JsonElement parsed = JsonParser.parseString(Files.readString(playRuntimeFile, StandardCharsets.UTF_8));
        require(parsed.isJsonObject(), "M8 play runtime state root is not an object");
        return parsed.getAsJsonObject();
    }
    private JsonObject observationDelivery(String id) throws IOException {
        for (JsonElement candidate : playRuntime().getAsJsonArray("deliveries")) {
            JsonObject delivery = candidate.getAsJsonObject();
            if (delivery.get("id").getAsString().equals(id)) return delivery;
        }
        throw new IllegalStateException("Missing M8 observation delivery: " + id);
    }

    private static AuthoringScreen ui(MinecraftClient client) { return (AuthoringScreen) client.currentScreen; }
    private void openUi(MinecraftClient client, String name, Tab tab) {
        task(name, () -> ClientPlayNetworking.send(new AuthoringUiPayloads.Request(Action.OPEN, "", "", 0, tab, 0)),
                () -> client.currentScreen instanceof AuthoringScreen screen && !screen.pending() && screen.state().tab() == tab);
    }
    private void uiPress(MinecraftClient client, String label, java.util.function.Predicate<Snapshot> accepted) {
        task("GUI button: " + label, () -> require(ui(client).triggerForTest(label), "GUI button unavailable: " + label),
                () -> client.currentScreen instanceof AuthoringScreen screen && !screen.pending() && accepted.test(screen.state()));
        delay("settle GUI " + label, 100);
    }

    private void setupTypedLoot(MinecraftClient client) {
        command(client, "open campaign loot type picker", "chronicle author chest types", ".*Loot types=5.*");
        check("menu shows server override and campaign recommendations", () -> require(messages.stream().anyMatch(message -> message.text.contains("Fixture supplies") && message.text.contains("recommended=2")), "loot type override/count missing from picker"));
        String[] types = {"quiet_below:settlement_supplies", "test:timed", "test:empty"};
        for (int i = 0; i < types.length; i++) {
            int x = 24 + i * 2;
            command(client, "place typed fixture " + x, "setblock " + x + " -60 16 minecraft:chest", ".*Changed the block.*");
            command(client, "seed replaceable contents " + x, "item replace block " + x + " -60 16 container.0 with minecraft:cobblestone 1", ".*Replaced.*");
            command(client, "look at typed fixture " + x, "tp @s " + (x + .5) + " -58 16.5 0 90", ".*Teleported.*");
            command(client, "one step target and type " + types[i], "chronicle author chest bind \"" + types[i] + "\"", ".*Bound loot chest.*to " + types[i] + ".*binding changed no items.*");
            command(client, "binding preserves existing items " + x, "data get block " + x + " -60 16 Items", ".*minecraft:cobblestone.*");
            command(client, "inspect saved loot type " + x, "chronicle author chest inspect", ".*type=" + types[i] + ".*");
        }
        command(client, "duplicate target bind is explicit", "chronicle author chest bind \"test:empty\"", ".*already exists.*");
        command(client, "loot counts are advisory", "chronicle author chest validate", ".*type shortages=[1-9].*Recommendations are advisory.*");
        Path config = serverRoot.resolve("config/chronicle/chest-loot.json");
        String[] original = new String[1];
        check("prepare invalid server loot override", () -> {
            original[0] = Files.readString(config);
            Files.writeString(config, original[0].replace("minecraft:bread", "minecraft:not_an_item"));
        });
        command(client, "registry validation rejects bad reload", "chronicle author chest reload", ".*Unknown loot item.*minecraft:not_an_item.*");
        check("restore valid fixture config", () -> Files.writeString(config, original[0]));
        command(client, "reload whole valid candidate", "chronicle author chest reload", ".*Reloaded 5 loot types.*not reset.*");
        check("direct type binding does not add named map anchors", this::assertAuthoring);
    }

    private void verifyTypedStart(MinecraftClient client) {
        command(client, "start fill uses configured item components", "data get block 24 -60 16 Items", "^(?!.*minecraft:emerald)(?!.*minecraft:cobblestone).*Typed supply.*$");
        command(client, "zero percent entries produce a valid empty chest", "data get block 28 -60 16 Items", ".*\\[\\].*");
        command(client, "typed edits refused while active", "chronicle author chest bind \"test:timed\"", ".*require non-active author mode.*");
        check("session-start type receipts and timer configuration persisted", () -> {
            require(typedReceipt("quiet_below:settlement_supplies").get("cycle").getAsLong() == 0, "session-only chest cycle mismatch");
            require(typedReceipt("test:empty").get("cycle").getAsLong() == 0, "empty rolled result was not consumed");
        });
    }

    private JsonObject typedReceipt(String type) throws IOException {
        JsonObject state = JsonParser.parseString(Files.readString(serverRoot.resolve("world/chronicle/chest-content.json"))).getAsJsonObject();
        String id = null;
        for (JsonElement value : state.getAsJsonArray("bindings")) {
            JsonObject binding = value.getAsJsonObject();
            if (binding.has("lootType") && !binding.get("lootType").isJsonNull() && binding.get("lootType").getAsString().equals(type)) id = binding.get("id").getAsString();
        }
        require(id != null, "typed binding missing: " + type);
        for (JsonElement value : state.getAsJsonArray("receipts")) if (value.getAsJsonObject().get("bindingId").getAsString().equals(id)) return value.getAsJsonObject();
        throw new IllegalStateException("typed receipt missing: " + type);
    }

    private void initialContentAuthoring(MinecraftClient client) {
        command(client, "look at chest for template capture", "tp @s 16.5 -58 16.5 0 90", ".*Teleported.*");
        command(client, "prepare named fixture supply", "item replace block 16 -60 16 container.0 with minecraft:diamond[minecraft:custom_name='Chronicle supply'] 7", ".*Replaced.*");
        command(client, "capture reusable contents", "chronicle author chest legacy capture smoke_supply", ".*Captured chest template smoke_supply.*slots=27.*");
        command(client, "capture preserves source components", "data get block 16 -60 16 Items", ".*Chronicle supply.*");
        command(client, "clear disposable source for fill test", "item replace block 16 -60 16 container.0 with minecraft:air", ".*Replaced.*");
        command(client, "bind once per campaign chest", "chronicle author chest legacy bind smoke_stock smoke_chest smoke_supply once_campaign", ".*Bound chest smoke_stock.*once_campaign.*");
        command(client, "bind manual arrival purpose", "chronicle spawn bind \"chronicle_core:arrival\" smoke_arrival", ".*Bound chronicle_core:arrival.*No player was moved.*");
        command(client, "inspect safe arrival", "chronicle spawn inspect \"chronicle_core:arrival\"", ".*safety=safe.*");
        command(client, "inactive fills refused", "chronicle chest preview smoke_stock", ".*require an active Chronicle session.*");
    }

    private void exerciseContent(MinecraftClient client) {
        command(client, "fills require a preview", "chronicle chest apply smoke_stock", ".*Preview this binding before applying.*");
        command(client, "preview empty target fill", "chronicle chest preview smoke_stock", ".*Previewed chest smoke_stock.*occupied=1.*");
        command(client, "journal and apply empty-only fill", "chronicle chest apply smoke_stock", ".*Filled chest smoke_stock.*receipt saved.*");
        command(client, "filled target preserves item components", "data get block 16 -60 16 Items", ".*Chronicle supply.*");
        command(client, "once campaign rejects repeat", "chronicle chest preview smoke_stock", ".*already run in this campaign.*");
        command(client, "fill receipt has no pending intent", "chronicle chest status", ".*templates=1; bindings=4; receipts=4; pending=0.*");
        command(client, "teleport requires preview", "chronicle spawn confirm ChronicleFabricA", ".*Preview this destination.*first.*");
        command(client, "preview exact named player", "chronicle spawn preview \"chronicle_core:arrival\" ChronicleFabricA", ".*Previewed chronicle_core:arrival.*yaw=90.0.*");
        command(client, "block unsafe destination after preview", "setblock 16 -60 18 minecraft:stone", ".*Changed the block.*");
        command(client, "recheck standing room at confirm", "chronicle spawn confirm ChronicleFabricA", ".*Cannot execute.*standing.*");
        command(client, "restore safe standing room", "setblock 16 -60 18 minecraft:air", ".*Changed the block.*");
        command(client, "preview restored arrival", "chronicle spawn preview \"chronicle_core:arrival\" ChronicleFabricA", ".*Previewed chronicle_core:arrival.*");
        command(client, "move exact named player", "chronicle spawn confirm ChronicleFabricA", ".*Moved ChronicleFabricA.*Vanilla respawn was not changed.*");
        check("manual arrival retains precise position and facing", () -> {
            require(Math.abs(client.player.getX() - 16.5) < .05 && Math.abs(client.player.getZ() - 18.5) < .05, "manual spawn destination differs");
            require(Math.abs(client.player.getYaw() - 90) < .05, "manual spawn facing differs");
        });
    }

    private void assertAuthoring() throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(serverRoot.resolve("world/chronicle/authoring.json"))).getAsJsonObject();
        require(root.get("schemaVersion").getAsInt() == 1 && (root.get("revision").getAsLong() == 8 || root.get("revision").getAsLong() == 10), "authoring schema/revision mismatch");
        JsonArray sites = root.getAsJsonArray("sites");
        require(sites.size() == 3, "authoring lost or added definitions");
        Map<String, JsonObject> byId = new HashMap<>();
        for (JsonElement site : sites) byId.put(site.getAsJsonObject().get("id").getAsString(), site.getAsJsonObject());
        require(byId.keySet().equals(java.util.Set.of("smoke_city", "smoke_arrival", "smoke_chest")), "unexpected durable site IDs");
        require(byId.get("smoke_city").get("revision").getAsLong() == 2, "region replace revision missing");
        require(byId.get("smoke_arrival").get("yaw").getAsFloat() == 90 && byId.get("smoke_arrival").get("x").getAsDouble() == 16.5, "precise location/facing lost");
        require(byId.get("smoke_chest").get("expectedBlock").getAsString().equals("minecraft:chest"), "expected block binding lost");
    }

    private void command(MinecraftClient client, String name, String command, String expression) {
        command(client, name, command, Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.DOTALL), matcher -> { });
    }

    private void command(MinecraftClient client, String name, String command, Pattern expected, Consumer<Matcher> matched) {
        final long[] baseline = new long[1];
        task(name, () -> {
            baseline[0] = messageSequence;
            client.player.networkHandler.sendChatCommand(command);
        }, () -> {
            for (Message message : messages) {
                if (message.sequence <= baseline[0] || message.overlay) continue;
                Matcher matcher = expected.matcher(message.text);
                if (matcher.matches()) {
                    matched.accept(matcher);
                    return true;
                }
            }
            return false;
        });
    }

    private void mutation(MinecraftClient client, String name, String command, String expected, CheckedRunnable assertion) {
        command(client, name, command, expected);
        delay(name + " persistence settle", 1_000);
        check(name + " persisted", assertion);
    }

    private void rejectedCommand(MinecraftClient client, String name, String command, String expected) {
        String key = "snapshot: " + name;
        capture(key, campaignFile);
        command(client, name, command, expected);
        delay(name + " settle", 250);
        unchanged(name + " does not mutate campaign", key, campaignFile);
    }

    private void readOnlyCommand(MinecraftClient client, String name, String command, String expected) {
        String key = "snapshot: " + name;
        capture(key, campaignFile);
        command(client, name, command, expected);
        delay(name + " settle", 250);
        unchanged(name + " preserves campaign JSON", key, campaignFile);
    }

    private void timeQuery(MinecraftClient client, String name, Consumer<Integer> value) {
        command(client, name, "time query daytime", TIME_REPLY, matcher -> value.accept(Integer.parseInt(matcher.group(1))));
    }

    private void pigYQuery(MinecraftClient client, String name, Consumer<Double> value) {
        command(client, name,
                "data get entity @e[type=minecraft:pig,tag=chronicle_fabric_smoke,limit=1,sort=nearest] Pos[1]",
                PIG_Y_REPLY, matcher -> value.accept(Double.parseDouble(matcher.group(1))));
    }

    private void actionbar(String name, String expected) {
        task(name, () -> { }, () -> messages.stream().anyMatch(message -> message.overlay && message.text.equals(expected)));
    }

    private void movement(MinecraftClient client, String name) {
        final Vec3d[] start = new Vec3d[1];
        final long[] until = new long[1];
        task(name, () -> {
            start[0] = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
            client.options.forwardKey.setPressed(true);
            until[0] = System.nanoTime() + 1_100_000_000L;
        }, () -> {
            if (System.nanoTime() < until[0]) {
                // A second automated client can take desktop focus. Reassert the
                // opt-in test input each tick so focus callbacks cannot clear it.
                client.options.forwardKey.setPressed(true);
                return false;
            }
            client.options.forwardKey.setPressed(false);
            Vec3d end = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
            double dx = end.x - start[0].x;
            double dz = end.z - start[0].z;
            require(Math.sqrt(dx * dx + dz * dz) > 0.1, "player did not move horizontally while simulation was frozen");
            return true;
        });
    }

    private void profileRequest(String name, UUID owner, Consumer<ProfilePayloads.View> assertion) {
        final int[] baseline = new int[1];
        task(name, () -> {
            baseline[0] = views.size();
            ClientPlayNetworking.send(new ProfilePayloads.Request(owner));
        }, () -> {
            if (views.size() <= baseline[0]) return false;
            assertion.accept(views.get(baseline[0]));
            return true;
        });
    }

    private void profileSave(String name, long expectedRevision, String publicName, String pronouns,
                             String description, Consumer<ProfilePayloads.View> assertion) {
        final int[] baseline = new int[1];
        task(name, () -> {
            baseline[0] = views.size();
            ClientPlayNetworking.send(new ProfilePayloads.Save(expectedRevision, publicName, pronouns, description));
        }, () -> {
            if (views.size() <= baseline[0]) return false;
            assertion.accept(views.get(baseline[0]));
            return true;
        });
    }

    private void profileNotice(String name, ProfilePayloads.Save save, String expectedKey) {
        final int[] baseline = new int[1];
        task(name, () -> {
            baseline[0] = notices.size();
            ClientPlayNetworking.send(save);
        }, () -> {
            if (notices.size() <= baseline[0]) return false;
            require(notices.get(baseline[0]).translationKey().equals(expectedKey),
                    "expected profile notice " + expectedKey + " but received " + notices.get(baseline[0]).translationKey());
            return true;
        });
    }

    private void capture(String key, Path path) {
        check(key, () -> snapshots.put(key, fileSnapshot(path)));
    }

    private void unchanged(String name, String key, Path path) {
        check(name, () -> require(snapshots.containsKey(key) && snapshots.get(key).equals(fileSnapshot(path)), name + ": durable JSON changed"));
    }

    private void delay(String name, long milliseconds) {
        final long[] until = new long[1];
        task(name, () -> until[0] = System.nanoTime() + milliseconds * 1_000_000L, () -> System.nanoTime() >= until[0]);
    }

    private void waitForMarker(String name, Path marker) {
        task(name, 120_000_000_000L, () -> { }, () -> Files.isRegularFile(marker));
    }

    private void markerTask(String name, Path marker) {
        check(name, () -> {
            JsonObject progress = new JsonObject();
            progress.addProperty("role", role);
            progress.addProperty("phase", phase);
            progress.addProperty("step", name);
            Files.writeString(marker, progress + "\n", StandardCharsets.UTF_8);
        });
    }

    private void check(String name, CheckedRunnable assertion) {
        final boolean[] done = new boolean[1];
        task(name, () -> {
            assertion.run();
            done[0] = true;
        }, () -> done[0]);
    }

    private void task(String name, CheckedRunnable start, CheckedBoolean done) {
        task(name, DEFAULT_STEP_TIMEOUT_NANOS, start, done);
    }

    private void task(String name, long timeoutNanos, CheckedRunnable start, CheckedBoolean done) {
        tasks.add(new Task(name, timeoutNanos, start, done));
    }

    private JsonObject campaign() throws IOException {
        require(Files.isRegularFile(campaignFile), "campaign state file is missing: " + campaignFile);
        JsonElement parsed = JsonParser.parseString(Files.readString(campaignFile, StandardCharsets.UTF_8));
        require(parsed.isJsonObject(), "campaign state root is not an object");
        return parsed.getAsJsonObject();
    }

    private void assertCampaign(String expectedPhase, int expectedNumber) throws IOException {
        JsonObject state = campaign();
        require(state.get("phase").getAsString().equals(expectedPhase), "expected campaign phase " + expectedPhase + " but found " + state.get("phase"));
        require(state.get("sessionNumber").getAsInt() == expectedNumber, "expected session " + expectedNumber + " but found " + state.get("sessionNumber"));
    }

    private void assertCampaignAndM1Persistence(String expectedPhase, int expectedNumber) throws IOException {
        JsonObject state = campaign();
        assertCampaign(expectedPhase, expectedNumber);
        require(state.getAsJsonObject("flags").get("quiet_below:fabric_smoke").getAsBoolean(), "Fabric smoke flag did not survive restart");
        require(state.getAsJsonObject("flags").get("quiet_below:readiness_checked").getAsBoolean(), "readiness flag did not survive restart");
        require(jsonArrayContains(state.getAsJsonArray("completedEvents"), "quiet_below:readiness_check"), "completed readiness event did not survive restart");
    }

    private void assertAudit(List<String> expectedActions) throws IOException {
        JsonObject state = campaign();
        JsonArray history = state.getAsJsonArray("history");
        require(history.size() == expectedActions.size(), "audit size proves a failed/read-only operation committed: expected "
                + expectedActions.size() + " but found " + history.size());
        for (int index = 0; index < expectedActions.size(); index++) {
            JsonObject entry = history.get(index).getAsJsonObject();
            require(entry.get("sequence").getAsLong() == index, "audit sequence is not contiguous at index " + index);
            require(entry.get("action").getAsString().equals(expectedActions.get(index)), "unexpected audit action at index " + index + ": " + entry.get("action"));
            if (expectedActions.get(index).equals("observation.reset.requested")) {
                require(entry.get("target").getAsString().equals("quiet_below:distant_stir"), "reset audit lost the exact cue ID");
                JsonObject evidence = entry.getAsJsonObject("parameters");
                require(evidence.get("priorStatus").getAsString().equals("DELIVERED"), "reset audit lost the previous receipt status");
                require(evidence.get("recipients.0").getAsString().contains(FABRIC_A.toString()) && evidence.get("recipients.0").getAsString().contains(FABRIC_B.toString()), "reset audit lost the prior recipients");
            }
            JsonObject actor = entry.getAsJsonObject("actor");
            require(actor.get("name").getAsString().equals("ChronicleFabricA"), "campaign mutation actor was not ChronicleFabricA");
            require(actor.has("uuid") && actor.get("uuid").getAsString().equals(FABRIC_A.toString()), "campaign mutation actor UUID was not Fabric A");
        }
        require(state.get("nextSequence").getAsLong() == expectedActions.size(), "next audit sequence was not persisted");
    }

    private void assertProfile(UUID owner, String expectedName, String expectedPronouns,
                               String expectedDescription, long expectedRevision) throws IOException {
        JsonObject profile = JsonParser.parseString(Files.readString(profileFile(owner), StandardCharsets.UTF_8)).getAsJsonObject();
        require(profile.get("owner").getAsString().equals(owner.toString()), "persisted profile owner mismatch");
        require(profile.get("name").getAsString().equals(expectedName), "persisted profile name mismatch");
        require(profile.get("pronouns").getAsString().equals(expectedPronouns), "persisted profile pronouns mismatch");
        require(profile.get("description").getAsString().equals(expectedDescription), "persisted profile description mismatch");
        require(profile.get("revision").getAsLong() == expectedRevision, "persisted profile revision mismatch");
    }

    private static void assertView(ProfilePayloads.View view, UUID owner, String expectedName,
                                   String expectedPronouns, String expectedDescription, long expectedRevision,
                                   boolean editable, boolean saved) {
        require(view.owner().equals(owner), "profile response owner mismatch");
        require(view.name().equals(expectedName), "profile response name mismatch");
        require(view.pronouns().equals(expectedPronouns), "profile response pronouns mismatch");
        require(view.description().equals(expectedDescription), "profile response description mismatch");
        require(view.revision() == expectedRevision, "profile response revision mismatch");
        require(view.editable() == editable, "profile response editability mismatch");
        require(view.saved() == saved, "profile response acknowledgement mismatch");
    }

    private Path profileFile(UUID owner) { return profilesDirectory.resolve(owner + ".json"); }
    private Path marker(String markerRole, String markerPhase, String name) {
        return output.resolve("progress-" + markerRole + "-" + markerPhase + "-" + name + ".json");
    }
    private static String fileSnapshot(Path path) throws IOException {
        return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "<absent>";
    }
    private static int dayDelta(int before, int after) { return Math.floorMod(after - before, 24_000); }
    private static boolean jsonArrayContains(JsonArray values, String expected) {
        for (JsonElement value : values) if (value.isJsonPrimitive() && value.getAsString().equals(expected)) return true;
        return false;
    }
    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void fail(MinecraftClient client, Throwable error) {
        ChronicleCore.LOGGER.error("Fabric regression {}/{} failed", role, phase, error);
        finish(client, false, error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
    }

    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (complete) return;
        complete = true;
        client.options.forwardKey.setPressed(false);
        JsonObject result = new JsonObject();
        result.addProperty("result", pass ? "PASS" : "FAIL");
        result.addProperty("role", role);
        result.addProperty("phase", phase);
        result.addProperty("detail", detail);
        result.addProperty("currentStep", taskIndex < tasks.size() ? tasks.get(taskIndex).name : "complete");
        JsonArray steps = new JsonArray();
        completedSteps.forEach(steps::add);
        result.add("completedSteps", steps);
        JsonArray recent = new JsonArray();
        int start = Math.max(0, messages.size() - 12);
        for (int index = start; index < messages.size(); index++) {
            Message message = messages.get(index);
            JsonObject encoded = new JsonObject();
            encoded.addProperty("overlay", message.overlay);
            encoded.addProperty("text", message.text);
            recent.add(encoded);
        }
        result.add("recentMessages", recent);
        JsonArray receivedNotices = new JsonArray();
        notices.forEach(notice -> receivedNotices.add(notice.translationKey()));
        result.add("profileNotices", receivedNotices);
        try {
            Files.writeString(output.resolve("role-" + role + "-" + phase + ".json"), result + "\n", StandardCharsets.UTF_8);
        } catch (IOException error) {
            ChronicleCore.LOGGER.error("Could not write Fabric regression result", error);
        }
        client.scheduleStop();
    }

    private static void writeSetupFailure(Throwable error) {
        try {
            Path directory = Path.of(OUTPUT_ENV).toAbsolutePath().normalize();
            if (SERVER_ENV != null && directory.startsWith(Path.of(SERVER_ENV).toAbsolutePath().normalize())) return;
            Files.createDirectories(directory);
            JsonObject result = new JsonObject();
            result.addProperty("result", "FAIL");
            result.addProperty("role", String.valueOf(ROLE_ENV));
            result.addProperty("phase", String.valueOf(PHASE_ENV));
            result.addProperty("detail", "setup: " + error.getMessage());
            Files.writeString(directory.resolve("role-" + ROLE_ENV + "-" + PHASE_ENV + ".json"), result + "\n", StandardCharsets.UTF_8);
        } catch (RuntimeException | IOException ignored) {
            // The logger remains the only diagnostic if the explicit output path is unusable.
        }
    }

    @FunctionalInterface private interface CheckedRunnable { void run() throws Exception; }
    @FunctionalInterface private interface CheckedBoolean { boolean test() throws Exception; }
    private record Task(String name, long timeoutNanos, CheckedRunnable start, CheckedBoolean done) { }
    private record Message(long sequence, String text, boolean overlay) { }
}
