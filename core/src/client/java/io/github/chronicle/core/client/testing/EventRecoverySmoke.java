package io.github.chronicle.core.client.testing;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.server.testing.EventActionsTrial;
import io.github.chronicle.core.server.testing.EventRecoveryTrial;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Real-player driver for the isolated CF22 conflict/recovery and maximum-apply proof. */
public final class EventRecoverySmoke {
    private static final String OUTPUT = System.getenv("CHRONICLE_EVENT_RECOVERY_SMOKE");
    private static final String ROOT = System.getenv("CHRONICLE_EVENT_RECOVERY_SERVER_DIR");
    private static final String PHASE = System.getenv("CHRONICLE_EVENT_RECOVERY_PHASE");
    private static final String ADDRESS = "127.0.0.1:25575";
    private static EventRecoverySmoke instance;

    private final Path output;
    private final Path root;
    private final List<String> messages = new ArrayList<>();
    private final List<String> checks = new ArrayList<>();
    private final long began = System.nanoTime();
    private boolean connecting;
    private boolean done;
    private int step;

    private EventRecoverySmoke() {
        output = Path.of(OUTPUT).toAbsolutePath().normalize();
        root = Path.of(ROOT).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve(".event-actions-fixture"))
                || !Files.isRegularFile(root.resolve(".event-recovery-fixture"))
                || output.startsWith(root)) {
            throw new IllegalArgumentException("refusing Event recovery smoke outside disposable fixture");
        }
        if (!List.of("conflict", "recovery", "player", "player_recovery", "maximum").contains(PHASE))
            throw new IllegalArgumentException("unknown Event recovery smoke phase");
    }

    public static void register() {
        if (OUTPUT == null) return;
        try {
            instance = new EventRecoverySmoke();
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
                if (!overlay) instance.messages.add(message.getString());
            });
            ClientTickEvents.END_CLIENT_TICK.register(instance::tick);
        } catch (Exception failure) {
            ChronicleCore.LOGGER.error("Event recovery smoke setup failed", failure);
        }
    }

    private void tick(MinecraftClient client) {
        if (done) return;
        try {
            long timeout = PHASE.equals("maximum") ? 1_200_000_000_000L : 600_000_000_000L;
            if (System.nanoTime() - began > timeout)
                throw new IllegalStateException("Event recovery smoke timeout at step " + step);
            if (client.player == null && client.getOverlay() == null
                    && client.currentScreen instanceof AccessibilityOnboardingScreen)
                client.setScreen(new TitleScreen());
            if (!connecting && client.player == null && client.getOverlay() == null
                    && client.currentScreen instanceof TitleScreen) {
                connecting = true;
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(ADDRESS),
                        new ServerInfo("Event recovery smoke", ADDRESS, ServerInfo.ServerType.OTHER), false, null);
                return;
            }
            if (client.player == null) return;
            client.getToastManager().clear();
            switch (PHASE) {
                case "conflict" -> conflict(client);
                case "recovery" -> recovery(client);
                case "player" -> playerConflict(client);
                case "player_recovery" -> playerRecovery(client);
                case "maximum" -> maximum(client);
                default -> throw new IllegalStateException();
            }
        } catch (Throwable failure) {
            finish(client, false, failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private void conflict(MinecraftClient client) {
        switch (step) {
            case 0 -> { command(client, "chronicle_events_trial prepare_ui"); step++; }
            case 1 -> {
                if (!passed("Events trial", "prepare_ui")) return;
                command(client, "chronicle_events_trial configure"); step++;
            }
            case 2 -> {
                if (!passed("Events trial", "configure")) return;
                command(client, "chronicle_events_trial activate"); step++;
            }
            case 3 -> {
                if (!passed("Events trial", "activate")) return;
                command(client, "chronicle_events_trial enable_flag"); step++;
            }
            case 4 -> {
                if (!passed("Events trial", "enable_flag")) return;
                command(client, "chronicle_events_trial target_world"); step++;
            }
            case 5 -> {
                if (!passed("Events trial", "target_world")) return;
                command(client, "chronicle_event_recovery_trial run_conflict"); step++;
            }
            case 6 -> {
                if (!passed("Event recovery trial", "conflict")) return;
                require(count(EventActionsTrial.WORLD_MESSAGE) == 0,
                        "conflicting Event emitted media before exact recovery");
                checks.add("target 0 durably saved before a foreign target-1 cell produced CONFLICT without overwrite or early media");
                finish(client, true, "committed multi-Region conflict retained exact recovery authority");
            }
            default -> throw new IllegalStateException("invalid conflict step");
        }
    }

    private void recovery(MinecraftClient client) {
        switch (step) {
            case 0 -> { command(client, "chronicle_event_recovery_trial recover_conflict"); step++; }
            case 1 -> {
                if (!passed("Event recovery trial", "recover_conflict")) return;
                require(count(EventActionsTrial.WORLD_MESSAGE) == 0,
                        "recovery media was sent to a supervisor outside the pinned audience");
                checks.add("a distinct live OP restored the one foreign cell and recovered the same UUID to exact terrain/NBT completion");
                checks.add("the absent pinned recipient received no catch-up media during supervisor recovery");
                finish(client, true, "explicit second-operator recovery completed without UUID replacement");
            }
            default -> throw new IllegalStateException("invalid recovery step");
        }
    }

    private void playerConflict(MinecraftClient client) {
        switch (step) {
            case 0 -> { command(client, "chronicle_events_trial prepare_ui"); step++; }
            case 1 -> {
                if (!passed("Events trial", "prepare_ui")) return;
                command(client, "chronicle_events_trial configure"); step++;
            }
            case 2 -> {
                if (!passed("Events trial", "configure")) return;
                command(client, "chronicle_events_trial activate"); step++;
            }
            case 3 -> {
                if (!passed("Events trial", "activate")) return;
                command(client, "chronicle_events_trial enable_flag"); step++;
            }
            case 4 -> {
                if (!passed("Events trial", "enable_flag")) return;
                command(client, "chronicle_events_trial target_world"); step++;
            }
            case 5 -> {
                if (!passed("Events trial", "target_world")) return;
                command(client, "chronicle_event_recovery_trial run_player"); step++;
            }
            case 6 -> {
                if (!passed("Event recovery trial", "player")) return;
                require(count(EventActionsTrial.WORLD_MESSAGE) == 0,
                        "player-ingress conflict emitted media before exact recovery");
                checks.add("initiating player physically entered target 1 only after target 0's durable save and left the committed batch HELD");
                checks.add("player ingress performed no target-1 writes and emitted no early media");
                finish(client, true, "committed player-ingress hold retained exact recovery authority");
            }
            default -> throw new IllegalStateException("invalid player-ingress step");
        }
    }

    private void playerRecovery(MinecraftClient client) {
        switch (step) {
            case 0 -> { command(client, "chronicle_event_recovery_trial recover_player"); step++; }
            case 1 -> {
                if (!passed("Event recovery trial", "player_recovery")) return;
                require(count(EventActionsTrial.WORLD_MESSAGE) == 0,
                        "player-ingress recovery media was sent to a supervisor outside the pinned audience");
                checks.add("after the initiating player disconnected, a distinct live OP recovered the same UUID without terrain repair");
                checks.add("the absent pinned recipient received no catch-up media after player-ingress recovery");
                finish(client, true, "explicit second-operator player-ingress recovery completed");
            }
            default -> throw new IllegalStateException("invalid player-ingress recovery step");
        }
    }

    private void maximum(MinecraftClient client) {
        switch (step) {
            case 0 -> { command(client, "chronicle_event_recovery_trial max_clear"); step++; }
            case 1 -> {
                if (!passed("Event recovery trial", "max_clear")) return;
                command(client, "chronicle_events_trial prepare_max"); step++;
            }
            case 2 -> {
                if (!passed("Events trial", "prepare_max")) return;
                command(client, "chronicle_event_recovery_trial max_fill_configure"); step++;
            }
            case 3 -> {
                if (!passed("Event recovery trial", "max_fill_configure")) return;
                command(client, "chronicle_events_trial activate"); step++;
            }
            case 4 -> {
                if (!passed("Events trial", "activate")) return;
                command(client, "chronicle_event_recovery_trial max_position"); step++;
            }
            case 5 -> {
                if (!passed("Event recovery trial", "max_position")) return;
                command(client, "chronicle_event_recovery_trial max_run"); step++;
            }
            case 6 -> {
                if (!passed("Event recovery trial", "max_run") || !seen(EventRecoveryTrial.MAX_MESSAGE)) return;
                require(count(EventRecoveryTrial.MAX_MESSAGE) == 1,
                        "maximum Event media was not emitted exactly once after completion");
                checks.add("actual EventRuntime APPLY changed and verified all 262144 cells across exactly 64 player-loaded chunks");
                checks.add("maximum apply completed with no forced chunks and emitted media only after exact world completion");
                finish(client, true, "exact supported-volume Event apply passed");
            }
            default -> throw new IllegalStateException("invalid maximum step");
        }
    }

    private void command(MinecraftClient client, String command) {
        client.player.networkHandler.sendChatCommand(command);
    }

    private boolean passed(String prefix, String action) {
        for (String message : messages) {
            if (message.contains(prefix + " FAIL ")) throw new IllegalStateException(message);
            if (message.contains(prefix + " PASS " + action)) return true;
        }
        return false;
    }

    private boolean seen(String value) {
        return messages.stream().anyMatch(message -> message.contains(value));
    }

    private int count(String value) {
        return (int) messages.stream().filter(message -> message.contains(value)).count();
    }

    private void finish(MinecraftClient client, boolean pass, String detail) {
        if (done) return;
        done = true;
        try {
            Files.createDirectories(output.getParent());
            JsonObject value = new JsonObject();
            value.addProperty("result", pass ? "PASS" : "FAIL");
            value.addProperty("phase", PHASE);
            value.addProperty("detail", detail);
            JsonArray array = new JsonArray();
            checks.forEach(array::add);
            value.add("checks", array);
            Files.writeString(output, new Gson().toJson(value));
        } catch (Exception ignored) {}
        client.scheduleStop();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
