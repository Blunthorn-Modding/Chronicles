package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.enchanting.EnchantmentCapConfig;
import io.github.chronicle.core.enchanting.EnchantmentCaps;
import io.github.chronicle.core.enchanting.IngredientEnchantmentConfig;
import io.github.chronicle.core.enchanting.IngredientEnchantmentRecipe;
import io.github.chronicle.core.network.ItemEnchantingPayloads;
import io.github.chronicle.core.screen.ItemEnchantingScreenHandler;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.core.server.ItemEnchantingRuntime;
import io.github.chronicle.core.server.GameSetupTools;
import io.github.chronicle.core.server.MapSiteAssignments;
import io.github.chronicle.core.server.PlayRuntime;
import io.github.chronicle.quietbelow.QuietBelow;
import io.github.chronicle.quietbelow.content.AncientForgeBlock;
import io.github.chronicle.quietbelow.content.AncientForgeBlockEntity;
import io.github.chronicle.quietbelow.content.AncientForges;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static net.minecraft.server.command.CommandManager.literal;

/** Guarded disposable native evidence fixture for the real-slot Ancient Forge. */
public final class AncientForgeTrial {
    private static final BlockPos HEART = new BlockPos(-4, 64, 0), CONTACT = new BlockPos(-3, 64, 0);
    private static final BlockPos FORGE = new BlockPos(2, 65, 3), SAFE = new BlockPos(3, 65, 3);
    private static final String SOURCE = "forge_trial_source", ROOT = "forge_trial_heart";
    private static final Pattern RUN = Pattern.compile("[0-9]{8}T[0-9]{12,}Z");
    private static int checks;
    private static int expectedHeat;
    private static long saturatedAt;
    private static final java.util.Map<BlockPos, net.minecraft.block.BlockState> isolatedNeighbors = new java.util.LinkedHashMap<>();
    private static java.util.LinkedHashSet<AncientForgeBlockEntity> savedModelTracker;

    private AncientForgeTrial() { }

    public static void register() {
        if (!"true".equals(System.getenv("CHRONICLE_FORGE_NATIVE_TRIAL"))) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var root = literal("quiet_below_forge_trial").requires(source -> source.hasPermissionLevel(2));
            for (String verb : List.of("prepare", "ready", "offer", "transactions", "check", "save", "restart", "render", "itemrender", "model_off", "model_kindling", "model_active", "model_cut", "model_reconnect", "model_pause", "model_resume", "model_disable", "model_enable", "model_isolated", "model_adjacent", "model_saturated_pause", "model_check", "feeding_age"))
                root.then(literal(verb).executes(context -> run(context.getSource(), verb)));
            dispatcher.register(root);
        });
    }

    private static int run(ServerCommandSource source, String verb) {
        checks = 0;
        try {
            requireFixture(source);
            ServerWorld world = source.getWorld();
            ServerPlayerEntity player = source.getPlayerOrThrow();
            switch (verb) {
                case "prepare" -> prepare(source, world, player);
                case "model_off", "model_kindling", "model_active", "model_cut", "model_reconnect", "model_pause", "model_resume", "model_disable", "model_enable", "model_isolated", "model_adjacent", "model_saturated_pause" -> model(world, player, verb);
                case "model_check" -> modelCheck(world);
                case "ready" -> ready(world);
                case "feeding_age" -> feedingAge(world, player);
                case "offer" -> offer(world, player);
                case "transactions" -> transactions(world, player);
                case "check" -> checkCompleted(world, player);
                case "save" -> save(source, world, player);
                case "restart" -> restart(source, world, player);
                case "render" -> render(world, player);
                case "itemrender" -> { player.getInventory().setStack(8, new ItemStack(QuietBelow.ANCIENT_FORGE)); player.getInventory().setSelectedSlot(8); player.playerScreenHandler.syncState(); check(player.getMainHandStack().isOf(QuietBelow.ANCIENT_FORGE), "Forge inventory/held model fixture missing"); }
                default -> throw new IllegalArgumentException("Unknown Forge trial verb");
            }
            int count = checks;
            source.sendFeedback(() -> Text.literal("PASS Forge " + verb + " (" + count + " checks)"), false);
            return 1;
        } catch (Exception error) {
            QuietBelow.LOGGER.error("Ancient Forge native trial failed: {}", verb, error);
            source.sendError(Text.literal("FAIL Forge " + verb + ":" + detail(error)));
            return 0;
        }
    }

    private static void prepare(ServerCommandSource source, ServerWorld world, ServerPlayerEntity player) throws Exception {
        for (int x = -6; x <= 18; x++) for (int z = -3; z <= 18; z++) put(world, new BlockPos(x, 63, z), Blocks.STONE.getDefaultState());
        put(world, HEART, Blocks.REINFORCED_DEEPSLATE.getDefaultState());
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            BlockPos neighbor = HEART.add(x, y, z);
            if (!neighbor.equals(HEART) && !neighbor.equals(CONTACT)) put(world, neighbor, Blocks.GLASS.getDefaultState());
        }
        for (int x = -3; x <= 15; x++) put(world, new BlockPos(x, 64, 0), Blocks.SCULK.getDefaultState());
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) put(world, new BlockPos(x, 64, z), Blocks.SCULK.getDefaultState());
        put(world, FORGE, QuietBelow.ANCIENT_FORGE_BLOCK.getDefaultState()); put(world, SAFE, Blocks.STONE.getDefaultState());
        check(world.getBlockEntity(FORGE) instanceof AncientForgeBlockEntity, "Forge block entity was not created");

        var authoring = AuthoringRuntime.get(source.getServer()); authoring.enable(player.getUuid());
        String dimension = world.getRegistryKey().getValue().toString();
        var region = (AuthoringSite.Region) authoring.service().put(new AuthoringSite.Region(SOURCE, dimension, 1, new BlockPosition(-4, 64, 0), new BlockPosition(15, 64, 15)), false);
        var heart = (AuthoringSite.Anchor) authoring.service().put(new AuthoringSite.Anchor(ROOT, dimension, 1, new BlockPosition(-4, 64, 0), "minecraft:reinforced_deepslate"), false);
        MapSiteAssignments.assign(source, "quiet_below:source_city", region.id()); MapSiteAssignments.assign(source, "quiet_below:relic_heart", heart.id());
        EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
        engine.configure(new EcologyEngine.Setup(region.id(), region.revision(), heart.id(), heart.revision(), dimension), new EcologyEngine.Area(new EcologyTerrain.Pos(-4, 64, 0), new EcologyTerrain.Pos(15, 64, 15)), QuietBelowEcology.pos(HEART));
        var terrain = new QuietBelowEcology.Adapter(world); for (int attempt = 0; attempt < 20 && !engine.view().indexed(); attempt++) engine.index(terrain);
        check(engine.view().indexed() && engine.cells().size() >= 256, "bounded Forge source index did not complete");
        var rules = engine.vitality().rules(); engine.tuneVitality(new EcologyVitality.Rules(rules.lossMinutes(), rules.recoveryMinutes(), Math.max(0, rules.extentPreset() - 2))); engine.mode(true);
        ChronicleRuntime.service(source.getServer()).start(new Actor(player.getName().getString(), player.getUuidAsString()), null); PlayRuntime.setSelfParticipant(player, true); authoring.disable(player.getUuid());
        player.interactionManager.changeGameMode(GameMode.SURVIVAL); player.teleport(world, 3.5, 66, 3.5, java.util.Set.of(), 0, 0, false);
        suppliedInputs(player); check(ChronicleRuntime.service(source.getServer()).state().phase().name().equals("ACTIVE"), "Core session is not active");
    }

    private static void ready(ServerWorld world) { check(QuietBelowEcology.stationPower(world, FORGE) == null, "Forge is not physically powered: " + QuietBelowEcology.stationPower(world, FORGE)); }

    private static void feedingAge(ServerWorld world, ServerPlayerEntity player) throws Exception {
        var engine=QuietBelowEcology.engineForTest(world.getServer());var rules=engine.vitality().rules();int speed=engine.speedPercent();
        BlockPos support=FORGE.down();
        check(world.getBlockState(support).isOf(Blocks.SCULK),"missing single Forge support");
        for(Direction direction:Direction.values())if(direction!=Direction.UP)
            check(!world.getBlockState(support.offset(direction)).isAir(),"support has a direct air outlet; regression invalid");
        ready(world);
        var first=staged(world,player,blade(),new ItemStack(Items.ECHO_SHARD));
        ItemEnchantingRuntime.take(player,take(first,first.view(),0,0));
        check(sharpness(world,first.getCursorStack())==1,"first real Forge enchant failed");close(player);
        try {
            engine.speed(25);engine.tuneVitality(new EcologyVitality.Rules(1,1,rules.extentPreset()));
            var terrain=new QuietBelowEcology.Adapter(world);
            // Accelerate ecology time only: this exceeds a full support loss duration.
            for(int n=0;n<1500;n++){engine.beginTick();engine.tick(terrain,true);}
            engine.flushVitality();
            check(engine.vitality().mode(QuietBelowEcology.pos(support))==EcologyVitality.Mode.SHADED,"open Forge sealed or lit its support");
            check(engine.living(QuietBelowEcology.pos(support)),"Forge starved its own support after one craft");
            check(engine.vitality().percent(QuietBelowEcology.pos(support))==100,"open Forge support lost vitality");
            ready(world);
            var second=staged(world,player,blade(),new ItemStack(Items.ECHO_SHARD));
            ItemEnchantingRuntime.take(player,take(second,second.view(),0,0));
            check(sharpness(world,second.getCursorStack())==1,"second real Forge enchant failed after aged support");close(player);
            check(QuietBelowEcology.stationPower(world,FORGE)==null,"second craft consumed station power");
        } finally {close(player);engine.tuneVitality(rules);engine.speed(speed);}
    }

    /** Real ecology-driven model changes, not posed heat values. Only disposable native worlds register these commands. */
    private static void model(ServerWorld world, ServerPlayerEntity player, String verb) throws Exception {
        close(player);
        var engine = QuietBelowEcology.engineForTest(world.getServer());
        var actor = new Actor(player.getName().getString(), player.getUuidAsString());
        switch (verb) {
            case "model_off", "model_kindling", "model_active" -> {
                int preset = verb.equals("model_off") ? 4 : verb.equals("model_kindling") ? 3 : 0;
                var rules = engine.vitality().rules();
                engine.tuneVitality(new EcologyVitality.Rules(rules.lossMinutes(), rules.recoveryMinutes(), preset));
                expectedHeat = verb.equals("model_off") ? 0 : verb.equals("model_kindling") ? 1 : 2;
                if (verb.equals("model_off")) {
                    engine.speed(25);
                    world.setTimeOfDay(18000);
                    put(world, new BlockPos(7, 65, 9), Blocks.STONE.getDefaultState());
                    player.teleport(world, 7.5, 66, 9.5, java.util.Set.of(), 140, 18, false);
                }
            }
            case "model_cut" -> { put(world, CONTACT, Blocks.AIR.getDefaultState()); expectedHeat = 0; }
            case "model_reconnect" -> { put(world, CONTACT, Blocks.SCULK.getDefaultState()); expectedHeat = 2; }
            case "model_isolated" -> {
                isolatedNeighbors.clear();
                for (Direction direction : Direction.values()) {
                    BlockPos at = FORGE.offset(direction); isolatedNeighbors.put(at, world.getBlockState(at));
                    put(world, at, Blocks.GLASS.getDefaultState());
                }
                expectedHeat = 0;
            }
            case "model_adjacent" -> {
                for (var entry : isolatedNeighbors.entrySet()) put(world, entry.getKey(), entry.getValue());
                isolatedNeighbors.clear(); expectedHeat = 2;
            }
            case "model_disable" -> { engine.mode(false); expectedHeat = 0; }
            case "model_enable" -> { engine.mode(true); expectedHeat = 2; }
            case "model_saturated_pause" -> {
                var trackers = modelTrackers();
                var queue = trackers.computeIfAbsent(world.getServer(), server -> new java.util.LinkedHashSet<>());
                savedModelTracker = new java.util.LinkedHashSet<>(queue);
                var forge = (AncientForgeBlockEntity) world.getBlockEntity(FORGE);
                check(forge != null && world.getBlockState(FORGE).get(AncientForgeBlock.HEAT) == 2, "saturated test requires an actual active Forge");
                queue.clear();
                // Unloaded identities exercise the bounded queue/pruning path without placing extra blocks or loading chunks.
                for (int i = 0; i < 1023; i++) queue.add(new AncientForgeBlockEntity(new BlockPos(10000 + i, 65, 10000), QuietBelow.ANCIENT_FORGE_BLOCK.getDefaultState()));
                queue.add(forge);
                saturatedAt = world.getServer().getTicks();
                ChronicleRuntime.service(world.getServer()).pause(actor); expectedHeat = 0;
            }
            case "model_pause" -> { ChronicleRuntime.service(world.getServer()).pause(actor); expectedHeat = 0; }
            case "model_resume" -> {
                ChronicleRuntime.service(world.getServer()).resume(actor); expectedHeat = 2;
                world.setTimeOfDay(1000);
                player.teleport(world, 3.5, 66, 3.5, java.util.Set.of(), 0, 0, false);
            }
            default -> throw new IllegalArgumentException("Unknown model fixture");
        }
        check(engine.setup() != null, "model fixture lost source setup");
    }
    @SuppressWarnings("unchecked")
    private static java.util.Map<MinecraftServer, java.util.LinkedHashSet<AncientForgeBlockEntity>> modelTrackers() throws ReflectiveOperationException {
        var field = AncientForges.class.getDeclaredField("LOADED"); field.setAccessible(true);
        return (java.util.Map<MinecraftServer, java.util.LinkedHashSet<AncientForgeBlockEntity>>) field.get(null);
    }
    private static void modelCheck(ServerWorld world) throws Exception {
        var state = world.getBlockState(FORGE);
        check(state.isOf(QuietBelow.ANCIENT_FORGE_BLOCK), "model fixture lost the Forge");
        check(state.get(AncientForgeBlock.HEAT) == expectedHeat, "real block model did not follow readiness");
        check(QuietBelowEcology.forgeHeat(world, FORGE) == expectedHeat, "appearance disagrees with the physical readiness proof");
        check((QuietBelowEcology.stationPower(world, FORGE) == null) == (expectedHeat == 2), "model readiness and crafting power disagree");
        check(state.getLuminance() == (expectedHeat == 2 ? 12 : 0), "wrong Forge luminance");
        int light = world.getLightLevel(net.minecraft.world.LightType.BLOCK, FORGE.up());
        check(expectedHeat == 2 ? light >= 10 : light == 0, "native block light did not update with the model: " + light);
        if (savedModelTracker != null) {
            long elapsed = world.getServer().getTicks() - saturatedAt;
            check(elapsed >= 100 && elapsed <= AncientForges.MAX_REFRESH_TICKS + 40, "saturated model refresh exceeded its bounded poll/delivery window: " + elapsed);
            var queue = modelTrackers().get(world.getServer()); queue.clear(); queue.addAll(savedModelTracker); savedModelTracker = null;
        }
    }

    /** Opens an empty station. The known hotbar inputs let the real client exercise native staging. */
    private static void offer(ServerWorld world, ServerPlayerEntity player) {
        close(player); suppliedInputs(player); int levels = player.experienceLevel; AncientForges.open(player, FORGE);
        var h = handler(player); check(h.target().isEmpty() && h.material().isEmpty(), "Forge must open with real empty staging slots");
        check(h.getSlot(30).getStack().isOf(Items.IRON_SWORD) && h.getSlot(31).getStack().isOf(Items.ECHO_SHARD), "supplied test inputs are not in hotbar slots 0/1");
        check(player.experienceLevel == levels, "opening Forge changed XP");
    }

    /** Server-side matrix complements actual client clicks: authoritative Take variants, stale state and recovery. */
    private static void transactions(ServerWorld world, ServerPlayerEntity player) throws Exception {
        ItemEnchantingScreenHandler h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD, 3));
        ItemEnchantingPayloads.View view = h.view(); ItemEnchantingRuntime.take(player, take(h, view, 0, 0));
        ItemStack result = h.getCursorStack(); check(sharpness(world, result) == 1 && "Trial blade".equals(result.getName().getString()) && result.getOrDefault(DataComponentTypes.DAMAGE, 0) == 17, "cursor result lost target components");
        check(h.target().isEmpty() && h.material().getCount() == 2, "valid take did not consume exact staged inputs");
        ItemEnchantingRuntime.take(player, take(h, view, 0, 0)); check(h.material().getCount() == 2, "duplicate token changed inputs"); h.setCursorStack(ItemStack.EMPTY); close(player);

        // Right-click uses the same cursor destination, then direct hotbar and offhand result gestures.
        h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); ItemEnchantingRuntime.take(player, take(h, h.view(), 0, 1));
        check(sharpness(world, h.getCursorStack()) == 1 && h.material().isEmpty(), "right-click result did not use the authoritative cursor delivery"); h.setCursorStack(ItemStack.EMPTY); close(player);
        player.getInventory().setStack(8, ItemStack.EMPTY); h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); ItemEnchantingRuntime.take(player, take(h, h.view(), 2, 8));
        check(sharpness(world, player.getInventory().getStack(8)) == 1 && h.target().isEmpty(), "hotbar result delivery failed"); close(player);
        player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY); h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); ItemEnchantingRuntime.take(player, take(h, h.view(), 2, 40));
        check(sharpness(world, player.getOffHandStack()) == 1 && h.target().isEmpty(), "offhand result delivery failed"); player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY); close(player);

        h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); h.target().setCount(2); ItemEnchantingRuntime.changed(player, h); check(h.view().state() == ItemEnchantingPayloads.State.INCOMPATIBLE, "stacked target must be incompatible"); close(player);
        h = staged(world, player, blade(), new ItemStack(Items.FLINT)); check(h.view().state() != ItemEnchantingPayloads.State.READY, "wrong material became ready"); close(player);
        ItemStack already = blade(); already.addEnchantment(sharpnessEntry(world), 1); h = staged(world, player, already, new ItemStack(Items.ECHO_SHARD)); check(h.view().state() == ItemEnchantingPayloads.State.UNCHANGED, "already enchanted blade became ready"); close(player);
        h = staged(world, player, new ItemStack(Items.BOOK), new ItemStack(Items.ECHO_SHARD)); ItemEnchantingRuntime.take(player, take(h, h.view(), 1, 0)); check(hasStoredSharpness(world, player), "book output was not delivered to player inventory"); close(player);

        // No native SlotActionType may mint the derived result. Only the nonce-bound Take can commit.
        h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); ItemStack targetBefore = h.target().copy(), materialBefore = h.material().copy(), cursorBefore = h.getCursorStack().copy(), previewBefore = h.getSlot(2).getStack().copy();
        for (SlotActionType action : SlotActionType.values()) h.onSlotClick(2, 0, action, player);
        h.quickMove(player, 2);
        check(ItemStack.areEqual(targetBefore, h.target()) && ItemStack.areEqual(materialBefore, h.material()) && ItemStack.areEqual(cursorBefore, h.getCursorStack()) && ItemStack.areEqual(previewBefore, h.getSlot(2).getStack()), "native result slot action minted, consumed, or changed preview state"); close(player);

        // An input component mutation is a stale authority snapshot, even if the item type/count match.
        h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); ItemEnchantingPayloads.View staleComponents = h.view(); h.target().set(DataComponentTypes.DAMAGE, 18);
        ItemEnchantingRuntime.take(player, take(h, staleComponents, 0, 0)); check(!h.target().isEmpty() && h.material().getCount() == 1 && h.getCursorStack().isEmpty(), "component-stale preview consumed staged inputs"); close(player);

        for (int destination : List.of(1, 2, 3)) {
            h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); ItemEnchantingPayloads.View token = h.view(); int button = destination == 2 ? 8 : 0;
            if (destination == 1) for (int slot = 3; slot < 39; slot++) h.getSlot(slot).setStack(new ItemStack(Items.STONE, 64));
            if (destination == 2) player.getInventory().setStack(8, new ItemStack(Items.STONE, 64));
            ItemEnchantingRuntime.take(player, take(h, token, destination, button));
            if (destination != 3) check(!h.target().isEmpty(), "blocked destination consumed staged target"); close(player);
        }
        h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); ItemEnchantingPayloads.View stale = h.view();
        put(world, CONTACT, Blocks.AIR.getDefaultState()); ItemEnchantingRuntime.take(player, take(h, stale, 0, 0)); check(!h.target().isEmpty(), "cut network consumed staged target"); put(world, CONTACT, Blocks.SCULK.getDefaultState()); close(player);
        h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); put(world, FORGE, Blocks.AIR.getDefaultState()); check(!ItemEnchantingRuntime.canUse(player, h), "replacement retained station authority"); close(player); put(world, FORGE, QuietBelow.ANCIENT_FORGE_BLOCK.getDefaultState()); suppliedInputs(player);

        // Typed gate failures retain real staged inputs. The client sees their current State; Take cannot bypass it.
        h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); ItemEnchantingPayloads.View paused = h.view(); ChronicleRuntime.service(world.getServer()).pause(new Actor(player.getName().getString(), player.getUuidAsString()));
        ItemEnchantingRuntime.take(player, take(h, paused, 0, 0)); retained(h, "paused Forge consumed staged input"); ChronicleRuntime.service(world.getServer()).resume(new Actor(player.getName().getString(), player.getUuidAsString())); close(player);
        h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); ItemEnchantingPayloads.View creative = h.view(); player.interactionManager.changeGameMode(GameMode.CREATIVE);
        ItemEnchantingRuntime.take(player, take(h, creative, 0, 0)); retained(h, "Creative Forge consumed staged input"); player.interactionManager.changeGameMode(GameMode.SURVIVAL); close(player);
        h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); ItemEnchantingPayloads.View away = h.view(); player.teleport(world, 30.5, 66, 3.5, java.util.Set.of(), 0, 0, false);
        ItemEnchantingRuntime.take(player, take(h, away, 0, 0)); retained(h, "distant Forge consumed staged input"); player.teleport(world, 3.5, 66, 3.5, java.util.Set.of(), 0, 0, false); close(player);
        h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); ItemEnchantingPayloads.View held = h.view(); PlayRuntime.setSelfParticipant(player, false);
        ItemEnchantingRuntime.take(player, take(h, held, 0, 0)); retained(h, "non-participant Forge consumed staged input"); PlayRuntime.setSelfParticipant(player, true); close(player);

        // Connection/session/sync/token authority belongs to the actor's current handler only.
        h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); ItemEnchantingPayloads.View authority = h.view();
        ItemEnchantingRuntime.take(player, new ItemEnchantingPayloads.Take(h.syncId + 1, authority.session(), authority.token(), 0, 0)); retained(h, "wrong sync accepted Forge result");
        ItemEnchantingRuntime.take(player, new ItemEnchantingPayloads.Take(h.syncId, java.util.UUID.randomUUID(), authority.token(), 0, 0)); retained(h, "wrong session accepted Forge result");
        ItemEnchantingRuntime.take(player, new ItemEnchantingPayloads.Take(h.syncId, authority.session(), java.util.UUID.randomUUID(), 0, 0)); retained(h, "wrong token accepted Forge result");
        ServerPlayerEntity second = world.getServer().getPlayerManager().getPlayer("ChronicleFabricB"); check(second != null, "real second client must be connected for foreign Forge authority");
        ItemEnchantingRuntime.take(second, take(h, authority, 0, 0)); retained(h, "foreign actor accepted Forge result"); close(player);

        reloadCases(world, player);

        // Full inventory close and repeated close keep real staged inputs recoverable exactly once.
        for (int slot = 0; slot < player.getInventory().size(); slot++) player.getInventory().setStack(slot, new ItemStack(Items.STONE, 64));
        int droppedBlades = dropped(world, player, false), droppedEchoes = dropped(world, player, true);
        h = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); close(player); check(h.settled(), "close did not settle full-inventory handler"); h.onClosed(player); check(h.settled(), "idempotent close reopened settlement");
        check(dropped(world, player, false) == droppedBlades + 1 && dropped(world, player, true) == droppedEchoes + 1, "full inventory/idempotent close did not recover exactly one of each staged input");
        var returned = world.getEntitiesByClass(ItemEntity.class, new Box(player.getBlockPos()).expand(5), item -> !item.getStack().isEmpty());
        check(returned.stream().anyMatch(item -> "Trial blade".equals(item.getStack().getName().getString()) && item.getStack().getOrDefault(DataComponentTypes.DAMAGE, 0) == 17), "full-inventory close lost staged target instead of returning/dropping it");
        check(returned.stream().anyMatch(item -> item.getStack().isOf(Items.ECHO_SHARD) && item.getStack().getCount() == 1), "full-inventory close lost staged material instead of returning/dropping it");
        suppliedInputs(player);
    }

    /** Real handler regression for recipe/cap reload: old nonce is stale, count2 applies and levelIII is capped atII. */
    private static void reloadCases(ServerWorld world, ServerPlayerEntity player) throws Exception {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("chronicle"), recipes = configDir.resolve("ingredient-enchantments.json"), caps = configDir.resolve("enchantment-caps.json");
        byte[] oldRecipes = Files.readAllBytes(recipes), oldCaps = Files.readAllBytes(caps); var actor = new Actor(player.getName().getString(), player.getUuidAsString());
        try {
            ItemEnchantingScreenHandler old = stagedCore(player, blade(), new ItemStack(Items.ECHO_SHARD, 3)); ItemEnchantingPayloads.View oldView = old.view();
            ChronicleRuntime.service(world.getServer()).pause(actor); AuthoringRuntime.get(world.getServer()).enable(player.getUuid());
            var changed = IngredientEnchantmentConfig.decode(new String(oldRecipes, java.nio.charset.StandardCharsets.UTF_8)).stream().map(recipe -> recipe.ingredient().equals("minecraft:echo_shard") ? new IngredientEnchantmentRecipe(recipe.id(), recipe.group(), recipe.ingredient(), recipe.enchantment(), 3, 2, true) : recipe).toList();
            Files.writeString(recipes, IngredientEnchantmentConfig.encode(changed)); GameSetupTools.execute(player.getCommandSource(), "chronicle_core:item_enchanting", "reload");
            Files.writeString(caps, EnchantmentCapConfig.encode(new EnchantmentCaps(true, 1, Map.of("minecraft:sharpness", 2)))); GameSetupTools.execute(player.getCommandSource(), "chronicle_core:enchantment_caps", "reload");
            AuthoringRuntime.get(world.getServer()).disable(player.getUuid()); ChronicleRuntime.service(world.getServer()).resume(actor);
            ItemEnchantingRuntime.take(player, take(old, oldView, 0, 0)); check(!old.target().isEmpty() && old.material().isOf(Items.ECHO_SHARD) && old.material().getCount() == 3 && old.getCursorStack().isEmpty(), "recipe/cap reload accepted stale result token"); close(player);
            ItemEnchantingScreenHandler shortPrice = stagedCore(player, blade(), new ItemStack(Items.ECHO_SHARD)); check(shortPrice.view().state() == ItemEnchantingPayloads.State.SHORT_MATERIAL, "count2 recipe did not reject one echo shard"); close(player);
            ItemEnchantingScreenHandler capped = stagedCore(player, blade(), new ItemStack(Items.ECHO_SHARD, 2)); ItemEnchantingRuntime.take(player, take(capped, capped.view(), 0, 0));
            check(sharpness(world, capped.getCursorStack()) == 2 && capped.material().isEmpty(), "levelIII count2 recipe did not obey capII/exact consumption"); capped.setCursorStack(ItemStack.EMPTY); close(player);
        } finally {
            if (ChronicleRuntime.service(world.getServer()).state().phase().name().equals("ACTIVE")) ChronicleRuntime.service(world.getServer()).pause(actor);
            AuthoringRuntime.get(world.getServer()).enable(player.getUuid()); Files.write(recipes, oldRecipes); Files.write(caps, oldCaps);
            GameSetupTools.execute(player.getCommandSource(), "chronicle_core:item_enchanting", "reload"); GameSetupTools.execute(player.getCommandSource(), "chronicle_core:enchantment_caps", "reload");
            AuthoringRuntime.get(world.getServer()).disable(player.getUuid()); ChronicleRuntime.service(world.getServer()).resume(actor);
        }
    }

    /** Called after the client does a real result-slot click; asserts its exact persistent outcome. */
    private static void checkCompleted(ServerWorld world, ServerPlayerEntity player) {
        ItemStack found = player.currentScreenHandler instanceof ItemEnchantingScreenHandler h && sharpness(world, h.getCursorStack()) == 1 ? h.getCursorStack() : findSharpness(world, player); check(!found.isEmpty(), "native result click did not deliver Sharpness I output");
        check("Trial blade".equals(found.getName().getString()) && found.getOrDefault(DataComponentTypes.DAMAGE, 0) == 17, "native output lost name/damage"); check(QuietBelowEcology.stationPower(world, FORGE) == null, "Forge lost power during accepted transaction");
    }

    private static ItemEnchantingScreenHandler staged(ServerWorld world, ServerPlayerEntity player, ItemStack target, ItemStack material) {
        close(player); AncientForges.open(player, FORGE); ItemEnchantingScreenHandler h = handler(player); h.getSlot(0).setStack(target.copy()); h.getSlot(1).setStack(material.copy()); ItemEnchantingRuntime.changed(player, h);
        check(h.view().state() != ItemEnchantingPayloads.State.EMPTY_TARGET && h.view().state() != ItemEnchantingPayloads.State.EMPTY_MATERIAL, "staged slots did not refresh"); return h;
    }

    /** Isolate Core recipe/cap checks from the campaign's deliberately asynchronous connectivity/migration holds. */
    private static ItemEnchantingScreenHandler stagedCore(ServerPlayerEntity player, ItemStack target, ItemStack material) {
        close(player);
        ItemEnchantingRuntime.open(player, AncientForges.GROUP, "Ancient Forge", actor -> ItemEnchantingPayloads.State.READY);
        var h = handler(player);
        h.getSlot(0).setStack(target.copy());
        h.getSlot(1).setStack(material.copy());
        ItemEnchantingRuntime.changed(player, h);
        return h;
    }

    private static ItemEnchantingPayloads.Take take(ItemEnchantingScreenHandler h, ItemEnchantingPayloads.View v, int destination, int button) { return new ItemEnchantingPayloads.Take(h.syncId, v.session(), v.token(), destination, button); }
    private static ItemEnchantingScreenHandler handler(ServerPlayerEntity player) { check(player.currentScreenHandler instanceof ItemEnchantingScreenHandler, "Forge did not open the current real-slot handler"); return (ItemEnchantingScreenHandler) player.currentScreenHandler; }
    private static void close(ServerPlayerEntity player) { if (player.currentScreenHandler instanceof ItemEnchantingScreenHandler) player.closeHandledScreen(); }
    private static void suppliedInputs(ServerPlayerEntity player) { player.getInventory().setStack(0, blade()); player.getInventory().setStack(1, new ItemStack(Items.ECHO_SHARD, 3)); player.getInventory().setSelectedSlot(0); player.playerScreenHandler.syncState(); }
    private static ItemStack blade() { ItemStack sword = new ItemStack(Items.IRON_SWORD); sword.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Trial blade")); sword.set(DataComponentTypes.DAMAGE, 17); return sword; }

    private static void save(ServerCommandSource source, ServerWorld world, ServerPlayerEntity player) throws Exception {
        close(player); ItemStack result = findSharpness(world, player); check(!result.isEmpty(), "save requires the final named Sharpness I target"); check(world.getBlockEntity(FORGE) instanceof AncientForgeBlockEntity, "Forge block entity missing before save");
        Path receipt = source.getServer().getSavePath(WorldSavePath.ROOT).resolve("quiet-below/forge-trial.json"); Files.createDirectories(receipt.getParent()); Files.writeString(receipt, "{\"sharpnessI\":true,\"name\":\"Trial blade\",\"forge\":\"2,65,3\",\"realSlots\":true}");
        player.interactionManager.changeGameMode(GameMode.SURVIVAL); player.getAbilities().flying = false; player.sendAbilitiesUpdate(); player.teleport(world, 3.5, 66, 3.5, java.util.Set.of(), 0, 0, false);
        player.getInventory().setStack(6, ItemStack.EMPTY); player.getInventory().setStack(7, ItemStack.EMPTY);
        ItemEnchantingScreenHandler staged = staged(world, player, blade(), new ItemStack(Items.ECHO_SHARD)); check(!staged.target().isEmpty() && !staged.material().isEmpty(), "disconnect/restart fixture requires live staged Forge inputs");
        ChronicleRuntime.service(source.getServer()).pause(new Actor(player.getName().getString(), player.getUuidAsString()));
    }

    private static void restart(ServerCommandSource source, ServerWorld world, ServerPlayerEntity player) throws Exception {
        String saved = Files.readString(source.getServer().getSavePath(WorldSavePath.ROOT).resolve("quiet-below/forge-trial.json")); check(saved.contains("sharpnessI") && saved.contains("realSlots"), "Forge receipt evidence missing after restart"); check(world.getBlockEntity(FORGE) instanceof AncientForgeBlockEntity, "Forge block entity did not survive restart"); check(!findSharpness(world, player).isEmpty(), "saved component-preserving Forge output did not survive restart");
        check(plainBlades(world, player) == 1 && echoCount(player) == 3, "disconnect/restart did not recover exactly one staged blade and echo shard");
        if (ChronicleRuntime.service(source.getServer()).state().phase().name().equals("ACTIVE")) ChronicleRuntime.service(source.getServer()).pause(new Actor(player.getName().getString(), player.getUuidAsString()));
    }

    private static void render(ServerWorld world, ServerPlayerEntity player) {
        int index = 0; for (Direction facing : Direction.Type.HORIZONTAL) for (int heat = 0; heat <= 2; heat++) { BlockPos at = new BlockPos(8 + (index % 4) * 3, 65, 8 + (index++ / 4) * 3); put(world, at, QuietBelow.ANCIENT_FORGE_BLOCK.getDefaultState().with(AncientForgeBlock.FACING, facing).with(AncientForgeBlock.HEAT, heat)); VoxelShape collision = world.getBlockState(at).getCollisionShape(world, at); var box = collision.getBoundingBox(); check(box.minX == 0 && box.minY == 0 && box.minZ == 0 && box.maxX == 1 && box.maxY == 1 && box.maxZ == 1, "Forge collision must be its explicit base cube"); }
        player.interactionManager.changeGameMode(GameMode.CREATIVE); player.getAbilities().flying = true; player.sendAbilitiesUpdate(); player.teleport(world, 22, 70, 23, java.util.Set.of(), 140, 24, false);
    }

    private static ItemStack findSharpness(ServerWorld world, ServerPlayerEntity player) { for (int slot = 0; slot < player.getInventory().size(); slot++) { ItemStack stack = player.getInventory().getStack(slot); if (sharpness(world, stack) == 1) return stack; } return ItemStack.EMPTY; }
    private static boolean hasStoredSharpness(ServerWorld world, ServerPlayerEntity player) { for (int slot = 0; slot < player.getInventory().size(); slot++) if (player.getInventory().getStack(slot).getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).getLevel(sharpnessEntry(world)) == 1) return true; return false; }
    private static void retained(ItemEnchantingScreenHandler handler, String message) { check(!handler.target().isEmpty() && handler.material().isOf(Items.ECHO_SHARD) && handler.material().getCount() == 1 && handler.getCursorStack().isEmpty(), message); }
    private static int plainBlades(ServerWorld world, ServerPlayerEntity player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if ("Trial blade".equals(stack.getName().getString()) && stack.getOrDefault(DataComponentTypes.DAMAGE, 0) == 17 && sharpness(world, stack) == 0) count += stack.getCount();
        }
        return count;
    }
    private static int echoCount(ServerPlayerEntity player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) if (player.getInventory().getStack(slot).isOf(Items.ECHO_SHARD)) count += player.getInventory().getStack(slot).getCount();
        return count;
    }
    private static int dropped(ServerWorld world, ServerPlayerEntity player, boolean echoes) {
        return world.getEntitiesByClass(ItemEntity.class, new Box(player.getBlockPos()).expand(5), item -> {
            ItemStack stack = item.getStack();
            return echoes ? stack.isOf(Items.ECHO_SHARD) : "Trial blade".equals(stack.getName().getString()) && stack.getOrDefault(DataComponentTypes.DAMAGE, 0) == 17 && sharpness(world, stack) == 0;
        }).stream().mapToInt(item -> item.getStack().getCount()).sum();
    }
    private static RegistryEntry<Enchantment> sharpnessEntry(ServerWorld world) { return world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(net.minecraft.registry.RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of("minecraft:sharpness"))); }
    private static int sharpness(ServerWorld world, ItemStack stack) { return stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).getLevel(sharpnessEntry(world)); }
    private static void put(ServerWorld world, BlockPos pos, net.minecraft.block.BlockState state) { check(world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4), "fixture chunk is not loaded"); world.setBlockState(pos, state, Block.NOTIFY_LISTENERS); }
    private static void requireFixture(ServerCommandSource source) throws Exception { MinecraftServer server = source.getServer(); Path root = server.getRunDirectory().toRealPath(); check(server.isDedicated() && !server.isOnlineMode() && server.getServerPort() == 25575 && "127.0.0.1".equals(server.getServerIp()), "trial requires offline loopback 127.0.0.1:25575"); check(root.getParent() != null && "test-runs".equals(root.getParent().getFileName().toString()) && RUN.matcher(root.getFileName().toString()).matches(), "trial requires a disposable test-runs timestamp directory"); check(source.getPlayerOrThrow().hasPermissionLevel(2), "trial requires operator level 2"); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); checks++; }
    private static String detail(Exception error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
}
