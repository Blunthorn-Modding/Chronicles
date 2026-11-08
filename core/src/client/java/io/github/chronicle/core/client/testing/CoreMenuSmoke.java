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
    private static final String CITY_MESSAGE = "Something ancient lies before you\u2026";
    private static final String DISTANT_MESSAGE = "Something is whispering in the dark below\u2026.";
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

    private boolean rememberedFilterStarted, rememberedFilterChecked, ordinaryReopened, pendingVisualChecked;

    private void driveOperator(MinecraftClient client) {
        if(step>=20000){driveBuildingMode(client);return;}
        // The former reverse "Use for" Region assignment workflow was removed.
        // Region create/edit and all remaining block/setup permission oracles ran first.
        if(step>=210&&step<1000){driveLootCreation(client);return;}
        if(step>=110){driveBlockBindings(client);return;}
        switch (step) {
            case 0 -> { openMenu(client); advance("operator opened the native menu"); }
            case 1 -> awaitScreen(client, Page.SESSION, "Session");
            case 2 -> { if(client.getNetworkHandler().getPlayerList().size()<2||++stableTicks<20)return;stableTicks=0;verifyCleanDebug(client);capture(client, "menu-operator-" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight() + ".png"); checks.add("captured native clean menu layout");step=20000; }
            case 3 -> {
                if(!pendingVisualChecked) {
                    var menu=screen(client);var before=menu.probeButtonForTest("Session");var closeBefore=menu.probeButtonForTest("Close");
                    require(menu.triggerForTest("Players"),"Players navigation unavailable");
                    require(menu.pending(),"navigation did not start a real server request");
                    require(before.equals(menu.probeButtonForTest("Session"))&&before.active(),"request rebuilt or greyed unrelated navigation");
                    require(closeBefore.equals(menu.probeButtonForTest("Close"))&&closeBefore.active(),"request disabled Close");
                    require(menu.triggerForTest("Events"),"pending controls lost their visual enabled state");
                    require(before.equals(menu.probeButtonForTest("Session")),"duplicate click rebuilt current controls");
                    waitingFor=viewCount;pendingVisualChecked=true;return;
                }
                if(viewCount<=waitingFor)return;
                require(screen(client).state().page()==Page.PLAYERS,"pending duplicate navigation replaced the original request");
                advance("server requests preserve enabled button identity and ignore duplicate navigation");
            }
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
            case 13 -> {
                if(!rememberedFilterStarted){screen(client).close();openMenu(client);rememberedFilterStarted=true;return;}
                if(!rememberedFilterChecked){if(!(client.currentScreen instanceof CoreMenuScreen remembered)||remembered.pending())return;require(remembered.state().page()==Page.LOCATIONS&&remembered.state().filter().equals("BLOCK"),"generic reopen lost the Location subpage");rememberedFilterChecked=true;checks.add("generic reopen restored Locations and Block sites filter");}
                navigate(client,"Game setup",Page.SETUP);
            }
            case 14 -> {
                if (client.getNetworkHandler().getPlayerList().size() < 2 || ++stableTicks < 10) return;
                CoreMenuScreen screen = screen(client);
                require(screen.triggerForTest("Enable editing"), "Enable editing is unavailable to the operator");
                waitingFor = viewCount; step++;
            }
            case 15 -> awaitView(() -> screen(client).state().editing(), "server acknowledged editing mode");
            case 16 -> {
                require(press(screen(client), "Regions"), "Regions navigation unavailable after editing acknowledgement");
                waitingFor = viewCount; step++;
            }
            case 17 -> awaitView(() -> screen(client).state().page() == Page.REGIONS, "opened Regions while editing");
            case 18 -> {
                require(press(screen(client), "Create region…"), "Create region action unavailable"); step++;
            }
            case 19 -> {
                require(client.currentScreen!=null&&client.currentScreen.getClass().getSimpleName().equals("RegionCreateScreen"), "Region creation popup did not open");
                var fields=client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.TextFieldWidget.class::isInstance).map(net.minecraft.client.gui.widget.TextFieldWidget.class::cast).toList();
                require(fields.size()==1,"Region creation popup name field missing"); fields.getFirst().setText("menu_smoke_region");
                require(client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast).anyMatch(button->button.getMessage().getString().equals("Begin selection")),"Region creation popup submit missing");
                client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast).filter(button->button.getMessage().getString().equals("Begin selection")).findFirst().orElseThrow().onPress(new net.minecraft.client.input.MouseInput(0,0)); waitingFor = viewCount; step++;
            }
            case 20 -> {
                if (client.currentScreen != null || !client.player.getMainHandStack().isOf(net.minecraft.item.Items.WOODEN_SHOVEL)) return;
                if(chatMessages.stream().noneMatch(message -> message.startsWith("Use the Debug shovel:")))return;
                advance("native Region creation returned to the world with its leased Debug shovel");
            }
            case 21 -> { client.player.networkHandler.sendChatCommand("chronicle regions pos1 1 64 1"); step++; }
            case 22 -> {
                if (chatMessages.stream().noneMatch(message -> message.contains("Set Region draft menu_smoke_region position 1 at 1 64 1"))) return;
                client.player.networkHandler.sendChatCommand("chronicle regions pos2 4 67 4"); step++;
            }
            case 23 -> {
                if (chatMessages.stream().noneMatch(message -> message.contains("Set Region draft menu_smoke_region position 2 at 4 67 4"))) return;
                openMenu(client); step++;
            }
            case 24 -> awaitScreen(client, Page.REGIONS, "generic opener restored Regions after world corner selection");
            case 25 -> {
                var region = screen(client).state().detail() == null ? null : screen(client).state().detail().region();
                require(region != null && region.id().equals("menu_smoke_region") && region.first().equals("1 64 1") && region.second().equals("4 67 4"), "world corner selection did not restore the exact Region draft");
                client.player.networkHandler.sendChatCommand("chronicle regions save"); step++;
            }
            case 26 -> {
                if (chatMessages.stream().noneMatch(message -> message.contains("Saved Region 'menu_smoke_region'"))) return;
                require(Files.isRegularFile(authoringFile) && fileContains(authoringFile, "menu_smoke_region"), "saved Region does not persist under its stable ID");
                advance("native popup, world shovel selection and canonical save persisted the exact Region");
            }
            case 27 -> { require(Files.isRegularFile(authoringFile) && fileContains(authoringFile, "menu_smoke_region"), "persisted Region disappeared before follow-on editor checks"); step++; }
            case 28 -> { checks.add("generic opener retained the active Region page after world selection"); step++; }
            case 29 -> {
                // A preview must be definition-only: it cannot create the
                // optional play-runtime file or alter it while paused.
                require(press(screen(client), "Events"), "Events navigation unavailable"); waitingFor = viewCount; step++;
            }
            case 30 -> awaitView(() -> screen(client).state().page() == Page.EVENTS, "opened Events");
            case 31 -> {
                screen(client).selectForTest("cue:quiet_below:city_discovery"); waitingFor = viewCount; step++;
            }
            case 32 -> awaitView(() -> screen(client).state().detail() != null && screen(client).state().detail().id().equals("cue:quiet_below:city_discovery"),
                    "selected unconfigured cue");
            case 33 -> { capture(client, "menu-event-detail-" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight() + ".png"); snapshot("unconfigured event before preview", playFile); previewMessageCount = chatCount(CITY_MESSAGE); require(press(screen(client), "Preview privately"), "event preview unavailable"); waitingFor = viewCount; step++; }
            case 34 -> awaitView(() -> previewChatArrived(CITY_MESSAGE) && sameSnapshot("unconfigured event before preview", playFile), "paused unconfigured City preview sends private chat without changing play ledger");
            case 35 -> {
                // This action uses the normal screen callback and server route;
                // no public delivery or completion is asserted from a sample.
                previewMessageCount = chatCount(CITY_MESSAGE);
                if (press(screen(client), "Preview privately")) { waitingFor = viewCount; step++; }
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
            case 41 -> { snapshot("loot before add", lootFile); require(press(screen(client), "Add item"), "Add item control unavailable"); step++; }
            case 42 -> { require(client.currentScreen!=null&&client.currentScreen.getClass().getSimpleName().equals("CatalogAddPopup"), "shared registry popup did not open"); client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast).filter(b->b.getMessage().getString().equals("Cancel")).findFirst().orElseThrow().onPress(new net.minecraft.client.input.MouseInput(0,0)); step=-1; }
            case -1 -> { require(sameSnapshot("loot before add", lootFile), "catalog cancellation changed the loot draft"); require(press(screen(client), "Add item"), "item catalog did not reopen after cancellation"); step=43; }
            case 43 -> { if(client.currentScreen==null||!client.currentScreen.children().stream().anyMatch(child->child instanceof net.minecraft.client.gui.widget.ButtonWidget button&&button.getMessage().getString().equals("Find")&&button.active))return;capture(client, "menu-loot-picker-" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight() + ".png"); var field=client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.TextFieldWidget.class::isInstance).map(net.minecraft.client.gui.widget.TextFieldWidget.class::cast).findFirst().orElseThrow();field.setText("  gold_ingot  ");client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast).filter(button->button.getMessage().getString().equals("Find")).findFirst().orElseThrow().onPress(new net.minecraft.client.input.MouseInput(0,0)); step++; }
            case 44 -> { if(client.currentScreen==null||!client.currentScreen.getClass().getSimpleName().equals("CatalogAddPopup"))return; if(lastView==null||lastView.picker()==null||!lastView.picker().query().equals("gold_ingot")||lastView.picker().entries().stream().noneMatch(entry->entry.id().equals("minecraft:gold_ingot")))return; advance("shared registry popup searched real item IDs"); }
            case 45 -> { client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast).filter(button->button.getMessage().getString().equals(lastView.picker().entries().stream().filter(entry->entry.id().equals("minecraft:gold_ingot")).findFirst().orElseThrow().label())).findFirst().orElseThrow().onPress(new net.minecraft.client.input.MouseInput(0,0)); step++; }
            case 46 -> { if(client.currentScreen==null||!client.currentScreen.getClass().getSimpleName().equals("CatalogAddPopup"))return; client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast).filter(button->button.getMessage().getString().equals("Add")).findFirst().orElseThrow().onPress(new net.minecraft.client.input.MouseInput(0,0)); step=47; }
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
            case 53 -> { require(!screen(client).triggerForTest("Use for\u2026"), "removed reverse region-use action remained available"); checks.add("saved Regions remain independent geometry without obsolete reverse use assignment"); step=57; }
            case 57 -> {
                CoreMenuScreen screen = screen(client);
                screen.selectForTest("menu_smoke_region");
                require(screen.pending(),"selected Region did not start real navigation");
                waitingFor=viewCount;stableTicks=0;
                require(screen.triggerForTest("Close"), "Close is unavailable while navigation is pending");
                advance("closed menu without changing server editing state");
            }
            case 58 -> {
                if(viewCount<=waitingFor||++stableTicks<10)return;
                require(!(client.currentScreen instanceof CoreMenuScreen),"late navigation response reopened the closed menu");
                checks.add("closing during pending navigation stays closed after the actual response");
                stableTicks=0;openMenu(client);advance("reopened menu after close");
            }
            case 59 -> {if(!(client.currentScreen instanceof CoreMenuScreen remembered)||remembered.pending())return;require(remembered.state().page()==Page.REGIONS&&remembered.state().selected().equals("menu_smoke_region"),"reopen lost the selected Region");advance("generic opener restored the Region page and validated selected record");}
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
            case 92 -> awaitView(() -> screen(client).state().page() == Page.EVENTS, "opened Events for whisper preview");
            case 93 -> { screen(client).selectForTest("cue:quiet_below:whisper_from_below"); waitingFor = viewCount; step++; }
            case 94 -> awaitView(() -> "cue:quiet_below:whisper_from_below".equals(screen(client).state().selected()), "selected unconfigured Whisper from Below");
            case 95 -> { snapshot("whisper preview", playFile); previewMessageCount = chatCount(DISTANT_MESSAGE); require(press(screen(client), "Preview privately"), "Whisper preview unavailable"); waitingFor = viewCount; step++; }
            case 96 -> awaitView(() -> previewChatArrived(DISTANT_MESSAGE) && sameSnapshot("whisper preview", playFile), "paused Whisper preview sends private chat without changing play ledger");
            case 97 -> { previewMessageCount = chatCount(DISTANT_MESSAGE); require(press(screen(client), "Preview privately"), "Whisper playback unavailable"); waitingFor = viewCount; step++; }
            case 98 -> awaitView(() -> previewChatArrived(DISTANT_MESSAGE) && sameSnapshot("whisper preview", playFile), "Whisper playback includes private chat and leaves play ledger unchanged");
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

    private static final String CUSTOM_LOOT = "chronicle_core:loot_review_supplies";
    private String customBook;
    private boolean creationReloadSelected;
    private void driveLootCreation(MinecraftClient client) {
        switch(step) {
            case 210 -> navigate(client,"Loot Tables",Page.LOOT_TABLES);
            case 211 -> {snapshot("loot creation cancel",lootFile);require(press(screen(client),"Create new loot table"),"Create new loot table unavailable");step++;}
            case 212 -> {creationFields(client).getFirst().setText("Cancelled supplies");popupPress(client,"Cancel");step++;}
            case 213 -> {require(sameSnapshot("loot creation cancel",lootFile),"Cancel created a table");advance("loot table creation Cancel leaves config unchanged");}
            case 214 -> {require(press(screen(client),"Create new loot table"),"creator unavailable");step++;}
            case 215 -> {
                var f=creationFields(client);f.get(0).setText("Review supplies");f.get(1).setText("invalid id");require(!popupButton(client,"Create").active,"invalid ID allowed creation");f.get(1).setText("");
                for(var child:client.currentScreen.children())if(child instanceof net.minecraft.client.gui.widget.ClickableWidget w)require(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=client.getWindow().getScaledWidth()&&w.getY()+w.getHeight()<=client.getWindow().getScaledHeight(),"creator escapes viewport");
                capture(client,"menu-create-loot-table.png");snapshot("chests before creation",serverRoot.resolve("world/chronicle/chest-content.json"));waitingFor=viewCount;popupPress(client,"Create");step++;
            }
            case 216 -> awaitView(()->screen(client).state().success()&&screen(client).state().selected().equals(CUSTOM_LOOT)&&screen(client).state().detail().loot().entries().isEmpty()&&sameSnapshot("chests before creation",serverRoot.resolve("world/chronicle/chest-content.json")),"native Create saves and selects an empty custom table without filling chests");
            case 217 -> {require(press(screen(client),"Add item"),"new table Add item unavailable");step++;}
            case 218 -> {if(!popupReady(client))return;client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.TextFieldWidget.class::isInstance).map(net.minecraft.client.gui.widget.TextFieldWidget.class::cast).findFirst().orElseThrow().setText("apple");popupPress(client,"Find");step++;}
            case 219 -> {if(lastView==null||lastView.picker()==null||!lastView.picker().query().equals("apple")||lastView.picker().entries().stream().noneMatch(e->e.id().equals("minecraft:apple")))return;popupPress(client,lastView.picker().entries().stream().filter(e->e.id().equals("minecraft:apple")).findFirst().orElseThrow().label());popupPress(client,"Add");step++;}
            case 220 -> {var m=screen(client);m.selectLootEntryForTest(0);m.setLootChanceForTest("37.5");m.setLootMaxForTest("3");m.setLootMinForTest("2");require(press(m,"Lore books"),"Lore books unavailable");require(press(m,"Add book\u2026"),"Add book unavailable");step++;}
            case 221 -> {if(!popupReady(client)||lastView==null||lastView.picker()==null||!lastView.picker().kind().equals("BOOKS")||lastView.picker().entries().isEmpty())return;var b=lastView.picker().entries().getFirst();customBook=b.id();popupPress(client,b.label());popupPress(client,"Add");step++;}
            case 222 -> {require(press(screen(client),"Save loot table"),"new table Save unavailable");waitingFor=viewCount;step++;}
            case 223 -> awaitView(()->screen(client).state().success()&&customTableReady(screen(client)),"new custom table saves item probabilities and lore through the shared pickers");
            case 224 -> {snapshot("loot duplicate",lootFile);screen(client).triggerForTest("Back to list");require(press(screen(client),"Create new loot table"),"duplicate creator unavailable");creationFields(client).getFirst().setText("Review supplies");waitingFor=viewCount;popupPress(client,"Create");step++;}
            case 225 -> {if(viewCount<=waitingFor||client.currentScreen==null||!client.currentScreen.getClass().getSimpleName().equals("LootTableCreateScreen"))return;require(!lastView.success()&&sameSnapshot("loot duplicate",lootFile)&&creationFields(client).getFirst().getText().equals("Review supplies"),"duplicate replaced data or lost fields");popupPress(client,"Cancel");advance("duplicate loot table creation preserves the table and popup fields");}
            case 226 -> {if(!creationReloadSelected){screen(client).selectForTest(CUSTOM_LOOT);creationReloadSelected=true;waitingFor=viewCount;return;}if(viewCount<=waitingFor)return;require(press(screen(client),"Reload loot configuration"),"reload unavailable");waitingFor=viewCount;step++;}
            case 227 -> awaitView(()->screen(client).state().success()&&customTableReady(screen(client)),"custom loot table survives authoritative config reload");
            case 228 -> {snapshot("loot stale creator",lootFile);screen(client).triggerForTest("Back to list");require(press(screen(client),"Create new loot table"),"stale creator unavailable");creationFields(client).getFirst().setText("Do not create");client.player.networkHandler.sendChatCommand("chronicle editing off");step++;}
            case 229 -> {if(lastView==null||lastView.editing())return;require(!popupButton(client,"Create").active&&sameSnapshot("loot stale creator",lootFile),"stale creator retained authority");popupPress(client,"Cancel");advance("editing changes invalidate an open loot creator without changing config");}
            case 230 -> {waitingFor=viewCount;ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.LOOT_NEW,Page.LOOT_TABLES,"chronicle_core:forged",io.github.chronicle.core.loot.LootTableDraftJson.encode(new io.github.chronicle.core.loot.LootType("chronicle_core:forged","Forged","Must be refused.",0,0,List.of())),screen(client).state().token(),0,"",""));step++;}
            case 231 -> awaitView(()->!screen(client).state().success()&&sameSnapshot("loot stale creator",lootFile),"forged creation outside Editing is refused without a config write");
            case 232 -> finish(client,true,"completed "+checks.size()+" native operator checks");
            default -> throw new IllegalStateException("unexpected loot creation step "+step);
        }
    }
    private boolean customTableReady(CoreMenuScreen menu) {
        var t=menu.state().detail()==null?null:menu.state().detail().loot();
        return t!=null&&t.id().equals(CUSTOM_LOOT)&&t.label().equals("Review supplies")&&t.entries().size()==1&&t.entries().getFirst().item().equals("minecraft:apple")&&t.entries().getFirst().chancePercent()==37.5&&t.entries().getFirst().minCount()==2&&t.entries().getFirst().maxCount()==3&&t.books().size()==1&&(customBook==null||t.books().getFirst().bookId().equals(customBook));
    }
    private static List<net.minecraft.client.gui.widget.TextFieldWidget> creationFields(MinecraftClient client) {
        require(client.currentScreen!=null&&client.currentScreen.getClass().getSimpleName().equals("LootTableCreateScreen"),"loot creator did not open");
        var f=client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.TextFieldWidget.class::isInstance).map(net.minecraft.client.gui.widget.TextFieldWidget.class::cast).toList();require(f.size()==3,"creation fields missing");return f;
    }
    private static net.minecraft.client.gui.widget.ButtonWidget popupButton(MinecraftClient client,String label) {return client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance).map(net.minecraft.client.gui.widget.ButtonWidget.class::cast).filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow();}
    private static void popupPress(MinecraftClient client,String label) {var b=popupButton(client,label);require(b.active,"popup action disabled: "+label);b.onPress(new net.minecraft.client.input.MouseInput(0,0));}
    private static boolean popupReady(MinecraftClient client) {return client.currentScreen!=null&&client.currentScreen.getClass().getSimpleName().equals("CatalogAddPopup")&&popupButton(client,"Find").active;}

    private void driveBlockBindings(MinecraftClient client){
        switch(step){
            case 110 -> navigate(client,"Locations",Page.LOCATIONS);
            case 111 -> {require(press(screen(client),"Block sites"),"Block sites unavailable");waitingFor=viewCount;step++;}
            case 112 -> awaitView(()->screen(client).state().filter().equals("BLOCK")&&placedSites(screen(client)).isEmpty(),"Block sites starts without physical instances; pending departures stay distinct");
            case 113 -> {require(!screen(client).triggerForTest("Game bindings")&&!screen(client).triggerForTest("Saved sites"),"nested block selectors remain");require(press(screen(client),"Enable editing"),"editing unavailable");waitingFor=viewCount;step++;}
            case 114 -> awaitView(()->screen(client).state().editing(),"server acknowledges editing for manual references");
            case 115 -> {screen(client).close();client.player.networkHandler.sendChatCommand("setblock 0 99 0 minecraft:stone");client.player.networkHandler.sendChatCommand("setblock 0 101 3 minecraft:stone");client.player.networkHandler.sendChatCommand("tp @s 0.5 100 0.5 0 0");step++;}
            case 116 -> {if(Math.abs(client.player.getY()-100)>.05||!(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit)||!hit.getBlockPos().equals(new net.minecraft.util.math.BlockPos(0,101,3)))return;openMenu(client);advance("prepared an actual looked-at vanilla reference");}
            case 117 -> awaitScreen(client,Page.LOCATIONS,"remembered Locations reopened over vanilla block");
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
            case 1331 -> {if(viewCount<=waitingFor||screen(client).state().picker()==null)return;var menu=screen(client);require(menu.state().picker().kind().equals("BLOCK_LIMIT_TYPE")&&menu.state().picker().entries().size()==3,"Game setup selector did not list registered types");verifyLimitBackdrop(menu);require(!press(menu, "Locations"),"modal allowed background navigation");capture(client,"menu-limit-chooser.png");advance("Game setup owns the sole managed block policy selector");step=134;}
            case 134 -> {screen(client).selectForTest("quiet_below:relic_heart");waitingFor=viewCount;step++;}
            case 135 -> {if(viewCount<=waitingFor||!screen(client).state().selected().equals("limit:quiet_below:relic_heart"))return;var menu=screen(client);verifyLimitBackdrop(menu);require(!menu.limitFieldEnabledForTest()&&!menu.triggerForTest("Save"),"structural Heart limit is editable");capture(client,"menu-fixed-heart-limit.png");advance("Heart policy is fixed and read only");step=1350;}
            case 1350 -> {require(screen(client).triggerForTest("Close"),"fixed limit Close unavailable");waitingFor=viewCount;step=13501;}
            case 13501 -> {if(viewCount<=waitingFor||screen(client).pending())return;require(!screen(client).limitDialogOpenForTest()&&screen(client).state().selected().equals("limits"),"fixed dialog did not restore Game setup");require(press(screen(client),"Choose block type\u2026"),"type selector missing");waitingFor=viewCount;step=1351;}
            case 1351 -> {if(viewCount<=waitingFor||screen(client).state().picker()==null)return;screen(client).selectForTest("quiet_below:ancient_forge");waitingFor=viewCount;step=1352;}
            case 1352 -> {if(viewCount<=waitingFor||!screen(client).state().selected().equals("limit:quiet_below:ancient_forge"))return;var menu=screen(client);verifyLimitBackdrop(menu);require(menu.limitFieldEnabledForTest(),"server-authorized limit field unavailable");menu.setLimitValueForTest("1025");require(!menu.triggerForTest("Save"),"out-of-range Save remained enabled");menu.setLimitValueForTest("2");require(menu.triggerForTest("Save"),"direct modal Save unavailable after correcting value");waitingFor=viewCount;step=1353;}
            case 1353 -> {if(viewCount<=waitingFor||screen(client).pending())return;var menu=screen(client);require(menu.state().success()&&managedLimitForTest("quiet_below:ancient_forge")==2,"Game setup did not save Forge cap2");checks.add("Game setup value edit durably changes the selected cap");verifyLimitBackdrop(menu);capture(client,"menu-limit-edit.png");menu.setLimitValueForTest("1");require(menu.triggerForTest("Save"),"direct modal restoration unavailable");waitingFor=viewCount;step=1354;}
            case 1354 -> {if(viewCount<=waitingFor||screen(client).pending())return;require(screen(client).state().success()&&managedLimitForTest("quiet_below:ancient_forge")==1,"Forge cap restoration failed");advance("Game setup value edit restores the cap without changing placed blocks");step=136;}
            case 136 -> {snapshot("cancelled limit",serverRoot.resolve("config/chronicle/managed-block-limits.json"));screen(client).setLimitValueForTest("7");require(screen(client).triggerForTest("Close"),"limit modal Close unavailable");waitingFor=viewCount;step++;}
            case 137 -> {if(viewCount<=waitingFor||screen(client).pending())return;require(screen(client).state().page()==Page.SETUP&&screen(client).state().picker()==null&&screen(client).state().selected().equals("limits")&&!screen(client).limitDialogOpenForTest(),"modal did not return to original Game setup");require(sameSnapshot("cancelled limit",serverRoot.resolve("config/chronicle/managed-block-limits.json")),"Close saved an unsaved limit draft");advance("Block limits modal preserves the menu and Close discards drafts");}
            case 138 -> navigate(client,"Game setup",Page.SETUP);
            case 139 -> {require(screen(client).state().entries().stream().allMatch(e->e.kind().equals("TOOL")||e.kind().equals("LIMITS")),"geometry requirements remain in Game setup");screen(client).selectForTest("tool:quiet_below:heart");waitingFor=viewCount;advance("Game setup contains global tools only");}
            case 140 -> awaitView(()->screen(client).state().detail().title().equals("Heart weakness"),"Heart weakness remains a global rule");
            case 141 -> {snapshot("forged old binding",assignmentsFile);waitingFor=viewCount;ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.SETUP_CLEAR,Page.LOCATIONS,"binding:quiet_below:source_city","",screen(client).state().token(),0,"BINDINGS",""));step++;}
            case 142 -> awaitView(()->!screen(client).state().success()&&sameSnapshot("forged old binding",assignmentsFile),"obsolete binding packet cannot clear a region use");
            case 143 -> {require(sameSnapshot("manual bindings",assignmentsFile)&&fileContains(authoringFile,"menu_smoke_block"),"manual reference unexpectedly wrote a game assignment");checks.add("manual references stay independent of retired reverse setup assignments");step=149;}
            case 149 -> navigate(client,"Locations",Page.LOCATIONS);
            case 150 -> {require(press(screen(client),"Block sites"),"Block sites unavailable");waitingFor=viewCount;step++;}
            case 151 -> awaitView(()->screen(client).state().filter().equals("BLOCK"),"reopened flat references after compatibility commands");
            case 152 -> {screen(client).selectForTest("manual:menu_smoke_block");waitingFor=viewCount;step++;}
            case 153 -> awaitView(()->screen(client).state().selected().equals("manual:menu_smoke_block"),"manual reference remains selectable");
            case 154 -> {require(press(screen(client),"Delete location"),"manual-only delete unavailable");waitingFor=viewCount;step++;}
            case 155 -> awaitView(()->!fileContains(authoringFile,"menu_smoke_block")&&fileContains(authoringFile,"menu_smoke_region"),"manual reference removal preserves unrelated geometry");
            case 156 -> {client.player.networkHandler.sendChatCommand("chronicle locations blocks bind alias_one target");client.player.networkHandler.sendChatCommand("chronicle locations blocks bind alias_two target");waitingFor=viewCount;step++;}
            case 157 -> awaitView(()->fileContains(authoringFile,"alias_one")&&fileContains(authoringFile,"alias_two"),"two legacy references share one physical position");
            case 158 -> {client.player.networkHandler.sendChatCommand("setblock 0 101 3 quiet_below:ancient_altar");waitingFor=viewCount;step++;}
            case 159 -> awaitView(()->placedSites(screen(client)).size()==1&&placedSites(screen(client)).getFirst().kind().equals("MANAGED"),"discovery deduplicates both aliases into one physical row");
            case 160 -> {screen(client).selectForTest(placedSites(screen(client)).getFirst().id());waitingFor=viewCount;step++;}
            case 161 -> awaitView(()->screen(client).state().selected().startsWith("managed:"),"selected managed position with multiple references");
            case 162 -> {require(press(screen(client),"Manual references\u2026"),"multiple-reference selector unavailable");waitingFor=viewCount;step++;}
            case 163 -> awaitView(()->screen(client).state().picker()!=null&&screen(client).state().picker().kind().equals("MANUAL_REFERENCE")&&screen(client).state().picker().entries().size()==2,"both legacy aliases remain discoverable");
            case 164 -> {screen(client).selectForTest("manual:alias_two");waitingFor=viewCount;step++;}
            case 165 -> awaitView(()->screen(client).state().selected().equals("manual:alias_two")&&screen(client).state().detail().title().equals("alias_two"),"alias picker opens the exact selected manual detail");
            case 166 -> {require(press(screen(client),"Delete location"),"selected alias deletion unavailable");waitingFor=viewCount;step++;}
            case 167 -> awaitView(()->!fileContains(authoringFile,"alias_two")&&fileContains(authoringFile,"alias_one")&&placedSites(screen(client)).size()==1&&placedSites(screen(client)).getFirst().kind().equals("MANAGED"),"deleting one alias retains the other and its managed quota");
            case 168 -> {screen(client).selectForTest(placedSites(screen(client)).getFirst().id());waitingFor=viewCount;step++;}
            case 169 -> awaitView(()->screen(client).state().detail().facts().stream().anyMatch(f->f.label().equals("Manual reference")&&f.value().equals("alias_one")),"remaining alias retains its scoped managed detail");
            case 170 -> {require(press(screen(client),"Delete manual reference"),"last manual reference deletion unavailable");waitingFor=viewCount;step++;}
            case 171 -> awaitView(()->!fileContains(authoringFile,"alias_one")&&placedSites(screen(client)).size()==1&&placedSites(screen(client)).getFirst().kind().equals("MANAGED"),"deleting all manual references cannot free physical quota");
            case 172 -> step=210;
            case 173 -> awaitView(()->!screen(client).hasTextPromptForTest()&&sameSnapshot("manual cancelled",authoringFile),"changed looked-at target closes the old naming prompt without a mutation");
            case 174 -> {client.player.networkHandler.sendChatCommand("tp @s 0.5 100 0.5 0 0");waitingFor=viewCount;step++;}
            case 175 -> awaitView(()->client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit&&hit.getBlockPos().equals(new net.minecraft.util.math.BlockPos(0,101,3)),"restored the actual manual-reference target");
            case 176 -> {require(press(screen(client),"Bind looked-at block\u2026"),"manual block naming unavailable after target change");screen(client).textForTest("menu_smoke_block");require(screen(client).triggerForTest("Send"),"manual reference Send unavailable");waitingFor=viewCount;step=125;}
            default -> throw new IllegalStateException("unexpected flat block test step "+step);
        }
    }
    private static List<io.github.chronicle.core.menu.CoreMenuModel.Entry> placedSites(CoreMenuScreen menu){return menu.state().entries().stream().filter(e->e.kind().equals("MANAGED")||e.kind().equals("MANUAL")).toList();}
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
                if(!ordinaryReopened){screen(client).close();openMenu(client);ordinaryReopened=true;return;}
                if(!(client.currentScreen instanceof CoreMenuScreen reopened)||reopened.pending())return;
                require(!reopened.state().operator()&&reopened.state().page()==Page.PLAYERS,"ordinary player reopen lost permitted page or gained authority");
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
            case 14 -> {waitingFor=viewCount;ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.LOOT_NEW,Page.LOOT_TABLES,"chronicle_core:forbidden",io.github.chronicle.core.loot.LootTableDraftJson.encode(new io.github.chronicle.core.loot.LootType("chronicle_core:forbidden","Forbidden","Must be refused.",0,0,List.of())),screen(client).state().token(),0,"",""));step++;}
            case 15 -> awaitView(()->!screen(client).state().success()&&!fileContains(lootFile,"chronicle_core:forbidden"),"non-operator cannot create a loot table through a forged request");
            case 16 -> {waitingFor=viewCount;ClientPlayNetworking.send(new CoreMenuPayloads.Request(Action.FLAG_CREATE,Page.FLAGS,"chronicle_core:forbidden_flag","",screen(client).state().token(),0,"",""));step++;}
            case 17 -> awaitView(()->!screen(client).state().success()&&!screen(client).state().operator()&&!fileContains(serverRoot.resolve("world/chronicle/campaign-state.json"),"chronicle_core:forbidden_flag"),"non-operator cannot create a flag through a forged request");
            case 18 -> {checks.add("disconnect while the operator has an unsaved naming prompt");finish(client, true, "completed " + checks.size() + " ordinary-player checks");}
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
            case 7 -> {if(screen(client).state().page()==Page.LOOT_TABLES){require(screen(client).state().entries().stream().anyMatch(e->e.id().equals(CUSTOM_LOOT))&&customTableReady(screen(client)),"saved custom table lost entries after layout/restart");checks.add("saved custom loot table remains available with item and lore entries");}navigate(client,"Regions",Page.REGIONS);}
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
            case 2001 -> {if(viewCount<=waitingFor||screen(client).state().picker()==null)return;require(screen(client).state().picker().kind().equals("BLOCK_LIMIT_TYPE"),"layout policy selector missing");verifyLimitBackdrop(screen(client));capture(client,"menu-layout-limit-chooser.png");advance("layout managed type selector rendered only under Game setup");step=21;}
            case 21 -> {screen(client).selectForTest("quiet_below:relic_heart");waitingFor=viewCount;step++;}
            case 22 -> awaitView(()->screen(client).state().selected().equals("limit:quiet_below:relic_heart"),"layout fixed Heart limit detail rendered");
            case 23 -> {verifyLimitBackdrop(screen(client));capture(client,"menu-layout-fixed-limit.png");advance("captured generic limit detail at requested logical size");}
            case 24 -> {
                var parent=client.currentScreen;
                var entries=net.minecraft.registry.Registries.ITEM.getIds().stream().sorted().limit(12).map(id->new io.github.chronicle.core.menu.CoreMenuModel.Entry(id.toString(),net.minecraft.registry.Registries.ITEM.get(id).getName().getString(),id.toString(),"ITEM","",0)).toList();
                client.setScreen(new io.github.chronicle.core.client.CatalogAddPopup(parent,net.minecraft.text.Text.literal("Shared catalog layout"),true,new io.github.chronicle.core.client.CatalogAddPopup.Owner(){
                    public void requestPage(io.github.chronicle.core.client.CatalogAddPopup popup,String query,int page){popup.replacePage(new io.github.chronicle.core.menu.CoreMenuModel.Picker("ITEMS",query,0,1,entries));}
                    public String validate(io.github.chronicle.core.menu.CoreMenuModel.Entry entry,String quantity){return "";}
                    public void add(io.github.chronicle.core.menu.CoreMenuModel.Entry entry,String quantity){throw new IllegalStateException("layout probe cannot add supplies");}
                    public void cancel(){}
                    public void drawIcon(net.minecraft.client.gui.DrawContext context,io.github.chronicle.core.menu.CoreMenuModel.Entry entry,int x,int y){context.drawItem(new net.minecraft.item.ItemStack(net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(entry.id()))),x,y);}
                }));stableTicks=0;step++;
            }
            case 25 -> {
                var widgets=client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.ClickableWidget.class::isInstance).map(net.minecraft.client.gui.widget.ClickableWidget.class::cast).filter(w->w.visible).toList();
                int width=client.getWindow().getScaledWidth(),height=client.getWindow().getScaledHeight();
                for(var w:widgets)require(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=width&&w.getY()+w.getHeight()<=height,"shared popup widget escapes viewport: "+w.getMessage().getString());
                for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){var x=widgets.get(i);var y=widgets.get(j);require(x.getX()+x.getWidth()<=y.getX()||y.getX()+y.getWidth()<=x.getX()||x.getY()+x.getHeight()<=y.getY()||y.getY()+y.getHeight()<=x.getY(),"shared popup widgets overlap");}
                var down=widgets.stream().filter(w->w.getMessage().getString().equals(Character.toString(0x2193))).findFirst().orElseThrow();
                require(down.getY()>widgets.stream().filter(w->w.getMessage().getString().equals("Find")).findFirst().orElseThrow().getY()+30,"row scroll control still covers item icons");
                ((net.minecraft.client.gui.widget.ButtonWidget)down).onPress(new net.minecraft.client.input.MouseInput(0,0));
                checks.add("shared Add popup fits the native viewport with separate rows, icons, paging and quantity controls");stableTicks=0;step++;
            }
            case 26 -> {if(++stableTicks<4)return;capture(client,"menu-layout-shared-catalog.png");step++;}
            case 27 -> finish(client,true,"completed "+checks.size()+" native layout checks");
            default -> throw new IllegalStateException("unexpected layout menu step "+step);
        }
    }

    private void openMenu(MinecraftClient client) {
        client.player.networkHandler.sendChatCommand("chronicle");
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
        verifyFrame(screen(client));
        advance(check);
    }

    private void awaitView(Checked condition, String check) {
        if (viewCount <= waitingFor || !condition.test()) return;
        verifyFrame(screen(MinecraftClient.getInstance()));
        advance(check);
    }

    private static CoreMenuScreen screen(MinecraftClient client) {
        if (!(client.currentScreen instanceof CoreMenuScreen screen)) throw new IllegalStateException("native Core menu screen is not open");
        return screen;
    }

    /** Finds controls that are intentionally below the compact detail fold. */
    private static boolean press(CoreMenuScreen screen, String label) {
        if(java.util.Set.of("Chests","Loot Tables","Books & Lore","Regions","Locations","Player Roles","Game setup").contains(label)) {
            if(screen.triggerForTest(label))return true;
            for(int i=0;i<12;i++){if(!screen.triggerForTest("^"))break;if(screen.triggerForTest(label))return true;}
            for(int i=0;i<12;i++){if(!screen.triggerForTest("v"))break;if(screen.triggerForTest(label))return true;}
            return false;
        }
        for (int attempt = 0; attempt < 60; attempt++) screen.scrollDetailForTest(1);
        for (int attempt = 0; attempt < 30; attempt++) {
            if (screen.triggerForTest(label)) {
                verifyFrame(screen);
                // Confirmations are a local modal step: press the original
                // action once, then its explicit confirmation if it appeared.
                screen.triggerForTest("Confirm: " + label);
                verifyFrame(screen);
                return true;
            }
            screen.scrollDetailForTest(-1);
        }
        if(List.of("Points","Block sites","Saved sites","Game bindings").contains(label)&&screen.triggerForTest("Back to list"))return screen.triggerForTest(label);
        return false;
    }

    private static void verifyFrame(CoreMenuScreen screen) {
        var c=screen.contentBoundsForTest();
        require(c.x()>=14&&c.y()>=14,"popup content has no real inset");
        for(var widget:screen.visibleWidgetBoundsForTest())
            require(widget.x()>=c.x()&&widget.y()>=c.y()&&widget.x()+widget.width()<=c.x()+c.width()
                    &&widget.y()+widget.height()<=c.y()+c.height(),"widget escapes popup content: "+widget);
        var header=screen.headerBoundsForTest();
        if(header.debug()!=null&&header.editing()!=null) {
            require(header.editing().x()+header.editing().width()<header.debug().x()
                    &&header.debug().x()+header.debug().width()<header.refresh().x(),"header controls overlap or Debug is misplaced");
            require(header.editing().y()==header.debug().y()&&header.debug().y()==header.refresh().y(),"Debug is not beside editing");
        }
    }

    private static void verifyLimitBackdrop(CoreMenuScreen screen) {
        verifyFrame(screen);
        require(screen.limitDialogOpenForTest(),"policy picker is not a modal");
        require(screen.limitBackgroundPageForTest()==Page.SETUP&&screen.limitBackgroundSelectedForTest().equals("limits"),"modal replaced the underlying Game setup selection");
    }

    private void verifyCleanDebug(MinecraftClient client) {
        var screen=screen(client);var before=screen.state();
        verifyFrame(screen);
        checks.add("Visible controls stay inside the popup and Debug sits beside editing");
        require(!screen.debugForTest(),"a fresh client did not default to Clean view");
        require(screen.debugVisibleFactLabelsForTest().isEmpty(),"Clean view exposes selected-item facts");
        screen.toggleDebugForTest();require(screen.debugForTest()&&screen.debugVisibleFactLabelsForTest().equals(before.detail().facts().stream().map(f->f.label()).toList()),"Debug view does not reveal every fact in order");
        require(screen.state()==before,"Debug toggle changed the authoritative view");
        screen.toggleDebugForTest();require(!screen.debugForTest()&&screen.debugVisibleFactLabelsForTest().isEmpty()&&screen.state()==before,"Clean toggle changed authority or failed to hide facts");
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
