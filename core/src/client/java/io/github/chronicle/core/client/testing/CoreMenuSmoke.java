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
        if(step>=110){driveBlockBindings(client);return;}
        switch (step) {
            case 0 -> { openMenu(client); advance("operator opened the native menu"); }
            case 1 -> awaitScreen(client, Page.SESSION, "Session");
            case 2 -> { capture(client, "menu-operator-" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight() + ".png"); advance("captured native menu layout"); }
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
            case 33 -> { capture(client, "menu-event-detail-" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight() + ".png"); snapshot("unconfigured event before preview", playFile); previewMessageCount = chatCount(CITY_MESSAGE); require(press(screen(client), "Preview event"), "event preview unavailable"); waitingFor = viewCount; step++; }
            case 34 -> awaitView(() -> previewChatArrived(CITY_MESSAGE) && sameSnapshot("unconfigured event before preview", playFile), "paused unconfigured City preview sends private chat without changing play ledger");
            case 35 -> {
                // This action uses the normal screen callback and server route;
                // no public delivery or completion is asserted from a sample.
                previewMessageCount = chatCount(CITY_MESSAGE);
                if (press(screen(client), "Play preview — only me")) { waitingFor = viewCount; step++; }
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
            case 49 -> { require(press(screen(client), "Game setup"), "Game setup navigation unavailable"); waitingFor = viewCount; step++; }
            case 50 -> awaitView(() -> screen(client).state().page() == Page.SETUP, "opened Game setup");
            case 51 -> { screen(client).selectForTest("quiet_below:source_city"); waitingFor = viewCount; step++; }
            case 52 -> awaitView(() -> screen(client).state().detail() != null, "selected typed setup requirement");
            case 53 -> { require(press(screen(client), "Choose existing site…"), "setup assignment picker unavailable"); waitingFor = viewCount; step++; }
            case 54 -> awaitView(() -> screen(client).state().picker() != null && "REGION".equals(screen(client).state().picker().kind()), "setup only offers matching region sites");
            case 55 -> { screen(client).selectForTest("menu_smoke_region"); require(screen(client).triggerForTest("Confirm: Choose existing site…"), "setup assignment review unavailable"); waitingFor = viewCount; step++; }
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
            case 95 -> { snapshot("distant preview", playFile); previewMessageCount = chatCount(DISTANT_MESSAGE); require(press(screen(client), "Preview event"), "Distant preview unavailable"); waitingFor = viewCount; step++; }
            case 96 -> awaitView(() -> previewChatArrived(DISTANT_MESSAGE) && sameSnapshot("distant preview", playFile), "paused Distant preview sends private chat without changing play ledger");
            case 97 -> { previewMessageCount = chatCount(DISTANT_MESSAGE); require(press(screen(client), "Play preview — only me"), "Distant playback unavailable"); waitingFor = viewCount; step++; }
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
            default -> finish(client, true, "completed " + checks.size() + " native operator checks");
        }
    }

    private void driveBlockBindings(MinecraftClient client){
        switch(step){
            case 110 -> navigate(client,"Locations",Page.LOCATIONS);
            case 111 -> {require(press(screen(client),"Block sites"),"Block sites unavailable");waitingFor=viewCount;step++;}
            case 112 -> awaitView(()->"BLOCK".equals(screen(client).state().filter())&&screen(client).state().entries().isEmpty(),"saved block sites are empty and separate from game requirements");
            case 113 -> {require(press(screen(client),"Game bindings"),"Game bindings selector missing");waitingFor=viewCount;step++;}
            case 114 -> {
                var v=screen(client).state();if(viewCount<=waitingFor||!v.filter().equals("BINDINGS"))return;
                require(v.entries().size()==3&&v.entries().stream().allMatch(e->e.kind().equals("BINDING")&&e.id().startsWith("binding:")),"block requirements are missing or mixed with saved geometry");
                require(v.detail().controls().stream().filter(c->c.action()==Action.SETUP_ASSIGN).noneMatch(c->c.enabled()),"binding edit allowed without author mode");
                snapshot("bindings before creation",assignmentsFile);snapshot("geometry before creation",authoringFile);
                capture(client,"menu-empty-game-bindings.png");advance("all three registered block requirements exist before saved anchors and are permission gated");
            }
            case 115 -> {screen(client).selectForTest("binding:quiet_below:relic_heart");waitingFor=viewCount;step++;}
            case 116 -> awaitView(()->screen(client).state().selected().equals("binding:quiet_below:relic_heart"),"selected Heart binding in Block sites");
            case 117 -> {require(press(screen(client),"Enable editing"),"editing unavailable");waitingFor=viewCount;step++;}
            case 118 -> {awaitView(()->screen(client).state().editing()&&screen(client).state().selected().equals("binding:quiet_below:relic_heart"),"binding editing is acknowledged by server without changing selection");if(step==119)stableTicks=0;}
            case 119 -> {
                if(stableTicks++==0){capture(client,"menu-binding-before-picker.png");return;}if(stableTicks<20)return;
                require(press(screen(client),"Choose existing site…"),"binding picker missing: "+bindingLayout(screen(client)));waitingFor=viewCount;step++;
            }
            case 120 -> awaitView(()->screen(client).state().picker()!=null&&screen(client).state().picker().kind().equals("BLOCK")&&screen(client).state().picker().entries().isEmpty(),"empty binding picker offers only saved anchors");
            case 121 -> {require(press(screen(client),"Cancel selection"),"empty picker cancel missing");waitingFor=viewCount;step++;}
            case 122 -> awaitView(()->screen(client).state().picker()==null&&screen(client).state().selected().equals("binding:quiet_below:relic_heart")&&sameSnapshot("bindings before creation",assignmentsFile),"empty picker cancellation preserves originating binding");
            case 123 -> {require(press(screen(client),"Create a block site…"),"binding creation route missing");waitingFor=viewCount;step++;}
            case 124 -> awaitView(()->screen(client).state().filter().equals("BLOCK"),"binding creation routes to ordinary saved sites");
            case 125 -> {require(press(screen(client),"Bind looked-at block…"),"normal block creation missing");step++;}
            case 126 -> {require(press(screen(client),"Cancel"),"creation cancellation missing");waitingFor=viewCount;step++;}
            case 127 -> awaitView(()->screen(client).state().filter().equals("BINDINGS")&&screen(client).state().selected().equals("binding:quiet_below:relic_heart")&&sameSnapshot("bindings before creation",assignmentsFile)&&sameSnapshot("geometry before creation",authoringFile),"creation cancellation returns to binding without saving geometry or assignment");
            case 128 -> {
                screen(client).close();client.player.networkHandler.sendChatCommand("setblock 0 99 0 minecraft:stone");client.player.networkHandler.sendChatCommand("setblock 0 101 3 minecraft:stone");client.player.networkHandler.sendChatCommand("tp @s 0.5 100 0.5 0 0");step++;
            }
            case 129 -> {
                if(Math.abs(client.player.getY()-100)>.05||!(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit)||!hit.getBlockPos().equals(new net.minecraft.util.math.BlockPos(0,101,3)))return;
                openMenu(client);advance("prepared actual looked-at vanilla block for generic site creation");
            }
            case 130 -> awaitScreen(client,Page.SESSION,"reopened menu over block fixture");
            case 131 -> navigate(client,"Locations",Page.LOCATIONS);
            case 132 -> {require(press(screen(client),"Block sites"),"Block sites unavailable");waitingFor=viewCount;step++;}
            case 133 -> awaitView(()->screen(client).state().filter().equals("BLOCK"),"saved block sites selected");
            case 134 -> {require(press(screen(client),"Game bindings"),"Game bindings unavailable");waitingFor=viewCount;step++;}
            case 135 -> awaitView(()->screen(client).state().filter().equals("BINDINGS"),"binding list reopened");
            case 136 -> {screen(client).selectForTest("binding:quiet_below:relic_heart");waitingFor=viewCount;step++;}
            case 137 -> awaitView(()->screen(client).state().selected().equals("binding:quiet_below:relic_heart"),"Heart binding selected for new site");
            case 138 -> {require(press(screen(client),"Create a block site…"),"binding create unavailable");waitingFor=viewCount;step++;}
            case 139 -> awaitView(()->screen(client).state().filter().equals("BLOCK"),"ordinary creation route reused");
            case 140 -> {require(press(screen(client),"Bind looked-at block…"),"looked block create missing");step++;}
            case 141 -> {screen(client).textForTest("menu_smoke_block");require(screen(client).triggerForTest("Send"),"block name submit missing");waitingFor=viewCount;step++;}
            case 142 -> awaitView(()->screen(client).state().filter().equals("BINDINGS")&&screen(client).state().selected().equals("binding:quiet_below:relic_heart")&&fileContains(authoringFile,"menu_smoke_block")&&sameSnapshot("bindings before creation",assignmentsFile),"new block site returns to original binding without automatic assignment");
            case 143 -> {require(press(screen(client),"Choose existing site…"),"new anchor picker missing");waitingFor=viewCount;step++;}
            case 144 -> awaitView(()->screen(client).state().picker()!=null&&screen(client).state().picker().entries().stream().anyMatch(e->e.id().equals("menu_smoke_block"))&&screen(client).state().picker().entries().stream().allMatch(e->e.kind().equals("BLOCK")),"matching block picker includes new anchor and excludes points and regions");
            case 145 -> {screen(client).selectForTest("menu_smoke_block");require(screen(client).triggerForTest("Confirm: Choose existing site…"),"binding explicit confirmation missing");waitingFor=viewCount;step++;}
            case 146 -> awaitView(()->boundHeart()&&fact(client,"Reference").equals("Current"),"binding pins existing anchor revision through current assignment service");
            case 147 -> {snapshot("bound before show",assignmentsFile);require(press(screen(client),"Show assigned site"),"binding show unavailable");waitingFor=viewCount;step++;}
            case 148 -> awaitView(()->screen(client).state().success()&&sameSnapshot("bound before show",assignmentsFile),"binding show is private and does not mutate assignments");
            case 149 -> {snapshot("anchor before replace",authoringFile);screen(client).close();client.player.networkHandler.sendChatCommand("chronicles author block replace menu_smoke_block target");step++;}
            case 150 -> {if(sameSnapshot("anchor before replace",authoringFile))return;openMenu(client);advance("existing anchor replacement keeps identity and changes revision");}
            case 151 -> awaitScreen(client,Page.SESSION,"reopened after anchor revision change");
            case 152 -> navigate(client,"Locations",Page.LOCATIONS);
            case 153 -> {require(press(screen(client),"Block sites"),"Block sites unavailable");waitingFor=viewCount;step++;}
            case 154 -> awaitView(()->screen(client).state().filter().equals("BLOCK"),"saved sites reopened");
            case 155 -> {require(press(screen(client),"Game bindings"),"binding selector missing");waitingFor=viewCount;step++;}
            case 156 -> awaitView(()->screen(client).state().filter().equals("BINDINGS"),"binding view reopened for stale reference");
            case 157 -> {screen(client).selectForTest("binding:quiet_below:relic_heart");waitingFor=viewCount;step++;}
            case 158 -> awaitView(()->screen(client).state().selected().equals("binding:quiet_below:relic_heart")&&!fact(client,"Reference").isBlank()&&!fact(client,"Reference").equals("Current"),"revised anchor exposes stale pinned binding without silent repair");
            case 159 -> {capture(client,"menu-stale-game-binding.png");require(press(screen(client),"Choose existing site…"),"stale binding cannot choose replacement");waitingFor=viewCount;step++;}
            case 160 -> awaitView(()->screen(client).state().picker()!=null,"stale binding can explicitly revalidate through anchor picker");
            case 161 -> {screen(client).selectForTest("menu_smoke_block");require(screen(client).triggerForTest("Confirm: Choose existing site…"),"revalidation confirmation missing");waitingFor=viewCount;step++;}
            case 162 -> awaitView(()->screen(client).state().picker()==null&&fact(client,"Reference").equals("Current")&&!sameSnapshot("bound before show",assignmentsFile),"explicit assignment revalidates updated anchor revision");
            case 163 -> {require(press(screen(client),"Clear assignment"),"binding clear unavailable");waitingFor=viewCount;step++;}
            case 164 -> awaitView(()->!boundHeart()&&fileContains(authoringFile,"menu_smoke_block"),"clearing game binding retains saved block geometry");
            case 165 -> {snapshot("abandoned route bindings",assignmentsFile);require(press(screen(client),"Create a block site…"),"abandonment create route missing");waitingFor=viewCount;step++;}
            case 166 -> awaitView(()->screen(client).state().filter().equals("BLOCK"),"entered creation route before abandoning it");
            case 167 -> navigate(client,"Players",Page.PLAYERS);
            case 168 -> navigate(client,"Locations",Page.LOCATIONS);
            case 169 -> {require(press(screen(client),"Block sites"),"saved sites missing after abandoned route");waitingFor=viewCount;step++;}
            case 170 -> awaitView(()->screen(client).state().filter().equals("BLOCK"),"ordinary saved sites reopened after leaving creation route");
            case 171 -> {require(press(screen(client),"Bind looked-at block…"),"ordinary block creation unavailable after abandoned route");step++;}
            case 172 -> {screen(client).textForTest("menu_smoke_abandoned_block");require(screen(client).triggerForTest("Send"),"ordinary block name submit missing");waitingFor=viewCount;step++;}
            case 173 -> {
                if(viewCount<=waitingFor||!fileContains(authoringFile,"menu_smoke_abandoned_block")||!screen(client).state().filter().equals("BLOCK"))return;
                require(sameSnapshot("abandoned route bindings",assignmentsFile),"abandoned creation route changed binding");checks.add("unrelated later block creation stays in Saved sites after abandoned binding route");step=185;
            }
            case 185 -> {require(press(screen(client),"Saved sites"),"saved sites selector missing");waitingFor=viewCount;step++;}
            case 186 -> awaitView(()->screen(client).state().filter().equals("BLOCK")&&screen(client).state().entries().stream().allMatch(e->e.kind().equals("BLOCK")),"saved sites list contains geometry only after clear");
            case 187 -> {screen(client).selectForTest("menu_smoke_block");waitingFor=viewCount;step++;}
            case 188 -> awaitView(()->screen(client).state().selected().equals("menu_smoke_block"),"selected saved site exposes geometry actions");
            case 189 -> {require(press(screen(client),"Manage game bindings"),"saved site binding navigation missing");waitingFor=viewCount;step++;}
            case 190 -> awaitView(()->screen(client).state().filter().equals("BINDINGS"),"saved-site detail routes to game bindings");
            case 191 -> navigate(client,"Game setup",Page.SETUP);
            case 192 -> {
                var entries=screen(client).state().entries();require(entries.stream().noneMatch(e->e.kind().equals("BLOCK")||e.id().equals("quiet_below:relic_heart")),"block requirement remains duplicated in Game setup");
                require(entries.stream().anyMatch(e->e.id().equals("quiet_below:source_city")),"Source city area removed from Game setup");
                require(entries.stream().anyMatch(e->e.id().equals("tool:quiet_below:heart")&&e.label().equals("Heart weakness")),"Heart global tool not distinctly named");
                screen(client).selectForTest("tool:quiet_below:heart");waitingFor=viewCount;advance("global settings and area references remain separate from block bindings");
            }
            case 193 -> awaitView(()->screen(client).state().detail().title().equals("Heart weakness"),"Heart weakness is a distinct global setting");
            case 194 -> {capture(client,"menu-heart-weakness.png");advance("captured final global Heart settings layout");}
            case 195 -> {snapshot("gm forged binding",assignmentsFile);waitingFor=viewCount;ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.SETUP_CLEAR,Page.LOCATIONS,"binding:quiet_below:source_city","",screen(client).state().token(),0,"BINDINGS",""));step++;}
            case 196 -> awaitView(()->!screen(client).state().success()&&sameSnapshot("gm forged binding",assignmentsFile),"GM cross-kind binding packet denied without clearing Source city");
            case 197 -> {waitingFor=viewCount;ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.SETUP_ASSIGN,Page.LOCATIONS,"quiet_below:relic_heart","menu_smoke_block",screen(client).state().token(),0,"BINDINGS",""));step++;}
            case 198 -> awaitView(()->!screen(client).state().success()&&sameSnapshot("gm forged binding",assignmentsFile),"GM raw requirement packet denied in prefixed block-binding view");
            case 199 -> {client.player.networkHandler.sendChatCommand("chronicles setup assign \"quiet_below:relic_heart\" menu_smoke_block");waitingFor=viewCount;step++;}
            case 200 -> awaitView(this::boundHeart,"legacy raw setup-assign command remains compatible with block bindings");
            case 201 -> {snapshot("legacy inspect binding",assignmentsFile);client.player.networkHandler.sendChatCommand("chronicles setup inspect \"quiet_below:relic_heart\"");waitingFor=viewCount;step++;}
            case 202 -> awaitView(()->screen(client).state().success()&&screen(client).state().message().contains("outline")&&sameSnapshot("legacy inspect binding",assignmentsFile),"legacy raw setup-inspect command remains private and read only");
            case 203 -> {client.player.networkHandler.sendChatCommand("chronicles setup clear \"quiet_below:relic_heart\"");waitingFor=viewCount;step++;}
            case 204 -> awaitView(()->!boundHeart()&&fileContains(authoringFile,"menu_smoke_block"),"legacy raw setup-clear command retains saved geometry");
            default -> finish(client,true,"completed "+checks.size()+" native operator checks");
        }
    }
    private static String bindingLayout(CoreMenuScreen screen){
        try{
            StringBuilder out=new StringBuilder();
            for(String name:List.of("detailOnCompact","detailScroll","detailContentHeight","detailHeight","detailX","detailY","detailWidth","clipTop","clipBottom","pending")){
                var f=CoreMenuScreen.class.getDeclaredField(name);f.setAccessible(true);out.append(name).append('=').append(f.get(screen)).append(' ');
            }
            var f=CoreMenuScreen.class.getDeclaredField("buttons");f.setAccessible(true);var buttons=(Map<?,?>)f.get(screen);
            for(String label:List.of("Choose existing site…","Back to list","Enable editing","Stop editing")){
                var value=buttons.get(label);out.append(label).append('=');
                if(value instanceof net.minecraft.client.gui.widget.ClickableWidget widget)out.append(widget.getX()).append(',').append(widget.getY()).append(',').append(widget.getWidth()).append(',').append(widget.getHeight()).append(" active=").append(widget.active).append(" visible=").append(widget.visible);
                else out.append("absent");out.append(' ');
            }
            return out.toString();
        }catch(ReflectiveOperationException error){return error.toString();}
    }
    private String fact(MinecraftClient client,String name){return screen(client).state().detail().facts().stream().filter(f->f.label().equals(name)).map(f->f.value()).findFirst().orElse("");}
    private boolean boundHeart(){try{return java.util.stream.StreamSupport.stream(com.google.gson.JsonParser.parseString(Files.readString(assignmentsFile)).getAsJsonObject().getAsJsonArray("bindings").spliterator(),false).anyMatch(e->e.getAsJsonObject().get("requirementId").getAsString().equals("quiet_below:relic_heart"));}catch(IOException error){throw new IllegalStateException(error);}}

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
            default -> finish(client, true, "completed " + checks.size() + " ordinary-player checks");
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
            case 15 -> {capture(client,"menu-layout-heart-weakness.png");advance("captured global settings at requested logical size");}
            case 16 -> navigate(client,"Locations",Page.LOCATIONS);
            case 17 -> {require(press(screen(client),"Block sites"),"layout Block sites missing");waitingFor=viewCount;step++;}
            case 18 -> awaitView(()->screen(client).state().filter().equals("BLOCK"),"layout saved block sites rendered");
            case 19 -> {capture(client,"menu-layout-saved-sites.png");require(press(screen(client),"Game bindings"),"layout Game bindings missing");waitingFor=viewCount;step++;}
            case 20 -> awaitView(()->screen(client).state().filter().equals("BINDINGS"),"layout game binding list rendered");
            case 21 -> {screen(client).selectForTest("binding:quiet_below:relic_heart");waitingFor=viewCount;step++;}
            case 22 -> awaitView(()->screen(client).state().selected().equals("binding:quiet_below:relic_heart"),"layout Heart binding detail rendered");
            case 23 -> {capture(client,"menu-layout-game-bindings.png");advance("captured binding detail at requested logical size");}
            default -> finish(client, true, "completed " + checks.size() + " native layout checks");
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
