package io.github.chronicle.quietbelow.ecology.testing;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.chronicle.core.server.MapSiteAssignments;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Path;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Finite map-assignment bridge for disposable Quiet Below native fixtures only. */
public final class CanonicalAuthoringTrial {
    private static final String ECOLOGY_ENV = "CHRONICLE_ECOLOGY_NATIVE_TRIAL";
    private static final String WARDEN_ENV = "CHRONICLE_WARDEN_NATIVE_TRIAL";
    private static final String HEART_ENV = "CHRONICLE_HEART_REPRO_SMOKE";
    private static final String HEART_ROOT_ENV = "CHRONICLE_HEART_REPRO_SERVER_DIR";

    private CanonicalAuthoringTrial() { }

    public static void register() {
        if (!scenarioEnabled() && !heartEnabled()) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                literal("quiet_below_authoring_trial").requires(source -> source.hasPermissionLevel(2))
                        .then(literal("assign-source").then(argument("site", StringArgumentType.string())
                                .executes(context -> assign(context.getSource(), "source", "quiet_below:source_city",
                                        StringArgumentType.getString(context, "site")))))
                        .then(literal("assign-heart").then(argument("site", StringArgumentType.string())
                                .executes(context -> assign(context.getSource(), "heart", "quiet_below:relic_heart",
                                        StringArgumentType.getString(context, "site")))))
                        .then(literal("assign-takeover").then(argument("site", StringArgumentType.string())
                                .executes(context -> assign(context.getSource(), "takeover", "quiet_below:sculk_takeover",
                                        StringArgumentType.getString(context, "site")))))
                        .then(literal("clear-heart").executes(context -> clearHeart(context.getSource())))));
    }

    private static int assign(ServerCommandSource source, String label, String requirement, String site) {
        return command(source, () -> {
            MapSiteAssignments.assign(source, requirement, site);
            return "PASS canonical authoring assign-" + label + ": " + requirement + " -> " + site + ".";
        });
    }

    private static int clearHeart(ServerCommandSource source) {
        return command(source, () -> {
            MapSiteAssignments.clear(source, "quiet_below:relic_heart");
            return "PASS canonical authoring clear-heart.";
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

    private static void fixture(ServerCommandSource source) throws IOException {
        var server = source.getServer();
        Path root = server.getRunDirectory().toRealPath();
        boolean ecology = scenarioEnabled();
        boolean heart = heartEnabled();
        require(root.getParent() != null && root.getParent().getFileName().toString().equals("test-runs")
                        && root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")
                        && server.isDedicated() && !server.isOnlineMode()
                        && server.getServerPort() == 25575 && "127.0.0.1".equals(server.getServerIp())
                        && source.getWorld() == server.getOverworld() && source.getPlayer() != null
                        && ((ecology && source.getName().equals("ChronicleFabricA"))
                        || (heart && source.getName().equals("HeartRepro"))),
                "unsafe Quiet Below canonical-authoring fixture");
        if (heart) {
            String configured = System.getenv(HEART_ROOT_ENV);
            require(configured != null && !configured.isBlank()
                            && root.equals(Path.of(configured).toRealPath()),
                    "Heart reproduction server root does not match its explicit fixture root");
        }
    }

    private static boolean scenarioEnabled() { return flag(ECOLOGY_ENV) || flag(WARDEN_ENV); }
    private static boolean flag(String name) { String value=System.getenv(name);return "1".equals(value)||"true".equals(value); }
    private static boolean heartEnabled() { return enabled(HEART_ENV) && enabled(HEART_ROOT_ENV); }

    private static boolean enabled(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @FunctionalInterface
    private interface Operation { String run() throws IOException; }
}
