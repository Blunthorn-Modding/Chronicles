package io.github.chronicle.core.client.testing;

import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.nio.file.Files;
import java.nio.file.Path;

/** Connects a fixture actor and relays the server-side proof to the runner. */
public final class PersonalCopyCrashSmoke {
    private static final String OUTPUT = System.getenv("CHRONICLE_PERSONAL_COPY_SMOKE"), ROOT = System.getenv("CHRONICLE_PERSONAL_COPY_SERVER_DIR");
    private final Path output, root;
    private final long started = System.nanoTime();
    private boolean connecting, done;
    private PersonalCopyCrashSmoke() {
        output = Path.of(OUTPUT).toAbsolutePath().normalize(); root = Path.of(ROOT).toAbsolutePath().normalize();
        if (root.getParent() == null || !root.getParent().getFileName().toString().equals("test-runs")
                || !root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")
                || !Files.isRegularFile(root.resolve(".personal-copy-fixture")) || output.startsWith(root)) throw new IllegalArgumentException("Unsafe personal-copy client fixture.");
    }
    public static void register() {
        if (OUTPUT == null) return;
        var smoke = new PersonalCopyCrashSmoke(); ClientTickEvents.END_CLIENT_TICK.register(smoke::tick);
    }
    private void tick(MinecraftClient client) {
        if (done) return;
        try {
            if (System.nanoTime() - started > 180_000_000_000L) throw new IllegalStateException("Personal-copy client timeout.");
            if (client.player == null && client.currentScreen instanceof AccessibilityOnboardingScreen) client.setScreen(new TitleScreen());
            if (!connecting && client.player == null && client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                connecting = true; ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse("127.0.0.1:25575"), new ServerInfo("Personal-copy fixture", "127.0.0.1:25575", ServerInfo.ServerType.OTHER), false, null); return;
            }
            Path result = root.resolve(".personal-copy-result.json");
            if (Files.isRegularFile(result)) finish(client, BookConfig.strictObject(Files.readString(result)));
        } catch (Exception failure) {
            JsonObject result = new JsonObject(); result.addProperty("result", "FAIL"); result.addProperty("detail", failure.toString()); finish(client, result);
        }
    }
    private void finish(MinecraftClient client, JsonObject result) {
        if (done) return; done = true;
        try { Files.createDirectories(output.getParent()); Files.writeString(output, result + "\n"); }
        catch (Exception failure) { io.github.chronicle.core.ChronicleCore.LOGGER.error("Cannot write personal-copy smoke result", failure); }
        client.scheduleStop();
    }
}
