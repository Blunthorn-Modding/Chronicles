package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.AuthoringService;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.AuthoringStore;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.authoring.RegionDraft;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.network.AuthoringPreviewPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server-thread-only per-world authoring state. Geometry never activates gameplay. */
public final class AuthoringRuntime {
    private static final Map<MinecraftServer, AuthoringRuntime> WORLDS = new IdentityHashMap<>();
    private final AuthoringService service;
    private final Map<UUID, Selection> authors = new HashMap<>();
    private final Map<UUID, RegionDraft> regionDrafts = new HashMap<>();
    private final Map<UUID, String> previews = new HashMap<>();

    private AuthoringRuntime(AuthoringService service) { this.service = service; }

    public static void start(MinecraftServer server) {
        var file = server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/authoring.json");
        try {
            AuthoringService service = new AuthoringService(new AuthoringStore(file));
            service.mutationGuard((existing, proposed, operation) -> ManagedBlocks.checkAuthoringMutation(server, existing, proposed));
            WORLDS.put(server, new AuthoringRuntime(service));
            ChronicleCore.LOGGER.info("Chronicle authoring ready: {}", file);
        } catch (IOException | IllegalArgumentException exception) {
            WORLDS.remove(server);
            ChronicleCore.LOGGER.error("Authoring unavailable; existing data was not replaced. Restore {} while stopped.", file, exception);
        }
    }

    public static AuthoringRuntime get(MinecraftServer server) {
        if (!server.isOnThread()) throw new IllegalStateException("Authoring requires the server thread.");
        var runtime = WORLDS.get(server);
        if (runtime == null) throw new IllegalStateException("Authoring unavailable; inspect the server log and authoring.json while stopped.");
        return runtime;
    }

    public static void stop(MinecraftServer server) { WORLDS.remove(server); }

    public static void disconnected(ServerPlayerEntity player) {
        var runtime = WORLDS.get(player.getEntityWorld().getServer());
        if (runtime != null) {
            runtime.authors.remove(player.getUuid());
            runtime.regionDrafts.remove(player.getUuid());
            runtime.previews.remove(player.getUuid());
        }
    }

    /** Future condition services use this instead of letting builder walks consume discoveries. */
    public static boolean isAuthor(ServerPlayerEntity player) {
        var runtime = WORLDS.get(player.getEntityWorld().getServer());
        return runtime != null && runtime.authors.containsKey(player.getUuid()) && editable(player.getEntityWorld().getServer());
    }

    public static boolean editable(MinecraftServer server) {
        try { return ChronicleRuntime.service(server).state().phase() != SessionPhase.ACTIVE; }
        catch (IllegalStateException exception) { return false; }
    }

    public AuthoringService service() { return service; }
    public void enable(UUID player) { authors.putIfAbsent(player, new Selection(null, null, null)); }
    public void disable(UUID player) {
        authors.remove(player);
        regionDrafts.remove(player);
    }
    public Selection selection(UUID player) {
        var selection = authors.get(player);
        if (selection == null) throw new IllegalStateException("Enable author mode first: /chronicle author mode on");
        return selection;
    }
    public void selection(UUID player, Selection selection) { selection(player); authors.put(player, selection); }

    /** Begins a new, unsaved draft. The draft remains while the author menu is closed. */
    public RegionDraft beginRegionDraft(ServerPlayerEntity player, String id) {
        requireEditableAuthor(player);
        if (service.state().sites().containsKey(id)) {
            throw new IllegalStateException("Region already exists: " + id + "; use region edit " + id + ".");
        }
        Selection selected = selection(player.getUuid());
        String dimension = dimension(player);
        if (selected.dimension() != null && !selected.dimension().equals(dimension)) {
            throw new IllegalStateException("Selection is in another dimension; clear it first.");
        }
        RegionDraft draft = RegionDraft.create(id, dimension);
        if (selected.first() != null) draft = draft.withCorner(1, selected.first());
        if (selected.second() != null) draft = draft.withCorner(2, selected.second());
        regionDrafts.put(player.getUuid(), draft);
        selection(player.getUuid(), new Selection(draft.dimension(), draft.first(), draft.second()));
        previewRegionDraft(player);
        return draft;
    }

    /**
     * Resumes this author's unsaved draft for the same ID, otherwise loads the exact saved region
     * and immediately displays its outline. The loaded revision is checked again on save.
     */
    public RegionDraft resumeRegionDraft(ServerPlayerEntity player, String id) {
        requireEditableAuthor(player);
        RegionDraft current = regionDrafts.get(player.getUuid());
        if (current != null && current.id().equals(id)) {
            previewRegionDraft(player);
            return current;
        }
        AuthoringSite site = service.state().sites().get(id);
        if (!(site instanceof AuthoringSite.Region region)) {
            throw new IllegalArgumentException("No saved region named " + id + ".");
        }
        if (!region.dimension().equals(dimension(player))) {
            throw new IllegalStateException("Region is in " + region.dimension() + "; edit it from that dimension.");
        }
        RegionDraft draft = RegionDraft.edit(region);
        regionDrafts.put(player.getUuid(), draft);
        selection(player.getUuid(), new Selection(draft.dimension(), draft.first(), draft.second()));
        previewRegionDraft(player);
        return draft;
    }

    /** Reopens the current draft after a menu close without changing either selected corner. */
    public RegionDraft resumeRegionDraft(ServerPlayerEntity player) {
        requireEditableAuthor(player);
        RegionDraft draft = regionDrafts.get(player.getUuid());
        if (draft == null) throw new IllegalStateException("No region draft is open; use region edit <id> or region create <id>.");
        previewRegionDraft(player);
        return draft;
    }

    public Optional<RegionDraft> regionDraft(UUID player) { return Optional.ofNullable(regionDrafts.get(player)); }

    /** Replaces one selected corner only and refreshes the private draft outline. */
    public RegionDraft setRegionDraftCorner(ServerPlayerEntity player, int corner, BlockPosition position) {
        requireEditableAuthor(player);
        RegionDraft draft = regionDrafts.get(player.getUuid());
        if (draft == null) throw new IllegalStateException("Start a region with region create <id> or region edit <id> first.");
        if (!draft.dimension().equals(dimension(player))) {
            throw new IllegalStateException("Region draft is in another dimension.");
        }
        checkBounds((ServerWorld) player.getEntityWorld(), position);
        draft = draft.withCorner(corner, position);
        regionDrafts.put(player.getUuid(), draft);
        selection(player.getUuid(), new Selection(draft.dimension(), draft.first(), draft.second()));
        previewRegionDraft(player);
        return draft;
    }

    /** Displays a cuboid when complete, or the selected corner when only one exists. */
    public void previewRegionDraft(ServerPlayerEntity player) {
        RegionDraft draft = regionDrafts.get(player.getUuid());
        if (draft == null) throw new IllegalStateException("No region draft is open.");
        if (draft.complete()) {
            preview(player, draft.region());
            return;
        }
        BlockPosition corner = draft.first() == null ? draft.second() : draft.first();
        if (corner == null) {
            clearPreview(player);
            return;
        }
        // Location preview renders the one selected corner without inventing a partial cuboid.
        preview(player, new AuthoringSite.Location("region_draft_corner", draft.dimension(), 1,
                corner.x() + .5, corner.y() + .5, corner.z() + .5, 0, 0));
    }

    /** Saves a new draft or exact-revision replacement, and keeps the saved outline open. */
    public AuthoringSite.Region saveRegionDraft(ServerPlayerEntity player) throws IOException {
        requireEditableAuthor(player);
        RegionDraft draft = regionDrafts.get(player.getUuid());
        if (draft == null) throw new IllegalStateException("No region draft is open.");
        if (!draft.dimension().equals(dimension(player))) throw new IllegalStateException("Region draft is in another dimension.");
        AuthoringSite.Region candidate = draft.region();
        checkBounds((ServerWorld) player.getEntityWorld(), candidate.min());
        checkBounds((ServerWorld) player.getEntityWorld(), candidate.max());
        ChronicleCore.authoring().validate(candidate);
        AuthoringSite current = service.state().sites().get(draft.id());
        boolean replace = draft.existing();
        if (replace) {
            if (!(current instanceof AuthoringSite.Region existing) || existing.revision() != draft.baseRevision()) {
                throw new IllegalStateException("Region changed since this draft was opened. Reopen region edit " + draft.id() + " before saving.");
            }
        } else if (current != null) {
            throw new IllegalStateException("Region was created while this draft was open. Reopen it before saving.");
        }
        AuthoringSite.Region saved = (AuthoringSite.Region) service.put(candidate, replace);
        RegionDraft savedDraft = RegionDraft.edit(saved);
        regionDrafts.put(player.getUuid(), savedDraft);
        selection(player.getUuid(), new Selection(saved.dimension(), saved.min(), saved.max()));
        preview(player, saved);
        return saved;
    }
    public void preview(ServerPlayerEntity player, AuthoringSite site) {
        if (!site.dimension().equals(player.getEntityWorld().getRegistryKey().getValue().toString())) {
            clearPreview(player);
            return;
        }
        if (!ServerPlayNetworking.canSend(player, AuthoringPreviewPayload.ID)) {
            player.sendMessage(Text.literal("Preview requires the matching Chronicle M7 client pack."), false);
            return;
        }
        AuthoringPreviewPayload payload;
        if (site instanceof AuthoringSite.Region region) {
            payload = new AuthoringPreviewPayload(site.dimension(), true,
                    region.min().x(), region.min().y(), region.min().z(),
                    region.max().x() + 1.0, region.max().y() + 1.0, region.max().z() + 1.0, false, 0, 0);
        } else if (site instanceof AuthoringSite.Location point) {
            payload = new AuthoringPreviewPayload(site.dimension(), true, point.x() - .1, point.y() - .1, point.z() - .1,
                    point.x() + .1, point.y() + .1, point.z() + .1, true, point.yaw(), point.pitch());
        } else {
            var anchor = (AuthoringSite.Anchor) site;
            var pos = anchor.position();
            payload = new AuthoringPreviewPayload(site.dimension(), true, pos.x(), pos.y(), pos.z(), pos.x() + 1.0, pos.y() + 1.0, pos.z() + 1.0, false, 0, 0);
        }
        ServerPlayNetworking.send(player, payload);
        previews.put(player.getUuid(), site.dimension());
    }

    public static void clearPreview(ServerPlayerEntity player) {
        var runtime = WORLDS.get(player.getEntityWorld().getServer());
        if (runtime != null) runtime.previews.remove(player.getUuid());
        if (ServerPlayNetworking.canSend(player, AuthoringPreviewPayload.ID)) {
            ServerPlayNetworking.send(player, AuthoringPreviewPayload.clear(player.getEntityWorld().getRegistryKey().getValue().toString()));
        }
    }

    public static void tick(MinecraftServer server) {
        var runtime = WORLDS.get(server);
        if (runtime == null) return;
        if (!editable(server) && !runtime.authors.isEmpty()) {
            for (UUID id : java.util.List.copyOf(runtime.authors.keySet())) {
                var player = server.getPlayerManager().getPlayer(id);
                if (player != null) clearPreview(player);
            }
            runtime.authors.clear();
            runtime.regionDrafts.clear();
        }
        for (var entry : java.util.List.copyOf(runtime.previews.entrySet())) {
            var player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) runtime.previews.remove(entry.getKey());
            else if (!player.getCommandSource().hasPermissionLevel(2)
                    || !entry.getValue().equals(player.getEntityWorld().getRegistryKey().getValue().toString())) clearPreview(player);
        }
    }

    public record Selection(String dimension, BlockPosition first, BlockPosition second) { }

    private static String dimension(ServerPlayerEntity player) {
        return player.getEntityWorld().getRegistryKey().getValue().toString();
    }

    private void requireEditableAuthor(ServerPlayerEntity player) {
        if (!editable(player.getEntityWorld().getServer())) {
            throw new IllegalStateException("Authoring edits require a non-active, available Chronicle session.");
        }
        selection(player.getUuid());
    }

    private static void checkBounds(ServerWorld world, BlockPosition position) {
        var block = new net.minecraft.util.math.BlockPos(position.x(), position.y(), position.z());
        if (!world.isInBuildLimit(block)) throw new IllegalArgumentException("Position outside dimension build limits.");
        if (!world.getWorldBorder().contains(block)) throw new IllegalArgumentException("Position outside world border.");
    }
}
