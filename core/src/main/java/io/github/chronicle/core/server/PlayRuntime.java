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
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(literal("chronicle").then(literal("join").executes(context -> join(context.getSource())))));
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

    /** Includes uncheckpointed active ticks so the UI can display the live clock. */
    public static long elapsedTicks(MinecraftServer server) {
        requireServerThread(server);
        Runtime runtime = WORLDS.get(server);
        return runtime == null || runtime.failure != null ? 0L : runtime.service.elapsedTicks();
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
        if (delivery == null) throw new IllegalStateException("observation delivery does not exist: " + id);
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
    }

    public static void setParticipant(ServerCommandSource source, UUID player, boolean participating) throws IOException {
        requireAuthor(source);
        Objects.requireNonNull(player, "participant UUID is required");
        write(source.getServer(), () -> service(source.getServer()).setParticipant(player, participating));
    }

    /** Self opt-in/out is intentionally allowed during play; eligibility still excludes spectators, dead players and authors. */
    public static void setSelfParticipant(ServerPlayerEntity player, boolean participating) throws IOException {
        Objects.requireNonNull(player, "player is required");
        MinecraftServer server = player.getEntityWorld().getServer();
        write(server, () -> service(server).setParticipant(player.getUuid(), participating));
    }

    /**
     * Builds a read-only, short-lived manual delivery preview. It does not claim completion or play the cue.
     */
    public static Preview preview(MinecraftServer server, String id) {
        requireServerThread(server);
        Runtime runtime = runtime(server);
        CampaignState campaign = activeCampaign(server);
        if (RuntimeHolds.reason(server) != null) throw new IllegalStateException("Resolve the active Chronicle operation before previewing an observation.");

        ObservationCatalog.Definition definition = definition(id);
        AuthoringRegistry authoring = authoring(server);
        PlayState.Binding binding = runtime.service.validate(definition, authoring, id);
        if (runtime.service.state().deliveries().containsKey(id)) throw new IllegalStateException("Observation has already been claimed; reset it in non-active author mode to use it again.");
        requireFlag(binding, campaign.flags());
        List<ServerPlayerEntity> recipients = audience(server, runtime.service.state(), definition, binding, authoring);
        if (recipients.isEmpty()) throw new IllegalStateException("Observation has no eligible recipients.");
        List<UUID> recipientIds = recipients.stream().map(ServerPlayerEntity::getUuid).toList();
        String recipientNames = recipients.stream().limit(8)
                .map(player -> player.getName().getString() + "[" + player.getUuidAsString().substring(0, 8) + "]")
                .reduce((left, right) -> left + ", " + right).orElse("none");
        if (recipients.size() > 8) recipientNames += ", +" + (recipients.size() - 8) + " more";
        String description = bounded(definition.label() + ": " + definition.text() + " Recipients=" + recipientIds.size()
                + " (" + recipientNames + ")"
                + (definition.sound().isBlank() ? "; no sound." : "; private sound=" + definition.sound() + "."), 1_000);
        return new Preview(id, definition.fingerprint(), binding.revision(), campaign.sessionNumber(), recipientIds,
                System.nanoTime(), description);
    }

    /** Rechecks every pinned input before durably claiming and privately delivering the observation. */
    public static void fire(MinecraftServer server, String id, Preview preview, UUID actor) throws IOException {
        requireServerThread(server);
        if (preview == null) throw new IllegalStateException("Preview this observation before firing it.");
        Objects.requireNonNull(actor, "manual observation actor is required");
        if (!Objects.equals(id, preview.observationId())) throw new IllegalStateException("Preview belongs to a different observation.");
        long previewAge = System.nanoTime() - preview.createdAtNanos();
        if (previewAge < 0 || previewAge > PREVIEW_NANOS) throw new IllegalStateException("Observation preview expired; preview it again.");
        Runtime runtime = runtime(server);
        CampaignState campaign = activeCampaign(server);
        if (RuntimeHolds.reason(server) != null) throw new IllegalStateException("Resolve the active Chronicle operation before firing an observation.");
        if (campaign.sessionNumber() != preview.sessionNumber()) throw new IllegalStateException("Session changed after the observation preview.");

        ObservationCatalog.Definition definition = definition(id);
        if (!definition.fingerprint().equals(preview.definitionFingerprint())) throw new IllegalStateException("Observation definition changed after preview.");
        AuthoringRegistry authoring = authoring(server);
        PlayState.Binding binding = runtime.service.validate(definition, authoring, id);
        if (binding.revision() != preview.bindingRevision()) throw new IllegalStateException("Observation binding changed after preview.");
        requireFlag(binding, campaign.flags());
        List<ServerPlayerEntity> recipients = audience(server, runtime.service.state(), definition, binding, authoring);
        List<UUID> currentRecipients = recipients.stream().map(ServerPlayerEntity::getUuid).toList();
        if (!currentRecipients.equals(preview.recipients())) throw new IllegalStateException("Eligible observation audience changed after preview; preview it again.");
        if (recipients.isEmpty()) throw new IllegalStateException("Observation has no eligible recipients.");

        SoundEvent sound = sound(definition);
        boolean claimed;
        try {
            claimed = runtime.service.claim(definition, authoring, id, PlayState.DeliverySource.MANUAL, actor,
                    campaign.sessionNumber(), currentRecipients, campaign.flags());
            runtime.lastCheckpointTicks = runtime.service.elapsedTicks();
        } catch (IOException exception) {
            disable(server, exception, "manual observation claim persistence failed");
            throw exception;
        }
        if (!claimed) throw new IllegalStateException("Observation has already been claimed.");
        try {
            deliver(recipients, definition, sound);
            runtime.service.finish(id);
            runtime.lastCheckpointTicks = runtime.service.elapsedTicks();
        } catch (IOException | RuntimeException exception) {
            disable(server, exception, "manual observation delivery remained uncertain after its durable claim");
            throw exception;
        }
    }

    private static int join(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Joining Chronicle participation requires your player account."));
            return 0;
        }
        try {
            boolean already = service(source.getServer()).state().participants().contains(player.getUuid());
            setSelfParticipant(player, true);
            source.sendFeedback(() -> Text.literal(already
                    ? "You are already a Chronicle participant."
                    : "You joined Chronicle participation. Active occupied play now includes you; no observation history was replayed."), false);
            return 1;
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            source.sendError(Text.literal(message(exception)));
            return 0;
        }
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
        PlayState state = runtime.service.state();
        AuthoringRegistry authoring = authoring(server);
        runtime.invalidBindings.keySet().retainAll(state.bindings().keySet());
        List<String> ids = new ArrayList<>(state.bindings().keySet());
        ids.sort(String::compareTo);
        for (String id : ids) {
            if (state.deliveries().containsKey(id)) continue;
            PlayState.Binding saved = state.bindings().get(id);
            AutomaticCandidate candidate;
            try {
                ObservationCatalog.Definition definition = definition(id);
                PlayState.Binding binding = runtime.service.validate(definition, authoring, id);
                List<ServerPlayerEntity> present = binding.regionId().isBlank()
                        ? List.of() : playersInRegion(eligible, region(authoring, binding));
                if (!runtime.service.due(binding, !present.isEmpty(), campaign.flags())) {
                    runtime.valid(id);
                    continue;
                }
                List<ServerPlayerEntity> recipients = audience(eligible, definition, binding, authoring);
                if (recipients.isEmpty()) {
                    runtime.valid(id);
                    continue;
                }
                PlayState.DeliverySource source = deliverySource(binding.mode());
                UUID actor = source == PlayState.DeliverySource.ENTRY ? present.getFirst().getUuid() : null;
                candidate = new AutomaticCandidate(definition, recipients, source, actor,
                        recipients.stream().map(ServerPlayerEntity::getUuid).toList(), sound(definition));
                runtime.valid(id);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                runtime.invalid(id, saved.revision(), exception);
                continue;
            }
            if (!runtime.service.claim(candidate.definition(), authoring, id, candidate.source(), candidate.actor(),
                    campaign.sessionNumber(), candidate.recipientIds(), campaign.flags())) continue;
            runtime.lastCheckpointTicks = runtime.service.elapsedTicks();
            deliver(candidate.recipients(), candidate.definition(), candidate.sound());
            runtime.service.finish(id);
            runtime.lastCheckpointTicks = runtime.service.elapsedTicks();
            ChronicleCore.LOGGER.info("Chronicle observation delivered: id={} source={} recipients={}",
                    id, candidate.source(), candidate.recipientIds().size());
            return;
        }
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
        return server.getPlayerManager().getPlayerList().stream()
                .filter(player -> state.participants().contains(player.getUuid()))
                .filter(ServerPlayerEntity::isAlive)
                .filter(player -> !player.isSpectator())
                .filter(player -> !AuthoringRuntime.isAuthor(player))
                .sorted(Comparator.comparing(player -> player.getUuid().toString()))
                .toList();
    }

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
            throw new IllegalStateException("Participation and observation edits require operator permission and non-active author mode.");
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

    private static String message(Exception exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) return "Participation update failed; inspect the server log.";
        value = value.replaceAll("[\\p{Cntrl}]", " ");
        return value.substring(0, Math.min(256, value.length()));
    }

    private static String bounded(String value, int maximum) {
        return value.substring(0, Math.min(maximum, value.length()));
    }

    public record Preview(String observationId, String definitionFingerprint, long bindingRevision, int sessionNumber,
                          List<UUID> recipients, long createdAtNanos, String description) {
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
        long lastCheckpointTicks;
        SessionPhase lastPhase;
        Boolean lastOccupied;
        Boolean lastHeld;
        String failure;

        Runtime(PlayService service) {
            this.service = service;
            this.lastCheckpointTicks = service.elapsedTicks();
        }

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
