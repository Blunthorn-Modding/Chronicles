package io.github.chronicle.lastcouncil.server;

import com.google.gson.JsonObject;
import io.github.chronicle.lastcouncil.banquet.*;
import io.github.chronicle.lastcouncil.network.BanquetPayloads.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.WorldSavePath;
import static io.github.chronicle.lastcouncil.banquet.BanquetState.*;
import static net.minecraft.server.command.CommandManager.literal;

/** Explicit, guarded native item trial; it operates only in the disposable fixture. */
public final class BanquetTrial {
    private static final Map<UUID,Illness> CLOCKS=new HashMap<>();
    private static final Map<UUID,ServerPlayerEntity> DEAD=new HashMap<>();
    private static boolean dropped;
    public static boolean suppressGiftReply(ServerPlayerEntity player,Request request){
        if(dropped||request.action()!=Action.GIFT||!"1".equals(System.getenv("CHRONICLE_BANQUET_DROP_GIFT_REPLY"))||System.getenv("CHRONICLE_BANQUET_TRIAL")==null)return false;
        try{
            var server=player.getEntityWorld().getServer();check(server);if(!"ChronicleFabricA".equals(player.getName().getString()))return false;
            var runtime=method(BanquetRuntime.class,"runtime",MinecraftServer.class).invoke(null,server);var journal=(BanquetJournal)field(runtime,"journal").get(runtime);
            var receipt=journal.get(request.request());if(receipt==null||receipt.stage()!=BanquetJournal.Stage.COMPLETE)return false;
            JsonObject data=new JsonObject();data.addProperty("request",request.request().toString());data.addProperty("worldId",field(runtime,"worldId").get(runtime).toString());data.addProperty("supplies",BanquetRuntime.state(server).supplies().size());
            Path output=Path.of(System.getenv("CHRONICLE_BANQUET_TRIAL_REPORT")).toAbsolutePath().getParent().resolve("initial-drop-reply.json");
            require(!output.startsWith(server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize()),"unsafe reply-loss report");Files.writeString(output,data+"\n");dropped=true;return true;
        }catch(Exception failure){throw new IllegalStateException("Reply-loss fixture failed",failure);}
    }
    private BanquetTrial() { }
    public static void register() {
        if (System.getenv("CHRONICLE_BANQUET_TRIAL") == null) return;
        ServerLifecycleEvents.SERVER_STARTED.register(BanquetTrial::check);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(literal("chronicle").then(literal("banquet").then(literal("trial").then(literal("exercise").executes(context -> {
            ServerPlayerEntity player=context.getSource().getPlayerOrThrow(); MinecraftServer server=context.getSource().getServer();
            check(server); if(!"ChronicleFabricA".equals(player.getName().getString()) || server.getPlayerManager().getPlayer(player.getUuid())!=player)throw new IllegalStateException("Unsafe trial actor");
            try { exercise(server,player); write(true,"Item path completed"); return 1; }
            catch(Exception failure){Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();cause.printStackTrace();try{write(false,cause.toString());}catch(Exception ignored){} return 0;}
        }))))));
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            var trial=literal("trial");
            for(String mode:List.of("clock_start","clock_advance","clock_held","build_on","build_off","death_prepare","death_check","change_settings","critical","critical_cure","fatal_check"))trial.then(literal(mode).executes(c->{
                var server=c.getSource().getServer();check(server);var player=c.getSource().getPlayerOrThrow();
                if(!"ChronicleFabricA".equals(player.getName().getString()))throw new IllegalStateException("Unsafe trial actor");
                try{
                    if(mode.startsWith("build_"))io.github.chronicle.core.server.SessionBuilding.set(c.getSource(),mode.equals("build_on"));
                    var runtime=method(BanquetRuntime.class,"runtime",MinecraftServer.class).invoke(null,server);
                    if(mode.equals("change_settings")){
                        ((BanquetSettings)field(runtime,"settings").get(runtime)).save(new BanquetConfig(true,3,4,5));
                        var revision=field(runtime,"revision");revision.setLong(runtime,Math.incrementExact(revision.getLong(runtime)));
                        Files.writeString(Path.of(System.getenv("CHRONICLE_BANQUET_TRIAL_REPORT")+"."+mode),"PASS");return 1;
                    }
                    if (mode.startsWith("critical")) {
                        method(BanquetRuntime.class,"flush",runtime.getClass()).invoke(null,runtime);
                        var base = BanquetRuntime.state(server);
                        var next = BanquetTransitions.advance(base, 100000, Set.of(player.getUuid()));
                        require(next.illnesses().get(player.getUuid()).stage() == Stage.CRITICAL, "critical stage missing");
                        ((BanquetStore)field(runtime,"store").get(runtime)).save(next);
                        field(runtime,"state").set(runtime,next);
                        player.clearStatusEffects();
                        player.setHealth(1.0f);
                        player.getHungerManager().setFoodLevel(0);
                        player.getHungerManager().setSaturationLevel(0);
                        if (mode.equals("critical_cure")) {
                            method(BanquetRuntime.class,"symptoms",runtime.getClass(),ServerPlayerEntity.class).invoke(null,runtime,player);
                            require(player.getStatusEffect(StatusEffects.POISON).getAmplifier() == 1, "critical Poison II missing");
                            gift(player, 0, 0, 1);
                            equip(player, SupplyKind.ANTIDOTE, Hand.MAIN_HAND);
                            use(player, Hand.MAIN_HAND);
                            require(player.isAlive() && !BanquetRuntime.state(server).illnesses().containsKey(player.getUuid()), "critical cure failed");
                            require(!player.hasStatusEffect(StatusEffects.POISON), "critical cure retained owned Poison");
                            gift(player, 1, 1, 0);
                            drink(player);
                        }
                        Files.writeString(Path.of(System.getenv("CHRONICLE_BANQUET_TRIAL_REPORT")+"."+mode),"PASS");return 1;
                    }
                    if (mode.equals("fatal_check")) {
                        require(player.isSpectator() && BanquetRuntime.state(server).eliminated().contains(player.getUuid()),"fatal poisoning did not exclude the character");
                        require(!io.github.chronicle.core.server.PlayRuntime.available(server).participants().contains(player.getUuid()),"excluded character retained participation");
                        Files.writeString(Path.of(System.getenv("CHRONICLE_BANQUET_TRIAL_REPORT")+"."+mode),"PASS");return 1;
                    }
                    method(BanquetRuntime.class,"flush",runtime.getClass()).invoke(null,runtime);
                    var current=BanquetRuntime.state(server).illnesses().get(player.getUuid());require(current!=null,"illness missing during clock test");
                    if(mode.equals("clock_advance")){var before=CLOCKS.get(player.getUuid());require(before!=null&&current.onsetTicks()+current.durationTicks()<before.onsetTicks()+before.durationTicks(),"occupied active illness clock did not advance");}
                    else if(mode.equals("clock_held"))require(current.equals(CLOCKS.get(player.getUuid())),"held illness clock advanced");
                    else if(mode.equals("death_check"))require(DEAD.get(player.getUuid())!=player&&current.onsetTicks()+current.durationTicks()<=CLOCKS.get(player.getUuid()).onsetTicks()+CLOCKS.get(player.getUuid()).durationTicks(),"illness did not survive a real respawn unchanged");
                    else CLOCKS.put(player.getUuid(),current);
                    if(mode.equals("death_prepare")){DEAD.put(player.getUuid(),player);player.kill(player.getEntityWorld());}
                    Files.writeString(Path.of(System.getenv("CHRONICLE_BANQUET_TRIAL_REPORT")+"."+mode),"PASS");return 1;
                }catch(Exception e){try{Files.writeString(Path.of(System.getenv("CHRONICLE_BANQUET_TRIAL_REPORT")+"."+mode),e.toString());}catch(Exception ignored){}return 0;}
            }));
            dispatcher.register(literal("chronicle").then(literal("banquet").then(trial)));
        });
    }
    private static void check(MinecraftServer server) {
        Path root=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();
        Path expected=Path.of(System.getenv("CHRONICLE_BANQUET_TRIAL")).toAbsolutePath().normalize();
        if(root==null||!root.equals(expected)||root.getParent()==null||!root.getParent().getFileName().toString().equals("test-runs")
                ||!root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")||!Files.isRegularFile(root.resolve(".council-banquet-fixture"))
                ||!server.isDedicated()||server.isOnlineMode()||server.getServerPort()!=25575||!"127.0.0.1".equals(server.getServerIp()))throw new IllegalStateException("Unsafe banquet trial fixture");
    }
    private static void write(boolean success,String detail)throws Exception {
        Path root=Path.of(System.getenv("CHRONICLE_BANQUET_TRIAL")).toAbsolutePath().normalize();
        Path output=Path.of(System.getenv("CHRONICLE_BANQUET_TRIAL_REPORT")).toAbsolutePath().normalize();
        if(output.startsWith(root))throw new IllegalStateException("Report must be outside fixture");
        JsonObject result=new JsonObject();result.addProperty("result",success?"PASS":"FAIL");result.addProperty("detail",detail);
        Files.writeString(output,result+"\n",StandardCharsets.UTF_8);
    }
    private static void exercise(MinecraftServer server,ServerPlayerEntity player)throws Exception {
        Object runtime=method(BanquetRuntime.class,"runtime",MinecraftServer.class).invoke(null,server);
        Field journalField=field(runtime,"journal"),stateField=field(runtime,"state"),storeField=field(runtime,"store");
        BanquetJournal journal=(BanquetJournal)journalField.get(runtime);
        if("exclusion".equals(System.getenv("CHRONICLE_BANQUET_PHASE"))){
            require(BanquetRuntime.state(server).eliminated().contains(player.getUuid()) && player.isSpectator(), "fatal exclusion missing after restart");
            require(!io.github.chronicle.core.server.PlayRuntime.available(server).participants().contains(player.getUuid()), "dead character still participating");
            return;
        }
        if("restart".equals(System.getenv("CHRONICLE_BANQUET_PHASE"))){
            require(BanquetRuntime.state(server).illnesses().containsKey(player.getUuid()),"illness missing after restart");
            require(journal.receipts().size()>=8,"receipts missing after restart");return;
        }
        require(BanquetRuntime.canUse(player),"actor is not active eligible participant");
        Request gift=new Request(UUID.randomUUID(),Action.GIFT,0,null,player.getUuid(),3,3,1);
        String result=(String)method(BanquetRuntime.class,"gift",ServerPlayerEntity.class,Request.class).invoke(null,player,gift);
        require(result.equals("Supplies delivered."),"gift refused: "+result);
        require(journal.get(gift.request())!=null,"gift receipt missing");
        int initial=BanquetRuntime.state(server).supplies().size();require(initial==7,"wrong issued supply count");
        drink(player); BanquetState.Illness first=BanquetRuntime.state(server).illnesses().get(player.getUuid());
        require(first!=null&&first.onsetTicks()==2400&&first.durationTicks()==4800,"first illness timing mismatch");
        BanquetState advanced=BanquetTransitions.advance(BanquetRuntime.state(server),2500,Set.of(player.getUuid()));
        ((BanquetStore)storeField.get(runtime)).save(advanced);stateField.set(runtime,advanced);
        drink(player); BanquetState.Illness refreshed=BanquetRuntime.state(server).illnesses().get(player.getUuid());
        require(refreshed!=null&&refreshed.onsetTicks()==0&&refreshed.durationTicks()==4700,"repeat failed to refresh active illness without restarting incubation");
        advanced=BanquetTransitions.advance(BanquetRuntime.state(server),2400,Set.of(player.getUuid()));
        ((BanquetStore)storeField.get(runtime)).save(advanced);stateField.set(runtime,advanced);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA,200,1));
        var foreign=player.getStatusEffect(StatusEffects.NAUSEA);
        method(BanquetRuntime.class,"symptoms",runtime.getClass(),ServerPlayerEntity.class).invoke(null,runtime,player);
        require(player.getStatusEffect(StatusEffects.NAUSEA)==foreign,"foreign nausea overwritten");
        require(player.getStatusEffect(StatusEffects.SLOWNESS)!=null,"owned symptom missing");
        equip(player,SupplyKind.ANTIDOTE,Hand.MAIN_HAND);use(player,Hand.MAIN_HAND);
        require(player.getStatusEffect(StatusEffects.NAUSEA)==foreign,"cure removed foreign effect");
        require(player.getStatusEffect(StatusEffects.SLOWNESS)==null,"cure retained owned symptom");
        require(!BanquetRuntime.state(server).illnesses().containsKey(player.getUuid()),"antidote failed to cure");
        drink(player);require(BanquetRuntime.state(server).illnesses().containsKey(player.getUuid()),"final illness missing");
        require(journal.receipts().size()>=8,"missing operation receipts");
        recovery(server,player,runtime);
        exerciseFoods(server, player);
    }
    private static void exerciseFoods(MinecraftServer server, ServerPlayerEntity player) throws Exception {
        for (var item : net.minecraft.registry.Registries.ITEM) {
            if (!net.minecraft.registry.Registries.ITEM.getId(item).getNamespace().equals("minecraft")) continue;
            ItemStack ordinary = new ItemStack(item);
            if (!BanquetItems.edible(ordinary)) continue;
            gift(player, 0, 1, 0);
            equip(player, SupplyKind.POISON, Hand.MAIN_HAND);
            int count = ordinary.isOf(net.minecraft.item.Items.BREAD) ? 4 : 1;
            ordinary.setCount(count);
            player.setStackInHand(Hand.OFF_HAND, ordinary);
            require(BanquetRuntime.contaminate(player), "could not contaminate " + item);
            ItemStack serving = player.getOffHandStack();
            require(serving.getCount() == 1 && serving.isOf(item), "wrong poisoned portion count");
            require(serving.getName().equals(ordinary.getName()), "poisoning changed the food name");
            require(serving.get(net.minecraft.component.DataComponentTypes.FOOD).equals(ordinary.get(net.minecraft.component.DataComponentTypes.FOOD)), "nutrition changed");
            if (count > 1) require(player.getMainHandStack().getCount() == count - 1 && !BanquetItems.trackedFood(player.getMainHandStack()), "clean stack remainder lost");
            if (ordinary.isOf(net.minecraft.item.Items.BREAD)) {
                var pos = player.getBlockPos().add(3, 0, 0);
                player.getEntityWorld().setBlockState(pos, net.minecraft.block.Blocks.CHEST.getDefaultState());
                var chest = (net.minecraft.block.entity.ChestBlockEntity)player.getEntityWorld().getBlockEntity(pos);
                chest.setStack(0, serving.copy());
                player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
                ItemStack stored = chest.removeStack(0);
                var dropped = player.dropItem(stored, false, false);
                require(dropped != null, "food drop missing");
                dropped.resetPickupDelay();
                dropped.onPlayerCollision(player);
                equip(player, SupplyKind.FOOD, Hand.OFF_HAND);
                require(BanquetItems.token(player.getOffHandStack(), SupplyKind.FOOD).equals(BanquetItems.token(serving, SupplyKind.FOOD)), "storage/drop lost food identity");
            }
            ItemStack copied = player.getOffHandStack().copy();
            var remainder = copied.get(net.minecraft.component.DataComponentTypes.USE_REMAINDER);
            player.getHungerManager().setFoodLevel(10);
            player.setCurrentHand(Hand.OFF_HAND);
            ItemStack result = ordinary.isOf(net.minecraft.item.Items.BREAD) ? eatWithSaveFailure(server, player) : player.getOffHandStack().finishUsing(player.getEntityWorld(), player);
            player.stopUsingItem();
            require(player.getHungerManager().getFoodLevel() > 10, "vanilla nutrition did not apply for " + item);
            require(remainder == null ? result.isEmpty() : ItemStack.areEqual(result, remainder.convertInto()), "vanilla container remainder changed for " + item);
            require(BanquetRuntime.state(server).supplies().get(BanquetItems.token(copied, SupplyKind.FOOD)).usedBy().equals(player.getUuid()), "food exposure not committed");
            if (ordinary.isOf(net.minecraft.item.Items.GOLDEN_APPLE)) require(player.hasStatusEffect(StatusEffects.ABSORPTION), "golden apple effects lost");
            if (ordinary.isOf(net.minecraft.item.Items.PUFFERFISH)) require(player.hasStatusEffect(StatusEffects.POISON), "pufferfish effects lost");
            player.setStackInHand(Hand.OFF_HAND, copied);
            player.setCurrentHand(Hand.OFF_HAND);
            copied.finishUsing(player.getEntityWorld(), player);
            player.stopUsingItem();
            require(ItemStack.areEqual(copied, player.getOffHandStack()), "copied token replay consumed another food");
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            player.clearStatusEffects();
        }
        gift(player, 0, 1, 1);
        equip(player, SupplyKind.ANTIDOTE, Hand.MAIN_HAND);
        use(player, Hand.MAIN_HAND);
        require(!BanquetRuntime.state(server).illnesses().containsKey(player.getUuid()), "food illness not cured");
        equip(player, SupplyKind.POISON, Hand.MAIN_HAND);
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(net.minecraft.item.Items.MUSHROOM_STEW));
        require(BanquetRuntime.contaminate(player), "stew preparation failed");
        player.setStackInHand(Hand.MAIN_HAND, player.getOffHandStack());
        player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
        player.getHungerManager().setFoodLevel(16);
    }
    private static ItemStack eatWithSaveFailure(MinecraftServer server, ServerPlayerEntity player) throws Exception {
        check(server);
        Path world = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        Path data = world.resolve("playerdata"), backup = world.resolve("playerdata-food-trial");
        require(data.getParent().equals(world) && backup.getParent().equals(world) && !Files.isSymbolicLink(data) && !Files.exists(backup), "unsafe food failure fixture");
        BanquetState before = BanquetRuntime.state(server);
        Files.move(data, backup);
        try {
            Files.writeString(data, "food trial write failure\n");
            player.getOffHandStack().finishUsing(player.getEntityWorld(), player);
            require(BanquetRuntime.state(server).equals(before), "food ledger published after failed inventory save");
        } finally {
            if (Files.isRegularFile(data) && Files.readString(data).equals("food trial write failure\n")) Files.delete(data);
            require(!Files.exists(data), "food blocker changed");
            Files.move(backup, data);
        }
        Object runtime = method(BanquetRuntime.class, "runtime", MinecraftServer.class).invoke(null, server);
        require(((BanquetJournal)field(runtime, "journal").get(runtime)).pending() != null, "food save failure lost its pending intent");
        method(BanquetRuntime.class, "recover", MinecraftServer.class, runtime.getClass()).invoke(null, server, runtime);
        require(((BanquetJournal)field(runtime, "journal").get(runtime)).pending() == null, "food after-inventory recovery failed");
        return player.getOffHandStack();
    }
    private static void gift(ServerPlayerEntity player, int drinks, int poison, int antidotes) throws Exception {
        var request = new Request(UUID.randomUUID(), Action.GIFT, 0, null, player.getUuid(), drinks, poison, antidotes);
        method(BanquetRuntime.class, "gift", ServerPlayerEntity.class, Request.class).invoke(null, player, request);
    }
    private static void recovery(MinecraftServer server,ServerPlayerEntity player,Object runtime)throws Exception{
        check(server);
        Path world=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        Path data=world.resolve("playerdata"),backup=world.resolve("playerdata-banquet-trial");
        require(data.getParent().equals(world)&&backup.getParent().equals(world)&&!Files.isSymbolicLink(data)&&!Files.exists(backup),"unsafe playerdata failure target");
        BanquetState before=BanquetRuntime.state(server);Request gift=new Request(UUID.randomUUID(),Action.GIFT,0,null,player.getUuid(),1,0,0);
        Files.move(data,backup);
        try{
            Files.writeString(data,"banquet trial write failure\n");
            boolean refused=false;try{method(BanquetRuntime.class,"gift",ServerPlayerEntity.class,Request.class).invoke(null,player,gift);}catch(InvocationTargetException expected){refused=expected.getCause() instanceof java.io.IOException;}
            require(refused,"failed playerdata write was not detected");
            require(BanquetRuntime.state(server).equals(before),"ledger advanced after unconfirmed playerdata save");
            require(((BanquetJournal)field(runtime,"journal").get(runtime)).pending()!=null,"failed save discarded inventory evidence");
        }finally{
            if(Files.isRegularFile(data)&&Files.readString(data).equals("banquet trial write failure\n"))Files.delete(data);
            require(!Files.exists(data),"playerdata blocker changed; preserve trial evidence");Files.move(backup,data);
        }
        method(BanquetRuntime.class,"recover",MinecraftServer.class,runtime.getClass()).invoke(null,server,runtime);
        require(((BanquetJournal)field(runtime,"journal").get(runtime)).pending()==null,"after inventory recovery remained held");
        int issued=BanquetRuntime.state(server).supplies().size();require(issued==before.supplies().size()+1,"saved after inventory did not commit exactly once");
        method(BanquetRuntime.class,"gift",ServerPlayerEntity.class,Request.class).invoke(null,player,gift);
        require(BanquetRuntime.state(server).supplies().size()==issued,"same nonce duplicated recovered gift");
        for(boolean mixed:new boolean[]{false,true}){
            var snapshot=(io.github.chronicle.core.content.InventorySnapshot)method(BanquetRuntime.class,"inventory",MinecraftServer.class,ServerPlayerEntity.class).invoke(null,server,player);
            var stacks=new ArrayList<>(io.github.chronicle.core.server.InventorySnapshots.decode(server,snapshot));int slot=-1;for(int i=0;i<36;i++)if(stacks.get(i).isEmpty()){slot=i;break;}require(slot>=0,"fixture inventory full");
            UUID request=UUID.randomUUID(),token=UUID.randomUUID();BanquetState base=BanquetRuntime.state(server);
            BanquetState after=BanquetTransitions.issue(base,base.revision(),UUID.randomUUID(),player.getUuid(),token,SupplyKind.DRINK);
            stacks.set(slot,BanquetItems.stack(SupplyKind.DRINK,token));
            var afterInventory=io.github.chronicle.core.server.InventorySnapshots.capture(server,new net.minecraft.inventory.SimpleInventory(stacks.toArray(ItemStack[]::new)));
            String hash=(String)method(BanquetRuntime.class,"ledgerHash",BanquetState.class).invoke(null,base);
            BanquetJournal current=(BanquetJournal)field(runtime,"journal").get(runtime);
            current.claim(new BanquetJournal.Intent(request,player.getUuid(),player.getUuid(),"0".repeat(64),"Fixture interrupted preparation",hash,after,snapshot,afterInventory));
            io.github.chronicle.core.server.RuntimeHolds.hold(server,BanquetRuntime.HOLD,"Fixture prepared operation");
            if(mixed)player.getInventory().setStack(slot,new ItemStack(net.minecraft.item.Items.STONE));
            // Reload actual persisted ledger/journal into a new runtime before inspecting inventory.
            method(BanquetRuntime.class,"start",MinecraftServer.class).invoke(null,server);
            runtime=method(BanquetRuntime.class,"runtime",MinecraftServer.class).invoke(null,server);
            method(BanquetRuntime.class,"recover",MinecraftServer.class,runtime.getClass()).invoke(null,server,runtime);
            current=(BanquetJournal)field(runtime,"journal").get(runtime);
            if(mixed){
                require(current.pending()!=null&&player.getInventory().getStack(slot).isOf(net.minecraft.item.Items.STONE),"mixed evidence was overwritten or completed");
                method(BanquetRuntime.class,"apply",ServerPlayerEntity.class,List.class).invoke(null,player,io.github.chronicle.core.server.InventorySnapshots.decode(server,snapshot));
                method(BanquetRuntime.class,"recover",MinecraftServer.class,runtime.getClass()).invoke(null,server,runtime);
            }
            require(current.pending()==null&&current.get(request).stage()==BanquetJournal.Stage.CANCELLED,"before evidence did not cancel safely");
            require(BanquetRuntime.state(server).equals(base),"cancelled preparation changed ledger");
        }
    }
    private static void drink(ServerPlayerEntity player){
        equip(player,SupplyKind.DRINK,Hand.OFF_HAND);equip(player,SupplyKind.POISON,Hand.MAIN_HAND);
        ItemStack before=player.getOffHandStack().copy();
        player.getMainHandStack().getItem().use(player.getEntityWorld(),player,Hand.MAIN_HAND);
        require(player.getMainHandStack().isEmpty(),"poison was not consumed");
        require(ItemStack.areEqual(before,player.getOffHandStack()),"contamination changed drink appearance/components");
        use(player,Hand.OFF_HAND);
    }
    private static void use(ServerPlayerEntity player,Hand hand){
        ItemStack stack=player.getStackInHand(hand);player.getHungerManager().setFoodLevel(20);
        stack.getItem().use(player.getEntityWorld(),player,hand);
        require(player.isUsingItem()&&stack.getItem().getMaxUseTime(stack,player)==32,"full-hunger use did not start");
        stack.getItem().finishUsing(stack,player.getEntityWorld(),player);player.stopUsingItem();
        require(!ItemStack.areEqual(stack,player.getStackInHand(hand)),"supply was not consumed");
    }
    private static void equip(ServerPlayerEntity player,SupplyKind kind,Hand hand){
        int target=hand==Hand.OFF_HAND?40:player.getInventory().getSelectedSlot();
        for(int i=0;i<36;i++){
            ItemStack stack=player.getInventory().getStack(i);
            try{BanquetItems.token(stack,kind);}catch(RuntimeException invalid){continue;}
            ItemStack displaced=player.getInventory().getStack(target);player.getInventory().setStack(target,stack);if(i!=target)player.getInventory().setStack(i,displaced);player.getInventory().markDirty();return;
        }
        throw new IllegalStateException("Missing "+kind);
    }
    private static Method method(Class<?> type,String name,Class<?>... parameters)throws Exception{Method method=type.getDeclaredMethod(name,parameters);method.setAccessible(true);return method;}
    private static Field field(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field;}
    private static void require(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}
}
