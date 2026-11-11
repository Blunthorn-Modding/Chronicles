package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Client driver for the opt-in automatic ecology maintenance rehearsal. */
final class EcologyAutoMaintenanceSmoke {
    private static final String OUTPUT = System.getenv("CHRONICLE_ECOLOGY_AUTO_MAINTENANCE_SMOKE");
    private static final String ROOT = System.getenv("CHRONICLE_ECOLOGY_AUTO_MAINTENANCE_ROOT");
    private static final String PHASE = System.getenv("CHRONICLE_ECOLOGY_AUTO_MAINTENANCE_PHASE");
    private static final String ADDRESS = "127.0.0.1:25575";

    private final Path output;
    private final String phase;
    private final List<String> messages = new ArrayList<>(), checks = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private JsonObject status = new JsonObject();
    private int index, baseline;
    private long until, nextPoll, deadline;
    private final long overall = System.nanoTime() + 180_000_000_000L;
    private boolean connected, built, started, finished, unloadRequested;

    private EcologyAutoMaintenanceSmoke(Path output, Path root, String phase) throws IOException {
        this.output = output.toAbsolutePath().normalize(); this.phase = phase;
        require(List.of("initial", "restart").contains(phase), "Unknown automatic-maintenance phase");
        root = root.toRealPath();
        require(root.getParent().getFileName().toString().equals("test-runs")
                        && root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z"),
                "Not a timestamped test runtime");
        require(this.output.equals(root.getParent().getParent().resolve("reports/automatic-compaction-" + root.getFileName())),
                "Unexpected automatic-maintenance reports path");
        require(Files.isRegularFile(root.resolve(".ecology-auto-maintenance-fixture")), "Missing owned fixture marker");
    }

    static void register() {
        if (OUTPUT == null) return;
        try {
            var smoke = new EcologyAutoMaintenanceSmoke(Path.of(OUTPUT), Path.of(ROOT), PHASE);
            ClientReceiveMessageEvents.GAME.register((text, overlay) -> {
                if (overlay || smoke.finished) return;
                String value = text.getString(); smoke.messages.add(value);
                if (value.startsWith("AUTO ")) smoke.status = JsonParser.parseString(value.substring(5)).getAsJsonObject();
            });
            ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
        } catch (Exception error) { setupFailure(error); MinecraftClient.getInstance().scheduleStop(); }
    }

    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            require(System.nanoTime() < overall, "Overall automatic-maintenance timeout");
            if (!connected && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                connected = true;
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS),
                        new ServerInfo("Disposable automatic maintenance", ADDRESS, ServerInfo.ServerType.OTHER), false, null);
                return;
            }
            if (client.player == null || client.world == null || client.player.age < 40) return;
            require(client.getCurrentServerEntry() != null && ADDRESS.equals(client.getCurrentServerEntry().address), "Wrong fixture endpoint");
            require(!Files.exists(output.resolve("trial-failure.json")), "Server trial failed: " + failure());
            if (!built) { build(client); built = true; }
            if (index == steps.size()) { finish(client, true, "Native automatic maintenance rehearsal completed"); return; }
            Step step = steps.get(index);
            if (!started) { baseline = messages.size(); deadline = System.nanoTime() + step.timeoutMillis * 1_000_000L; step.start.run(); started = true; }
            if (step.done.getAsBoolean()) { checks.add(step.name); index++; started = false; }
            else require(System.nanoTime() < deadline, "Timed out: " + step.name + "; status=" + status);
        } catch (Throwable error) { finish(client, false, error.getClass().getSimpleName() + ": " + error.getMessage()); }
    }

    private void build(MinecraftClient client) {
        if (phase.equals("restart")) {
            command(client, "reload compacted journal through ordinary server startup", "restart");
            return;
        }
        step("move native player to naturally load the source", () -> send(client, "tp @s 1023.5 82 1023.5"),
                () -> Math.abs(client.player.getX() - 1023.5) < 1 && Math.abs(client.player.getY() - 82) < 1, 15_000);
        delay("allow player-loaded source chunks", 2_000);
        command(client, "prepare indexed real source and reducible history", "prepare");
        command(client, "start paused scheduler trigger", "paused");
        waitFor("hold paused worker off thread", () -> poll(client) && truth("busy") && "paused_held".equals(state()), 30_000);
        delay("prove server ticks continue while worker waits", 1_500);
        command(client, "verify held barrier rejects mutation and terrain writes", "during");
        command(client,"inject production unload callback while held (synthetic)","queue_unload");
        command(client, "release paused worker", "release");
        waitFor("complete verified paused compaction", () -> poll(client) && !truth("busy") && number("completed") >= 1 && "paused_done".equals(state()), 30_000);
        command(client, "trigger hard threshold during active participating play", "active");
        waitFor("complete active scheduler route", () -> poll(client) && !truth("busy") && number("completed") >= 2 && "active_done".equals(state()), 30_000);
        step("leave source during ordinary active play",()->send(client,"tp @s 4096.5 82 4096.5"),()->Math.abs(client.player.getX()-4096.5)<1,15_000);
        waitFor("naturally unload source outside frozen hold",()->{
            poll(client);
            if(!unloadRequested&&status.has("sourceLoaded")&&!truth("sourceLoaded")&&!truth("busy")){
                send(client,"quiet_below_auto_maintenance_trial natural_unload");unloadRequested=true;
            }
            return Files.exists(output.resolve("natural-unload.json"));
        },45_000);
        step("return to naturally load the source",()->send(client,"tp @s 1023.5 82 1023.5"),()->Math.abs(client.player.getX()-1023.5)<1,15_000);
        delay("allow source chunks to reload",2_000);
        command(client, "verify archive, shrink, append and identity", "complete");
        command(client, "start pending maintenance for clean shutdown join", "shutdown");
        waitFor("leave a genuinely pending worker for normal server stop", () -> poll(client) && truth("busy") && "shutdown_held".equals(state()), 20_000);
    }

    private boolean poll(MinecraftClient client) {
        if (System.nanoTime() >= nextPoll) { send(client, "quiet_below_auto_maintenance_trial status"); nextPoll = System.nanoTime() + 400_000_000L; }
        return status.has("state");
    }
    private boolean truth(String name) { return status.has(name) && status.get(name).getAsBoolean(); }
    private int number(String name) { return status.has(name) ? status.get(name).getAsInt() : 0; }
    private String state() { return status.has("state") ? status.get("state").getAsString() : ""; }
    private void command(MinecraftClient client, String name, String verb) {
        step(name, () -> send(client, "quiet_below_auto_maintenance_trial " + verb), () -> {
            for (String value : messages.subList(baseline, messages.size())) {
                require(!value.startsWith("FAIL Auto maintenance"), value);
                if (value.equals("PASS Auto maintenance " + verb)) return true;
            }
            return false;
        }, 30_000);
    }
    private void waitFor(String name, BooleanSupplier condition, long timeout) { step(name, () -> { status = new JsonObject(); nextPoll = 0; }, condition, timeout); }
    private void delay(String name, long millis) { step(name, () -> until = System.nanoTime() + millis * 1_000_000L, () -> System.nanoTime() >= until, millis + 5_000); }
    private void step(String name, Runnable start, BooleanSupplier done, long timeout) { steps.add(new Step(name, start, done, timeout)); }
    private static void send(MinecraftClient client, String command) { client.player.networkHandler.sendChatCommand(command); }
    private String failure() {
        try { return Files.readString(output.resolve("trial-failure.json")); }
        catch (IOException ignored) { return "No additional detail"; }
    }
    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (finished) return; finished = true;
        try { Files.writeString(output.resolve("client-" + phase + ".json"), new Gson().toJson(Map.of(
                "result", pass ? "PASS" : "FAIL", "phase", phase, "detail", detail, "checks", checks, "messages", messages))); }
        catch (IOException ignored) { }
        client.scheduleStop();
    }
    private static void setupFailure(Exception error) {
        try { if (OUTPUT != null) Files.writeString(Path.of(OUTPUT).resolve("client-" + PHASE + ".json"),
                new Gson().toJson(Map.of("result", "FAIL", "detail", "setup: " + error.getMessage()))); }
        catch (IOException ignored) { }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private record Step(String name, Runnable start, BooleanSupplier done, long timeoutMillis) { }
}
