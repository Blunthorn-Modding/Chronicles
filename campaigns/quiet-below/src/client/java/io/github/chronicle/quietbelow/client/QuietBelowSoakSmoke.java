package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/**
 * Explicitly opt-in, two-client driver for the disposable native long-soak fixture.
 * It deliberately uses only ordinary chat commands and a real Fabric connection.
 */
final class QuietBelowSoakSmoke {
    private static final String ADDRESS = "127.0.0.1:25575";
    private static final int MAX_CHAT_MESSAGES = 300;
    // A 20-minute setup can legitimately issue 2,000 city visits at the required 600ms cadence.
    private static final int MAX_COMMANDS = 4_096;
    private static final long SETUP_TIMEOUT_NANOS = 1_200_000_000_000L;
    private static final long STATUS_INTERVAL_NANOS = 10_000_000_000L;
    private static final String JOURNAL_MAINTENANCE_HOLD = "Ecology journal maintenance is holding writes; wait for completion or inspect the server log.";
    private static final Pattern TIMESTAMP_WORLD = Pattern.compile("\\d{8}T\\d{6}.*");
    private static final Gson GSON = new Gson();

    private final Config config;
    private final List<String> messages = new ArrayList<>();
    private final List<String> checks = new ArrayList<>();
    private final long setupDeadline = System.nanoTime() + SETUP_TIMEOUT_NANOS;
    private String state = "connecting", setupStep = "awaiting title screen";
    private int messageBaseline, sentCommands;
    private long nextAction, nextStatus, nextRepeat, nextCommandRetry, nextServerPoll, nativeClientTicks;
    private boolean connected, seenPlayer, completed, settingsConfigured;
    private boolean maintenanceDeadlineExtended, maintenanceRetryPending;
    private long soakStartedAt, runDeadline;
    private ServerResult cachedServer = new ServerResult("", false, "");

    private QuietBelowSoakSmoke(Config config) {
        this.config = config;
    }

    static void registerIfRequested() {
        if (!"1".equals(System.getenv("CHRONICLE_ECOLOGY_NATIVE_TRIAL"))) return;
        if (System.getenv("CHRONICLE_LONG_SOAK_REPORTS") == null || System.getenv("CHRONICLE_LONG_SOAK_SECONDS") == null
                || System.getenv("CHRONICLE_LONG_SOAK_ROLE") == null || System.getenv("CHRONICLE_LONG_SOAK_WORLD") == null) return;
        try {
            Config config = Config.read();
            QuietBelowSoakSmoke smoke = new QuietBelowSoakSmoke(config);
            ClientReceiveMessageEvents.GAME.register((text, overlay) -> smoke.recordMessage(text.getString()));
            ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
        } catch (RuntimeException error) {
            // A malformed test invocation must never turn a normal client into a soak driver.
            System.err.println("Quiet Below long-soak client disabled: " + error.getMessage());
        }
    }

    private void recordMessage(String message) {
        if (messages.size() == MAX_CHAT_MESSAGES) {
            messages.remove(0);
            messageBaseline = Math.max(0, messageBaseline - 1);
        }
        messages.add(message);
    }

    private void tick(MinecraftClient client) {
        if (completed) return;
        nativeClientTicks++;
        try {
            writeStatusIfDue(client);
            ServerResult server = serverResult();
            if (server.terminal()) {
                if (server.passed()) {
                    require(seenPlayer && client.player != null && client.world != null,
                            "client disconnected before acknowledging completed soak");
                    require(expectedUuid().equals(client.player.getUuid()) && client.getCurrentServerEntry() != null
                            && ADDRESS.equals(client.getCurrentServerEntry().address), "completion connection mismatch");
                }
                finish(client, server.passed(), "server " + server.state() + ": " + server.detail());
                return;
            }
            if (seenPlayer && (client.player == null || client.world == null)) {
                throw new IllegalStateException("native client disconnected after joining fixture (screen=" + screenName(client) + ")");
            }
            if (!connected && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                connected = true;
                setupStep = "connecting to disposable fixture";
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS),
                        new ServerInfo("Chronicle long-soak fixture", ADDRESS, ServerInfo.ServerType.OTHER), false, null);
                return;
            }
            if (client.player == null || client.world == null) {
                require(System.nanoTime() < setupDeadline, "did not join native fixture before setup deadline (screen=" + screenName(client) + ")");
                return;
            }
            seenPlayer = true;
            require(client.getCurrentServerEntry() != null && ADDRESS.equals(client.getCurrentServerEntry().address), "fixture address mismatch");
            require(expectedUuid().equals(client.player.getUuid()), "unexpected native player UUID: " + client.player.getUuidAsString());
            if (!settingsConfigured) {
                client.options.getViewDistance().setValue(config.anchor ? 12 : 6);
                client.options.sendClientSettings();
                settingsConfigured = true;
                checks.add("native client settings sent once");
            }
            if (config.anchor) anchor(client); else roamer(client);
            if (soakStartedAt != 0) require(System.nanoTime() < runDeadline, "long-soak run deadline elapsed");
            else require(System.nanoTime() < setupDeadline, "long-soak setup deadline elapsed at " + setupStep);
        } catch (Throwable error) {
            finish(client, false, error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void anchor(MinecraftClient client) {
        switch (state) {
            case "connecting", "prepare-wait" -> command("prepare", "quiet_below_city_trial prepare", "PASS ancient city prepared:.*", 180_000, "prepare_done");
            case "prepare_done", "building-wait" -> command("building", "chronicle session building on", "Building Mode enabled.*", 20_000, "building_done");
            case "building_done", "index-wait" -> command("index", "chronicle setup tool \"quiet_below:ecology\" index", "Source indexing started.*", 20_000, "index_done");
            case "index_done", "visiting-city-wait" -> repeated("visiting-city", "quiet_below_city_trial visit", "CITY_INDEX_READY.*", 600, 720_000, "census");
            case "census", "census-wait" -> command("census", "quiet_below_city_trial indexed", "PASS ancient city indexed:.*", 30_000, "census_done");
            case "census_done", "converge-wait" -> command("converge", "quiet_below_city_trial converge", "CITY_CONVERGE.*", 30_000, "converge_done");
            case "converge_done", "delay-wait" -> delay("restore normal client loading", 15_000, "enable");
            case "enable", "enable-wait" -> command("enable", "chronicle setup tool \"quiet_below:ecology\" enable", "Growth enabled.*", 20_000, "enable_done");
            case "enable_done", "session-wait" -> command("session", "chronicle session start", "Started Chronicle session.*", 20_000, "session_done");
            case "session_done", "joining-session-wait" -> repeated("joining-session", "chronicle players join", ".*Participation enabled.*", 5_000, 120_000, "certified");
            case "certified", "certifying city-wait" -> repeated("certifying city", "quiet_below_city_trial certified", "PASS ancient city certified:.*", 5_000, 120_000, "soak");
            case "soak", "starting long soak-wait" -> repeated("starting long soak", "quiet_below_long_soak start", "SOAK_STARTED.*", 5_000, 120_000, "running");
            case "running" -> beginRun();
            case "active" -> { state = "active"; setupStep = "long soak active"; }
            default -> throw new IllegalStateException("unknown anchor state " + state);
        }
    }

    private void roamer(MinecraftClient client) {
        switch (state) {
            case "connecting", "joining-session-wait" -> repeated("joining-session", "chronicle players join", ".*Participation enabled.*", 5_000, 1_200_000, "waiting-server");
            case "waiting-server" -> {
                setupStep = "waiting for server RUNNING";
                if ("RUNNING".equals(serverResult().state())) { checks.add("server RUNNING"); state = "running"; }
            }
            case "running" -> beginRun();
            case "active" -> { state = "active"; setupStep = "long soak active"; }
            default -> throw new IllegalStateException("unknown roamer state " + state);
        }
    }

    private void command(String name, String command, String response, long timeoutMillis, String next) {
        if (!state.equals(name + "-wait")) {
            setupStep = name;
            messageBaseline = messages.size();
            send(command);
            nextAction = System.nanoTime() + timeoutMillis * 1_000_000L;
            nextCommandRetry = System.nanoTime() + 5_000_000_000L;
            maintenanceDeadlineExtended = false;
            maintenanceRetryPending = false;
            state = name + "-wait";
            return;
        }
        if (received(response)) { checks.add(name); state = next; return; }
        if (!"prepare".equals(name) && consumeMaintenanceHold()) {
            long now = System.nanoTime();
            if (!maintenanceDeadlineExtended) {
                // One bounded extension accommodates the expected automatic journal compaction.
                nextAction = Math.max(nextAction, now + 180_000_000_000L);
                maintenanceDeadlineExtended = true;
            }
            maintenanceRetryPending = true;
        }
        long now = System.nanoTime();
        require(now < nextAction, "timed out: " + name);
        if (maintenanceRetryPending && now >= nextCommandRetry) {
            send(command);
            maintenanceRetryPending = false;
            nextCommandRetry = now + 5_000_000_000L;
        }
    }

    private void repeated(String name, String command, String response, long intervalMillis, long timeoutMillis, String next) {
        if (!state.equals(name + "-wait")) {
            setupStep = name;
            messageBaseline = messages.size();
            send(command);
            nextAction = System.nanoTime() + timeoutMillis * 1_000_000L;
            nextRepeat = System.nanoTime() + intervalMillis * 1_000_000L;
            state = name + "-wait";
            return;
        }
        if ("starting long soak".equals(name) && "RUNNING".equals(serverResult().state())) {
            checks.add("server RUNNING");
            state = next;
            return;
        }
        if (received(response)) { checks.add(name); state = next; return; }
        require(System.nanoTime() < nextAction, "timed out: " + name);
        if (System.nanoTime() >= nextRepeat) { send(command); nextRepeat = System.nanoTime() + intervalMillis * 1_000_000L; }
    }

    private void delay(String name, long millis, String next) {
        if (!state.equals("delay-wait")) { setupStep = name; nextAction = System.nanoTime() + millis * 1_000_000L; state = "delay-wait"; return; }
        if (System.nanoTime() >= nextAction) { checks.add(name); state = next; }
    }

    private void beginRun() {
        soakStartedAt = System.nanoTime();
        runDeadline = soakStartedAt + (config.seconds + 300L) * 1_000_000_000L;
        checks.add("SOAK_STARTED / server RUNNING");
        state = "active";
    }

    private void send(String command) {
        require(sentCommands++ < MAX_COMMANDS, "bounded chat-command limit exceeded");
        MinecraftClient.getInstance().player.networkHandler.sendChatCommand(command);
    }

    private boolean received(String expression) {
        Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
        for (String message : messages.subList(messageBaseline, messages.size())) {
            if (message.contains(JOURNAL_MAINTENANCE_HOLD)) continue;
            if (message.startsWith("FAIL") || message.startsWith("SOAK_FAIL:")) throw new IllegalStateException(message);
        }
        for (String message : messages.subList(messageBaseline, messages.size())) {
            if (message.contains(JOURNAL_MAINTENANCE_HOLD)) continue;
            if (pattern.matcher(message).matches()) return true;
        }
        return false;
    }

    private boolean consumeMaintenanceHold() {
        for (int i = messageBaseline; i < messages.size(); i++) {
            if (messages.get(i).contains(JOURNAL_MAINTENANCE_HOLD)) {
                messageBaseline = i + 1;
                return true;
            }
        }
        return false;
    }

    private ServerResult serverResult() {
        if (System.nanoTime() < nextServerPoll) return cachedServer;
        nextServerPoll = System.nanoTime() + STATUS_INTERVAL_NANOS;
        return cachedServer = pollServerResult();
    }

    private ServerResult pollServerResult() {
        for (Path file : List.of(config.reports.resolve("status.json"), config.reports.resolve("summary.json"))) {
            if (!Files.isRegularFile(file)) continue;
            try {
                JsonObject value = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                String status = field(value, "state", field(value, "status", ""));
                String result = field(value, "result", "");
                String detail = field(value, "detail", field(value, "failure", ""));
                if ("FAILED".equalsIgnoreCase(status) || "FAIL".equalsIgnoreCase(result)) return new ServerResult("FAILED", false, detail);
                if ("INTERRUPTED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) return new ServerResult(status.toUpperCase(), false, detail);
                // The server's schema uses state=FINISHED as its successful result.  A present
                // explicit result must agree; a summary never gets to turn a failed status into PASS.
                if ("FINISHED".equalsIgnoreCase(status)) return new ServerResult("FINISHED", !"FAIL".equalsIgnoreCase(result), detail);
                return new ServerResult(status, false, detail);
            } catch (Exception error) {
                throw new IllegalStateException("invalid server status " + file.getFileName() + ": " + error.getMessage());
            }
        }
        return new ServerResult("", false, "");
    }

    private void writeStatusIfDue(MinecraftClient client) {
        if (System.nanoTime() < nextStatus) return;
        nextStatus = System.nanoTime() + STATUS_INTERVAL_NANOS;
        JsonObject value = new JsonObject();
        value.addProperty("utc", Instant.now().toString());
        value.addProperty("state", state);
        value.addProperty("setupStep", setupStep);
        value.addProperty("nativeClientTicks", nativeClientTicks);
        value.addProperty("connection", seenPlayer && client.player != null && client.world != null);
        value.addProperty("alive", client.player != null && client.player.isAlive());
        if (client.player != null) {
            value.addProperty("x", client.player.getX()); value.addProperty("y", client.player.getY()); value.addProperty("z", client.player.getZ());
        }
        writeAtomically(config.reports.resolve("client-" + config.role + "-status.json"), value);
    }

    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (completed) return;
        completed = true;
        JsonObject value = new JsonObject();
        value.addProperty("result", pass ? "PASS" : "FAIL");
        value.addProperty("detail", detail == null ? "" : detail);
        value.add("checks", GSON.toJsonTree(checks));
        value.addProperty("role", config.role);
        value.addProperty("utc", Instant.now().toString());
        writeAtomically(config.reports.resolve("client-" + config.role + ".json"), value);
        client.scheduleStop();
    }

    private static void writeAtomically(Path destination, JsonObject value) {
        try {
            Files.createDirectories(destination.getParent());
            Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(value) + "\n", StandardCharsets.UTF_8);
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException error) { System.err.println("Unable to write long-soak status: " + error.getMessage()); }
    }

    private UUID expectedUuid() {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + (config.anchor ? "ChronicleSoakA" : "ChronicleSoakB")).getBytes(StandardCharsets.UTF_8));
    }

    private static String field(JsonObject object, String name, String fallback) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }
    private static String screenName(MinecraftClient client) { return client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName(); }
    private static void require(boolean condition, String detail) { if (!condition) throw new IllegalStateException(detail); }
    private record ServerResult(String state, boolean passed, String detail) { boolean terminal() { return List.of("FINISHED", "FAILED", "INTERRUPTED", "CANCELLED").contains(state); } }

    private record Config(Path reports, Path world, long seconds, String role, boolean anchor) {
        static Config read() {
            String reportsText = required("CHRONICLE_LONG_SOAK_REPORTS");
            Path reports = Path.of(reportsText);
            require(reports.isAbsolute(), "CHRONICLE_LONG_SOAK_REPORTS must be absolute");
            require(Files.isDirectory(reports), "CHRONICLE_LONG_SOAK_REPORTS must be an existing directory");
            Path world = Path.of(required("CHRONICLE_LONG_SOAK_WORLD")).toAbsolutePath().normalize();
            require(world.getParent() != null && "test-runs".equals(world.getParent().getFileName().toString()), "world must be a direct test-runs child");
            require(TIMESTAMP_WORLD.matcher(world.getFileName().toString()).matches(), "world directory must begin with a timestamp");
            require(Files.isRegularFile(world.resolve(".long-soak-fixture")), "missing .long-soak-fixture");
            long seconds;
            try { seconds = Long.parseLong(required("CHRONICLE_LONG_SOAK_SECONDS")); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("CHRONICLE_LONG_SOAK_SECONDS must be an integer", error); }
            require(seconds >= 30 && seconds <= 10_800, "CHRONICLE_LONG_SOAK_SECONDS must be 30..10800");
            String role = required("CHRONICLE_LONG_SOAK_ROLE");
            require("anchor".equals(role) || "roamer".equals(role), "CHRONICLE_LONG_SOAK_ROLE must be anchor or roamer");
            return new Config(reports, world, seconds, role, "anchor".equals(role));
        }
        private static String required(String key) { String value = System.getenv(key); if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + key); return value; }
    }
}
