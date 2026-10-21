package io.github.chronicle.quietbelow.client;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/** Long-running opt-in client driver for the real generated ancient-city fixture. */
final class QuietBelowCitySmoke {
    private static final String OUTPUT = System.getenv("CHRONICLE_ECOLOGY_SMOKE");
    private static final String PHASE = System.getenv("CHRONICLE_ECOLOGY_PHASE");
    private static final String ADDRESS = "127.0.0.1:25575";
    private final List<String> messages = new ArrayList<>(), checks = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private final long overallDeadline = System.nanoTime() + 1_200_000_000_000L;
    private int index, baseline;
    private long deadline, until, nextVisit, nextCertification;
    private boolean connected, started, built, finished;

    static void register() {
        if (OUTPUT == null || !("city".equals(PHASE) || "city_restart".equals(PHASE))) return;
        QuietBelowCitySmoke smoke = new QuietBelowCitySmoke();
        ClientReceiveMessageEvents.GAME.register((text, overlay) -> smoke.messages.add(text.getString()));
        ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
    }

    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            require(System.nanoTime() < overallDeadline, "ancient-city smoke overall timeout");
            if (!connected && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                connected = true;
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS), new ServerInfo("Chronicle ancient-city fixture", ADDRESS, ServerInfo.ServerType.OTHER), false, null);
            }
            if (client.player == null || client.world == null || client.currentScreen != null) return;
            if (!built) { if ("city".equals(PHASE)) city(client); else restart(client); built = true; }
            if (index >= steps.size()) { finish(client, true, "ancient-city rehearsal complete"); return; }
            Step step = steps.get(index);
            if (!started) { baseline = messages.size(); deadline = System.nanoTime() + step.timeoutMillis * 1_000_000L; step.start.run(); started = true; }
            if (step.done.getAsBoolean()) { checks.add(step.name); index++; started = false; }
            else require(System.nanoTime() < deadline, "timed out: " + step.name);
        } catch (Exception error) { finish(client, false, error.toString()); }
    }

    private void city(MinecraftClient client) {
        client.options.getViewDistance().setValue(12);client.options.sendClientSettings();
        command("prepare a generated ancient city with exact native bounds", "quiet_below_city_trial prepare", "PASS ancient city prepared:.*", 180_000);
        command("start normal loaded-only ancient-city index", "chronicles setup tool \"quiet_below:ecology\" index", "Source indexing started.*", 20_000);
        indexCity();
        command("record actual bounds chunks cells and completed index", "quiet_below_city_trial indexed", "PASS ancient city indexed:.*", 30_000);
        command("normally load the whole city at native 20 TPS", "quiet_below_city_trial converge", "CITY_CONVERGE.*", 30_000);
        delay("allow normal whole-city client loading",15_000);
        command("enable indexed native-city ecology", "chronicles setup tool \"quiet_below:ecology\" enable", "Growth enabled.*", 20_000);
        command("start native-city session", "chronicle session start", "Started Chronicle session.*", 20_000);
        command("join native-city session", "chronicles join", ".*(joined|already).*Chronicle participation.*", 20_000);
        certifyCity();
        soakCity();
        command("move the actual client away for ordinary city chunk unload", "quiet_below_city_trial unload", "CITY_UNLOAD.*", 20_000);
        awaitUnload();
        delay("unloaded eligible city remains stable", 5_000);
        command("unresolved unloaded city has no writes or vitality decay", "quiet_below_city_trial stable", "PASS ancient city unloaded stability:.*", 30_000);
        command("save paused native-city world", "chronicle session pause", "Paused Chronicle session.*", 20_000);
        command("persist city owned/vitality snapshot before restart", "quiet_below_city_trial save", "PASS ancient city saved:.*", 20_000);
    }

    private void restart(MinecraftClient client) {
        client.options.getViewDistance().setValue(12);client.options.sendClientSettings();
        command("restore native city exact pins and indexed ownership", "quiet_below_city_trial restart", "PASS ancient city restart:.*", 30_000);
        command("return native client to the whole city after restart", "quiet_below_city_trial converge", "CITY_CONVERGE.*", 20_000);
        delay("allow ordinary client chunk reload after restart", 5_000);
        command("resume restarted native-city play", "chronicle session resume", "Resumed Chronicle session.*", 20_000);
        command("join restarted native-city play", "chronicles join", ".*(joined.*Chronicle participation|already a Chronicle participant).*", 20_000);
        certifyCity();
        soakCity();
        command("leave restarted ancient city paused", "chronicle session pause", "Paused Chronicle session.*", 20_000);
    }

    private void command(String name, String command, String response, long timeout) { step(name, () -> send(command), () -> received(response), timeout); }
    private void indexCity() {
        step("move native client through actual city until normal index completes", () -> {
            send("quiet_below_city_trial visit"); nextVisit = System.nanoTime() + 600_000_000L;
        }, () -> {
            if (received("CITY_INDEX_READY.*")) return true;
            if (System.nanoTime() >= nextVisit) { send("quiet_below_city_trial visit"); nextVisit = System.nanoTime() + 600_000_000L; }
            return false;
        }, 720_000);
    }
    private void certifyCity() {
        step("certify rooted city in eligible non-author play", () -> {
            send("quiet_below_city_trial certified"); nextCertification = System.nanoTime() + 500_000_000L;
        }, () -> {
            if (received("PASS ancient city certified:.*")) return true;
            if (System.nanoTime() >= nextCertification) { send("quiet_below_city_trial certified"); nextCertification = System.nanoTime() + 500_000_000L; }
            return false;
        }, 90_000);
    }
    private void soakCity(){
        command("start full-city native 20 TPS performance soak","quiet_below_city_trial soak","CITY_SOAK.*",20_000);
        delay("sample a minute of full-city occupied play",61_000);
        step("verify measured native and ecology work budgets",()->{send("quiet_below_city_trial measured");nextCertification=System.nanoTime()+1_000_000_000L;},()->{
            if(received("PASS ancient city measured:.*"))return true;
            if(System.nanoTime()>=nextCertification){send("quiet_below_city_trial measured");nextCertification=System.nanoTime()+1_000_000_000L;}return false;
        },60_000);
    }
    private void awaitUnload(){
        step("wait for natural city chunk unload and capture settled baseline",()->{send("quiet_below_city_trial unloaded");nextCertification=System.nanoTime()+1_000_000_000L;},()->{
            if(received("PASS ancient city unload:.*"))return true;
            if(System.nanoTime()>=nextCertification){send("quiet_below_city_trial unloaded");nextCertification=System.nanoTime()+1_000_000_000L;}return false;
        },120_000);
    }
    private void delay(String name, long millis) { step(name, () -> until = System.nanoTime() + millis * 1_000_000L, () -> System.nanoTime() >= until, millis + 5_000); }
    private void step(String name, Runnable start, BooleanSupplier done, long timeout) { steps.add(new Step(name, start, done, timeout)); }
    private void send(String command) { MinecraftClient.getInstance().player.networkHandler.sendChatCommand(command); }
    private boolean received(String expression) {
        for (String message : messages.subList(baseline, messages.size())) {
            if (message.startsWith("FAIL ancient city trial:")) throw new IllegalStateException(message);
            if (Pattern.compile(expression, Pattern.CASE_INSENSITIVE).matcher(message).matches()) return true;
        }
        return false;
    }
    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (finished) return; finished = true;
        try { Files.createDirectories(Path.of(OUTPUT)); Files.writeString(Path.of(OUTPUT).resolve("ecology-" + PHASE + ".json"), new Gson().toJson(Map.of("result", pass ? "PASS" : "FAIL", "detail", detail, "checks", checks, "step", index < steps.size() ? steps.get(index).name : "complete", "messages", messages))); }
        catch (Exception error) { error.printStackTrace(); }
        client.scheduleStop();
    }
    private static void require(boolean condition, String detail) { if (!condition) throw new IllegalStateException(detail); }
    private record Step(String name, Runnable start, BooleanSupplier done, long timeoutMillis) { }
}
