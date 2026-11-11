package io.github.chronicle.core.enchanting;

import io.github.chronicle.core.ChronicleCore;
import io.github.chronicle.core.server.EnchantmentCapRuntime;
import io.github.chronicle.core.server.GameSetupTools;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static net.minecraft.server.command.CommandManager.literal;

/** Opt-in native rehearsal. Never registered in an ordinary server process. */
public final class EnchantmentCapsTrial {
    private static final String TOOL="chronicle_core:enchantment_caps";
    private static final BlockPos CHEST=new BlockPos(3,64,3);
    private static final BlockPos SAVE_FALLBACK_CHEST=new BlockPos(31,81,15);
    private static int checks;
    private EnchantmentCapsTrial() { }

    public static void register() {
        if (!"true".equals(System.getenv("CHRONICLE_CAPS_NATIVE_TRIAL"))) return;
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment) -> {
            var root=literal("chronicle_enchantment_trial").requires(source -> source.hasPermissionLevel(2));
            for (String verb:List.of("basic","vanilla","check_creative","save","restart")) root.then(literal(verb).executes(context -> run(context.getSource(),verb)));
            dispatcher.register(root);
        });
    }

    private static int run(ServerCommandSource source,String verb) {
        try {
            var server=source.getServer();
            Path root=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
            require(root.getParent().getFileName().toString().equals("test-runs")&&!server.isOnlineMode()
                    &&server.getServerPort()==25575&&server.getServerIp().equals("127.0.0.1"),"unsafe native fixture");
            var player=source.getPlayerOrThrow(); checks=0;
            switch (verb) {
                case "basic" -> basic(source);
                case "vanilla" -> vanilla(source);
                case "check_creative" -> require(rawLevel(player.getInventory().getStack(0),sharp(source),false)==1,"creative input did not store I");
                case "save" -> save(source);
                case "restart" -> restart(source);
                default -> throw new IllegalArgumentException("Unknown trial action");
            }
            source.sendFeedback(() -> Text.literal("Enchantment trial PASS "+verb+" ("+checks+" checks)"),false);
            return 1;
        } catch (Exception error) {
            ChronicleCore.LOGGER.error("Enchantment native trial failed: "+verb,error);
            source.sendError(Text.literal("Enchantment trial FAIL "+verb+": "+error.getMessage())); return 0;
        }
    }
    private static RegistryEntry<Enchantment> sharp(ServerCommandSource source) {
        return source.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
    }
    private static ItemStack sword(RegistryEntry<Enchantment> enchant,int level) {
        ItemStack result=new ItemStack(Items.DIAMOND_SWORD);
        result.set(DataComponentTypes.ENCHANTMENTS,component(enchant,level));return result;
    }
    private static ItemEnchantmentsComponent component(RegistryEntry<Enchantment> enchant,int level) {
        var builder=new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);builder.set(enchant,level);return builder.build();
    }
    private static int rawLevel(ItemStack stack,RegistryEntry<Enchantment> enchant,boolean stored) {
        var map=((EnchantmentStackAccess)(Object)stack).chronicle$capComponents();
        var component=map.get(stored?DataComponentTypes.STORED_ENCHANTMENTS:DataComponentTypes.ENCHANTMENTS);
        return component==null?0:component.getLevel(enchant);
    }
    /** Models side-neutral async decode; no world/player APIs may be used inside supplier. */
    private static <T> T unbound(Supplier<T> supplier) throws Exception {
        return CompletableFuture.supplyAsync(supplier).get(10,TimeUnit.SECONDS);
    }
    private static void require(boolean condition,String message) { checks++;if (!condition) throw new IllegalStateException(message); }

    private static void basic(ServerCommandSource source) throws Exception {
        var enchant=sharp(source);var server=source.getServer();
        require(EnchantmentCapRuntime.policy(server).enabled()&&EnchantmentCapRuntime.policy(server).defaultMaxLevel()==1,"Quiet Below first-file defaults");
        require(enchant.value().getMaxLevel()==1,"server native maximum is not capped");
        ItemStack gear=unbound(() -> {
            ItemStack item=sword(enchant,5);item.set(DataComponentTypes.STORED_ENCHANTMENTS,component(enchant,4));
            item.setDamage(73);item.set(DataComponentTypes.CUSTOM_NAME,Text.literal("Retained name"));return item;
        });
        require(rawLevel(gear,enchant,false)==5&&rawLevel(gear,enchant,true)==4,"async decode unexpectedly acquired server policy");
        gear.getComponents();
        require(rawLevel(gear,enchant,false)==1&&rawLevel(gear,enchant,true)==1,"both stored components were not permanently capped");
        require(gear.getDamage()==73&&gear.getCount()==1&&gear.getName().getString().equals("Retained name"),"unrelated components changed");
        var stamp=((EnchantmentStackAccess)(Object)gear).chronicle$capStamp();gear.getComponents();
        require(stamp==((EnchantmentStackAccess)(Object)gear).chronicle$capStamp(),"unchanged stack missed constant-time cache");
        gear.set(DataComponentTypes.ENCHANTMENTS,component(enchant,5));
        require(rawLevel(gear,enchant,false)==1,"set reused mutable map and escaped cap");
        // Raw map mutation is intentionally only in this fixture: the value-identity cache must notice it.
        ((EnchantmentStackAccess)(Object)gear).chronicle$capComponents().set(DataComponentTypes.ENCHANTMENTS,component(enchant,5));
        gear.getComponentChanges();require(rawLevel(gear,enchant,false)==1,"component-change serialization escaped permanent cap");

        var original=unbound(() -> sword(enchant,5));
        require(rawLevel(original.copy(),enchant,false)==1&&rawLevel(original,enchant,false)==5,"canonical copy did not cap its own map while preserving source");
        ItemStack nested=unbound(() -> {
            ItemStack shulker=new ItemStack(Items.SHULKER_BOX);
            shulker.set(DataComponentTypes.CONTAINER,ContainerComponent.fromStacks(List.of(ItemStack.EMPTY,original)));
            ItemStack bundle=new ItemStack(Items.BUNDLE);
            var builder=new BundleContentsComponent.Builder(new BundleContentsComponent(List.of(original.copy())));builder.setSelectedStackIndex(0);
            bundle.set(DataComponentTypes.BUNDLE_CONTENTS,builder.build());
            ItemStack crossbow=new ItemStack(Items.CROSSBOW);crossbow.set(DataComponentTypes.CHARGED_PROJECTILES,ChargedProjectilesComponent.of(original.copy()));
            shulker.set(DataComponentTypes.BUNDLE_CONTENTS,new BundleContentsComponent(List.of(bundle)));
            shulker.set(DataComponentTypes.CHARGED_PROJECTILES,ChargedProjectilesComponent.of(crossbow));return shulker;
        });
        var oldContainer=((EnchantmentStackAccess)(Object)nested).chronicle$capComponents().get(DataComponentTypes.CONTAINER);
        nested.getImmutableComponents();
        require(rawLevel(original,enchant,false)==5,"normalization mutated an externally shared child");
        var children=nested.get(DataComponentTypes.CONTAINER).stream().toList();
        require(children.size()==2&&children.get(0).isEmpty()&&rawLevel(children.get(1),enchant,false)==1,"container lost holes/levels");
        var bundle=nested.get(DataComponentTypes.BUNDLE_CONTENTS).get(0).get(DataComponentTypes.BUNDLE_CONTENTS);
        require(bundle.getSelectedStackIndex()==0&&rawLevel(bundle.get(0),enchant,false)==1,"bundle selected item was not preserved/capped");
        var crossbow=nested.get(DataComponentTypes.CHARGED_PROJECTILES).getProjectiles().getFirst();
        require(rawLevel(crossbow.get(DataComponentTypes.CHARGED_PROJECTILES).getProjectiles().getFirst(),enchant,false)==1,"charged nested projectile escaped");
        require(rawLevel(oldContainer.iterateNonEmpty().iterator().next(),enchant,false)==5,"old immutable container was mutated");
        require(rawLevel(oldContainer.stream().toList().get(1),enchant,false)==1,"container stream copies bypassed canonical constructor");
        ItemStack sparse=unbound(() -> {
            var slots=new ArrayList<ItemStack>(java.util.Collections.nCopies(27,ItemStack.EMPTY));slots.set(26,sword(enchant,5));
            ItemStack outer=new ItemStack(Items.SHULKER_BOX);outer.set(DataComponentTypes.CONTAINER,ContainerComponent.fromStacks(slots));return outer;
        });
        sparse.getComponents();var sparseSlots=sparse.get(DataComponentTypes.CONTAINER).stream().toList();
        require(sparseSlots.size()==27&&sparseSlots.subList(0,26).stream().allMatch(ItemStack::isEmpty)&&rawLevel(sparseSlots.get(26),enchant,false)==1,"sparse slot26 moved during cap normalization");
        ItemStack deep=unbound(() -> {
            ItemStack item=sword(enchant,5);
            for(int n=0;n<18;n++){ItemStack outer=new ItemStack(Items.SHULKER_BOX);outer.set(DataComponentTypes.CONTAINER,ContainerComponent.fromStacks(List.of(item)));item=outer;}
            return item;
        });
        var before=((EnchantmentStackAccess)(Object)deep).chronicle$capComponents().getChanges();
        boolean rejected=false;
        try { EnchantmentStacks.normalize(deep,EnchantmentCapRuntime.policy(server)); } catch (IllegalArgumentException expected) { rejected=true; }
        require(rejected&&before.equals(((EnchantmentStackAccess)(Object)deep).chronicle$capComponents().getChanges()),"oversized nested tree partially changed");
        GameSetupTools.execute(source,TOOL,"raise");
        var craft=new net.minecraft.screen.CraftingScreenHandler(97,source.getPlayerOrThrow().getInventory(),ScreenHandlerContext.create(source.getWorld(),new BlockPos(0,64,0)));
        craft.getSlot(1).setStack(new ItemStack(Items.IRON_NUGGET));craft.onContentChanged(craft.getSlot(1).inventory);
        require(rawLevel(craft.getSlot(0).getStack(),enchant,false)==2,"raising cap did not affect fresh crafting output; cached recipe template was damaged");
        require(enchant.value().getMaxLevel()==2&&rawLevel(gear,enchant,false)==1,"raising maximum restored removed levels");
        gear.getComponents();require(rawLevel(gear,enchant,false)==1,"raised-policy access restored an old level");
        require(rawLevel(sword(enchant,5),enchant,false)==2,"new stored level ignores raised cap");
        GameSetupTools.execute(source,TOOL,"disable");
        var untouched=new net.minecraft.world.chunk.WorldChunk(source.getWorld(),new net.minecraft.util.math.ChunkPos(80,80));
        require(!untouched.needsSaving(),"synthetic loaded chunk already dirty");
        EnchantmentCapRuntime.loadedChunk(source.getWorld(),untouched);
        require(!untouched.needsSaving(),"disabled caps dirtied a clean chunk");
        ItemStack uncapped=sword(enchant,5);require(rawLevel(uncapped,enchant,false)==5&&enchant.value().getMaxLevel()==5,"disabled rule changed items/native maximum");
        GameSetupTools.execute(source,TOOL,"enable");uncapped.getComponents();
        require(rawLevel(uncapped,enchant,false)==2,"re-enabled cap not permanent");
        GameSetupTools.execute(source,TOOL,"lower");
        require(EnchantmentCapRuntime.policy(server).defaultMaxLevel()==1,"fixture failed to restore I default");
        var config=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("chronicle/enchantment-caps.json");
        byte[] valid=Files.readAllBytes(config);
        try {
            Files.writeString(config,"{\"schemaVersion\":1,\"enabled\":true,\"enabled\":false,\"defaultMaxLevel\":1,\"overrides\":{}}");
            boolean failed=false;try{GameSetupTools.execute(source,TOOL,"reload");}catch(Exception expected){failed=true;}
            require(failed&&io.github.chronicle.core.server.RuntimeHolds.reason(server)!=null&&EnchantmentCapRuntime.policy(server).defaultMaxLevel()==1,"bad reload lost old policy or failed to hold");
        } finally { Files.write(config,valid);GameSetupTools.execute(source,TOOL,"reload"); }
        require(io.github.chronicle.core.server.RuntimeHolds.reason(server)==null,"repaired configuration did not release cap hold");
        try {
            Files.writeString(config,EnchantmentCapConfig.encode(new EnchantmentCaps(true,1,Map.of("minecraft:sharpness",2))));
            GameSetupTools.execute(source,TOOL,"reload");
            require(enchant.value().getMaxLevel()==2&&rawLevel(sword(enchant,5),enchant,false)==2,"enchantment override ignored");
            var unbreaking=source.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
            require(rawLevel(sword(unbreaking,3),unbreaking,false)==1,"override altered another enchantment cap");
        } finally { Files.write(config,valid);GameSetupTools.execute(source,TOOL,"reload"); }
        migration(source);
    }

    /** Native overflow/snapshot proof plus the same unload callback used by Fabric. */
    private static void migration(ServerCommandSource source) throws Exception {
        var world=source.getWorld();var server=source.getServer();var enchant=sharp(source);
        var first=world.getChunk(0,0);var second=world.getChunk(1,0);
        var containers=new ArrayList<net.minecraft.block.entity.ChestBlockEntity>();
        for(int i=0;i<400;i++) {
            var position=new BlockPos(i%32,80,i/32);
            world.setBlockState(position,Blocks.CHEST.getDefaultState(),Block.NOTIFY_ALL);
            var chest=(net.minecraft.block.entity.ChestBlockEntity)world.getBlockEntity(position);
            chest.setStack(0,sword(enchant,1));containers.add(chest);
        }
        int baselineSlots=EnchantmentCapRuntime.pendingSlots(server),baselineInventories=EnchantmentCapRuntime.pendingInventories(server);
        // Simulate old asynchronously decoded values after normal native inventory assignment.
        for(var chest:containers)((EnchantmentStackAccess)(Object)chest.getStack(0)).chronicle$capComponents().set(DataComponentTypes.ENCHANTMENTS,component(enchant,5));
        EnchantmentCapRuntime.loadedChunk(world,first);EnchantmentCapRuntime.loadedChunk(world,second);
        require(EnchantmentCapRuntime.pendingSlots(server)<=InventoryMigrationQueue.MAX_SLOTS&&EnchantmentCapRuntime.pendingSlots(server)>baselineSlots,"native queue exceeded slot bound or admitted nothing");
        require(EnchantmentCapRuntime.pendingInventories(server)-baselineInventories<400,"overflow fixture did not exceed pending slot capacity");
        int serialized=0,permanent=0;
        for(var chest:containers) {
            var data=chest.createNbtWithIdentifyingData(source.getRegistryManager());
            var items=data.getListOrEmpty("Items");
            if(items.size()==1&&items.getFirst() instanceof net.minecraft.nbt.NbtCompound item
                    &&item.getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:enchantments").getInt("minecraft:sharpness",-1)==1) serialized++;
            if(rawLevel(chest.getStack(0),enchant,false)==1)permanent++;
        }
        require(serialized==400,"native dirty/save fallback did not serialize all400 stored levels toI: "+serialized);
        require(permanent==400,"native save normalized only copies rather than actual400 inventory stacks: "+permanent);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(world,first);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(world,second);
        require(EnchantmentCapRuntime.pendingSlots(server)==baselineSlots&&EnchantmentCapRuntime.pendingInventories(server)==baselineInventories,"chunk-unload callback retained pending inventory references");
        // No manual NBT creation or migration queue access for this stack: exercise the
        // real chunk snapshot/save path before later raising the cap and restarting.
        world.setBlockState(SAVE_FALLBACK_CHEST,Blocks.CHEST.getDefaultState(),Block.NOTIFY_ALL);
        var fallback=(net.minecraft.block.entity.ChestBlockEntity)world.getBlockEntity(SAVE_FALLBACK_CHEST);
        ItemStack actual=sword(enchant,1);fallback.setStack(0,actual);
        ((EnchantmentStackAccess)(Object)actual).chronicle$capComponents().set(DataComponentTypes.ENCHANTMENTS,component(enchant,5));
        fallback.markDirty();
        require(rawLevel(actual,enchant,false)==5,"save-only fixture was normalized before real saving");
        server.save(true,true,true);
        require(rawLevel(actual,enchant,false)==1,"real chunk save did not permanently normalize the unqueued inventory stack");
    }

    private static void vanilla(ServerCommandSource source) throws Exception {
        var enchant=sharp(source);var player=source.getPlayerOrThrow();var world=source.getWorld();
        player.getInventory().clear();
        source.getServer().getCommandManager().getDispatcher().execute("give @s minecraft:diamond_sword[minecraft:enchantments={\"minecraft:sharpness\":5}]",source.withSilent());
        require(rawLevel(player.getInventory().getStack(0),enchant,false)==1,"give did not reduce stored levels");
        source.getServer().getCommandManager().getDispatcher().execute("loot give @s loot test:capped",source.withSilent());
        require(rawLevel(player.getInventory().getStack(1),enchant,false)==1,"final native loot output did not cap");
        boolean rejected=false;try{source.getServer().getCommandManager().getDispatcher().execute("enchant @s minecraft:sharpness 5",source.withSilent());}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){rejected=true;}
        require(rejected,"native enchant command accepted a level above the cap");
        var table=new EnchantmentScreenHandler(93,player.getInventory(),ScreenHandlerContext.create(world,new BlockPos(0,64,0)));
        table.getSlot(0).setStack(new ItemStack(Items.DIAMOND_SWORD));table.getSlot(1).setStack(new ItemStack(Items.LAPIS_LAZULI,64));
        table.onContentChanged(table.getSlot(0).inventory);
        for(int level:table.enchantmentLevel) require(level<=1,"table hint advertises over-cap enchantment");
        boolean enchanted=false;
        for(int button=0;button<3;button++) if(table.enchantmentPower[button]>0){enchanted=table.onButtonClick(player,button);if(enchanted)break;}
        require(enchanted&&!table.getSlot(0).getStack().getEnchantments().isEmpty(),"native table did not produce an enchanted item");
        for(var entry:table.getSlot(0).getStack().getEnchantments().getEnchantments()) require(table.getSlot(0).getStack().getEnchantments().getLevel(entry)==1,"native table output over cap");
        var anvil=new AnvilScreenHandler(94,player.getInventory(),ScreenHandlerContext.create(world,new BlockPos(0,64,0)));
        var book=new ItemStack(Items.ENCHANTED_BOOK);book.set(DataComponentTypes.STORED_ENCHANTMENTS,component(enchant,1));
        anvil.getSlot(0).setStack(sword(enchant,1));anvil.getSlot(1).setStack(book);anvil.updateResult();
        require(anvil.getSlot(2).getStack().isEmpty()&&anvil.getLevelCost()==0,"I+I anvil charges for no improvement");
        var unbreaking=source.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        var usefulBook=new ItemStack(Items.ENCHANTED_BOOK);usefulBook.set(DataComponentTypes.STORED_ENCHANTMENTS,component(unbreaking,3));
        anvil.getSlot(1).setStack(usefulBook);anvil.updateResult();
        require(!anvil.getSlot(2).getStack().isEmpty()&&rawLevel(anvil.getSlot(2).getStack(),unbreaking,false)==1&&anvil.getLevelCost()>0,"useful capped anvil merge rejected");
        require(rawLevel(anvil.getSlot(2).getStack(),enchant,false)==1,"anvil lost original enchantment");
        require(anvil.setNewItemName("Renamed sword")&&!anvil.getSlot(2).getStack().isEmpty(),"rename rejected by no-op guard");
        ItemStack damaged=sword(enchant,1);damaged.setDamage(200);anvil.getSlot(0).setStack(damaged);anvil.getSlot(1).setStack(new ItemStack(Items.DIAMOND));anvil.updateResult();
        require(!anvil.getSlot(2).getStack().isEmpty()&&anvil.getSlot(2).getStack().getDamage()<200,"repair rejected by no-op guard");
        var craft=new net.minecraft.screen.CraftingScreenHandler(95,player.getInventory(),ScreenHandlerContext.create(world,new BlockPos(0,64,0)));
        craft.getSlot(1).setStack(new ItemStack(Items.IRON_NUGGET));craft.onContentChanged(craft.getSlot(1).inventory);
        ItemStack crafted=craft.getSlot(0).getStack();int craftLevel=rawLevel(crafted,enchant,false);
        require(crafted.isOf(Items.DIAMOND_SWORD)&&craftLevel==1,"custom native crafting output: "+net.minecraft.registry.Registries.ITEM.getId(crafted.getItem())+" raw level "+craftLevel);
        var smith=new net.minecraft.screen.SmithingScreenHandler(96,player.getInventory(),ScreenHandlerContext.create(world,new BlockPos(0,64,0)));
        smith.getSlot(0).setStack(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));smith.getSlot(1).setStack(sword(enchant,5));smith.getSlot(2).setStack(new ItemStack(Items.NETHERITE_INGOT));smith.updateResult();
        require(smith.getSlot(3).getStack().isOf(Items.NETHERITE_SWORD)&&rawLevel(smith.getSlot(3).getStack(),enchant,false)==1,"native smithing output escaped cap");
        var dropped=new net.minecraft.entity.ItemEntity(world,3.5,65,3.5,unbound(()->sword(enchant,5)));
        require(world.spawnEntity(dropped)&&rawLevel(dropped.getStack(),enchant,false)==1,"loaded item entity escaped cap");dropped.discard();
        var zombie=new net.minecraft.entity.mob.ZombieEntity(net.minecraft.entity.EntityType.ZOMBIE,world);zombie.setPosition(4,65,4);zombie.setAiDisabled(true);
        zombie.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,unbound(()->sword(enchant,5)));
        require(world.spawnEntity(zombie)&&rawLevel(zombie.getEquippedStack(net.minecraft.entity.EquipmentSlot.MAINHAND),enchant,false)==1,"loaded equipment escaped cap");zombie.discard();
        var offered=unbound(() -> sword(enchant,5));
        var trade=new net.minecraft.village.TradeOffer(new net.minecraft.village.TradedItem(Items.EMERALD),offered,10,1,0.05f);
        require(rawLevel(trade.getSellItem(),enchant,false)==1&&rawLevel(trade.copySellItem(),enchant,false)==1,"merchant displayed/output item escaped cap");
        var villager=new net.minecraft.entity.passive.VillagerEntity(net.minecraft.entity.EntityType.VILLAGER,world);
        villager.setVillagerData(villager.getVillagerData().withProfession(source.getRegistryManager().getOrThrow(RegistryKeys.VILLAGER_PROFESSION).getOrThrow(net.minecraft.village.VillagerProfession.LIBRARIAN)));
        require(!villager.getOffers().isEmpty(),"native librarian offers absent");
        for(var offer:villager.getOffers())for(var entry:offer.getSellItem().getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS,ItemEnchantmentsComponent.DEFAULT).getEnchantments())
            require(rawLevel(offer.getSellItem(),entry,true)<=1,"fresh librarian enchantment over cap");
        var arrow=new net.minecraft.entity.projectile.ArrowEntity(world,3.5,66,3.5,unbound(()->sword(enchant,5)),unbound(()->sword(enchant,5)));
        arrow.setNoGravity(true);require(world.spawnEntity(arrow),"native arrow did not spawn");
        require(rawLevel(arrow.getItemStack(),enchant,false)==1&&rawLevel(arrow.getWeaponStack(),enchant,false)==1,"projectile stack/weapon escaped cap");arrow.discard();
    }

    private static void save(ServerCommandSource source) throws Exception {
        var player=source.getPlayerOrThrow();var enchant=sharp(source);var world=source.getWorld();
        ItemStack old=sword(enchant,5);require(rawLevel(old,enchant,false)==1,"old fixture not lowered before raising");
        GameSetupTools.execute(source,TOOL,"raise");
        player.getInventory().clear();player.getInventory().setStack(0,old);player.getInventory().setStack(1,sword(enchant,5));
        var book=new ItemStack(Items.ENCHANTED_BOOK);book.set(DataComponentTypes.STORED_ENCHANTMENTS,component(enchant,5));player.getInventory().setStack(2,book);
        var slots=new ArrayList<ItemStack>(java.util.Collections.nCopies(27,ItemStack.EMPTY));slots.set(0,old);slots.set(26,sword(enchant,5));
        var nested=new ItemStack(Items.SHULKER_BOX);nested.set(DataComponentTypes.CONTAINER,ContainerComponent.fromStacks(slots));
        player.getEnderChestInventory().setStack(0,nested);
        world.setBlockState(CHEST,Blocks.CHEST.getDefaultState(),Block.NOTIFY_ALL);
        var chest=(Inventory)world.getBlockEntity(CHEST);chest.setStack(0,old.copy());chest.setStack(1,nested.copy());chest.markDirty();
        player.getInventory().markDirty();player.getEnderChestInventory().markDirty();player.playerScreenHandler.syncState();
        Files.writeString(source.getServer().getSavePath(WorldSavePath.ROOT).resolve("chronicle/cap-trial.txt"),"old=1,new=2,nested=1/2,default=2");
        require(rawLevel(player.getInventory().getStack(1),enchant,false)==2,"new fixture cap not II");
        require(rawLevel(player.getInventory().getStack(0),enchant,false)==1,"cap increase restored old gear");
    }
    private static void restart(ServerCommandSource source) throws Exception {
        var enchant=sharp(source);var player=source.getPlayerOrThrow();
        require(Files.readString(source.getServer().getSavePath(WorldSavePath.ROOT).resolve("chronicle/cap-trial.txt")).contains("old=1,new=2"),"save receipt absent");
        require(EnchantmentCapRuntime.policy(source.getServer()).defaultMaxLevel()==2,"cap configuration did not survive restart");
        require(rawLevel(player.getInventory().getStack(0),enchant,false)==1,"permanent stored old I not retained under II cap");
        require(rawLevel(player.getInventory().getStack(1),enchant,false)==2,"new II not retained");
        require(rawLevel(player.getInventory().getStack(2),enchant,true)==2,"book II not retained");
        var nested=player.getEnderChestInventory().getStack(0).get(DataComponentTypes.CONTAINER).stream().toList();
        require(nested.size()==27&&rawLevel(nested.get(0),enchant,false)==1&&rawLevel(nested.get(26),enchant,false)==2&&nested.subList(1,26).stream().allMatch(ItemStack::isEmpty),"sparse nested permanent levels not retained");
        var chest=(Inventory)source.getWorld().getBlockEntity(CHEST);
        require(chest!=null&&rawLevel(chest.getStack(0),enchant,false)==1,"native chest save did not preserve old I");
        var fallback=(Inventory)source.getWorld().getBlockEntity(SAVE_FALLBACK_CHEST);
        require(fallback!=null&&rawLevel(fallback.getStack(0),enchant,false)==1,"real save-only chest did not retain I after raising cap and restarting");
        require(enchant.value().getMaxLevel()==2,"server maximum not restored from policy");
    }
}
