package io.github.chronicle.core.server.testing;

import io.github.chronicle.core.areas.AreaChange;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.departures.AreaLink;
import io.github.chronicle.core.departures.DepartureDefinition;
import io.github.chronicle.core.departures.DepartureIntent;
import io.github.chronicle.core.departures.DepartureOperation;
import io.github.chronicle.core.departures.DepartureReceipt;
import io.github.chronicle.core.departures.SiteRef;
import io.github.chronicle.core.server.AreaChanges;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.core.server.Departures;
import io.github.chronicle.core.server.PlayRuntime;
import io.github.chronicle.core.server.PlayerRoles;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.literal;

/** Opt-in native CF20 proof. Every entry point refuses anything but its disposable loopback world. */
public final class DeparturesTrial {
    private static final String AREA = "departure_ship";
    private static final String ANCHOR = "departure_helm";
    private static final String BOARDING = "departure_boarding";
    private static final String DEPARTURE = "departure_smoke";
    private static final String RECOVERY = "departure_recovery";
    private static final String RECOVERY_ANCHOR = "recovery_helm";
    private static final String RECOVERY_BOARDING = "recovery_boarding";
    private static final String CONCLUSION = "departure_conclusion";
    private static final String CONCLUSION_ANCHOR = "conclusion_helm";
    private static final String CONCLUSION_BOARDING = "conclusion_boarding";
    private static final String A = "ChronicleDepartA", B = "ChronicleDepartB", C = "ChronicleDepartC";
    private static final BlockPos HELM = new BlockPos(4, 64, 4);
    private static final BlockPos FINAL_HELM = new BlockPos(8, 64, 4);

    private DeparturesTrial() { }

    public static void register() {
        if (!"true".equals(System.getenv("CHRONICLE_DEPARTURES_TRIAL"))) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var root = literal("chronicle_departures_trial").requires(source -> source.hasPermissionLevel(2));
            for (String verb : List.of("prepare", "capture_end", "arm", "verify", "restart",
                    "seed_recovery", "verify_recovery_a", "verify_recovery_b", "prepare_conclusion", "verify_conclusion")) {
                root.then(literal(verb).executes(context -> run(context.getSource(), verb)));
            }
            dispatcher.register(root);
        });
    }

    private static int run(ServerCommandSource source, String verb) {
        try {
            fixture(source);
            switch (verb) {
                case "prepare" -> prepare(source);
                case "capture_end" -> captureEnd(source);
                case "arm" -> arm(source);
                case "verify" -> verify(source);
                case "restart" -> restart(source);
                case "seed_recovery" -> seedRecovery(source);
                case "verify_recovery_a" -> verifyRecoveryA(source);
                case "verify_recovery_b" -> verifyRecoveryB(source);
                case "prepare_conclusion" -> prepareConclusion(source);
                case "verify_conclusion" -> verifyConclusion(source);
                default -> throw new IllegalArgumentException("unknown departure trial action");
            }
            source.sendFeedback(() -> Text.literal("Departures trial PASS " + verb), false);
            return 1;
        } catch (Exception error) {
            source.sendError(Text.literal("Departures trial FAIL " + verb + ": " + error.getMessage()));
            return 0;
        }
    }

    private static void prepare(ServerCommandSource source) throws Exception {
        var server = source.getServer();
        ServerPlayerEntity actor = source.getPlayerOrThrow();
        require(actor.getName().getString().equals(A), "only fixture actor A may prepare departures");
        require(Departures.ready(server), "departure service is unavailable: " + Departures.problem(server));
        require(AreaChanges.ready(server), "Area service is unavailable");
        require(!Departures.state(server).definitions().containsKey(DEPARTURE), "fixture departure already exists");
        require(AreaChanges.catalog(server).areas().isEmpty(), "fixture Area catalog is not empty");
        require(server.getPlayerManager().getPlayer(B) != null, "fixture passenger B is not online");

        ServerWorld world = source.getWorld();
        for (int x = 0; x <= 9; x++) for (int z = 0; z <= 8; z++) {
            world.setBlockState(new BlockPos(x, 63, z), Blocks.STONE.getDefaultState(), 3);
        }
        putHelm(world);
        AuthoringRuntime.get(server).enable(actor.getUuid());
        AreaChanges.begin(source, AREA, "Departure ship", AreaState.START);
        AreaChanges.setDraftCorner(actor, 1, position(HELM));
        AreaChanges.setDraftCorner(actor, 2, position(HELM));
        AreaChanges.saveDraft(source);
    }

    private static void captureEnd(ServerCommandSource source) {
        require(AreaChanges.ready(source.getServer()), "initial Area capture has not completed");
        AreaChange area = area(source);
        require(area.start().isPresent() && area.end().isEmpty(), "unexpected initial Area snapshots");
        put(source.getWorld(), HELM, Blocks.AIR);
        AreaChanges.captureCurrent(source, AREA, AreaState.END, area.revision());
    }

    private static void arm(ServerCommandSource source) throws Exception {
        var server = source.getServer();
        ServerPlayerEntity actor = source.getPlayerOrThrow();
        ServerPlayerEntity passenger = server.getPlayerManager().getPlayer(B);
        require(passenger != null, "fixture passenger B disconnected");
        require(AreaChanges.ready(server), "END Area capture has not completed");
        AreaChange area = area(source);
        require(area.start().isPresent() && area.end().isPresent(), "paired Area snapshots are incomplete");

        putHelm(source.getWorld());
        require(source.getWorld().getEntitiesByClass(ItemEntity.class, new Box(3, 63, 3, 6, 66, 6), entity -> true).isEmpty(),
                "fixture construction created item drops before the departure");
        var authoring = AuthoringRuntime.get(server).service();
        AuthoringSite.Anchor anchor = (AuthoringSite.Anchor) authoring.put(new AuthoringSite.Anchor(
                ANCHOR, "minecraft:overworld", 1, position(HELM), "minecraft:barrel"), false);
        AuthoringSite.Region boarding = (AuthoringSite.Region) authoring.put(new AuthoringSite.Region(
                BOARDING, "minecraft:overworld", 1, new BlockPosition(2, 64, 2), new BlockPosition(7, 66, 6)), false);
        DepartureDefinition definition = new DepartureDefinition(DEPARTURE, "Smoke vessel", 1,
                Optional.of(new SiteRef(anchor.id(), anchor.revision())),
                Optional.of(new SiteRef(boarding.id(), boarding.revision())),
                "Review who is aboard this exact smoke-test vessel.",
                "This final action permanently removes the listed passengers from play.",
                Optional.of(new AreaLink(AREA, AreaState.END)), true);
        Departures.create(source, definition, Departures.state(server).revision());

        PlayRuntime.setParticipant(source, actor.getUuid(), true);
        PlayRuntime.setParticipant(source, passenger.getUuid(), true);
        PlayRuntime.setParticipant(source, offline(C), true);
        AuthoringRuntime.get(server).disable(actor.getUuid());
        ChronicleRuntime.service(server).start(new Actor("Departure fixture", actor.getUuidAsString()), 1);
        position(actor, source.getWorld(), 4.5, 64, 3.5);
        position(passenger, source.getWorld(), 5.5, 64, 3.5);
    }

    private static void verify(ServerCommandSource source) throws Exception {
        var server = source.getServer();
        UUID a = offline(A), b = offline(B), c = offline(C);
        var state = Departures.state(server);
        require(state.isDeparted(a) && state.isDeparted(b), "exact A+B roster was not durably departed");
        require(!state.isDeparted(c), "offline enrolled C incorrectly departed");
        require(state.concluded().isEmpty(), "campaign concluded while enrolled C remained");
        require(ChronicleRuntime.service(server).state().phase() == SessionPhase.ACTIVE, "campaign is no longer active");
        require(state.usedByDefinition().containsKey(DEPARTURE), "departure was not durably consumed");
        DepartureOperation operation = state.operations().get(state.usedByDefinition().get(DEPARTURE));
        require(operation != null && operation.stage() == DepartureOperation.Stage.COMPLETE, "departure operation is incomplete");
        require(new LinkedHashSet<>(operation.onlineParticipants()).equals(Set.of(a, b)), "committed roster is not exactly A+B");
        ServerPlayerEntity onlineA = server.getPlayerManager().getPlayer(A), onlineB = server.getPlayerManager().getPlayer(B);
        require(onlineA != null && onlineB != null && onlineA.isSpectator() && onlineB.isSpectator(), "A+B are not both online Spectators");
        require(source.getWorld().getBlockState(HELM).isAir(), "linked END Area did not remove the vessel helm");
        require(source.getWorld().getEntitiesByClass(ItemEntity.class, new Box(3, 63, 3, 6, 66, 6), entity -> true).isEmpty(), "discarded container contents dropped into the world");
        require(!AuthoringRuntime.get(server).service().state().sites().containsKey(ANCHOR), "used exact helm anchor was not retired");

        expectRefusal(() -> PlayRuntime.setSelfParticipant(onlineA, true), "depart");
        require(server.getCommandManager().getDispatcher().execute("chronicle join", onlineA.getCommandSource()) == 0,
                "departed player bypassed participation through /chronicle join");
        if (!PlayerRoles.definitions(server).isEmpty()) {
            String role = PlayerRoles.definitions(server).keySet().iterator().next();
            expectRefusal(() -> PlayerRoles.assign(source, a, role), "depart");
        }
    }

    private static void restart(ServerCommandSource source) {
        var server = source.getServer();
        ServerPlayerEntity a = server.getPlayerManager().getPlayer(A), b = server.getPlayerManager().getPlayer(B);
        require(a != null && b != null, "both departed passengers must reconnect for restart proof");
        require(a.isSpectator() && b.isSpectator(), "restart did not enforce Spectator for both departed passengers");
        require(Departures.departed(server, a.getUuid()) && Departures.departed(server, b.getUuid()), "departure flags did not survive restart");
        require(!Departures.departed(server, offline(C)), "offline enrolled C changed over restart");
        require(ChronicleRuntime.service(server).state().phase() == SessionPhase.ACTIVE && !Departures.concluded(server), "open campaign state did not survive restart");
        require(source.getWorld().getBlockState(HELM).isAir(), "linked Area result did not survive restart");
        expectRefusal(() -> PlayRuntime.setSelfParticipant(a, true), "depart");
    }

    /** Seeds only pure committed evidence; normal server ticks must perform and receipt every native effect. */
    private static void seedRecovery(ServerCommandSource source) throws Exception {
        var server = source.getServer();
        ServerPlayerEntity actor = source.getPlayerOrThrow();
        require(actor.getName().getString().equals(A), "only fixture actor A may seed recovery");
        require(server.getPlayerManager().getPlayer(B) == null, "recovery passenger B must be offline on the first boot");
        require(!Departures.state(server).definitions().containsKey(RECOVERY), "recovery departure already exists");
        require(ChronicleRuntime.service(server).state().phase() == SessionPhase.INACTIVE, "recovery campaign is not fresh");

        for (int x = 0; x <= 9; x++) for (int z = 0; z <= 8; z++) {
            source.getWorld().setBlockState(new BlockPos(x, 63, z), Blocks.STONE.getDefaultState(), 3);
        }
        putHelm(source.getWorld());
        AuthoringRuntime.get(server).enable(actor.getUuid());
        var authoring = AuthoringRuntime.get(server).service();
        AuthoringSite.Anchor anchor = (AuthoringSite.Anchor) authoring.put(new AuthoringSite.Anchor(
                RECOVERY_ANCHOR, "minecraft:overworld", 1, position(HELM), "minecraft:barrel"), false);
        AuthoringSite.Region boarding = (AuthoringSite.Region) authoring.put(new AuthoringSite.Region(
                RECOVERY_BOARDING, "minecraft:overworld", 1, new BlockPosition(2, 64, 2), new BlockPosition(7, 66, 6)), false);
        DepartureDefinition definition = new DepartureDefinition(RECOVERY, "Recovery vessel", 1,
                Optional.of(new SiteRef(anchor.id(), anchor.revision())),
                Optional.of(new SiteRef(boarding.id(), boarding.revision())),
                "Recovery fixture first review.", "Recovery fixture final review.", Optional.empty(), true);
        definition = Departures.create(source, definition, Departures.state(server).revision());
        UUID a = actor.getUuid(), b = offline(B), c = offline(C);
        PlayRuntime.setParticipant(source, a, true);
        PlayRuntime.setParticipant(source, b, true);
        PlayRuntime.setParticipant(source, c, true);
        AuthoringRuntime.get(server).disable(a);
        ChronicleRuntime.service(server).start(new Actor("Departure recovery fixture", actor.getUuidAsString()), 1);
        position(actor, source.getWorld(), 4.5, 64, 3.5);

        UUID operationId = UUID.nameUUIDFromBytes("chronicle-departure-staggered-recovery".getBytes(StandardCharsets.UTF_8));
        DepartureIntent intent = new DepartureIntent(operationId, definition.id(), definition.revision(), a,
                List.of(a, b), false, Optional.empty(), Instant.now().toString());
        Departures.service(server).begin(intent, Departures.state(server).revision());
    }

    private static void verifyRecoveryA(ServerCommandSource source) throws Exception {
        var server = source.getServer();
        DepartureOperation operation = recoveryOperation(server);
        UUID a = offline(A), b = offline(B), c = offline(C);
        require(operation.stage() == DepartureOperation.Stage.COMMITTED, "operation advanced before offline B was saved");
        require(new LinkedHashSet<>(operation.onlineParticipants()).equals(Set.of(a, b)), "recovery roster is not exact A+B");
        require(operation.preparedAreaEffect().isEmpty() && !operation.concludeCampaign(), "recovery obligations are not no-Area/no-conclusion");
        require(Departures.departed(server, a) && Departures.departed(server, b) && !Departures.departed(server, c), "committed departure flags are incorrect");
        ServerPlayerEntity actor = server.getPlayerManager().getPlayer(A);
        require(actor != null && actor.isSpectator(), "online A was not saved as Spectator");
        require(server.getPlayerManager().getPlayer(B) == null, "B unexpectedly became online on A-only boot");
        require(validReceipt(server, operation, a), "A passenger receipt is missing or does not match its operation/player evidence");
        require(!Files.exists(receipt(server, operation, b)), "offline B already has a passenger receipt");
        require(receiptCount(server) == 1, "A-only boot did not retain exactly one passenger receipt");
        require(ChronicleRuntime.service(server).state().phase() == SessionPhase.ACTIVE && !Departures.concluded(server), "non-concluding recovery changed campaign phase");
    }

    private static void verifyRecoveryB(ServerCommandSource source) throws Exception {
        var server = source.getServer();
        DepartureOperation operation = recoveryOperation(server);
        UUID a = offline(A), b = offline(B), c = offline(C);
        require(server.getPlayerManager().getPlayer(A) == null, "A must remain offline on B-only recovery boot");
        ServerPlayerEntity passenger = server.getPlayerManager().getPlayer(B);
        require(passenger != null && passenger.isSpectator(), "reconnected B was not saved as Spectator");
        require(operation.stage() == DepartureOperation.Stage.COMPLETE, "staggered passenger operation did not complete");
        require(validReceipt(server, operation, a) && validReceipt(server, operation, b), "both durable passenger receipts are not present and exact");
        require(receiptCount(server) == 2, "completed recovery does not have exactly two passenger receipts");
        require(operation.playerEffects().orElseThrow().disposition() == DepartureReceipt.Disposition.SAVED,
                "completed operation lacks saved player evidence");
        require(operation.areaEffects().orElseThrow().disposition() == DepartureReceipt.Disposition.NOT_REQUIRED,
                "no-Area recovery recorded an Area effect");
        require(operation.conclusion().orElseThrow().disposition() == DepartureReceipt.Disposition.NOT_REQUIRED,
                "non-concluding recovery recorded a conclusion");
        require(!Departures.departed(server, c) && ChronicleRuntime.service(server).state().phase() == SessionPhase.ACTIVE
                && !Departures.concluded(server), "offline C/open campaign changed during recovery");
        expectRefusal(() -> PlayRuntime.setSelfParticipant(passenger, true), "depart");
        require(server.getCommandManager().getDispatcher().execute("chronicle join", passenger.getCommandSource()) == 0,
                "recovered passenger bypassed departure through /chronicle join");
    }

    private static void prepareConclusion(ServerCommandSource source) throws Exception {
        var server = source.getServer();
        ServerPlayerEntity actor = source.getPlayerOrThrow();
        require(actor.getName().getString().equals(C), "only remaining fixture passenger C may prepare the conclusion");
        require(server.getPlayerManager().getPlayer(A) == null && server.getPlayerManager().getPlayer(B) == null,
                "A and B must remain offline during the C-only conclusion");
        require(recoveryOperation(server).stage() == DepartureOperation.Stage.COMPLETE, "staggered recovery is not complete");
        require(!Departures.departed(server, actor.getUuid()) && !Departures.concluded(server), "C/open campaign precondition is missing");
        require(ChronicleRuntime.service(server).state().phase() == SessionPhase.ACTIVE, "campaign is not active before conclusion setup");

        ChronicleRuntime.service(server).pause(new Actor("Departure conclusion fixture", actor.getUuidAsString()));
        AuthoringRuntime.get(server).enable(actor.getUuid());
        putConclusionHelm(source.getWorld());
        var authoring = AuthoringRuntime.get(server).service();
        AuthoringSite.Anchor anchor = (AuthoringSite.Anchor) authoring.put(new AuthoringSite.Anchor(
                CONCLUSION_ANCHOR, "minecraft:overworld", 1, position(FINAL_HELM), "minecraft:barrel"), false);
        AuthoringSite.Region boarding = (AuthoringSite.Region) authoring.put(new AuthoringSite.Region(
                CONCLUSION_BOARDING, "minecraft:overworld", 1, new BlockPosition(7, 64, 2), new BlockPosition(10, 66, 6)), false);
        DepartureDefinition definition = new DepartureDefinition(CONCLUSION, "Final vessel", 1,
                Optional.of(new SiteRef(anchor.id(), anchor.revision())),
                Optional.of(new SiteRef(boarding.id(), boarding.revision())),
                "Only the campaign's final remaining participant is aboard.",
                "This departure permanently concludes the campaign for every participant.", Optional.empty(), true);
        Departures.create(source, definition, Departures.state(server).revision());
        AuthoringRuntime.get(server).disable(actor.getUuid());
        ChronicleRuntime.service(server).resume(new Actor("Departure conclusion fixture", actor.getUuidAsString()));
        position(actor, source.getWorld(), 8.5, 64, 3.5);
    }

    private static void verifyConclusion(ServerCommandSource source) throws Exception {
        var server = source.getServer();
        ServerPlayerEntity actor = source.getPlayerOrThrow();
        UUID c = offline(C);
        require(actor.getUuid().equals(c) && actor.isSpectator(), "C did not finish as the departed Spectator");
        require(Departures.departed(server, c) && Departures.concluded(server), "C departure did not durably conclude the campaign");
        UUID operationId = Departures.state(server).usedByDefinition().get(CONCLUSION);
        DepartureOperation operation = operationId == null ? null : Departures.state(server).operations().get(operationId);
        require(operation != null && operation.stage() == DepartureOperation.Stage.COMPLETE && operation.concludeCampaign(),
                "concluding departure operation is incomplete or not pinned to conclusion");
        require(operation.onlineParticipants().equals(List.of(c)), "concluding roster is not exactly C");
        require(operation.areaEffects().orElseThrow().disposition() == DepartureReceipt.Disposition.NOT_REQUIRED,
                "no-Area conclusion recorded an Area effect");
        require(operation.conclusion().orElseThrow().disposition() == DepartureReceipt.Disposition.SAVED,
                "campaign conclusion lacks saved native evidence");
        require(validReceipt(server, operation, c), "C conclusion passenger receipt is missing or not exact");
        require(ChronicleRuntime.service(server).state().phase() == SessionPhase.ENDED, "campaign lifecycle is not durably ENDED");

        require(server.getCommandManager().getDispatcher().execute("chronicle session start", actor.getCommandSource()) == 0,
                "concluded campaign accepted CLI session start");
        require(server.getCommandManager().getDispatcher().execute("chronicle session resume", actor.getCommandSource()) == 0,
                "concluded campaign accepted CLI session resume");
        require(server.getCommandManager().getDispatcher().execute("chronicle join", actor.getCommandSource()) == 0,
                "concluded campaign accepted participant join");
        if (!PlayerRoles.definitions(server).isEmpty()) {
            String role = PlayerRoles.definitions(server).keySet().iterator().next();
            expectRefusal(() -> PlayerRoles.assign(source, c, role), "concluded");
        }

        AuthoringRuntime.get(server).enable(c);
        AuthoringSite saved = AuthoringRuntime.get(server).service().put(new AuthoringSite.Location(
                "post_conclusion_probe", "minecraft:overworld", 1, 1.5, 64, 1.5, 0, 0), false);
        AuthoringRuntime.get(server).beginRegionDraft(actor, "post_conclusion_region");
        require(saved.id().equals("post_conclusion_probe") && AuthoringRuntime.isAuthor(actor),
                "non-active operator authoring was not available after conclusion");
        require(AuthoringRuntime.get(server).regionDraft(c).isPresent(), "post-conclusion authoring draft was not accepted");
        AuthoringRuntime.get(server).disable(c);
    }

    private static DepartureOperation recoveryOperation(net.minecraft.server.MinecraftServer server) {
        UUID operationId = Departures.state(server).usedByDefinition().get(RECOVERY);
        require(operationId != null, "recovery operation is missing");
        DepartureOperation operation = Departures.state(server).operations().get(operationId);
        if (operation == null) throw new IllegalStateException("recovery operation evidence is missing");
        return operation;
    }

    private static Path receipt(net.minecraft.server.MinecraftServer server, DepartureOperation operation, UUID player) {
        return server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("chronicle/departure-player-effects")
                .resolve(operation.operationId() + "." + player + ".receipt");
    }

    private static boolean validReceipt(net.minecraft.server.MinecraftServer server, DepartureOperation operation, UUID player) throws Exception {
        Path path = receipt(server, operation, player);
        String expected = "chronicle-departure-spectator-v1\n" + operation.operationId() + "\n" + player + "\n"
                + operation.definition().id() + "\n";
        return Files.isRegularFile(path) && Files.readString(path, StandardCharsets.UTF_8).equals(expected);
    }

    private static long receiptCount(net.minecraft.server.MinecraftServer server) throws Exception {
        Path directory = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("chronicle/departure-player-effects");
        if (!Files.isDirectory(directory)) return 0;
        try (var files = Files.list(directory)) { return files.filter(Files::isRegularFile).count(); }
    }

    private static void putHelm(ServerWorld world) {
        put(world, HELM, Blocks.BARREL);
        require(world.getBlockEntity(HELM) instanceof BarrelBlockEntity, "fixture barrel block entity is missing");
        BarrelBlockEntity barrel = (BarrelBlockEntity) world.getBlockEntity(HELM);
        barrel.clear();
        barrel.setStack(0, new ItemStack(Items.DIAMOND, 7));
        barrel.markDirty();
    }

    private static void putConclusionHelm(ServerWorld world) {
        put(world, FINAL_HELM, Blocks.BARREL);
        require(world.getBlockEntity(FINAL_HELM) instanceof BarrelBlockEntity, "conclusion barrel block entity is missing");
        BarrelBlockEntity barrel = (BarrelBlockEntity) world.getBlockEntity(FINAL_HELM);
        barrel.clear();
        barrel.setStack(0, new ItemStack(Items.EMERALD, 3));
        barrel.markDirty();
    }

    private static void put(ServerWorld world, BlockPos pos, net.minecraft.block.Block block) {
        world.setBlockState(pos, block.getDefaultState(), net.minecraft.block.Block.FORCE_STATE_AND_SKIP_CALLBACKS_AND_DROPS
                | net.minecraft.block.Block.NOTIFY_LISTENERS);
    }

    private static void position(ServerPlayerEntity player, ServerWorld world, double x, double y, double z) {
        player.changeGameMode(GameMode.SURVIVAL);
        player.teleport(world, x, y, z, Set.of(), 0, 0, false);
        player.setVelocity(0, 0, 0);
        player.onLanding();
    }

    private static AreaChange area(ServerCommandSource source) {
        AreaChange area = AreaChanges.catalog(source.getServer()).areas().get(AREA);
        if (area == null) throw new IllegalStateException("fixture Area is missing");
        return area;
    }

    private static BlockPosition position(BlockPos pos) { return new BlockPosition(pos.getX(), pos.getY(), pos.getZ()); }
    private static UUID offline(String name) { return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)); }

    private static void expectRefusal(Checked action, String text) {
        Exception refusal = null;
        try {
            action.run();
        } catch (Exception expected) {
            refusal = expected;
        }
        require(refusal != null, "departed-player gate accepted a forbidden mutation");
        require(refusal.getMessage() != null && refusal.getMessage().toLowerCase().contains(text), "unexpected gate refusal: " + refusal.getMessage());
    }

    private static void fixture(ServerCommandSource source) {
        var server = source.getServer();
        var root = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
        require(root != null && root.getParent() != null && root.getParent().getFileName().toString().equals("test-runs")
                && root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")
                && java.nio.file.Files.isRegularFile(root.resolve(".departures-fixture"))
                && server.isDedicated() && !server.isOnlineMode() && server.getServerPort() == 25575
                && "127.0.0.1".equals(server.getServerIp()) && source.getWorld().getRegistryKey() == net.minecraft.world.World.OVERWORLD,
                "unsafe departures fixture");
    }

    private static void require(boolean condition, String text) { if (!condition) throw new IllegalStateException(text); }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
}
