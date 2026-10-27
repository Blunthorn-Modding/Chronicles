package io.github.chronicle.core.server.testing;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.chronicle.core.play.ObservationConfiguration;
import io.github.chronicle.core.play.PlayState;
import io.github.chronicle.core.server.PlayRuntime;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Strictly disposable loopback helpers for the default Fabric regression's backend assertions. */
public final class RegressionTrial {
    private static final String ENABLE_ENV = "CHRONICLE_FABRIC_SMOKE";
    private static final String SERVER_ENV = "CHRONICLE_FABRIC_SERVER_DIR";

    private RegressionTrial() { }

    public static void register() {
        if (System.getenv(ENABLE_ENV) == null || System.getenv(SERVER_ENV) == null) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                literal("chronicle_regression_trial").requires(source -> source.hasPermissionLevel(2))
                        .then(literal("observation")
                                .then(literal("entry")
                                        .then(argument("id", StringArgumentType.string())
                                                .then(argument("region", StringArgumentType.string())
                                                        .then(argument("flag", StringArgumentType.string())
                                                                .executes(context -> bind(context.getSource(), PlayState.Mode.ENTRY,
                                                                        StringArgumentType.getString(context, "id"),
                                                                        StringArgumentType.getString(context, "region"),
                                                                        StringArgumentType.getString(context, "flag"), 0, false))))))
                                .then(literal("timer")
                                        .then(argument("id", StringArgumentType.string())
                                                .then(argument("seconds", IntegerArgumentType.integer(1, (int) ObservationConfiguration.MAX_DELAY_SECONDS))
                                                        .executes(context -> bind(context.getSource(), PlayState.Mode.TIMER,
                                                                StringArgumentType.getString(context, "id"), "", "",
                                                                IntegerArgumentType.getInteger(context, "seconds"), false)))))
                                .then(literal("replace-timer")
                                        .then(argument("id", StringArgumentType.string())
                                                .then(argument("seconds", IntegerArgumentType.integer(1, (int) ObservationConfiguration.MAX_DELAY_SECONDS))
                                                        .executes(context -> bind(context.getSource(), PlayState.Mode.TIMER,
                                                                StringArgumentType.getString(context, "id"), "", "",
                                                                IntegerArgumentType.getInteger(context, "seconds"), true)))))
                                .then(literal("reset").then(argument("id", StringArgumentType.string())
                                        .executes(context -> reset(context.getSource(), StringArgumentType.getString(context, "id"))))))));
    }

    private static int bind(ServerCommandSource source, PlayState.Mode mode, String id, String region,
                            String flag, int seconds, boolean replace) {
        return command(source, () -> {
            PlayRuntime.bind(source, id, new ObservationConfiguration(mode, region, flag, seconds), replace);
            return "Regression observation " + id + (replace ? " replaced." : " configured.");
        });
    }

    private static int reset(ServerCommandSource source, String id) {
        return command(source, () -> {
            PlayRuntime.reset(source, id);
            return "Regression observation " + id + " reset.";
        });
    }

    private static int command(ServerCommandSource source, Operation operation) {
        try {
            fixture(source);
            String result = operation.run();
            source.sendFeedback(() -> Text.literal(result), false);
            return 1;
        } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
            source.sendError(Text.literal(failure.getMessage()));
            return 0;
        }
    }

    private static void fixture(ServerCommandSource source) {
        var server = source.getServer();
        Path world = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        Path root = world.getParent();
        String configured = System.getenv(SERVER_ENV);
        Path expected = configured == null ? null : Path.of(configured).toAbsolutePath().normalize();
        require(root != null && root.equals(expected)
                        && root.getParent() != null && root.getParent().getFileName().toString().equals("test-runs")
                        && root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")
                        && Files.isDirectory(world) && server.isDedicated() && !server.isOnlineMode()
                        && server.getServerPort() == 25575 && "127.0.0.1".equals(server.getServerIp())
                        && source.getWorld().getRegistryKey() == net.minecraft.world.World.OVERWORLD
                        && source.getPlayer() != null && source.getName().equals("ChronicleFabricA"),
                "unsafe Fabric regression fixture");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @FunctionalInterface
    private interface Operation { String run() throws IOException; }
}
