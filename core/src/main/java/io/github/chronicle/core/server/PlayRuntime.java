package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.AuthoringRegistry;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.campaign.CampaignState;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.play.ObservationCatalog;
import io.github.chronicle.core.play.ObservationConfiguration;
import io.github.chronicle.core.play.PlayService;
import io.github.chronicle.core.play.PlayState;
import io.github.chronicle.core.play.PlayStore;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Minecraft adapter for explicit participation, the occupied-play clock and private observations.
 * The bounded pure state/service types live in {@code core.play}; this class alone reads players and worlds.
 */
public final class PlayRuntime {
    private static final long CHECKPOINT_TICKS = 20L * 60L;
    private static final long PREVIEW_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final Map<MinecraftServer, Runtime> WORLDS = new IdentityHashMap<>();

    private PlayRuntime() { }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(PlayRuntime::start);
        ServerTickEvents.END_SERVER_TICK.register(PlayRuntime::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(PlayRuntime::stopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
    }

    private static void start(MinecraftServer server) {
        var path = server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/play-runtime.json");
        try {
            validateSounds();
            PlayService service = new PlayService(new PlayStore(path));
            WORLDS.put(server, new Runtime(service));
            ChronicleCore.LOGGER.info("Chronicle participation and observations ready: {}", path);
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            WORLDS.remove(server);
            ChronicleCore.LOGGER.error("Chronicle participation and observations disabled; existing data preserved at {}", path, exception);
        }
    }

    public static PlayService service(MinecraftServer server) {
        requireServerThread(server);
        Runtime runtime = WORLDS.get(server);
        if (runtime == null || runtime.failure != null) {
            throw new IllegalStateException("Participation and observations are unavailable; inspect chronicle/play-runtime.json and the server log.");
        }
        return runtime.service;
    }

    /** Returns the durable state for private authoring UI snapshots, or {@code null} while this optional service is disabled. */
    public static PlayState available(MinecraftServer server) {
        requireServerThread(server);
        Runtime runtime = WORLDS.get(server);
        return runtime == null || runtime.failure != null ? null : runtime.service.state();
    }

    /** Connection-scoped review pin: clocks/events do not change it, every membership edit does. */
    public static long membershipRevision(MinecraftServer server){Runtime r=runtime(server);r.observeMembership();return r.membershipRevision;}

    /** Includes uncheckpointed active ticks so the UI can display the live clock. */
    public static long elapsedTicks(MinecraftServer server) {
        requireServerThread(server);
        Runtime runtime = WORLDS.get(server);
        return runtime == null || runtime.failure != null ? 0L : runtime.service.elapsedTicks();
    }

    /** Checkpoints the occupied-play clock before another authority discards cooldown evidence. */
    public static long checkpointElapsedTicks(MinecraftServer server) throws IOException {
        service(server);Runtime runtime=WORLDS.get(server);
        try{runtime.service.checkpoint();runtime.lastCheckpointTicks=runtime.service.state().elapsedTicks();return runtime.lastCheckpointTicks;}
        catch(IOException|RuntimeException error){disable(server,error,"play clock dependency checkpoint failed");throw error;}
    }

    /** Enrolled, online, alive players who currently count as play rather than authoring or spectating. */
    public static List<ServerPlayerEntity> eligiblePlayers(MinecraftServer server) {
        requireServerThread(server);
        PlayState state = service(server).state();
        return eligiblePlayers(server, state);
    }

    public static List<UUID> eligibleIds(MinecraftServer server) {
        requireServerThread(server);
        PlayState state = available(server);
        return state == null ? List.of() : eligiblePlayers(server, state).stream().map(ServerPlayerEntity::getUuid).toList();
    }

    public static void bind(ServerCommandSource source, String id, ObservationConfiguration configuration, boolean replace) throws IOException {
        requireAuthor(source);
        Objects.requireNonNull(configuration, "observation configuration is required");
        ObservationCatalog.Definition definition = definition(id);
        write(source.getServer(), () -> service(source.getServer()).bind(definition, authoring(source.getServer()), configuration, replace));
    }

    public static void off(ServerCommandSource source, String id) throws IOException {
        requireAuthor(source);
        write(source.getServer(), () -> service(source.getServer()).off(id));
    }

    public static void reset(ServerCommandSource source, String id) throws IOException {
        requireAuthor(source);
        var delivery = service(source.getServer()).state().deliveries().get(id);
        if (delivery == null) {
            var execution=service(source.getServer()).state().executions().get(id);
            if(execution==null)throw new IllegalStateException("Event delivery does not exist: "+id);
            write(source.getServer(),()->service(source.getServer()).reset(id));
            EventProgressRuntime.arm(source.getServer(),EventRuntime.configuration(source.getServer(),id));return;
        }
        // Retain the old receipt before clearing it. A crash/failure between stores records a
        // reset request, never a false claim that the reset completed or the cue was delivered.
        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("priorStatus", delivery.status().name());
        evidence.put("source", delivery.source().name());
        evidence.put("actor", delivery.actor() == null ? "automatic" : delivery.actor().toString());
        evidence.put("session", Integer.toString(delivery.session()));
        evidence.put("elapsedTicks", Long.toString(delivery.elapsedTicks()));
        for (int offset = 0; offset < delivery.recipients().size(); offset += 24) {
            evidence.put("recipients." + offset / 24, delivery.recipients().subList(offset, Math.min(offset + 24, delivery.recipients().size())).toString());
        }
        write(source.getServer(), () -> {
            ChronicleRuntime.service(source.getServer()).recordOperation(new io.github.chronicle.core.campaign.Actor(source.getName(), source.getPlayer().getUuidAsString()), "observation.reset.requested", id, evidence);
            service(source.getServer()).reset(id);
        });
        EventProgressRuntime.arm(source.getServer(),EventRuntime.configuration(source.getServer(),id));
    }

    public static void setParticipant(ServerCommandSource source, UUID player, boolean participating) throws IOException {
        requireAuthor(source);
        Objects.requireNonNull(player, "participant UUID is required");
        Departures.requireNoPending(source.getServer());
        if (participating) Departures.requireParticipation(source.getServer(), player);
        write(source.getServer(), () -> service(source.getServer()).setParticipant(player, participating));
    }

    /** Self opt-in/out is intentionally allowed during play; eligibility still excludes spectators, dead players and authors. */
    public static void setSelfParticipant(ServerPlayerEntity player, boolean participating) throws IOException {
        Objects.requireNonNull(player, "player is required");
        MinecraftServer server = player.getEntityWorld().getServer();
        Departures.requireNoPending(server);
        if (participating) Departures.requireParticipation(server, player.getUuid());
        write(server, () -> service(server).setParticipant(player.getUuid(), participating));
    }

    /**
     * Builds a read-only, short-lived manual delivery preview. It does not claim completion or play the cue.
     */
    public static Preview preview(MinecraftServer server, String id) {
        var review=EventRuntime.review(server,id);
        return new Preview(id,review.configuration().definition().fingerprint(),review.configuration().definition().revision(),review.session(),review.recipients(),review.createdAtNanos(),review.description(),review);
    }

    /** Rechecks every pinned input before durably claiming and privately delivering the observation. */
    public static void fire(MinecraftServer server, String id, Preview preview, UUID actor) throws IOException {
        var player=server.getPlayerManager().getPlayer(actor);
        if(player==null)throw new IllegalStateException("The Event operator must be online.");
        EventRuntime.run(player,id,preview==null?null:preview.eventReview());
    }

    private static void tick(MinecraftServer server) {
        Runtime runtime = WORLDS.get(server);
        if (runtime == null || runtime.failure != null) return;
        try {
            CampaignState campaign = campaign(server);
            List<ServerPlayerEntity> eligible = eligiblePlayers(server, runtime.service.state());
            SessionPhase phase = campaign == null ? null : campaign.phase();
            boolean occupied = !eligible.isEmpty();
            boolean held = RuntimeHolds.reason(server) != null;
            boolean advancing = phase == SessionPhase.ACTIVE && !held && occupied;
            if (runtime.lastPhase != phase || runtime.lastOccupied == null || runtime.lastOccupied != occupied
                    || runtime.lastHeld == null || runtime.lastHeld != held) {
                runtime.service.checkpoint();
                runtime.lastCheckpointTicks = runtime.service.elapsedTicks();
                runtime.lastPhase = phase;
                runtime.lastOccupied = occupied;
                runtime.lastHeld = held;
            }
            runtime.service.advance(advancing);
            if (runtime.service.elapsedTicks() - runtime.lastCheckpointTicks >= CHECKPOINT_TICKS) {
                runtime.service.checkpoint();
                runtime.lastCheckpointTicks = runtime.service.elapsedTicks();
            }
            if (!advancing) return;
            automatic(server, runtime, campaign, eligible);
        } catch (IOException | RuntimeException exception) {
            disable(server, exception, "automatic play clock or observation processing failed");
        }
    }

    /** Scans bounded configured bindings and stops after the first actual claim/delivery attempt. */
    private static void automatic(MinecraftServer server, Runtime runtime, CampaignState campaign,
                                  List<ServerPlayerEntity> eligible) throws IOException {
        EventRuntime.automatic(server);
    }

    private static void stopping(MinecraftServer server) {
        Runtime runtime = WORLDS.get(server);
        if (runtime == null || runtime.failure != null) return;
        try {
            runtime.service.checkpoint();
            runtime.lastCheckpointTicks = runtime.service.elapsedTicks();
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            disable(server, exception, "play clock shutdown checkpoint failed");
        }
    }

    private static List<ServerPlayerEntity> eligiblePlayers(MinecraftServer server, PlayState state) {
        if (!Departures.ready(server) || Departures.concluded(server)) return List.of();
        var departures=Departures.state(server);
        return server.getPlayerManager().getPlayerList().stream()
                .filter(player -> state.participants().contains(player.getUuid()))
                .filter(ServerPlayerEntity::isAlive)
                .filter(player -> !player.isSpectator())
                .filter(player -> !AuthoringRuntime.isAuthor(player))
                .filter(player -> departureAllows(true,false,departures.isDeparted(player.getUuid())))
                .sorted(Comparator.comparing(player -> player.getUuid().toString()))
                .toList();
    }

    static boolean departureAllows(boolean ready,boolean concluded,boolean departed){return ready&&!concluded&&!departed;}

    private static List<ServerPlayerEntity> audience(MinecraftServer server, PlayState state,
                                                     ObservationCatalog.Definition definition, PlayState.Binding binding,
                                                     AuthoringRegistry authoring) {
        return audience(eligiblePlayers(server, state), definition, binding, authoring);
    }

    private static List<ServerPlayerEntity> audience(List<ServerPlayerEntity> eligible,
                                                     ObservationCatalog.Definition definition, PlayState.Binding binding,
                                                     AuthoringRegistry authoring) {
        return switch (definition.audience()) {
            case PARTICIPANTS -> eligible;
            case REGION -> playersInRegion(eligible, region(authoring, binding));
        };
    }

    private static List<ServerPlayerEntity> playersInRegion(List<ServerPlayerEntity> players, AuthoringSite.Region region) {
        return players.stream().filter(player -> inside(player, region)).toList();
    }

    private static boolean inside(ServerPlayerEntity player, AuthoringSite.Region region) {
        if (!region.dimension().equals(player.getEntityWorld().getRegistryKey().getValue().toString())) return false;
        var position = player.getBlockPos();
        return region.contains(new BlockPosition(position.getX(), position.getY(), position.getZ()));
    }

    private static AuthoringSite.Region region(AuthoringRegistry authoring, PlayState.Binding binding) {
        AuthoringSite site = authoring.sites().get(binding.regionId());
        if (!(site instanceof AuthoringSite.Region region) || region.revision() != binding.regionRevision()) {
            throw new IllegalStateException("Observation region is missing, changed or is not a region: " + binding.regionId());
        }
        return region;
    }

    private static void deliver(List<ServerPlayerEntity> recipients, ObservationCatalog.Definition definition, SoundEvent sound) {
        for (ServerPlayerEntity player : recipients) {
            player.sendMessage(Text.literal(definition.text()), false);
            if (sound != null) player.playSoundToPlayer(sound, SoundCategory.AMBIENT, 1.0F, 1.0F);
        }
    }

    private static SoundEvent sound(ObservationCatalog.Definition definition) {
        if (definition.sound().isBlank()) return null;
        Identifier id = Identifier.tryParse(definition.sound());
        if (id == null) throw new IllegalStateException("Observation has an invalid sound ID: " + definition.id());
        return Registries.SOUND_EVENT.getOptionalValue(id)
                .orElseThrow(() -> new IllegalStateException("Observation uses an unknown sound: " + definition.sound()));
    }

    private static void validateSounds() {
        for (ObservationCatalog.Definition definition : ChronicleCore.observations().definitions()) sound(definition);
    }

    private static PlayState.DeliverySource deliverySource(PlayState.Mode mode) {
        return switch (mode) {
            case ENTRY -> PlayState.DeliverySource.ENTRY;
            case TIMER -> PlayState.DeliverySource.TIMER;
            case CONDITION -> PlayState.DeliverySource.CONDITION;
            case SYSTEM -> PlayState.DeliverySource.SYSTEM;
            case MANUAL -> throw new IllegalStateException("Manual observations are never automatic.");
        };
    }

    private static void requireFlag(PlayState.Binding binding, Map<String, Boolean> flags) {
        if (!binding.requiredFlag().isBlank() && !flags.getOrDefault(binding.requiredFlag(), false)) {
            throw new IllegalStateException("Required observation flag is not true: " + binding.requiredFlag());
        }
    }

    private static ObservationCatalog.Definition definition(String id) {
        return ChronicleCore.observations().get(id)
                .orElseThrow(() -> new IllegalArgumentException("Choose a registered observation."));
    }

    private static AuthoringRegistry authoring(MinecraftServer server) {
        return AuthoringRuntime.get(server).service().state();
    }

    private static CampaignState activeCampaign(MinecraftServer server) {
        CampaignState campaign = campaign(server);
        if (campaign == null || campaign.phase() != SessionPhase.ACTIVE) {
            throw new IllegalStateException("Observation delivery requires an active Chronicle session.");
        }
        return campaign;
    }

    private static CampaignState campaign(MinecraftServer server) {
        var campaign = ChronicleRuntime.available(server);
        return campaign == null ? null : campaign.state();
    }

    private static void requireAuthor(ServerCommandSource source) {
        if (!source.hasPermissionLevel(2) || source.getPlayer() == null || !AuthoringRuntime.isAuthor(source.getPlayer())
                || !AuthoringRuntime.editable(source.getServer())) {
            throw new IllegalStateException("Participation and observation edits require operator permission and non-active Editing.");
        }
    }

    private static Runtime runtime(MinecraftServer server) {
        service(server);
        return WORLDS.get(server);
    }

    private static void write(MinecraftServer server, Write operation) throws IOException {
        try {
            operation.run();
            Runtime runtime = runtime(server);
            runtime.observeMembership();
            runtime.lastCheckpointTicks = runtime.service.elapsedTicks();
        } catch (IOException exception) {
            disable(server, exception, "play state persistence failed");
            throw exception;
        }
    }

    private static void disable(MinecraftServer server, Exception exception, String context) {
        Runtime runtime = WORLDS.get(server);
        if (runtime != null && runtime.failure == null) runtime.failure = context + ": " + exception;
        ChronicleCore.LOGGER.error("Chronicle participation and observations disabled: {}. Existing evidence is preserved; no automatic retry.", context, exception);
    }

    private static void requireServerThread(MinecraftServer server) {
        Objects.requireNonNull(server, "server is required");
        if (!server.isOnThread()) throw new IllegalStateException("Participation and observations require the server thread.");
    }

    static String message(Exception exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) return "Participation update failed; inspect the server log.";
        value = value.replaceAll("[\\p{Cntrl}]", " ");
        return value.substring(0, Math.min(256, value.length()));
    }

    private static String bounded(String value, int maximum) {
        return value.substring(0, Math.min(maximum, value.length()));
    }

    public record Preview(String observationId, String definitionFingerprint, long bindingRevision, int sessionNumber,
                          List<UUID> recipients, long createdAtNanos, String description, EventRuntime.Review eventReview) {
        public Preview {
            Objects.requireNonNull(observationId, "observation ID is required");
            Objects.requireNonNull(definitionFingerprint, "definition fingerprint is required");
            recipients = List.copyOf(Objects.requireNonNull(recipients, "preview recipients are required"));
            if (bindingRevision < 1) throw new IllegalArgumentException("preview binding revision must be positive");
            if (sessionNumber < 1) throw new IllegalArgumentException("preview session must be positive");
            if (recipients.isEmpty() || recipients.size() > PlayState.MAX_PARTICIPANTS
                    || recipients.stream().anyMatch(Objects::isNull)
                    || recipients.stream().distinct().count() != recipients.size()) {
                throw new IllegalArgumentException("preview must contain distinct eligible recipients");
            }
            Objects.requireNonNull(description, "preview description is required");
        }
    }

    private static final class Runtime {
        final PlayService service;
        final Map<String, String> invalidBindings = new LinkedHashMap<>();
        long lastCheckpointTicks,membershipRevision;
        java.util.Set<UUID> membership;
        SessionPhase lastPhase;
        Boolean lastOccupied;
        Boolean lastHeld;
        String failure;

        Runtime(PlayService service) {
            this.service = service;
            this.membership=java.util.Set.copyOf(service.state().participants());
            this.lastCheckpointTicks = service.elapsedTicks();
        }

        void observeMembership(){var current=service.state().participants();if(!current.equals(membership)){membership=java.util.Set.copyOf(current);membershipRevision=Math.addExact(membershipRevision,1);}}

        void valid(String id) { invalidBindings.remove(id); }

        void invalid(String id, long revision, Exception exception) {
            String diagnostic = revision + ":" + exception.getClass().getSimpleName() + ":" + message(exception);
            if (diagnostic.equals(invalidBindings.put(id, diagnostic))) return;
            ChronicleCore.LOGGER.warn("Chronicle observation binding skipped until it is replaced: id={} revision={} reason={}",
                    id, revision, message(exception));
        }
    }

    private record AutomaticCandidate(ObservationCatalog.Definition definition, List<ServerPlayerEntity> recipients,
                                      PlayState.DeliverySource source, UUID actor, List<UUID> recipientIds,
                                      SoundEvent sound) { }

    @FunctionalInterface
    private interface Write { void run() throws IOException; }
}
