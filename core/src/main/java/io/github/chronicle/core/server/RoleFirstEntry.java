package io.github.chronicle.core.server;

import com.google.gson.Gson;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.content.InventorySnapshot;
import io.github.chronicle.core.entry.PlayerPosition;
import io.github.chronicle.core.entry.RoleBookGrant;
import io.github.chronicle.core.entry.RoleEntryOperation;
import io.github.chronicle.core.entry.RoleEntryRecovery;
import io.github.chronicle.core.entry.RoleEntryService;
import io.github.chronicle.core.entry.RoleEntryStore;
import io.github.chronicle.core.roles.PlayerRole;
import io.github.chronicle.core.spawn.AutomaticSpawnState;
import io.github.chronicle.core.spawn.SpawnBinding;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Coordinates role movement, supplies and private books as one conservative first-entry operation. */
public final class RoleFirstEntry {
    private static final Map<MinecraftServer, Runtime> WORLDS = new IdentityHashMap<>();
    private static final String HOLD_ROOT = "chronicle_core:role_first_entry:";
    private static final String MEMBERSHIP_HOLD=HOLD_ROOT+"membership";
    private static final Gson JSON = new Gson();
    private static boolean registered;

    private RoleFirstEntry() { }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(literal("chronicle")
                .then(literal("roles").then(literal("entry").then(literal("recover")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(argument("player", EntityArgumentType.player()).executes(context -> recoverCommand(
                                context.getSource(), EntityArgumentType.getPlayer(context, "player")))))))));
        ServerLifecycleEvents.SERVER_STARTED.register(RoleFirstEntry::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WORLDS.remove(server));
        ServerTickEvents.END_SERVER_TICK.register(RoleFirstEntry::tick);
    }

    /** Existing arrival handling must defer while a role start is unclaimed or has journal evidence. */
    public static boolean managesStart(MinecraftServer server, UUID player) {
        if (server == null || player == null || !server.isOnThread()) return false;
        Runtime runtime = WORLDS.get(server);
        if (runtime != null) {
            RoleEntryOperation operation = runtime.service.state().operations().get(player);
            if (operation != null && operation.pending()) return true;
        }
        if (!PlayerRoles.ready(server)) return false;
        var assignments = PlayerRoles.assignments(server);
        return assignments.assigned().containsKey(player) && !assignments.started().contains(player);
    }

    private static void start(MinecraftServer server) {
        var path = server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/role-first-entry.json");
        try {
            Runtime runtime = new Runtime(new RoleEntryService(new RoleEntryStore(path)));
            WORLDS.put(server, runtime);
            for (RoleEntryOperation operation : runtime.service.state().operations().values()) {
                if (operation.pending()) hold(server, operation);
            }
            ChronicleCore.LOGGER.info("Role first-entry journal ready: {}", path);
        } catch (IOException | RuntimeException failure) {
            ChronicleCore.LOGGER.error("Role first-entry processing disabled; existing evidence preserved at {}", path, failure);
            RuntimeHolds.hold(server, HOLD_ROOT + "journal", "Role first-entry evidence is unavailable. Inspect the journal before resuming play.");
        }
    }

    private static void tick(MinecraftServer server) {
        Runtime runtime = WORLDS.get(server);
        if (runtime == null) return;
        recover(server, runtime);
        if (!reconcileCompleted(server, runtime)) return;
        if(active(server)&&PlayerRoles.ready(server)&&!RuntimeHolds.heldByOther(server,MEMBERSHIP_HOLD)){
            try{
                for(var participant:PlayRuntime.eligiblePlayers(server))
                    if(!PlayerRoles.assignments(server).assigned().containsKey(participant.getUuid())&&!PlayerRoles.assignments(server).started().contains(participant.getUuid()))PlayerRoles.markStarted(server,participant.getUuid());
                if(runtime.membershipBlocked){RuntimeHolds.release(server,MEMBERSHIP_HOLD);runtime.membershipBlocked=false;}
            }catch(IOException|RuntimeException failure){runtime.membershipBlocked=true;RuntimeHolds.hold(server,MEMBERSHIP_HOLD,"Cannot save first-participation history. Repair the role ledger before continuing.");return;}
        }
        if (!active(server) || RuntimeHolds.reason(server) != null || !PlayerRoles.ready(server)) return;
        for (ServerPlayerEntity player : PlayRuntime.eligiblePlayers(server)) {
            UUID id = player.getUuid();
            RoleEntryOperation prior = runtime.service.state().operations().get(id);
            if (!PlayerRoles.assignments(server).assigned().containsKey(id)
                    || PlayerRoles.assignments(server).started().contains(id)
                    || prior != null && prior.stage() != RoleEntryOperation.Stage.ABORTED) continue;
            try {
                Plan plan = plan(server, player);
                if (plan == null) continue;
                apply(server, runtime, player, plan);
                return; // At most one new durable entry transaction per server tick.
            } catch (Deferred exception) {
                notice(runtime, player, exception.getMessage());
                continue;
            } catch (IOException | RuntimeException exception) {
                ChronicleCore.LOGGER.error("Role first entry failed before or during its durable operation: player={}", id, exception);
                RoleEntryOperation operation = runtime.service.state().operations().get(id);
                if (operation != null && operation.pending()) hold(server, operation);
                else notice(runtime, player, "Chronicle could not prepare your role start. Ask the GM to inspect the server log.");
                return;
            }
        }
    }

    private static void recover(MinecraftServer server, Runtime runtime) {
        for (RoleEntryOperation operation : List.copyOf(runtime.service.state().operations().values())) {
            if (!operation.pending() || runtime.inspected.contains(operation.operationId())) continue;
            hold(server, operation);
            if (operation.stage() == RoleEntryOperation.Stage.CONFLICT) {
                runtime.inspected.add(operation.operationId());
                continue;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(operation.playerId());
            if (player == null) continue;
            try {
                RoleEntryRecovery.Decision decision = RoleEntryRecovery.inspect(operation, snapshot(server, player), position(player));
                switch (decision) {
                    case ABORT_BEFORE_EFFECTS -> {
                        runtime.service.abortPrepared(operation.playerId(), operation.operationId());
                        RuntimeHolds.release(server, owner(operation.playerId()));
                    }
                    case FINISH_EXACT_POST_STATE -> {
                        // A live-process retry may be observing mutations whose first player-data save failed.
                        // Re-save and recheck exact evidence before any secondary ledger can become terminal.
                        server.getPlayerManager().saveAllPlayerData();
                        if (RoleEntryRecovery.inspect(operation, snapshot(server, player), position(player))
                                != RoleEntryRecovery.Decision.FINISH_EXACT_POST_STATE) {
                            throw new IllegalStateException("player state changed while role-entry recovery was saving");
                        }
                        finishLedgers(server, operation);
                        runtime.service.complete(operation.playerId(), operation.operationId());
                        RuntimeHolds.release(server, owner(operation.playerId()));
                    }
                    case CONFLICT -> {
                        // Retain PREPARED versus APPLYING: that distinction is essential evidence for a later
                        // exact-state GM recovery and must not be collapsed into a generic conflict marker.
                        runtime.inspected.add(operation.operationId());
                        RuntimeHolds.hold(server, owner(operation.playerId()),
                                "A role first-entry outcome is ambiguous. Preserve the player inventory and position; use the evidence-only recovery command after inspection.");
                        ChronicleCore.LOGGER.error("Role first-entry evidence is ambiguous and remains held: operation={} player={} stage={}",
                                operation.operationId(), operation.playerId(), operation.stage());
                    }
                }
            } catch (IOException | RuntimeException failure) {
                runtime.inspected.add(operation.operationId());
                ChronicleCore.LOGGER.error("Role first-entry recovery remains held: operation={} player={}", operation.operationId(), operation.playerId(), failure);
            }
        }
    }

    private static int recoverCommand(net.minecraft.server.command.ServerCommandSource source, ServerPlayerEntity player) {
        Runtime runtime = WORLDS.get(source.getServer());
        if (runtime == null) { source.sendError(Text.literal("Role first-entry journal is unavailable; inspect the server log.")); return 0; }
        RoleEntryOperation operation = runtime.service.state().operations().get(player.getUuid());
        if (operation == null || operation.stage() != RoleEntryOperation.Stage.PREPARED && operation.stage() != RoleEntryOperation.Stage.APPLYING) {
            source.sendError(Text.literal("That player has no recoverable role first-entry operation.")); return 0;
        }
        runtime.inspected.remove(operation.operationId());
        recover(source.getServer(), runtime);
        RoleEntryOperation result = runtime.service.state().operations().get(player.getUuid());
        if (result.stage() == RoleEntryOperation.Stage.COMPLETE || result.stage() == RoleEntryOperation.Stage.ABORTED) {
            source.sendFeedback(() -> Text.literal("Role first-entry evidence recovered as " + result.stage().name().toLowerCase(java.util.Locale.ROOT) + "."), false);
            return 1;
        }
        source.sendError(Text.literal("Recovery refused: restore the exact "
                + (operation.stage() == RoleEntryOperation.Stage.PREPARED ? "before" : "post-effect")
                + " inventory and position evidence, then retry. Nothing was changed."));
        return 0;
    }

    /** A completed role journal remains the once-only authority even if its secondary start marker was lost. */
    private static boolean reconcileCompleted(MinecraftServer server, Runtime runtime) {
        if (!PlayerRoles.ready(server)) return false;
        for (RoleEntryOperation operation : runtime.service.state().operations().values()) {
            if (operation.stage() != RoleEntryOperation.Stage.COMPLETE
                    || !PlayerRoles.assignments(server).assigned().containsKey(operation.playerId())
                    || PlayerRoles.assignments(server).started().contains(operation.playerId())) continue;
            try {
                PlayerRoles.markStarted(server, operation.playerId());
                RuntimeHolds.release(server, owner(operation.playerId()));
            } catch (IOException | RuntimeException failure) {
                RuntimeHolds.hold(server, owner(operation.playerId()), "A completed role start could not restore its once-only marker. Inspect the role ledgers.");
                ChronicleCore.LOGGER.error("Completed role-entry receipt could not restore the start marker: player={} operation={}", operation.playerId(), operation.operationId(), failure);
                return false;
            }
        }
        return true;
    }

    private static Plan plan(MinecraftServer server, ServerPlayerEntity player) {
        PlayerRole role = PlayerRoles.roleFor(server, player.getUuid());
        if (role == null) throw new Deferred("Your assigned role is unavailable. Ask the GM to review Player Roles.");

        var automatic = AutomaticPlayerSpawns.available(server);
        if (automatic == null) throw new Deferred("Starting-position history is unavailable. Ask the GM to inspect automatic spawns.");
        var existingArrival=automatic.arrivals().get(player.getUuid());
        if(existingArrival!=null&&existingArrival.status()==AutomaticSpawnState.Status.CLAIMED)throw new Deferred("Your earlier starting-position claim is unresolved. Ask the GM to inspect or reset that receipt before starting your role.");
        boolean hasArrivalReceipt = existingArrival!=null;
        SpawnBinding movement = null;
        AuthoringSite.Location destination = null;
        if (!hasArrivalReceipt && !PlayerRoles.assignments(server).startingDisabled().contains(player.getUuid())) {
            AutomaticSpawnState.Policy policy = automatic.policies().get(player.getUuid());
            if (policy != null && policy.arrival() != null) {
                movement = policy.arrival(); destination = SpawnAuthoring.safePolicyDestination(server, movement);
            } else if (role.starting() != null) {
                movement = role.starting(); destination = SpawnAuthoring.safeRoleDestination(server, movement);
            }
        }

        InventorySnapshot before = snapshot(server, player);
        List<ItemStack> additions = new ArrayList<>();
        for (var item : role.items()) additions.add(decode(server, item.stackJson()));
        List<RoleBookGrant> bookGrants = new ArrayList<>();
        for (String bookId : role.books()) {
            var book = BookRuntime.books(server).get(bookId);
            if (book == null) throw new Deferred("A role book is unavailable: " + bookId + ". Ask the GM to review Books & Lore.");
            var delivery = ChestAuthoring.service(server).state().bookDelivery().deliveries().get(bookId);
            if (delivery != null && !delivery.pendingOperationIds().isEmpty()) {
                throw new Deferred("A role book has an unfinished chest delivery: " + bookId + ". Resolve that operation first.");
            }
            String token = ChestAuthoring.service(server).deliveryToken(bookId);
            additions.add(decode(server, BookDeliveryRuntime.stackJson(server, book)));
            bookGrants.add(new RoleBookGrant(bookId, token));
        }
        InventorySnapshot after = addWithoutMerging(server, before, additions);
        PlayerPosition beforePosition = position(player);
        PlayerPosition afterPosition = destination == null ? beforePosition : position(destination);
        String roleContent = JSON.toJson(role);
        RoleEntryOperation operation = new RoleEntryOperation(UUID.randomUUID(), player.getUuid(), role.id(), fingerprint(roleContent), roleContent,
                before, after, beforePosition, afterPosition, movement == null ? "" : movement.purpose(), bookGrants,
                RoleEntryOperation.Stage.PREPARED, "Preflight complete; no effects claimed.", Instant.now().toString());
        return new Plan(role, movement, destination, operation);
    }

    private static void apply(MinecraftServer server, Runtime runtime, ServerPlayerEntity player, Plan plan) throws IOException {
        RoleEntryOperation operation = plan.operation;
        runtime.service.prepare(operation); // A failed save publishes no entitlement claim and mutates nothing.
        hold(server, operation);
        try {
            recheck(server, player, plan);
            runtime.service.begin(player.getUuid(), operation.operationId()); // Claims precede every external side effect.
            if (plan.movement != null && !AutomaticPlayerSpawns.service(server).claim(player.getUuid(), plan.movement.purpose())) {
                throw new IllegalStateException("starting-position history changed after role preflight");
            }
            for (RoleBookGrant book : operation.books()) ChestAuthoring.service(server).claimOperatorIssuance(book.bookId());
            applyInventory(server, player, operation.afterInventory());
            if (plan.destination != null) SpawnAuthoring.moveToPolicy(player, plan.destination);
            server.getPlayerManager().saveAllPlayerData();
            finishLedgers(server, operation);
            runtime.service.complete(player.getUuid(), operation.operationId());
            RuntimeHolds.release(server, owner(player.getUuid()));
            player.sendMessage(Text.literal("Your Chronicle role is ready."), false);
            ChronicleCore.LOGGER.info("Role first entry completed: player={} role={} operation={}", player.getUuid(), operation.roleId(), operation.operationId());
        } catch (IOException | RuntimeException failure) {
            // PREPARED is safe-before-effects; APPLYING is intentionally uncertain until exact evidence is inspected after restart.
            hold(server, runtime.service.state().operations().get(player.getUuid()));
            throw failure;
        }
    }

    private static void recheck(MinecraftServer server, ServerPlayerEntity player, Plan plan) {
        if (!active(server) || RuntimeHolds.reason(server) == null || !eligible(server, player)) {
            throw new IllegalStateException("role-entry eligibility changed after preflight");
        }
        PlayerRole current = PlayerRoles.roleFor(server, player.getUuid());
        if (current == null || !JSON.toJson(current).equals(plan.operation.roleContent())
                || PlayerRoles.assignments(server).started().contains(player.getUuid())) {
            throw new IllegalStateException("role assignment changed after preflight");
        }
        if (!plan.operation.beforeInventory().equals(snapshot(server, player))
                || !plan.operation.beforePosition().exactly(position(player))) {
            throw new IllegalStateException("player inventory or position changed after preflight");
        }
    }

    private static void finishLedgers(MinecraftServer server, RoleEntryOperation operation) throws IOException {
        if (!operation.movementPurpose().isEmpty()) {
            var arrival = AutomaticPlayerSpawns.service(server).state().arrivals().get(operation.playerId());
            if (arrival == null) throw new IllegalStateException("role-entry movement claim is missing");
            if (!arrival.purpose().equals(operation.movementPurpose())) throw new IllegalStateException("role-entry movement purpose changed");
            if (arrival.status() == AutomaticSpawnState.Status.CLAIMED) {
                AutomaticPlayerSpawns.service(server).finish(operation.playerId(), AutomaticSpawnState.Status.MOVED,
                        "Coordinated role first entry moved the player; no automatic repeat.");
            } else if (arrival.status() != AutomaticSpawnState.Status.MOVED) {
                throw new IllegalStateException("role-entry movement has a conflicting terminal receipt");
            }
        }
        for (RoleBookGrant book : operation.books()) ChestAuthoring.service(server).claimOperatorIssuance(book.bookId());
        PlayerRoles.markStarted(server, operation.playerId());
        for (RoleBookGrant book : operation.books()) {
            ChestAuthoring.service(server).recordDiscovery(book.bookId(), book.deliveryToken(), operation.playerId().toString(), operation.createdAt());
        }
    }

    private static boolean active(MinecraftServer server) {
        var campaign = ChronicleRuntime.available(server);
        return campaign != null && campaign.state().phase() == SessionPhase.ACTIVE;
    }

    private static boolean eligible(MinecraftServer server, ServerPlayerEntity player) {
        var play = PlayRuntime.available(server);
        return play != null && play.participants().contains(player.getUuid()) && player.isAlive()
                && !player.isSpectator() && !AuthoringRuntime.isAuthor(player);
    }

    private static InventorySnapshot snapshot(MinecraftServer server, ServerPlayerEntity player) {
        SimpleInventory inventory = new SimpleInventory(RoleEntryOperation.INVENTORY_SLOTS);
        for (int slot = 0; slot < RoleEntryOperation.INVENTORY_SLOTS; slot++) inventory.setStack(slot, player.getInventory().getStack(slot).copy());
        return ChestAuthoring.snapshot(server, inventory);
    }

    private static InventorySnapshot addWithoutMerging(MinecraftServer server, InventorySnapshot before, List<ItemStack> additions) {
        List<ItemStack> stacks = new ArrayList<>(ChestAuthoring.decode(server, before));
        List<Integer> empty = new ArrayList<>();
        for (int slot = 0; slot < stacks.size(); slot++) if (stacks.get(slot).isEmpty()) empty.add(slot);
        if (additions.size() > empty.size()) throw new Deferred("Make room for " + additions.size() + " role supplies; existing inventory will not be overwritten.");
        for (int index = 0; index < additions.size(); index++) {
            ItemStack stack = additions.get(index);
            if (stack.isEmpty() || stack.getCount() > stack.getMaxCount()) throw new Deferred("A configured role supply is invalid. Ask the GM to review Player Roles.");
            stacks.set(empty.get(index), stack.copy());
        }
        SimpleInventory result = new SimpleInventory(RoleEntryOperation.INVENTORY_SLOTS);
        for (int slot = 0; slot < stacks.size(); slot++) result.setStack(slot, stacks.get(slot));
        return ChestAuthoring.snapshot(server, result);
    }

    private static void applyInventory(MinecraftServer server, ServerPlayerEntity player, InventorySnapshot snapshot) {
        List<ItemStack> stacks = ChestAuthoring.decode(server, snapshot);
        for (int slot = 0; slot < RoleEntryOperation.INVENTORY_SLOTS; slot++) player.getInventory().setStack(slot, stacks.get(slot));
        player.getInventory().markDirty(); player.currentScreenHandler.sendContentUpdates();
    }

    private static ItemStack decode(MinecraftServer server, String stack) {
        var decoded=ChestAuthoring.decode(server, new InventorySnapshot(1, List.of(stack))).getFirst();
        EnchantmentCapRuntime.normalize(server,decoded);return decoded;
    }

    private static PlayerPosition position(ServerPlayerEntity player) {
        return new PlayerPosition(player.getEntityWorld().getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
    }

    private static PlayerPosition position(AuthoringSite.Location location) {
        return new PlayerPosition(location.dimension(), location.x(), location.y(), location.z(), location.yaw(), location.pitch());
    }

    private static String fingerprint(String roleContent) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(roleContent.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private static String owner(UUID player) { return HOLD_ROOT + player; }
    private static void hold(MinecraftServer server, RoleEntryOperation operation) {
        if (operation != null) RuntimeHolds.hold(server, owner(operation.playerId()),
                operation.stage() == RoleEntryOperation.Stage.CONFLICT
                        ? "A role first-entry outcome is ambiguous. Preserve the player inventory and inspect the journal."
                        : "A role first entry is being recovered. Preserve the player inventory and position.");
    }

    private static void notice(Runtime runtime, ServerPlayerEntity player, String message) {
        int now = player.getEntityWorld().getServer().getTicks();
        if (runtime.noticeAt.getOrDefault(player.getUuid(), Integer.MIN_VALUE) > now) return;
        runtime.noticeAt.put(player.getUuid(), now + 100);
        player.sendMessage(Text.literal(message), false);
    }

    private record Plan(PlayerRole role, SpawnBinding movement, AuthoringSite.Location destination, RoleEntryOperation operation) { }
    private static final class Runtime {
        boolean membershipBlocked;
        final RoleEntryService service;
        final Map<UUID, Integer> noticeAt = new java.util.HashMap<>();
        final java.util.Set<UUID> inspected = new java.util.HashSet<>();
        Runtime(RoleEntryService service) { this.service = service; }
    }
    private static final class Deferred extends IllegalStateException { Deferred(String message) { super(message); } }
}
