package io.github.chronicle.core.server.testing;

import io.github.chronicle.core.areas.AreaChange;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.departures.DepartureDefinition;
import io.github.chronicle.core.server.AreaChanges;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.Departures;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.literal;

/** Opt-in CF19/20/21 catalog-editor fixture. It only prepares metadata; the client performs every editor mutation. */
public final class CatalogsTrial {
    private static final BlockPos HELM = new BlockPos(4, 64, 4);
    private static final String ANCHOR = "catalog_helm", REGION = "catalog_boarding";

    private CatalogsTrial() { }

    public static void register() {
        if (!"true".equals(System.getenv("CHRONICLE_CATALOGS_TRIAL"))) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                literal("chronicle_catalogs_trial").requires(source -> source.hasPermissionLevel(2))
                        .then(literal("prepare").executes(context -> run(context.getSource(), "prepare")))
                        .then(literal("verify").executes(context -> run(context.getSource(), "verify")))
                        .then(literal("verify_edit").executes(context -> run(context.getSource(), "verify_edit")))));
    }

    private static int run(ServerCommandSource source, String action) {
        try {
            fixture(source);
            if (action.equals("prepare")) prepare(source); else if(action.equals("verify_edit")){
                var definition=Departures.state(source.getServer()).definitions().values().stream().filter(d->d.name().equals("Edited departure")).findFirst().orElseThrow(()->new IllegalStateException("Edited departure not saved"));
                require(definition.revision()==2,"Editing did not advance exactly one revision");
                require(definition.areaLink().isPresent(),"Editing lost saved Area state");
                require(io.github.chronicle.core.server.SessionBuilding.enabled(source.getServer()),"Building Mode lost after editor use");
            } else verify(source);
            source.sendFeedback(() -> Text.literal("Catalogs trial PASS " + action), false);
            return 1;
        } catch (Exception error) {
            source.sendError(Text.literal("Catalogs trial FAIL " + action + ": " + error.getMessage()));
            return 0;
        }
    }

    private static void prepare(ServerCommandSource source) throws Exception {
        var server = source.getServer();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        require(AreaChanges.ready(server), "Area changes are unavailable");
        require(Departures.ready(server), "Departures are unavailable: " + Departures.problem(server));
        require(AreaChanges.catalog(server).areas().isEmpty(), "catalog Area list must start empty");
        require(Departures.state(server).definitions().values().stream().noneMatch(d->d.name().equals("Menu departure")), "catalog fixture Departure already exists");
        ServerWorld world = source.getWorld();
        for (int x = 0; x <= 9; x++) for (int z = 0; z <= 8; z++)
            world.setBlockState(new BlockPos(x, 63, z), Blocks.STONE.getDefaultState(), 3);
        world.setBlockState(HELM, Blocks.BARREL.getDefaultState(), 3);
        var authoring = AuthoringRuntime.get(server);
        authoring.enable(player.getUuid());
        authoring.service().put(new AuthoringSite.Anchor(ANCHOR, "minecraft:overworld", 1, position(HELM), "minecraft:barrel"), false);
        authoring.service().put(new AuthoringSite.Region(REGION, "minecraft:overworld", 1,
                new BlockPosition(2, 64, 2), new BlockPosition(7, 66, 7)), false);
        player.changeGameMode(GameMode.CREATIVE);
        player.teleport(world, 1.5, 64, 1.5, Set.of(), 0, 0, false);
        player.setVelocity(0, 0, 0); player.onLanding();
        io.github.chronicle.core.server.SessionBuilding.set(source,true);
    }

    private static void verify(ServerCommandSource source) {
        var server = source.getServer();
        AreaChange area = AreaChanges.catalog(server).areas().values().stream()
                .filter(value -> value.name().equals("Menu sample")).findFirst()
                .orElseThrow(() -> new IllegalStateException("UI did not persist Menu sample"));
        require(io.github.chronicle.core.server.SessionBuilding.enabled(server),"Building Mode was discarded by Area capture");
        require(area.end().isPresent() && area.start().isPresent(), "Menu sample must retain its UI-captured Start and End snapshots");
        DepartureDefinition departure = Departures.state(server).definitions().values().stream()
                .filter(value -> value.name().equals("Menu departure")).findFirst()
                .orElseThrow(() -> new IllegalStateException("UI did not persist Menu departure"));
        require(departure.exactBlockAnchor().isPresent() && departure.exactBlockAnchor().get().id().equals(ANCHOR), "departure did not pin catalog_helm");
        require(departure.boardingRegion().isPresent() && departure.boardingRegion().get().id().equals(REGION), "departure did not pin catalog_boarding");
        require(departure.areaLink().isPresent() && departure.areaLink().get().areaId().equals(area.id()), "departure did not pin Menu sample");
        require(departure.areaLink().get().requestedState().name().equals("END"), "departure did not retain End state");
    }

    private static BlockPosition position(BlockPos value) { return new BlockPosition(value.getX(), value.getY(), value.getZ()); }
    private static void fixture(ServerCommandSource source) {
        var server = source.getServer();
        var root = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
        require(root != null && root.getParent() != null && root.getParent().getFileName().toString().equals("test-runs")
                        && root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")
                        && Files.isRegularFile(root.resolve(".catalogs-fixture")) && server.isDedicated() && !server.isOnlineMode()
                        && server.getServerPort() == 25575 && "127.0.0.1".equals(server.getServerIp())
                        && source.getWorld().getRegistryKey() == net.minecraft.world.World.OVERWORLD,
                "unsafe catalog fixture");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
