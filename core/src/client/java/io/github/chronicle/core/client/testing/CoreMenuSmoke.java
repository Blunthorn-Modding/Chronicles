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
    private final Map<String, String> snapshots = new HashMap<>();
    private View lastView;
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
            instance.viewCount++;
        }
    }

    private void tick(MinecraftClient client) {
        if (complete) return;
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("overall 240 second deadline exceeded");
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
            // A visible native action has already sent its request.  Do not let
            // the next scripted case press a newly rebuilt control until the
            // authoritative response has arrived (notably Continue in world).
            if (client.currentScreen instanceof CoreMenuScreen screen && screen.pending()) return;
            if (role.equals("A")) { if (LAYOUT_ONLY) driveLayout(client); else driveOperator(client); } else drivePlayer(client);
        } catch (Throwable error) {
            ChronicleCore.LOGGER.error("Core menu smoke {} failed", role, error);
            finish(client, false, error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void driveOperator(MinecraftClient client) {
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
            case 33 -> { capture(client, "menu-event-detail-" + client.getWindow().getScaledWidth() + "x" + client.getWindow().getScaledHeight() + ".png"); snapshot("unconfigured event before preview", playFile); require(press(screen(client), "Preview event"), "event preview unavailable"); waitingFor = viewCount; step++; }
            case 34 -> awaitView(() -> sameSnapshot("unconfigured event before preview", playFile), "paused unconfigured preview leaves play ledger unchanged");
            case 35 -> {
                // This action uses the normal screen callback and server route;
                // no public delivery or completion is asserted from a sample.
                if (press(screen(client), "Play preview — only me")) { waitingFor = viewCount; step++; }
                else { checks.add("cue has no private audio in this fixture"); step = 37; }
            }
            case 36 -> awaitView(() -> sameSnapshot("unconfigured event before preview", playFile), "private audio preview leaves play ledger unchanged");
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
            default -> finish(client, true, "completed " + checks.size() + " native operator checks");
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
        return false;
    }

    private void advance(String check) { checks.add(check); step++; }

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
