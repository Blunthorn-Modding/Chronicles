package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.areas.AreaChange;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.campaign.AuditEntry;
import io.github.chronicle.core.campaign.CampaignState;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.departures.AreaLink;
import io.github.chronicle.core.departures.DepartureDefinition;
import io.github.chronicle.core.departures.DepartureIntent;
import io.github.chronicle.core.departures.DepartureOperation;
import io.github.chronicle.core.departures.DepartureService;
import io.github.chronicle.core.departures.DepartureState;
import io.github.chronicle.core.departures.DepartureStore;
import io.github.chronicle.core.departures.PreparedAreaEffect;
import io.github.chronicle.core.network.DeparturePayloads;
import io.github.chronicle.core.play.PlayState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Native CF20 coordinator: exact helm interaction, two reviews, durable effects and recovery. */
public final class Departures {
    public static final String HOLD = "chronicle_core:departure";
    private static final long MAX_AGE_NANOS = 30_000_000_000L;
    private static final double MAX_ANCHOR_DISTANCE_SQUARED = 36.0D;
    private static final int MAX_OFFERS = 256;
    private static final Map<MinecraftServer, Runtime> WORLDS = new IdentityHashMap<>();
    private static final Map<String, DepartureDefinition> DEFAULTS = new LinkedHashMap<>();
    private static boolean defaultsFrozen;

    private Departures() { }

    /** Campaign definitions must be registered during mod initialization, before any server starts. */
    public static synchronized void registerDefaults(Collection<DepartureDefinition> definitions) {
        if (defaultsFrozen) throw new IllegalStateException("Departure defaults are frozen");
        if (definitions == null) throw new IllegalArgumentException("Departure defaults are required");
        for (DepartureDefinition definition : definitions) {
            if (definition == null || definition.revision() != 1)
                throw new IllegalArgumentException("Departure defaults must be revision-1 definitions");
            if (DEFAULTS.putIfAbsent(definition.id(), definition) != null)
                throw new IllegalArgumentException("Duplicate departure default: " + definition.id());
        }
        if (DEFAULTS.size() > DepartureState.MAX_DEFINITIONS)
            throw new IllegalArgumentException("Too many departure defaults");
    }

    public static void register() {
        DeparturePayloads.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> defaultsFrozen = true);
        ServerLifecycleEvents.SERVER_STARTED.register(Departures::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(Departures::stop);
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
        ServerTickEvents.END_SERVER_TICK.register(Departures::tick);
        ServerPlayNetworking.registerGlobalReceiver(DeparturePayloads.Action.ID,
                (payload, context) -> context.server().execute(() -> act(context.player(), payload)));
        ServerDisconnects.register((handler, server) -> disconnected(server, handler.player));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> joined(server, handler.player));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> use(player, world, hand, hit.getBlockPos()));
    }

    public static boolean ready(MinecraftServer server) {
        requireThread(server);
        Runtime runtime = WORLDS.get(server);
        return runtime != null && runtime.failure == null;
    }

    public static String problem(MinecraftServer server) {
        requireThread(server);
        Runtime runtime = WORLDS.get(server);
        return runtime == null ? "Departures are unavailable; inspect the server log and departures.json."
                : Objects.requireNonNullElse(runtime.failure, "");
    }

    public static DepartureService service(MinecraftServer server) { return runtime(server).service; }
    public static DepartureState state(MinecraftServer server) { return service(server).state(); }
    public static boolean departed(MinecraftServer server, UUID player) { return state(server).isDeparted(player); }
    public static boolean concluded(MinecraftServer server) { return state(server).concluded().isPresent(); }
    public static List<String> references(MinecraftServer server, String areaId) {
        return state(server).definitions().values().stream()
                .filter(definition -> definition.areaLink().map(link -> link.areaId().equals(areaId)).orElse(false))
                .map(definition -> "Departure: " + definition.name() + " [" + definition.id() + "]")
                .sorted().toList();
    }

    public static void requireParticipation(MinecraftServer server, UUID player) {
        Objects.requireNonNull(player, "player is required");
        requireCampaignOpen(server);
        if (departed(server, player)) throw new IllegalStateException("This character has permanently departed the campaign.");
    }

    public static boolean pending(MinecraftServer server){return state(server).operations().values().stream().anyMatch(o->o.stage()!=DepartureOperation.Stage.COMPLETE);}
    public static void requireNoPending(MinecraftServer server){if(pending(server))throw new IllegalStateException("A committed departure must finish before changing play or membership.");}
    public static void requireCampaignOpen(MinecraftServer server) {
        requireNoPending(server);
        if (concluded(server)) throw new IllegalStateException("This campaign concluded when its remaining participants departed.");
    }

    public static DepartureDefinition create(ServerCommandSource source, DepartureDefinition definition,
                                              long expectedStateRevision) throws IOException {
        requireEditor(source);
        validateDefinition(source.getServer(), definition, null);
        return service(source.getServer()).create(definition, expectedStateRevision);
    }

    public static DepartureDefinition save(ServerCommandSource source, DepartureDefinition definition,
                                            long expectedDefinitionRevision, long expectedStateRevision) throws IOException {
        requireEditor(source);
        validateDefinition(source.getServer(), definition, definition.id());
        return service(source.getServer()).replace(definition, expectedDefinitionRevision, expectedStateRevision);
    }

    public static void delete(ServerCommandSource source, String id, long expectedDefinitionRevision,
                              long expectedStateRevision) throws IOException {
        requireEditor(source);
        service(source.getServer()).retire(id, expectedDefinitionRevision, expectedStateRevision);
    }

    private static void start(MinecraftServer server) {
        DepartureStore store = null;
        try {
            store = new DepartureStore(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/departures.json"),
                    List.copyOf(DEFAULTS.values()));
            Runtime runtime = new Runtime(store, new DepartureService(store));
            WORLDS.put(server, runtime);
            AuthoringRuntime.get(server).service().addMutationGuard((old, next, operation) -> guardSite(runtime, old));
            boolean unresolved = runtime.service.state().operations().values().stream()
                    .anyMatch(operation -> operation.stage() != DepartureOperation.Stage.COMPLETE);
            if (unresolved) RuntimeHolds.hold(server, HOLD, "A committed departure is completing durable effects.");
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) enforce(runtime, player);
            ChronicleCore.LOGGER.info("Chronicle departures ready: {}", server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/departures.json"));
        } catch (IOException | RuntimeException failure) {
            if (store != null) try { store.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            WORLDS.remove(server);
            RuntimeHolds.hold(server, HOLD, "Departure authority is unavailable; inspect departures.json and the server log.");
            ChronicleCore.LOGGER.error("Chronicle departures unavailable; existing evidence was preserved", failure);
        }
    }

    private static void stop(MinecraftServer server) {
        Runtime runtime = WORLDS.get(server);
        if (runtime == null) return;
        try { runtime.store.close(); }
        catch (IOException failure) { ChronicleCore.LOGGER.error("Unable to close the departure authority lock cleanly", failure); }
    }

    private static void tick(MinecraftServer server) {
        Runtime runtime = WORLDS.get(server);
        if (runtime == null) return;
        expire(server, runtime);
        if (runtime.failure != null) return;
        DepartureOperation operation = runtime.service.state().operations().values().stream()
                .filter(candidate -> candidate.stage() != DepartureOperation.Stage.COMPLETE)
                .findFirst().orElse(null);
        if (operation == null) return;
        RuntimeHolds.hold(server, HOLD, "A committed departure is completing durable effects.");
        try {
            drive(server, runtime, operation);
            runtime.lastRecoveryDiagnostic = "";
        } catch (IOException | RuntimeException failure) {
            String diagnostic = failure.getClass().getSimpleName() + ":" + safe(failure.getMessage());
            if (!diagnostic.equals(runtime.lastRecoveryDiagnostic)) {
                runtime.lastRecoveryDiagnostic = diagnostic;
                ChronicleCore.LOGGER.error("Committed departure {} remains held for idempotent recovery", operation.operationId(), failure);
            }
        }
    }

    private static ActionResult use(net.minecraft.entity.player.PlayerEntity player, net.minecraft.world.World world,
                                    Hand hand, BlockPos position) {
        if (hand != Hand.MAIN_HAND || !(world instanceof ServerWorld serverWorld)
                || !(player instanceof ServerPlayerEntity actor)) return ActionResult.PASS;
        Runtime runtime = WORLDS.get(serverWorld.getServer());
        if (runtime == null) return ActionResult.PASS;
        List<DepartureDefinition> matches = matching(runtime, serverWorld, position);
        if (matches.isEmpty()) return ActionResult.PASS;
        if (matches.size() != 1) actor.sendMessage(Text.literal("This block has conflicting departure definitions; ask an operator to repair them."), false);
        else interact(actor, matches.getFirst());
        return ActionResult.SUCCESS;
    }

    private static void interact(ServerPlayerEntity actor, DepartureDefinition definition) {
        MinecraftServer server = actor.getEntityWorld().getServer();
        Runtime runtime = runtime(server);
        if (!ServerPlayNetworking.canSend(actor, DeparturePayloads.View.ID)) {
            actor.sendMessage(Text.literal("Install the matching Chronicle client to review this departure."), false);
            return;
        }
        try {
            if (runtime.pending != null || !runtime.offers.isEmpty()) throw new IllegalStateException("Another departure review or preflight is already active.");
            UUID operationId = UUID.randomUUID();
            Review review = captureReview(server, actor, definition, operationId, Optional.empty(), false);
            if (definition.areaLink().isEmpty()) {
                openFirst(server, runtime, review);
                return;
            }
            Pending pending = new Pending(operationId, actor.getUuid(), actor.networkHandler, review, System.nanoTime());
            runtime.pending = pending;
            actor.sendMessage(Text.literal("Preparing an exact departure review. The world remains unchanged."), false);
            AreaLink link = definition.areaLink().orElseThrow();
            WorldAreaOperations.prepareDeparture(server, operationId, actor.getUuid(), link.areaId(), link.requestedState(),
                    new LinkedHashSet<>(review.roster()), Set.of(definition.exactBlockAnchor().orElseThrow().id()),
                    prepared -> prepared(server, runtime, pending, prepared),
                    refused -> refused(server, runtime, pending, refused));
        } catch (IOException | RuntimeException failure) {
            Pending pending = runtime.pending;
            if (pending != null && pending.actor().equals(actor.getUuid()) && pending.connection() == actor.networkHandler) {
                runtime.pending = null;
                releasePrepared(server, pending.review().expectedAreaOperationId());
            }
            actor.sendMessage(Text.literal(safe(failure.getMessage())), false);
        }
    }

    private static void prepared(MinecraftServer server, Runtime runtime, Pending pending, WorldAreaOperations.Prepared prepared) {
        if (runtime.pending != pending) {
            WorldAreaOperations.releasePreparation(server, prepared.operation().operationId());
            return;
        }
        runtime.pending = null;
        try {
            ServerPlayerEntity actor = server.getPlayerManager().getPlayer(pending.actor());
            if (actor == null || pending.connection() != actor.networkHandler)
                throw new IllegalStateException("The departure actor reconnected; review the departure again.");
            PreparedAreaEffect effect = new PreparedAreaEffect(prepared.operation().operationId(), prepared.operation().areaId(),
                    prepared.operation().requestedState().orElseThrow(), prepared.operation().after().orElseThrow().sha256());
            Review withEffect = pending.review().withEffect(effect);
            validateReview(server, withEffect, true);
            openFirst(server, runtime, withEffect);
        } catch (IOException | RuntimeException failure) {
            releasePrepared(server, pending.review().expectedAreaOperationId());
            notify(server, pending.actor(), safe(failure.getMessage()));
        }
    }

    private static void refused(MinecraftServer server, Runtime runtime, Pending pending, String reason) {
        if (runtime.pending == pending) runtime.pending = null;
        notify(server, pending.actor(), safe(reason));
    }

    private static void openFirst(MinecraftServer server, Runtime runtime, Review review) throws IOException {
        validateReview(server, review, review.prepared().isPresent());
        ServerPlayerEntity actor = server.getPlayerManager().getPlayer(review.actor());
        if (actor == null || actor.networkHandler != review.connection()) throw new IllegalStateException("The departure actor disconnected.");
        if (runtime.offers.size() >= MAX_OFFERS && !runtime.offers.containsKey(review.actor()))
            throw new IllegalStateException("Too many pending departure reviews; try again shortly.");
        Offer offer = new Offer(UUID.randomUUID(), DeparturePayloads.NO_PARENT, 1, System.nanoTime(), review);
        runtime.offers.put(review.actor(), offer);
        send(actor, offer, "Review the exact passengers before continuing.", true, false, true);
    }

    private static void act(ServerPlayerEntity actor, DeparturePayloads.Action request) {
        MinecraftServer server = actor.getEntityWorld().getServer();
        Runtime runtime = WORLDS.get(server);
        if (runtime == null) return;
        Offer offer = runtime.offers.get(actor.getUuid());
        if (offer == null || offer.connection() != actor.networkHandler || !offer.token().equals(request.token())) return;
        runtime.offers.remove(actor.getUuid()); // Every token is consumed before any recheck or callback.
        if (expired(offer)) {
            releaseReview(server, offer.review());
            send(actor, offer, "Departure review expired. Use the exact block again.", false, true, false);
            return;
        }
        if (request.action() == DeparturePayloads.CANCEL) {
            releaseReview(server, offer.review());
            return;
        }
        if (request.action() == DeparturePayloads.BACK) {
            if (offer.stage() != 2) { releaseReview(server, offer.review()); return; }
            try {
                validateReview(server, offer.review(), offer.review().prepared().isPresent());
                Offer first = new Offer(UUID.randomUUID(), DeparturePayloads.NO_PARENT, 1, System.nanoTime(), offer.review());
                runtime.offers.put(actor.getUuid(), first);
                send(actor, first, "Review the exact passengers before continuing.", true, false, true);
            } catch (IOException | RuntimeException failure) {
                releaseReview(server, offer.review());
                send(actor, offer, safe(failure.getMessage()), false, true, false);
            }
            return;
        }
        if (request.action() != DeparturePayloads.CONTINUE_OR_CONFIRM) {
            releaseReview(server, offer.review());
            return;
        }
        try {
            validateReview(server, offer.review(), offer.review().prepared().isPresent());
            if (offer.stage() == 1) {
                Offer finalOffer = new Offer(UUID.randomUUID(), offer.token(), 2, System.nanoTime(), offer.review());
                runtime.offers.put(actor.getUuid(), finalOffer);
                send(actor, finalOffer, "Leaving is permanent. Continue only when you accept these consequences.", true, false, true);
            } else commit(server, runtime, actor, offer);
        } catch (IOException | RuntimeException failure) {
            releaseReview(server, offer.review());
            send(actor, offer, safe(failure.getMessage()), false, true, false);
        }
    }

    private static void commit(MinecraftServer server, Runtime runtime, ServerPlayerEntity actor, Offer offer) throws IOException {
        Review review = offer.review();
        DepartureIntent intent = new DepartureIntent(review.operationId(), review.definition().id(), review.definition().revision(),
                review.actor(), review.roster(), review.concludeCampaign(), review.prepared(), review.createdAt());
        boolean committed = false;
        try {
            DepartureOperation operation = runtime.service.begin(intent, review.departureStateRevision());
            committed = true;
            RuntimeHolds.hold(server, HOLD, "A committed departure is completing durable effects.");
            send(actor, offer, "Departure committed for the reviewed passengers. Durable effects are completing.", false, true, false);
            drive(server, runtime, operation);
        } catch (IOException | RuntimeException failure) {
            if (!committed) releaseReview(server, review);
            else ChronicleCore.LOGGER.error("Departure {} committed and remains held for recovery", review.operationId(), failure);
            if (!committed) throw failure;
        }
    }

    private static void drive(MinecraftServer server, Runtime runtime, DepartureOperation initial) throws IOException {
        DepartureOperation operation = runtime.service.state().operations().get(initial.operationId());
        if (operation == null) throw new IllegalStateException("Committed departure evidence is missing.");
        if (operation.stage() == DepartureOperation.Stage.COMMITTED) {
            for (UUID id : operation.onlineParticipants()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player == null) continue; // Each committed passenger may reconnect and save separately.
                player.changeGameMode(GameMode.SPECTATOR);
                if (!player.isSpectator()) throw new IllegalStateException("Could not enforce Spectator for committed passenger " + id);
            }
            server.getPlayerManager().saveAllPlayerData();
            for(UUID id:operation.onlineParticipants())if(server.getPlayerManager().getPlayer(id)!=null)DeparturePlayerEffects.acknowledge(server,operation,id);
            for(UUID id:operation.onlineParticipants())if(!DeparturePlayerEffects.acknowledged(server,operation,id))return;
            operation = runtime.service.recordPlayerEffects(operation.operationId(), "player-data:spectator:" + operation.operationId());
        }
        if (operation.stage() == DepartureOperation.Stage.PLAYER_EFFECTS_SAVED) {
            if (operation.preparedAreaEffect().isEmpty()) operation = runtime.service.recordNoAreaEffect(operation.operationId());
            else {
                UUID areaOperation = operation.preparedAreaEffect().orElseThrow().operationId();
                WorldAreaOperations.commitPrepared(server, areaOperation);
                if (!WorldAreaOperations.complete(server, areaOperation)) return;
                operation = runtime.service.recordAreaEffects(operation.operationId(), "area-operation:" + areaOperation
                        + ":" + operation.preparedAreaEffect().orElseThrow().snapshotSha256());
            }
        }
        if (operation.stage() == DepartureOperation.Stage.AREA_EFFECTS_SAVED) {
            if (!operation.concludeCampaign()) operation = runtime.service.recordNoConclusion(operation.operationId());
            else {
                String evidence = ensureCampaignEnded(server, operation);
                operation = runtime.service.recordConclusion(operation.operationId(), evidence, Instant.now().toString());
            }
        }
        if (operation.stage() == DepartureOperation.Stage.CONCLUSION_SAVED) operation = runtime.service.complete(operation.operationId());
        if (operation.stage() == DepartureOperation.Stage.COMPLETE) {
            RuntimeHolds.release(server, HOLD);
            notify(server, operation.actor(), operation.concludeCampaign()
                    ? "Departure complete. The campaign has concluded." : "Departure complete.");
        }
    }

    private static String ensureCampaignEnded(MinecraftServer server, DepartureOperation operation) throws IOException {
        var campaign = ChronicleRuntime.service(server);
        String actorName = conclusionActor(operation.operationId());
        CampaignState state = campaign.state();
        Optional<AuditEntry> receipt = state.history().stream()
                .filter(entry -> entry.action().equals("session.end") && entry.actor().name().equals(actorName)
                        && Objects.equals(entry.actor().uuid(), operation.actor().toString()))
                .reduce((left, right) -> right);
        if (receipt.isEmpty()) {
            if (state.phase() != SessionPhase.ACTIVE && state.phase() != SessionPhase.PAUSED)
                throw new IllegalStateException("Campaign is not active/paused and has no matching departure conclusion receipt.");
            campaign.end(new Actor(actorName, operation.actor().toString()));
            state = campaign.state();
            receipt = state.history().stream().filter(entry -> entry.action().equals("session.end")
                    && entry.actor().name().equals(actorName)).reduce((left, right) -> right);
        }
        AuditEntry end = receipt.orElseThrow(() -> new IllegalStateException("Campaign end did not produce durable audit evidence."));
        if (campaign.state().phase() != SessionPhase.ENDED) throw new IllegalStateException("Campaign end did not become durable.");
        return "campaign-state:session=" + end.sessionNumber() + ":audit=" + end.sequence();
    }

    private static Review captureReview(MinecraftServer server, ServerPlayerEntity actor, DepartureDefinition definition,
                                        UUID operationId, Optional<PreparedAreaEffect> prepared, boolean allowPreparedHold) throws IOException {
        Runtime runtime = runtime(server);
        DepartureState departures = runtime.service.state();
        requireCampaignOpen(server);
        PlayState play = PlayRuntime.service(server).state();
        CampaignState campaign = ChronicleRuntime.service(server).state();
        AuthoringSite.Anchor anchor = anchor(server, definition);
        AuthoringSite.Region region = region(server, definition);
        List<UUID> roster = aboard(server, play, departures, region);
        if (!roster.contains(actor.getUuid())) throw new IllegalStateException("Stand inside the boarding Region to depart.");
        Optional<AreaPin> area = areaPin(server, definition);
        boolean conclude = conclusionRequired(play.participants(), departures.departedByPlayer().keySet(), new LinkedHashSet<>(roster));
        Review review = new Review(operationId, actor.getUuid(), actor.networkHandler, definition, departures.revision(),
                PlayRuntime.membershipRevision(server), play.participants(), campaign.sessionNumber(), roster, conclude, anchor, region, area,
                prepared, Instant.now().toString());
        validateReview(server, review, allowPreparedHold);
        return review;
    }

    private static void validateReview(MinecraftServer server, Review review, boolean allowPreparedHold) throws IOException {
        requireCampaignOpen(server);
        Runtime runtime = runtime(server);
        if (runtime.service.state().revision() != review.departureStateRevision()
                || !review.definition().equals(runtime.service.state().definitions().get(review.definition().id())))
            throw new IllegalStateException("Departure configuration changed; review it again.");
        CampaignState campaign = ChronicleRuntime.service(server).state();
        if (campaign.phase() != SessionPhase.ACTIVE || campaign.sessionNumber() != review.sessionNumber())
            throw new IllegalStateException("Departure requires the same occupied ACTIVE session.");
        if (allowPreparedHold) {
            if (RuntimeHolds.reason(server) == null || RuntimeHolds.heldByOther(server, WorldAreaOperations.HOLD_OWNER))
                throw new IllegalStateException("The prepared Area reservation changed; review the departure again.");
        } else if (RuntimeHolds.reason(server) != null) throw new IllegalStateException("Resolve the active Chronicle operation first.");
        PlayState play = PlayRuntime.service(server).state();
        if (PlayRuntime.membershipRevision(server) != review.membershipRevision() || !play.participants().equals(review.enrolled()))
            throw new IllegalStateException("Campaign membership changed; review the departure again.");
        if (!review.anchor().equals(anchor(server, review.definition())) || !review.region().equals(region(server, review.definition())))
            throw new IllegalStateException("Departure block or boarding Region changed; review again.");
        validateAreaPin(server, review, allowPreparedHold);
        ServerPlayerEntity actor = server.getPlayerManager().getPlayer(review.actor());
        if (actor == null || actor.networkHandler != review.connection()) throw new IllegalStateException("The departure actor disconnected.");
        validateAnchor(actor, review.anchor());
        if (!eligible(actor, play, runtime.service.state())) throw new IllegalStateException("You are no longer eligible to depart.");
        List<UUID> roster = aboard(server, play, runtime.service.state(), review.region());
        if (!roster.equals(review.roster())) throw new IllegalStateException("The passengers aboard changed; review the departure again.");
        boolean conclusion = conclusionRequired(play.participants(), runtime.service.state().departedByPlayer().keySet(), new LinkedHashSet<>(roster));
        if (conclusion != review.concludeCampaign()) throw new IllegalStateException("The campaign-ending consequence changed; review again.");
    }

    private static void validateAreaPin(MinecraftServer server, Review review, boolean requirePrepared) throws IOException {
        Optional<AreaPin> current = areaPin(server, review.definition());
        if (!Objects.equals(current, review.area())) throw new IllegalStateException("Linked Area state changed; review the departure again.");
        if (review.area().isEmpty() && review.prepared().isPresent() || requirePrepared && review.area().isPresent() && review.prepared().isEmpty())
            throw new IllegalStateException("Linked Area preparation is missing or belongs to another departure.");
        if (review.prepared().isPresent()) {
            PreparedAreaEffect effect = review.prepared().orElseThrow();
            AreaPin pin = review.area().orElseThrow();
            if (!effect.areaId().equals(pin.areaId()) || effect.requestedState() != pin.state()
                    || !effect.snapshotSha256().equals(pin.snapshotSha256())
                    || !effect.operationId().equals(review.expectedAreaOperationId()))
                throw new IllegalStateException("Prepared Area evidence changed; review the departure again.");
        }
    }

    private static List<UUID> aboard(MinecraftServer server, PlayState play, DepartureState departures,
                                    AuthoringSite.Region region) {
        return server.getPlayerManager().getPlayerList().stream()
                .filter(player -> eligible(player, play, departures))
                .filter(player -> inside(player, region))
                .map(ServerPlayerEntity::getUuid)
                .sorted(Comparator.comparing(UUID::toString)).toList();
    }

    private static boolean eligible(ServerPlayerEntity player, PlayState play, DepartureState departures) {
        return play.participants().contains(player.getUuid()) && player.isAlive() && !player.isSpectator()
                && !AuthoringRuntime.isAuthor(player) && !departures.isDeparted(player.getUuid());
    }

    static boolean conclusionRequired(Set<UUID> enrolled, Set<UUID> alreadyDeparted, Set<UUID> leaving) {
        Objects.requireNonNull(enrolled); Objects.requireNonNull(alreadyDeparted); Objects.requireNonNull(leaving);
        return enrolled.stream().allMatch(player -> alreadyDeparted.contains(player) || leaving.contains(player));
    }

    static UUID areaOperationId(UUID departureOperation) {
        return UUID.nameUUIDFromBytes((departureOperation + ":area").getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<AreaPin> areaPin(MinecraftServer server, DepartureDefinition definition) throws IOException {
        if (definition.areaLink().isEmpty()) return Optional.empty();
        AreaLink link = definition.areaLink().orElseThrow();
        AreaChange area = AreaChanges.catalog(server).areas().get(link.areaId());
        if (area == null) throw new IllegalStateException("The linked Area change is missing.");
        AreaChanges.currentRegion(server, area);
        var snapshot = area.requireSnapshot(link.requestedState());
        AreaChanges.snapshots(server).load(snapshot); // Fail closed on a missing/corrupt snapshot before showing terms.
        return Optional.of(new AreaPin(area.id(), area.revision(), link.requestedState(), snapshot.sha256()));
    }

    private static AuthoringSite.Anchor anchor(MinecraftServer server, DepartureDefinition definition) {
        definition.requireExecutable();
        var reference = definition.exactBlockAnchor().orElseThrow();
        AuthoringSite site = AuthoringRuntime.get(server).service().state().sites().get(reference.id());
        if (!(site instanceof AuthoringSite.Anchor anchor) || anchor.revision() != reference.revision())
            throw new IllegalStateException("Departure exact block is missing or changed: " + reference.id());
        return anchor;
    }

    private static AuthoringSite.Region region(MinecraftServer server, DepartureDefinition definition) {
        definition.requireExecutable();
        var reference = definition.boardingRegion().orElseThrow();
        AuthoringSite site = AuthoringRuntime.get(server).service().state().sites().get(reference.id());
        if (!(site instanceof AuthoringSite.Region region) || region.revision() != reference.revision())
            throw new IllegalStateException("Departure boarding Region is missing or changed: " + reference.id());
        return region;
    }

    private static void validateAnchor(ServerPlayerEntity actor, AuthoringSite.Anchor anchor) {
        String dimension = actor.getEntityWorld().getRegistryKey().getValue().toString();
        if (!dimension.equals(anchor.dimension())) throw new IllegalStateException("Stand at the exact departure block.");
        BlockPos position = block(anchor.position());
        ServerWorld world = (ServerWorld) actor.getEntityWorld();
        if (!world.getChunkManager().isChunkLoaded(position.getX() >> 4, position.getZ() >> 4))
            throw new IllegalStateException("Departure block chunk is unloaded; no chunk was forced.");
        String actual = Registries.BLOCK.getId(world.getBlockState(position).getBlock()).toString();
        if (!actual.equals(anchor.expectedBlock())) throw new IllegalStateException("The exact departure block no longer matches its binding.");
        if (actor.squaredDistanceTo(Vec3d.ofCenter(position)) > MAX_ANCHOR_DISTANCE_SQUARED)
            throw new IllegalStateException("Stay near the exact departure block while reviewing.");
    }

    private static boolean inside(ServerPlayerEntity player, AuthoringSite.Region region) {
        if (!player.getEntityWorld().getRegistryKey().getValue().toString().equals(region.dimension())) return false;
        BlockPos position = player.getBlockPos();
        return region.contains(new BlockPosition(position.getX(), position.getY(), position.getZ()));
    }

    private static void validateDefinition(MinecraftServer server, DepartureDefinition definition, String replacing) {
        if (definition == null) throw new IllegalArgumentException("Departure definition is required.");
        AuthoringSite.Anchor anchor = anchor(server, definition);
        region(server, definition);
        areaPinUnchecked(server, definition);
        for (DepartureDefinition candidate : service(server).state().definitions().values()) {
            if (candidate.id().equals(replacing) || candidate.exactBlockAnchor().isEmpty()) continue;
            var candidateRef=candidate.exactBlockAnchor().orElseThrow();
            var requestedRef=definition.exactBlockAnchor().orElseThrow();
            AuthoringSite candidateSite=AuthoringRuntime.get(server).service().state().sites().get(candidateRef.id());
            boolean sameLiveBlock=candidateSite instanceof AuthoringSite.Anchor candidateAnchor&&candidateAnchor.revision()==candidateRef.revision()&&candidateAnchor.dimension().equals(anchor.dimension())&&candidateAnchor.position().equals(anchor.position());
            if(candidateRef.id().equals(requestedRef.id())||sameLiveBlock)
                throw new IllegalStateException("Block site is already used by departure: " + candidate.name());
        }
        if (!anchor.expectedBlock().contains(":")) throw new IllegalStateException("Departure block binding is invalid.");
    }

    private static void areaPinUnchecked(MinecraftServer server, DepartureDefinition definition) {
        try { areaPin(server, definition); }
        catch (IOException failure) { throw new IllegalStateException("Linked Area snapshot could not be read.", failure); }
    }

    private static void requireEditor(ServerCommandSource source) {
        if (source == null || !source.hasPermissionLevel(2) || source.getPlayer() == null
                || !AuthoringRuntime.isAuthor(source.getPlayer()) || !AuthoringRuntime.editable(source.getServer()))
            throw new IllegalStateException("Departure definitions require non-active operator Editing.");
        if (RuntimeHolds.reason(source.getServer()) != null)
            throw new IllegalStateException("Resolve the active Chronicle operation before editing departures.");
    }

    private static void guardSite(Runtime runtime, AuthoringSite old) {
        if (old == null) return;
        for (DepartureDefinition definition : runtime.service.state().definitions().values()) {
            if (runtime.service.state().usedByDefinition().containsKey(definition.id())) continue;
            if (definition.exactBlockAnchor().filter(ref -> ref.id().equals(old.id()) && ref.revision() == old.revision()).isPresent()
                    || definition.boardingRegion().filter(ref -> ref.id().equals(old.id()) && ref.revision() == old.revision()).isPresent())
                throw new IllegalStateException("Site is pinned by unused departure '" + definition.name() + "'. Edit/delete the departure first.");
        }
    }

    private static List<DepartureDefinition> matching(Runtime runtime, ServerWorld world, BlockPos position) {
        MinecraftServer server = world.getServer();
        String dimension = world.getRegistryKey().getValue().toString();
        List<DepartureDefinition> result = new ArrayList<>();
        for (DepartureDefinition definition : runtime.service.state().definitions().values()) {
            if (runtime.service.state().usedByDefinition().containsKey(definition.id()) || definition.exactBlockAnchor().isEmpty()) continue;
            var reference = definition.exactBlockAnchor().orElseThrow();
            AuthoringSite site = AuthoringRuntime.get(server).service().state().sites().get(reference.id());
            if (site instanceof AuthoringSite.Anchor anchor && anchor.revision() == reference.revision()
                    && anchor.dimension().equals(dimension) && block(anchor.position()).equals(position)) result.add(definition);
        }
        return result;
    }

    private static void joined(MinecraftServer server, ServerPlayerEntity player) {
        Runtime runtime = WORLDS.get(server);
        if (runtime == null) return;
        enforce(runtime, player);
        DepartureOperation pending = runtime.service.state().operations().values().stream()
                .filter(operation -> operation.stage() != DepartureOperation.Stage.COMPLETE).findFirst().orElse(null);
        if (pending != null) try { drive(server, runtime, pending); }
        catch (IOException | RuntimeException failure) {
            ChronicleCore.LOGGER.error("Departure recovery remains pending after passenger reconnect", failure);
        }
    }

    private static void enforce(Runtime runtime, ServerPlayerEntity player) {
        if (runtime.service.state().isDeparted(player.getUuid())) player.changeGameMode(GameMode.SPECTATOR);
    }

    private static void disconnected(MinecraftServer server, ServerPlayerEntity player) {
        Runtime runtime = WORLDS.get(server);
        if (runtime == null) return;
        Offer offer = runtime.offers.remove(player.getUuid());
        if (offer != null && offer.connection() == player.networkHandler) releaseReview(server, offer.review());
        Pending pending = runtime.pending;
        if (pending != null && pending.actor().equals(player.getUuid()) && pending.connection() == player.networkHandler) {
            runtime.pending = null;
            releasePrepared(server, pending.review().expectedAreaOperationId());
        }
    }

    private static void expire(MinecraftServer server, Runtime runtime) {
        Iterator<Map.Entry<UUID, Offer>> iterator = runtime.offers.entrySet().iterator();
        while (iterator.hasNext()) {
            Offer offer = iterator.next().getValue();
            if (!expired(offer)) continue;
            iterator.remove();
            releaseReview(server, offer.review());
            ServerPlayerEntity actor=server.getPlayerManager().getPlayer(offer.review().actor());
            if(actor!=null&&actor.networkHandler==offer.review().connection())send(actor,offer,"Departure review expired. Use the exact block again.",false,true,false);
        }
    }

    private static boolean expired(Offer offer) {
        long age = System.nanoTime() - offer.createdNanos();
        return age < 0 || age > MAX_AGE_NANOS;
    }

    private static void releaseReview(MinecraftServer server, Review review) {
        review.prepared().ifPresent(effect -> releasePrepared(server, effect.operationId()));
    }

    private static void releasePrepared(MinecraftServer server, UUID areaOperation) {
        try { WorldAreaOperations.releasePreparation(server, areaOperation); }
        catch (RuntimeException failure) { ChronicleCore.LOGGER.warn("Unable to release uncommitted departure Area preparation {}", areaOperation, failure); }
    }

    private static void send(ServerPlayerEntity actor, Offer offer, String status, boolean opening,
                             boolean completed, boolean available) {
        if (!ServerPlayNetworking.canSend(actor, DeparturePayloads.View.ID)) return;
        Review review = offer.review();
        String title = offer.stage() == 1 ? "Leave via " + review.definition().name() + "?" : "Point of no return";
        String details = offer.stage() == 1 ? firstDetails(actor.getEntityWorld().getServer(), review)
                : finalDetails(actor.getEntityWorld().getServer(), review);
        String label = offer.stage() == 1 ? "Continue" : "Depart";
        ServerPlayNetworking.send(actor, new DeparturePayloads.View(offer.token(), offer.parentToken(), offer.stage(),
                title, details, label, clipped(status, 512), available, opening, completed));
    }

    private static String firstDetails(MinecraftServer server, Review review) {
        return review.definition().firstText() + "\n\nPassengers currently aboard:\n" + passengerNames(server, review.roster());
    }

    private static String finalDetails(MinecraftServer server, Review review) {
        return review.definition().finalText() + "\n\nThis permanently ends participation for:\n"
                + passengerNames(server, review.roster()) + "\n\n"
                + (review.concludeCampaign()
                ? "Every enrolled participant will then have departed. This campaign will end permanently."
                : "Everyone else will remain behind.");
    }

    private static String passengerNames(MinecraftServer server, List<UUID> roster) {
        StringBuilder result = new StringBuilder();
        for (UUID id : roster) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            String name = player == null ? id.toString() : player.getName().getString();
            result.append("• ").append(name).append('\n');
        }
        return utf8Clipped(result.toString().stripTrailing(), DeparturePayloads.View.MAX_DETAILS_BYTES - 8_192);
    }

    private static void notify(MinecraftServer server, UUID player, String message) {
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(player);
        if (target != null) target.sendMessage(Text.literal(safe(message)), false);
    }

    private static Runtime runtime(MinecraftServer server) {
        requireThread(server);
        Runtime runtime = WORLDS.get(server);
        if (runtime == null || runtime.failure != null)
            throw new IllegalStateException("Departures are unavailable; inspect departures.json and the server log.");
        return runtime;
    }

    private static void requireThread(MinecraftServer server) {
        Objects.requireNonNull(server, "server is required");
        if (!server.isOnThread()) throw new IllegalStateException("Departures require the server thread.");
    }

    private static ServerWorld world(MinecraftServer server, String dimension) {
        ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dimension)));
        if (world == null) throw new IllegalStateException("Departure dimension is unavailable: " + dimension);
        return world;
    }

    private static BlockPos block(BlockPosition position) { return new BlockPos(position.x(), position.y(), position.z()); }
    private static String conclusionActor(UUID operation) { return "Departure " + operation; }
    private static String safe(String value) { return utf8Clipped(value == null || value.isBlank() ? "Departure could not be completed; inspect the server log." : value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " "), 480); }
    private static String clipped(String value, int maximum) { return value.length() <= maximum ? value : value.substring(0, Math.max(0, maximum - 3)) + "..."; }
    private static String utf8Clipped(String value,int maximumBytes){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);if(bytes.length<=maximumBytes)return value;int end=0,used=0;while(end<value.length()){int point=value.codePointAt(end),count=new String(Character.toChars(point)).getBytes(StandardCharsets.UTF_8).length;if(used+count+3>maximumBytes)break;used+=count;end+=Character.charCount(point);}return value.substring(0,end)+"...";}

    private static final class Runtime {
        final DepartureStore store;
        final DepartureService service;
        final Map<UUID, Offer> offers = new HashMap<>();
        Pending pending;
        String failure;
        String lastRecoveryDiagnostic = "";
        Runtime(DepartureStore store, DepartureService service) { this.store = store; this.service = service; }
    }

    private record AreaPin(String areaId, long areaRevision, AreaState state, String snapshotSha256) { }

    private record Review(UUID operationId, UUID actor, net.minecraft.server.network.ServerPlayNetworkHandler connection,
                          DepartureDefinition definition, long departureStateRevision, long membershipRevision,
                          Set<UUID> enrolled, int sessionNumber, List<UUID> roster, boolean concludeCampaign,
                          AuthoringSite.Anchor anchor, AuthoringSite.Region region, Optional<AreaPin> area,
                          Optional<PreparedAreaEffect> prepared, String createdAt) {
        Review {
            enrolled = Set.copyOf(enrolled);
            roster = List.copyOf(roster);
            area = area == null ? Optional.empty() : area;
            prepared = prepared == null ? Optional.empty() : prepared;
        }
        Review withEffect(PreparedAreaEffect effect) {
            return new Review(operationId, actor, connection, definition, departureStateRevision, membershipRevision, enrolled,
                    sessionNumber, roster, concludeCampaign, anchor, region, area, Optional.of(effect), createdAt);
        }
        UUID expectedAreaOperationId() { return areaOperationId(operationId); }
    }

    private record Offer(UUID token, UUID parentToken, int stage, long createdNanos, Review review) {
        Object connection() { return review.connection(); }
    }

    private record Pending(UUID operationId, UUID actor, net.minecraft.server.network.ServerPlayNetworkHandler connection,
                           Review review, long createdNanos) { }
}
