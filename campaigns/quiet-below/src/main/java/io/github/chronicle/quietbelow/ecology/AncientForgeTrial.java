package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.enchanting.EnchantmentCaps;
import io.github.chronicle.core.enchanting.IngredientEnchantmentConfig;
import io.github.chronicle.core.network.ItemEnchantingPayloads;
import io.github.chronicle.core.server.AuthoringRuntime;
import io.github.chronicle.core.server.ChronicleRuntime;
import io.github.chronicle.core.server.ItemEnchantingRuntime;
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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static net.minecraft.server.command.CommandManager.literal;

/** Guarded disposable native evidence fixture for the Ancient Forge; never production gameplay. */
public final class AncientForgeTrial {
    private static final BlockPos HEART = new BlockPos(-4, 64, 0), CONTACT = new BlockPos(-3, 64, 0);
    private static final BlockPos FORGE = new BlockPos(2, 65, 3), SAFE = new BlockPos(3, 65, 3);
    private static final String SOURCE = "forge_trial_source", ROOT = "forge_trial_heart";
    private static final Pattern RUN = Pattern.compile("[0-9]{8}T[0-9]{12,}Z");
    private static int checks;

    private AncientForgeTrial() { }

    public static void register() {
        if (!"true".equals(System.getenv("CHRONICLE_FORGE_NATIVE_TRIAL"))) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var root = literal("quiet_below_forge_trial").requires(source -> source.hasPermissionLevel(2));
            for (String verb : List.of("prepare", "ready", "transactions", "offer", "check", "save", "restart", "render", "itemrender")) {
                root.then(literal(verb).executes(context -> run(context.getSource(), verb)));
            }
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
                case "ready" -> ready(world);
                case "transactions" -> transactions(world, player);
                case "offer" -> offer(world, player);
                case "check" -> check(world, player);
                case "save" -> save(source, world, player);
                case "restart" -> restart(source, world, player);
                case "render" -> render(world, player);
                case "itemrender" -> {player.getInventory().setStack(8,new ItemStack(QuietBelow.ANCIENT_FORGE));player.getInventory().setSelectedSlot(8);player.playerScreenHandler.syncState();check(player.getMainHandStack().isOf(QuietBelow.ANCIENT_FORGE),"Forge inventory/held model fixture missing");}
                default -> throw new IllegalArgumentException("Unknown Forge trial verb");
            }
            int count = checks;
            source.sendFeedback(() -> Text.literal("PASS Forge " + verb + " (" + count + " checks)"), false);
            return 1;
        } catch (Exception error) {
            QuietBelow.LOGGER.error("Ancient Forge native trial failed: " + verb, error);
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
        put(world, FORGE, QuietBelow.ANCIENT_FORGE_BLOCK.getDefaultState());
        put(world, SAFE, Blocks.STONE.getDefaultState());
        check(world.getBlockEntity(FORGE) instanceof AncientForgeBlockEntity, "Forge block entity was not created");

        AuthoringRuntime authoring = AuthoringRuntime.get(source.getServer());
        authoring.enable(player.getUuid());
        String dimension = world.getRegistryKey().getValue().toString();
        AuthoringSite.Region region = (AuthoringSite.Region) authoring.service().put(new AuthoringSite.Region(SOURCE, dimension, 1,
                new BlockPosition(-4, 64, 0), new BlockPosition(15, 64, 15)), false);
        AuthoringSite.Anchor heart = (AuthoringSite.Anchor) authoring.service().put(new AuthoringSite.Anchor(ROOT, dimension, 1,
                new BlockPosition(-4, 64, 0), "minecraft:reinforced_deepslate"), false);
        MapSiteAssignments.assign(source, "quiet_below:source_city", region.id());
        MapSiteAssignments.assign(source, "quiet_below:relic_heart", heart.id());
        EcologyEngine engine = QuietBelowEcology.engineForTest(source.getServer());
        EcologyEngine.Setup setup = new EcologyEngine.Setup(region.id(), region.revision(), heart.id(), heart.revision(), dimension);
        engine.configure(setup, new EcologyEngine.Area(new EcologyTerrain.Pos(-4, 64, 0), new EcologyTerrain.Pos(15, 64, 15)), QuietBelowEcology.pos(HEART));
        QuietBelowEcology.Adapter terrain = new QuietBelowEcology.Adapter(world);
        for (int attempt = 0; attempt < 20 && !engine.view().indexed(); attempt++) engine.index(terrain);
        check(engine.view().indexed() && engine.cells().size() >= 256, "bounded Forge source index did not complete");
        var rules = engine.vitality().rules();
        engine.tuneVitality(new EcologyVitality.Rules(rules.lossMinutes(), rules.recoveryMinutes(), Math.max(0, rules.extentPreset() - 2)));
        engine.mode(true);
        ChronicleRuntime.service(source.getServer()).start(new Actor(player.getName().getString(), player.getUuidAsString()), null);
        PlayRuntime.setSelfParticipant(player, true);
        authoring.disable(player.getUuid());
        player.interactionManager.changeGameMode(GameMode.SURVIVAL);
        player.teleport(world, 3.5, 66, 3.5, java.util.Set.of(), 0, 0, false);
        check(QuietBelowEcology.engineForTest(source.getServer()).setup() != null, "ecology did not configure");
        check(ChronicleRuntime.service(source.getServer()).state().phase().name().equals("ACTIVE"), "Core session is not active");
    }

    /** Invoke after the caller has allowed at least 100 server ticks for the bounded index/tick work. */
    private static void ready(ServerWorld world) {
        check(QuietBelowEcology.stationPower(world, FORGE) == null, "Forge is not physically powered: " + QuietBelowEcology.stationPower(world, FORGE));
    }

    private static void offer(ServerWorld world, ServerPlayerEntity player) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Trial blade"));
        sword.set(DataComponentTypes.DAMAGE, 17);
        player.setStackInHand(Hand.MAIN_HAND, sword);
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.ECHO_SHARD, 3));
        int levels = player.experienceLevel;
        AncientForges.open(player, FORGE);
        check(token(player) != null, "Forge did not issue an offer; matching client payload support is required");
        check(player.experienceLevel == levels, "opening Forge changed XP");
    }

    private static void check(ServerWorld world, ServerPlayerEntity player) {
        ItemStack sword = player.getMainHandStack();
        RegistryEntry<Enchantment> sharpness = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT)
                .getOrThrow(net.minecraft.registry.RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of("minecraft:sharpness")));
        ItemEnchantmentsComponent enchantments = sword.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        check(enchantments.getLevel(sharpness) == 1, "Forge did not grant Sharpness I");
        check("Trial blade".equals(sword.getName().getString()), "Forge did not retain custom name");
        check(sword.getOrDefault(DataComponentTypes.DAMAGE, 0) == 17, "Forge did not retain durability damage");
        check(player.getOffHandStack().isOf(Items.ECHO_SHARD) && player.getOffHandStack().getCount() == 2, "Forge did not consume exactly one echo shard");
        check(QuietBelowEcology.stationPower(world, FORGE) == null, "Forge lost power during accepted transaction");
    }

    private static void transactions(ServerWorld world, ServerPlayerEntity player) throws Exception {
        int initialXp = player.experienceLevel;
        for(int slot=0;slot<36;slot++)player.getInventory().setStack(slot,new ItemStack(Items.STONE,64));
        offer(world, player);
        UUID first = token(player);
        ItemEnchantingRuntime.act(player, new ItemEnchantingPayloads.Action(first, 0));
        check(player.getOffHandStack().getCount() == 2, "accepted offer did not consume one ingredient");
        check(player.experienceLevel == initialXp, "Forge changed XP");
        for(int slot=0;slot<36;slot++)if(slot!=player.getInventory().getSelectedSlot())
            check(player.getInventory().getStack(slot).isOf(Items.STONE)&&player.getInventory().getStack(slot).getCount()==64,"full-inventory craft changed an unrelated slot");
        ItemStack after = player.getMainHandStack().copy();
        ItemEnchantingRuntime.act(player, new ItemEnchantingPayloads.Action(first, 0));
        check(ItemStack.areEqual(after, player.getMainHandStack()) && player.getOffHandStack().getCount() == 2, "one-use token duplicated transaction");
        AncientForges.open(player, FORGE);
        UUID already = token(player);
        ItemEnchantingRuntime.act(player, new ItemEnchantingPayloads.Action(already, 0));
        check(ItemStack.areEqual(after, player.getMainHandStack()) && player.getOffHandStack().getCount() == 2, "already sufficient target consumed input");

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD, 2));
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.ECHO_SHARD, 2));
        AncientForges.open(player, FORGE);
        UUID stacked = token(player);
        ItemEnchantingRuntime.act(player, new ItemEnchantingPayloads.Action(stacked, 0));
        check(player.getMainHandStack().getCount() == 2 && player.getOffHandStack().getCount() == 2, "stacked target consumed input");

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BOOK));
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.ECHO_SHARD, 2));
        AncientForges.open(player, FORGE);
        ItemEnchantingRuntime.act(player, new ItemEnchantingPayloads.Action(token(player), 0));
        check(player.getMainHandStack().isOf(Items.ENCHANTED_BOOK) && player.getOffHandStack().getCount() == 1, "plain book was not safely converted to one stored-enchantment result");
        AncientForges.open(player, FORGE);
        ItemEnchantingRuntime.act(player, new ItemEnchantingPayloads.Action(token(player), 0));
        check(player.getOffHandStack().getCount() == 1, "already stored book consumed input");

        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.FLINT));
        AncientForges.open(player, FORGE);
        UUID wrong = token(player);
        ItemEnchantingRuntime.act(player, new ItemEnchantingPayloads.Action(wrong, 0));
        check(player.getOffHandStack().isOf(Items.FLINT), "wrong ingredient was consumed");

        var smite=world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(net.minecraft.enchantment.Enchantments.SMITE);
        offer(world,player);player.getMainHandStack().addEnchantment(smite,1);
        ItemStack incompatible=player.getMainHandStack().copy();AncientForges.open(player,FORGE);
        ItemEnchantingRuntime.act(player,new ItemEnchantingPayloads.Action(token(player),0));
        check(ItemStack.areEqual(incompatible,player.getMainHandStack())&&player.getOffHandStack().getCount()==3,"incompatible enchantment removed or ingredient consumed");
        offer(world,player);player.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);AncientForges.open(player,FORGE);
        ItemEnchantingRuntime.act(player,new ItemEnchantingPayloads.Action(token(player),0));
        check(sharpness(world,player.getMainHandStack())==0&&player.getOffHandStack().isEmpty(),"missing ingredient granted an enchantment");

        ServerPlayerEntity second=world.getServer().getPlayerManager().getPlayer("ChronicleFabricB");
        check(second!=null,"real second client must be connected for independent concurrent offers");
        second.teleport(world,3.5,66,3.5,java.util.Set.of(),0,0,false);second.interactionManager.changeGameMode(GameMode.SURVIVAL);
        PlayRuntime.setSelfParticipant(second,true);offer(world,player);UUID firstActor=token(player);offer(world,second);UUID secondActor=token(second);
        check(!firstActor.equals(secondActor),"two actors shared one offer token");
        ItemEnchantingRuntime.act(player,new ItemEnchantingPayloads.Action(secondActor,0));
        check(player.getOffHandStack().getCount()==3,"another player's token was accepted");
        ItemEnchantingRuntime.act(player,new ItemEnchantingPayloads.Action(firstActor,0));
        ItemEnchantingRuntime.act(second,new ItemEnchantingPayloads.Action(secondActor,0));
        check(sharpness(world,player.getMainHandStack())==1&&sharpness(world,second.getMainHandStack())==1
                &&player.getOffHandStack().getCount()==2&&second.getOffHandStack().getCount()==2,"concurrent two-actor offers did not keep independent inventories");

        offer(world,player);UUID changedTarget=token(player);player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        ItemEnchantingRuntime.act(player,new ItemEnchantingPayloads.Action(changedTarget,0));
        check(player.getMainHandStack().isOf(Items.IRON_PICKAXE)&&player.getOffHandStack().getCount()==3,"changed target was replaced or offering consumed");
        reloadCases(world,player);
        capacityReset(world);

        offer(world, player);
        UUID paused = token(player);
        ChronicleRuntime.service(world.getServer()).pause(new Actor(player.getName().getString(), player.getUuidAsString()));
        ItemEnchantingRuntime.act(player, new ItemEnchantingPayloads.Action(paused, 0));
        check(player.getOffHandStack().isOf(Items.ECHO_SHARD) && player.getOffHandStack().getCount() == 3, "paused Forge consumed input");
        ChronicleRuntime.service(world.getServer()).resume(new Actor(player.getName().getString(), player.getUuidAsString()));

        offer(world, player);
        UUID creative = token(player);
        player.interactionManager.changeGameMode(GameMode.CREATIVE);
        ItemEnchantingRuntime.act(player, new ItemEnchantingPayloads.Action(creative, 0));
        check(player.getOffHandStack().isOf(Items.ECHO_SHARD) && player.getOffHandStack().getCount() == 3, "Creative inspection consumed input");
        player.interactionManager.changeGameMode(GameMode.SURVIVAL);

        offer(world, player);
        UUID absent = token(player);
        PlayRuntime.setSelfParticipant(player, false);
        ItemEnchantingRuntime.act(player, new ItemEnchantingPayloads.Action(absent, 0));
        check(player.getOffHandStack().isOf(Items.ECHO_SHARD) && player.getOffHandStack().getCount() == 3, "absent participant consumed input");
        PlayRuntime.setSelfParticipant(player, true);

        offer(world, player);
        UUID distant = token(player);
        player.teleport(world, 30.5, 66, 3.5, java.util.Set.of(), 0, 0, false);
        ItemEnchantingRuntime.act(player, new ItemEnchantingPayloads.Action(distant, 0));
        check(player.getOffHandStack().isOf(Items.ECHO_SHARD) && player.getOffHandStack().getCount() == 3, "out-of-reach Forge consumed input");
        player.teleport(world, 3.5, 66, 3.5, java.util.Set.of(), 0, 0, false);

        offer(world, player);
        UUID selected = token(player);
        int originalSlot = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot((originalSlot + 1) % 9);
        ItemEnchantingRuntime.act(player, new ItemEnchantingPayloads.Action(selected, 0));
        check(player.getOffHandStack().isOf(Items.ECHO_SHARD) && player.getOffHandStack().getCount() == 3, "changed selected slot consumed input");
        player.getInventory().setSelectedSlot(originalSlot);

        offer(world, player);
        UUID replaced = token(player);
        put(world, FORGE, Blocks.AIR.getDefaultState());
        ItemEnchantingRuntime.act(player, new ItemEnchantingPayloads.Action(replaced, 0));
        check(player.getOffHandStack().isOf(Items.ECHO_SHARD) && player.getOffHandStack().getCount() == 3, "replaced Forge consumed input");
        put(world, FORGE, QuietBelow.ANCIENT_FORGE_BLOCK.getDefaultState());

        offer(world, player);
        UUID stale = token(player);
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.ECHO_SHARD, 2));
        ItemEnchantingRuntime.act(player, new ItemEnchantingPayloads.Action(stale, 0));
        check(player.getOffHandStack().getCount() == 2, "changed held ingredient was consumed");
        offer(world, player);
        UUID cut = token(player);
        put(world, CONTACT, Blocks.AIR.getDefaultState());
        ItemEnchantingRuntime.act(player, new ItemEnchantingPayloads.Action(cut, 0));
        check(player.getOffHandStack().isOf(Items.ECHO_SHARD) && player.getOffHandStack().getCount() == 3, "cut living contact consumed input");
        check(QuietBelowEcology.stationPower(world, FORGE) != null, "cut living contact left Forge powered");
        put(world, CONTACT, Blocks.SCULK.getDefaultState());
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
    }

    private static void reloadCases(ServerWorld world,ServerPlayerEntity player)throws Exception {
        var configDir=FabricLoader.getInstance().getConfigDir().resolve("chronicle");
        Path recipes=configDir.resolve("ingredient-enchantments.json"),caps=configDir.resolve("enchantment-caps.json");
        byte[] oldRecipes=Files.readAllBytes(recipes),oldCaps=Files.readAllBytes(caps);
        var actor=new Actor(player.getName().getString(),player.getUuidAsString());var source=player.getCommandSource();
        try{
            offer(world,player);UUID oldOffer=token(player);
            ChronicleRuntime.service(world.getServer()).pause(actor);AuthoringRuntime.get(world.getServer()).enable(player.getUuid());
            var modified=IngredientEnchantmentConfig.decode(new String(oldRecipes,java.nio.charset.StandardCharsets.UTF_8)).stream().map(r->r.ingredient().equals("minecraft:echo_shard")
                    ?new io.github.chronicle.core.enchanting.IngredientEnchantmentRecipe(r.id(),r.group(),r.ingredient(),r.enchantment(),3,2,true):r).toList();
            Files.writeString(recipes,IngredientEnchantmentConfig.encode(modified));
            io.github.chronicle.core.server.GameSetupTools.execute(source,"chronicle_core:item_enchanting","reload");
            Files.writeString(caps,io.github.chronicle.core.enchanting.EnchantmentCapConfig.encode(new EnchantmentCaps(true,1,Map.of("minecraft:sharpness",2))));
            io.github.chronicle.core.server.GameSetupTools.execute(source,"chronicle_core:enchantment_caps","reload");
            AuthoringRuntime.get(world.getServer()).disable(player.getUuid());ChronicleRuntime.service(world.getServer()).resume(actor);
            ItemEnchantingRuntime.act(player,new ItemEnchantingPayloads.Action(oldOffer,0));
            check(sharpness(world,player.getMainHandStack())==0&&player.getOffHandStack().getCount()==3,"recipe reload accepted a stale offer");
            offer(world,player);player.setStackInHand(Hand.OFF_HAND,new ItemStack(Items.ECHO_SHARD));AncientForges.open(player,FORGE);
            ItemEnchantingRuntime.act(player,new ItemEnchantingPayloads.Action(token(player),0));
            check(player.getOffHandStack().getCount()==1&&sharpness(world,player.getMainHandStack())==0,"configured two-item price ignored insufficient input");
            offer(world,player);ItemEnchantingRuntime.act(player,new ItemEnchantingPayloads.Action(token(player),0));
            check(sharpness(world,player.getMainHandStack())==2&&player.getOffHandStack().getCount()==1,"recipe levelIII/count2 or specific capII ignored");
        }finally{
            if(ChronicleRuntime.service(world.getServer()).state().phase().name().equals("ACTIVE"))ChronicleRuntime.service(world.getServer()).pause(actor);
            AuthoringRuntime.get(world.getServer()).enable(player.getUuid());Files.write(recipes,oldRecipes);Files.write(caps,oldCaps);
            io.github.chronicle.core.server.GameSetupTools.execute(source,"chronicle_core:item_enchanting","reload");
            io.github.chronicle.core.server.GameSetupTools.execute(source,"chronicle_core:enchantment_caps","reload");
            AuthoringRuntime.get(world.getServer()).disable(player.getUuid());ChronicleRuntime.service(world.getServer()).resume(actor);
        }
    }

    @SuppressWarnings("unchecked")
    private static void capacityReset(ServerWorld world)throws Exception {
        Field loaded=AncientForges.class.getDeclaredField("LOADED");loaded.setAccessible(true);
        var maps=(Map<MinecraftServer,java.util.LinkedHashSet<AncientForgeBlockEntity>>)loaded.get(null);
        var original=maps.get(world.getServer());var full=new java.util.LinkedHashSet<AncientForgeBlockEntity>();
        for(int i=0;i<1024;i++)full.add(new AncientForgeBlockEntity(new BlockPos(i,200,0),QuietBelow.ANCIENT_FORGE_BLOCK.getDefaultState()));
        maps.put(world.getServer(),full);
        try{
            put(world,FORGE,world.getBlockState(FORGE).with(AncientForgeBlock.HEAT,2));
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(world,world.getChunkManager().getWorldChunk(FORGE.getX()>>4,FORGE.getZ()>>4));
            check(world.getBlockState(FORGE).get(AncientForgeBlock.HEAT)==0,"untracked transient working art survived the real chunk-load callback");
            check(full.size()==1024&&!full.contains(world.getBlockEntity(FORGE)),"overflow callback retained another Forge");
        }finally{if(original==null)maps.remove(world.getServer());else maps.put(world.getServer(),original);}
    }

    private static void save(ServerCommandSource source, ServerWorld world, ServerPlayerEntity player) throws Exception {
        player.getInventory().setSelectedSlot(0);
        check(sharpness(world, player.getMainHandStack()) == 1, "save requires the final named Sharpness I target");
        check(player.getOffHandStack().isOf(Items.ECHO_SHARD) && player.getOffHandStack().getCount() == 2, "save requires exactly two retained echo shards");
        check(world.getBlockEntity(FORGE) instanceof AncientForgeBlockEntity, "Forge block entity missing before save");
        Path receipt = source.getServer().getSavePath(WorldSavePath.ROOT).resolve("quiet-below/forge-trial.json");
        Files.createDirectories(receipt.getParent());
        Files.writeString(receipt, "{\"sharpnessI\":true,\"echoes\":2,\"name\":\"Trial blade\",\"forge\":\"2,65,3\",\"recipes\":5}");
        player.teleport(world,3.5,66,3.5,java.util.Set.of(),0,0,false);
        player.interactionManager.changeGameMode(GameMode.SURVIVAL);player.getAbilities().flying=false;player.sendAbilitiesUpdate();
        ChronicleRuntime.service(source.getServer()).pause(new Actor(player.getName().getString(),player.getUuidAsString()));
    }

    private static void restart(ServerCommandSource source, ServerWorld world, ServerPlayerEntity player) throws Exception {
        Path receipt = source.getServer().getSavePath(WorldSavePath.ROOT).resolve("quiet-below/forge-trial.json");
        String saved = Files.readString(receipt);
        check(saved.contains("sharpnessI") && saved.contains("\"recipes\":5"), "Forge receipt/configuration evidence missing after restart");
        Path config = FabricLoader.getInstance().getConfigDir().resolve("chronicle/ingredient-enchantments.json");
        check(IngredientEnchantmentConfig.decode(Files.readString(config)).size() == 5, "saved recipe configuration did not survive restart");
        check(world.getBlockEntity(FORGE) instanceof AncientForgeBlockEntity, "Forge block entity did not survive restart");
        check(world.getBlockState(FORGE).get(AncientForgeBlock.HEAT) != 2, "transient Forge heat survived restart");
        check(sharpness(world, player.getMainHandStack()) == 1 && "Trial blade".equals(player.getMainHandStack().getName().getString()), "saved named Sharpness I target did not survive restart");
        check(player.getOffHandStack().isOf(Items.ECHO_SHARD) && player.getOffHandStack().getCount() == 2, "saved ingredient remainder did not survive restart");
        if (ChronicleRuntime.service(source.getServer()).state().phase().name().equals("ACTIVE")) {
            ChronicleRuntime.service(source.getServer()).pause(new Actor(player.getName().getString(), player.getUuidAsString()));
        }
    }

    @SuppressWarnings("unchecked")
    private static void render(ServerWorld world, ServerPlayerEntity player)throws Exception {
        Field field=AncientForges.class.getDeclaredField("LOADED");field.setAccessible(true);
        var tracked=((Map<MinecraftServer,java.util.LinkedHashSet<AncientForgeBlockEntity>>)field.get(null)).get(world.getServer());
        int index = 0;
        for (Direction facing : Direction.Type.HORIZONTAL) for (int heat = 0; heat <= 2; heat++) {
            BlockPos at = new BlockPos(8 + (index % 4) * 3, 65, 8 + (index++ / 4) * 3);
            put(world, at, QuietBelow.ANCIENT_FORGE_BLOCK.getDefaultState().with(AncientForgeBlock.FACING, facing).with(AncientForgeBlock.HEAT, heat));
            // Static test-only art gallery: don't let the production model poll replace
            // the three deliberately posed textures before the client captures them.
            if(tracked!=null)tracked.remove(world.getBlockEntity(at));
            VoxelShape collision = world.getBlockState(at).getCollisionShape(world, at);
            var box=collision.getBoundingBox();
            check(box.minX==0&&box.minY==0&&box.minZ==0&&box.maxX==1&&box.maxY==1&&box.maxZ==1, "Forge collision must be its explicit base cube");
        }
        player.interactionManager.changeGameMode(GameMode.CREATIVE);player.getAbilities().flying=true;player.sendAbilitiesUpdate();
        player.teleport(world,22,70,23,java.util.Set.of(),140,24,false);
    }

    @SuppressWarnings("unchecked")
    private static UUID token(ServerPlayerEntity player) {
        try {
            Field servers = ItemEnchantingRuntime.class.getDeclaredField("SERVERS"); servers.setAccessible(true);
            Object runtime = ((Map<MinecraftServer, ?>) servers.get(null)).get(player.getEntityWorld().getServer());
            if (runtime == null) return null;
            Field offers = runtime.getClass().getDeclaredField("offers"); offers.setAccessible(true);
            Object offer = ((Map<UUID, ?>) offers.get(runtime)).get(player.getUuid());
            if (offer == null) return null;
            Method method = offer.getClass().getDeclaredMethod("token"); method.setAccessible(true);
            return (UUID) method.invoke(offer);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException("cannot inspect guarded fixture offer", exception); }
    }

    private static void put(ServerWorld world, BlockPos pos, net.minecraft.block.BlockState state) {
        check(world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4), "fixture chunk is not loaded");
        world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }

    private static int sharpness(ServerWorld world, ItemStack stack) {
        RegistryEntry<Enchantment> entry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT)
                .getOrThrow(net.minecraft.registry.RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of("minecraft:sharpness")));
        return stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).getLevel(entry);
    }


    private static void requireFixture(ServerCommandSource source) throws Exception {
        MinecraftServer server = source.getServer(); Path root = server.getRunDirectory().toRealPath();
        check(server.isDedicated() && !server.isOnlineMode() && server.getServerPort() == 25575 && "127.0.0.1".equals(server.getServerIp()), "trial requires offline loopback 127.0.0.1:25575");
        check(root.getParent() != null && "test-runs".equals(root.getParent().getFileName().toString()) && RUN.matcher(root.getFileName().toString()).matches(), "trial requires a disposable test-runs timestamp directory");
        check(source.getPlayerOrThrow().hasPermissionLevel(2), "trial requires operator level 2");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); checks++; }
    private static String detail(Exception error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
}
