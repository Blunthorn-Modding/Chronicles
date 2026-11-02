package io.github.chronicle.quietbelow.content;

import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.offering.temporary.TemporaryOfferingClaim;
import io.github.chronicle.core.offering.OfferingRefusal;
import io.github.chronicle.core.offering.OfferingCooldown;
import io.github.chronicle.core.network.OfferingPayloads;
import io.github.chronicle.core.screen.OfferingScreenHandler;
import io.github.chronicle.core.offering.temporary.TemporaryOfferingTerms;
import io.github.chronicle.core.server.*;
import io.github.chronicle.core.stats.OfferingAttributes;
import io.github.chronicle.quietbelow.QuietBelow;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

import java.nio.file.Path;
import java.util.*;

import static net.minecraft.server.command.CommandManager.literal;

/** Disposable native proof for temporary Altar authority. Never registered without its explicit environment guard. */
public final class TemporaryAltarTrial {
    private static final BlockPos ALTAR=new BlockPos(2,65,3),CONTACT=new BlockPos(-3,64,0);
    private static final Map<MinecraftServer,Run> RUNS=new IdentityHashMap<>();
    private static final TemporaryOfferings.Definition UNCERTAIN=new TemporaryOfferings.Definition("chronicle_test:uncertain_altar",new TemporaryOfferings.Handler(){
        @Override public TemporaryOfferingTerms preview(ServerPlayerEntity player,TemporaryOfferings.Station station){return new TemporaryOfferingTerms(0,0,0,Map.of("fixture","deliberate-after-claim-failure"));}
        @Override public void commit(ServerPlayerEntity player,TemporaryOfferings.Station station,TemporaryOfferingClaim claim){throw new IllegalStateException("deliberate native uncertainty");}
    });
    private TemporaryAltarTrial(){}
    public static void register(){if(!"true".equals(System.getenv("CHRONICLE_TEMPORARY_ALTAR_TRIAL")))return;TemporaryOfferings.registerDefinition(UNCERTAIN);CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
        var root=literal("chronicle_temporary_altar_trial").requires(source->source.hasPermissionLevel(2));
        for(String verb:List.of("ui_prepare","ui_open","ui_refused_check","ui_cooldown_open","ui_cooldown_check","ui_expiry_open","ui_expiry_check","ui_safety","ui_check","prepare","purchase","foreign_check","owned_check","death","respawn_check","reconnect_check","restart_check","pause","paused_check","resume","expired_check","uncertainty","ending"))root.then(literal(verb).executes(context->run(context.getSource(),verb)));
        dispatcher.register(root);
    });ServerLifecycleEvents.SERVER_STOPPED.register(RUNS::remove);}
    private static int run(ServerCommandSource source,String verb){int checks=0;try{fixture(source);ServerPlayerEntity player=source.getPlayerOrThrow();checks=switch(verb){
        case "ui_expiry_open"->uiExpiryOpen(player);case "ui_expiry_check"->uiExpiryCheck(player);
        case "ui_refused_check"->uiRefused(player);case "ui_cooldown_open"->uiCooldownOpen(player);case "ui_cooldown_check"->uiCooldownCheck(player);case "ui_safety"->uiSafety(player);case "ui_prepare"->uiPrepare(player);case "ui_open"->uiOpen(player);case "ui_check"->uiCheck(player);case "prepare"->prepare(player);case "purchase"->purchase(player);case "foreign_check"->foreign(player);case "owned_check"->owned(player);case "death"->death(player);case "respawn_check","reconnect_check","restart_check"->persistent(player,verb);case "pause"->pause(player);case "paused_check"->paused(player);case "resume"->resume(player);case "expired_check"->expired(player);case "uncertainty"->uncertainty(player);case "ending"->ending(player);default->throw new IllegalArgumentException("unknown temporary Altar trial verb");};int passed=checks;source.sendFeedback(()->Text.literal("Temporary Altar trial PASS "+verb+" ("+passed+" checks)"),false);return 1;}catch(Exception error){QuietBelow.LOGGER.error("Temporary Altar native trial failed: "+verb,error);source.sendError(Text.literal("Temporary Altar trial FAIL "+verb+": "+error.getMessage()));return 0;}}
    private static int uiPrepare(ServerPlayerEntity player)throws Exception{MinecraftServer server=player.getEntityWorld().getServer();ServerWorld world=player.getEntityWorld();Actor actor=actor(player);var campaign=ChronicleRuntime.service(server);if(campaign.state().phase()==SessionPhase.INACTIVE)campaign.start(actor,null);if(campaign.state().phase()==SessionPhase.PAUSED)campaign.resume(actor);AuthoringRuntime.get(server).disable(player.getUuid());PlayRuntime.setSelfParticipant(player,true);player.interactionManager.changeGameMode(GameMode.SURVIVAL);put(world,ALTAR,QuietBelow.ANCIENT_ALTAR_BLOCK.getDefaultState());put(world,CONTACT,Blocks.SCULK.getDefaultState());player.teleport(world,3.5,66,3.5,Set.of(),0,0,false);player.getInventory().clear();ItemStack armor=new ItemStack(Items.DIAMOND_CHESTPLATE);armor.setDamage(400);player.getInventory().setStack(0,armor);player.getInventory().setSelectedSlot(0);player.experienceProgress=0;player.addExperienceLevels(12-player.experienceLevel);RUNS.put(server,new Run(player.getUuid(),armor.getDamage(),player.experienceLevel,player.getHungerManager().getFoodLevel(),new UUID(0,0)));var config=AltarOfferingConfig.get();AltarOfferingConfig.replaceForTest(AltarOfferingConfig.encode(new AltarOfferingConfig(config.emergency(),config.repair(),config.sight(),config.harvest(),15)));
        world.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false,server);player.setHealth(16);player.addExperienceLevels(-player.experienceLevel);
        require(io.github.chronicle.quietbelow.ecology.QuietBelowEcology.stationPower(world,ALTAR)==null,"UI Altar is not physically powered");return 6;}
    private static int uiRefused(ServerPlayerEntity player){
        require(player.getHealth()==14&&player.getMaxHealth()==20,"failed prayer did not deal ordinary 2 HP without changing max health");
        require(TemporaryOfferings.ledger(player.getEntityWorld().getServer()).claims().isEmpty()&&OfferingStats.ledger(player.getEntityWorld().getServer()).receipts().isEmpty(),"failed prayer granted or paid a trade");
        player.closeHandledScreen();player.addExperienceLevels(12);player.setHealth(16);return 3;
    }
    private static int uiCooldownOpen(ServerPlayerEntity player){
        var world=player.getEntityWorld();BlockPos second=ALTAR.east(2);put(world,second,io.github.chronicle.quietbelow.QuietBelow.ANCIENT_ALTAR_BLOCK.getDefaultState());
        player.teleport(world,4.5,66,3.5,Set.of(),0,0,false);AncientAltars.open(player,second);return 1;
    }
    private static int uiCooldownCheck(ServerPlayerEntity player)throws Exception{
        var server=player.getEntityWorld().getServer();require(player.getHealth()==14&&player.getMaxHealth()==20,"shared cooldown refusal changed wrong stats or replayed damage");
        require(OfferingStats.ledger(server).receipts().isEmpty(),"temporary success did not block lasting prayer at another Altar");
        var claim=TemporaryOfferings.ledger(server).claims().getFirst();require(OfferingCooldown.until(claim)==claim.claimedAtTicks()+300,"temporary claim did not atomically capture configured cooldown");
        require(TemporaryOfferings.maintain(server)==0&&TemporaryOfferings.ledger(server).claims().contains(claim),"maintenance removed ongoing cooldown evidence");
        return 4;
    }
    private static UUID token(ServerPlayerEntity player){return ((OfferingScreenHandler)player.currentScreenHandler).view().token();}
    private static int uiExpiryOpen(ServerPlayerEntity player){
        var server=player.getEntityWorld().getServer();
        require(PlayRuntime.elapsedTicks(server)>=OfferingCooldown.until(OfferingStats.ledger(server),TemporaryOfferings.ledger(server),player.getUuid(),AncientAltars.SOURCE),"occupied cooldown did not expire");
        require(OfferingStats.ledger(server).receipts().isEmpty(),"a refused lasting prayer left a receipt");
        player.closeHandledScreen();player.teleport(player.getEntityWorld(),3.5,66,3.5,Set.of(),0,0,false);
        AncientAltars.open(player,ALTAR);return 2;
    }
    private static int uiExpiryCheck(ServerPlayerEntity player){
        var server=player.getEntityWorld().getServer();var receipts=OfferingStats.ledger(server).receipts();
        require(receipts.size()==1&&receipts.getFirst().offering().equals(AncientAltars.MOVEMENT.id()),"post-cooldown lasting prayer failed or repeated");
        var receipt=receipts.getFirst();require(receipt.cooldownUntilTicks()==receipt.acceptedAtTicks()+300,"new lasting prayer did not capture its cooldown");
        require(player.getMaxHealth()==18,"post-cooldown lasting sacrifice missing");return 3;
    }
    private static int uiSafety(ServerPlayerEntity player)throws Exception{
        var server=player.getEntityWorld().getServer();var world=player.getEntityWorld();float health=player.getHealth();player.closeHandledScreen();player.teleport(world,3.5,66,3.5,Set.of(),0,0,false);
        AncientAltars.open(player,ALTAR);UUID paused=token(player);ChronicleRuntime.service(server).pause(actor(player));LastingBargains.act(player,new OfferingPayloads.Action(paused,1,0));require(player.getHealth()==health,"paused packet damaged player");ChronicleRuntime.service(server).resume(actor(player));
        player.interactionManager.changeGameMode(GameMode.CREATIVE);AncientAltars.open(player,ALTAR);LastingBargains.act(player,new OfferingPayloads.Action(token(player),1,0));require(player.getHealth()==health,"Creative inspection damaged player");player.interactionManager.changeGameMode(GameMode.SURVIVAL);
        PlayRuntime.setSelfParticipant(player,false);AncientAltars.open(player,ALTAR);LastingBargains.act(player,new OfferingPayloads.Action(token(player),1,0));require(player.getHealth()==health,"nonparticipant packet damaged player");PlayRuntime.setSelfParticipant(player,true);
        AncientAltars.open(player,ALTAR);UUID stale=token(player);AltarOfferingConfig.replaceForTest(AltarOfferingConfig.encode(AltarOfferingConfig.get()));LastingBargains.act(player,new OfferingPayloads.Action(stale,1,0));LastingBargains.act(player,new OfferingPayloads.Action(stale,1,0));require(player.getHealth()==health,"stale configuration or nonce damaged player");
        AncientAltars.open(player,ALTAR);UUID missing=token(player);put(world,ALTAR,Blocks.AIR.getDefaultState());LastingBargains.act(player,new OfferingPayloads.Action(missing,1,0));require(player.getHealth()==health,"missing physical station damaged player");put(world,ALTAR,io.github.chronicle.quietbelow.QuietBelow.ANCIENT_ALTAR_BLOCK.getDefaultState());
        AncientAltars.open(player,ALTAR);UUID held=token(player);RuntimeHolds.hold(server,"chronicle_test:altar","Fixture unavailable authority");LastingBargains.act(player,new OfferingPayloads.Action(held,1,0));RuntimeHolds.release(server,"chronicle_test:altar");require(player.getHealth()==health,"unavailable authority damaged player");
        AncientAltars.open(player,ALTAR);UUID missingRoot=token(player);BlockPos heart=new BlockPos(-4,64,0);var heartState=world.getBlockState(heart);
        // Deliberate fixture-only corruption: normal writes correctly protect the bound Heart.
        try(var fault=io.github.chronicle.quietbelow.ecology.EcologyHooks.ownWrite()){put(world,heart,Blocks.AIR.getDefaultState());}
        String authority=io.github.chronicle.quietbelow.ecology.QuietBelowEcology.stationAuthorityUnavailable(world,ALTAR);
        require(world.getBlockState(heart).isAir()&&authority!=null,"missing Root fixture was not a technical failure: block="+world.getBlockState(heart)+", authority="+authority);
        LastingBargains.act(player,new OfferingPayloads.Action(missingRoot,1,0));
        require(player.getHealth()==health,"missing Root authority changed health: before="+health+", after="+player.getHealth()+", authority="+authority);
        try(var fault=io.github.chronicle.quietbelow.ecology.EcologyHooks.ownWrite()){put(world,heart,heartState);}player.closeHandledScreen();
        player.closeHandledScreen();require(GameSetupTools.menuTools().stream().noneMatch(t->t.id().equals("chronicle_core:offerings")||t.id().equals(TemporaryOfferings.OWNER)),"duplicate player-facing Altar setup entries");
        var tool=GameSetupTools.get(AltarOfferingConfig.TOOL).orElseThrow();require(tool.verbs().size()==7&&GameSetupTools.status(player.getCommandSource(),tool.id()).facts().size()<=32,"flat merged Altar controls lost bounded recovery verbs");return 8;
    }
    private static int uiOpen(ServerPlayerEntity player){AncientAltars.open(player,ALTAR,Hand.MAIN_HAND);return 1;}
    private static int uiCheck(ServerPlayerEntity player){Run run=state(player);List<TemporaryOfferingClaim> sight=TemporaryOfferings.ledger(player.getEntityWorld().getServer()).claims().stream().filter(c->c.player().equals(player.getUuid())&&c.offering().equals(TemporaryAltarOfferings.SIGHT_ID)).toList();require(sight.size()==1&&sight.getFirst().state()==TemporaryOfferingClaim.State.COMPLETE,"five-choice click did not commit exactly one Clear Sight claim");require(player.experienceLevel==run.initialXp-3,"five-choice click charged the wrong XP level count or replayed");require(TemporaryOfferings.ledger(player.getEntityWorld().getServer()).claims().stream().noneMatch(c->c.player().equals(player.getUuid())&&c.offering().equals(TemporaryAltarOfferings.REPAIR_ID)),"five-choice Clear Sight click routed to Mending");return 3;}
    private static int prepare(ServerPlayerEntity player)throws Exception{MinecraftServer server=player.getEntityWorld().getServer();ServerWorld world=player.getEntityWorld();ServerPlayerEntity other=other(player);Actor actor=actor(player);var campaign=ChronicleRuntime.service(server);if(campaign.state().phase()==SessionPhase.INACTIVE)campaign.start(actor,null);if(campaign.state().phase()==SessionPhase.PAUSED)campaign.resume(actor);for(var participant:List.of(player,other)){AuthoringRuntime.get(server).disable(participant.getUuid());PlayRuntime.setSelfParticipant(participant,true);participant.interactionManager.changeGameMode(GameMode.SURVIVAL);}
        world.getGameRules().get(GameRules.KEEP_INVENTORY).set(false,server);floor(world);put(world,ALTAR,QuietBelow.ANCIENT_ALTAR_BLOCK.getDefaultState());player.teleport(world,3.5,66,3.5,Set.of(),0,0,false);other.teleport(world,4.5,66,3.5,Set.of(),0,0,false);
        player.getInventory().clear();ItemStack armor=new ItemStack(Items.DIAMOND_CHESTPLATE);armor.setDamage(400);armor.set(DataComponentTypes.CUSTOM_NAME,Text.literal("Exact temporary repair proof"));player.getInventory().setStack(0,armor);player.getInventory().setStack(1,new ItemStack(Items.BREAD,16));player.getInventory().setSelectedSlot(0);player.experienceProgress=0;player.addExperienceLevels(12-player.experienceLevel);player.setHealth(16);player.getHungerManager().setFoodLevel(17);player.getHungerManager().setSaturationLevel(1);other.setHealth(Math.max(1,other.getMaxHealth()-6));
        require(world.getBlockEntity(ALTAR) instanceof AncientAltarBlockEntity,"Altar station identity missing");RUNS.put(server,new Run(player.getUuid(),armor.getDamage(),player.experienceLevel,player.getHungerManager().getFoodLevel(),other.getUuid()));return 8;}
    private static int purchase(ServerPlayerEntity player)throws Exception{Run run=state(player);ServerWorld world=player.getEntityWorld();TemporaryOfferings.Station station=TemporaryAltarOfferings.station(world,ALTAR,Hand.MAIN_HAND);ItemStack armor=player.getMainHandStack();int beforeDamage=armor.getDamage(),beforeXp=player.experienceLevel,beforeFood=player.getHungerManager().getFoodLevel();
        buy(player,TemporaryAltarOfferings.REPAIR,station);int repaired=beforeDamage-armor.getDamage();require(repaired==(int)Math.ceil(armor.getMaxDamage()*.25),"repair amount/rounding changed");require(armor.getName().getString().equals("Exact temporary repair proof"),"repair changed held components");require(player.getMaxHealth()==18,"repair temporary health price missing");reject(()->buy(player,TemporaryAltarOfferings.REPAIR,station),"second active repair accepted");
        buy(player,TemporaryAltarOfferings.SIGHT,station);require(player.experienceLevel==beforeXp-3,"sight XP-level price changed");require(Math.abs(player.getAttributeValue(OfferingAttributes.EXHAUSTION_MULTIPLIER)-1.25)<.00001,"sight exhaustion price missing");reject(()->buy(player,TemporaryAltarOfferings.SIGHT,station),"second active sight accepted");
        reject(()->buy(player,TemporaryAltarOfferings.HARVEST,station),"removed harvest still offered");seedLegacyHarvest(player,station);require(count(player,Items.BREAD)==0,"harvest bread price was not exact");require(!TemporaryAltarOfferings.growthAuras(world.getServer(),world.getRegistryKey().getValue().toString()).isEmpty(),"harvest aura missing");reject(()->buy(player,TemporaryAltarOfferings.HARVEST,station),"second station harvest accepted");
        ServerPlayerEntity other=world.getServer().getPlayerManager().getPlayer(run.other);float donor=player.getHealth(),recipient=other.getHealth();
        var settings=AltarOfferingConfig.get();AltarOfferingConfig.replaceForTest(AltarOfferingConfig.encode(new AltarOfferingConfig(settings.emergency(),settings.repair(),settings.sight(),settings.harvest(),900)));
        buy(player,TemporaryAltarOfferings.EMERGENCY,station);run.cooldownUntil=OfferingCooldown.until(TemporaryOfferings.ledger(world.getServer()).claims().stream().filter(c->c.offering().equals(TemporaryAltarOfferings.EMERGENCY_ID)).findFirst().orElseThrow());
        reject(()->OfferingStats.preview(player,AncientAltars.SOURCE,AncientAltars.EXTRA_HEART),"temporary success failed to block lasting purchase");require(player.getHealth()==donor-4,"emergency donor cost changed");require(other.getHealth()==Math.min(other.getMaxHealth(),recipient+4),"emergency shared healing changed");require(player.getHungerManager().getFoodLevel()==beforeFood,"temporary prayers changed hunger");other.teleport(world,20.5,66,20.5,Set.of(),0,0,false);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION,80,0));put(world,ALTAR,Blocks.AIR.getDefaultState());put(world,ALTAR,QuietBelow.ANCIENT_ALTAR_BLOCK.getDefaultState());require(TemporaryAltarOfferings.growthAuras(world.getServer(),world.getRegistryKey().getValue().toString()).isEmpty(),"replacement Altar inherited harvest aura");run.damage=armor.getDamage();run.paidXp=player.experienceLevel;return 15;}
    private static int foreign(ServerPlayerEntity player){StatusEffectInstance effect=player.getStatusEffect(StatusEffects.NIGHT_VISION);require(effect!=null&&!TemporaryAltarOfferings.owned(effect),"foreign Night Vision was replaced or marked owned");return 1;}
    private static int owned(ServerPlayerEntity player){StatusEffectInstance effect=player.getStatusEffect(StatusEffects.NIGHT_VISION);require(effect!=null&&TemporaryAltarOfferings.owned(effect),"Altar Night Vision did not resume after foreign effect");return 1;}
    private static int death(ServerPlayerEntity player){Run run=state(player);run.clock=PlayRuntime.elapsedTicks(player.getEntityWorld().getServer());run.remaining=remaining(player);player.kill(player.getEntityWorld());require(!player.isAlive(),"native death failed");return 2;}
    private static int persistent(ServerPlayerEntity player,String phase){Run run=state(player);require(player.getMaxHealth()==18,"repair price did not survive "+phase);require(Math.abs(player.getAttributeValue(OfferingAttributes.EXHAUSTION_MULTIPLIER)-1.25)<.00001,"sight price did not survive "+phase);require(player.experienceLevel<=run.paidXp,"death/reconnect refunded paid XP");require(count(player,Items.BREAD)==0,"death/reconnect refunded paid bread");List<ItemEntity> drops=player.getEntityWorld().getEntitiesByClass(ItemEntity.class,new Box(ALTAR).expand(32),item->item.getStack().isOf(Items.DIAMOND_CHESTPLATE)&&item.getStack().getName().getString().equals("Exact temporary repair proof"));require(drops.size()==1&&drops.getFirst().getStack().getCount()==1&&drops.getFirst().getStack().getDamage()==run.damage,"death duplicated or changed repaired armor drop");require(remaining(player)>0,"temporary effects expired during death/reconnect/downtime");
        long until=OfferingCooldown.until(OfferingStats.ledger(player.getEntityWorld().getServer()),TemporaryOfferings.ledger(player.getEntityWorld().getServer()),player.getUuid(),AncientAltars.SOURCE);
        require(until==run.cooldownUntil&&until>PlayRuntime.elapsedTicks(player.getEntityWorld().getServer()),"captured shared cooldown reset or vanished through "+phase);
        rejectUnchecked(()->OfferingStats.preview(player,AncientAltars.SOURCE,AncientAltars.EXTRA_HEART),"cooldown did not survive "+phase);return 8;}
    private static int pause(ServerPlayerEntity player)throws Exception{Run run=state(player);ChronicleRuntime.service(player.getEntityWorld().getServer()).pause(actor(player));run.clock=PlayRuntime.elapsedTicks(player.getEntityWorld().getServer());run.remaining=remaining(player);return 1;}
    private static int paused(ServerPlayerEntity player){Run run=state(player);require(PlayRuntime.elapsedTicks(player.getEntityWorld().getServer())==run.clock,"paused wall time advanced temporary clock");require(remaining(player)==run.remaining,"paused wall time changed expiry");return 2;}
    private static int resume(ServerPlayerEntity player)throws Exception{ChronicleRuntime.service(player.getEntityWorld().getServer()).resume(actor(player));return 1;}
    private static int expired(ServerPlayerEntity player)throws Exception{MinecraftServer server=player.getEntityWorld().getServer();require(TemporaryOfferings.active(server,AncientAltars.SOURCE).stream().noneMatch(c->c.player().equals(player.getUuid())),"temporary effects did not expire on active clock");require(player.getMaxHealth()==20,"expired repair price remained");require(player.getAttributeValue(OfferingAttributes.EXHAUSTION_MULTIPLIER)==1,"expired sight price remained");require(!TemporaryAltarOfferings.owned(player.getStatusEffect(StatusEffects.NIGHT_VISION)),"expired Altar Night Vision remained");
        TemporaryOfferings.maintain(server);require(TemporaryOfferings.ledger(server).claims().stream().anyMatch(c->c.player().equals(player.getUuid())&&c.offering().equals(TemporaryAltarOfferings.EMERGENCY_ID)&&OfferingCooldown.until(c)>PlayRuntime.elapsedTicks(server)),"terminal instant claim archived during active cooldown");return 5;}
    private static int uncertainty(ServerPlayerEntity player)throws Exception{MinecraftServer server=player.getEntityWorld().getServer();TemporaryOfferings.Station station=TemporaryAltarOfferings.station(player.getEntityWorld(),ALTAR,Hand.MAIN_HAND);boolean failed=false;try{var preview=TemporaryOfferings.preview(player,"chronicle_test:uncertain",UNCERTAIN,station);TemporaryOfferings.purchase(player,"chronicle_test:uncertain",UNCERTAIN,station,UUID.randomUUID(),preview);}catch(IllegalStateException expected){failed=true;}require(failed&&TemporaryOfferings.ledger(server).unresolved().size()==1&&RuntimeHolds.reason(server)!=null,"after-claim failure did not retain exact uncertainty");ChronicleRuntime.service(server).pause(actor(player));GameSetupTools.execute(player.getCommandSource(),TemporaryOfferings.OWNER,"prepare_review");GameSetupTools.execute(player.getCommandSource(),TemporaryOfferings.OWNER,"confirm_review");require(TemporaryOfferings.ledger(server).unresolved().isEmpty(),"confirmed review left unresolved claim");require(TemporaryOfferings.ledger(server).claims().stream().anyMatch(c->c.offering().equals(UNCERTAIN.id())&&c.state()==TemporaryOfferingClaim.State.REVIEWED),"review manufactured a successful result");return 4;}
    private static int ending(ServerPlayerEntity player)throws Exception{MinecraftServer server=player.getEntityWorld().getServer();TemporaryOfferings.closeSource(server,AncientAltars.SOURCE);require(TemporaryOfferings.ledger(server).closedSources().contains(AncientAltars.SOURCE),"temporary source tombstone missing");require(count(player,Items.BREAD)==0&&player.experienceLevel<=state(player).paidXp,"source closure reversed paid costs");return 2;}
    /** Guarded fixture of a formerly paid Harvest: production preview remains unavailable. */
    private static void seedLegacyHarvest(ServerPlayerEntity player,TemporaryOfferings.Station station)throws Exception{
        var config=AltarOfferingConfig.get().harvest();var evidence=new TreeMap<>(station.context());
        evidence.put("bread","16");evidence.put("bread_slots","1=16@16#"+digestForSeed(player.getInventory().getStack(1).getComponents().toString()));
        evidence.put("radius",Integer.toString(config.radius()));evidence.put("crop_percent",Integer.toString(config.cropPercent()));evidence.put("growth_percent",Integer.toString(config.growthPercent()));
        var legacy=new TemporaryOfferings.Definition(TemporaryAltarOfferings.HARVEST_ID,new TemporaryOfferings.Handler(){
            @Override public TemporaryOfferingTerms preview(ServerPlayerEntity actor,TemporaryOfferings.Station original){return new TemporaryOfferingTerms(config.seconds()*20L,0,0,evidence);}
            @Override public void commit(ServerPlayerEntity actor,TemporaryOfferings.Station original,TemporaryOfferingClaim claim)throws Exception{TemporaryAltarOfferings.HARVEST.handler().commit(actor,original,claim);}
        });buy(player,legacy,station);
    }
    private static String digestForSeed(String value)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    private static void buy(ServerPlayerEntity player,TemporaryOfferings.Definition definition,TemporaryOfferings.Station station)throws Exception{var preview=TemporaryOfferings.preview(player,AncientAltars.SOURCE,definition,station);TemporaryOfferings.purchase(player,AncientAltars.SOURCE,definition,station,UUID.randomUUID(),preview);}
    private static long remaining(ServerPlayerEntity player){long clock=PlayRuntime.elapsedTicks(player.getEntityWorld().getServer());return TemporaryOfferings.active(player.getEntityWorld().getServer(),AncientAltars.SOURCE).stream().filter(c->c.player().equals(player.getUuid())).mapToLong(c->c.expiresAtTicks()-clock).min().orElse(0);}
    private static Run state(ServerPlayerEntity player){MinecraftServer server=player.getEntityWorld().getServer();Run run=RUNS.get(server);if(run!=null&&run.player.equals(player.getUuid()))return run;TemporaryOfferingClaim repair=TemporaryOfferings.ledger(server).claims().stream().filter(c->c.player().equals(player.getUuid())&&c.offering().equals(TemporaryAltarOfferings.REPAIR_ID)).findFirst().orElseThrow(()->new IllegalStateException("temporary Altar fixture not prepared"));TemporaryOfferingClaim sight=TemporaryOfferings.ledger(server).claims().stream().filter(c->c.player().equals(player.getUuid())&&c.offering().equals(TemporaryAltarOfferings.SIGHT_ID)).findFirst().orElseThrow();int original=Integer.parseInt(repair.terms().evidence().get("damage")),repaired=Integer.parseInt(repair.terms().evidence().get("repair")),paid=Integer.parseInt(sight.terms().evidence().get("experience_level"))-Integer.parseInt(sight.terms().evidence().get("levels"));run=new Run(player.getUuid(),original,paid,0,new UUID(0,0));run.damage=Math.max(0,original-repaired);run.paidXp=paid;run.cooldownUntil=OfferingCooldown.until(OfferingStats.ledger(server),TemporaryOfferings.ledger(server),player.getUuid(),AncientAltars.SOURCE);RUNS.put(server,run);return run;}
    private static ServerPlayerEntity other(ServerPlayerEntity player){return player.getEntityWorld().getServer().getPlayerManager().getPlayerList().stream().filter(value->value!=player).findFirst().orElseThrow(()->new IllegalStateException("two native players required"));}
    private static int count(ServerPlayerEntity player,net.minecraft.item.Item item){int count=0;for(int i=0;i<player.getInventory().size();i++)if(player.getInventory().getStack(i).isOf(item))count+=player.getInventory().getStack(i).getCount();return count;}
    private static void fixture(ServerCommandSource source){MinecraftServer server=source.getServer();Path root=server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();require(root.getParent().getFileName().toString().equals("test-runs")&&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")&&!server.isOnlineMode()&&server.getServerPort()==25575&&server.getServerIp().equals("127.0.0.1"),"unsafe native fixture");}
    private static void put(ServerWorld world,BlockPos pos,net.minecraft.block.BlockState state){require(world.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4)!=null,"fixture attempted unloaded write");world.setBlockState(pos,state,Block.NOTIFY_ALL);}
    private static void floor(ServerWorld world){for(int x=-8;x<=24;x++)for(int z=-8;z<=24;z++)put(world,new BlockPos(x,64,z),Blocks.STONE.getDefaultState());}
    private static Actor actor(ServerPlayerEntity player){return new Actor("temporary-altar-trial",player.getUuidAsString());}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private interface Throwing{void run()throws Exception;}
    private static void rejectUnchecked(Runnable action,String message){try{action.run();}catch(OfferingRefusal expected){return;}throw new IllegalStateException(message);}
    private static void reject(Throwing action,String message)throws Exception{try{action.run();}catch(IllegalStateException expected){return;}throw new IllegalStateException(message);}
    private static final class Run{final UUID player,other;final int initialDamage,initialXp,initialFood;int damage,paidXp;long clock,remaining,cooldownUntil;Run(UUID player,int damage,int xp,int food,UUID other){this.player=player;this.initialDamage=damage;this.initialXp=xp;this.initialFood=food;this.other=other;}}
}
