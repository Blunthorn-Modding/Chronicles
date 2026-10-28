package io.github.chronicle.core.server.testing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.chronicle.core.areas.AreaState;
import io.github.chronicle.core.authoring.AuthoringSite;
import io.github.chronicle.core.authoring.BlockPosition;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.campaign.Actor;
import io.github.chronicle.core.campaign.SessionPhase;
import io.github.chronicle.core.menu.EventEditorForm;
import io.github.chronicle.core.play.*;
import io.github.chronicle.core.server.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static net.minecraft.server.command.CommandManager.literal;

/** Opt-in, fresh loopback-only runtime proof. No command is registered in ordinary launches. */
public final class EventProgressionTrial {
    private static final String PREFIX="chronicle_core:progress_trial_", A=PREFIX+"a", B=PREFIX+"b",
            DONE=PREFIX+"done", DEPENDENT=PREFIX+"dependent", RESTART_GATE=PREFIX+"restart_gate",
            RESTART_DONE=PREFIX+"restart_done", EMPTY_DONE=PREFIX+"empty_done", OVERRIDE=PREFIX+"override";
    private static final String ENTRY=PREFIX+"entry", TIMER=PREFIX+"timer", DEP=PREFIX+"dep", RESTART=PREFIX+"restart",
            EMPTY=PREFIX+"empty", MEDIA=PREFIX+"media", WORLD=PREFIX+"world", CRASH=PREFIX+"crash";
    private static Job job;
    private EventProgressionTrial() { }

    public static void register() {
        if(!"true".equals(System.getenv("CHRONICLE_EVENT_PROGRESSION_TRIAL")))return;
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)-> {
            var command=literal("chronicle_event_progression_trial");
            for(String verb:List.of("basic","bounce","restart","crash","recover","final"))
                command.then(literal(verb).executes(context->run(context.getSource(),verb)));
            dispatcher.register(command);
        });
        ServerTickEvents.END_SERVER_TICK.register(EventProgressionTrial::tick);
    }

    private static int run(ServerCommandSource source,String verb) {
        try {
            fixture(source.getServer());require(source.hasPermissionLevel(2),"OP2 required");
            require(source.getWorld().getRegistryKey()==net.minecraft.world.World.OVERWORLD,"Overworld fixture required");
            var player=source.getPlayerOrThrow();require(player.getName().getString().equals("ChronProgress"),"wrong fixture account");
            switch(verb) {
                case "basic" -> prepare(source,player);
                case "bounce" -> {
                    require(job!=null&&job.stage==3,"bounce was not requested");
                    var campaign=ChronicleRuntime.service(source.getServer());
                    campaign.setFlag(actor(player),B,false);campaign.setFlag(actor(player),B,true);
                    job.bounceAt=clock(source.getServer());job.stage=4;
                }
                case "restart" -> restart(source,player);
                case "crash" -> {
                    require("true".equals(System.getenv("CHRONICLE_EVENT_PROGRESSION_HALT_AFTER_FLAGS")),"crash seam is not armed");
                    var review=EventRuntime.review(source.getServer(),CRASH);EventRuntime.run(player,CRASH,review);
                    throw new IllegalStateException("flag outcome halt seam did not execute");
                }
                case "recover","final" -> verifyRecovery(source,verb);
                default -> throw new IllegalArgumentException("unknown fixture action");
            }
            return 1;
        }catch(Exception failure){fail(source,verb,failure);return 0;}
    }

    private static void prepare(ServerCommandSource source,ServerPlayerEntity player)throws Exception {
        require(job==null,"fixture job already active");var server=source.getServer();var campaign=ChronicleRuntime.service(server);
        require(campaign.state().phase()==SessionPhase.INACTIVE,"fresh fixture campaign required");
        require(PlayRuntime.service(server).state().events().isEmpty(),"fresh fixture Events required");
        for(String flag:List.of(A,B,DONE,DEPENDENT,RESTART_GATE,RESTART_DONE,EMPTY_DONE,OVERRIDE))campaign.defineFlag(actor(player),flag);
        campaign.start(actor(player),1);campaign.pause(actor(player));AuthoringRuntime.get(server).enable(player.getUuid());
        source.getWorld().setBlockState(new net.minecraft.util.math.BlockPos(1,64,1),Blocks.STONE.getDefaultState());
        source.getWorld().setBlockState(new net.minecraft.util.math.BlockPos(21,64,21),Blocks.STONE.getDefaultState());
        PlayRuntime.setParticipant(source,player.getUuid(),true);player.changeGameMode(GameMode.SURVIVAL);teleport(player,1.5,65,1.5);
        var authoring=AuthoringRuntime.get(server).service();
        authoring.put(new AuthoringSite.Region("progress_entry","minecraft:overworld",1,new BlockPosition(20,60,20),new BlockPosition(23,75,23)),false);
        authoring.put(new AuthoringSite.Region("progress_empty","minecraft:overworld",1,new BlockPosition(40,60,40),new BlockPosition(43,75,43)),false);
        source.getWorld().setBlockState(new net.minecraft.util.math.BlockPos(8,64,8),Blocks.STONE.getDefaultState());
        AreaChanges.begin(source,"progress_world","Progression world target",AreaState.START);
        AreaChanges.setDraftCorner(player,1,new BlockPosition(8,64,8));AreaChanges.setDraftCorner(player,2,new BlockPosition(8,64,8));AreaChanges.saveDraft(source);
        job=new Job(player.getUuid(),"basic");job.stage=0;
    }

    private static void configure(ServerPlayerEntity player)throws Exception {
        var server=player.getEntityWorld().getServer();
        save(player,ENTRY,PlayState.Mode.ENTRY,0,List.of(),Map.of(A,true,B,false),"progress_entry","",List.of(),"");
        save(player,TIMER,PlayState.Mode.TIMER,5,List.of(A,B),Map.of(DONE,true,B,false),"","",List.of(),"");
        save(player,DEP,PlayState.Mode.CONDITION,0,List.of(DONE),Map.of(DEPENDENT,true),"","",List.of(),"");
        save(player,RESTART,PlayState.Mode.TIMER,10,List.of(A,RESTART_GATE),Map.of(RESTART_DONE,true),"","",List.of(),"");
        save(player,EMPTY,PlayState.Mode.MANUAL,0,List.of(),Map.of(EMPTY_DONE,true),"","progress_empty",List.of(),"");
        save(player,MEDIA,PlayState.Mode.MANUAL,0,List.of(),Map.of(),"","progress_empty",List.of(),"No empty audience media");
        var area=AreaChanges.catalog(server).areas().get("progress_world");var snapshot=area.requireSnapshot(AreaState.START);
        var change=new EventEditorForm.Change(area.bounds().regionId(),area.name(),"START",area.revision(),snapshot.sha256(),area.bounds().dimension(),true,"");
        save(player,WORLD,PlayState.Mode.MANUAL,0,List.of(),Map.of(),"","progress_empty",List.of(change),"");
        save(player,CRASH,PlayState.Mode.MANUAL,0,List.of(),Map.of(OVERRIDE,true),"","",List.of(),"");
        AuthoringRuntime.get(server).disable(player.getUuid());ChronicleRuntime.service(server).resume(actor(player));
        teleport(player,21.5,65,21.5);
    }

    private static void tick(MinecraftServer server) {
        if(job==null)return;var player=server.getPlayerManager().getPlayer(job.player);if(player==null)return;
        var source=player.getCommandSource();
        try {
            fixture(server);require(++job.serverTicks<2400,"fixture tick deadline");
            long now=clock(server);
            switch(job.stage) {
                case 0 -> {var area=AreaChanges.catalog(server).areas().get("progress_world");if(area==null||area.start().isEmpty()||AreaChanges.busy(server))return;configure(player);job.stage=1;}
                case 1 -> {
                    if(!complete(server,ENTRY))return;require(flag(server,A)&&!flag(server,B),"entry atomic On/Off patch missing");
                    require(execution(server,ENTRY).source()==PlayState.DeliverySource.ENTRY,"entry was not real Region trigger");
                    require(progress(server,TIMER).startedAt()==-1,"missing second prerequisite gave countdown credit");
                    check("real Region ENTRY silently applied atomic On/Off flags; second prerequisite prevented timer start");
                    job.observed=now;job.stage=2;
                }
                case 2 -> {
                    require(progress(server,TIMER).startedAt()==-1,"timer started with only one prerequisite");
                    if(now-job.observed<30)return;ChronicleRuntime.service(server).setFlag(actor(player),B,true);job.stage=20;
                }
                case 20 -> {
                    var e=progress(server,TIMER);if(e.startedAt()<0)return;job.firstStart=e.startedAt();
                    require(!complete(server,TIMER),"timer fired immediately on unlock");check("all prerequisites started a fresh full 100 occupied-tick interval");job.stage=21;
                }
                case 21 -> {
                    require(!complete(server,TIMER),"timer fired before full interval");if(now-job.firstStart<40)return;
                    job.stage=3;source.sendFeedback(()->Text.literal("Event progression trial NEXT bounce"),false);
                }
                case 3 -> require(!complete(server,TIMER),"client failed to bounce before deadline");
                case 4 -> {
                    var e=progress(server,TIMER);if(e.startedAt()<job.bounceAt)return;job.bouncedStart=e.startedAt();
                    require(job.bouncedStart>job.firstStart,"brief Off/On failed to reset epoch");job.stage=5;
                }
                case 5 -> {
                    if(now<job.firstStart+100)return;require(!complete(server,TIMER),"old countdown deadline still fired");
                    check("Off then On in one server command invalidated the old deadline using the durable Off epoch");job.stage=6;
                }
                case 6 -> {
                    if(!complete(server,TIMER))return;require(execution(server,TIMER).elapsedTicks()>=job.bouncedStart+100,"new deadline was shortened");
                    require(flag(server,DONE)&&!flag(server,B),"timer On/Off outcome was not atomic");job.stage=7;
                }
                case 7 -> {
                    if(!complete(server,DEP))return;require(flag(server,DEPENDENT),"dependent Event outcome missing");
                    require(execution(server,DEP).elapsedTicks()>execution(server,TIMER).elapsedTicks(),"dependent Event recursively executed in same clock turn");
                    check("fresh deadline completed two flag outcomes; dependent Event fired on the next eligible turn");
                    var review=EventRuntime.review(server,EMPTY);require(review.recipients().isEmpty(),"empty fixture audience is not empty");EventRuntime.run(player,EMPTY,review);
                    require(complete(server,EMPTY)&&flag(server,EMPTY_DONE),"zero-audience flag-only Event refused");
                    require(execution(server,EMPTY).recipients().isEmpty(),"silent Event manufactured recipients");
                    require(!PlayRuntime.service(server).state().deliveries().containsKey(EMPTY),"silent Event manufactured legacy delivery");
                    audienceRefused(server,MEDIA);audienceRefused(server,WORLD);
                    check("zero-audience flag-only Event completed; media and saved-world Events refused before execution");
                    ChronicleRuntime.service(server).setFlag(actor(player),RESTART_GATE,true);job.stage=8;
                }
                case 8 -> {
                    var e=progress(server,RESTART);if(e.startedAt()<0)return;job.restartStart=e.startedAt();if(now-e.startedAt()<20)return;
                    ChronicleRuntime.service(server).pause(actor(player));SessionBuilding.set(source,true);job.frozen=clock(server);job.wait=0;job.stage=9;
                }
                case 9 -> {
                    require(clock(server)==job.frozen,"paused Building Mode advanced Chronicle clock");require(!complete(server,RESTART),"pause fired countdown");
                    if(++job.wait<40)return;PlayRuntime.service(server).checkpoint();
                    check("pause plus Building Mode preserved countdown while real server ticks continued");
                    snapshot(server,"paused-countdown");pass(source,"basic");job=null;
                }
                case 30 -> {
                    if(now-job.observed<20)return;player.changeGameMode(GameMode.SPECTATOR);job.frozen=clock(server);job.wait=0;job.stage=31;
                }
                case 31 -> {
                    require(PlayRuntime.eligiblePlayers(server).isEmpty(),"spectator remained eligible");require(clock(server)==job.frozen,"no-eligible interval advanced clock");
                    if(++job.wait<40)return;player.changeGameMode(GameMode.SURVIVAL);check("no eligible players stopped countdown with no empty-world credit");job.stage=32;
                }
                case 32 -> {
                    if(!complete(server,RESTART))return;require(execution(server,RESTART).elapsedTicks()>=job.restartStart+200,"restart granted downtime credit");
                    require(flag(server,RESTART_DONE),"restart Event outcome missing");check("actual restart retained countdown pins and fired only after its full occupied interval");
                    snapshot(server,"restart-complete");pass(source,"restart");job=null;
                }
                default -> throw new IllegalStateException("unknown fixture stage");
            }
        }catch(Exception failure){fail(source,job.phase,failure);job=null;}
    }

    private static void restart(ServerCommandSource source,ServerPlayerEntity player)throws Exception {
        var server=source.getServer();JsonObject before=BookConfig.strictObject(Files.readString(root(server).resolve(".event-progression-trial.json")));
        require(ChronicleRuntime.service(server).state().phase()==SessionPhase.PAUSED,"restart lost paused phase");
        require(clock(server)==before.get("clock").getAsLong(),"restart advanced paused clock");
        require(!SessionBuilding.enabled(server),"Building Mode unexpectedly persisted");require(!complete(server,RESTART),"restart timer already completed");
        long start=progress(server,RESTART).startedAt();require(start==before.get("restartStart").getAsLong(),"restart changed countdown origin");
        require(flag(server,DONE)&&flag(server,DEPENDENT)&&!flag(server,B),"campaign flags did not survive restart");
        player.changeGameMode(GameMode.SURVIVAL);ChronicleRuntime.service(server).resume(actor(player));
        job=new Job(player.getUuid(),"restart");job.stage=30;job.restartStart=start;job.observed=clock(server);
        check("actual JVM restart retained exact paused clock, source/countdown pins and campaign outcomes without downtime credit");
    }

    /** Root coordinator calls this immediately after the durable flag receipt and before its next play phase. */
    public static void afterFlagOutcome(MinecraftServer server,EventExecution execution)throws java.io.IOException {
        if(!"true".equals(System.getenv("CHRONICLE_EVENT_PROGRESSION_TRIAL"))
                ||!"true".equals(System.getenv("CHRONICLE_EVENT_PROGRESSION_HALT_AFTER_FLAGS"))||!CRASH.equals(execution.eventId()))return;
        fixture(server);var campaign=ChronicleRuntime.service(server);
        require(campaign.state().eventFlagOutcomes().containsKey(execution.operationId()),"halt preceded durable flag receipt");
        require(flag(server,OVERRIDE),"first flag outcome was not applied before halt");
        campaign.setFlag(new Actor("progression-trial-later-GM",null),OVERRIDE,false);PlayRuntime.service(server).checkpoint();
        JsonObject marker=new JsonObject();marker.addProperty("operation",execution.operationId().toString());marker.addProperty("clock",clock(server));
        marker.addProperty("phase",PlayRuntime.service(server).state().executions().get(CRASH).phase().name());
        Files.writeString(root(server).resolve(".event-progression-halt.json"),marker+"\n");Runtime.getRuntime().halt(99);
    }

    private static void verifyRecovery(ServerCommandSource source,String phase)throws Exception {
        var server=source.getServer();var marker=BookConfig.strictObject(Files.readString(root(server).resolve(".event-progression-halt.json")));
        var execution=execution(server,CRASH);require(complete(server,CRASH),"restarted flag outcome did not complete");
        require(execution.operationId().toString().equals(marker.get("operation").getAsString()),"recovery manufactured new operation");
        require(!flag(server,OVERRIDE),"receipt replay overwrote later GM flag value");
        long receipts=ChronicleRuntime.service(server).state().eventFlagOutcomes().values().stream().filter(r->r.eventId().equals(CRASH)).count();
        require(receipts==1,"recovery duplicated atomic flag receipt");require(RuntimeHolds.reason(server)==null,"recovery left held world");
        job=new Job(source.getPlayerOrThrow().getUuid(),phase);check("durable receipt survived hard halt before play phase advance; later GM override retained with one receipt and same operation");
        snapshot(server,phase);pass(source,phase);job=null;
    }

    private static void save(ServerPlayerEntity player,String id,PlayState.Mode mode,long seconds,List<String> requirements,Map<String,Boolean> patch,
                             String trigger,String audience,List<EventEditorForm.Change> changes,String message)throws Exception {
        var server=player.getEntityWorld().getServer();var sites=AuthoringRuntime.get(server).service().state().sites();
        EventRuntime.save(player,new EventEditorForm(id,0,id.substring(id.lastIndexOf('_')+1),"Disposable progression runtime proof",message,"",
                audience.isEmpty()?"PARTICIPANTS":"REGION",audience,audience.isEmpty()?0:sites.get(audience).revision(),mode.name(),trigger,
                trigger.isEmpty()?0:sites.get(trigger).revision(),seconds,requirements,changes,true,patch,EventSource.empty()),null,true);
    }
    private static void audienceRefused(MinecraftServer server,String id) {
        try{EventRuntime.review(server,id);}catch(IllegalStateException refusal){require(refusal.getMessage().contains("audience"),"unexpected empty-audience refusal: "+refusal.getMessage());require(execution(server,id)==null,"refused Event published execution");return;}
        throw new IllegalStateException("empty media/world audience was accepted");
    }
    private static void snapshot(MinecraftServer server,String phase)throws Exception {
        JsonObject value=new JsonObject();value.addProperty("phase",phase);value.addProperty("clock",clock(server));
        value.addProperty("observedServerTicks",job.serverTicks);value.addProperty("frozenIntervalServerTicks",job.wait);
        value.addProperty("restartStart",job.restartStart);value.addProperty("firstStart",job.firstStart);value.addProperty("bouncedStart",job.bouncedStart);
        value.addProperty("campaignNextSequence",ChronicleRuntime.service(server).state().nextSequence());value.addProperty("progressRevision",EventProgressRuntime.revision(server));
        value.add("play",BookConfig.strictObject(Files.readString(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/play-runtime.json"))));
        value.add("progress",BookConfig.strictObject(Files.readString(server.getSavePath(WorldSavePath.ROOT).resolve("chronicle/event-progress.json"))));
        JsonArray checks=new JsonArray();job.checks.forEach(checks::add);value.add("checks",checks);
        Files.writeString(root(server).resolve(".event-progression-trial.json"),value+"\n");
    }
    private static void check(String text){job.checks.add(text);}
    private static boolean flag(MinecraftServer server,String id){return Boolean.TRUE.equals(ChronicleRuntime.service(server).state().flags().get(id));}
    private static EventExecution execution(MinecraftServer server,String id){return PlayRuntime.service(server).state().executions().get(id);}
    private static boolean complete(MinecraftServer server,String id){var e=execution(server,id);return e!=null&&e.phase()==EventExecution.Phase.COMPLETE;}
    private static EventProgressState.Entry progress(MinecraftServer server,String id){var e=EventProgressRuntime.entry(server,EventRuntime.configuration(server,id));require(e!=null,"missing progress pin: "+id);return e;}
    private static long clock(MinecraftServer server){return PlayRuntime.elapsedTicks(server);}
    private static Actor actor(ServerPlayerEntity player){return new Actor("event-progression-trial",player.getUuidAsString());}
    private static void teleport(ServerPlayerEntity player,double x,double y,double z){player.teleport((ServerWorld)player.getEntityWorld(),x,y,z,Set.of(),0,0,false);player.setVelocity(0,0,0);player.onLanding();}
    private static void pass(ServerCommandSource source,String action){source.sendFeedback(()->Text.literal("Event progression trial PASS "+action),false);}
    private static void fail(ServerCommandSource source,String action,Exception failure){source.sendError(Text.literal("Event progression trial FAIL "+action+": "+failure.getMessage()));}
    private static Path root(MinecraftServer server){return server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getParent();}
    private static void fixture(MinecraftServer server) {
        var root=root(server);require(root!=null&&root.getParent()!=null&&root.getParent().getFileName().toString().equals("test-runs")
                &&root.getFileName().toString().matches("[0-9]{8}T[0-9]{12,}Z")&&Files.isRegularFile(root.resolve(".event-progression-fixture"))
                &&server.isDedicated()&&!server.isOnlineMode()&&server.getServerPort()==25575&&"127.0.0.1".equals(server.getServerIp()),"unsafe progression fixture");
    }
    private static void require(boolean condition,String text){if(!condition)throw new IllegalStateException(text);}
    private static final class Job {
        final UUID player;final String phase;final List<String> checks=new ArrayList<>();
        int stage,wait,serverTicks;long observed,firstStart,bouncedStart,bounceAt,restartStart,frozen;
        Job(UUID player,String phase){this.player=player;this.phase=phase;}
    }
}
