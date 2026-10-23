package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.authoring.*;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.enchanting.*;
import io.github.chronicle.core.network.ItemEnchantingPayloads;
import io.github.chronicle.core.screen.ItemEnchantingScreenHandler;
import io.github.chronicle.core.server.*;
import io.github.chronicle.quietbelow.QuietBelow;
import io.github.chronicle.quietbelow.content.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static net.minecraft.server.command.CommandManager.literal;

/** Guarded disposable native evidence fixture for the sequential Ancient Forge ritual. */
public final class AncientForgeTrial {
    private static final BlockPos HEART=new BlockPos(-4,64,0),CONTACT=new BlockPos(-3,64,0),FORGE=new BlockPos(2,65,3),SAFE=new BlockPos(3,65,3);
    private static final String SOURCE="forge_trial_source",ROOT="forge_trial_heart";
    private static final Pattern RUN=Pattern.compile("[0-9]{8}T[0-9]{12,}Z");
    private static final ItemEnchantingPayloads.Artwork ART=new ItemEnchantingPayloads.Artwork(
            Identifier.of("quiet_below","workshop/forge_off"),Identifier.of("quiet_below","workshop/forge_idle"),Identifier.of("quiet_below","workshop/forge_working"));
    private static int checks,expectedHeat;private static long saturatedAt;
    private static final Map<BlockPos,net.minecraft.block.BlockState> isolatedNeighbors=new LinkedHashMap<>();
    private static LinkedHashSet<AncientForgeBlockEntity> savedModelTracker;
    private static TimingProbe timing;
    private AncientForgeTrial(){ }

    public static void register(){
        if(!"true".equals(System.getenv("CHRONICLE_FORGE_NATIVE_TRIAL")))return;
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            var root=literal("quiet_below_forge_trial").requires(source->source.hasPermissionLevel(2));
            for(String verb:List.of("prepare","ready","offer","transactions","check","result_cut","result_reconnect","invalid_begin","invalid_check","maxed_begin","maxed_check","reverse_begin","reverse_check","owned_close_begin","owned_close_ready","owned_close_check","save","restart","render","itemrender","model_off","model_kindling","model_active","model_cut","model_reconnect","model_pause","model_resume","model_disable","model_enable","model_isolated","model_adjacent","model_saturated_pause","model_check","feeding_age"))
                root.then(literal(verb).executes(context->run(context.getSource(),verb)));
            dispatcher.register(root);
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            TimingProbe probe=timing;
            if(probe==null||probe.done||probe.server!=server||probe.handler.phase()==ItemEnchantingPayloads.Phase.WORKING)return;
            probe.elapsed=(long)server.getTicks()-probe.started;probe.done=true;
            if(probe.elapsed<30)probe.failure="ritual resolved after only "+probe.elapsed+" server ticks";
        });
    }
    private static int run(ServerCommandSource source,String verb){
        checks=0;
        try{
            requireFixture(source);ServerWorld world=source.getWorld();ServerPlayerEntity player=source.getPlayerOrThrow();
            switch(verb){
                case "prepare"->prepare(source,world,player);
                case "ready"->ready(world);
                case "feeding_age"->feedingAge(world);
                case "offer"->offer(world,player);
                case "transactions"->transactions(world,player);
                case "check"->checkCompleted(world,player);
                case "result_cut"->resultCut(world,player);
                case "result_reconnect"->{put(world,CONTACT,Blocks.SCULK.getDefaultState());check(world.getBlockState(CONTACT).isOf(Blocks.SCULK),"physical reconnection placement failed");}
                case "invalid_begin"->invalidBegin(world,player);
                case "invalid_check"->invalidCheck(world,player);
                case "maxed_begin"->maxedBegin(world,player);
                case "maxed_check"->maxedCheck(world,player);
                case "reverse_begin"->reverseBegin(world,player);
                case "reverse_check"->reverseCheck(world,player);
                case "owned_close_begin"->ownedCloseBegin(world,player);
                case "owned_close_ready"->ownedCloseReady(world,player);
                case "owned_close_check"->ownedCloseCheck(world,player);
                case "save"->save(source,world,player);
                case "restart"->restart(source,world,player);
                case "render"->render(world,player);
                case "itemrender"->{player.getInventory().setStack(8,new ItemStack(QuietBelow.ANCIENT_FORGE));player.getInventory().setSelectedSlot(8);player.playerScreenHandler.syncState();check(player.getMainHandStack().isOf(QuietBelow.ANCIENT_FORGE),"Forge held model missing");}
                case "model_off","model_kindling","model_active","model_cut","model_reconnect","model_pause","model_resume","model_disable","model_enable","model_isolated","model_adjacent","model_saturated_pause"->model(world,player,verb);
                case "model_check"->modelCheck(world);
                default->throw new IllegalArgumentException("Unknown Forge trial verb");
            }
            int count=checks;source.sendFeedback(()->Text.literal("PASS Forge "+verb+" ("+count+" checks)"),false);return 1;
        }catch(Exception error){QuietBelow.LOGGER.error("Ancient Forge native trial failed: {}",verb,error);source.sendError(Text.literal("FAIL Forge "+verb+":"+detail(error)));return 0;}
    }

    private static void prepare(ServerCommandSource source,ServerWorld world,ServerPlayerEntity player)throws Exception{
        for(int x=-6;x<=18;x++)for(int z=-3;z<=18;z++)put(world,new BlockPos(x,63,z),Blocks.STONE.getDefaultState());
        put(world,HEART,Blocks.REINFORCED_DEEPSLATE.getDefaultState());
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){BlockPos at=HEART.add(x,y,z);if(!at.equals(HEART)&&!at.equals(CONTACT))put(world,at,Blocks.GLASS.getDefaultState());}
        for(int x=-3;x<=15;x++)put(world,new BlockPos(x,64,0),Blocks.SCULK.getDefaultState());
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)put(world,new BlockPos(x,64,z),Blocks.SCULK.getDefaultState());
        put(world,FORGE,QuietBelow.ANCIENT_FORGE_BLOCK.getDefaultState());put(world,SAFE,Blocks.STONE.getDefaultState());check(world.getBlockEntity(FORGE) instanceof AncientForgeBlockEntity,"Forge block entity missing");
        var authoring=AuthoringRuntime.get(source.getServer());authoring.enable(player.getUuid());String dimension=world.getRegistryKey().getValue().toString();
        var region=(AuthoringSite.Region)authoring.service().put(new AuthoringSite.Region(SOURCE,dimension,1,new BlockPosition(-4,64,0),new BlockPosition(15,64,15)),false);
        var heart=(AuthoringSite.Anchor)authoring.service().put(new AuthoringSite.Anchor(ROOT,dimension,1,new BlockPosition(-4,64,0),"minecraft:reinforced_deepslate"),false);
        MapSiteAssignments.assign(source,"quiet_below:source_city",region.id());MapSiteAssignments.assign(source,"quiet_below:relic_heart",heart.id());
        EcologyEngine engine=QuietBelowEcology.engineForTest(source.getServer());engine.configure(new EcologyEngine.Setup(region.id(),region.revision(),heart.id(),heart.revision(),dimension),new EcologyEngine.Area(new EcologyTerrain.Pos(-4,64,0),new EcologyTerrain.Pos(15,64,15)),QuietBelowEcology.pos(HEART));
        var terrain=new QuietBelowEcology.Adapter(world);for(int attempt=0;attempt<20&&!engine.view().indexed();attempt++)engine.index(terrain);check(engine.view().indexed()&&engine.cells().size()>=256,"source index incomplete");
        var rules=engine.vitality().rules();engine.tuneVitality(new EcologyVitality.Rules(rules.lossMinutes(),rules.recoveryMinutes(),Math.max(0,rules.extentPreset()-2)));engine.mode(true);
        ChronicleRuntime.service(source.getServer()).start(new Actor(player.getName().getString(),player.getUuidAsString()),null);PlayRuntime.setSelfParticipant(player,true);authoring.disable(player.getUuid());
        player.interactionManager.changeGameMode(GameMode.SURVIVAL);player.teleport(world,3.5,66,3.5,Set.of(),0,0,false);suppliedInputs(player);check(ChronicleRuntime.service(source.getServer()).state().phase().name().equals("ACTIVE"),"Core session not active");
    }
    private static void ready(ServerWorld world){check(QuietBelowEcology.stationPower(world,FORGE)==null,"Forge not powered: "+QuietBelowEcology.stationPower(world,FORGE));}
    private static void feedingAge(ServerWorld world)throws Exception{
        var engine=QuietBelowEcology.engineForTest(world.getServer());var rules=engine.vitality().rules();int speed=engine.speedPercent();BlockPos support=FORGE.down();
        check(world.getBlockState(support).isOf(Blocks.SCULK),"missing Forge support");for(Direction direction:Direction.values())if(direction!=Direction.UP)check(!world.getBlockState(support.offset(direction)).isAir(),"support has direct air outlet");
        try{engine.speed(25);engine.tuneVitality(new EcologyVitality.Rules(1,1,rules.extentPreset()));var terrain=new QuietBelowEcology.Adapter(world);for(int n=0;n<1500;n++){engine.beginTick();engine.tick(terrain,true);}engine.flushVitality();
            check(engine.vitality().mode(QuietBelowEcology.pos(support))==EcologyVitality.Mode.SHADED,"open Forge sealed/lit support");check(engine.living(QuietBelowEcology.pos(support)),"Forge starved support");check(engine.vitality().percent(QuietBelowEcology.pos(support))==100,"Forge support lost vitality");ready(world);
        }finally{engine.tuneVitality(rules);engine.speed(speed);}
    }

    private static void model(ServerWorld world,ServerPlayerEntity player,String verb)throws Exception{
        close(player);var engine=QuietBelowEcology.engineForTest(world.getServer());var actor=new Actor(player.getName().getString(),player.getUuidAsString());
        switch(verb){
            case "model_off","model_kindling","model_active"->{int preset=verb.equals("model_off")?4:verb.equals("model_kindling")?3:0;var rules=engine.vitality().rules();engine.tuneVitality(new EcologyVitality.Rules(rules.lossMinutes(),rules.recoveryMinutes(),preset));expectedHeat=verb.equals("model_off")?0:verb.equals("model_kindling")?1:2;if(verb.equals("model_off")){engine.speed(25);world.setTimeOfDay(18000);put(world,new BlockPos(7,65,9),Blocks.STONE.getDefaultState());player.teleport(world,7.5,66,9.5,Set.of(),140,18,false);}}
            case "model_cut"->{put(world,CONTACT,Blocks.AIR.getDefaultState());expectedHeat=0;}
            case "model_reconnect"->{put(world,CONTACT,Blocks.SCULK.getDefaultState());expectedHeat=2;}
            case "model_isolated"->{isolatedNeighbors.clear();for(Direction direction:Direction.values()){BlockPos at=FORGE.offset(direction);isolatedNeighbors.put(at,world.getBlockState(at));put(world,at,Blocks.GLASS.getDefaultState());}expectedHeat=0;}
            case "model_adjacent"->{isolatedNeighbors.forEach((at,state)->put(world,at,state));isolatedNeighbors.clear();expectedHeat=2;}
            case "model_disable"->{engine.mode(false);expectedHeat=0;}
            case "model_enable"->{engine.mode(true);expectedHeat=2;}
            case "model_saturated_pause"->{var trackers=modelTrackers();var queue=trackers.computeIfAbsent(world.getServer(),server->new LinkedHashSet<>());savedModelTracker=new LinkedHashSet<>(queue);var forge=(AncientForgeBlockEntity)world.getBlockEntity(FORGE);check(forge!=null&&world.getBlockState(FORGE).get(AncientForgeBlock.HEAT)==2,"saturation requires active Forge");queue.clear();for(int i=0;i<1023;i++)queue.add(new AncientForgeBlockEntity(new BlockPos(10000+i,65,10000),QuietBelow.ANCIENT_FORGE_BLOCK.getDefaultState()));queue.add(forge);saturatedAt=world.getServer().getTicks();ChronicleRuntime.service(world.getServer()).pause(actor);expectedHeat=0;}
            case "model_pause"->{ChronicleRuntime.service(world.getServer()).pause(actor);expectedHeat=0;}
            case "model_resume"->{ChronicleRuntime.service(world.getServer()).resume(actor);expectedHeat=2;world.setTimeOfDay(1000);player.teleport(world,3.5,66,3.5,Set.of(),0,0,false);}
            default->throw new IllegalArgumentException("Unknown model fixture");
        }
        check(engine.setup()!=null,"model fixture lost source setup");
    }
    @SuppressWarnings("unchecked") private static Map<MinecraftServer,LinkedHashSet<AncientForgeBlockEntity>> modelTrackers()throws ReflectiveOperationException{var field=AncientForges.class.getDeclaredField("LOADED");field.setAccessible(true);return(Map<MinecraftServer,LinkedHashSet<AncientForgeBlockEntity>>)field.get(null);}
    private static void modelCheck(ServerWorld world)throws Exception{
        var state=world.getBlockState(FORGE);check(state.isOf(QuietBelow.ANCIENT_FORGE_BLOCK),"model fixture lost Forge");check(state.get(AncientForgeBlock.HEAT)==expectedHeat,"block model disagrees with readiness");check(QuietBelowEcology.forgeHeat(world,FORGE)==expectedHeat,"appearance proof mismatch");check((QuietBelowEcology.stationPower(world,FORGE)==null)==(expectedHeat==2),"craft power mismatch");check(state.getLuminance()==(expectedHeat==2?12:0),"wrong luminance");int light=world.getLightLevel(net.minecraft.world.LightType.BLOCK,FORGE.up());check(expectedHeat==2?light>=10:light==0,"block light mismatch: "+light);
        if(savedModelTracker!=null){long elapsed=world.getServer().getTicks()-saturatedAt;check(elapsed>=100&&elapsed<=AncientForges.MAX_REFRESH_TICKS+40,"bounded refresh exceeded: "+elapsed);var queue=modelTrackers().get(world.getServer());queue.clear();queue.addAll(savedModelTracker);savedModelTracker=null;}
    }

    private static void offer(ServerWorld world,ServerPlayerEntity player){close(player);clearRelevant(world,player);suppliedInputs(player);int levels=player.experienceLevel;AncientForges.open(player,FORGE);var h=handler(player);check(h.entry().isEmpty()&&h.output().isEmpty(),"Forge did not open empty");check(h.getSlot(29).getStack().isOf(Items.IRON_SWORD)&&h.getSlot(30).getStack().isOf(Items.ECHO_SHARD),"native hotbar inputs missing");check(player.experienceLevel==levels,"opening changed XP");}
    private static void transactions(ServerWorld world,ServerPlayerEntity player)throws Exception{
        clearRelevant(world,player);var h=open(world,player);stage(h,blade());var authority=h.view();ItemEnchantingRuntime.offer(player,request(h,authority));check(h.phase()==ItemEnchantingPayloads.Phase.OFFERED&&h.entry().isEmpty(),"first offer not lodged");ItemEnchantingRuntime.offer(player,request(h,authority));check(h.phase()==ItemEnchantingPayloads.Phase.OFFERED,"nonce replay changed escrow");close(player);check(plainBlades(world,player)==1,"close lost first offering");
        clearRelevant(world,player);h=open(world,player);stage(h,blade());authority=h.view();ItemEnchantingRuntime.offer(player,new ItemEnchantingPayloads.Offer(h.syncId+1,authority.sessionId(),authority.actionToken()));ItemEnchantingRuntime.offer(player,new ItemEnchantingPayloads.Offer(h.syncId,UUID.randomUUID(),authority.actionToken()));ItemEnchantingRuntime.offer(player,new ItemEnchantingPayloads.Offer(h.syncId,authority.sessionId(),UUID.randomUUID()));ServerPlayerEntity second=world.getServer().getPlayerManager().getPlayerList().stream().filter(candidate->candidate!=player).findFirst().orElseThrow();ItemEnchantingRuntime.offer(second,request(h,authority));check(h.phase()==ItemEnchantingPayloads.Phase.EMPTY&&!h.entry().isEmpty(),"foreign authority moved entry");close(player);
        clearRelevant(world,player);h=startPair(world,player,blade(),new ItemStack(Items.ECHO_SHARD));close(player);check(plainBlades(world,player)==1&&echoCount(player)==1,"working close lost escrow");
        clearRelevant(world,player);for(int slot=0;slot<player.getInventory().size();slot++)player.getInventory().setStack(slot,new ItemStack(Items.COBBLESTONE,64));h=startPair(world,player,blade(),new ItemStack(Items.ECHO_SHARD));int blades=dropped(world,player,false),echoes=dropped(world,player,true);close(player);check(dropped(world,player,false)-blades==1&&dropped(world,player,true)-echoes==1,"full inventory did not drop exact escrow");for(int slot=0;slot<player.getInventory().size();slot++)player.getInventory().setStack(slot,ItemStack.EMPTY);
        clearRelevant(world,player);h=startPair(world,player,blade(),new ItemStack(Items.ECHO_SHARD));player.teleport(world,30.5,66,3.5,Set.of(),0,0,false);ItemEnchantingRuntime.changed(player,h);check(h.phase()==ItemEnchantingPayloads.Phase.EMPTY&&plainBlades(world,player)==1&&echoCount(player)==1,"range loss did not recover escrow");close(player);player.teleport(world,3.5,66,3.5,Set.of(),0,0,false);
        clearRelevant(world,player);h=startPair(world,player,blade(),new ItemStack(Items.ECHO_SHARD));put(world,FORGE,Blocks.AIR.getDefaultState());ItemEnchantingRuntime.changed(player,h);check(!(player.currentScreenHandler instanceof ItemEnchantingScreenHandler)&&plainBlades(world,player)==1&&echoCount(player)==1,"replacement did not recover escrow");put(world,FORGE,QuietBelow.ANCIENT_FORGE_BLOCK.getDefaultState());
        reloadCancellation(world,player);
        clearRelevant(world,player);h=startPair(world,player,blade(),new ItemStack(Items.ECHO_SHARD));put(world,CONTACT,Blocks.AIR.getDefaultState());ItemEnchantingRuntime.changed(player,h);check(h.phase()==ItemEnchantingPayloads.Phase.EMPTY&&plainBlades(world,player)==1&&echoCount(player)==1,"power loss did not recover escrow");put(world,CONTACT,Blocks.SCULK.getDefaultState());close(player);
        clearRelevant(world,player);suppliedInputs(player);
    }
    private static void reloadCancellation(ServerWorld world,ServerPlayerEntity player)throws Exception{
        Path config=FabricLoader.getInstance().getConfigDir().resolve("chronicle"),recipes=config.resolve("ingredient-enchantments.json"),caps=config.resolve("enchantment-caps.json");byte[] oldRecipes=Files.readAllBytes(recipes),oldCaps=Files.readAllBytes(caps);var actor=new Actor(player.getName().getString(),player.getUuidAsString());ChronicleRuntime.service(world.getServer()).pause(actor);AuthoringRuntime.get(world.getServer()).enable(player.getUuid());
        try{clearRelevant(world,player);var h=openCore(player);stage(h,blade());ItemEnchantingRuntime.offer(player,request(h,h.view()));var changed=IngredientEnchantmentConfig.decode(new String(oldRecipes,java.nio.charset.StandardCharsets.UTF_8)).stream().map(recipe->recipe.ingredient().equals("minecraft:echo_shard")?new IngredientEnchantmentRecipe(recipe.id(),recipe.group(),recipe.ingredient(),recipe.enchantment(),3,2,true):recipe).toList();Files.writeString(recipes,IngredientEnchantmentConfig.encode(changed));GameSetupTools.execute(player.getCommandSource(),"chronicle_core:item_enchanting","reload");check(h.phase()==ItemEnchantingPayloads.Phase.EMPTY&&plainBlades(world,player)==1,"recipe reload did not cancel escrow");close(player);
            clearRelevant(world,player);h=openCore(player);stage(h,blade());ItemEnchantingRuntime.offer(player,request(h,h.view()));Files.writeString(caps,EnchantmentCapConfig.encode(new EnchantmentCaps(true,1,Map.of("minecraft:sharpness",2))));GameSetupTools.execute(player.getCommandSource(),"chronicle_core:enchantment_caps","reload");ItemEnchantingRuntime.changed(player,h);check(h.phase()==ItemEnchantingPayloads.Phase.EMPTY&&plainBlades(world,player)==1,"cap reload did not cancel escrow");close(player);
        }finally{Files.write(recipes,oldRecipes);Files.write(caps,oldCaps);GameSetupTools.execute(player.getCommandSource(),"chronicle_core:item_enchanting","reload");GameSetupTools.execute(player.getCommandSource(),"chronicle_core:enchantment_caps","reload");AuthoringRuntime.get(world.getServer()).disable(player.getUuid());ChronicleRuntime.service(world.getServer()).resume(actor);}
    }
    private static void invalidBegin(ServerWorld world,ServerPlayerEntity player){clearRelevant(world,player);startTimedPair(world,player,blade(),new ItemStack(Items.FLINT));}
    private static void invalidCheck(ServerWorld world,ServerPlayerEntity player){var h=handler(player);assertTiming(h);check(h.phase()==ItemEnchantingPayloads.Phase.EMPTY&&plainBlades(world,player)==1&&count(player,Items.FLINT)==1,"invalid pair not returned");close(player);}
    private static void maxedBegin(ServerWorld world,ServerPlayerEntity player){clearRelevant(world,player);ItemStack maxed=blade();maxed.addEnchantment(sharpnessEntry(world),1);startTimedPair(world,player,maxed,new ItemStack(Items.ECHO_SHARD));}
    private static void maxedCheck(ServerWorld world,ServerPlayerEntity player){var h=handler(player);assertTiming(h);check(h.phase()==ItemEnchantingPayloads.Phase.EMPTY&&!findSharpness(world,player).isEmpty()&&echoCount(player)==1,"maxed pair not returned");close(player);}
    private static void reverseBegin(ServerWorld world,ServerPlayerEntity player){clearRelevant(world,player);startTimedPair(world,player,new ItemStack(Items.ECHO_SHARD,3),blade());}
    private static void reverseCheck(ServerWorld world,ServerPlayerEntity player){var h=handler(player);assertTiming(h);ItemStack result=sharpness(world,h.getCursorStack())==1?h.getCursorStack():findSharpness(world,player);check(!result.isEmpty()&&"Trial blade".equals(result.getName().getString())&&result.getOrDefault(DataComponentTypes.DAMAGE,0)==17,"reverse output lost components");check(echoCount(player)==2,"reverse remainder not exact");}
    private static void ownedCloseBegin(ServerWorld world,ServerPlayerEntity player){
        clearRelevant(world,player);for(int slot=0;slot<player.getInventory().size();slot++)player.getInventory().setStack(slot,ItemStack.EMPTY);
        startTimedPair(world,player,blade(),new ItemStack(Items.ECHO_SHARD));
        for(int slot=0;slot<player.getInventory().size();slot++)player.getInventory().setStack(slot,new ItemStack(Items.COBBLESTONE,64));
        player.getInventory().markDirty();
    }
    private static void ownedCloseReady(ServerWorld world,ServerPlayerEntity player){var h=handler(player);assertTiming(h);check(h.phase()==ItemEnchantingPayloads.Phase.RESULT&&!h.output().isEmpty(),"owned output did not commit; phase="+h.phase()+", available="+h.view().available()+", gate="+QuietBelowEcology.stationPower(world,FORGE));}
    private static void ownedCloseCheck(ServerWorld world,ServerPlayerEntity player){
        List<ItemEntity> results=world.getEntitiesByClass(ItemEntity.class,new Box(player.getBlockPos()).expand(5),entity->{
            ItemStack stack=entity.getStack();return sharpness(world,stack)==1&&"Trial blade".equals(stack.getName().getString())&&stack.getOrDefault(DataComponentTypes.DAMAGE,0)==17;
        });
        check(results.stream().mapToInt(entity->entity.getStack().getCount()).sum()==1,"closing with an uncollected result did not drop exactly one owned output");
        check(results.size()==1,"uncollected result was split or duplicated");
        for(int slot=0;slot<player.getInventory().size();slot++)player.getInventory().setStack(slot,ItemStack.EMPTY);
        ItemStack recovered=results.get(0).getStack().copy();results.get(0).discard();
        check(player.getInventory().insertStack(recovered)&&recovered.isEmpty(),"verified owned output could not be reclaimed for the remaining fixture");
    }
    private static void resultCut(ServerWorld world,ServerPlayerEntity player){var h=handler(player);check(h.phase()==ItemEnchantingPayloads.Phase.RESULT&&!h.output().isEmpty(),"committed result missing");put(world,CONTACT,Blocks.AIR.getDefaultState());ItemEnchantingRuntime.changed(player,h);check(h.phase()==ItemEnchantingPayloads.Phase.RESULT&&!h.output().isEmpty(),"power loss removed committed output");}
    private static void checkCompleted(ServerWorld world,ServerPlayerEntity player){ItemStack found=player.currentScreenHandler instanceof ItemEnchantingScreenHandler h&&sharpness(world,h.getCursorStack())==1?h.getCursorStack():findSharpness(world,player);check(!found.isEmpty()&&"Trial blade".equals(found.getName().getString())&&found.getOrDefault(DataComponentTypes.DAMAGE,0)==17,"native output lost components");}

    private static ItemEnchantingScreenHandler open(ServerWorld world,ServerPlayerEntity player){close(player);AncientForges.open(player,FORGE);return handler(player);}
    private static ItemEnchantingScreenHandler openCore(ServerPlayerEntity player){close(player);ItemEnchantingRuntime.open(player,AncientForges.GROUP,"Ancient Forge",ART,actor->ItemEnchantingPayloads.State.READY);return handler(player);}
    private static void stage(ItemEnchantingScreenHandler h,ItemStack stack){h.getSlot(ItemEnchantingScreenHandler.ENTRY_SLOT).setStack(stack.copy());ItemEnchantingRuntime.changed(h.owner(),h);check(h.view().offerable(),"entry not offerable");}
    private static ItemEnchantingScreenHandler startPair(ServerWorld world,ServerPlayerEntity player,ItemStack first,ItemStack second){var h=open(world,player);stage(h,first);ItemEnchantingRuntime.offer(player,request(h,h.view()));check(h.phase()==ItemEnchantingPayloads.Phase.OFFERED,"first not lodged");stage(h,second);ItemEnchantingRuntime.offer(player,request(h,h.view()));check(h.phase()==ItemEnchantingPayloads.Phase.WORKING,"second did not start ritual");return h;}
    private static ItemEnchantingScreenHandler startTimedPair(ServerWorld world,ServerPlayerEntity player,ItemStack first,ItemStack second){var h=startPair(world,player,first,second);check(timing==null||timing.done,"previous ritual timing proof still active");timing=new TimingProbe(world.getServer(),h,world.getServer().getTicks());return h;}
    private static void assertTiming(ItemEnchantingScreenHandler expected){TimingProbe probe=timing;check(probe!=null,"ritual timing probe is missing");check(probe.done,"ritual timing transition was not observed; phase="+probe.handler.phase()+", elapsed="+((long)probe.server.getTicks()-probe.started)+", available="+probe.handler.view().available());check(expected==null||probe.handler==expected,"ritual timing observed a different handler");check(probe.failure==null,probe.failure==null?"ritual timing failure":probe.failure);check(probe.elapsed>=30,"ritual resolved before thirty server ticks");timing=null;}
    private static ItemEnchantingPayloads.Offer request(ItemEnchantingScreenHandler h,ItemEnchantingPayloads.View view){return new ItemEnchantingPayloads.Offer(h.syncId,view.sessionId(),view.actionToken());}
    private static ItemEnchantingScreenHandler handler(ServerPlayerEntity player){check(player.currentScreenHandler instanceof ItemEnchantingScreenHandler,"Forge handler not open");return(ItemEnchantingScreenHandler)player.currentScreenHandler;}
    private static void close(ServerPlayerEntity player){if(player.currentScreenHandler instanceof ItemEnchantingScreenHandler)player.closeHandledScreen();}
    private static void suppliedInputs(ServerPlayerEntity player){player.getInventory().setStack(0,blade());player.getInventory().setStack(1,new ItemStack(Items.ECHO_SHARD,3));player.getInventory().setSelectedSlot(0);player.playerScreenHandler.syncState();}
    private static ItemStack blade(){ItemStack sword=new ItemStack(Items.IRON_SWORD);sword.set(DataComponentTypes.CUSTOM_NAME,Text.literal("Trial blade"));sword.set(DataComponentTypes.DAMAGE,17);return sword;}

    private static void save(ServerCommandSource source,ServerWorld world,ServerPlayerEntity player)throws Exception{
        close(player);ItemStack result=findSharpness(world,player);check(!result.isEmpty(),"save requires final output");check(world.getBlockEntity(FORGE) instanceof AncientForgeBlockEntity,"Forge missing before save");Path receipt=source.getServer().getSavePath(WorldSavePath.ROOT).resolve("quiet-below/forge-trial.json");Files.createDirectories(receipt.getParent());Files.writeString(receipt,"{\"sharpnessI\":true,\"ritual\":true}");player.interactionManager.changeGameMode(GameMode.SURVIVAL);player.teleport(world,3.5,66,3.5,Set.of(),0,0,false);clearRelevant(world,player);var h=open(world,player);stage(h,blade());ItemEnchantingRuntime.offer(player,request(h,h.view()));check(h.phase()==ItemEnchantingPayloads.Phase.OFFERED,"disconnect/restart fixture did not hold first offer");
    }
    private static void restart(ServerCommandSource source,ServerWorld world,ServerPlayerEntity player)throws Exception{String saved=Files.readString(source.getServer().getSavePath(WorldSavePath.ROOT).resolve("quiet-below/forge-trial.json"));check(saved.contains("ritual"),"receipt missing");check(world.getBlockEntity(FORGE) instanceof AncientForgeBlockEntity,"Forge missing after restart");check(plainBlades(world,player)+plainBladeDrops(world,player)==1,"disconnect/restart did not return or drop the private first offer exactly once");if(ChronicleRuntime.service(source.getServer()).state().phase().name().equals("ACTIVE"))ChronicleRuntime.service(source.getServer()).pause(new Actor(player.getName().getString(),player.getUuidAsString()));}
    private static void render(ServerWorld world,ServerPlayerEntity player){int index=0;for(Direction facing:Direction.Type.HORIZONTAL)for(int heat=0;heat<=2;heat++){BlockPos at=new BlockPos(8+(index%4)*3,65,8+(index++/4)*3);put(world,at,QuietBelow.ANCIENT_FORGE_BLOCK.getDefaultState().with(AncientForgeBlock.FACING,facing).with(AncientForgeBlock.HEAT,heat));VoxelShape collision=world.getBlockState(at).getCollisionShape(world,at);var box=collision.getBoundingBox();check(box.minX==0&&box.minY==0&&box.minZ==0&&box.maxX==1&&box.maxY==1&&box.maxZ==1,"Forge collision not full cube");}player.interactionManager.changeGameMode(GameMode.CREATIVE);player.getAbilities().flying=true;player.sendAbilitiesUpdate();player.teleport(world,22,70,23,Set.of(),140,24,false);}

    private static void clearRelevant(ServerWorld world,ServerPlayerEntity player){close(player);for(int slot=0;slot<player.getInventory().size();slot++){ItemStack stack=player.getInventory().getStack(slot);if(stack.isOf(Items.IRON_SWORD)||stack.isOf(Items.ECHO_SHARD)||stack.isOf(Items.FLINT)||stack.isOf(Items.BOOK)||stack.isOf(Items.ENCHANTED_BOOK))player.getInventory().setStack(slot,ItemStack.EMPTY);}for(ItemEntity entity:world.getEntitiesByClass(ItemEntity.class,new Box(player.getBlockPos()).expand(8),entity->true))entity.discard();player.setStackInHand(net.minecraft.util.Hand.OFF_HAND,ItemStack.EMPTY);}
    private static ItemStack findSharpness(ServerWorld world,ServerPlayerEntity player){if(player.currentScreenHandler instanceof ItemEnchantingScreenHandler h&&sharpness(world,h.output())>0)return h.output();for(int slot=0;slot<player.getInventory().size();slot++){ItemStack stack=player.getInventory().getStack(slot);if(sharpness(world,stack)>0)return stack;}return ItemStack.EMPTY;}
    private static int plainBlades(ServerWorld world,ServerPlayerEntity player){int count=0;for(int slot=0;slot<player.getInventory().size();slot++){ItemStack stack=player.getInventory().getStack(slot);if("Trial blade".equals(stack.getName().getString())&&stack.getOrDefault(DataComponentTypes.DAMAGE,0)==17&&sharpness(world,stack)==0)count+=stack.getCount();}return count;}
    private static int plainBladeDrops(ServerWorld world,ServerPlayerEntity player){return world.getEntitiesByClass(ItemEntity.class,new Box(player.getBlockPos()).expand(8),entity->{ItemStack stack=entity.getStack();return "Trial blade".equals(stack.getName().getString())&&stack.getOrDefault(DataComponentTypes.DAMAGE,0)==17&&sharpness(world,stack)==0;}).stream().mapToInt(entity->entity.getStack().getCount()).sum();}
    private static int echoCount(ServerPlayerEntity player){return count(player,Items.ECHO_SHARD);}
    private static int count(ServerPlayerEntity player,Item item){int count=0;for(int slot=0;slot<player.getInventory().size();slot++)if(player.getInventory().getStack(slot).isOf(item))count+=player.getInventory().getStack(slot).getCount();return count;}
    private static int dropped(ServerWorld world,ServerPlayerEntity player,boolean echoes){return world.getEntitiesByClass(ItemEntity.class,new Box(player.getBlockPos()).expand(5),item->echoes?item.getStack().isOf(Items.ECHO_SHARD):"Trial blade".equals(item.getStack().getName().getString())).stream().mapToInt(item->item.getStack().getCount()).sum();}
    private static RegistryEntry<Enchantment> sharpnessEntry(ServerWorld world){return world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(net.minecraft.registry.RegistryKey.of(RegistryKeys.ENCHANTMENT,Identifier.of("minecraft:sharpness")));}
    private static int sharpness(ServerWorld world,ItemStack stack){return stack.getOrDefault(DataComponentTypes.ENCHANTMENTS,ItemEnchantmentsComponent.DEFAULT).getLevel(sharpnessEntry(world));}
    private static void put(ServerWorld world,BlockPos pos,net.minecraft.block.BlockState state){check(world.getChunkManager().isChunkLoaded(pos.getX()>>4,pos.getZ()>>4),"fixture chunk unloaded");world.setBlockState(pos,state,Block.NOTIFY_LISTENERS);}
    private static void requireFixture(ServerCommandSource source)throws Exception{MinecraftServer server=source.getServer();Path root=server.getRunDirectory().toRealPath();check(server.isDedicated()&&!server.isOnlineMode()&&server.getServerPort()==25575&&"127.0.0.1".equals(server.getServerIp()),"trial requires offline loopback");check(root.getParent()!=null&&"test-runs".equals(root.getParent().getFileName().toString())&&RUN.matcher(root.getFileName().toString()).matches(),"trial requires disposable test-runs root");check(source.getPlayerOrThrow().hasPermissionLevel(2),"trial requires operator");}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);checks++;}
    private static String detail(Exception error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
    private static final class TimingProbe{final MinecraftServer server;final ItemEnchantingScreenHandler handler;final long started;boolean done;long elapsed;String failure;TimingProbe(MinecraftServer server,ItemEnchantingScreenHandler handler,long started){this.server=server;this.handler=handler;this.started=started;}}
}
