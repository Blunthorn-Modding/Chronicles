package io.github.chronicle.core.server;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.access.RegionAccessStore;
import io.github.chronicle.core.access.RegionAccessStore.Binding;
import io.github.chronicle.core.access.RegionBoundary;
import io.github.chronicle.core.authoring.AuthoringRegistry;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.spawn.SpawnDestinationValidator;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Server-owned Region access. The registering mod owns player eligibility and any visit state. */
public final class RegionAccess {
    private static final String HOLD = "chronicle_core:region_access";
    private static final Map<String, Policy> POLICIES = new HashMap<>();
    private static final Map<MinecraftServer, Runtime> WORLDS = new IdentityHashMap<>();
    private static boolean frozen;

    public interface Policy {
        boolean allows(ServerPlayerEntity player, AuthoringSite.Region region);
        default boolean active(MinecraftServer server) { return true; }
        default String editRefusal(MinecraftServer server) { return ""; }
        default void entered(ServerPlayerEntity player, AuthoringSite.Region region) { }
        default void left(ServerPlayerEntity player, AuthoringSite.Region region) { }
    }

    private record Guard(Binding binding, AuthoringSite.Region region, Box volume, AuthoringSite.Location point) { }
    private record Catalog(Map<String, Binding> bindings, Map<String, Guard> guards, Map<String, List<Guard>> dimensions) { }
    public record Movement(ServerWorld world, Box body, Vec3d origin) { }

    private static final class Runtime {
        final RegionAccessStore store;
        final Map<UUID, Set<String>> occupied = new HashMap<>();
        final Map<UUID, Set<String>> bypassOnly = new HashMap<>();
        final Map<UUID, Integer> lastRefusal = new HashMap<>();
        final Map<UUID, Movement> packetMoves = new HashMap<>();
        final Map<UUID, Integer> teleports = new HashMap<>();
        final Set<String> failedPolicies = new HashSet<>();
        Catalog catalog = new Catalog(Map.of(), Map.of(), Map.of());
        boolean failed;
        int lastVerifiedTick = Integer.MIN_VALUE;

        Runtime(RegionAccessStore store) { this.store = store; }
        boolean inUse() { return failed || !catalog.bindings().isEmpty(); }
    }

    private RegionAccess() { }

    public static void registerPolicy(String id, Policy policy) {
        if (frozen) throw new IllegalStateException("Region access policy registration is frozen");
        if (id == null || id.length() > 160 || !AuthoringSite.NAMESPACED_ID.matcher(id).matches() || policy == null)
            throw new IllegalArgumentException("A namespaced Region access policy and handler are required");
        if (POLICIES.putIfAbsent(id, policy) != null) throw new IllegalArgumentException("Duplicate Region access policy: " + id);
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> frozen = true);
        ServerLifecycleEvents.SERVER_STARTED.register(RegionAccess::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
        ServerTickEvents.START_SERVER_TICK.register(RegionAccess::tick);
        ServerTickEvents.END_SERVER_TICK.register(RegionAccess::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> reconcile(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> reconcile(player));
        ServerDisconnects.register((handler, server) -> {
            Runtime runtime = WORLDS.get(server);
            if (runtime == null) return;
            UUID id = handler.player.getUuid();
            runtime.occupied.remove(id);
            runtime.bypassOnly.remove(id);
            runtime.lastRefusal.remove(id);
            runtime.packetMoves.remove(id);
            runtime.teleports.remove(id);
        });
    }

    public static List<Binding> bindings(MinecraftServer server, String policyId) {
        return requireRuntime(server).catalog.bindings().values().stream()
                .filter(binding -> binding.policyId().equals(policyId)).toList();
    }

    public static Optional<Binding> binding(MinecraftServer server, String regionId) {
        return Optional.ofNullable(requireRuntime(server).catalog.bindings().get(regionId));
    }

    public static String editRefusal(MinecraftServer server, String policyId) {
        Runtime runtime = WORLDS.get(server);
        if (runtime == null || runtime.failed) return "Repair Region access data while the server is stopped.";
        Policy policy = POLICIES.get(policyId);
        if (policy == null) return "The Region access policy is unavailable.";
        return policy.editRefusal(server);
    }

    public static List<String> references(MinecraftServer server, String siteId) {
        return requireRuntime(server).catalog.bindings().values().stream()
                .filter(binding -> binding.regionId().equals(siteId) || binding.outsidePointId().equals(siteId))
                .map(binding -> "Region access: " + binding.regionId()).toList();
    }

    public static void enable(ServerCommandSource source, String regionId, String policyId) throws IOException {
        Runtime runtime = editor(source, policyId);
        if (runtime.catalog.bindings().containsKey(regionId)) throw new IllegalStateException("This Region already has an access policy.");
        var server = source.getServer();
        var sites = AuthoringRuntime.get(server).service().state().sites();
        if (!(sites.get(regionId) instanceof AuthoringSite.Region region)) throw new IllegalArgumentException("Choose a saved Region.");
        var next = new TreeMap<>(runtime.catalog.bindings());
        next.put(regionId, new Binding(regionId, region.revision(), policyId, "", 0));
        commit(server, runtime, next);
    }

    public static void disable(ServerCommandSource source, String regionId) throws IOException {
        var server = source.getServer();
        Binding binding = requiredBinding(server, regionId);
        Runtime runtime = editor(source, binding.policyId());
        var next = new TreeMap<>(runtime.catalog.bindings());
        next.remove(regionId);
        commit(server, runtime, next);
    }

    public static void setOutsidePoint(ServerCommandSource source, String regionId, String pointId) throws IOException {
        var server = source.getServer();
        Binding binding = requiredBinding(server, regionId);
        Runtime runtime = editor(source, binding.policyId());
        var sites = AuthoringRuntime.get(server).service().state().sites();
        if (!(sites.get(pointId) instanceof AuthoringSite.Location point)) throw new IllegalArgumentException("Choose a saved outside Point.");
        var next = new TreeMap<>(runtime.catalog.bindings());
        next.put(regionId, new Binding(regionId, binding.regionRevision(), binding.policyId(), point.id(), point.revision()));
        Catalog resolved = resolve(AuthoringRuntime.get(server).service().state(), next);
        requireSafePoint(server, resolved.guards().get(regionId), null);
        publish(server, runtime, resolved);
    }

    public static AuthoringSite.Location preflight(MinecraftServer server, String regionId) {
        Runtime runtime = requireRuntime(server);
        verifyCatalog(server, runtime);
        Guard guard = requiredGuard(runtime, regionId);
        if (activePolicy(server, runtime, guard) == null) throw new IllegalStateException("The Region access policy is unavailable.");
        return requireSafePoint(server, guard, null);
    }

    /** Called after the trusted policy repairs its own protected state. */
    public static void recoverPolicy(MinecraftServer server, String policyId) throws IOException {
        Runtime runtime = requireRuntime(server);
        if (!runtime.failedPolicies.contains(policyId)) return;
        runtime.store.verifyUnchanged();
        resolve(AuthoringRuntime.get(server).service().state(), runtime.catalog.bindings());
        var policy = POLICIES.get(policyId);
        if (policy == null || !policy.active(server)) throw new IllegalStateException("The Region access policy is unavailable.");
        runtime.failedPolicies.remove(policyId);
        if (runtime.failedPolicies.isEmpty()) RuntimeHolds.release(server, HOLD);
    }

    public static boolean contains(ServerPlayerEntity player, String regionId) {
        Guard guard = requiredGuard(requireRuntime(server(player)), regionId);
        return guard.region().dimension().equals(dimension(player.getEntityWorld())) && guard.volume().intersects(player.getBoundingBox());
    }

    /** Explicit durable-operation step. Revalidate immediately before moving an inside player. */
    public static void eject(ServerPlayerEntity player, String regionId) {
        var server = server(player);
        Runtime runtime = requireRuntime(server);
        verifyCatalog(server, runtime);
        Guard guard = requiredGuard(runtime, regionId);
        if (!contains(player, regionId)) return;
        eject(player, guard);
    }

    public static boolean canMove(Entity entity, Vec3d movement) {
        if (!(entity.getEntityWorld() instanceof ServerWorld world)) return true;
        Runtime runtime = WORLDS.get(world.getServer());
        if (runtime == null || !runtime.inUse() || !world.getServer().isOnThread()) return true;
        if (entity instanceof ServerPlayerEntity player && !canMove(player, runtime, movement)) return false;
        if (entity.hasPassengers()) {
            for (Entity passenger : entity.getPassengersDeep()) {
                if (passenger instanceof ServerPlayerEntity player && !canMove(player, runtime, movement)) return false;
            }
        }
        return true;
    }

    private static boolean canMove(ServerPlayerEntity player, Runtime runtime, Vec3d movement) {
        if (runtime.failed || !finite(movement)) return false;
        if (bypass(player)) return true;
        for (Guard guard : guards(runtime, player.getEntityWorld())) {
            if (RegionBoundary.crosses(guard.volume(), player.getBoundingBox(), movement) && !allows(player, runtime, guard))
                return false;
        }
        return true;
    }

    public static boolean canTeleport(ServerPlayerEntity player, ServerWorld world, Vec3d position) {
        Runtime runtime = WORLDS.get(world.getServer());
        if (runtime == null || !runtime.inUse() || !world.getServer().isOnThread()) return true;
        return !runtime.failed && finite(position) && (bypass(player) || denied(player, runtime, world, standingAt(player, position)) == null);
    }

    public static boolean canTeleportPassengers(Entity vehicle, ServerWorld world, Vec3d position) {
        Runtime runtime = WORLDS.get(world.getServer());
        if (runtime == null || !runtime.inUse() || !vehicle.hasPassengers()) return true;
        Vec3d change = position.subtract(vehicle.getEntityPos());
        for (Entity passenger : vehicle.getPassengersDeep()) {
            if (passenger instanceof ServerPlayerEntity player
                    && !canTeleport(player, world, player.getEntityPos().add(change))) return false;
        }
        return true;
    }

    public static boolean canMount(ServerPlayerEntity player, Entity vehicle) {
        if (!(vehicle.getEntityWorld() instanceof ServerWorld world)) return true;
        Runtime runtime = WORLDS.get(world.getServer());
        if (runtime == null || !runtime.inUse()) return true;
        Vec3d seat = vehicle.getPassengerRidingPos(player).subtract(player.getVehicleAttachmentPos(vehicle));
        return canTeleport(player, world, seat);
    }

    public static void beginTeleport(ServerPlayerEntity player) {
        Runtime runtime = WORLDS.get(server(player));
        if (runtime != null && runtime.inUse()) runtime.teleports.merge(player.getUuid(), 1, Integer::sum);
    }

    public static void finishTeleport(ServerPlayerEntity player, boolean applied) {
        Runtime runtime = WORLDS.get(server(player));
        if (runtime == null) return;
        Integer remaining = runtime.teleports.computeIfPresent(player.getUuid(), (id, depth) -> depth == 1 ? null : depth - 1);
        if (applied && remaining == null) moved(player);
    }

    public static void beginPacketMove(ServerPlayerEntity player) {
        Runtime runtime = WORLDS.get(server(player));
        if (runtime != null && runtime.inUse()) runtime.packetMoves.put(player.getUuid(), movement(player));
    }

    public static void finishPacketMove(ServerPlayerEntity player) {
        var server = server(player);
        if (!server.isOnThread()) return;
        Runtime runtime = WORLDS.get(server);
        if (runtime == null) return;
        var movement = runtime.packetMoves.remove(player.getUuid());
        if (movement == null) return;
        crossedBetweenPositions(player, movement);
        moved(player);
    }

    public static Movement captureMovement(ServerPlayerEntity player) {
        Runtime runtime = WORLDS.get(server(player));
        return runtime == null || !runtime.inUse() || runtime.packetMoves.containsKey(player.getUuid())
                || runtime.teleports.containsKey(player.getUuid()) ? null : movement(player);
    }

    private static Movement movement(ServerPlayerEntity player) {
        return new Movement(player.getEntityWorld(), player.getBoundingBox(), player.getEntityPos());
    }

    /** A successful fast move may pass through a thin Region even if both final positions are outside. */
    public static void crossedBetweenPositions(ServerPlayerEntity player, Movement start) {
        if (start == null || start.world() != player.getEntityWorld() || start.origin().equals(player.getEntityPos())) return;
        Runtime runtime = WORLDS.get(server(player));
        if (runtime == null || runtime.failed || !online(player) || bypass(player)) return;
        Vec3d change = player.getEntityPos().subtract(start.origin());
        Box end = player.getBoundingBox();
        for (Guard guard : guards(runtime, player.getEntityWorld())) {
            Box volume = guard.volume();
            if (!volume.intersects(start.body()) && !volume.intersects(end)
                    && RegionBoundary.crosses(volume, start.body(), change) && allows(player, runtime, guard)) {
                transition(player, runtime, guard.region().id(), true);
                transition(player, runtime, guard.region().id(), false);
            }
        }
    }

    /** Low-level commits used by physical movement, dismount, passenger seating and spectator camera tracking. */
    public static boolean canSetPosition(ServerPlayerEntity player, Vec3d position) {
        var server = server(player);
        Runtime runtime = WORLDS.get(server);
        if (runtime == null || !runtime.inUse() || !server.isOnThread() || !online(player)) return true;
        if (runtime.failed || !finite(position)) return false;
        if (bypass(player)) return true;
        Box body = player.getBoundingBox().offset(position.subtract(player.getEntityPos()));
        return denied(player, runtime, player.getEntityWorld(), body) == null;
    }

    /** Used before vanilla registers/sends a saved login or respawn position. */
    public static AuthoringSite.Location admissionExit(ServerPlayerEntity player, ServerWorld world, Vec3d position) {
        Runtime runtime = WORLDS.get(world.getServer());
        if (runtime == null || !runtime.inUse()) return null;
        if (runtime.failed) throw new IllegalStateException("Region access data is unavailable.");
        if (bypass(player)) return null;
        Guard guard = denied(player, runtime, world, standingAt(player, position));
        return guard == null ? null : requireSafePoint(world.getServer(), guard, player);
    }

    public static TeleportTarget guardRespawn(ServerPlayerEntity player, TeleportTarget original) {
        var point = admissionExit(player, original.world(), original.position());
        return point == null ? original : original.withPosition(position(point)).withRotation(point.yaw(), point.pitch());
    }

    public static void refused(ServerPlayerEntity player) {
        Runtime runtime = WORLDS.get(server(player));
        if (runtime == null || player.networkHandler == null || !player.networkHandler.isConnectionOpen()) return;
        int tick = server(player).getTicks();
        Integer last = runtime.lastRefusal.get(player.getUuid());
        if (last == null || tick - last >= 30) {
            runtime.lastRefusal.put(player.getUuid(), tick);
            player.sendMessage(Text.literal("You cannot enter this guarded Region."), true);
        }
    }

    /** Observe only an actual committed position, including teleports and the final vanilla packet result. */
    public static void moved(ServerPlayerEntity player) {
        var server = server(player);
        Runtime runtime = WORLDS.get(server);
        if (runtime == null || !runtime.inUse() || runtime.failed || !server.isOnThread() || !online(player)
                || runtime.packetMoves.containsKey(player.getUuid()) || runtime.teleports.containsKey(player.getUuid()) || !player.isAlive()) return;
        UUID id = player.getUuid();
        Set<String> previous = runtime.occupied.getOrDefault(id, Set.of());
        Set<String> inside = new HashSet<>();
        for (Guard guard : guards(runtime, player.getEntityWorld()))
            if (guard.volume().intersects(player.getBoundingBox())) inside.add(guard.region().id());
        if (bypass(player)) {
            inside.removeAll(previous);
            track(runtime.bypassOnly, id, inside);
            return;
        }
        Set<String> bypassOnly = new HashSet<>(runtime.bypassOnly.getOrDefault(id, Set.of()));
        bypassOnly.retainAll(inside);
        track(runtime.bypassOnly, id, bypassOnly);
        inside.removeAll(bypassOnly);
        var iterator = inside.iterator();
        while (iterator.hasNext()) {
            String region = iterator.next();
            if (!allows(player, runtime, runtime.catalog.guards().get(region))) iterator.remove();
        }
        track(runtime.occupied, id, inside);
        for (String region : previous) if (!inside.contains(region)) transition(player, runtime, region, false);
        for (String region : inside) if (!previous.contains(region)) transition(player, runtime, region, true);
    }

    private static void track(Map<UUID, Set<String>> state, UUID id, Set<String> value) {
        if (value.isEmpty()) state.remove(id);
        else state.put(id, Set.copyOf(value));
    }

    private static void transition(ServerPlayerEntity player, Runtime runtime, String id, boolean enter) {
        Guard guard = runtime.catalog.guards().get(id);
        if (guard == null) return;
        Policy policy = activePolicy(server(player), runtime, guard);
        if (policy == null) return;
        try {
            if (enter) policy.entered(player, guard.region());
            else policy.left(player, guard.region());
        } catch (RuntimeException failure) {
            policyFailed(server(player), runtime, guard.binding().policyId(), failure);
        }
    }

    private static void start(MinecraftServer server) {
        var store = new RegionAccessStore(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle"));
        var runtime = new Runtime(store);
        WORLDS.put(server, runtime);
        try {
            var bindings = store.load();
            if (!bindings.isEmpty()) runtime.catalog = resolve(AuthoringRuntime.get(server).service().state(), bindings);
        } catch (IOException | RuntimeException failure) {
            failCatalog(server, runtime, failure);
        }
        // A missing or corrupt access catalogue cannot be worked around by discarding its authoring sites.
        try {
            AuthoringRuntime.get(server).service().addMutationGuard((existing, proposed, operation) -> {
                if (runtime.failed) throw new IllegalStateException("Repair Region access data before changing authoring sites.");
                if (existing == null) return;
                for (Binding binding : runtime.catalog.bindings().values()) {
                    if (binding.regionId().equals(existing.id()) || binding.outsidePointId().equals(existing.id()))
                        throw new IllegalStateException("Change the Region access assignment for '" + binding.regionId() + "' before editing this site.");
                }
            });
        } catch (IllegalStateException ignored) {
            // Authoring already reports unavailable and no site can be mutated through it.
        }
    }

    private static Catalog resolve(AuthoringRegistry registry, Map<String, Binding> bindings) {
        var sortedBindings = new TreeMap<>(bindings);
        Map<String, Guard> guards = new LinkedHashMap<>();
        Map<String, List<Guard>> dimensions = new HashMap<>();
        for (Binding binding : sortedBindings.values()) {
            if (!POLICIES.containsKey(binding.policyId()))
                throw new IllegalStateException("Unknown Region access policy: " + binding.policyId());
            if (!(registry.sites().get(binding.regionId()) instanceof AuthoringSite.Region region)
                    || region.revision() != binding.regionRevision())
                throw new IllegalStateException("Guarded Region is missing or changed: " + binding.regionId());
            AuthoringSite.Location outside = null;
            if (!binding.outsidePointId().isEmpty()) {
                if (!(registry.sites().get(binding.outsidePointId()) instanceof AuthoringSite.Location point)
                        || point.revision() != binding.outsidePointRevision() || !point.dimension().equals(region.dimension()))
                    throw new IllegalStateException("Outside Point is missing, changed, or in another dimension: " + binding.outsidePointId());
                outside = point;
            }
            Guard guard = new Guard(binding, region, RegionBoundary.volume(region), outside);
            guards.put(region.id(), guard);
            dimensions.computeIfAbsent(region.dimension(), ignored -> new ArrayList<>()).add(guard);
        }
        for (Guard guard : guards.values()) {
            if (guard.point() == null) continue;
            Box body = RegionBoundary.standingAt(guard.point());
            if (dimensions.get(guard.region().dimension()).stream().anyMatch(other -> other.volume().intersects(body)))
                throw new IllegalStateException("Outside Point overlaps a guarded Region: " + guard.point().id());
        }
        dimensions.replaceAll((dimension, entries) -> List.copyOf(entries));
        return new Catalog(java.util.Collections.unmodifiableMap(sortedBindings), Map.copyOf(guards), Map.copyOf(dimensions));
    }

    private static Runtime editor(ServerCommandSource source, String policyId) {
        var server = source.getServer();
        Runtime runtime = requireRuntime(server);
        if (!source.hasPermissionLevel(2) || source.getPlayer() == null || !AuthoringRuntime.isAuthor(source.getPlayer())
                || !AuthoringRuntime.editable(server))
            throw new IllegalStateException("Region access edits require non-active operator Editing.");
        if (RuntimeHolds.reason(server) != null) throw new IllegalStateException("Finish pending operations before changing guarded Regions.");
        verifyCatalog(server, runtime);
        String refusal = editRefusal(server, policyId);
        if (!refusal.isBlank()) throw new IllegalStateException(refusal);
        return runtime;
    }

    private static void commit(MinecraftServer server, Runtime runtime, Map<String, Binding> next) throws IOException {
        publish(server, runtime, resolve(AuthoringRuntime.get(server).service().state(), next));
    }

    private static void publish(MinecraftServer server, Runtime runtime, Catalog next) throws IOException {
        try { runtime.store.save(next.bindings()); }
        catch (IOException | RuntimeException failure) {
            failCatalog(server, runtime, failure);
            throw failure;
        }
        runtime.catalog = next;
        if (next.bindings().isEmpty()) {
            runtime.occupied.clear();
            runtime.bypassOnly.clear();
            runtime.lastRefusal.clear();
        }
    }

    private static void verifyCatalog(MinecraftServer server, Runtime runtime) {
        try { runtime.store.verifyUnchanged(); }
        catch (IOException | RuntimeException failure) {
            failCatalog(server, runtime, failure);
            throw new IllegalStateException("Region access data changed. Repair it while the server is stopped.", failure);
        }
    }

    private static void failCatalog(MinecraftServer server, Runtime runtime, Exception failure) {
        runtime.failed = true;
        RuntimeHolds.hold(server, HOLD, "Region access data needs repair while the server is stopped.");
        ChronicleCore.LOGGER.error("Region access could not be verified or saved. Inspect the world files while stopped.", failure);
    }

    private static void tick(MinecraftServer server) {
        Runtime runtime = WORLDS.get(server);
        if (runtime == null) return;
        int tick = server.getTicks();
        if (!runtime.failed && tick % 20 == 0 && tick != runtime.lastVerifiedTick) {
            runtime.lastVerifiedTick = tick;
            try { verifyCatalog(server, runtime); }
            catch (IllegalStateException failure) { /* Reconcile connected players against the failed catalogue below. */ }
        }
        if (!runtime.inUse()) return;
        for (var player : List.copyOf(server.getPlayerManager().getPlayerList())) reconcile(player);
    }

    private static void reconcile(ServerPlayerEntity player) {
        Runtime runtime = WORLDS.get(server(player));
        if (runtime == null || !runtime.inUse() || !player.isAlive() || player.networkHandler == null || !player.networkHandler.isConnectionOpen()) return;
        if (runtime.failed) {
            player.networkHandler.disconnect(Text.literal("Region access data needs repair before you can enter this world."));
            return;
        }
        if (!bypass(player)) {
            Entity camera = player.getCameraEntity();
            if (camera != player && camera.getEntityWorld() instanceof ServerWorld world
                    && !canTeleport(player, world, camera.getEntityPos())) player.setCameraEntity(player);
            Guard guard = denied(player, runtime, player.getEntityWorld(), player.getBoundingBox());
            if (guard != null) {
                try { eject(player, guard); }
                catch (IllegalStateException failure) {
                    ChronicleCore.LOGGER.warn("Guarded Region '{}' could not safely relocate an occupant: {}", guard.region().id(), failure.getMessage());
                    player.networkHandler.disconnect(Text.literal("The guarded Region has no safe, loaded outside Point. Ask an operator to check its setup."));
                    return;
                }
            }
        }
        moved(player);
    }

    private static boolean bypass(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2) && AuthoringRuntime.isAuthor(player) || SessionBuilding.enabled(server(player));
    }

    private static boolean allows(ServerPlayerEntity player, Runtime runtime, Guard guard) {
        if (guard.point() == null) return false;
        MinecraftServer server = server(player);
        Policy policy = activePolicy(server, runtime, guard);
        if (policy == null) return false;
        try { return policy.allows(player, guard.region()); }
        catch (RuntimeException failure) {
            policyFailed(server, runtime, guard.binding().policyId(), failure);
            return false;
        }
    }

    private static Policy activePolicy(MinecraftServer server, Runtime runtime, Guard guard) {
        String id = guard.binding().policyId();
        var policy = POLICIES.get(id);
        if (policy == null || runtime.failedPolicies.contains(id)) return null;
        try { return policy.active(server) ? policy : null; }
        catch (RuntimeException failure) {
            policyFailed(server, runtime, id, failure);
            return null;
        }
    }

    private static void policyFailed(MinecraftServer server, Runtime runtime, String id, RuntimeException failure) {
        if (runtime.failedPolicies.add(id)) {
            RuntimeHolds.hold(server, HOLD, "A Region access policy needs repair before play can resume.");
            ChronicleCore.LOGGER.error("Guarded Regions using policy {} now refuse entry", id, failure);
        }
    }

    private static Guard denied(ServerPlayerEntity player, Runtime runtime, ServerWorld world, Box body) {
        for (Guard guard : guards(runtime, world))
            if (guard.volume().intersects(body) && !allows(player, runtime, guard)) return guard;
        return null;
    }

    private static List<Guard> guards(Runtime runtime, ServerWorld world) {
        return runtime.catalog.dimensions().getOrDefault(dimension(world), List.of());
    }

    private static Runtime requireRuntime(MinecraftServer server) {
        if (!server.isOnThread()) throw new IllegalStateException("Region access requires the server thread.");
        Runtime runtime = WORLDS.get(server);
        if (runtime == null || runtime.failed) throw new IllegalStateException("Region access data is unavailable.");
        return runtime;
    }

    private static Binding requiredBinding(MinecraftServer server, String regionId) {
        return binding(server, regionId).orElseThrow(() -> new IllegalStateException("Choose a Region with access enabled."));
    }

    private static Guard requiredGuard(Runtime runtime, String regionId) {
        Guard guard = runtime.catalog.guards().get(regionId);
        if (guard == null) throw new IllegalStateException("Choose a Region with access enabled.");
        return guard;
    }

    private static void eject(ServerPlayerEntity player, Guard guard) {
        var point = requireSafePoint(server(player), guard, player);
        var world = player.getEntityWorld();
        player.closeHandledScreen();
        if (player.isSleeping()) player.wakeUp(true, true);
        player.dismountVehicle();
        player.setCameraEntity(player);
        if (!player.teleport(world, point.x(), point.y(), point.z(), Set.of(), point.yaw(), point.pitch(), false)
                || !player.getEntityPos().equals(position(point)))
            throw new IllegalStateException("Minecraft rejected the safe exit movement.");
    }

    private static AuthoringSite.Location requireSafePoint(MinecraftServer server, Guard guard, ServerPlayerEntity player) {
        var point = guard.point();
        if (point == null) throw new IllegalStateException("Choose an outside Point for the guarded Region.");
        var world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(point.dimension())));
        if (world == null) throw new IllegalStateException("The outside Point's dimension is unavailable.");
        var body = player == null ? RegionBoundary.standingAt(point) : standingAt(player, position(point));
        for (Guard other : guards(requireRuntime(server), world)) {
            if (other.volume().intersects(body)) throw new IllegalStateException("The outside Point overlaps a guarded Region.");
        }
        // Vanilla collision checks also inspect adjacent cells for collision shapes extending over block borders.
        Box margin = body.expand(1.0, 0.0, 1.0);
        for (int x = ((int) Math.floor(margin.minX)) >> 4; x <= ((int) Math.floor(margin.maxX)) >> 4; x++) {
            for (int z = ((int) Math.floor(margin.minZ)) >> 4; z <= ((int) Math.floor(margin.maxZ)) >> 4; z++) {
                if (world.getChunkManager().getWorldChunk(x, z) == null)
                    throw new IllegalStateException("The outside Point or an adjacent collision chunk is unloaded.");
            }
        }
        var verdict = SpawnDestinationValidator.validate(new SpawnAuthoring.MinecraftWorld(server), point);
        if (!verdict.safe()) throw new IllegalStateException("The outside Point is unsafe: " + verdict.reason() + ".");
        if (!world.getWorldBorder().contains(body) || !world.isSpaceEmpty(player, body)
                || !world.isInBuildLimit(BlockPos.ofFloored(body.minX, body.minY - 1, body.minZ))
                || !world.isInBuildLimit(BlockPos.ofFloored(body.maxX, body.maxY, body.maxZ)))
            throw new IllegalStateException("The outside Point does not have safe standing space.");
        for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(body.minX, body.minY - 1, body.minZ),
                BlockPos.ofFloored(body.maxX, body.maxY, body.maxZ))) {
            var state = world.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.SWEET_BERRY_BUSH)
                    || state.isOf(Blocks.WITHER_ROSE) || state.isOf(Blocks.POWDER_SNOW) || state.isOf(Blocks.POINTED_DRIPSTONE)
                    || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.MAGMA_BLOCK)
                    || state.isOf(Blocks.NETHER_PORTAL) || state.isOf(Blocks.END_PORTAL) || state.isOf(Blocks.END_GATEWAY)
                    || state.getBlock() instanceof CampfireBlock)
                throw new IllegalStateException("The outside Point contains a hazardous block.");
        }
        return point;
    }

    private static Box standingAt(ServerPlayerEntity player, Vec3d position) {
        return player.getDimensions(EntityPose.STANDING).getBoxAt(position);
    }

    private static MinecraftServer server(ServerPlayerEntity player) { return player.getEntityWorld().getServer(); }
    private static String dimension(ServerWorld world) { return world.getRegistryKey().getValue().toString(); }
    private static Vec3d position(AuthoringSite.Location point) { return new Vec3d(point.x(), point.y(), point.z()); }
    private static boolean online(ServerPlayerEntity player) { return server(player).getPlayerManager().getPlayer(player.getUuid()) == player; }
    public static boolean finite(Vec3d position) { return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z); }
}
