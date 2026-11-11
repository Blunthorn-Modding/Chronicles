package io.github.chronicle.quietbelow.ecology;

import io.github.chronicle.core.campaign.*;
import io.github.chronicle.core.network.*;
import io.github.chronicle.core.play.EventSource;
import io.github.chronicle.core.screen.*;
import io.github.chronicle.core.server.*;
import io.github.chronicle.core.server.testing.EventSourcesTrial;
import io.github.chronicle.core.progression.SourceLedger;
import io.github.chronicle.core.progression.SourceLedgerStore;
import io.github.chronicle.quietbelow.content.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.nio.file.*;
import java.util.*;
import static net.minecraft.server.command.CommandManager.literal;

/** Reuse the real powered Forge fixture; verify real station, ecology and Heart hooks through silent Events. */
public final class SourceStationsTrial {
    private static final BlockPos FORGE=new BlockPos(2,65,3),ALTAR=new BlockPos(6,65,3),HEART=new BlockPos(-4,64,0);
    private static final EventSource PRAYER=new EventSource("chronicle_core:prayer_completed",AncientAltars.EXTRA_HEART.id(),"");
    private static final EventSource FORGING=new EventSource("chronicle_core:forge_completed","*","");
    private static final EventSource EXTENT=new EventSource("quiet_below:ecology_milestone","network","Established");
    private static final EventSource DESTROYED=new EventSource("quiet_below:heart_destroyed","network","");
    private static Job job;
    private SourceStationsTrial() { }
    public static void register() {
        if(!"true".equals(System.getenv("CHRONICLE_EVENT_SOURCES_TRIAL"))||!"true".equals(System.getenv("CHRONICLE_FORGE_NATIVE_TRIAL")))return;
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("chronicle_source_stations_trial").then(literal("run").executes(c->start(c.getSource())))));
        ServerTickEvents.END_SERVER_TICK.register(SourceStationsTrial::tick);
    }
    private static int start(ServerCommandSource source) {
        try {
            fixture(source);require(job==null,"station proof already active");var player=source.getPlayerOrThrow();var server=source.getServer();
            require(ChronicleRuntime.service(server).state().phase()==SessionPhase.INACTIVE,"fresh station source fixture required");
            require(server.getCommandManager().getDispatcher().execute("quiet_below_forge_trial prepare",source)==1,"guarded powered Forge fixture failed");
            var campaign=ChronicleRuntime.service(server);campaign.pause(actor(player));AuthoringRuntime.get(server).enable(player.getUuid());
            for(var eventSource:List.of(PRAYER,FORGING,EXTENT,DESTROYED))EventSourcesTrial.arm(player,eventSource,List.of());
            AuthoringRuntime.get(server).disable(player.getUuid());campaign.resume(actor(player));job=new Job(player.getUuid());return 1;
        }catch(Exception failure){source.sendError(Text.literal("Event sources trial FAIL stations: "+failure.getMessage()));return 0;}
    }
    private static void tick(MinecraftServer server) {
        if(job==null)return;var player=server.getPlayerManager().getPlayer(job.player);if(player==null)return;
        try {
            fixture(player.getCommandSource());require(++job.ticks<2400,"station source deadline at "+job.stage);
            if(job.wait-->0)return;
            var world=player.getEntityWorld();var engine=QuietBelowEcology.engineForTest(server);
            switch(job.stage) {
                case 0->{if(QuietBelowEcology.stationPower(world,FORGE)!=null||QuietBelowEcology.stationPower(world,ALTAR)!=null||!flagged(server,EXTENT))return;
                    EventSourcesTrial.verify(server,EXTENT);job.checks.add("actual durable ecology transition to Established dispatched the selected milestone Event");
                    AncientAltars.open(player,ALTAR);require(player.currentScreenHandler instanceof OfferingScreenHandler,"real Altar did not open");var h=(OfferingScreenHandler)player.currentScreenHandler;
                    LastingBargains.act(player,new OfferingPayloads.Action(h.view().token(),1,0));player.closeHandledScreen();job.wait=20;job.stage++;}
                case 1->{if(!flagged(server,PRAYER))return;EventSourcesTrial.verify(server,PRAYER);require(OfferingStats.ledger(server).count(player.getUuid(),AncientAltars.SOURCE,AncientAltars.EXTRA_HEART.id())==1,"prayer source lacks its real durable purchase");
                    job.checks.add("real LastingBargains purchase, including station gates and durable receipt, dispatched prayer completion");player.setHealth(player.getMaxHealth());player.getHungerManager().setFoodLevel(20);
                    pair(player,new ItemStack(Items.FLINT),new ItemStack(Items.ARROW));job.stage++;}
                case 2->{var h=forge(player);if(h.phase()==ItemEnchantingPayloads.Phase.WORKING)return;require(h.phase()==ItemEnchantingPayloads.Phase.EMPTY,"scrap pair unexpectedly produced output");require(!flagged(server,FORGING),"scraps claimed successful enchantment");
                    job.checks.add("real invalid Forge pair consumed as scraps without claiming enchantment completion");player.closeHandledScreen();job.wait=20;job.stage=20;}
                case 20->{pair(player,new ItemStack(Items.IRON_SWORD),new ItemStack(Items.ECHO_SHARD));job.stage=3;}
                case 3->{var execution=PlayRuntime.service(server).state().executions().get(EventSourcesTrial.event(FORGING));if(execution==null||execution.phase()!=io.github.chronicle.core.play.EventExecution.Phase.COMPLETE||!flagged(server,FORGING))return;
                    verifyConservedForgeResult(server,player);EventSourcesTrial.verify(server,FORGING);
                    job.checks.add("real ingredient workshop produced changed enchanted output and immutable result evidence before SYSTEM completion; its one result survived normal hold-driven screen closure");player.closeHandledScreen();
                    engine.tuneHeart(new EcologyHeart.Rules(EcologyEngine.MAX_CELLS,1));job.stage++;}
                case 4->{if(!engine.heartVulnerable(true))return;player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));player.teleport(world,-2.5,65,0.5,Set.of(),90,25,false);
                    player.interactionManager.tryBreakBlock(HEART);require(engine.ending()!=null,"real protected Heart break did not commit ending");job.stage++;}
                case 5->{if(!flagged(server,DESTROYED))return;EventSourcesTrial.verify(server,DESTROYED);job.checks.add("real participating pickaxe destruction committed Heart ending and its configured source Event; sculk cleanup remained separate");finish(player.getCommandSource());}
                default->throw new IllegalStateException();
            }
        }catch(Exception failure){player.getCommandSource().sendError(Text.literal("Event sources trial FAIL stations stage "+job.stage+" tick "+job.ticks+" handler "+player.currentScreenHandler.getClass().getSimpleName()+": "+failure.getMessage()));job=null;}
    }
    private static void pair(ServerPlayerEntity player,ItemStack first,ItemStack second) {
        AncientForges.open(player,FORGE);var h=forge(player);stage(player,h,first);ItemEnchantingRuntime.offer(player,request(h));require(h.phase()==ItemEnchantingPayloads.Phase.OFFERED,"first real workshop offering refused");
        stage(player,h,second);ItemEnchantingRuntime.offer(player,request(h));require(h.phase()==ItemEnchantingPayloads.Phase.WORKING,"second real workshop offering refused");
    }
    private static void verifyConservedForgeResult(MinecraftServer server,ServerPlayerEntity player)throws Exception {
        var ledger=new SourceLedgerStore(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/event-sources.bin")).load();
        var receipts=ledger.receipts().values().stream().filter(receipt->receipt.type().equals(FORGING.type())&&receipt.actor().equals(player.getUuid())&&receipt.stage()==SourceLedger.Stage.COMMITTED).toList();
        require(receipts.size()==1,"Forge source lacks exactly one durable result operation");var evidence=EventSources.forgeEvidence(server,receipts.getFirst());
        var input=decode(server,evidence.first());
        var ingredient=decode(server,evidence.second());
        var expected=decode(server,evidence.result());
        require(input.isOf(Items.IRON_SWORD)&&input.getOrDefault(DataComponentTypes.ENCHANTMENTS,ItemEnchantmentsComponent.DEFAULT).isEmpty()&&ingredient.isOf(Items.ECHO_SHARD),"Forge receipt does not pin the actual unenchanted sword and recipe ingredient");
        require(expected.isOf(Items.IRON_SWORD)&&!expected.getOrDefault(DataComponentTypes.ENCHANTMENTS,ItemEnchantmentsComponent.DEFAULT).isEmpty()&&!ItemStack.areEqual(input,expected),"Forge source claimed unchanged native output");
        int copies=0;for(int slot=0;slot<player.getInventory().size();slot++)if(ItemStack.areEqual(player.getInventory().getStack(slot),expected))copies++;
        if(player.currentScreenHandler instanceof ItemEnchantingScreenHandler handler&&ItemStack.areEqual(handler.output(),expected))copies++;
        if(ItemStack.areEqual(player.currentScreenHandler.getCursorStack(),expected))copies++;
        for(ItemEntity drop:player.getEntityWorld().getEntitiesByClass(ItemEntity.class,new Box(player.getBlockPos()).expand(8),entity->true))if(ItemStack.areEqual(drop.getStack(),expected))copies++;
        require(copies==1,"Forge's actual enchanted result was lost or duplicated across hold-driven native close: "+copies);
    }
    private static ItemStack decode(MinecraftServer server,String json) {
        var ops=net.minecraft.registry.RegistryOps.of(com.mojang.serialization.JsonOps.INSTANCE,server.getRegistryManager());
        return ItemStack.OPTIONAL_CODEC.parse(ops,com.google.gson.JsonParser.parseString(json)).getOrThrow(message->new IllegalArgumentException("Invalid native Forge evidence: "+message));
    }
    private static void stage(ServerPlayerEntity player,ItemEnchantingScreenHandler h,ItemStack stack){h.getSlot(ItemEnchantingScreenHandler.ENTRY_SLOT).setStack(stack);ItemEnchantingRuntime.changed(player,h);require(h.view().offerable(),"native workshop entry is unavailable");}
    private static ItemEnchantingPayloads.Offer request(ItemEnchantingScreenHandler h){var view=h.view();return new ItemEnchantingPayloads.Offer(h.syncId,view.sessionId(),view.actionToken());}
    private static ItemEnchantingScreenHandler forge(ServerPlayerEntity player){require(player.currentScreenHandler instanceof ItemEnchantingScreenHandler,"native Forge handler closed");return (ItemEnchantingScreenHandler)player.currentScreenHandler;}
    private static boolean flagged(MinecraftServer server,EventSource source){return ChronicleRuntime.service(server).state().flag(EventSourcesTrial.flag(source));}
    private static Actor actor(ServerPlayerEntity player){return new Actor("event-sources-stations",player.getUuidAsString());}
    private static void finish(ServerCommandSource source)throws Exception {var report=new com.google.gson.JsonObject();report.addProperty("phase","stations");var checks=new com.google.gson.JsonArray();job.checks.forEach(checks::add);report.add("checks",checks);Files.writeString(root(source.getServer()).resolve(".event-sources-trial.json"),report+"\n");job=null;source.sendFeedback(()->Text.literal("Event sources trial PASS stations"),false);}
    private static Path root(MinecraftServer server){return server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();}
    private static void fixture(ServerCommandSource source){var server=source.getServer();Path root=root(server);require(source.hasPermissionLevel(2)&&source.getPlayer()!=null&&source.getName().equals("ChronSources")&&root!=null&&root.getParent()!=null&&root.getParent().getFileName().toString().equals("test-runs")&&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")&&Files.isRegularFile(root.resolve(".event-sources-fixture"))&&server.isDedicated()&&!server.isOnlineMode()&&server.getServerPort()==25575&&server.getServerIp().equals("127.0.0.1"),"unsafe station source fixture");}
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException(text);}
    private static final class Job{final UUID player;final List<String> checks=new ArrayList<>();int stage,ticks,wait;Job(UUID player){this.player=player;}}
}
