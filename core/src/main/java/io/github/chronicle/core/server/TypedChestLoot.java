package io.github.chronicle.core.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.content.ChestBinding;
import io.github.chronicle.core.content.ChestTarget;
import io.github.chronicle.core.content.InventorySnapshot;
import io.github.chronicle.core.content.LootSchedule;
import io.github.chronicle.core.content.PendingChestOperation;
import io.github.chronicle.core.loot.LootConfig;
import io.github.chronicle.core.loot.LootCatalog;
import io.github.chronicle.core.loot.LootRoller;
import io.github.chronicle.core.loot.LootType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Campaign-registered loot types, one-step authoring and session-owned refill execution. */
public final class TypedChestLoot {
    private static final String HOLD = "chronicle_core:loot";
    private static final String SYSTEM_UUID = "00000000-0000-0000-0000-000000000000";
    private static final Map<MinecraftServer, Runtime> WORLDS = new IdentityHashMap<>();
    private TypedChestLoot() { }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var chests = literal("chests");
            chests.then(literal("inspect").requires(source -> source.hasPermissionLevel(2)).executes(context -> inspect(context.getSource())))
                    .then(literal("unbind").requires(source -> source.hasPermissionLevel(2)).executes(context -> unbind(context.getSource())))
                    .then(literal("validate").requires(source -> source.hasPermissionLevel(2)).executes(context -> validate(context.getSource())));
            for (boolean replace : new boolean[]{false, true}) {
                chests.then(literal(replace ? "rebind" : "bind").requires(source -> source.hasPermissionLevel(2)).then(argument("type", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            Runtime runtime = WORLDS.get(context.getSource().getServer());
                            if (runtime != null) for (String id : runtime.types.keySet()) {
                                String escaped = StringArgumentType.escapeIfRequired(id);
                                if (id.startsWith(builder.getRemainingLowerCase()) || escaped.startsWith(builder.getRemainingLowerCase())) builder.suggest(escaped);
                            }
                            return builder.buildFuture();
                        }).executes(context -> bind(context.getSource(), StringArgumentType.getString(context, "type"), replace))));
            }
            dispatcher.register(literal("chronicle").then(chests)
                    .then(literal("loot-tables").then(literal("reload").requires(source -> source.hasPermissionLevel(2))
                            .executes(context -> reload(context.getSource())))));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try { load(server); }
            catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                RuntimeHolds.hold(server, HOLD, "Loot configuration is unavailable; inspect config/chronicle/chest-loot.json and reload in Editing.");
                ChronicleCore.LOGGER.error("Typed chest loot unavailable; configuration was not replaced", exception);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(TypedChestLoot::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Runtime runtime = WORLDS.get(server);
            if (runtime != null) try { ChestAuthoring.service(server).saveSchedule(runtime.schedule); }
            catch (IOException | IllegalStateException exception) { ChronicleCore.LOGGER.error("Unable to checkpoint loot refill clock at shutdown", exception); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(WORLDS::remove);
    }

    private static Path configPath() { return FabricLoader.getInstance().getConfigDir().resolve("chronicle/chest-loot.json"); }
    private static void load(MinecraftServer server) throws IOException {
        LootConfig config = new LootConfig(configPath());
        Map<String, LootType> candidate = config.loadOrCreate(ChronicleCore.chestTypes().types());
        validateTypes(server, candidate);
        Runtime previous = WORLDS.get(server);
        Runtime runtime = new Runtime(config, candidate, previous == null ? ChestAuthoring.service(server).state().schedule() : previous.schedule);
        WORLDS.put(server, runtime);
        RuntimeHolds.release(server, HOLD);
        ChronicleCore.LOGGER.info("Chronicle loot types ready: {} types from {}", candidate.size(), configPath());
    }

    private static void validateTypes(MinecraftServer server, Map<String, LootType> candidate) {
        for(var type:candidate.values())for(var book:type.books())if(!BookRuntime.books(server).containsKey(book.bookId()))throw new IllegalArgumentException("Unknown named book in "+type.id()+": "+book.bookId());
        for(var b:ChestAuthoring.service(server).state().bindings().values())if(b.directBook()!=null&&!BookRuntime.books(server).containsKey(b.directBook().bookId()))throw new IllegalArgumentException("Unknown direct chest book: "+b.directBook().bookId());
        for (LootType type : candidate.values()) for (var entry : type.entries()) {
            if (!Registries.ITEM.containsId(Identifier.of(entry.item()))) throw new IllegalArgumentException("Unknown loot item in " + type.id() + ": " + entry.item());
            ItemStack stack = ChestAuthoring.decode(server, new InventorySnapshot(1, List.of(stackJson(entry.item(), entry.maxCount(), entry.componentsJson())))).getFirst();
            if (stack.isEmpty() || stack.getCount() > stack.getMaxCount()) throw new IllegalArgumentException("Invalid loot count/components in " + type.id() + ": " + entry.item());
        }
    }

    public static Map<String, LootType> types(MinecraftServer server) { return runtime(server).types; }

    /**
     * Saves one full loot-table definition from the authoring UI. This leaves typed chest
     * bindings, refill cycles and receipts alone; only the effective type catalog is published
     * after the durable config replacement has succeeded.
     */
    public static void saveType(ServerCommandSource source, LootType replacement, LootType expected) throws IOException {
        if (!source.hasPermissionLevel(2)) throw new IllegalStateException("Loot chest authoring requires operator permission level 2.");
        requireAuthor(source);
        if (replacement == null || expected == null) throw new IllegalArgumentException("replacement and expected loot tables are required");
        if (!replacement.id().equals(expected.id())) throw new IllegalArgumentException("Loot table id cannot change while editing");
        Runtime runtime = runtime(source.getServer());
        if (!expected.equals(runtime.types.get(expected.id()))) {
            throw new IllegalStateException("Loot table changed since this editor opened; reopen it before saving");
        }
        TreeMap<String, LootType> candidate = new TreeMap<>(runtime.types);
        candidate.put(replacement.id(), replacement);
        validateTypes(source.getServer(), candidate);
        Map<String, LootType> saved = runtime.config.replaceType(replacement, expected);
        // Do not publish a candidate before the config and its valid backup are durable.
        runtime.types = saved;
    }

    /** Publishes a newly created custom table only after the existing durable config writer succeeds. */
    public static void createType(ServerCommandSource source, LootType addition) throws IOException {
        if (!source.hasPermissionLevel(2)) throw new IllegalStateException("Loot chest authoring requires operator permission level 2.");
        requireAuthor(source);
        if (RuntimeHolds.reason(source.getServer()) != null) throw new IllegalStateException("Finish pending operations before creating a loot table.");
        if (addition == null) throw new IllegalArgumentException("new loot table is required");
        Runtime runtime = runtime(source.getServer());
        if (runtime.types.containsKey(addition.id())) throw new IllegalStateException("A loot table with this ID already exists; choose another ID.");
        TreeMap<String, LootType> candidate = new TreeMap<>(runtime.types);
        candidate.put(addition.id(), addition);
        if (candidate.size() > LootCatalog.MAX_TYPES) throw new IllegalStateException("The loot table limit has been reached.");
        validateTypes(source.getServer(), candidate);
        runtime.types = runtime.config.createType(addition);
    }

    private static Runtime runtime(MinecraftServer server) {
        Runtime result = WORLDS.get(server);
        if (result == null) throw new IllegalStateException("Loot types unavailable; correct config/chronicle/chest-loot.json and run /chronicle loot-tables reload.");
        return result;
    }

    private static int bind(ServerCommandSource source, String typeId, boolean replace) {
        return command(source, () -> {
            requireAuthor(source);
            if (!runtime(source.getServer()).types.containsKey(typeId)) throw new IllegalArgumentException("Unknown loot type: " + typeId);
            BlockPos pos = ChestAuthoring.target(source);
            ChestAuthoring.supported(source.getWorld(), pos);
            var target = new ChestTarget("target", 1, source.getWorld().getRegistryKey().getValue().toString(), pos.getX(), pos.getY(), pos.getZ(),
                    Registries.BLOCK.getId(source.getWorld().getBlockState(pos).getBlock()).toString());
            var binding = ChestAuthoring.service(source.getServer()).putTypedBinding(typeId, target, replace);
            return "Bound loot chest to " + typeId + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                    + ". Scheduled fills replace all contents; binding changed no items.";
        });
    }

    private static ChestBinding targeted(ServerCommandSource source) {
        BlockPos pos = ChestAuthoring.target(source);
        String dimension = source.getWorld().getRegistryKey().getValue().toString();
        return ChestAuthoring.service(source.getServer()).state().bindings().values().stream().filter(ChestBinding::typed)
                .filter(binding -> binding.directTarget().dimension().equals(dimension) && binding.directTarget().x() == pos.getX()
                        && binding.directTarget().y() == pos.getY() && binding.directTarget().z() == pos.getZ()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The targeted block is not a bound loot chest."));
    }

    private static int inspect(ServerCommandSource source) {
        return command(source, () -> {
            var binding = targeted(source);
            return "Loot chest at " + binding.directTarget().x() + " " + binding.directTarget().y() + " " + binding.directTarget().z() + "; type=" + binding.lootType() + "; expected=" + binding.directTarget().expectedBlock() + ".";
        });
    }
    private static int unbind(ServerCommandSource source) {
        return command(source, () -> {
            requireAuthor(source);
            var binding = targeted(source);
            ChestAuthoring.service(source.getServer()).removeTypedBinding(binding.id());
            return "Unbound targeted loot chest. Contents and refill history were preserved.";
        });
    }
    private static int validate(ServerCommandSource source) {
        return command(source, () -> {
            var bindings = ChestAuthoring.service(source.getServer()).state().bindings().values();
            int shortages = 0, invalid = 0, unresolved = 0;
            for (LootType type : runtime(source.getServer()).types.values()) {
                long count = bindings.stream().filter(binding -> type.id().equals(binding.lootType())).count();
                if (count < type.recommendedChests()) shortages++;
                source.sendFeedback(() -> Text.literal(type.id() + ": bound=" + count + "; recommended=" + type.recommendedChests()), false);
            }
            for (ChestBinding binding : bindings) if (binding.typed()) {
                String problem = null;
                if (!runtime(source.getServer()).types.containsKey(binding.lootType())) { invalid++; problem = "unknown type"; }
                else try { ChestAuthoring.resolve(source.getServer(), binding.directTarget()); }
                catch (IllegalStateException exception) {
                    problem = exception.getMessage();
                    if (problem.contains("unloaded")) unresolved++; else invalid++;
                }
                if (problem != null) { String line = binding.id() + ": " + problem; source.sendFeedback(() -> Text.literal(line), false); }
            }
            return "Loot chest validation: type shortages=" + shortages + "; invalid=" + invalid + "; unloaded=" + unresolved + ". Recommendations are advisory.";
        });
    }
    private static int reload(ServerCommandSource source) {
        return command(source, () -> {
            requireAuthor(source);
            load(source.getServer());
            return "Reloaded " + runtime(source.getServer()).types.size() + " loot types. Existing contents, cycles and receipts were not reset.";
        });
    }

    private static void requireAuthor(ServerCommandSource source) {
        if (source.getPlayer() == null || !AuthoringRuntime.isAuthor(source.getPlayer())) throw new IllegalStateException("Loot chest edits require non-active Editing.");
        if (source.getWorld() != source.getPlayer().getEntityWorld()) throw new IllegalStateException("Use the player's actual dimension for chest authoring.");
    }
    private static int command(ServerCommandSource source, Operation operation) {
        if (!source.hasPermissionLevel(2)) { source.sendError(Text.literal("Loot chest authoring requires operator permission level 2.")); return 0; }
        try { String result = operation.run(); source.sendFeedback(() -> Text.literal(result), false); return 1; }
        catch (IOException | IllegalArgumentException | IllegalStateException exception) { source.sendError(Text.literal(exception.getMessage())); return 0; }
    }
    private static void tick(MinecraftServer server) {
        Runtime runtime = WORLDS.get(server);
        if (runtime == null || RuntimeHolds.reason(server) != null) return;
        var campaign = ChronicleRuntime.available(server);
        if (campaign == null || campaign.state().phase() != SessionPhase.ACTIVE || server.getPlayerManager().getCurrentPlayerCount() == 0) return;
        try {
            var intervals = new TreeMap<String, Long>();
            runtime.types.forEach((id, type) -> intervals.put(id, type.refillTicks()));
            LootSchedule next = LootSchedule.advance(runtime.schedule, campaign.state().sessionNumber(), 1, intervals);
            boolean generationChanged = next.sessionNumber() != runtime.schedule.sessionNumber() || !next.types().equals(runtime.schedule.types());
            runtime.schedule = next;
            var service = ChestAuthoring.service(server);
            if (generationChanged || server.getTicks() % 1200 == 0) service.saveSchedule(next);
            if ((long) server.getTicks() - runtime.lastBatchTick < 20) return;
            runtime.lastBatchTick = server.getTicks();
            if(BookDeliveryRuntime.reconcile(server,next))return;
            List<Prepared> prepared = new ArrayList<>();
            var reservedBooks = new java.util.HashSet<String>();
            List<ChestBinding> bindings = service.state().bindings().values().stream().filter(ChestBinding::typed).toList();
            // Round-robin prevents broken/unloaded targets at the front from starving later ones.
            for (int checked = 0; checked < bindings.size() && prepared.size() < 4; checked++) {
                var binding = bindings.get(Math.floorMod(runtime.cursor++, bindings.size()));
                var cycle = next.types().get(binding.lootType());
                if (cycle == null || service.hasCompletedCycle(binding.id(), next.sessionNumber(), cycle.generation())) continue;
                try {
                    var target = ChestAuthoring.resolve(server, binding.directTarget());
                    ChestAuthoring.requireSavingEnabled(target);
                    UUID id = UUID.nameUUIDFromBytes((binding.id() + "|" + next.sessionNumber() + "|" + cycle.generation()).getBytes(StandardCharsets.UTF_8));
                    InventorySnapshot before=ChestAuthoring.snapshot(server,target.inventory());
                    var bookPlan=BookDeliveryRuntime.planFill(server,binding,runtime.types.get(binding.lootType()),before,roll(server,runtime.types.get(binding.lootType()),target.inventory().size(),id),id,reservedBooks);
                    InventorySnapshot after = bookPlan.after();
                    var operation = new PendingChestOperation(id.toString(), SYSTEM_UUID, "Chronicle loot scheduler", binding.id(), binding.revision(), next.sessionNumber(), cycle.generation(),
                            binding.directTarget(), before, after, Instant.now().toString(),PendingChestOperation.Purpose.FILL,null,0,null,null,null,bookPlan.claims());
                    prepared.add(new Prepared(operation, target, ChestAuthoring.validateForInventory(server, after, target.inventory())));
                    runtime.problems.remove(binding.id());
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    if (!exception.getMessage().equals(runtime.problems.put(binding.id(), exception.getMessage()))) {
                        ChronicleCore.LOGGER.warn("Loot chest {} waits: {}", binding.id(), exception.getMessage());
                    }
                }
            }
            if (prepared.isEmpty()) return;
            // Persist the current clock with the intent so a restart cannot rewind to a completed cycle.
            service.saveSchedule(next);
            var rules=new TreeMap<String,io.github.chronicle.core.books.BookDefinition.Rule>();BookRuntime.books(server).forEach((key,value)->rules.put(key,value.rule()));
            for (Prepared item : prepared){if(item.operation.bookClaims().isEmpty())service.begin(item.operation,true);else service.beginLore(item.operation,rules,true);}
            ChestAuthoring.refreshHold(server);
            for (Prepared item : prepared) {
                ChestAuthoring.closeViewers(server, item.target.inventory());
                for (int slot = 0; slot < item.stacks.size(); slot++) item.target.inventory().setStack(slot, item.stacks.get(slot).copy());
                item.target.inventory().markDirty();
            }
            if (!server.saveAll(true, true, true)) throw new IOException("Minecraft did not confirm a flushed loot batch save");
            for (Prepared item : prepared) service.complete(item.operation.operationId(), Instant.now().toString());
            ChestAuthoring.refreshHold(server);
            ChronicleCore.LOGGER.info("Refilled {} typed loot chests for session {}", prepared.size(), next.sessionNumber());
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            // Keep any durable pending evidence. Ordinary target validation failures were handled above.
            RuntimeHolds.hold(server, HOLD, "Loot refill is held; inspect /chronicle chests status, recover pending fills, then reload loot configuration in Editing.");
            try { ChestAuthoring.refreshHold(server); } catch (IllegalStateException ignored) { }
            ChronicleCore.LOGGER.error("Typed chest scheduling stopped without discarding pending evidence", exception);
        }
    }

    private static InventorySnapshot roll(MinecraftServer server, LootType type, int size, UUID operation) {
        var random = new Random(operation.getMostSignificantBits() ^ operation.getLeastSignificantBits());
        var selected = LootRoller.roll(type, random);
        if (selected.size() > size) throw new IllegalArgumentException("Loot type exceeds target capacity: " + type.id());
        List<String> stacks = new ArrayList<>(Collections.nCopies(size, "{}"));
        List<Integer> slots = new ArrayList<>(); for (int i = 0; i < size; i++) slots.add(i);
        Collections.shuffle(slots, random);
        for (int i = 0; i < selected.size(); i++) {
            var entry = selected.get(i);
            stacks.set(slots.get(i), stackJson(entry.entry().item(), entry.count(), entry.entry().componentsJson()));
        }
        var normalized = ChestAuthoring.decode(server, new InventorySnapshot(size, stacks));
        return ChestAuthoring.snapshot(server, new net.minecraft.inventory.SimpleInventory(normalized.toArray(ItemStack[]::new)));
    }
    private static String stackJson(String item, int count, String components) {
        JsonObject stack = new JsonObject(); stack.addProperty("id", item); stack.addProperty("count", count);
        var value = JsonParser.parseString(components).getAsJsonObject(); if (!value.isEmpty()) stack.add("components", value);
        return stack.toString();
    }
    private static final class Runtime {
        final LootConfig config;
        Map<String, LootType> types;
        LootSchedule schedule;
        int cursor;
        long lastBatchTick = -20;
        final Map<String, String> problems = new HashMap<>();
        Runtime(LootConfig config, Map<String, LootType> types, LootSchedule schedule) {
            this.config = config;
            this.types = types;
            this.schedule = schedule;
        }
    }
    private record Prepared(PendingChestOperation operation, ChestAuthoring.ResolvedTarget target, List<ItemStack> stacks) { }
    @FunctionalInterface private interface Operation { String run() throws IOException; }
}
