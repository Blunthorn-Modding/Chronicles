package io.github.chronicle.core.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.content.ChestBinding;
import io.github.chronicle.core.content.ChestContentService;
import io.github.chronicle.core.content.ChestContentStore;
import io.github.chronicle.core.content.ChestPolicy;
import io.github.chronicle.core.content.ChestTarget;
import io.github.chronicle.core.content.InventorySnapshot;
import io.github.chronicle.core.content.PendingChestOperation;
import io.github.chronicle.core.content.RecoveryDecision;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.command.CommandSource;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Reusable Core chest templates and explicit, journaled empty-only fills. */
public final class ChestAuthoring {
    private static final String HOLD_OWNER = "chronicle_core:chests";
    private static final int PREVIEW_TICKS = 20 * 60;
    private static final Map<MinecraftServer, Runtime> WORLDS = new IdentityHashMap<>();

    private ChestAuthoring() { }

    /** Called exactly once by Chronicle Core during mod initialization. */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commands(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(ChestAuthoring::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WORLDS.remove(server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Runtime runtime = WORLDS.get(server);
            if (runtime != null) runtime.previews.remove(handler.player.getUuid());
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(world instanceof ServerWorld serverWorld) || !isPendingTarget(serverWorld, hit.getBlockPos())) return ActionResult.PASS;
            player.sendMessage(Text.literal("This container is locked while Chronicle recovers an uncertain fill."), false);
            return ActionResult.FAIL;
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!isPendingTarget((ServerWorld) world, pos)) return true;
            player.sendMessage(Text.literal("This container cannot be broken while Chronicle recovers an uncertain fill."), false);
            return false;
        });
    }

    private static void start(MinecraftServer server) {
        var file = server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/chest-content.json");
        try {
            Runtime runtime = new Runtime(new ChestContentService(new ChestContentStore(file)));
            validateCodecPayloads(server, runtime.service.state());
            WORLDS.put(server, runtime);
            synchronizeHold(server, runtime);
            ChronicleCore.LOGGER.info("Chronicle chest content ready: {}", file);
        } catch (IOException | IllegalArgumentException exception) {
            WORLDS.remove(server);
            RuntimeHolds.hold(server, HOLD_OWNER, "Chronicle chest content is unavailable; inspect the server log and restore chest-content.json while stopped.");
            ChronicleCore.LOGGER.error("Chest content unavailable; existing data was not replaced. Restore {} while stopped.", file, exception);
        }
    }

    private static Runtime runtime(MinecraftServer server) {
        if (!server.isOnThread()) throw new IllegalStateException("Chest content requires the server thread.");
        Runtime runtime = WORLDS.get(server);
        if (runtime == null) throw new IllegalStateException("Chest content is unavailable; inspect the server log and chest-content.json while stopped.");
        return runtime;
    }

    private static void commands(CommandDispatcher<ServerCommandSource> dispatcher) {
        var authorChest = literal("chest")
                .then(literal("capture").then(argument("template", StringArgumentType.string())
                        .executes(context -> capture(context.getSource(), text(context, "template"), false))))
                .then(literal("replace").then(argument("template", StringArgumentType.string())
                        .suggests((context, builder) -> suggestions(runtime(context.getSource().getServer()).service.state().templates().keySet().stream(), builder))
                        .executes(context -> capture(context.getSource(), text(context, "template"), true))))
                .then(literal("list").executes(context -> listAuthoring(context.getSource())))
                .then(literal("show").then(argument("template", StringArgumentType.string())
                        .suggests((context, builder) -> suggestions(runtime(context.getSource().getServer()).service.state().templates().keySet().stream(), builder))
                        .executes(context -> showTemplate(context.getSource(), text(context, "template")))))
                .then(literal("bind").then(bindingArguments(false)))
                .then(literal("rebind").then(bindingArguments(true)));
        dispatcher.register(literal("chronicle").then(literal("author").requires(source -> source.hasPermissionLevel(2)).then(authorChest)));

        var chest = literal("chest").requires(source -> source.hasPermissionLevel(2))
                .then(literal("status").executes(context -> status(context.getSource())))
                .then(literal("preview").then(argument("binding", StringArgumentType.string())
                        .suggests((context, builder) -> suggestions(runtime(context.getSource().getServer()).service.state().bindings().keySet().stream(), builder))
                        .executes(context -> preview(context.getSource(), text(context, "binding")))))
                .then(literal("apply").then(argument("binding", StringArgumentType.string())
                        .suggests((context, builder) -> suggestions(runtime(context.getSource().getServer()).service.state().bindings().keySet().stream(), builder))
                        .executes(context -> apply(context.getSource(), text(context, "binding")))))
                .then(literal("recover").then(argument("operation", StringArgumentType.string())
                        .suggests((context, builder) -> suggestions(runtime(context.getSource().getServer()).service.state().pending().keySet().stream(), builder))
                        .executes(context -> recover(context.getSource(), text(context, "operation")))));
        dispatcher.register(literal("chronicle").then(chest));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<ServerCommandSource, ?> bindingArguments(boolean replace) {
        return argument("binding", StringArgumentType.string())
                .then(argument("anchor", StringArgumentType.string())
                        .suggests((context, builder) -> suggestions(AuthoringRuntime.get(context.getSource().getServer()).service().state().sites().values().stream()
                                .filter(AuthoringSite.Anchor.class::isInstance).map(AuthoringSite::id), builder))
                        .then(argument("template", StringArgumentType.string())
                                .suggests((context, builder) -> suggestions(runtime(context.getSource().getServer()).service.state().templates().keySet().stream(), builder))
                                .then(argument("policy", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(Stream.of("manual", "once_campaign", "once_session"), builder))
                                        .executes(context -> bind(context.getSource(), text(context, "binding"), text(context, "anchor"), text(context, "template"), text(context, "policy"), replace)))));
    }

    private static int capture(ServerCommandSource source, String id, boolean replace) {
        return authorEdit(source, runtime -> {
            BlockPos pos = target(source);
            Inventory inventory = supported(source.getWorld(), pos);
            if (inventory.isEmpty()) throw new IllegalStateException("Refusing to capture an entirely empty template.");
            InventorySnapshot snapshot = snapshot(source.getServer(), inventory);
            var saved = runtime.service.putTemplate(id, snapshot, replace);
            ChronicleCore.LOGGER.info("Chest template {} {} by {} ({}), revision={}", replace ? "replaced" : "captured", id, source.getName(), player(source).getUuid(), saved.revision());
            return "Captured chest template " + saved.id() + "; revision=" + saved.revision() + "; slots=" + snapshot.size() + ". Source inventory was not changed.";
        });
    }

    private static int bind(ServerCommandSource source, String id, String anchorId, String templateId, String policyName, boolean replace) {
        return authorEdit(source, runtime -> {
            AuthoringSite site = AuthoringRuntime.get(source.getServer()).service().state().sites().get(anchorId);
            if (!(site instanceof AuthoringSite.Anchor anchor)) throw new IllegalArgumentException("Unknown block anchor: " + anchorId);
            var template = runtime.service.state().templates().get(templateId);
            if (template == null) throw new IllegalArgumentException("Unknown chest template: " + templateId);
            ResolvedTarget target = resolve(source.getServer(), anchor);
            if (target.inventory.size() != template.contents().size()) throw new IllegalStateException("Anchor inventory has " + target.inventory.size() + " slots; template has " + template.contents().size() + ".");
            ChestPolicy policy;
            try { policy = ChestPolicy.valueOf(policyName.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Policy must be manual, once_campaign or once_session."); }
            ChestBinding binding = runtime.service.putBinding(id, templateId, template.revision(), anchorId, anchor.revision(), policy, replace);
            return (replace ? "Rebound " : "Bound ") + "chest " + binding.id() + " to anchor " + anchorId + "; policy=" + policyName + "; revision=" + binding.revision() + ".";
        });
    }

    private static int listAuthoring(ServerCommandSource source) {
        return authorRead(source, runtime -> {
            source.sendFeedback(() -> Text.literal("Chest templates: " + String.join(", ", runtime.service.state().templates().keySet())), false);
            source.sendFeedback(() -> Text.literal("Chest bindings: " + String.join(", ", runtime.service.state().bindings().keySet())), false);
            return "Chest authoring: templates=" + runtime.service.state().templates().size() + "; bindings=" + runtime.service.state().bindings().size() + ".";
        });
    }

    private static int showTemplate(ServerCommandSource source, String id) {
        return authorRead(source, runtime -> {
            var template = runtime.service.state().templates().get(id);
            if (template == null) throw new IllegalArgumentException("Unknown chest template: " + id);
            List<ItemStack> stacks = decode(source.getServer(), template.contents());
            long occupied = stacks.stream().filter(stack -> !stack.isEmpty()).count();
            return "Chest template " + id + "; revision=" + template.revision() + "; slots=" + template.contents().size() + "; occupied=" + occupied + ".";
        });
    }

    private static int preview(ServerCommandSource source, String id) {
        return run(source, runtime -> {
            requireActive(source);
            requireNoRuntimeHold(source.getServer());
            ServerPlayerEntity player = player(source);
            ChestBinding binding = binding(runtime, id);
            int session = session(source).sessionNumber();
            runtime.service.requireEligible(binding, session);
            var template = runtime.service.requireCurrentTemplate(binding);
            AuthoringSite.Anchor anchor = currentAnchor(source.getServer(), binding);
            ResolvedTarget target = resolve(source.getServer(), anchor);
            if (!target.inventory.isEmpty()) throw new IllegalStateException("Target inventory is not completely empty; preview refused.");
            List<ItemStack> decoded = validateForInventory(source.getServer(), template.contents(), target.inventory);
            InventorySnapshot before = snapshot(source.getServer(), target.inventory);
            if (before.equals(template.contents())) throw new IllegalStateException("Template would not change the empty target.");
            runtime.previews.put(player.getUuid(), new Preview(binding.id(), binding.revision(), template.revision(), anchor.revision(), session,
                    before, source.getServer().getTicks() + PREVIEW_TICKS));
            long occupied = decoded.stream().filter(stack -> !stack.isEmpty()).count();
            return "Previewed chest " + id + " at " + anchor.id() + ": slots=" + decoded.size() + "; occupied=" + occupied
                    + "; policy=" + binding.policy().name().toLowerCase(Locale.ROOT) + ". Apply within 60 seconds with /chronicle chest apply " + StringArgumentType.escapeIfRequired(id) + ".";
        });
    }

    private static int apply(ServerCommandSource source, String id) {
        return run(source, runtime -> {
            requireActive(source);
            requireNoRuntimeHold(source.getServer());
            ServerPlayerEntity player = player(source);
            Preview preview = runtime.previews.get(player.getUuid());
            if (preview == null || !preview.bindingId.equals(id)) throw new IllegalStateException("Preview this binding before applying it.");
            if (source.getServer().getTicks() > preview.expiresAtTick) { runtime.previews.remove(player.getUuid()); throw new IllegalStateException("Chest preview expired; preview it again."); }
            int session = session(source).sessionNumber();
            if (session != preview.sessionNumber) throw new IllegalStateException("Session changed after preview; preview again.");
            ChestBinding binding = runtime.service.requireCurrentBinding(id, preview.bindingRevision);
            var template = runtime.service.requireCurrentTemplate(binding);
            if (template.revision() != preview.templateRevision) throw new IllegalStateException("Template changed after preview; preview again.");
            AuthoringSite.Anchor anchor = currentAnchor(source.getServer(), binding);
            if (anchor.revision() != preview.anchorRevision) throw new IllegalStateException("Anchor changed after preview; preview again.");
            ResolvedTarget target = resolve(source.getServer(), anchor);
            requireSavingEnabled(target);
            InventorySnapshot before = snapshot(source.getServer(), target.inventory);
            if (!target.inventory.isEmpty() || !before.equals(preview.before)) throw new IllegalStateException("Target contents changed after preview; no fill was attempted.");
            List<ItemStack> after = validateForInventory(source.getServer(), template.contents(), target.inventory);
            PendingChestOperation operation = new PendingChestOperation(UUID.randomUUID().toString(), player.getUuidAsString(), source.getName(),
                    binding.id(), binding.revision(), session, chestTarget(anchor), before, template.contents(), Instant.now().toString());
            boolean began = false;
            try {
                runtime.service.begin(operation, true);
                began = true;
                synchronizeHold(source.getServer(), runtime);
                closeViewers(source.getServer(), target.inventory);
                for (int slot = 0; slot < after.size(); slot++) target.inventory.setStack(slot, after.get(slot).copy());
                target.inventory.markDirty();
                if (!source.getServer().saveAll(false, true, true)) throw new IOException("Minecraft did not confirm a flushed world save");
                runtime.service.complete(operation.operationId(), Instant.now().toString());
                runtime.previews.remove(player.getUuid());
                synchronizeHold(source.getServer(), runtime);
                return "Filled chest " + id + "; operation=" + operation.operationId() + "; receipt saved.";
            } catch (IOException | RuntimeException exception) {
                if (began) {
                    RuntimeHolds.hold(source.getServer(), HOLD_OWNER, "Chest fill " + operation.operationId() + " is unresolved; use /chronicle chest recover " + operation.operationId() + ".");
                    ChronicleCore.LOGGER.error("Chest fill {} is uncertain and remains journaled", operation.operationId(), exception);
                    throw new IllegalStateException("Chest fill is uncertain. World simulation is held; inspect status and recover operation " + operation.operationId() + ".", exception);
                }
                throw exception;
            }
        });
    }

    private static int recover(ServerCommandSource source, String operationId) {
        return run(source, runtime -> {
            PendingChestOperation operation = runtime.service.requirePending(operationId);
            ResolvedTarget target = resolve(source.getServer(), operation.target());
            requireSavingEnabled(target);
            InventorySnapshot live = snapshot(source.getServer(), target.inventory);
            RecoveryDecision decision = RecoveryDecision.compare(operation, live);
            switch (decision) {
                case ABORT_UNCHANGED -> runtime.service.abortUnchanged(operationId);
                case COMMIT_APPLIED -> {
                    if (!source.getServer().saveAll(false, true, true)) {
                        RuntimeHolds.hold(source.getServer(), HOLD_OWNER, "Chest fill " + operationId + " matches the intended contents but its world save is not confirmed.");
                        throw new IllegalStateException("Applied contents match, but Minecraft did not confirm a flushed world save. Pending evidence remains and simulation stays held.");
                    }
                    runtime.service.complete(operationId, Instant.now().toString());
                }
                case CONFLICT -> {
                    RuntimeHolds.hold(source.getServer(), HOLD_OWNER, "Chest fill " + operationId + " conflicts with live contents; preserve evidence and resolve manually while stopped.");
                    throw new IllegalStateException("Recovery conflict: live contents match neither the journaled before nor after snapshot. No inventory or journal data was changed.");
                }
            }
            synchronizeHold(source.getServer(), runtime);
            return decision == RecoveryDecision.ABORT_UNCHANGED
                    ? "Recovered chest operation " + operationId + ": target was unchanged; pending intent removed without a receipt."
                    : "Recovered chest operation " + operationId + ": applied contents verified; durable receipt recorded.";
        });
    }

    private static int status(ServerCommandSource source) {
        return run(source, runtime -> {
            var state = runtime.service.state();
            for (PendingChestOperation operation : state.pending().values()) {
                source.sendFeedback(() -> Text.literal("Pending " + operation.operationId() + " | binding=" + operation.bindingId() + " | target="
                        + operation.target().dimension() + " " + operation.target().x() + " " + operation.target().y() + " " + operation.target().z()), false);
            }
            return "Chest content: templates=" + state.templates().size() + "; bindings=" + state.bindings().size() + "; receipts=" + state.receipts().size()
                    + "; pending=" + state.pending().size() + "; revision=" + state.revision() + ".";
        });
    }

    private static AuthoringSite.Anchor currentAnchor(MinecraftServer server, ChestBinding binding) {
        AuthoringSite site = AuthoringRuntime.get(server).service().state().sites().get(binding.anchorId());
        if (!(site instanceof AuthoringSite.Anchor anchor)) throw new IllegalStateException("Chest binding anchor is missing: " + binding.anchorId());
        if (anchor.revision() != binding.anchorRevision()) throw new IllegalStateException("Chest binding anchor revision is stale: " + binding.anchorId());
        return anchor;
    }

    private static ResolvedTarget resolve(MinecraftServer server, AuthoringSite.Anchor anchor) {
        return resolve(server, chestTarget(anchor));
    }

    private static ResolvedTarget resolve(MinecraftServer server, ChestTarget target) {
        ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(target.dimension())));
        if (world == null) throw new IllegalStateException("Target dimension is unavailable: " + target.dimension());
        BlockPos pos = new BlockPos(target.x(), target.y(), target.z());
        if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) throw new IllegalStateException("Target chunk is unloaded; move near it and try again.");
        String actual = Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString();
        if (!actual.equals(target.expectedBlock())) throw new IllegalStateException("Target block changed: expected " + target.expectedBlock() + ", found " + actual + ".");
        return new ResolvedTarget(world, pos, supported(world, pos));
    }

    private static void requireSavingEnabled(ResolvedTarget target) {
        if (target.world.savingDisabled || net.minecraft.SharedConstants.DONT_SAVE_WORLD) {
            throw new IllegalStateException("Enable world saving before applying or recovering a chest fill.");
        }
    }

    private static Inventory supported(ServerWorld world, BlockPos pos) {
        var entity = world.getBlockEntity(pos);
        if (!(entity instanceof LootableContainerBlockEntity lootable)
                || (!(entity instanceof ChestBlockEntity) && !(entity instanceof BarrelBlockEntity) && !(entity instanceof ShulkerBoxBlockEntity))) {
            throw new IllegalStateException("Supported chest targets are single chests, barrels and shulker boxes.");
        }
        if (entity instanceof ChestBlockEntity && world.getBlockState(pos).get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            throw new IllegalStateException("Double chests are not supported; use a single container anchor.");
        }
        if (lootable.getLootTable() != null) throw new IllegalStateException("Container still has an unresolved loot table; open or replace it before capture/binding.");
        return lootable;
    }

    private static InventorySnapshot snapshot(MinecraftServer server, Inventory inventory) {
        List<String> encoded = new ArrayList<>(inventory.size());
        RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, server.getRegistryManager());
        for (int slot = 0; slot < inventory.size(); slot++) {
            int index = slot;
            encoded.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, inventory.getStack(slot).copy())
                    .getOrThrow(message -> new IllegalArgumentException("Unable to encode inventory slot " + index + ": " + message)).toString());
        }
        return new InventorySnapshot(inventory.size(), encoded);
    }

    private static List<ItemStack> decode(MinecraftServer server, InventorySnapshot snapshot) {
        RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, server.getRegistryManager());
        List<ItemStack> stacks = new ArrayList<>(snapshot.size());
        for (int slot = 0; slot < snapshot.size(); slot++) {
            int index = slot;
            stacks.add(ItemStack.OPTIONAL_CODEC.parse(ops, JsonParser.parseString(snapshot.stacks().get(slot)))
                    .getOrThrow(message -> new IllegalArgumentException("Unable to decode inventory slot " + index + ": " + message)));
        }
        return List.copyOf(stacks);
    }

    private static List<ItemStack> validateForInventory(MinecraftServer server, InventorySnapshot snapshot, Inventory inventory) {
        if (snapshot.size() != inventory.size()) throw new IllegalStateException("Target inventory size changed.");
        List<ItemStack> stacks = decode(server, snapshot);
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (!stack.isEmpty() && (!inventory.isValid(slot, stack) || stack.getCount() > inventory.getMaxCount(stack))) {
                throw new IllegalStateException("Template stack is not valid for target slot " + slot + ".");
            }
        }
        return stacks;
    }

    private static void validateCodecPayloads(MinecraftServer server, io.github.chronicle.core.content.ChestContentState state) {
        state.templates().values().forEach(template -> decode(server, template.contents()));
        state.pending().values().forEach(operation -> { decode(server, operation.before()); decode(server, operation.after()); });
    }

    private static ChestTarget chestTarget(AuthoringSite.Anchor anchor) {
        return new ChestTarget(anchor.id(), anchor.revision(), anchor.dimension(), anchor.position().x(), anchor.position().y(), anchor.position().z(), anchor.expectedBlock());
    }

    private static void closeViewers(MinecraftServer server, Inventory inventory) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.currentScreenHandler != player.playerScreenHandler
                    && player.currentScreenHandler.slots.stream().anyMatch(slot -> slot.inventory == inventory)) player.closeHandledScreen();
        }
    }

    private static boolean isPendingTarget(ServerWorld world, BlockPos pos) {
        Runtime runtime = WORLDS.get(world.getServer());
        if (runtime == null) return false;
        String dimension = world.getRegistryKey().getValue().toString();
        return runtime.service.state().pending().values().stream().map(PendingChestOperation::target)
                .anyMatch(target -> target.dimension().equals(dimension) && target.x() == pos.getX() && target.y() == pos.getY() && target.z() == pos.getZ());
    }

    private static void synchronizeHold(MinecraftServer server, Runtime runtime) {
        if (runtime.service.state().pending().isEmpty()) RuntimeHolds.release(server, HOLD_OWNER);
        else RuntimeHolds.hold(server, HOLD_OWNER, "A Chronicle chest fill is unresolved; use /chronicle chest status and recover it before play continues.");
    }

    private static BlockPos target(ServerCommandSource source) {
        ServerPlayerEntity player = player(source);
        var start = player.getEyePos(); var end = start.add(player.getRotationVec(1).multiply(6));
        for (int sample = 0; sample <= 24; sample++) {
            BlockPos pos = BlockPos.ofFloored(start.lerp(end, sample / 24.0));
            if (!source.getWorld().getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) throw new IllegalStateException("Target ray crosses an unloaded chunk.");
        }
        HitResult hit = source.getWorld().raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) throw new IllegalStateException("Look at a container within six blocks.");
        return block.getBlockPos();
    }

    private static ChestBinding binding(Runtime runtime, String id) {
        ChestBinding binding = runtime.service.state().bindings().get(id);
        if (binding == null) throw new IllegalArgumentException("Unknown chest binding: " + id);
        return binding;
    }

    private static io.github.chronicle.core.campaign.CampaignState session(ServerCommandSource source) { return ChronicleRuntime.service(source.getServer()).state(); }
    private static void requireActive(ServerCommandSource source) { if (session(source).phase() != SessionPhase.ACTIVE) throw new IllegalStateException("Chest fills require an active Chronicle session."); }
    private static void requireNoRuntimeHold(MinecraftServer server) {
        String reason = RuntimeHolds.reason(server);
        if (reason != null) throw new IllegalStateException("Chest operation blocked while world simulation is held: " + reason);
    }
    private static ServerPlayerEntity player(ServerCommandSource source) { if (source.getPlayer() == null) throw new IllegalStateException("This chest command requires a player."); return source.getPlayer(); }
    private static String text(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context, String name) { return StringArgumentType.getString(context, name); }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestions(Stream<String> choices, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(choices.map(StringArgumentType::escapeIfRequired), builder);
    }

    private static int authorRead(ServerCommandSource source, Operation operation) {
        if (!source.hasPermissionLevel(2)) { source.sendError(Text.literal("Chest authoring requires operator permission level 2.")); return 0; }
        try {
            if (!AuthoringRuntime.editable(source.getServer()) || !AuthoringRuntime.isAuthor(player(source))) throw new IllegalStateException("Chest authoring requires non-active author mode.");
            String message = operation.run(runtime(source.getServer())); source.sendFeedback(() -> Text.literal(message), false); return 1;
        } catch (IOException exception) { ChronicleCore.LOGGER.error("Chest authoring save failed", exception); source.sendError(Text.literal("Chest content could not be saved; no definition was changed.")); }
        catch (IllegalArgumentException | IllegalStateException exception) { source.sendError(Text.literal(exception.getMessage())); }
        return 0;
    }

    private static int authorEdit(ServerCommandSource source, Operation operation) { return authorRead(source, operation); }

    private static int run(ServerCommandSource source, Operation operation) {
        if (!source.hasPermissionLevel(2)) { source.sendError(Text.literal("Chest operations require operator permission level 2.")); return 0; }
        try { String message = operation.run(runtime(source.getServer())); source.sendFeedback(() -> Text.literal(message), false); return 1; }
        catch (IOException exception) { ChronicleCore.LOGGER.error("Chest content could not be saved", exception); source.sendError(Text.literal("Chest content could not be saved; no unjournaled mutation was attempted.")); }
        catch (IllegalArgumentException | IllegalStateException exception) { if (exception.getCause() != null) ChronicleCore.LOGGER.error("Chest operation failed", exception); source.sendError(Text.literal(exception.getMessage())); }
        return 0;
    }

    private record Runtime(ChestContentService service, Map<UUID, Preview> previews) {
        private Runtime(ChestContentService service) { this(service, new HashMap<>()); }
    }
    private record Preview(String bindingId, long bindingRevision, long templateRevision, long anchorRevision,
                           int sessionNumber, InventorySnapshot before, int expiresAtTick) { }
    private record ResolvedTarget(ServerWorld world, BlockPos pos, Inventory inventory) { }
    @FunctionalInterface private interface Operation { String run(Runtime runtime) throws IOException; }
}
