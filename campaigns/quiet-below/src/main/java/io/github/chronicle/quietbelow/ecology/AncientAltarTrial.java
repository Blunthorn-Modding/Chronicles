package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.network.OfferingPayloads;
import io.github.chronicle.core.offering.*;
import io.github.chronicle.core.server.*;
import io.github.chronicle.core.stats.FoodCapacity;
import io.github.chronicle.core.stats.OfferingAttributes;
import io.github.chronicle.quietbelow.QuietBelow;
import io.github.chronicle.quietbelow.content.AncientAltars;
import io.github.chronicle.quietbelow.content.AncientAltarBlock;
import io.github.chronicle.quietbelow.content.AncientAltarBlockEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static net.minecraft.server.command.CommandManager.literal;

/** Disposable native rehearsal only. Normal servers register none of these destructive fixtures. */
public final class AncientAltarTrial {
    private static final BlockPos ALTAR = new BlockPos(2,65,3), CONTACT = new BlockPos(-3,64,0);
    private static final Map<MinecraftServer, FoodRun> FOOD = new IdentityHashMap<>();
    private static final String PAGE_GROUP="chronicle_test:pages";
    private static int checks;
    private AncientAltarTrial() { }
    public static void register() {
        if (!"true".equals(System.getenv("CHRONICLE_BARGAIN_NATIVE_TRIAL"))) return;
        var pages=new ArrayList<LastingBargains.Choice>();
        for(int i=0;i<16;i++)pages.add(new LastingBargains.Choice(new OfferingDefinition("chronicle_test:page_"+i,new OfferingTerms(2,2,0),3),
                "Choice "+(i+1),"Native bounded pagination proof","+1 food icon","+1 heart"));
        LastingBargains.registerGroup(PAGE_GROUP,pages);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var root = literal("chronicle_bargain_trial").requires(source -> source.hasPermissionLevel(2));
            for (String verb : List.of("identity","basic","food","prepare","safety","inventory_prepare","inventory_check","out_of_reach","return_to_altar","cut_power","restore_power","closed_check","pages","preview_foreign","preview_clean","expire","no_purchase","open","ui_check","death","respawn_check","orphan_death","orphan_respawn_repair","save","restart","corrupt","ending","ended","render","itemrender")) {
                root.then(literal(verb).executes(context -> run(context.getSource(), verb)));
            }
            dispatcher.register(root);
        });
        ServerTickEvents.END_SERVER_TICK.register(AncientAltarTrial::tickFood);
        ServerLifecycleEvents.SERVER_STOPPED.register(FOOD::remove);
    }
    private static int run(ServerCommandSource source, String verb) {
        checks = 0;
        try {
            fixture(source); var player = source.getPlayerOrThrow(); var server = source.getServer(); var world = player.getEntityWorld();
            switch (verb) {
                case "identity" -> { check(FoodCapacity.capacity(player)==20,"other player capacity is not native20"); check(OfferingStats.ledger(server).totals(player.getUuid()).isEmpty(),"other player inherited bargains"); }
                case "basic" -> basic(player);
                case "food" -> { startFood(source); return 1; }
                case "prepare" -> prepare(player);
                case "safety" -> safety(player);
                case "inventory_prepare" -> {
                    var inventory=player.getInventory();inventory.clear();
                    ItemStack bread=new ItemStack(Items.BREAD,7);bread.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,Text.literal("Workstation proof"));
                    inventory.setStack(9,bread);inventory.setStack(1,new ItemStack(Items.IRON_INGOT,3));
                    player.currentScreenHandler.syncState();
                }
                case "inventory_check" -> {
                    var handler=player.currentScreenHandler;
                    check(handler instanceof io.github.chronicle.core.screen.OfferingScreenHandler,"native offering handler absent");
                    check(handler.slots.size()==36,"Altar created station storage");
                    for(var slot:handler.slots)check(slot.inventory==player.getInventory(),"non-player slot in Altar");
                    check(handler.getCursorStack().isEmpty(),"cursor item lost in workstation");
                    int occupied=0;for(int i=0;i<36;i++)if(!player.getInventory().getStack(i).isEmpty())occupied++;
                    check(occupied==2,"workstation duplicated/lost inventory stacks");
                    ItemStack bread=player.getInventory().getStack(0),iron=player.getInventory().getStack(9);
                    check(bread.isOf(Items.BREAD)&&bread.getCount()==7&&bread.getName().getString().equals("Workstation proof"),"shift-click/number-key lost bread/components");
                    check(iron.isOf(Items.IRON_INGOT)&&iron.getCount()==3,"number-key failed player hotbar swap");
                    checkNoPurchase(player,"slot interactions accepted bargain");
                }
                case "out_of_reach" -> player.teleport(world,15.5,70,15.5,Set.of(),0,0,false);
                case "return_to_altar" -> safe(player);
                case "cut_power" -> put(world,CONTACT,Blocks.AIR.getDefaultState());
                case "restore_power" -> put(world,CONTACT,Blocks.SCULK.getDefaultState());
                case "closed_check" -> {check(token(player)==null,"Escape left a live bargain token");checkNoPurchase(player,"close committed a bargain");}
                case "expire" -> expire(player);
                case "no_purchase" -> checkNoPurchase(player,"expired offer committed a bargain");
                case "pages" -> LastingBargains.open(player,"chronicle_test:pages",PAGE_GROUP,"Core pagination proof",actor->null);
                case "preview_foreign" -> {
                    var id=Identifier.of("chronicle_test","screen_preview");
                    player.getAttributeInstance(EntityAttributes.MAX_HEALTH).addTemporaryModifier(new EntityAttributeModifier(id,4,EntityAttributeModifier.Operation.ADD_VALUE));
                    player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).addTemporaryModifier(new EntityAttributeModifier(id,.2,EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
                case "preview_clean" -> {
                    var id=Identifier.of("chronicle_test","screen_preview");
                    player.getAttributeInstance(EntityAttributes.MAX_HEALTH).removeModifier(id);
                    player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).removeModifier(id);
                }
                case "open" -> { AncientAltars.open(player,ALTAR); check(token(player)!=null,"Altar did not issue an offer"); }
                case "ui_check","restart","respawn_check" -> purchased(player);
                case "death" -> { purchased(player); player.kill(world); check(!player.isAlive(),"real native death failed"); }
                case "orphan_death" -> {
                    purchased(player);pause(player);
                    player.getAttributeInstance(EntityAttributes.MAX_HEALTH).addPersistentModifier(new EntityAttributeModifier(Identifier.of("chronicle_core","offerings/chronicle_test/orphan_death"),-2,EntityAttributeModifier.Operation.ADD_VALUE));
                    player.kill(world);check(!player.isAlive(),"orphan-price native death failed");
                }
                case "orphan_respawn_repair" -> {
                    var orphan=Identifier.of("chronicle_core","offerings/chronicle_test/orphan_death");
                    check(RuntimeHolds.reason(server)!=null,"death hid unknown authority instead of holding");
                    check(player.getAttributeInstance(EntityAttributes.MAX_HEALTH).hasModifier(orphan)&&player.getMaxHealth()==18,"death silently refunded unknown sacrificed heart");
                    player.getAttributeInstance(EntityAttributes.MAX_HEALTH).removeModifier(orphan);
                    AuthoringRuntime.get(server).enable(player.getUuid());GameSetupTools.execute(player.getCommandSource(),"chronicle_core:offerings","reload");AuthoringRuntime.get(server).disable(player.getUuid());
                    purchased(player);check(RuntimeHolds.reason(server)==null,"native orphan repair left hold");
                }
                case "save" -> {
                    purchased(player); safe(player); player.interactionManager.changeGameMode(GameMode.SURVIVAL);
                    // This account goes offline before closure and must be reconciled on its later login.
                    var other=server.getPlayerManager().getPlayerList().stream().filter(value->value!=player).findFirst().orElseThrow();
                    OfferingStats.purchase(other,AncientAltars.SOURCE,AncientAltars.MOVEMENT,UUID.randomUUID(),OfferingStats.preview(other,AncientAltars.SOURCE,AncientAltars.MOVEMENT));
                    check(FoodCapacity.capacity(other)==20&&other.getMaxHealth()==18,"offline-ending account fixture missing sacrificed heart");
                    pause(player); server.save(false,true,true); check(Files.exists(ledgerPath(server)),"offering ledger not on disk");
                    Files.writeString(server.getSavePath(WorldSavePath.ROOT).resolve("altar-trial.saved"),"six permanent purchases; no refund on death/reconnect/restart\n");
                }
                case "corrupt" -> corrupt(player);
                case "ending" -> { purchased(player); OfferingStats.closeSource(server,AncientAltars.SOURCE); ended(player); }
                case "ended" -> ended(player);
                case "render" -> render(player);
                case "itemrender" -> {player.getInventory().setStack(8,new ItemStack(QuietBelow.ANCIENT_ALTAR));player.getInventory().setSelectedSlot(8);player.playerScreenHandler.syncState();}
                default -> throw new IllegalArgumentException("Unknown Altar trial verb");
            }
            pass(source,verb,checks); return 1;
        } catch (Exception error) { fail(source,verb,error); return 0; }
    }
    private static void fixture(ServerCommandSource source) throws Exception {
        MinecraftServer server = source.getServer(); Path root = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
        check(root.getParent().getFileName().toString().equals("test-runs") && root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")
                && !server.isOnlineMode() && server.getServerPort()==25575 && server.getServerIp().equals("127.0.0.1"),"unsafe native fixture");
        source.getPlayerOrThrow();
    }
    private static void basic(ServerPlayerEntity player) throws Exception {
        var server=player.getEntityWorld().getServer(); String source="chronicle_test:basic";
        var health=player.getAttributeInstance(EntityAttributes.MAX_HEALTH); var speed=player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        Identifier foreign=Identifier.of("chronicle_test","unrelated");
        health.addPersistentModifier(new EntityAttributeModifier(foreign,4,EntityAttributeModifier.Operation.ADD_VALUE));
        speed.addPersistentModifier(new EntityAttributeModifier(foreign,.2,EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        player.setHealth(15); player.getHungerManager().setFoodLevel(17); float oldHealth=player.getHealth(); int oldFood=player.getHungerManager().getFoodLevel();
        OfferingStats.Preview last=null; UUID transaction=null;
        for(int index=0;index<3;index++){
            var preview=OfferingStats.preview(player,source,AncientAltars.EXTRA_HEART); check(preview.count()==index,"independent extra-heart count");
            transaction=UUID.randomUUID(); last=preview; OfferingStats.purchase(player,source,AncientAltars.EXTRA_HEART,transaction,preview);
            check(FoodCapacity.capacity(player)==22+index*2,"capacity did not gain one food icon");
            check(player.getMaxHealth()==26+index*2,"maximum heart delta incorrect");
            check(player.getHealth()==oldHealth && player.getHungerManager().getFoodLevel()==oldFood,"purchase refilled health or food");
        }
        long revision=OfferingStats.ledger(server).revision();
        OfferingStats.purchase(player,source,AncientAltars.EXTRA_HEART,transaction,last);
        check(OfferingStats.ledger(server).revision()==revision,"third-purchase retry duplicated the receipt");
        reject(()->OfferingStats.preview(player,source,AncientAltars.EXTRA_HEART),"fourth extra-heart purchase allowed");
        UUID accepted=transaction; OfferingStats.Preview acceptedPreview=last;
        reject(()->OfferingStats.purchase(player,source,AncientAltars.MOVEMENT,accepted,acceptedPreview),"mismatched transaction reuse allowed");
        for(int index=0;index<3;index++){
            var preview=OfferingStats.preview(player,source,AncientAltars.MOVEMENT); check(preview.count()==index,"movement count not independent");
            OfferingStats.purchase(player,source,AncientAltars.MOVEMENT,UUID.randomUUID(),preview);
        }
        check(player.getMaxHealth()==24 && FoodCapacity.capacity(player)==26,"combined lasting trades incorrect");
        check(Math.abs(player.getAttributeValue(EntityAttributes.MOVEMENT_SPEED)-.156)<.00001,"native foreign speed stacking incorrect");
        check(health.hasModifier(foreign) && speed.hasModifier(foreign),"foreign modifiers removed");
        reject(()->OfferingStats.preview(player,source,AncientAltars.MOVEMENT),"fourth movement purchase allowed");
        OfferingStats.closeSource(server,source);
        check(player.getMaxHealth()==24 && FoodCapacity.capacity(player)==20,"source closure did not remove both trade sides");
        check(Math.abs(player.getAttributeValue(EntityAttributes.MOVEMENT_SPEED)-.12)<.00001,"source closure removed unrelated speed");
        reject(()->OfferingStats.preview(player,source,AncientAltars.EXTRA_HEART),"closed source reopened");
        health.removeModifier(foreign);speed.removeModifier(foreign);
        check(player.getMaxHealth()==20,"foreign cleanup did not restore baseline");
        var stale=OfferingStats.preview(player,"chronicle_test:stale",AncientAltars.EXTRA_HEART);
        health.addTemporaryModifier(new EntityAttributeModifier(foreign,2,EntityAttributeModifier.Operation.ADD_VALUE));
        reject(()->OfferingStats.purchase(player,"chronicle_test:stale",AncientAltars.EXTRA_HEART,UUID.randomUUID(),stale),"changed foreign stats accepted stale preview");
        health.removeModifier(foreign);
        check(OfferingStats.ledger(server).totals(player.getUuid()).isEmpty(),"basic fixture left purchases behind");
    }
    private static void startFood(ServerCommandSource source) throws Exception {
        var player=source.getPlayerOrThrow(); var server=source.getServer(); var world=player.getEntityWorld();
        check(!FOOD.containsKey(server),"food run already active");
        server.setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);
        for(int index=0;index<3;index++) OfferingStats.purchase(player,"chronicle_test:food",AncientAltars.EXTRA_HEART,UUID.randomUUID(),OfferingStats.preview(player,"chronicle_test:food",AncientAltars.EXTRA_HEART));
        var hunger=player.getHungerManager(); hunger.setFoodLevel(20); hunger.setSaturationLevel(0);
        new ItemStack(Items.BREAD).finishUsing(world,player); check(hunger.getFoodLevel()==25,"native food consumption still clamps at20");
        new ItemStack(Items.BREAD).finishUsing(world,player); check(hunger.getFoodLevel()==26 && !hunger.isNotFull(),"native food did not clamp at capacity26");
        put(world,new BlockPos(3,89,3),Blocks.STONE.getDefaultState());player.teleport(world,3.5,90,3.5,Set.of(),0,0,false);
        foodState(player,23,0); FOOD.put(server,new FoodRun(source,player.getUuid(),checks));
    }
    private static void foodState(ServerPlayerEntity player,int food,float saturation) {
        player.setHealth(10); var hunger=player.getHungerManager(); hunger.setFoodLevel(food); hunger.setSaturationLevel(saturation);
        hunger.addExhaustion(40);hunger.addExhaustion(-40);
    }
    private static void tickFood(MinecraftServer server) {
        FoodRun run=FOOD.get(server);if(run==null)return;
        try {
            var player=server.getPlayerManager().getPlayer(run.player);check(player!=null&&player.isAlive(),"food fixture player disappeared");
            int duration=run.stage<2?100:12;if(++run.ticks<duration)return;
            switch(run.stage++){
                case 0 -> { check(player.getHealth()==10,"23/26 food incorrectly triggered regeneration");foodState(player,24,0); }
                case 1 -> { check(player.getHealth()>10,"24/26 food failed ordinary natural regeneration");foodState(player,25,5); }
                case 2 -> { check(player.getHealth()==10,"25/26 food incorrectly triggered fast saturated regeneration");foodState(player,26,5); }
                case 3 -> {
                    check(player.getHealth()>10,"26/26 full saturation failed fast natural regeneration");
                    OfferingStats.closeSource(server,"chronicle_test:food");
                    check(player.getMaxHealth()==20&&FoodCapacity.capacity(player)==20&&player.getHungerManager().getFoodLevel()<=20,"closure failed heart/food removal and clamp");
                    safe(player); FOOD.remove(server);pass(run.source,"food",checks+run.initialChecks);return;
                }
                default -> throw new IllegalStateException("food stage");
            }
            run.ticks=0;
        }catch(Exception error){FOOD.remove(server);fail(run.source,"food",error);}
    }
    private static void prepare(ServerPlayerEntity player) {
        var world=player.getEntityWorld();put(world,ALTAR,QuietBelow.ANCIENT_ALTAR_BLOCK.getDefaultState());safe(player);
        player.interactionManager.changeGameMode(GameMode.SURVIVAL);
        check(world.getBlockEntity(ALTAR) instanceof AncientAltarBlockEntity,"intrinsic Altar identity missing");
        check(QuietBelowEcology.stationPower(world,ALTAR)==null,"Altar not physically powered: "+QuietBelowEcology.stationPower(world,ALTAR));
        check(OfferingStats.ledger(world.getServer()).totals(player.getUuid()).isEmpty(),"initial Altar already has purchases");
        int index=0;for(Direction direction:Direction.Type.HORIZONTAL){
            BlockPos pos=new BlockPos(6+index++*4,65,8);put(world,pos,QuietBelow.ANCIENT_ALTAR_BLOCK.getDefaultState().with(AncientAltarBlock.FACING,direction));
            var box=world.getBlockState(pos).getCollisionShape(world,pos).getBoundingBox();
            check(box.minX==0&&box.minZ==0&&box.maxX==1&&box.maxZ==1&&box.maxY==1,"Altar base collision differs by facing");
        }
    }
    private static void safety(ServerPlayerEntity player) throws Exception {
        var server=player.getEntityWorld().getServer();var world=player.getEntityWorld();
        var other=server.getPlayerManager().getPlayerList().stream().filter(value->value!=player).findFirst().orElseThrow();
        AncientAltars.open(player,ALTAR);UUID first=token(player);AncientAltars.open(other,ALTAR);UUID second=token(other);
        check(first!=null&&second!=null&&!first.equals(second),"two actors lack distinct consent offers");
        LastingBargains.act(other,new OfferingPayloads.Action(first,1,0));LastingBargains.act(player,new OfferingPayloads.Action(second,1,0));
        check(OfferingStats.ledger(server).totals(player.getUuid()).isEmpty()&&OfferingStats.ledger(server).totals(other.getUuid()).isEmpty(),"cross-actor token accepted");
        LastingBargains.open(player,"chronicle_test:tokens",AncientAltars.GROUP,"Token trial",actor->null);
        UUID once=token(player);LastingBargains.act(player,new OfferingPayloads.Action(once,1,0));LastingBargains.act(player,new OfferingPayloads.Action(once,1,0));
        check(OfferingStats.ledger(server).count(player.getUuid(),"chronicle_test:tokens",AncientAltars.EXTRA_HEART.id())==1,"duplicate consent token repeated purchase");
        OfferingStats.closeSource(server,"chronicle_test:tokens");
        LastingBargains.open(player,"chronicle_test:pending",AncientAltars.GROUP,"Pending ending trial",actor->null);
        UUID ending=token(player);OfferingStats.closeSource(server,"chronicle_test:pending");LastingBargains.act(player,new OfferingPayloads.Action(ending,1,0));
        checkNoPurchase(player,"source ending accepted an outstanding consent offer");
        AncientAltars.open(player,ALTAR);first=token(player);
        pause(player);LastingBargains.act(player,new OfferingPayloads.Action(first,1,0));checkNoPurchase(player,"paused consent accepted");resume(player);
        player.interactionManager.changeGameMode(GameMode.CREATIVE);attempt(player);checkNoPurchase(player,"Creative consent accepted");player.interactionManager.changeGameMode(GameMode.SURVIVAL);
        AncientAltars.open(player,ALTAR);UUID distant=token(player);player.teleport(world,15.5,70,15.5,Set.of(),0,0,false);
        LastingBargains.act(player,new OfferingPayloads.Action(distant,1,0));checkNoPurchase(player,"out-of-range consent accepted");safe(player);
        AncientAltars.open(player,ALTAR);UUID replaced=token(player);put(world,ALTAR,Blocks.AIR.getDefaultState());put(world,ALTAR,QuietBelow.ANCIENT_ALTAR_BLOCK.getDefaultState());
        LastingBargains.act(player,new OfferingPayloads.Action(replaced,1,0));checkNoPurchase(player,"replaced station accepted old consent");
        AuthoringRuntime.get(server).enable(player.getUuid());attempt(player);checkNoPurchase(player,"editing author accepted bargain");AuthoringRuntime.get(server).disable(player.getUuid());
        PlayRuntime.setSelfParticipant(player,false);attempt(player);checkNoPurchase(player,"nonparticipant accepted bargain");PlayRuntime.setSelfParticipant(player,true);
        AncientAltars.open(player,ALTAR);UUID closed=token(player);LastingBargains.act(player,new OfferingPayloads.Action(closed,2,0));
        UUID successor=token(player);check(!successor.equals(closed),"refresh did not replace token");
        LastingBargains.act(player,new OfferingPayloads.Action(closed,3,0));LastingBargains.act(player,new OfferingPayloads.Action(successor,1,0));checkNoPurchase(player,"Close failed to revoke refreshed successor");
        AncientAltars.open(player,ALTAR);UUID cut=token(player);put(world,CONTACT,Blocks.AIR.getDefaultState());
        LastingBargains.act(player,new OfferingPayloads.Action(cut,1,0));checkNoPurchase(player,"cut network accepted bargain");
        check(QuietBelowEcology.stationPower(world,ALTAR)!=null,"cut network still powered");put(world,CONTACT,Blocks.SCULK.getDefaultState());
        check(FoodCapacity.capacity(other)==20,"other player's manager inherited expanded capacity");
    }
    private static void attempt(ServerPlayerEntity player) { AncientAltars.open(player,ALTAR);LastingBargains.act(player,new OfferingPayloads.Action(token(player),1,0)); }
    private static void checkNoPurchase(ServerPlayerEntity player,String message) { check(OfferingStats.ledger(player.getEntityWorld().getServer()).totals(player.getUuid()).isEmpty(),message); }
    private static void purchased(ServerPlayerEntity player) {
        var ledger=OfferingStats.ledger(player.getEntityWorld().getServer());
        check(ledger.count(player.getUuid(),AncientAltars.SOURCE,AncientAltars.EXTRA_HEART.id())==3,"extra-heart count did not persist");
        check(ledger.count(player.getUuid(),AncientAltars.SOURCE,AncientAltars.MOVEMENT.id())==3,"movement count did not persist");
        check(FoodCapacity.capacity(player)==26&&player.getMaxHealth()==20,"lasting native stats did not persist");
        check(Math.abs(player.getAttributeValue(EntityAttributes.MOVEMENT_SPEED)-.13)<.00001,"lasting movement did not persist");
        check(player.getAttributeInstance(OfferingAttributes.FOOD_CAPACITY).getPersistentModifiers().size()==1,"native food projection duplicated modifiers");
        for(var other:player.getEntityWorld().getServer().getPlayerManager().getPlayerList()) if(other!=player&&ledger.totals(other.getUuid()).isEmpty())
            check(FoodCapacity.capacity(other)==20,"expanded capacity leaked to a different player");
    }
    private static void corrupt(ServerPlayerEntity player) throws Exception {
        var server=player.getEntityWorld().getServer();var file=ledgerPath(server);byte[] original=Files.readAllBytes(file);var before=OfferingStats.ledger(server);
        var definition=new OfferingDefinition("chronicle_test:corrupt",new OfferingTerms(1,0,0),1);
        var preview=OfferingStats.preview(player,"chronicle_test:corrupt",definition);byte[] invalid="{invalid offering ledger}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try{
            Files.write(file,invalid);boolean failed=false;try{OfferingStats.purchase(player,"chronicle_test:corrupt",definition,UUID.randomUUID(),preview);}catch(java.io.IOException expected){failed=true;}
            check(failed&&RuntimeHolds.reason(server)!=null,"corrupt authority did not hold");
            check(Arrays.equals(Files.readAllBytes(file),invalid),"corrupt authority was replaced");
            check(player.getMaxHealth()==20&&FoodCapacity.capacity(player)==26&&Math.abs(player.getAttributeValue(EntityAttributes.MOVEMENT_SPEED)-.13)<.00001,"corrupt authority refunded persisted modifiers");
        }finally{
            Files.write(file,original);pause(player);AuthoringRuntime.get(server).enable(player.getUuid());
            GameSetupTools.execute(player.getCommandSource(),"chronicle_core:offerings","reload");AuthoringRuntime.get(server).disable(player.getUuid());
        }
        check(OfferingStats.ledger(server).equals(before)&&RuntimeHolds.reason(server)==null,"repaired authority did not reconcile exact ledger");
        // A syntactically valid but orphaned native modifier must hold instead of silently refunding.
        Identifier orphan=Identifier.of("chronicle_core","offerings/chronicle_test/orphan");
        player.getAttributeInstance(EntityAttributes.MAX_HEALTH).addPersistentModifier(new EntityAttributeModifier(orphan,2,EntityAttributeModifier.Operation.ADD_VALUE));
        AuthoringRuntime.get(server).enable(player.getUuid());boolean rejected=false;
        try{GameSetupTools.execute(player.getCommandSource(),"chronicle_core:offerings","reload");}catch(IllegalStateException expected){rejected=true;}
        check(rejected&&RuntimeHolds.reason(server)!=null&&player.getAttributeInstance(EntityAttributes.MAX_HEALTH).hasModifier(orphan),"orphan authority silently removed a modifier");
        player.getAttributeInstance(EntityAttributes.MAX_HEALTH).removeModifier(orphan);
        GameSetupTools.execute(player.getCommandSource(),"chronicle_core:offerings","reload");AuthoringRuntime.get(server).disable(player.getUuid());purchased(player);
    }
    private static void ended(ServerPlayerEntity player) throws Exception {
        var ledger=OfferingStats.ledger(player.getEntityWorld().getServer());
        check(ledger.closedSources().contains(AncientAltars.SOURCE),"ending tombstone missing");
        check(ledger.totals(player.getUuid()).isEmpty(),"ending kept bargain receipts");
        check(player.getMaxHealth()==20&&FoodCapacity.capacity(player)==20,"ending failed to remove bonus and sacrifice");
        check(Math.abs(player.getAttributeValue(EntityAttributes.MOVEMENT_SPEED)-.1)<.00001,"ending kept movement bonus");
        check(player.getHungerManager().getFoodLevel()<=20,"ending failed to clamp excess food");
        reject(()->OfferingStats.preview(player,AncientAltars.SOURCE,AncientAltars.EXTRA_HEART),"ending source reopened");
    }
    private static void render(ServerPlayerEntity player) { player.interactionManager.changeGameMode(GameMode.CREATIVE);player.getAbilities().flying=true;player.sendAbilitiesUpdate();player.teleport(player.getEntityWorld(),12,69,18,Set.of(),180,24,false); }
    private static void safe(ServerPlayerEntity player) { player.teleport(player.getEntityWorld(),3.5,66,3.5,Set.of(),0,0,false); }
    private static void pause(ServerPlayerEntity player) throws Exception { var service=ChronicleRuntime.service(player.getEntityWorld().getServer());if(service.state().phase().name().equals("ACTIVE"))service.pause(actor(player)); }
    private static void resume(ServerPlayerEntity player) throws Exception { ChronicleRuntime.service(player.getEntityWorld().getServer()).resume(actor(player)); }
    private static Actor actor(ServerPlayerEntity player) { return new Actor(player.getName().getString(),player.getUuidAsString()); }
    private static Path ledgerPath(MinecraftServer server) { return server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/offerings.json"); }
    private static void put(ServerWorld world,BlockPos pos,net.minecraft.block.BlockState state) { check(world.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4)!=null,"fixture attempted unloaded write");world.setBlockState(pos,state,Block.NOTIFY_ALL); }
    @SuppressWarnings("unchecked") private static UUID token(ServerPlayerEntity player) {
        try{
            Field field=LastingBargains.class.getDeclaredField("OFFERS");field.setAccessible(true);
            var servers=(Map<MinecraftServer,Map<UUID,?>>)field.get(null);var offers=servers.get(player.getEntityWorld().getServer());Object offer=offers==null?null:offers.get(player.getUuid());if(offer==null)return null;
            var method=offer.getClass().getDeclaredMethod("token");method.setAccessible(true);return (UUID)method.invoke(offer);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("fixture cannot inspect consent token",error);}
    }
    /** Age only this opt-in fixture offer; production uses the unchanged monotonic60s limit. */
    @SuppressWarnings("unchecked") private static void expire(ServerPlayerEntity player)throws Exception {
        var field=LastingBargains.class.getDeclaredField("OFFERS");field.setAccessible(true);
        var offers=((Map<MinecraftServer,Map<UUID,Object>>)field.get(null)).get(player.getEntityWorld().getServer());
        Object offer=offers.get(player.getUuid());check(offer!=null,"no offer to expire");
        var components=offer.getClass().getRecordComponents();Object[] values=new Object[components.length];
        Class<?>[] types=new Class<?>[components.length];
        for(int i=0;i<components.length;i++){
            var accessor=components[i].getAccessor();accessor.setAccessible(true);types[i]=components[i].getType();
            values[i]=components[i].getName().equals("created")?System.nanoTime()-61_000_000_000L:accessor.invoke(offer);
        }
        var constructor=offer.getClass().getDeclaredConstructor(types);constructor.setAccessible(true);
        offers.put(player.getUuid(),constructor.newInstance(values));
    }
    private static void check(boolean condition,String message) { if(!condition)throw new IllegalStateException(message);checks++; }
    private interface Throwing {void run()throws Exception;}
    private static void reject(Throwing action,String message)throws Exception {try{action.run();}catch(IllegalStateException expected){checks++;return;}throw new IllegalStateException(message);}
    private static void pass(ServerCommandSource source,String verb,int count) { source.sendFeedback(()->Text.literal("Bargain trial PASS "+verb+" ("+count+" checks)"),false); }
    private static void fail(ServerCommandSource source,String verb,Exception error) { QuietBelow.LOGGER.error("Altar native trial failed: "+verb,error);source.sendError(Text.literal("Bargain trial FAIL "+verb+": "+error)); }
    private static final class FoodRun { final ServerCommandSource source;final UUID player;final int initialChecks;int stage,ticks;FoodRun(ServerCommandSource source,UUID player,int checks){this.source=source;this.player=player;this.initialChecks=checks;} }
}
