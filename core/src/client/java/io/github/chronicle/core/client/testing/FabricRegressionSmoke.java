package io.github.chronicle.core.client.testing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.client.DossierScreen;
import io.github.chronicle.core.client.AuthoringPreviewRenderer;
import io.github.chronicle.core.network.ProfilePayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
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
            "session.start", "flag.set", "event.trigger", "session.pause", "session.resume", "session.pause");
    private static final List<String> RESTART_AUDIT = List.of(
            "session.start", "flag.set", "event.trigger", "session.pause", "session.resume", "session.pause",
            "session.resume", "session.end", "session.start", "session.end");

    private static FabricRegressionSmoke instance;

    private final String role;
    private final String phase;
    private final Path output;
    private final Path serverRoot;
    private final Path campaignFile;
    private final Path profilesDirectory;
    private final long overallDeadline = System.nanoTime() + OVERALL_TIMEOUT_NANOS;
    private final List<Message> messages = new ArrayList<>();
    private final List<ProfilePayloads.View> views = new ArrayList<>();
    private final List<ProfilePayloads.Notice> notices = new ArrayList<>();
    private final List<Task> tasks = new ArrayList<>();
    private final List<String> completedSteps = new ArrayList<>();
    private final Map<String, String> snapshots = new HashMap<>();

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
            if (client.currentScreen instanceof DossierScreen) client.setScreen(null);
            if (client.currentScreen != null) return;
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
        waitForMarker("wait for B unauthorized denial", marker("B", "initial", "denied"));
        initialAuthoring(client);
        actionbar("inactive actionbar", "Chronicle | Session 0: Inactive | World frozen");
        timeQuery(client, "capture frozen time", value -> firstTime = value);
        movement(client, "player moves while inactive and frozen");
        timeQuery(client, "capture frozen time after movement", value -> secondTime = value);
        check("inactive world time stays frozen", () -> require(dayDelta(firstTime, secondTime) <= 2,
                "inactive world time advanced: " + firstTime + " -> " + secondTime));

        mutation(client, "start session 1", "chronicle session start 1", "^Started Chronicle session 1\\.$", () -> assertCampaign("ACTIVE", 1));
        actionbar("active session 1 actionbar", "Chronicle | Session 1: Active");
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
        actionbar("paused session 1 actionbar", "Chronicle | Session 1: Paused | World frozen");
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
        delay("allow tagged pig gravity while active", 900);
        pigYQuery(client, "capture active pig Y", value -> secondPigY = value);
        check("tagged pig falls while active", () -> require(secondPigY < firstPigY - 0.1,
                "tagged pig did not fall after resume: " + firstPigY + " -> " + secondPigY));
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
                "chronicle author show smoke_city", "chronicle author list")) {
            command(client, "non-op denied: " + command, command, UNAUTHORIZED_REPLY, matcher -> { });
        }
        unchanged("non-op author commands preserve registry", "authoring before unauthorized edits", authoring);
        markerTask("publish B unauthorized denial", marker("B", "initial", "denied"));

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
        command(client, "author mode is not restored on restart", "chronicle author remove smoke_city", ".*Enable author mode first.*");
        command(client, "persisted region can be shown", "chronicle author show smoke_city", ".*region smoke_city.*volume=36.*validation=valid.*");
        command(client, "persisted anchor validates", "chronicle author validate", ".*valid=3; invalid=0; unresolved=0.*");
        actionbar("restart paused actionbar", "Chronicle | Session 1: Paused | World frozen");
        check("A observes restart persistence", () -> {
            assertCampaignAndM1Persistence("PAUSED", 1);
            assertProfile(FABRIC_A, "Fabric A", "they/them", "A public test card", 1);
            assertProfile(FABRIC_B, "Fabric B", "", "", 1);
            assertAudit(INITIAL_AUDIT);
        });
        profileRequest("A fetches own persisted profile", FABRIC_A,
                view -> assertView(view, FABRIC_A, "Fabric A", "they/them", "A public test card", 1, true, false));
        capture("capture restart A profile", profileFile(FABRIC_A));
        capture("capture restart B profile", profileFile(FABRIC_B));
        waitForMarker("wait for B restart checks and denied resume", marker("B", "restart", "ready"));

        mutation(client, "restart resume session 1", "chronicle session resume", "^Resumed Chronicle session 1\\.$", () -> assertCampaign("ACTIVE", 1));
        actionbar("restart active actionbar", "Chronicle | Session 1: Active");
        mutation(client, "end session 1", "chronicle session end", "^Ended Chronicle session 1\\.$", () -> assertCampaign("ENDED", 1));
        mutation(client, "start default session 2", "chronicle session start", "^Started Chronicle session 2\\.$", () -> assertCampaign("ACTIVE", 2));
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
        command(client, "move to precise oriented test location", "tp @s 16.5 -58 16.5 90 0", ".*Teleported.*");
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
        check("independently inspect durable authoring metadata", this::assertAuthoring);
        unchanged("metadata authoring preserves campaign progress", "campaign before metadata authoring", campaignFile);
        command(client, "view region outline from outside", "tp @s 22 -56 22 135 30", ".*Teleported.*");
        command(client, "show private outline", "chronicle author show smoke_city", ".*region smoke_city.*validation=valid.*");
        check("hide HUD for outline inspection", () -> client.options.hudHidden = true);
        delay("allow particle outline to render", 300);
        check("private outline renders while world frozen", () -> require(AuthoringPreviewRenderer.renderedFrames() > 0,
                "authoring outline skipped frozen world rendering"));
        check("capture authoring outline", () -> ScreenshotRecorder.saveScreenshot(output.toFile(), "m6-outline.png", client.getFramebuffer(), 1, ignored -> { }));
        delay("allow outline screenshot to complete", 300);
        check("restore HUD after outline inspection", () -> client.options.hudHidden = false);
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

    private void assertAuthoring() throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(serverRoot.resolve("world/chronicle/authoring.json"))).getAsJsonObject();
        require(root.get("schemaVersion").getAsInt() == 1 && root.get("revision").getAsLong() == 6, "authoring schema/revision mismatch");
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
