package io.github.chronicle.quietbelow.ecology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.core.server.MapSiteAssignments;
import io.github.chronicle.core.server.PlayRuntime;
import io.github.chronicle.quietbelow.QuietBelow;
import io.github.chronicle.quietbelow.content.AncientForgeBlock;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * An explicitly opted-in, disposable-world rehearsal of the live automatic maintenance
 * coordinator.  Its worker callback is deliberately latch-only: it never reaches Minecraft,
 * the runtime, or the live engine off the server thread.
 */
public final class EcologyAutoMaintenanceTrial {
    private static final int BASE = 1024;
    private static final BlockPos HEART = new BlockPos(BASE, 80, BASE);
    private static final BlockPos SOURCE = new BlockPos(BASE, 79, BASE);
    private static final BlockPos FORGE = new BlockPos(BASE + 1, 80, BASE);
    private static final EcologyMaintenancePolicy POLICY = new EcologyMaintenancePolicy(4096, 20, 8192, 40, 100, 1024, 10);
    private static final Map<MinecraftServer, Stage> STAGES = new IdentityHashMap<>();

    private enum Mode { PREPARED, PAUSED_WAITING, PAUSED_HELD, PAUSED_DONE, ACTIVE_ARMING, ACTIVE_WAITING, ACTIVE_HELD, ACTIVE_RELEASING, ACTIVE_DONE, SHUTDOWN_HELD }

    private static final class Stage {
        Mode mode = Mode.PREPARED;
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        int engineIdentity;
        long ticksAtHold;
        long terrainAtHold;
        long activeTicksAtHold;
        long activeTerrainAtHold;
        boolean unloaded;
        boolean rootWriteRejected;
        boolean shutdownProofWritten;
    }

    private EcologyAutoMaintenanceTrial() { }

    public static void register() {
        if (!"true".equals(System.getenv("CHRONICLE_ECOLOGY_AUTO_MAINTENANCE_TRIAL"))) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var root = literal("quiet_below_auto_maintenance_trial").requires(source -> source.hasPermissionLevel(2));
            for (String verb : Set.of("prepare", "paused", "during", "queue_unload", "natural_unload", "release", "active", "complete", "shutdown", "restart", "status")) {
                root.then(literal(verb).executes(context -> run(context.getSource(), verb)));
            }
            dispatcher.register(root);
        });
        ServerTickEvents.END_SERVER_TICK.register(EcologyAutoMaintenanceTrial::tick);
        // QuietBelowEcology registers its stop hook before this trial is registered.  The trial
        // therefore observes the coordinator only after its shutdown join/release boundary.
        ServerLifecycleEvents.SERVER_STOPPING.register(EcologyAutoMaintenanceTrial::stopping);
    }

    /** Called by the production-only test seam before a low-threshold policy is installed. */
    static void requireFixture(MinecraftServer server) {
        try {
            Path run = server.getRunDirectory().toRealPath();
            require("true".equals(System.getenv("CHRONICLE_ECOLOGY_AUTO_MAINTENANCE_TRIAL")),
                    "Automatic maintenance trial requires explicit opt-in");
            require(server.isDedicated() && !server.isOnlineMode()
                            && "127.0.0.1".equals(server.getServerIp()) && server.getServerPort() == 25575,
                    "Automatic maintenance trial requires the dedicated offline loopback fixture");
            require(run.getParent() != null && "test-runs".equals(run.getParent().getFileName().toString())
                            && run.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z"),
                    "Automatic maintenance trial requires a timestamped direct test-runs child");
            require(Files.isRegularFile(run.resolve(".ecology-auto-maintenance-fixture")),
                    "Missing automatic-maintenance fixture marker");
        } catch (IOException error) {
            throw new IllegalStateException("Automatic maintenance trial fixture cannot be resolved", error);
        }
    }

    private static int run(ServerCommandSource source, String verb) {
        try {
            guard(source);
            switch (verb) {
                case "prepare" -> prepare(source);
                case "paused" -> paused(source);
                case "during" -> during(source);
                case "queue_unload" -> queuedUnload(source);
                case "natural_unload" -> naturalUnload(source);
                case "release" -> release(source);
                case "active" -> active(source);
                case "complete" -> complete(source);
                case "shutdown" -> shutdown(source);
                case "restart" -> restart(source);
                case "status" -> { source.sendFeedback(() -> Text.literal("AUTO " + evidence(source.getServer(), stage(source.getServer()).mode.name().toLowerCase())), false); return 1; }
                default -> throw new IllegalArgumentException("Unknown automatic maintenance action");
            }
            source.sendFeedback(() -> Text.literal("PASS Auto maintenance " + verb), false);
            return 1;
        } catch (Exception error) {
            source.sendError(Text.literal("FAIL Auto maintenance " + verb + ": " + message(error)));
            return 0;
        }
    }

    private static void guard(ServerCommandSource source) throws IOException {
        requireFixture(source.getServer());
        require(source.getWorld().getRegistryKey() == World.OVERWORLD, "Overworld fixture required");
        reports(source.getServer());
    }

    private static void prepare(ServerCommandSource source) throws Exception {
        MinecraftServer server = source.getServer();
        ServerWorld world = source.getWorld();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        EcologyEngine engine = QuietBelowEcology.engineForTest(server);
        require(engine != null && engine.setup() == null, "Fixture is not fresh");
        for (int chunkX : Set.of((BASE - 5) >> 4, BASE >> 4)) for (int chunkZ : Set.of((BASE - 5) >> 4, BASE >> 4))
            require(world.getChunkManager().getWorldChunk(chunkX, chunkZ) != null,
                    "Wait for the player to naturally load all fixture chunks");

        player.interactionManager.changeGameMode(GameMode.CREATIVE);
        player.teleport(world, BASE + 8.5, 82, BASE + 8.5, Set.of(), 180, 30, false);
        for (int x = -4; x <= 3; x++) for (int z = -4; z <= 3; z++) {
            world.setBlockState(new BlockPos(BASE + x, 79, BASE + z), Blocks.SCULK.getDefaultState());
        }
        world.setBlockState(HEART, QuietBelow.RELIC_HEART.getDefaultState());
        world.setBlockState(FORGE, QuietBelow.ANCIENT_FORGE_BLOCK.getDefaultState());

        var author = AuthoringRuntime.get(server);
        author.enable(player.getUuid());
        String dimension = world.getRegistryKey().getValue().toString();
        var region = (AuthoringSite.Region) author.service().put(new AuthoringSite.Region(
                "auto_maintenance_source", dimension, 1,
                new BlockPosition(BASE - 5, 76, BASE - 5), new BlockPosition(BASE + 5, 82, BASE + 5)), false);
        var heart = (AuthoringSite.Anchor) author.service().put(new AuthoringSite.Anchor(
                "auto_maintenance_heart", dimension, 1, new BlockPosition(BASE, 80, BASE), "quiet_below:relic_heart"), false);
        MapSiteAssignments.assign(source, "quiet_below:source_city", region.id());
        MapSiteAssignments.assign(source, "quiet_below:relic_heart", heart.id());
        engine.configure(new EcologyEngine.Setup(region.id(), region.revision(), heart.id(), heart.revision(), dimension),
                new EcologyEngine.Area(new EcologyTerrain.Pos(BASE - 5, 76, BASE - 5), new EcologyTerrain.Pos(BASE + 5, 82, BASE + 5)),
                QuietBelowEcology.pos(HEART));
        var terrain = new QuietBelowEcology.Adapter(world);
        for (int n = 0; n < 100 && !engine.view().indexed(); n++) engine.index(terrain);
        require(engine.view().indexed() && engine.cells().size() >= 64, "Source did not index its real 64 sculk cells");
        engine.tuneHeart(new EcologyHeart.Rules(1,60));
        var vitality = engine.vitality().rules();
        engine.tuneVitality(new EcologyVitality.Rules(vitality.lossMinutes(), vitality.recoveryMinutes(), 0));
        engine.mode(true);
        for (int n = 0; n < 1000 && (!engine.heartProof(true).complete() || engine.forgeHeat() != 2); n++) {
            engine.beginTick(); engine.tick(terrain, true);
        }
        require(engine.heartProof(true).complete() && engine.forgeHeat() == 2,
                "Real source topology did not settle to the established Forge-ready proof");
        author.disable(player.getUuid());
        ChronicleRuntime.service(server).start(actor(), null);
        PlayRuntime.setSelfParticipant(player, true);
        ChronicleRuntime.service(server).pause(actor());

        // This preparation-only alternating history is intentionally reducible. It is not a
        // production action and does not synthesize a journal file.
        appendHistory(engine, 120);
        Stage stage = new Stage();
        stage.engineIdentity = System.identityHashCode(engine);
        STAGES.put(server, stage);
        QuietBelowEcology.maintenancePolicyForTest(server, POLICY, () -> await(stage));
        write(server, "prepared.json", evidence(server, "prepared"));
    }

    private static void paused(ServerCommandSource source) {
        Stage stage = stage(source.getServer());
        require(stage.mode == Mode.PREPARED, "Paused rehearsal is not prepared");
        require(ChronicleRuntime.service(source.getServer()).state().phase() == SessionPhase.PAUSED, "Fixture is not paused");
        stage.mode = Mode.PAUSED_WAITING;
    }

    private static void during(ServerCommandSource source) throws Exception {
        Stage stage = stage(source.getServer());
        require(stage.mode == Mode.PAUSED_HELD, "Maintenance worker is not held");
        var status = QuietBelowEcology.maintenanceForTest(source.getServer());
        require(status.busy() && !status.failed(), "Paused maintenance was not busy and healthy");
        require(stage.entered.getCount() == 0, "Worker did not reach the pure latch");
        require(stage.ticksAtHold + 20 < ticks(source.getServer()), "Server ticks did not continue while worker was held");
        require(engineMutationRefused(source.getServer()), "Live engine remained mutable while maintenance held it");
        boolean changed = source.getWorld().setBlockState(HEART, Blocks.STONE.getDefaultState());
        stage.rootWriteRejected = !changed && source.getWorld().getBlockState(HEART).isOf(QuietBelow.RELIC_HEART);
        require(stage.rootWriteRejected, "Maintenance failed to reject a root-owned terrain write");
        boolean sourceChanged=source.getWorld().setBlockState(SOURCE,Blocks.STONE.getDefaultState());
        require(!sourceChanged&&source.getWorld().getBlockState(SOURCE).isOf(Blocks.SCULK),"Maintenance did not reject an owned sculk write");
        try{io.github.chronicle.core.server.GameSetupTools.execute(source,QuietBelowEcology.TOOL,"disable");throw new IllegalStateException("Operator mutation was accepted during maintenance");}
        catch(IllegalStateException expected){require(expected.getMessage().contains("maintenance"),expected.getMessage());}

        require(stage.terrainAtHold == terrainFingerprint(source.getWorld()), "Maintenance changed fixture terrain while held");
        write(source.getServer(), "paused-held.json", evidence(source.getServer(), "paused-held"));
    }

    private static void queuedUnload(ServerCommandSource source) throws Exception {
        Stage stage=stage(source.getServer());require(stage.mode==Mode.PAUSED_HELD,"Maintenance hold ended before queued callback proof");
        require(QuietBelowEcology.maintenanceForTest(source.getServer()).busy(),"Worker did not stay held");
        // Vanilla freezes ticket expiry too. Inject the same production callback path without
        // pretending that these still-cached chunks really unloaded.
        for(int x=(BASE-5)>>4;x<=(BASE+5)>>4;x++)for(int z=(BASE-5)>>4;z<=(BASE+5)>>4;z++)
            QuietBelowEcology.queueUnloadForTest(source.getServer(),new net.minecraft.util.math.ChunkPos(x,z));
        stage.unloaded=true;
        write(source.getServer(),"queued-unload.json",evidence(source.getServer(),"synthetic-queued-unload"));
    }
    private static void naturalUnload(ServerCommandSource source) throws Exception {
        require(stage(source.getServer()).mode==Mode.ACTIVE_DONE,"Natural unload requires completed active maintenance");
        require(!QuietBelowEcology.maintenanceForTest(source.getServer()).busy(),"Natural unload must run outside the frozen hold");
        require(!sourceLoaded(source.getWorld()),"Source chunks are still naturally loaded");
        require(!QuietBelowEcology.engineForTest(source.getServer()).heartProof(true).complete(),"Naturally unloaded source retained a ready proof");
        write(source.getServer(),"natural-unload.json",evidence(source.getServer(),"natural-unload-active"));
    }
    private static void release(ServerCommandSource source) {
        Stage stage = stage(source.getServer());
        require(stage.mode == Mode.PAUSED_HELD, "No paused worker is held");
        stage.release.countDown();
    }

    private static void active(ServerCommandSource source) throws Exception {
        Stage stage = stage(source.getServer());
        require(stage.mode == Mode.PAUSED_DONE, "Paused compaction did not complete");
        EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
        require(System.identityHashCode(engine) == stage.engineIdentity, "Paused compaction replaced the live engine");
        appendHistory(engine, 120);
        QuietBelowEcology.maintenancePolicyForTest(source.getServer(), EcologyMaintenancePolicy.DEFAULT, () -> { });
        ChronicleRuntime.service(source.getServer()).resume(actor());
        require(ChronicleRuntime.service(source.getServer()).state().phase() == SessionPhase.ACTIVE, "Fixture did not resume active play");
        stage.mode = Mode.ACTIVE_ARMING;
    }

    private static void complete(ServerCommandSource source) throws Exception {
        Stage stage = stage(source.getServer());
        require(stage.mode == Mode.ACTIVE_DONE, "Active scheduler route did not complete");
        var status = QuietBelowEcology.maintenanceForTest(source.getServer());
        require(!status.busy() && !status.failed() && status.completed() >= 2, "Two verified maintenance jobs were not completed");
        require(status.afterBytes() < status.beforeBytes() && status.afterRecords() < status.beforeRecords(),
                "Active compaction did not reduce journal history");
        require(!status.sourceHash().isBlank() && !status.archive().isBlank(), "Compaction did not retain archive identity");
        Path archive = Path.of(status.archive());
        require(Files.isRegularFile(archive) && sha256(archive).equals(status.sourceHash()), "Archive hash differs from sealed source");
        EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
        require(System.identityHashCode(engine) == stage.engineIdentity, "Active compaction replaced the live engine");
        appendHistory(engine, 2); // Demonstrates new appends through the rebound journal handle.
        write(source.getServer(), "active-complete.json", evidence(source.getServer(), "active-complete"));
    }

    private static void shutdown(ServerCommandSource source) throws Exception {
        Stage stage = stage(source.getServer());
        require(stage.mode == Mode.ACTIVE_DONE, "Cannot rehearse shutdown before active completion");
        ChronicleRuntime.service(source.getServer()).pause(actor());
        require(ChronicleRuntime.service(source.getServer()).state().phase() == SessionPhase.PAUSED,
                "Shutdown rehearsal did not pause before recording final history");
        appendHistoryUnchecked(source.getServer(), 120);
        stage.entered = new CountDownLatch(1);
        stage.release = new CountDownLatch(1);
        QuietBelowEcology.maintenancePolicyForTest(source.getServer(), POLICY, () -> await(stage));
        stage.mode = Mode.SHUTDOWN_HELD;
        scheduleRelease(stage);
    }

    private static void restart(ServerCommandSource source) throws Exception {
        EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
        require(engine != null && engine.setup() != null && engine.view().indexed() && engine.ending() == null,
                "Restart did not load an ordinary healthy configured ecology journal");
        var snapshot = QuietBelowEcology.maintenanceForTest(source.getServer());
        require(!snapshot.busy() && !snapshot.failed(), "Restart inherited a pending maintenance worker");
        Stage stage = new Stage();
        stage.mode = Mode.ACTIVE_DONE;
        stage.engineIdentity = System.identityHashCode(engine);
        STAGES.put(source.getServer(), stage);
        write(source.getServer(), "restart.json", evidence(source.getServer(), "restart"));
    }

    private static void tick(MinecraftServer server) {
        Stage stage = STAGES.get(server);
        if (stage == null) return;
        try {
            var status = QuietBelowEcology.maintenanceForTest(server);
            if (stage.mode == Mode.PAUSED_WAITING && status.busy() && stage.entered.getCount() == 0) {
                stage.mode = Mode.PAUSED_HELD;
                stage.ticksAtHold = ticks(server);
                stage.terrainAtHold = terrainFingerprint(server.getOverworld());
            } else if (stage.mode == Mode.PAUSED_HELD && !status.busy() && status.completed() >= 1) {
                require(stage.unloaded, "Paused job completed before the synthetic queued-callback proof");
                require(!QuietBelowEcology.engineForTest(server).heartProof(true).complete(),
                        "Queued source unload did not invalidate the cached Heart proof");
                write(server, "post-queue-complete.json", evidence(server, "paused-done"));
                stage.mode = Mode.PAUSED_DONE;
            } else if (stage.mode == Mode.ACTIVE_ARMING) {
                require(sourceLoaded(server.getOverworld()), "Active Forge warmup attempted before the player reloaded the source");
                if (server.getOverworld().getBlockState(FORGE).get(AncientForgeBlock.HEAT) == 2) {
                    stage.entered = new CountDownLatch(1); stage.release = new CountDownLatch(1);
                    QuietBelowEcology.maintenancePolicyForTest(server, POLICY, () -> await(stage));
                    stage.mode = Mode.ACTIVE_WAITING;
                }
            } else if (stage.mode == Mode.ACTIVE_WAITING && status.busy() && stage.entered.getCount() == 0) {
                require(server.getOverworld().getBlockState(FORGE).get(AncientForgeBlock.HEAT) == 2,
                        "Powered Forge did not reach active maintenance hold");
                stage.activeTicksAtHold = ticks(server); stage.activeTerrainAtHold = terrainFingerprint(server.getOverworld());
                stage.mode = Mode.ACTIVE_HELD;
            } else if (stage.mode == Mode.ACTIVE_HELD && ticks(server) > stage.activeTicksAtHold + 20) {
                require(server.getOverworld().getBlockState(FORGE).get(AncientForgeBlock.HEAT) == 2,
                        "Maintenance changed the powered Forge heat state");
                require(stage.activeTerrainAtHold == terrainFingerprint(server.getOverworld()),
                        "Maintenance changed terrain while the powered Forge was held");
                write(server, "active-held.json", evidence(server, "active-held"));
                stage.release.countDown(); stage.mode = Mode.ACTIVE_RELEASING;
            } else if (stage.mode == Mode.ACTIVE_RELEASING && !status.busy() && status.completed() >= 2) {
                require(ChronicleRuntime.service(server).state().phase() == SessionPhase.ACTIVE,
                        "Active maintenance changed the campaign phase");
                require(System.identityHashCode(QuietBelowEcology.engineForTest(server)) == stage.engineIdentity,
                        "Active maintenance changed live engine identity");
                QuietBelowEcology.maintenancePolicyForTest(server,EcologyMaintenancePolicy.DEFAULT,()->{});
                stage.mode = Mode.ACTIVE_DONE;
            }
        } catch (Exception error) {
            try { write(server, "trial-failure.json", failure(error)); } catch (Exception ignored) { }
            STAGES.remove(server);
        }
    }

    private static void stopping(MinecraftServer server) {
        Stage stage = STAGES.get(server);
        if (stage == null || stage.mode != Mode.SHUTDOWN_HELD) return;
        // This listener is registered after QuietBelowEcology's production stop hook. The pure
        // timer was armed while the server was still alive, so the production join has to wait
        // for a bounded worker completion before this evidence can be written.
        try {
            require(stage.entered.getCount() == 0, "Shutdown worker never reached the latch");
            var snapshot = QuietBelowEcology.maintenanceForTest(server);
            require(!snapshot.busy() && !snapshot.failed(), "Shutdown did not join clean maintenance work");
            require(snapshot.engineIdentity() == stage.engineIdentity, "Shutdown compaction changed live engine identity");
            write(server, "shutdown-complete.json", evidence(server, "shutdown-complete"));
            stage.shutdownProofWritten = true;
        } catch (Exception error) {
            try { write(server, "trial-failure.json", failure(error)); } catch (Exception ignored) { }
        }
    }
    private static void scheduleRelease(Stage stage) {
        Thread releaser = new Thread(() -> {
            try { Thread.sleep(5_000L); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            stage.release.countDown();
        }, "chronicle-auto-maintenance-shutdown-release");
        releaser.setDaemon(true);
        releaser.start();
    }

    private static void appendHistory(EcologyEngine engine, int count) throws IOException {
        for (int n = 0; n < count; n++) engine.speed((n & 1) == 0 ? 50 : 100);
    }
    private static void appendHistoryUnchecked(MinecraftServer server, int count) {
        try { appendHistory(QuietBelowEcology.engineForTest(server), count); }
        catch (IOException error) { throw new IllegalStateException(error); }
    }
    private static boolean engineMutationRefused(MinecraftServer server) {
        try { QuietBelowEcology.engineForTest(server); return false; }
        catch (IllegalStateException expected) { return true; }
    }
    private static void await(Stage stage) {
        stage.entered.countDown();
        try { stage.release.await(60, TimeUnit.SECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
    private static Stage stage(MinecraftServer server) {
        Stage stage = STAGES.get(server); if (stage == null) throw new IllegalStateException("Automatic maintenance fixture is not prepared"); return stage;
    }
    private static Actor actor() { return new Actor("EcologyAutoMaintenanceTrial", null); }
    private static long ticks(MinecraftServer server) { return server.getTicks(); }
    private static long terrainFingerprint(ServerWorld world) {
        long value = 1;
        for (int x = -5; x <= 5; x++) for (int y = 76; y <= 82; y++) for (int z = -5; z <= 5; z++)
            value = 31 * value + QuietBelowEcology.encode(world.getBlockState(new BlockPos(BASE + x, y, BASE + z))).hashCode();
        return value;
    }
    private static boolean sourceLoaded(ServerWorld world) {
        for(int x=(BASE-5)>>4;x<=(BASE+5)>>4;x++)for(int z=(BASE-5)>>4;z<=(BASE+5)>>4;z++)
            if(world.getChunkManager().getWorldChunk(x,z)!=null)return true;
        return false;
    }
    private static Path reports(MinecraftServer server) throws IOException {
        Path run = server.getRunDirectory().toRealPath();
        Path report = run.getParent().getParent().resolve("reports").resolve("automatic-compaction-" + run.getFileName());
        require(Files.isDirectory(report) && report.toRealPath().equals(report), "Automatic maintenance report directory is invalid");
        return report;
    }
    private static void write(MinecraftServer server, String name, JsonObject object) throws IOException {
        Files.writeString(reports(server).resolve(name), object.toString(), StandardCharsets.UTF_8);
    }
    private static JsonObject evidence(MinecraftServer server, String state) {
        var snapshot = QuietBelowEcology.maintenanceForTest(server);
        JsonObject result = new JsonObject();
        result.addProperty("result", "PASS"); result.addProperty("state", state);
        result.addProperty("phase", ChronicleRuntime.service(server).state().phase().name());
        result.addProperty("ticks", ticks(server)); result.addProperty("busy", snapshot.busy());
        result.addProperty("completed", snapshot.completed()); result.addProperty("beforeBytes", snapshot.beforeBytes());
        result.addProperty("afterBytes", snapshot.afterBytes()); result.addProperty("beforeRecords", snapshot.beforeRecords());
        result.addProperty("afterRecords", snapshot.afterRecords()); result.addProperty("sourceHash", snapshot.sourceHash());
        result.addProperty("archive", snapshot.archive()); result.addProperty("engineIdentity", snapshot.engineIdentity());
        result.addProperty("sourceLoaded", sourceLoaded(server.getOverworld()));
        var chunks=new com.google.gson.JsonArray();
        var manager=server.getOverworld().getChunkManager();
        var tickets=manager.getPersistentStateManager().get(net.minecraft.server.world.ChunkTicketManager.STATE_TYPE);
        for(int x=(BASE-5)>>4;x<=(BASE+5)>>4;x++)for(int z=(BASE-5)>>4;z<=(BASE+5)>>4;z++){
            var chunk=new net.minecraft.util.math.ChunkPos(x,z);var info=new JsonObject();
            info.addProperty("position",chunk.toString());info.addProperty("holder",manager.getChunkLoadingDebugInfo(chunk));
            info.addProperty("tickets",tickets==null?"absent":tickets.getTickets(chunk.toLong()).toString());chunks.add(info);
        }
        result.add("chunks",chunks);
        var players=new com.google.gson.JsonArray();
        for(var player:server.getPlayerManager().getPlayerList())players.add(player.getChunkPos().toString());
        result.add("playerChunks",players);
        var forgeChunk=EcologyLoadedBlocks.chunk(server.getOverworld(),FORGE);
        result.addProperty("forgeHeat",forgeChunk!=null&&forgeChunk.getBlockState(FORGE).isOf(QuietBelow.ANCIENT_FORGE_BLOCK)
                ?forgeChunk.getBlockState(FORGE).get(AncientForgeBlock.HEAT):-1);
        Stage stage = STAGES.get(server);
        result.addProperty("activeHeldTicks", stage == null || stage.activeTicksAtHold == 0 ? 0 : ticks(server) - stage.activeTicksAtHold);
        return result;
    }
    private static JsonObject failure(Exception error) { JsonObject result = new JsonObject(); result.addProperty("result", "FAIL"); result.addProperty("error", message(error)); return result; }
    private static String sha256(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static String message(Exception error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
