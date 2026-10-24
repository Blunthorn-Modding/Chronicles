package io.github.chronicle.core.client.testing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.client.CoreMenuScreen;
import io.github.chronicle.core.menu.CoreMenuModel.Action;
import io.github.chronicle.core.menu.CoreMenuModel.Page;
import io.github.chronicle.core.menu.CoreMenuModel.View;
import io.github.chronicle.core.network.CoreMenuPayloads;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deliberately small, disposable native-screen probe.  It is independent from
 * {@link FabricRegressionSmoke}: a CF-10 run starts from a fresh server root,
 * so historic fixture revisions and receipts cannot affect its assertions.
 *
 * <p>The host supplies a loopback-only endpoint and collects one JSON result
 * per deterministic offline account.  This class never starts a server and
 * refuses to run when any of those guardrails are absent.</p>
 */
public final class CoreMenuSmoke {
    private static final String OUTPUT_ENV = System.getenv("CHRONICLE_MENU_SMOKE");
    private static final String ROLE_ENV = System.getenv("CHRONICLE_MENU_ROLE");
    private static final String SERVER_ENV = System.getenv("CHRONICLE_MENU_SERVER_DIR");
    private static final boolean LAYOUT_ONLY = "true".equals(System.getenv("CHRONICLE_MENU_LAYOUT_ONLY"));
    private static final long DEADLINE_NANOS = 240_000_000_000L;
    private static final UUID GM = offlineUuid("ChronicleMenuA");
    private static final UUID PLAYER = offlineUuid("ChronicleMenuB");
    private static final String CITY_MESSAGE = "The ancient stone carries a low vibration.";
    private static final String DISTANT_MESSAGE = "A low, unfamiliar rumble breaks the quiet.";
    private static CoreMenuSmoke instance;

    private final String role;
    private final Path output;
    private final Path serverRoot;
    private final Path authoringFile;
    private final Path playFile;
    private final Path lootFile;
    private final Path assignmentsFile;
    private final long deadline = System.nanoTime() + DEADLINE_NANOS;
    private final List<String> checks = new ArrayList<>();
    private final List<String> chatMessages = new ArrayList<>();
    private long previewMessageCount;
    private final Map<String, String> snapshots = new HashMap<>();
    private View lastView;
    private final List<String> rejectedViews = new ArrayList<>();
    private int observedStep = -1;
    private long stepStarted;
    private int driveTicks;
    private boolean connecting;
    private boolean complete;
    private int step;
    private long viewCount;
    private long waitingFor;
    private int stableTicks;
    private CoreMenuScreen.ButtonProbe stableProbe;

    private CoreMenuSmoke(String role, Path output, Path serverRoot) throws IOException {
        if (!role.equals("A") && !role.equals("B")) throw new IllegalArgumentException("CHRONICLE_MENU_ROLE must be A or B");
        this.role = role;
        this.output = output.toAbsolutePath().normalize();
        this.serverRoot = serverRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.serverRoot)) throw new IllegalArgumentException("CHRONICLE_MENU_SERVER_DIR must be a staged disposable server");
        if (this.output.startsWith(this.serverRoot)) throw new IllegalArgumentException("menu smoke evidence must be outside its test world");
        Files.createDirectories(this.output);
        Path chronicle = this.serverRoot.resolve("world/chronicle");
        this.authoringFile = chronicle.resolve("authoring.json");
        this.playFile = chronicle.resolve("play-runtime.json");
        this.lootFile = this.serverRoot.resolve("config/chronicle/chest-loot.json");
        this.assignmentsFile = chronicle.resolve("map-assignments.json");
    }

    /** Registers only for the dedicated menu-smoke environment. */
    public static void register() {
        if (OUTPUT_ENV == null) return;
        try {
            if (ROLE_ENV == null || SERVER_ENV == null) throw new IllegalArgumentException("menu smoke role and staged server directory are required");
            instance = new CoreMenuSmoke(ROLE_ENV, Path.of(OUTPUT_ENV), Path.of(SERVER_ENV));
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay && !instance.complete) instance.chatMessages.add(message.getString()); });
            ClientTickEvents.END_CLIENT_TICK.register(instance::tick);
        } catch (IOException | RuntimeException error) {
            ChronicleCore.LOGGER.error("Core menu smoke setup failed", error);
            writeSetupFailure(error);
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    /** Called by the normal menu transport before its screen consumes a view. */
    public static void onView(View view) {
        if (instance != null && !instance.complete) {
            instance.lastView = view;
            if (!view.success() && instance.rejectedViews.size() < 32) {
                instance.rejectedViews.add("step " + instance.step + ": " + view.message());
                ChronicleCore.LOGGER.info("Core menu smoke {} rejected at step {}: {}", instance.role, instance.step, view.message());
            }
            instance.viewCount++;
        }
    }

    private void tick(MinecraftClient client) {
        if (complete) return;
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("overall 240 second deadline exceeded");
            if (observedStep != step) {
                observedStep = step; stepStarted = System.nanoTime();
                ChronicleCore.LOGGER.info("Core menu smoke {} step {}", role, step);
            } else if (System.nanoTime() - stepStarted > 120_000_000_000L) {
                throw new IllegalStateException("step " + step + " exceeded 120 seconds; rejected responses: " + rejectedViews);
            }
            if (client.player == null && client.getOverlay() == null && client.currentScreen instanceof AccessibilityOnboardingScreen) client.setScreen(new TitleScreen());
            if (!connecting && client.player == null && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                connecting = true;
                String address = "127.0.0.1:25575";
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(address),
                        new ServerInfo("Chronicle Core menu smoke", address, ServerInfo.ServerType.OTHER), false, null);
                return;
            }
            if (client.player == null) return;
            client.getToastManager().clear();
            requireFixture(client);
            require(chatMessages.stream().noneMatch(message -> message.contains("Expected whitespace to end one argument")), "test command has invalid Brigadier quoting");
            // A visible native action has already sent its request.  Do not let
            // the next scripted case press a newly rebuilt control until the
            // authoritative response has arrived (notably Continue in world).
            if (client.currentScreen instanceof CoreMenuScreen screen && screen.pending()) return;
            // Pace scripted actions below the normal endpoint's 20 requests/second limit.
            if (++driveTicks % 4 != 0) return;
            if (role.equals("A")) { if (LAYOUT_ONLY) driveLayout(client); else driveOperator(client); } else drivePlayer(client);
        } catch (Throwable error) {
            ChronicleCore.LOGGER.error("Core menu smoke {} failed", role, error);
            finish(client, false, error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void driveOperator(MinecraftClient client) {
        if(step>=20000){driveBuildingMode(client);return;}
        if(step>=210&&step<1000){driveRegionUses(client);return;}
        if(step>=110){driveBlockBindings(client);return;}
        switch (step) {
            case 0 -> { openMenu(client); advance("operator opened the native menu"); }
            case 1 -> awaitScreen(client, Page.SESSION, "Session");
            case 2 -> { verifyCleanDebug(client);capture(client, "menu-operator-" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight() + ".png"); checks.add("captured native clean menu layout");step=20000; }
            case 3 -> navigate(client, "Players", Page.PLAYERS);
            case 4 -> navigate(client, "Events", Page.EVENTS);
            case 5 -> navigate(client, "Chests", Page.CHESTS);
            case 6 -> navigate(client, "Loot Tables", Page.LOOT_TABLES);
            case 7 -> navigate(client, "Regions", Page.REGIONS);
            case 8 -> navigate(client, "Locations", Page.LOCATIONS);
            case 9 -> {
                CoreMenuScreen screen = screen(client);
                require(screen.triggerForTest("Points"), "Points selector is unavailable");
                waitingFor = viewCount; step++;
            }
            case 10 -> awaitView(() -> screen(client).state().page() == Page.LOCATIONS && "POINT".equals(screen(client).state().filter()), "Locations exposes Points selector");
            case 11 -> {
                CoreMenuScreen screen = screen(client);
                require(screen.triggerForTest("Block sites"), "Block sites selector is unavailable");
                waitingFor = viewCount; step++;
            }
            case 12 -> awaitView(() -> "BLOCK".equals(screen(client).state().filter()), "Locations exposes Block sites selector");
            case 13 -> navigate(client, "Game setup", Page.SETUP);
            case 14 -> {
                if (client.getNetworkHandler().getPlayerList().size() < 2 || ++stableTicks < 10) return;
                CoreMenuScreen screen = screen(client);
                require(screen.triggerForTest("Enable editing"), "Enable editing is unavailable to the operator");
                waitingFor = viewCount; step++;
            }
            case 15 -> awaitView(() -> screen(client).state().editing(), "server acknowledged editing mode");
            case 16 -> {
                require(screen(client).triggerForTest("Regions"), "Regions navigation unavailable after editing acknowledgement");
                waitingFor = viewCount; step++;
            }
            case 17 -> awaitView(() -> screen(client).state().page() == Page.REGIONS, "opened Regions while editing");
            case 18 -> {
                require(press(screen(client), "Create region…"), "Create region action unavailable"); step++;
            }
            case 19 -> {
                screen(client).textForTest("menu_smoke_region");
                require(screen(client).triggerForTest("Send"), "region ID prompt cannot submit"); waitingFor = viewCount; step++;
            }
            case 20 -> awaitView(() -> screen(client).state().detail() != null && screen(client).state().detail().region() != null,
                    "created named region draft");
            case 21 -> { capture(client, "menu-region-draft-" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight() + ".png"); advance("captured native region draft"); }
            case 22 -> {
                require(press(screen(client), "Continue in world"), "Continue in world action unavailable");
                waitingFor = viewCount; step++;
            }
            case 23 -> {
                // Continue deliberately closes the presentation but leaves the
                // server-owned draft/outline active.  The documented commands
                // then establish both corners in that same draft.
                require(!(client.currentScreen instanceof CoreMenuScreen), "Continue in world did not return to the world");
                client.player.networkHandler.sendChatCommand("chronicles author region pos1 1 64 1");
                client.player.networkHandler.sendChatCommand("chronicles author region pos2 4 67 4");
                openMenu(client); step++;
            }
            case 24 -> awaitScreen(client, Page.SESSION, "reopened menu after world corner selection");
            case 25 -> {
                require(screen(client).triggerForTest("Regions"), "Regions navigation unavailable after world selection"); waitingFor = viewCount; step++;
            }
            case 26 -> awaitView(() -> screen(client).state().page() == Page.REGIONS && screen(client).state().detail() != null
                    && screen(client).state().detail().region() != null, "draft positions returned to Regions");
            case 27 -> {
                require(press(screen(client), "Create selected region"), "region save action unavailable after two positions");
                waitingFor = viewCount; step++;
            }
            case 28 -> awaitView(() -> Files.isRegularFile(authoringFile) && fileContains(authoringFile, "menu_smoke_region"),
                    "saved region persists under its stable ID");
            case 29 -> {
                // A preview must be definition-only: it cannot create the
                // optional play-runtime file or alter it while paused.
                require(press(screen(client), "Events"), "Events navigation unavailable"); waitingFor = viewCount; step++;
            }
            case 30 -> awaitView(() -> screen(client).state().page() == Page.EVENTS, "opened Events");
            case 31 -> {
                screen(client).selectForTest("cue:quiet_below:city_presence"); waitingFor = viewCount; step++;
            }
            case 32 -> awaitView(() -> screen(client).state().detail() != null && screen(client).state().detail().id().equals("cue:quiet_below:city_presence"),
                    "selected unconfigured cue");
            case 33 -> { capture(client, "menu-event-detail-" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight() + ".png"); snapshot("unconfigured event before preview", playFile); previewMessageCount = chatCount(CITY_MESSAGE); require(press(screen(client), "Preview cue"), "event preview unavailable"); waitingFor = viewCount; step++; }
            case 34 -> awaitView(() -> previewChatArrived(CITY_MESSAGE) && sameSnapshot("unconfigured event before preview", playFile), "paused unconfigured City preview sends private chat without changing play ledger");
            case 35 -> {
                // This action uses the normal screen callback and server route;
                // no public delivery or completion is asserted from a sample.
                previewMessageCount = chatCount(CITY_MESSAGE);
                if (press(screen(client), "Preview sound")) { waitingFor = viewCount; step++; }
                else { checks.add("cue has no private audio in this fixture"); step = 37; }
            }
            case 36 -> awaitView(() -> previewChatArrived(CITY_MESSAGE) && sameSnapshot("unconfigured event before preview", playFile), "City playback includes private chat and leaves play ledger unchanged");
            case 37 -> {
                require(press(screen(client), "Loot Tables"), "Loot Tables navigation unavailable"); waitingFor = viewCount; step++;
            }
            case 38 -> awaitView(() -> screen(client).state().page() == Page.LOOT_TABLES, "opened Loot Tables");
            case 39 -> { screen(client).selectForTest("test:timed"); waitingFor = viewCount; step++; }
            case 40 -> awaitView(() -> screen(client).state().detail() != null && screen(client).state().detail().loot() != null,
                    "selected disposable loot table");
            case 41 -> { snapshot("loot before add", lootFile); require(press(screen(client), "Add item"), "Add item control unavailable"); waitingFor = viewCount; step++; }
            case 42 -> awaitView(() -> screen(client).state().picker() != null && "ITEMS".equals(screen(client).state().picker().kind()), "opened registry item picker");
            case 43 -> { capture(client, "menu-loot-picker-" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight() + ".png"); screen(client).searchForTest("gold_ingot"); require(press(screen(client), "Find"), "registry picker search unavailable"); waitingFor = viewCount; step++; }
            case 44 -> awaitView(() -> screen(client).state().picker() != null && screen(client).state().picker().entries().stream().anyMatch(e -> e.id().equals("minecraft:gold_ingot")),
                    "registry picker searched real item IDs");
            case 45 -> { screen(client).selectForTest("minecraft:gold_ingot"); waitingFor = viewCount; step++; }
            case 46 -> awaitView(() -> screen(client).state().picker() == null && screen(client).state().detail() != null && screen(client).state().detail().loot() != null,
                    "added selected registry item to local table draft");
            case 47 -> {
                CoreMenuScreen menu = screen(client);
                // ``test:timed`` starts with exactly one entry in this private
                // fixture; the newly added registry item is draft row 1.
                menu.selectLootEntryForTest(1);
                menu.setLootChanceForTest("37.5"); menu.setLootMaxForTest("3"); menu.setLootMinForTest("2");
                require(press(menu, "Save loot table"), "Save loot table control unavailable"); waitingFor = viewCount; step++;
            }
            case 48 -> awaitView(() -> !sameSnapshot("loot before add", lootFile) && fileContains(lootFile, "minecraft:gold_ingot"),
                    "whole loot table save persists registry item without filling a chest");
            case 49 -> { require(press(screen(client), "Regions"), "Regions navigation unavailable"); waitingFor = viewCount; step++; }
            case 50 -> awaitView(() -> screen(client).state().page() == Page.REGIONS, "opened the sole region workflow");
            case 51 -> { screen(client).selectForTest("menu_smoke_region"); waitingFor = viewCount; step++; }
            case 52 -> awaitView(() -> screen(client).state().detail() != null && screen(client).state().selected().equals("menu_smoke_region"), "selected saved geometry for a typed use");
            case 53 -> { require(press(screen(client), "Use for\u2026"), "region use picker unavailable"); waitingFor = viewCount; step++; }
            case 54 -> awaitView(() -> screen(client).state().picker() != null && "REGION_PURPOSE".equals(screen(client).state().picker().kind()) && screen(client).state().picker().entries().stream().allMatch(e->e.kind().equals("PURPOSE")), "region only offers registered matching purposes");
            case 55 -> { screen(client).selectForTest("quiet_below:source_city"); require(screen(client).triggerForTest("Confirm: Use for\u2026"), "region use review unavailable"); waitingFor = viewCount; step++; }
            case 56 -> awaitView(() -> Files.isRegularFile(assignmentsFile) && fileContains(assignmentsFile, "menu_smoke_region"),
                    "setup assigns an existing region without copying geometry");
            case 57 -> {
                CoreMenuScreen screen = screen(client);
                require(screen.triggerForTest("Close"), "Close is unavailable");
                advance("closed menu without changing server editing state");
            }
            case 58 -> { require(!(client.currentScreen instanceof CoreMenuScreen), "closed menu remained visible"); openMenu(client); advance("reopened menu after close"); }
            case 59 -> awaitScreen(client, Page.SESSION, "menu reopened through normal command");
            case 60 -> { require(press(screen(client), "Locations"), "Locations unavailable"); waitingFor = viewCount; step++; }
            case 61 -> awaitView(() -> screen(client).state().page() == Page.LOCATIONS, "reopened Locations");
            case 62 -> { require(press(screen(client), "Points"), "Points unavailable"); waitingFor = viewCount; step++; }
            case 63 -> awaitView(() -> "POINT".equals(screen(client).state().filter()), "selected point locations");
            case 64 -> { require(press(screen(client), "Save my position…"), "point creation unavailable"); step++; }
            case 65 -> { screen(client).textForTest("menu_smoke_point"); require(press(screen(client), "Send"), "point prompt unavailable"); waitingFor = viewCount; step++; }
            case 66 -> awaitView(() -> fileContains(authoringFile, "menu_smoke_point"), "point saved through native location form");
            case 67 -> { require(press(screen(client), "Players"), "Players unavailable"); waitingFor = viewCount; step++; }
            case 68 -> awaitView(() -> screen(client).state().page() == Page.PLAYERS, "opened player management");
            case 69 -> { screen(client).selectForTest(GM.toString()); waitingFor = viewCount; step++; }
            case 70 -> awaitView(() -> GM.toString().equals(screen(client).state().selected()), "selected exact player account");
            case 71 -> { require(press(screen(client), "Set starting position…"), "starting destination unavailable"); waitingFor = viewCount; step++; }
            case 72 -> awaitView(() -> screen(client).state().picker() != null, "starting position picker opened");
            case 73 -> { screen(client).selectForTest("menu_smoke_point"); waitingFor = viewCount; step++; }
            case 74 -> awaitView(() -> fileContains(serverRoot.resolve("world/chronicle/automatic-spawns.json"), "arrival"), "starting policy persisted without immediate move");
            case 75 -> { require(press(screen(client), "Set after-death destination…"), "after-death destination unavailable"); waitingFor = viewCount; step++; }
            case 76 -> awaitView(() -> screen(client).state().picker() != null, "independent after-death picker opened");
            case 77 -> { screen(client).selectForTest("menu_smoke_point"); waitingFor = viewCount; step++; }
            case 78 -> awaitView(() -> fileContains(serverRoot.resolve("world/chronicle/automatic-spawns.json"), "respawn"), "independent after-death policy persisted");
            case 79 -> { require(press(screen(client), "Add participation"), "participation unavailable"); waitingFor = viewCount; step++; }
            case 80 -> awaitView(() -> fileContains(playFile, GM.toString()), "participation acknowledged and persisted");
            case 81 -> { require(press(screen(client), "Session"), "Session unavailable"); waitingFor = viewCount; step++; }
            case 82 -> awaitView(() -> screen(client).state().page() == Page.SESSION, "opened play window controls");
            case 83 -> { screen(client).selectForTest("play"); waitingFor = viewCount; step++; }
            case 84 -> awaitView(() -> "play".equals(screen(client).state().selected()), "selected play controls");
            case 85 -> { require(press(screen(client), "Start play"), "Start play unavailable"); waitingFor = viewCount; step++; }
            case 86 -> awaitView(() -> "ACTIVE".equals(screen(client).state().phase()) && !screen(client).state().editing(), "active play clears editing with server acknowledgement");
            case 87 -> { if (++stableTicks < 40) return; stableProbe = screen(client).probeButtonForTest("Refresh"); stableTicks = 0; step++; }
            case 88 -> {
                require(screen(client).probeButtonForTest("Refresh").identity() == stableProbe.identity(), "clock-only refresh rebuilt native controls");
                if (++stableTicks >= 100) advance("active-clock refresh keeps native controls stable for five seconds");
            }
            case 89 -> { require(press(screen(client), "Pause play"), "Pause play unavailable"); waitingFor = viewCount; step++; }
            case 90 -> awaitView(() -> "PAUSED".equals(screen(client).state().phase()), "paused play through native control");
            case 91 -> { require(press(screen(client), "Events"), "Events unavailable"); waitingFor = viewCount; step++; }
            case 92 -> awaitView(() -> screen(client).state().page() == Page.EVENTS, "opened Events for distant preview");
            case 93 -> { screen(client).selectForTest("cue:quiet_below:distant_stir"); waitingFor = viewCount; step++; }
            case 94 -> awaitView(() -> "cue:quiet_below:distant_stir".equals(screen(client).state().selected()), "selected unconfigured Distant stirring");
            case 95 -> { snapshot("distant preview", playFile); previewMessageCount = chatCount(DISTANT_MESSAGE); require(press(screen(client), "Preview cue"), "Distant preview unavailable"); waitingFor = viewCount; step++; }
            case 96 -> awaitView(() -> previewChatArrived(DISTANT_MESSAGE) && sameSnapshot("distant preview", playFile), "paused Distant preview sends private chat without changing play ledger");
            case 97 -> { previewMessageCount = chatCount(DISTANT_MESSAGE); require(press(screen(client), "Preview sound"), "Distant playback unavailable"); waitingFor = viewCount; step++; }
            case 98 -> awaitView(() -> previewChatArrived(DISTANT_MESSAGE) && sameSnapshot("distant preview", playFile), "Distant playback includes private chat and leaves play ledger unchanged");
            case 99 -> { try { Files.writeString(output.resolve("preview-chat-checked"), "done", StandardCharsets.UTF_8); } catch (IOException e) { throw new IllegalStateException(e); } advance("both preview actions checked for both cues"); }
            case 100 -> {require(press(screen(client),"Game setup"),"Game setup unavailable");waitingFor=viewCount;step++;}
            case 101 -> awaitView(()->screen(client).state().page()==Page.SETUP,"opened registered campaign tools");
            case 102 -> {screen(client).selectForTest("tool:quiet_below:ecology");waitingFor=viewCount;step++;}
            case 103 -> awaitView(()->screen(client).state().detail()!=null&&screen(client).state().detail().id().equals("tool:quiet_below:ecology"),"selected generic setup tool through native transport");
            case 104 -> {
                var detail=screen(client).state().detail();
                require(detail.controls().size()==9&&detail.controls().stream().allMatch(c->c.action()==Action.TOOL_ACTION&&c.input().startsWith("TOOL:")),"tool leaked non-finite controls");
                require(detail.controls().stream().anyMatch(c->c.label().equals("Show root")),"root locator missing from setup tool");
                require(detail.controls().stream().filter(c->c.label().equals("Enable growth")).noneMatch(c->c.enabled()),"unindexed tool allowed growth");
                snapshotSize("ecology before read-only refresh",serverRoot.resolve("world/quiet-below/ecology.jsonl"));
                capture(client,"menu-ecology-tool.png");
                require(press(screen(client),"Refresh status"),"registered refresh action unavailable");waitingFor=viewCount;step++;
            }
            case 105 -> awaitView(()->screen(client).state().success()&&screen(client).state().message().contains("Ecology status refreshed")&&sameSize("ecology before read-only refresh",serverRoot.resolve("world/quiet-below/ecology.jsonl")),"finite tool action acknowledged without ecology mutation");
            case 106 -> {screen(client).selectForTest("tool:quiet_below:ownership");waitingFor=viewCount;step++;}
            case 107 -> awaitView(()->screen(client).state().detail()!=null&&screen(client).state().detail().id().equals("tool:quiet_below:ownership"),"ownership management has its own setup tool");
            case 108 -> {
                var d=screen(client).state().detail();
                require(d.controls().size()==5,"ownership actions missing");
                require(d.controls().stream().filter(c->c.label().equals("Delete non-campaign sculk")).allMatch(c->c.confirm()&&!c.enabled()),"unconfigured cleanup must require confirmation and remain disabled");
                capture(client,"menu-sculk-ownership.png");require(press(screen(client),"Refresh status"),"ownership refresh unavailable");waitingFor=viewCount;step++;
            }
            case 109 -> awaitView(()->screen(client).state().success()&&screen(client).state().message().contains("Sculk ownership status refreshed")&&sameSize("ecology before read-only refresh",serverRoot.resolve("world/quiet-below/ecology.jsonl")),"ownership read-only refresh leaves journal unchanged");
            default -> throw new IllegalStateException("unexpected operator menu step "+step);
        }
    }

    private void driveBlockBindings(MinecraftClient client){
        switch(step){
            case 110 -> navigate(client,"Locations",Page.LOCATIONS);
            case 111 -> {require(press(screen(client),"Block sites"),"Block sites unavailable");waitingFor=viewCount;step++;}
            case 112 -> awaitView(()->screen(client).state().filter().equals("BLOCK")&&screen(client).state().entries().isEmpty(),"flat Block sites starts with actual instances only");
            case 113 -> {require(!screen(client).triggerForTest("Game bindings")&&!screen(client).triggerForTest("Saved sites"),"nested block selectors remain");require(press(screen(client),"Enable editing"),"editing unavailable");waitingFor=viewCount;step++;}
            case 114 -> awaitView(()->screen(client).state().editing(),"server acknowledges editing for manual references");
            case 115 -> {screen(client).close();client.player.networkHandler.sendChatCommand("setblock 0 99 0 minecraft:stone");client.player.networkHandler.sendChatCommand("setblock 0 101 3 minecraft:stone");client.player.networkHandler.sendChatCommand("tp @s 0.5 100 0.5 0 0");step++;}
            case 116 -> {if(Math.abs(client.player.getY()-100)>.05||!(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit)||!hit.getBlockPos().equals(new net.minecraft.util.math.BlockPos(0,101,3)))return;openMenu(client);advance("prepared an actual looked-at vanilla reference");}
            case 117 -> awaitScreen(client,Page.SESSION,"reopened menu over vanilla block");
            case 118 -> navigate(client,"Locations",Page.LOCATIONS);
            case 119 -> {require(press(screen(client),"Block sites"),"Block sites unavailable");waitingFor=viewCount;step++;}
            case 120 -> awaitView(()->screen(client).state().filter().equals("BLOCK"),"flat Block sites reopened");
            case 121 -> {snapshot("manual cancelled",authoringFile);snapshot("manual bindings",assignmentsFile);require(press(screen(client),"Bind looked-at block\u2026"),"manual block binding unavailable");step++;}
            case 122 -> {require(screen(client).triggerForTest("Cancel"),"manual naming prompt cancellation unavailable");waitingFor=viewCount;step++;}
            case 123 -> {require(sameSnapshot("manual cancelled",authoringFile),"cancelled manual prompt changed geometry");require(press(screen(client),"Bind looked-at block\u2026"),"manual block binding unavailable after cancel");screen(client).textForTest("menu_smoke_block");var before=screen(client).state();screen(client).toggleDebugForTest();require(screen(client).textForTest().equals("menu_smoke_block")&&screen(client).state()==before,"Debug toggle discarded the prompt or changed the server view");screen(client).toggleDebugForTest();require(screen(client).textForTest().equals("menu_smoke_block"),"Clean toggle discarded the prompt");checks.add("Debug toggle preserves unsaved prompt and server authority");waitingFor=viewCount;try{Files.writeString(output.resolve("prompt-disconnect-ready"),"ready");}catch(IOException error){throw new IllegalStateException(error);}step++;}
            case 124 -> {if(client.player.networkHandler.getPlayerListEntry(PLAYER)!=null||viewCount<=waitingFor)return;require(screen(client).textForTest().equals("menu_smoke_block"),"unrelated disconnect cleared the prompt draft");checks.add("unrelated player disconnect preserves the open naming prompt and typed text");client.player.networkHandler.sendChatCommand("tp @s 0.5 100 0.5 90 0");waitingFor=viewCount;step=173;}
            case 125 -> awaitView(()->screen(client).state().filter().equals("BLOCK")&&screen(client).state().entries().stream().anyMatch(e->e.id().equals("manual:menu_smoke_block"))&&sameSnapshot("manual bindings",assignmentsFile),"manual vanilla reference appears without a game binding");
            case 126 -> {screen(client).selectForTest("manual:menu_smoke_block");waitingFor=viewCount;step++;}
            case 127 -> awaitView(()->screen(client).state().selected().equals("manual:menu_smoke_block"),"selected physical manual reference");
            case 128 -> {snapshot("manual show",authoringFile);require(press(screen(client),"Show block site"),"private manual outline unavailable");waitingFor=viewCount;step++;}
            case 129 -> awaitView(()->screen(client).state().success()&&sameSnapshot("manual show",authoringFile),"showing a manual reference leaves geometry unchanged");
            case 130 -> {snapshot("manual replace",authoringFile);require(press(screen(client),"Replace block site"),"manual reference replacement unavailable");waitingFor=viewCount;step++;}
            case 131 -> awaitView(()->!sameSnapshot("manual replace",authoringFile)&&fileContains(authoringFile,"menu_smoke_block")&&sameSnapshot("manual bindings",assignmentsFile),"manual replacement retains identity and changes no game assignment");
            case 132 -> {require(screen(client).state().detail().controls().stream().noneMatch(c->c.action()==Action.BLOCK_LIMITS||c.action()==Action.BLOCK_LIMIT_SET),"Locations still contains policy controls");snapshot("old limit route",serverRoot.resolve("config/chronicle/managed-block-limits.json"));waitingFor=viewCount;ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.BLOCK_LIMIT_SET,Page.LOCATIONS,"limit:quiet_below:ancient_forge","2",screen(client).state().token(),0,"BLOCK",""));step=1320;}
            case 1320 -> {if(viewCount<=waitingFor)return;require(!screen(client).state().success()&&sameSnapshot("old limit route",serverRoot.resolve("config/chronicle/managed-block-limits.json")),"old Locations policy route changed the cap");waitingFor=viewCount;ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.OPTIONS,Page.LOCATIONS,screen(client).state().selected(),"BLOCK_LIMIT_TYPE",screen(client).state().token(),0,"BLOCK",""));step=1321;}
            case 1321 -> {if(viewCount<=waitingFor)return;require(!screen(client).state().success()&&screen(client).state().picker()==null&&sameSnapshot("old limit route",serverRoot.resolve("config/chronicle/managed-block-limits.json")),"old Locations policy selector still opened");checks.add("Locations has no policy editor and forged old policy routes are denied");navigate(client,"Game setup",Page.SETUP);step=133;}
            case 133 -> {if(viewCount<=waitingFor||screen(client).state().page()!=Page.SETUP)return;screen(client).selectForTest("limits");waitingFor=viewCount;step=1330;}
            case 1330 -> {if(viewCount<=waitingFor||!screen(client).state().selected().equals("limits"))return;require(press(screen(client),"Choose block type\u2026"),"Game setup block type selector unavailable");waitingFor=viewCount;step=1331;}
            case 1331 -> {if(viewCount<=waitingFor||screen(client).state().picker()==null)return;require(screen(client).state().picker().kind().equals("BLOCK_LIMIT_TYPE")&&screen(client).state().picker().entries().size()==3,"Game setup selector did not list registered types");advance("Game setup owns the sole managed block policy selector");step=134;}
            case 134 -> {screen(client).selectForTest("quiet_below:relic_heart");waitingFor=viewCount;step++;}
            case 135 -> {if(viewCount<=waitingFor||!screen(client).state().selected().equals("limit:quiet_below:relic_heart"))return;require(screen(client).state().detail().controls().stream().noneMatch(c->c.action()==Action.BLOCK_LIMIT_SET&&c.enabled()),"structural Heart limit is editable");capture(client,"menu-fixed-heart-limit.png");advance("Heart policy is fixed and read only");step=1350;}
            case 1350 -> {require(press(screen(client),"Choose block type\u2026"),"type selector missing");waitingFor=viewCount;step=1351;}
            case 1351 -> {if(viewCount<=waitingFor||screen(client).state().picker()==null)return;screen(client).selectForTest("quiet_below:ancient_forge");waitingFor=viewCount;step=1352;}
            case 1352 -> {if(viewCount<=waitingFor||!screen(client).state().selected().equals("limit:quiet_below:ancient_forge"))return;require(press(screen(client),"Set limit\u2026"),"global limit value editor missing");screen(client).textForTest("2");require(press(screen(client),"Send"),"limit value input missing");require(screen(client).triggerForTest("Confirm: Set limit\u2026"),"limit review missing");waitingFor=viewCount;step=1353;}
            case 1353 -> {if(viewCount<=waitingFor)return;require(screen(client).state().success()&&managedLimitForTest("quiet_below:ancient_forge")==2,"Game setup did not save Forge cap2");checks.add("Game setup value edit durably changes the selected cap");require(press(screen(client),"Set limit\u2026"),"limit restoration editor missing");screen(client).textForTest("1");require(press(screen(client),"Send"),"limit restoration input missing");require(screen(client).triggerForTest("Confirm: Set limit\u2026"),"limit restoration review missing");waitingFor=viewCount;step=1354;}
            case 1354 -> {if(viewCount<=waitingFor)return;require(screen(client).state().success()&&managedLimitForTest("quiet_below:ancient_forge")==1,"Forge cap restoration failed");advance("Game setup value edit restores the cap without changing placed blocks");step=136;}
            case 136 -> {require(press(screen(client),"Back to game setup"),"global settings return unavailable");waitingFor=viewCount;step++;}
            case 137 -> awaitView(()->screen(client).state().page()==Page.SETUP&&screen(client).state().picker()==null&&screen(client).state().selected().equals("limits"),"limit detail returns to global settings without a Locations link");
            case 138 -> navigate(client,"Game setup",Page.SETUP);
            case 139 -> {require(screen(client).state().entries().stream().allMatch(e->e.kind().equals("TOOL")||e.kind().equals("LIMITS")),"geometry requirements remain in Game setup");screen(client).selectForTest("tool:quiet_below:heart");waitingFor=viewCount;advance("Game setup contains global tools only");}
            case 140 -> awaitView(()->screen(client).state().detail().title().equals("Heart weakness"),"Heart weakness remains a global rule");
            case 141 -> {snapshot("forged old binding",assignmentsFile);waitingFor=viewCount;ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.SETUP_CLEAR,Page.LOCATIONS,"binding:quiet_below:source_city","",screen(client).state().token(),0,"BINDINGS",""));step++;}
            case 142 -> awaitView(()->!screen(client).state().success()&&sameSnapshot("forged old binding",assignmentsFile),"obsolete binding packet cannot clear a region use");
            case 143 -> {client.player.networkHandler.sendChatCommand("chronicles setup assign \"quiet_below:relic_heart\" menu_smoke_block");waitingFor=viewCount;step++;}
            case 144 -> awaitView(this::boundHeart,"explicit legacy block assignment remains compatible");
            case 145 -> {snapshot("legacy inspect",assignmentsFile);client.player.networkHandler.sendChatCommand("chronicles setup inspect \"quiet_below:relic_heart\"");waitingFor=viewCount;step++;}
            case 146 -> awaitView(()->screen(client).state().success()&&screen(client).state().message().contains("outline")&&sameSnapshot("legacy inspect",assignmentsFile),"explicit legacy inspection stays private and read only");
            case 147 -> {client.player.networkHandler.sendChatCommand("chronicles setup clear \"quiet_below:relic_heart\"");waitingFor=viewCount;step++;}
            case 148 -> awaitView(()->!boundHeart()&&fileContains(authoringFile,"menu_smoke_block"),"explicit legacy clear retains geometry");
            case 149 -> navigate(client,"Locations",Page.LOCATIONS);
            case 150 -> {require(press(screen(client),"Block sites"),"Block sites unavailable");waitingFor=viewCount;step++;}
            case 151 -> awaitView(()->screen(client).state().filter().equals("BLOCK"),"reopened flat references after compatibility commands");
            case 152 -> {screen(client).selectForTest("manual:menu_smoke_block");waitingFor=viewCount;step++;}
            case 153 -> awaitView(()->screen(client).state().selected().equals("manual:menu_smoke_block"),"manual reference remains selectable");
            case 154 -> {require(press(screen(client),"Delete location"),"manual-only delete unavailable");waitingFor=viewCount;step++;}
            case 155 -> awaitView(()->!fileContains(authoringFile,"menu_smoke_block")&&fileContains(authoringFile,"menu_smoke_region"),"manual reference removal preserves unrelated geometry");
            case 156 -> {client.player.networkHandler.sendChatCommand("chronicles author block bind alias_one target");client.player.networkHandler.sendChatCommand("chronicles author block bind alias_two target");waitingFor=viewCount;step++;}
            case 157 -> awaitView(()->fileContains(authoringFile,"alias_one")&&fileContains(authoringFile,"alias_two"),"two legacy references share one physical position");
            case 158 -> {client.player.networkHandler.sendChatCommand("setblock 0 101 3 quiet_below:ancient_altar");waitingFor=viewCount;step++;}
            case 159 -> awaitView(()->screen(client).state().entries().size()==1&&screen(client).state().entries().getFirst().kind().equals("MANAGED"),"discovery deduplicates both aliases into one physical row");
            case 160 -> {screen(client).selectForTest(screen(client).state().entries().getFirst().id());waitingFor=viewCount;step++;}
            case 161 -> awaitView(()->screen(client).state().selected().startsWith("managed:"),"selected managed position with multiple references");
            case 162 -> {require(press(screen(client),"Manual references\u2026"),"multiple-reference selector unavailable");waitingFor=viewCount;step++;}
            case 163 -> awaitView(()->screen(client).state().picker()!=null&&screen(client).state().picker().kind().equals("MANUAL_REFERENCE")&&screen(client).state().picker().entries().size()==2,"both legacy aliases remain discoverable");
            case 164 -> {screen(client).selectForTest("manual:alias_two");waitingFor=viewCount;step++;}
            case 165 -> awaitView(()->screen(client).state().selected().equals("manual:alias_two")&&screen(client).state().detail().title().equals("alias_two"),"alias picker opens the exact selected manual detail");
            case 166 -> {require(press(screen(client),"Delete location"),"selected alias deletion unavailable");waitingFor=viewCount;step++;}
            case 167 -> awaitView(()->!fileContains(authoringFile,"alias_two")&&fileContains(authoringFile,"alias_one")&&screen(client).state().entries().size()==1&&screen(client).state().entries().getFirst().kind().equals("MANAGED"),"deleting one alias retains the other and its managed quota");
            case 168 -> {screen(client).selectForTest(screen(client).state().entries().getFirst().id());waitingFor=viewCount;step++;}
            case 169 -> awaitView(()->screen(client).state().detail().facts().stream().anyMatch(f->f.label().equals("Manual reference")&&f.value().equals("alias_one")),"remaining alias retains its scoped managed detail");
            case 170 -> {require(press(screen(client),"Delete manual reference"),"last manual reference deletion unavailable");waitingFor=viewCount;step++;}
            case 171 -> awaitView(()->!fileContains(authoringFile,"alias_one")&&screen(client).state().entries().size()==1&&screen(client).state().entries().getFirst().kind().equals("MANAGED"),"deleting all manual references cannot free physical quota");
            case 172 -> step=210;
            case 173 -> awaitView(()->!screen(client).hasTextPromptForTest()&&sameSnapshot("manual cancelled",authoringFile),"changed looked-at target closes the old naming prompt without a mutation");
            case 174 -> {client.player.networkHandler.sendChatCommand("tp @s 0.5 100 0.5 0 0");waitingFor=viewCount;step++;}
            case 175 -> awaitView(()->client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit&&hit.getBlockPos().equals(new net.minecraft.util.math.BlockPos(0,101,3)),"restored the actual manual-reference target");
            case 176 -> {require(press(screen(client),"Bind looked-at block\u2026"),"manual block naming unavailable after target change");screen(client).textForTest("menu_smoke_block");require(screen(client).triggerForTest("Send"),"manual reference Send unavailable");waitingFor=viewCount;step=125;}
            default -> throw new IllegalStateException("unexpected flat block test step "+step);
        }
    }
    private void driveRegionUses(MinecraftClient client){
        switch(step){
            case 210 -> navigate(client,"Regions",Page.REGIONS);
            case 211 -> {screen(client).selectForTest("menu_smoke_region");waitingFor=viewCount;step++;}
            case 212 -> awaitView(()->screen(client).state().selected().equals("menu_smoke_region")&&fact(client,"Used by").contains("Current"),"region usage is contextual and current");
            case 213 -> {snapshot("region clear cancelled",assignmentsFile);require(press(screen(client),"Clear use\u2026"),"clear region use unavailable");waitingFor=viewCount;step++;}
            case 214 -> awaitView(()->screen(client).state().picker()!=null&&screen(client).state().picker().kind().equals("REGION_BOUND")&&screen(client).state().picker().entries().size()==1,"clear picker contains only the selected region's assigned purpose");
            case 215 -> {require(press(screen(client),"Cancel selection"),"region picker cancel unavailable");waitingFor=viewCount;step++;}
            case 216 -> awaitView(()->screen(client).state().picker()==null&&sameSnapshot("region clear cancelled",assignmentsFile),"cancelled region-use picker preserves assignment");
            case 217 -> {require(press(screen(client),"Clear use\u2026"),"clear region use unavailable");waitingFor=viewCount;step++;}
            case 218 -> awaitView(()->screen(client).state().picker()!=null,"reopened region clear picker");
            case 219 -> {screen(client).selectForTest("quiet_below:source_city");require(screen(client).triggerForTest("Confirm: Clear use\u2026"),"clear region use review unavailable");waitingFor=viewCount;step++;}
            case 220 -> awaitView(()->!fileContains(assignmentsFile,"quiet_below:source_city")&&fileContains(authoringFile,"menu_smoke_region"),"clearing a use retains the saved region");
            case 221 -> {snapshot("forged region purpose",assignmentsFile);waitingFor=viewCount;ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.REGION_ASSIGN,Page.REGIONS,"menu_smoke_region","quiet_below:relic_heart",screen(client).state().token(),0,"",""));step++;}
            case 222 -> awaitView(()->!screen(client).state().success()&&sameSnapshot("forged region purpose",assignmentsFile),"forged block purpose rejected by live region kind validation");
            case 223 -> {require(press(screen(client),"Use for\u2026"),"region use unavailable after denied packet");waitingFor=viewCount;step++;}
            case 224 -> awaitView(()->screen(client).state().picker()!=null,"reopened purpose picker after denied packet");
            case 225 -> {screen(client).selectForTest("quiet_below:source_city");require(screen(client).triggerForTest("Confirm: Use for\u2026"),"region reassignment review unavailable");waitingFor=viewCount;step++;}
            case 226 -> awaitView(()->fact(client,"Used by").contains("Current"),"explicit region reassignment restores current pin");
            case 227 -> {snapshot("region old pin",assignmentsFile);snapshot("region old bounds",authoringFile);require(press(screen(client),"Edit region"),"existing region edit unavailable");waitingFor=viewCount;step++;}
            case 228 -> awaitView(()->screen(client).state().detail().region()!=null,"existing region opens its current draft");
            case 229 -> {require(press(screen(client),"Set corner 1"),"single-corner editor unavailable");step++;}
            case 230 -> {screen(client).textForTest("2 64 1");require(screen(client).triggerForTest("Send"),"single-corner position submit unavailable");waitingFor=viewCount;step++;}
            case 231 -> awaitView(()->screen(client).state().detail().region()!=null&&screen(client).state().detail().region().first().equals("2 64 1"),"one corner changes without rebuilding the region");
            case 232 -> {require(press(screen(client),"Save region changes"),"region update save unavailable");waitingFor=viewCount;step++;}
            case 233 -> awaitView(()->!sameSnapshot("region old bounds",authoringFile)&&sameSnapshot("region old pin",assignmentsFile)&&fact(client,"Used by").contains("Stale"),"changed bounds leave the prior assignment explicitly stale");
            case 234 -> {require(press(screen(client),"Use for\u2026"),"stale region update unavailable");waitingFor=viewCount;step++;}
            case 235 -> awaitView(()->screen(client).state().picker()!=null&&screen(client).state().picker().entries().stream().anyMatch(e->e.id().equals("quiet_below:source_city")&&e.description().contains("Stale here")),"purpose picker identifies the selected region's stale pin");
            case 236 -> {screen(client).selectForTest("quiet_below:source_city");require(screen(client).triggerForTest("Confirm: Use for\u2026"),"stale region update review unavailable");waitingFor=viewCount;step++;}
            case 237 -> awaitView(()->!sameSnapshot("region old pin",assignmentsFile)&&fact(client,"Used by").contains("Current"),"explicit use updates only the selected purpose to the saved revision");
            case 238 -> {capture(client,"menu-regions-usage.png");advance("captured the single-list Regions workflow");}
            case 239 -> {snapshot("forged setup region clear",assignmentsFile);waitingFor=viewCount;ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.SETUP_CLEAR,Page.SETUP,"quiet_below:source_city","",screen(client).state().token(),0,"",""));step++;}
            case 240 -> awaitView(()->!screen(client).state().success()&&sameSnapshot("forged setup region clear",assignmentsFile),"raw Game setup packet cannot mutate a region use");
            case 241 -> {client.player.networkHandler.sendChatCommand("chronicles setup inspect \"quiet_below:source_city\"");waitingFor=viewCount;step++;}
            case 242 -> awaitView(()->screen(client).state().success()&&screen(client).state().message().contains("outline")&&sameSnapshot("forged setup region clear",assignmentsFile),"explicit legacy region inspect remains compatible and read only");
            case 243 -> finish(client,true,"completed "+checks.size()+" native operator checks");
            default -> throw new IllegalStateException("unexpected region menu step "+step);
        }
    }
    private String fact(MinecraftClient client,String name){return screen(client).state().detail().facts().stream().filter(f->f.label().equals(name)).map(f->f.value()).findFirst().orElse("");}
    private boolean boundHeart(){try{return java.util.stream.StreamSupport.stream(com.google.gson.JsonParser.parseString(Files.readString(assignmentsFile)).getAsJsonObject().getAsJsonArray("bindings").spliterator(),false).anyMatch(e->e.getAsJsonObject().get("requirementId").getAsString().equals("quiet_below:relic_heart"));}catch(IOException error){throw new IllegalStateException(error);}}

    private boolean buildingFact(MinecraftClient client, boolean enabled) {
        var detail=screen(client).state().detail();
        return detail!=null&&detail.facts().stream().anyMatch(f->f.label().equals("Building Mode")&&f.value().startsWith(enabled?"On - ":"Off - "));
    }
    private void driveBuildingMode(MinecraftClient client) {
        switch(step) {
            case 20000 -> {screen(client).selectForTest("play");waitingFor=viewCount;step++;}
            case 20001 -> {if(viewCount<=waitingFor||!buildingFact(client,false))return;require(press(screen(client),"Enable Building Mode"),"confirmed global build control unavailable");waitingFor=viewCount;step++;}
            case 20002 -> {if(viewCount<=waitingFor||!buildingFact(client,true))return;capture(client,"menu-global-building-on.png");advance("Session explicitly enables global Building Mode");}
            case 20003 -> {require(press(screen(client),"Disable Building Mode"),"global build off unavailable");waitingFor=viewCount;step++;}
            case 20004 -> awaitView(()->buildingFact(client,false),"Session disables global Building Mode");
            case 20005 -> {waitingFor=viewCount;ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.SESSION_BUILD_ON,Page.LOCATIONS,"play","",screen(client).state().token(),0,"BLOCK",""));step++;}
            case 20006 -> {if(viewCount<=waitingFor||screen(client).state().success())return;require(press(screen(client),"Session"),"Session unavailable after denied wrong-page build request");waitingFor=viewCount;step++;}
            case 20007 -> {if(viewCount<=waitingFor||screen(client).state().page()!=Page.SESSION||!buildingFact(client,false))return;checks.add("Global Building Mode is explicit and wrong-page requests cannot enable it");step=3;}
            default -> throw new IllegalStateException("unexpected global Building Mode menu step "+step);
        }
    }

    private void drivePlayer(MinecraftClient client) {
        switch (step) {
            case 0 -> { openMenu(client); advance("ordinary player opened own menu"); }
            case 1 -> awaitScreen(client, Page.PLAYERS, "ordinary player receives Players only");
            case 2 -> {
                CoreMenuScreen screen = screen(client);
                require(!screen.state().operator(), "ordinary player received an operator menu snapshot");
                require(!screen.state().editing(), "ordinary player received editing state");
                snapshot("non-operator before forged mutation", authoringFile);
                long token = screen.state().token();
                waitingFor = viewCount;
                ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.EDIT_ON, Page.REGIONS, "", "", token, 0, "", ""));
                step++;
            }
            case 3 -> awaitView(() -> !screen(client).state().success() && !screen(client).state().operator()
                    && sameSnapshot("non-operator before forged mutation", authoringFile), "forged operator mutation denied without authoring change");
            case 4 -> {
                if (!Files.isRegularFile(output.resolve("preview-chat-checked"))) return;
                if (++stableTicks < 20) return;
                require(chatCount(CITY_MESSAGE) == 0 && chatCount(DISTANT_MESSAGE) == 0, "private preview leaked to another player");
                advance("another online player receives neither cue preview message");
            }
            case 5 -> {
                snapshotSize("non-op ecology",serverRoot.resolve("world/quiet-below/ecology.jsonl"));waitingFor=viewCount;
                ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.TOOL_ACTION,Page.SETUP,"tool:quiet_below:ecology","enable",screen(client).state().token(),0,"",""));step++;
            }
            case 6 -> awaitView(()->!screen(client).state().success()&&!screen(client).state().operator()&&sameSize("non-op ecology",serverRoot.resolve("world/quiet-below/ecology.jsonl")),"forged non-operator campaign tool action denied without a journal write");
            case 7 -> {waitingFor=viewCount;ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.TOOL_ACTION,Page.SETUP,"tool:quiet_below:ownership","cleanup_on",screen(client).state().token(),0,"",""));step++;}
            case 8 -> awaitView(()->!screen(client).state().success()&&!screen(client).state().operator()&&sameSize("non-op ecology",serverRoot.resolve("world/quiet-below/ecology.jsonl")),"forged non-operator cleanup denied without a journal write");
            case 9 -> {snapshot("non-op block bindings",assignmentsFile);waitingFor=viewCount;ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.SETUP_ASSIGN,Page.LOCATIONS,"binding:quiet_below:relic_heart","menu_smoke_region",screen(client).state().token(),0,"BINDINGS",""));step++;}
            case 10 -> awaitView(()->!screen(client).state().success()&&!screen(client).state().operator()&&sameSnapshot("non-op block bindings",assignmentsFile),"forged non-operator block binding denied without assignment changes");
            case 11 -> {if(!Files.isRegularFile(output.resolve("prompt-disconnect-ready")))return;waitingFor=viewCount;ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.SESSION_BUILD_ON,Page.SESSION,"play","",screen(client).state().token(),0,"",""));step=13;}
            case 13 -> awaitView(()->!screen(client).state().success()&&!screen(client).state().operator(),"forged non-operator global Building Mode request is denied");
            case 14 -> {checks.add("disconnect while the operator has an unsaved naming prompt");finish(client, true, "completed " + checks.size() + " ordinary-player checks");}
            default -> throw new IllegalStateException("unexpected ordinary-player menu step "+step);
        }
    }

    /** Extra logical-size captures deliberately make no durable menu mutation. */
    private void driveLayout(MinecraftClient client) {
        switch (step) {
            case 0 -> { openMenu(client); advance("opened native layout capture menu"); }
            case 1 -> awaitScreen(client, Page.SESSION, "layout session page rendered");
            case 2 -> { capture(client, "menu-layout-" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight() + ".png"); advance("captured requested logical layout"); }
            case 3 -> navigate(client, "Players", Page.PLAYERS);
            case 4 -> navigate(client, "Events", Page.EVENTS);
            case 5 -> navigate(client, "Chests", Page.CHESTS);
            case 6 -> navigate(client, "Loot Tables", Page.LOOT_TABLES);
            case 7 -> navigate(client, "Regions", Page.REGIONS);
            case 8 -> { screen(client).selectForTest("menu_smoke_region"); waitingFor = viewCount; step++; }
            case 9 -> awaitView(() -> screen(client).state().detail() != null && screen(client).state().detail().id().equals("menu_smoke_region"), "layout region detail rendered");
            case 10 -> { capture(client, "menu-layout-region-" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight() + ".png"); advance("captured meaningful region detail"); }
            case 11 -> navigate(client, "Locations", Page.LOCATIONS);
            case 12 -> navigate(client, "Game setup", Page.SETUP);
            case 13 -> {screen(client).selectForTest("tool:quiet_below:heart");waitingFor=viewCount;step++;}
            case 14 -> awaitView(()->screen(client).state().detail().title().equals("Heart weakness"),"layout Heart weakness settings rendered");
            case 15 -> {capture(client,"menu-layout-heart-weakness.png");screen(client).toggleDebugForTest();step=1500;}
            case 1500 -> {require(screen(client).debugForTest(),"layout Debug view not enabled");capture(client,"menu-layout-heart-debug.png");screen(client).toggleDebugForTest();advance("captured clean and debug global settings at requested logical size");step=16;}
            case 16 -> navigate(client,"Locations",Page.LOCATIONS);
            case 17 -> {require(press(screen(client),"Block sites"),"layout Block sites missing");waitingFor=viewCount;step++;}
            case 18 -> awaitView(()->screen(client).state().filter().equals("BLOCK"),"layout saved block sites rendered");
            case 19 -> {capture(client,"menu-layout-block-sites.png");require(screen(client).state().detail().controls().stream().noneMatch(c->c.action()==Action.BLOCK_LIMITS||c.action()==Action.BLOCK_LIMIT_SET),"layout Locations policy control remains");navigate(client,"Game setup",Page.SETUP);step=20;}
            case 20 -> {if(viewCount<=waitingFor||screen(client).state().page()!=Page.SETUP)return;screen(client).selectForTest("limits");waitingFor=viewCount;step=2000;}
            case 2000 -> {if(viewCount<=waitingFor||!screen(client).state().selected().equals("limits"))return;require(press(screen(client),"Choose block type\u2026"),"layout Game setup type selector missing");waitingFor=viewCount;step=2001;}
            case 2001 -> {if(viewCount<=waitingFor||screen(client).state().picker()==null)return;require(screen(client).state().picker().kind().equals("BLOCK_LIMIT_TYPE"),"layout policy selector missing");advance("layout managed type selector rendered only under Game setup");step=21;}
            case 21 -> {screen(client).selectForTest("quiet_below:relic_heart");waitingFor=viewCount;step++;}
            case 22 -> awaitView(()->screen(client).state().selected().equals("limit:quiet_below:relic_heart"),"layout fixed Heart limit detail rendered");
            case 23 -> {capture(client,"menu-layout-fixed-limit.png");advance("captured generic limit detail at requested logical size");}
            case 24 -> finish(client, true, "completed " + checks.size() + " native layout checks");
            default -> throw new IllegalStateException("unexpected layout menu step "+step);
        }
    }

    private void openMenu(MinecraftClient client) {
        client.player.networkHandler.sendChatCommand("chronicles");
    }

    private void navigate(MinecraftClient client, String label, Page expected) {
        CoreMenuScreen screen = screen(client);
        if (screen.pending()) return;
        if (screen.state().page() != expected) {
            require(press(screen, label), "navigation button unavailable: " + label);
            return;
        }
        advance("opened " + expected.name() + " navigation");
    }

    private void awaitScreen(MinecraftClient client, Page expected, String check) {
        if (!(client.currentScreen instanceof CoreMenuScreen screen) || screen.pending() || screen.state().page() != expected) return;
        advance(check);
    }

    private void awaitView(Checked condition, String check) {
        if (viewCount <= waitingFor || !condition.test()) return;
        advance(check);
    }

    private static CoreMenuScreen screen(MinecraftClient client) {
        if (!(client.currentScreen instanceof CoreMenuScreen screen)) throw new IllegalStateException("native Core menu screen is not open");
        return screen;
    }

    /** Finds controls that are intentionally below the compact detail fold. */
    private static boolean press(CoreMenuScreen screen, String label) {
        for (int attempt = 0; attempt < 60; attempt++) screen.scrollDetailForTest(1);
        for (int attempt = 0; attempt < 30; attempt++) {
            if (screen.triggerForTest(label)) {
                // Confirmations are a local modal step: press the original
                // action once, then its explicit confirmation if it appeared.
                screen.triggerForTest("Confirm: " + label);
                return true;
            }
            screen.scrollDetailForTest(-1);
        }
        if(List.of("Points","Block sites","Saved sites","Game bindings").contains(label)&&screen.triggerForTest("Back to list"))return screen.triggerForTest(label);
        return false;
    }

    private void verifyCleanDebug(MinecraftClient client) {
        var screen=screen(client);var before=screen.state();
        require(!screen.debugForTest(),"a fresh client did not default to Clean view");
        require(!screen.debugVisibleFactLabelsForTest().contains("Active play"),"Clean view exposes the diagnostic clock");
        screen.toggleDebugForTest();require(screen.debugForTest()&&screen.debugVisibleFactLabelsForTest().contains("Active play"),"Debug view does not reveal diagnostics");
        require(screen.state()==before,"Debug toggle changed the authoritative view");
        screen.toggleDebugForTest();require(!screen.debugForTest()&&!screen.debugVisibleFactLabelsForTest().contains("Active play")&&screen.state()==before,"Clean toggle changed authority or failed to hide diagnostics");
        checks.add("Clean and Debug switch facts without changing server authority");
    }
    private int managedLimitForTest(String type){
        try{var overrides=new com.google.gson.Gson().fromJson(Files.readString(serverRoot.resolve("config/chronicle/managed-block-limits.json")),com.google.gson.JsonObject.class).getAsJsonObject("overrides");
            return overrides.has(type)?overrides.get(type).getAsInt():ChronicleCore.managedBlocks().get(type).orElseThrow().defaultLimit();}
        catch(Exception error){throw new IllegalStateException("missing durable managed limit override",error);}
    }
    private void advance(String check) { checks.add(check); step++; }
    private long chatCount(String text) { return chatMessages.stream().filter(text::equals).count(); }
    private boolean previewChatArrived(String text) { require(chatCount(text) > previewMessageCount, "preview did not send the cue text to chat: " + text); return true; }

    private void requireFixture(MinecraftClient client) {
        ServerInfo server = client.getCurrentServerEntry();
        require(server != null && server.address.equals("127.0.0.1:25575"), "refusing menu-smoke mutations outside exact isolated fixture address");
        require(client.player.getUuid().equals(role.equals("A") ? GM : PLAYER), "connected UUID does not match deterministic menu-smoke account");
    }

    private void capture(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(output.toFile(), name, client.getFramebuffer(), 1, ignored -> { });
    }

    private void snapshot(String key, Path path) {
        try { snapshots.put(key, Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : "<absent>"); }
        catch (IOException error) { throw new IllegalStateException("could not snapshot " + path, error); }
    }
    // The live append log is exclusively locked on Windows. An empty, monotonic
    // append-only file staying at zero bytes proves these actions append nothing.
    private void snapshotSize(String key,Path path) {
        try {long size=Files.size(path);require(size==0,"fixture ecology should be unconfigured");snapshots.put(key,Long.toString(size));}
        catch(IOException e){throw new IllegalStateException("Could not stat ecology journal",e);}
    }
    private boolean sameSize(String key,Path path) {
        try{return snapshots.get(key).equals(Long.toString(Files.size(path)));}
        catch(IOException e){throw new IllegalStateException("Could not stat ecology journal",e);}
    }
    private boolean sameSnapshot(String key, Path path) {
        try { return snapshots.get(key).equals(Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : "<absent>"); }
        catch (IOException error) { throw new IllegalStateException("could not read " + path, error); }
    }
    private static boolean fileContains(Path path, String expected) {
        try { return Files.readString(path, StandardCharsets.UTF_8).contains(expected); }
        catch (IOException error) { throw new IllegalStateException("could not read " + path, error); }
    }

    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (complete) return;
        complete = true;
        JsonObject result = new JsonObject();
        result.addProperty("result", pass ? "PASS" : "FAIL");
        result.addProperty("role", role);
        result.addProperty("detail", detail == null ? "" : detail);
        result.addProperty("views", viewCount);
        result.addProperty("step", step);
        JsonArray rejected = new JsonArray(); rejectedViews.forEach(rejected::add); result.add("rejectedViews", rejected);
        if(!pass){result.add("lastView",new com.google.gson.Gson().toJsonTree(lastView));capture(client,"menu-failure-"+step+".png");}
        JsonArray asserted = new JsonArray(); checks.forEach(asserted::add); result.add("checks", asserted);
        try { Files.writeString(output.resolve("role-" + role + ".json"), result + "\n", StandardCharsets.UTF_8); }
        catch (IOException error) { ChronicleCore.LOGGER.error("Could not write Core menu smoke result", error); }
        client.scheduleStop();
    }

    private static UUID offlineUuid(String name) { return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void writeSetupFailure(Throwable error) {
        try {
            if (OUTPUT_ENV == null) return;
            Path directory = Path.of(OUTPUT_ENV).toAbsolutePath().normalize();
            if (SERVER_ENV != null && directory.startsWith(Path.of(SERVER_ENV).toAbsolutePath().normalize())) return;
            Files.createDirectories(directory);
            JsonObject result = new JsonObject(); result.addProperty("result", "FAIL"); result.addProperty("role", String.valueOf(ROLE_ENV));
            result.addProperty("detail", "setup: " + error.getMessage());
            Files.writeString(directory.resolve("role-" + ROLE_ENV + ".json"), result + "\n", StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) { }
    }
    @FunctionalInterface private interface Checked { boolean test(); }
}
